package com.banking.frauddetectionservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class FraudEventDetectionConsumer {

    private final FraudDetectionService fraudDetectionService;


    /**
     * Listens to transaction.initiated topic
     * And every transaction goes through fraud check before completing
     * @param payload
     */
    @KafkaListener(topics = "transaction.initiated")
    public void consumeTransactionInitiated(
            @Payload Map<String,Object> payload)
    {
        log.info("Received Transaction for Fraud Check : {} ",payload.get("transactionID"));

        try{
            fraudDetectionService.checkTransaction(payload);
        }
        catch (Exception e){
            log.error("Checking fraud detection for transaction : {}",payload.get("transactionID"),e);
        }
    }

}
