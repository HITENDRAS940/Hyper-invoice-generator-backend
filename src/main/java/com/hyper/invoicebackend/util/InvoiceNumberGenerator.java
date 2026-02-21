package com.hyper.invoicebackend.util;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
public class InvoiceNumberGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * Generates a unique invoice number in the format: INV-YYYYMMDD-XXXXXXXX
     * Example: INV-20260221-A1B2C3D4
     */
    public String generate() {
        String datePart = LocalDate.now().format(DATE_FORMATTER);
        String uniquePart = UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase();
        return "INV-" + datePart + "-" + uniquePart;
    }
}

