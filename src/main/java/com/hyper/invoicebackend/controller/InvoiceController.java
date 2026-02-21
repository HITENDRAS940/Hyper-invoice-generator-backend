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

@Slf4j
@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    /**
     * POST /api/invoices/generate
     * Accepts a bookingId, generates the invoice PDF, uploads to Cloudinary,
     * delivers the URL to /invoice-receive, and returns the URL.
     */
    @PostMapping("/generate")
    public ResponseEntity<InvoiceResponseDTO> generateInvoice(
            @Valid @RequestBody InvoiceRequestDTO request) {
        log.info("Received invoice generation request for bookingId: {}", request.getBookingId());
        InvoiceResponseDTO response = invoiceService.generateInvoice(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
