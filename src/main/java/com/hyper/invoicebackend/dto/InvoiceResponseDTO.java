package com.hyper.invoicebackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceResponseDTO {

    private Long id;
    private String invoiceNumber;
    private String customerName;
    private String customerEmail;
    private BigDecimal amount;
    private String cloudinaryUrl;
    private LocalDateTime createdAt;
    private String message;
}

