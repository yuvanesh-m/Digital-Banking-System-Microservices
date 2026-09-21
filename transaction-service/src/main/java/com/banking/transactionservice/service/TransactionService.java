package com.banking.transactionservice.service;

import com.banking.transactionservice.client.AccountServiceClient;
import com.banking.transactionservice.dto.TransactionResponse;
import com.banking.transactionservice.dto.TransferRequest;
import com.banking.transactionservice.entity.Transaction;
import com.banking.transactionservice.entity.TransactionStatus;
import com.banking.transactionservice.entity.TransactionType;
import com.banking.transactionservice.event.TransactionCompletedEvent;
import com.banking.transactionservice.event.TransactionInitiatedEvent;
import com.banking.transactionservice.repo.TransactionRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepo transactionRepo;
    private final AccountServiceClient accountServiceClient;

    private final KafkaTemplate<String,Object> kafkaTemplate;
    private final RedisTemplate<String,String> redisTemplate;

    private static final String TRANSACTION_INITIATED_TOPIC = "transaction.initiated";
    private static final String TRANSACTION_COMPLETED_TOPIC = "transaction.completed";
    private static final String TRANSACTION_REFUNDED_TOPIC = "transaction.refunded";
    private static final String FRAUD_DETECTED = "fraud.detected";

    /**
     * SAGA STEP -1 Initiate transfer
     * Deducts money from sender via feign
     * Saves transaction as PROCESSING
     * Publish event to kafka for fraud check
     * @param request
     * @return
     */
    public TransactionResponse transfer(TransferRequest request) {

        log.info("SAGA START - Transfer {} -> {} amount : {}",
                request.getSenderAccountNumber(),
                request.getReceiverAccountNumber(),
                request.getAmount());

        // SAGA STEP-1: deduct from the sender
        accountServiceClient.deductBalance(request.getSenderAccountNumber(),request.getAmount());

        log.info("AFTER DEDUCTED BALANCE : {} FOR ACCOUNT : {} ",accountServiceClient.getBalance(request.getSenderAccountNumber()),request.getSenderAccountNumber());

        //Initiated transaction status to Processing
        Transaction transaction = new Transaction();
        transaction.setSenderAccountNumber(request.getSenderAccountNumber());
        transaction.setReceiverAccountNumber(request.getReceiverAccountNumber());
        transaction.setAmount(request.getAmount());
        transaction.setType(TransactionType.TRANSFER);
        transaction.setStatus(TransactionStatus.PROCESSING);
        transaction.setDescription(request.getDescription());
        transaction.setReferenceNumber(UUID.randomUUID().toString());

        Transaction savedTransaction = transactionRepo.save(transaction);

        log.info("Transaction saved as the processing : {} ",savedTransaction.getId());


        //SAGA STEP-2 Publish for fraud check
        TransactionInitiatedEvent event = new TransactionInitiatedEvent();
        event.setTransactionID(savedTransaction.getId());
        event.setSenderAccountNumber(savedTransaction.getSenderAccountNumber());
        event.setReceiverAccountNumber(savedTransaction.getReceiverAccountNumber());
        event.setAmount(savedTransaction.getAmount());
        event.setDescription(savedTransaction.getDescription());

        kafkaTemplate.send(TRANSACTION_INITIATED_TOPIC,savedTransaction.getId(),event);
        log.info("SAGA STEP 2 - TransactionInitiatedEvent published : {}",savedTransaction.getId());

        return mapToResponse(savedTransaction);

    }



    public TransactionResponse getTransaction(String transactionID) {

        Transaction transaction = transactionRepo.findById(transactionID)
                .orElseThrow(()->new RuntimeException("Transaction not found"));

        return mapToResponse(transaction);
    }

    public List<TransactionResponse> getTransactionHistory(String accountNumber) {
        return transactionRepo.findTransactionByAccountNumber(accountNumber)
                                .stream()
                                .map((transaction)->mapToResponse(transaction))

                                .toList();
    }

    public TransactionResponse verifyOTP(String transactionID, String otp) {
          log.info("OTP verification for the transaction : {}",transactionID);

          Transaction transaction = transactionRepo.findById(transactionID)
                  .orElseThrow(()->new RuntimeException("Transaction not found "+transactionID));

         String otpKey = "verification:otp"+transactionID;
         String otpRedis = redisTemplate.opsForValue().get(otpKey);

         if(otpRedis == null){
             log.warn("OTP expired for transaction : {}",transactionID);
             compensateTransaction(transaction,"OTP expired -transaction cancelled and amount refunded");
             return mapToResponse(transaction);
         }
         if(!otpRedis.equals(otp)){
             //BLOCK ACCOUNT AND REFUND (AS OTP WAS WRONG)
             log.warn("Wrong OTP blocking account and refunding : {}",transactionID);
             redisTemplate.delete(otpKey);
             blockAccountAndCompensate(transaction,
                     "Wrong OTP entered - transaction cancelled"+
                     "Account Blocked for security");
             return mapToResponse(transaction);
         }
         //OTP correct -compensate transaction
        log.info("OTP verified - completing transaction : {}",transactionID);
         redisTemplate.delete(otpKey);
         completeTransaction(transaction);

         return mapToResponse(transaction);

    }

    private void compensateTransaction(Transaction transaction , String reason)
    {
        log.warn("SAGA COMPENSATION - refunding: {} - amount: {}",
                transaction.getSenderAccountNumber(),transaction.getAmount());

        //CREDIT MONEY BACK TO SENDER SYNCHRONOUSLY

        accountServiceClient.creditBalance(transaction.getSenderAccountNumber(),transaction.getAmount());

        transaction.setStatus(TransactionStatus.FLAGGED);

        transaction.setFailureReason(reason +
                "SAGA compensation executed , amount refunded at "+ LocalDateTime.now());

        transactionRepo.save(transaction);

        //Publish Refund Event - Notification service will alert user
        Map<String,Object> refundEvent = new HashMap<>();
        refundEvent.put("transactionId",transaction.getId());
        refundEvent.put("senderAccountNumber",transaction.getSenderAccountNumber());
        refundEvent.put("amount",transaction.getAmount());
        refundEvent.put("reason",reason);

        kafkaTemplate.send(TRANSACTION_REFUNDED_TOPIC,transaction.getId(),refundEvent);

        log.info("SAGA Compensation complete -{} refunded to {}",
                transaction.getAmount(),transaction.getSenderAccountNumber());

    }

    private void blockAccountAndCompensate(Transaction transaction, String reason) {

        //Publish fraud.detected -> Account Service will block account
        Map<String,Object> fraudEvent = new HashMap<>();
        fraudEvent.put("transactionId",transaction.getId());
        fraudEvent.put("accountNumber",transaction.getSenderAccountNumber());
        fraudEvent.put("reason",reason);

        kafkaTemplate.send(FRAUD_DETECTED,transaction.getSenderAccountNumber(),fraudEvent);

        log.warn("fraud.detected published -account : {} will be blocked kinly contact your bank",transaction.getSenderAccountNumber());


        //SAGA COMPENSATION  - refund sender
        compensateTransaction(transaction,reason);

    }

    private void completeTransaction(Transaction transaction){

        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());
        transactionRepo.save(transaction);

        TransactionCompletedEvent completedEvent = new TransactionCompletedEvent();
        completedEvent.setTransactionID(transaction.getId());
        completedEvent.setSenderAccountNumber(transaction.getSenderAccountNumber());
        completedEvent.setReceiverAccountNumber(transaction.getReceiverAccountNumber());
        completedEvent.setAmount(transaction.getAmount());
        completedEvent.setDescription(transaction.getDescription());

        kafkaTemplate.send(TRANSACTION_COMPLETED_TOPIC,transaction.getId(),completedEvent);

        log.info("SAGA COMPLETED - Transaction {} completed",
                transaction.getId());

    }

    /**
     * Method used on the transactionEvent consumeFraudCheckCleanResult
     * @param transactionId
     */
    public void processCleanResult(String transactionId) {
        Transaction transaction = transactionRepo.findById(transactionId)
                .orElseThrow(()-> new RuntimeException("Transaction not found : "+transactionId));

        //IdempotencyGuard
        if(transaction.getStatus()!=TransactionStatus.PROCESSING){
            log.warn("Transaction {} not Processing - skipping",transactionId);
            return;
        }
        //As the transaction is clean without any fraud
        //Complete the transaction
        completeTransaction(transaction);
    }

    private TransactionResponse mapToResponse(Transaction savedTransaction) {

        TransactionResponse response = new TransactionResponse();

        response.setId(savedTransaction.getId());
        response.setSenderAccountNumber(
                savedTransaction.getSenderAccountNumber()
        );
        response.setReceiverAccountNumber(
                savedTransaction.getReceiverAccountNumber()
        );
        response.setAmount(savedTransaction.getAmount());
        response.setType(savedTransaction.getType());
        response.setStatus(savedTransaction.getStatus());
        response.setDescription(savedTransaction.getDescription());
        response.setFailureReason(savedTransaction.getFailureReason());
        response.setReferenceNumber(
                savedTransaction.getReferenceNumber()
        );
        response.setCreatedAt(savedTransaction.getCreatedAt());
        response.setCompletedAt(savedTransaction.getCompletedAt());

        return response;
    }



}
