package com.example.bankapp.service;

import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.TransactionStatus;
import com.example.bankapp.repository.BankTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class TransactionAuditService {

    private final BankTransactionRepository transactionRepository;

    // REQUIRES_NEW: own transaction, so the FAILED row survives the transfer rollback
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BankTransaction recordFailure(String fromAccount, String toAccount,
                                         BigDecimal amount, String reason) {
        String msg = reason == null ? "Unknown error"
                : (reason.length() > 255 ? reason.substring(0, 255) : reason);
        BankTransaction tx = new BankTransaction(
                (fromAccount == null || fromAccount.isBlank()) ? null : fromAccount,
                (toAccount == null || toAccount.isBlank()) ? null : toAccount,
                amount != null ? amount : BigDecimal.ZERO,
                TransactionStatus.FAILED, msg);
        return transactionRepository.save(tx);
    }
}