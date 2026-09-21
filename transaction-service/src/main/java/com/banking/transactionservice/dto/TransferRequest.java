package com.banking.transactionservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;


@Slf4j
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransferRequest {

    @NotBlank(message = "Sender account number is required")
    private String senderAccountNumber;

    @NotBlank(message = "Receiver account numebr is required")
    private String receiverAccountNumber;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount should be positive")
    private BigDecimal amount;


    private String description;



}
