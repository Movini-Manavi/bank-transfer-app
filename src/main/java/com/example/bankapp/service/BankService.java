package com.example.bankapp.service;

import com.example.bankapp.exception.AccountNotFoundException;
import com.example.bankapp.exception.BankException;
import com.example.bankapp.exception.InsufficientBalanceException;
import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.Customer;
import com.example.bankapp.model.TransactionStatus;
import com.example.bankapp.repository.BankTransactionRepository;
import com.example.bankapp.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BankService {

    public static final String BANK_FEE_ACCOUNT = "999999";
    public static final BigDecimal FEE_RATE = new BigDecimal("0.01");

    private final CustomerRepository customerRepository;
    private final BankTransactionRepository transactionRepository;

    // ===== TASK 10 EXPERIMENT: no rollback =====
    // Normal version (put this back after the experiment):
    @Transactional(rollbackFor = Exception.class)
    //@Transactional(noRollbackFor = Exception.class)
    public BankTransaction transfer(String fromAccount, String toAccount,
                                    BigDecimal amount, boolean simulateFailure) {
        // 1. validate
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new BankException("Amount must be greater than zero");
        if (fromAccount == null || fromAccount.equals(toAccount))
            throw new BankException("Sender and receiver must be different accounts");
        if (BANK_FEE_ACCOUNT.equals(fromAccount) || BANK_FEE_ACCOUNT.equals(toAccount))
            throw new BankException("The bank fee account cannot be used for transfers");
        if (customerRepository.countAccounts(fromAccount) == 0)
            throw new AccountNotFoundException(fromAccount);
        if (customerRepository.countAccounts(toAccount) == 0)
            throw new AccountNotFoundException(toAccount);

        BigDecimal fee = amount.multiply(FEE_RATE).setScale(2, RoundingMode.HALF_UP);

        // 2. debit sender (amount + 1% fee)
        if (customerRepository.debit(fromAccount, amount.add(fee)) == 0)
            throw new InsufficientBalanceException(fromAccount);

        // 3. simulated failure
        if (simulateFailure)
            throw new BankException("Simulated system failure after the withdrawal");

        // 4. credit receiver + fee to bank account
        customerRepository.credit(toAccount, amount);
        if (customerRepository.credit(BANK_FEE_ACCOUNT, fee) == 0)
            throw new AccountNotFoundException(BANK_FEE_ACCOUNT);

        // 5. save SUCCESS record
        BankTransaction tx = new BankTransaction(fromAccount, toAccount, amount,
                TransactionStatus.SUCCESS, "Transfer completed (fee LKR " + fee.toPlainString() + ")");
        tx.setFee(fee);
        return transactionRepository.save(tx);
    }

    @Transactional(rollbackFor = Exception.class)
    public BankTransaction deposit(String accountNumber, BigDecimal amount) {
        checkAmount(amount);
        if (customerRepository.credit(accountNumber, amount) == 0)
            throw new AccountNotFoundException(accountNumber);
        return transactionRepository.save(new BankTransaction(null, accountNumber, amount,
                TransactionStatus.SUCCESS, "Cash deposit"));
    }

    @Transactional(rollbackFor = Exception.class)
    public BankTransaction withdraw(String accountNumber, BigDecimal amount) {
        checkAmount(amount);
        if (customerRepository.countAccounts(accountNumber) == 0)
            throw new AccountNotFoundException(accountNumber);
        if (customerRepository.debit(accountNumber, amount) == 0)
            throw new InsufficientBalanceException(accountNumber);
        return transactionRepository.save(new BankTransaction(accountNumber, null, amount,
                TransactionStatus.SUCCESS, "Cash withdrawal"));
    }

    private void checkAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new BankException("Amount must be greater than zero");
    }

    @Transactional
    public Customer createCustomer(Customer customer) {
        if (customerRepository.countAccounts(customer.getAccountNumber()) > 0)
            throw new BankException("Account number " + customer.getAccountNumber() + " already exists");
        if (customerRepository.countEmails(customer.getEmail()) > 0)
            throw new BankException("Email " + customer.getEmail() + " already exists");
        customer.setId(null);
        return customerRepository.save(customer);
    }

    public List<Customer> getCustomers(String keyword) {
        if (keyword == null || keyword.isBlank()) return customerRepository.findAllOrdered();
        return customerRepository.searchByName(keyword.trim());
    }

    public List<Customer> getAllCustomers() {
        return customerRepository.findAllOrdered();
    }

    public Customer getCustomer(String accountNumber) {
        return customerRepository.getByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
    }

    public BigDecimal getTotalBalance() {
        BigDecimal t = customerRepository.getTotalBalance();
        return t != null ? t : BigDecimal.ZERO;
    }

    public long countCustomers() {
        return customerRepository.countCustomersNative();
    }

    public long countByStatus(TransactionStatus status) {
        return transactionRepository.countByStatus(status);
    }

    public List<BankTransaction> getLatestTransactions(int limit) {
        List<BankTransaction> all = transactionRepository.findAllLatest();
        return all.size() > limit ? all.subList(0, limit) : all;
    }

    public List<BankTransaction> getHistory(String accountNumber) {
        return transactionRepository.findHistory(accountNumber);
    }

    public List<BankTransaction> getLargeTransfers(BigDecimal min) {
        return transactionRepository.findLargeTransfers(min, TransactionStatus.SUCCESS);
    }

    public BigDecimal getTotalSent(String accountNumber) {
        BigDecimal t = transactionRepository.getTotalSent(accountNumber, TransactionStatus.SUCCESS);
        return t != null ? t : BigDecimal.ZERO;
    }
}