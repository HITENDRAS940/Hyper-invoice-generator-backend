package com.hyper.invoicebackend.service;

import com.hyper.invoicebackend.dto.InvoiceRequestDTO;
import com.hyper.invoicebackend.dto.InvoiceResponseDTO;
import com.hyper.invoicebackend.entity.Invoice;
import com.hyper.invoicebackend.exception.ResourceNotFoundException;
import com.hyper.invoicebackend.repository.InvoiceRepository;
import com.hyper.invoicebackend.util.InvoiceNumberGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final PdfGeneratorService pdfGeneratorService;
    private final CloudinaryService cloudinaryService;
    private final InvoiceNumberGenerator invoiceNumberGenerator;

    /**
     * Full invoice generation flow:
     * 1. Generate unique invoice number
     * 2. Build Thymeleaf context with invoice data
     * 3. Generate PDF from invoice.html template
     * 4. Upload PDF to Cloudinary
     * 5. Save invoice record to PostgreSQL
     * 6. Return response with Cloudinary URL
     */
    @Transactional
    public InvoiceResponseDTO generateInvoice(InvoiceRequestDTO request) {
        log.info("Starting invoice generation for customer: {}", request.getCustomerName());

        // Step 1: Generate unique invoice number
        String invoiceNumber = invoiceNumberGenerator.generate();
        log.info("Generated invoice number: {}", invoiceNumber);

        // Step 2: Build Thymeleaf context
        Context context = buildThymeleafContext(request, invoiceNumber);

        // Step 3: Generate PDF
        byte[] pdfBytes = pdfGeneratorService.generatePdf("invoice", context);
        log.info("PDF generated. Size: {} bytes", pdfBytes.length);

        // Step 4: Upload to Cloudinary
        String cloudinaryUrl = cloudinaryService.uploadPdf(pdfBytes, invoiceNumber);

        // Step 5: Save to database
        Invoice invoice = Invoice.builder()
                .invoiceNumber(invoiceNumber)
                .bookingId(request.getBookingId())
                .customerName(request.getCustomerName())
                .customerEmail(request.getCustomerEmail())
                .amount(request.getAmount())
                .invoiceDate(request.getInvoiceDate())
                .cloudinaryUrl(cloudinaryUrl)
                .build();

        Invoice savedInvoice = invoiceRepository.save(invoice);
        log.info("Invoice saved to database with ID: {}", savedInvoice.getId());

        // Step 6: Return response
        return InvoiceResponseDTO.builder()
                .id(savedInvoice.getId())
                .invoiceNumber(savedInvoice.getInvoiceNumber())
                .customerName(savedInvoice.getCustomerName())
                .customerEmail(savedInvoice.getCustomerEmail())
                .amount(savedInvoice.getAmount())
                .cloudinaryUrl(savedInvoice.getCloudinaryUrl())
                .createdAt(savedInvoice.getCreatedAt())
                .message("Invoice generated successfully!")
                .build();
    }

    /**
     * Retrieves an invoice by its invoice number.
     */
    public InvoiceResponseDTO getInvoiceByNumber(String invoiceNumber) {
        Invoice invoice = invoiceRepository.findByInvoiceNumber(invoiceNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "invoiceNumber", invoiceNumber));

        return InvoiceResponseDTO.builder()
                .id(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .customerName(invoice.getCustomerName())
                .customerEmail(invoice.getCustomerEmail())
                .amount(invoice.getAmount())
                .cloudinaryUrl(invoice.getCloudinaryUrl())
                .createdAt(invoice.getCreatedAt())
                .build();
    }

    /**
     * Retrieves all invoices.
     */
    public List<InvoiceResponseDTO> getAllInvoices() {
        return invoiceRepository.findAll().stream()
                .map(invoice -> InvoiceResponseDTO.builder()
                        .id(invoice.getId())
                        .invoiceNumber(invoice.getInvoiceNumber())
                        .customerName(invoice.getCustomerName())
                        .customerEmail(invoice.getCustomerEmail())
                        .amount(invoice.getAmount())
                        .cloudinaryUrl(invoice.getCloudinaryUrl())
                        .createdAt(invoice.getCreatedAt())
                        .build())
                .toList();
    }

    private Context buildThymeleafContext(InvoiceRequestDTO request, String invoiceNumber) {
        Context context = new Context();
        context.setVariable("invoiceNumber", invoiceNumber);
        context.setVariable("customerName", request.getCustomerName());
        context.setVariable("customerEmail", request.getCustomerEmail());
        context.setVariable("amount", request.getAmount());
        context.setVariable("invoiceDate", request.getInvoiceDate());
        context.setVariable("bookingId", request.getBookingId());
        context.setVariable("lineItems", request.getLineItems());
        return context;
    }
}

