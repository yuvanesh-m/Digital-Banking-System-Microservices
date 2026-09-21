package com.banking.accountservice.controller;

import com.banking.accountservice.dto.AccountResponse;
import com.banking.accountservice.dto.CreateAccountRequest;
import com.banking.accountservice.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/accounts")
@Slf4j
@RequiredArgsConstructor
public class AccountController {

    @Autowired
    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody CreateAccountRequest request){

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.createAccount(request));
    }

    @GetMapping("/{accountNumber}")
    public ResponseEntity<AccountResponse> getAccount(
            @PathVariable String accountNumber) {

        return ResponseEntity.ok(accountService.getAccount(accountNumber));
    }

    @GetMapping("/getAccount")
    public ResponseEntity<AccountResponse> getAccountByPhoneNumber(@RequestParam String ph){
        return ResponseEntity.ok(accountService.getAccountByPhoneNumber(ph));
    }

    @GetMapping("/{accountNumber}/balance")
    public ResponseEntity<BigDecimal> getBalance(
            @PathVariable String accountNumber) {

        return ResponseEntity.ok(accountService.getBalance(accountNumber));
    }

    @PutMapping("{accountNumber}/block")
    public ResponseEntity<String> blockAccount(
            @PathVariable String accountNumber)
    {
        accountService.blockAccount(accountNumber);
        return ResponseEntity.ok("Account Blocked Successfully");
    }

    /*
     * SAGA STEP 1  - Deduct Balance
     * Called by Transaction Service when transfer is initiated
     */

    @PutMapping("{accountNumber}/deduct")
    public ResponseEntity<String> deductBalance(
            @PathVariable String accountNumber,
            @RequestParam BigDecimal amount ){
        accountService.deductBalance(accountNumber,amount);
        return ResponseEntity.ok("Balance Deducted Successfully");
    }

    /*
     * SAGA STEP 4  - Compensating transaction endpoint
     * Called by Transaction Service in Two Scenarios
     * 1. Fraud Detected
     * 2. Transaction Completed ->Credit Receiver
     */
    @PutMapping("{accountNumber}/credit")
    public ResponseEntity<String> creditBalance(
            @PathVariable String accountNumber,
            @RequestParam  BigDecimal amount){
        accountService.creditBalance(accountNumber,amount);
        return ResponseEntity.ok("Balance credited successfully");
    }


}
