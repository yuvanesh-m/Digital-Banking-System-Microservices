package com.banking.frauddetectionservice.service;

import com.banking.frauddetectionservice.client.AccountServiceClient;
import com.banking.frauddetectionservice.model.FraudCheckResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class FraudDetectionService {

    private final AccountServiceClient accountServiceClient;

    private final KafkaTemplate<String,Object> kafkaTemplate;

    private final RedisTemplate<String,String> redisTemplate;

    @Value("${fraud.max-transactions-per-minute}")
    private int maxTransactionsPerMinute;

    @Value("${fraud.suspicious-amount-multiplier}")
    private double suspiciousAmountMultiplier;

    @Value("${fraud.max-balance-percentage-allowed}")
    private double maxBalancePercentage;

    private static final String VERIFICATION_REQUIRED_TOPIC = "verification.required";
    private static final String FRAUD_CHECK_CLEAN_RESULT_TOPIC = "fraud.check.clean";



    public void checkTransaction(Map<String, Object> payload) {
        String transactionId = (String) payload.get("transactionID");
        String accountNumber = (String) payload.get("senderAccountNumber");
        BigDecimal amount = new BigDecimal(
                payload.get("amount").toString()
        );

        //Fetch Real Balance from Account Service

        BigDecimal senderBalance = accountServiceClient.getBalance(accountNumber);

        log.info("Checking transaction : {} account: {} amount : {} balance : {}"
        ,transactionId,accountNumber,amount,senderBalance);

        FraudCheckResult result = performFraudChecks(accountNumber,amount,senderBalance);

        //check for fraud
        //If detected publish the event verification.required
        //Verification by getting OTP from the user
        //so transaction service consumes this verification.required listen and generates OTP
        //Publish otp.generated event
        //This otp.generated event consumed by notification event
        //so user will send the otp , it was verified by calling verifyOTP on transaction-service
        if(result.isFraud()){
            log.info("Suspicious Activity detected - account {} reason {} -requesting OTP verification ",
                    accountNumber,result.getReason());
            Map<String,Object> verificationEvent  = new HashMap<>();

            verificationEvent.put("transactionId",transactionId);
            verificationEvent.put("accountNumber",accountNumber);
            verificationEvent.put("amount",amount);
            verificationEvent.put("reason",result.getReason());

            //Listened by TransactionService
            kafkaTemplate.send(VERIFICATION_REQUIRED_TOPIC,transactionId,verificationEvent);

        }
        else{
            //Transaction is clean
            log.info("Transaction clean");
            Map<String,Object> cleanEvent  = new HashMap<>();

            cleanEvent.put("transactionId",transactionId);
            cleanEvent.put("isFraud",false);
            cleanEvent.put("Reason",null);

            kafkaTemplate.send(FRAUD_CHECK_CLEAN_RESULT_TOPIC,transactionId,cleanEvent);

        }


    }

    private FraudCheckResult performFraudChecks(String accountNumber, BigDecimal amount, BigDecimal senderBalance) {

        // 3 pattern
        // check velocity check fraudsters ran script so they do multiple transaction(5) in 60 seconds
        // 3x or 5x greater than the average transaction amount of the user
        // Balance check the transaction amount is 90% of user balance

        if(isVelocityExceeded(accountNumber)){
            return new FraudCheckResult(true,"Too many transactions in 60 seconds"+
                                                            "Velocity limit exceeded");
        }

        if(isAmountSuspicious(accountNumber,amount)){
            return new FraudCheckResult(true,"Unusual transaction amount "+
                                                            "Exceeds 3x your average");
        }

        if(senderBalance.compareTo(BigDecimal.ZERO)> 0
                && isBalanceCheckFailed(senderBalance,amount)){
            return new FraudCheckResult(true,"Transaction exceeds 90% of account balance");
        }

        return new FraudCheckResult(false,null);

    }



    private boolean isVelocityExceeded(String accountNumber) {
        String key = "fraud:velocity" + accountNumber;
        Long count = redisTemplate.opsForValue().increment(key);

        if(count!=null && count==1){
            redisTemplate.expire(key,60, TimeUnit.SECONDS);
        }
        log.info("Velocity check - account : {} count {}/{}",accountNumber,count,maxTransactionsPerMinute);

        return count!=null && count > maxTransactionsPerMinute;
    }

    private boolean isAmountSuspicious(String accountNumber, BigDecimal amount) {
        String avgKey = "fraud:avg_amount" + accountNumber;
        String avgStr = redisTemplate.opsForValue().get(avgKey);

        if(avgStr == null){
            redisTemplate.opsForValue().set(avgKey,amount.toString());
            return false;
        }

        BigDecimal avgAmount = new BigDecimal(avgStr);
        BigDecimal threshold = avgAmount.multiply(BigDecimal.valueOf(suspiciousAmountMultiplier));

        //Calculating Running Average
        BigDecimal newAvg = avgAmount.add(amount).divide(BigDecimal.valueOf(2),2, RoundingMode.HALF_UP);

        redisTemplate.opsForValue().set(avgKey,newAvg.toString());

        log.info("Amount check - amount {} threshold: {} suspicious : {}",amount,threshold,amount.compareTo(threshold)>0);

        return amount.compareTo(threshold) > 0;

    }

    private boolean isBalanceCheckFailed(BigDecimal senderBalance, BigDecimal amount) {
        BigDecimal maxThresholdAmount = senderBalance.multiply(BigDecimal.valueOf(maxBalancePercentage)).divide(BigDecimal.valueOf(100),2,RoundingMode.HALF_UP);

        log.info("Balance check - amount:{} maxAllowed :{} suspicious :{} ",amount,maxThresholdAmount,amount.compareTo(maxThresholdAmount)>0);

        return amount.compareTo(maxThresholdAmount)>0;

    }
}
