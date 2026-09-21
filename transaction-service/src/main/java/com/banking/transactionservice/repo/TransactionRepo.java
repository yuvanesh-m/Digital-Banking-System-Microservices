package com.banking.transactionservice.repo;

import com.banking.transactionservice.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TransactionRepo extends JpaRepository<Transaction,String> {

    @Query(""" 
               SELECT u 
               From Transaction u
               WHERE u.senderAccountNumber = :accountNumber 
               ORDER by u.createdAt desc 
               """)


    List<Transaction> findTransactionByAccountNumber(@Param("accountNumber") String accountNumber);
}
