package com.example.bankapp.controller;

import com.example.bankapp.dto.TransferRequest;
import com.example.bankapp.exception.BankException;
import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.Customer;
import com.example.bankapp.model.TransactionStatus;
import com.example.bankapp.service.BankService;
import com.example.bankapp.service.TransactionAuditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class BankController {

    private final BankService bankService;
    private final TransactionAuditService auditService;

    @GetMapping("/")
    public String dashboard(@RequestParam(name = "keyword", required = false) String keyword, Model model) {
        loadDashboard(model, keyword);
        return "bank";
    }

    @PostMapping("/customers")
    public String addCustomer(@Valid @ModelAttribute("customer") Customer customer, BindingResult result,
                              Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            loadDashboard(model, null);
            return "bank";
        }
        try {
            bankService.createCustomer(customer);
            redirect.addFlashAttribute("success", "Customer " + customer.getFullName() + " added.");
        } catch (BankException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/transfer")
    public String transfer(@Valid @ModelAttribute("transferRequest") TransferRequest request, BindingResult result,
                           Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            loadDashboard(model, null);
            return "bank";
        }
        try {
            BankTransaction tx = bankService.transfer(request.getFromAccount(), request.getToAccount(),
                    request.getAmount(), request.isSimulateFailure());
            redirect.addFlashAttribute("success", "Transfer of LKR " + tx.getAmount().toPlainString() + " completed.");
        } catch (Exception e) {
            // runs AFTER the rollback; FAILED row saved in a new transaction
            auditService.recordFailure(request.getFromAccount(), request.getToAccount(),
                    request.getAmount(), e.getMessage());
            redirect.addFlashAttribute("error", "Transfer failed: " + e.getMessage() + ". All changes were rolled back.");
        }
        return "redirect:/";
    }

    @GetMapping("/accounts/{accountNumber}/history")
    public String history(@PathVariable("accountNumber") String accountNumber, Model model) {
        model.addAttribute("customer", bankService.getCustomer(accountNumber));
        model.addAttribute("transactions", bankService.getHistory(accountNumber));
        model.addAttribute("totalSent", bankService.getTotalSent(accountNumber));
        return "history";
    }

    @GetMapping("/large-transfers")
    public String largeTransfers(@RequestParam(name = "min", defaultValue = "10000") BigDecimal min, Model model) {
        model.addAttribute("min", min);
        model.addAttribute("transfers", bankService.getLargeTransfers(min));
        return "large-transfers";
    }

    @GetMapping("/deposit")
    public String depositPage(Model model) {
        model.addAttribute("customers", customersWithoutBank());
        return "deposit";
    }

    @PostMapping("/deposit")
    public String deposit(@RequestParam(name = "accountNumber", required = false) String accountNumber,
                          @RequestParam(name = "amount", required = false) BigDecimal amount,
                          RedirectAttributes redirect) {
        try {
            bankService.deposit(accountNumber, amount);
            redirect.addFlashAttribute("success", "Deposited LKR " + amount.toPlainString() + " to " + accountNumber);
        } catch (Exception e) {
            auditService.recordFailure(null, accountNumber, amount, "Deposit failed: " + e.getMessage());
            redirect.addFlashAttribute("error", "Deposit failed: " + e.getMessage());
        }
        return "redirect:/deposit";
    }

    @GetMapping("/withdraw")
    public String withdrawPage(Model model) {
        model.addAttribute("customers", customersWithoutBank());
        return "withdraw";
    }

    @PostMapping("/withdraw")
    public String withdraw(@RequestParam(name = "accountNumber", required = false) String accountNumber,
                           @RequestParam(name = "amount", required = false) BigDecimal amount,
                           RedirectAttributes redirect) {
        try {
            bankService.withdraw(accountNumber, amount);
            redirect.addFlashAttribute("success", "Withdrew LKR " + amount.toPlainString() + " from " + accountNumber);
        } catch (Exception e) {
            auditService.recordFailure(accountNumber, null, amount, "Withdrawal failed: " + e.getMessage());
            redirect.addFlashAttribute("error", "Withdrawal failed: " + e.getMessage());
        }
        return "redirect:/withdraw";
    }

    private void loadDashboard(Model model, String keyword) {
        model.addAttribute("keyword", keyword);
        model.addAttribute("customers", bankService.getCustomers(keyword));
        model.addAttribute("allCustomers", bankService.getAllCustomers());
        model.addAttribute("transactions", bankService.getLatestTransactions(20));
        model.addAttribute("totalCustomers", bankService.countCustomers());
        model.addAttribute("totalBalance", bankService.getTotalBalance());
        model.addAttribute("successCount", bankService.countByStatus(TransactionStatus.SUCCESS));
        model.addAttribute("failedCount", bankService.countByStatus(TransactionStatus.FAILED));
        if (!model.containsAttribute("transferRequest")) model.addAttribute("transferRequest", new TransferRequest());
        if (!model.containsAttribute("customer")) model.addAttribute("customer", new Customer());
    }

    private List<Customer> customersWithoutBank() {
        return bankService.getAllCustomers().stream()
                .filter(c -> !BankService.BANK_FEE_ACCOUNT.equals(c.getAccountNumber()))
                .toList();
    }
}