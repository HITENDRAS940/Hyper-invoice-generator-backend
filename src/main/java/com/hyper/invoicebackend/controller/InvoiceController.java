package com.hyper.invoicebackend.controller;

import com.hyper.invoicebackend.dto.InvoiceRequestDTO;
import com.hyper.invoicebackend.dto.InvoiceResponseDTO;
import com.hyper.invoicebackend.service.InvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    /**
     * POST /api/invoices/generate
     * Generates a new invoice, uploads to Cloudinary, saves to DB, returns URL.
     */
    @PostMapping("/generate")
    public ResponseEntity<InvoiceResponseDTO> generateInvoice(
            @Valid @RequestBody InvoiceRequestDTO request) {
        log.info("Received invoice generation request for customer: {}", request.getCustomerName());
        InvoiceResponseDTO response = invoiceService.generateInvoice(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/invoices/{invoiceNumber}
     * Retrieves invoice details by invoice number.
     */
    @GetMapping("/{invoiceNumber}")
    public ResponseEntity<InvoiceResponseDTO> getInvoice(@PathVariable String invoiceNumber) {
        log.info("Fetching invoice with number: {}", invoiceNumber);
        InvoiceResponseDTO response = invoiceService.getInvoiceByNumber(invoiceNumber);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/invoices
     * Retrieves all invoices.
     */
    @GetMapping
    public ResponseEntity<List<InvoiceResponseDTO>> getAllInvoices() {
        log.info("Fetching all invoices");
        List<InvoiceResponseDTO> invoices = invoiceService.getAllInvoices();
        return ResponseEntity.ok(invoices);
    }
}

