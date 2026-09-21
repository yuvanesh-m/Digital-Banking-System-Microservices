package com.banking.transactionservice.service;


import com.banking.transactionservice.entity.Transaction;
import com.banking.transactionservice.entity.TransactionStatus;
import com.banking.transactionservice.repo.TransactionRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionEventConsumer {

    private final TransactionRepo transactionRepo;
    private final TransactionService transactionService;

    private final KafkaTemplate<String,Object> kafkaTemplate;
    private static final String TRANSACTION_OTP_GENERATED_TOPIC = "transaction.otp.generated";

    private final RedisTemplate<String,String> redisTemplate;
    private static final long otpExpiryMinutes = 5;

    /**
     * Consume verfication.required event arisen from fraud-detection-service
     * Generate OTP and ask user to verify
     * @param payload
     */
    @KafkaListener(topics = "verification.required")
    public void consumeVerificationRequired(
            @Payload Map<String,Object> payload)
    {
        try{
            String transactionId = (String) payload.get("transactionId");
            String accountNumber = (String) payload.get("accountNumber");
            BigDecimal amount =
                    new BigDecimal(payload.get("amount").toString());
            String reason =  (String) payload.get("reason");


            log.info("Verification required - transaction : {} reason: {}",
                    transactionId,reason);

            Transaction transaction = transactionRepo.findById(transactionId)
                    .orElseThrow(()-> new RuntimeException("Transaction not found "+transactionId));

            if(transaction.getStatus() != TransactionStatus.PROCESSING){
                log.warn("Transaction {} not PROCESSING - skipping" , transactionId);
                return;
            }

            //If its Processing generate OTP

            String otp = String.format("%06d",(int) (Math.random()*900000)+100000);

            //Store OTP in redis - expires in 5 minutes;

            String otpKey = "verification:otp"+transactionId;

            redisTemplate.opsForValue().set(otpKey,otp);
            redisTemplate.expire(otpKey,otpExpiryMinutes, TimeUnit.MINUTES);


            //Update the status
            transaction.setStatus(TransactionStatus.PENDING_VERIFICATION);
            transactionRepo.save(transaction);

            log.info("OTP generated for transaction : {} and expires in {} minute",transactionId,otpExpiryMinutes);

            //OTP generated and stored on redis
            //Next User need to notified by notification service for verifying otp by calling transaction-service(verifyOtp())

            //Notify User
            Map<String,Object> otpEvent = new HashMap<>();
            otpEvent.put("transactionId",transactionId);
            otpEvent.put("otp",otp);
            otpEvent.put("accountNumber",accountNumber);
            otpEvent.put("amount",amount);
            otpEvent.put("reason",reason);

            kafkaTemplate.send(TRANSACTION_OTP_GENERATED_TOPIC , transactionId , otpEvent);

        }
        catch (Exception e){
            log.error("Error Handling verification required : {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "fraud.check.clean")
    public void consumeFraudCheckCleanResult(
            @Payload  Map<String,Object> payload
    ){
        try{

            String transactionId = (String) payload.get("transactionId");
            transactionService.processCleanResult(transactionId);
        }
        catch (Exception e){
            log.error("Error Processing fraud check result: {}",e.getMessage());
        }
    }
}
