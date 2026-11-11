package com.example.bankapp.repository;

import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {

    @Query("SELECT t FROM BankTransaction t ORDER BY t.createdAt DESC, t.id DESC")
    List<BankTransaction> findAllLatest();

    @Query("SELECT t FROM BankTransaction t WHERE t.fromAccount = :accountNumber OR t.toAccount = :accountNumber ORDER BY t.createdAt DESC, t.id DESC")
    List<BankTransaction> findHistory(@Param("accountNumber") String accountNumber);

    @Query("SELECT t FROM BankTransaction t WHERE t.status = :status ORDER BY t.createdAt DESC, t.id DESC")
    List<BankTransaction> findByStatusLatest(@Param("status") TransactionStatus status);

    @Query("SELECT COUNT(t) FROM BankTransaction t WHERE t.status = :status")
    long countByStatus(@Param("status") TransactionStatus status);

    // Task 12: transfers above a given amount
    @Query("SELECT t FROM BankTransaction t WHERE t.status = :status AND t.amount > :min " +
           "AND t.fromAccount IS NOT NULL AND t.toAccount IS NOT NULL ORDER BY t.amount DESC, t.id DESC")
    List<BankTransaction> findLargeTransfers(@Param("min") BigDecimal min, @Param("status") TransactionStatus status);

    // Task 12: total amount sent by an account
    @Query("SELECT SUM(t.amount + COALESCE(t.fee, 0)) FROM BankTransaction t " +
           "WHERE t.fromAccount = :account AND t.toAccount IS NOT NULL AND t.status = :status")
    BigDecimal getTotalSent(@Param("account") String account, @Param("status") TransactionStatus status);
}