package com.hyper.invoicebackend.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Component
public class InvoiceNumberGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * Generates a unique invoice number in the format: INV-YYYYMMDD-XXXXXXXX
     * Example: INV-20260221-A1B2C3D4
     */
    public String generate() {
        log.debug("[InvoiceNumberGenerator] Generating invoice number...");
        String datePart = LocalDate.now().format(DATE_FORMATTER);
        String uniquePart = UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase();
        String invoiceNumber = "INV-" + datePart + "-" + uniquePart;
        log.info("[InvoiceNumberGenerator] Generated invoice number: {}", invoiceNumber);
        return invoiceNumber;
    }
}

