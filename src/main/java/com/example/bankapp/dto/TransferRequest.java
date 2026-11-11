package com.example.bankapp.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class TransferRequest {

    @NotBlank(message = "Select the sender account")
    private String fromAccount;

    @NotBlank(message = "Select the receiver account")
    private String toAccount;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    @Digits(integer = 12, fraction = 2, message = "Maximum 12 digits and 2 decimal places")
    private BigDecimal amount;

    private boolean simulateFailure;
}