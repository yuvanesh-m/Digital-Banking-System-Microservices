package com.banking.accountservice.service;

import com.banking.accountservice.dto.AccountResponse;
import com.banking.accountservice.dto.CreateAccountRequest;
import com.banking.accountservice.entity.Account;
import com.banking.accountservice.entity.AccountStatus;
import com.banking.accountservice.entity.AccountType;
import com.banking.accountservice.repo.AccountRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;

@Service
@Slf4j
public class AccountService {

    @Autowired
    private AccountRepository accountRepository;
    private static final SecureRandom secureRandom = new SecureRandom();

    public AccountResponse createAccount(CreateAccountRequest request) {

        log.info("Creating account for : {} ",request.getEmail());

        if(accountRepository.existsByEmail(request.getEmail())){
            throw new RuntimeException("Account Already Exists for email :  "+request.getEmail());
        }
        Account account = new Account();
        account.setAccountHolderName(request.getAccountHolderName());
        account.setEmail(request.getEmail());
        account.setPhone(request.getPhone());
        account.setAccountType(request.getAccountType());
        account.setStatus(AccountStatus.ACTIVE);
        account.setBalance(request.getInitialDeposit());
        account.setAccountNumber(generateAccountNumber());
        account.setDailyTransactionLimit(
                request.getAccountType() == AccountType.SAVINGS ? new BigDecimal("100000")
                                                                : new BigDecimal("500000")
        );

        Account saveAccount = accountRepository.save(account);
        log.info("Account Created {}",saveAccount.getAccountNumber());

    return mapToResponse(saveAccount);

    }



    //Generate Unique 12-digit account number
    private String generateAccountNumber(){
        String accountNumber;

        do{
            long number = secureRandom.nextLong(1_000_000_000_000L);
            accountNumber = String.format("%012d",number); //Generating 12-digit Account Number
        }while(accountRepository.existsByAccountNumber(accountNumber)); //Checking the Account Number already there in db;

        return accountNumber;
    }

    public AccountResponse getAccount(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found"));
        return mapToResponse(account);
    }

    public AccountResponse getAccountByPhoneNumber(String ph) {
        Account account = accountRepository.findByPhone(ph)
                .orElseThrow(()-> new RuntimeException("Account Not Found"));

        return mapToResponse(account);
    }

    public BigDecimal getBalance(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                                            .orElseThrow(() -> new RuntimeException("Account not found"));
        return account.getBalance();
    }

    /*
     * Block account - called by Fraud Detection service via Kafka
     * @param accountNumber
     *
     */
    public void blockAccount(String accountNumber) {
        log.info("Blocking Account :{}",accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber)
                                            .orElseThrow(()->new RuntimeException("Account Not Found"));
        account.setStatus(AccountStatus.BLOCKED);
        accountRepository.save(account);

        log.info("Account Blocked : {}",accountNumber);
    }

    public void deductBalance(String accountNumber, BigDecimal amount) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                                            .orElseThrow(()->new RuntimeException("Account Not Found"));

        if(account.getStatus()!= AccountStatus.ACTIVE){
            throw new RuntimeException("Account is not active "+accountNumber);
        }
        if(account.getBalance().compareTo(amount)<0){
            throw new RuntimeException("Insufficient balance for account "+accountNumber);
        }

        account.setBalance(account.getBalance().subtract(amount));

        accountRepository.save(account);

        log.info("Balance updated new Balance : {} ",account.getBalance());

    }

    /*
     * Credit Balance
     * Called by Transaction Service via Kafka
     * @Param accountNumber
     * @Param amount
     */
    public void creditBalance(String accountNumber, BigDecimal amount) {
        log.info("Crediting {} to account to {}",amount,accountNumber);

        Account account = accountRepository.findByAccountNumber(accountNumber)
                                            .orElseThrow(()->new RuntimeException("Account Not Found"));

        account.setBalance(account.getBalance().add(amount));

        accountRepository.save(account);

        log.info("Balance Credited New Balance : {}",account.getBalance());
    }

    private AccountResponse mapToResponse(Account account){
        AccountResponse response = new AccountResponse();
        response.setId(account.getId());
        response.setAccountNumber(account.getAccountNumber());
        response.setAccountHolderName(account.getAccountHolderName());
        response.setEmail(account.getEmail());
        response.setPhone(account.getPhone());
        response.setAccountType(account.getAccountType());
        response.setStatus(account.getStatus());
        response.setBalance(account.getBalance());
        response.setDailyTransactionLimit(account.getDailyTransactionLimit());
        response.setCreatedAt(account.getCreatedAt());

        return response;
    }


}
