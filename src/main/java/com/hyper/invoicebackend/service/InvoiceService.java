package com.hyper.invoicebackend.service;

import com.hyper.invoicebackend.client.BookingApiClient;
import com.hyper.invoicebackend.dto.BookingResponse;
import com.hyper.invoicebackend.dto.InvoiceRequestDTO;
import com.hyper.invoicebackend.dto.InvoiceResponseDTO;
import com.hyper.invoicebackend.util.AmountToWordsConverter;
import com.hyper.invoicebackend.util.InvoiceNumberGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final PdfGeneratorService pdfGeneratorService;
    private final CloudinaryService cloudinaryService;
    private final InvoiceNumberGenerator invoiceNumberGenerator;
    private final AmountToWordsConverter amountToWordsConverter;
    private final BookingApiClient bookingApiClient;

    /**
     * Invoice generation flow:
     * 1. Fetch booking data from external API
     * 2. Generate invoice number
     * 3. Render invoice.html → PDF
     * 4. Upload PDF to Cloudinary
     * 5. POST Cloudinary URL + bookingId to /invoice-receive
     * 6. Return URL in response
     */
    public InvoiceResponseDTO generateInvoice(InvoiceRequestDTO request) {
        log.info("Starting invoice generation for bookingId: {}", request.getBookingId());

        // Step 1: Fetch booking
        BookingResponse booking = bookingApiClient.getBooking(request.getBookingId());
        validateBooking(booking);

        // Step 2: Generate invoice number
        String invoiceNumber = invoiceNumberGenerator.generate();
        log.info("Generated invoice number: {}", invoiceNumber);

        // Step 3: Render HTML → PDF
        Context context = buildThymeleafContext(booking, invoiceNumber);
        byte[] pdfBytes = pdfGeneratorService.generatePdf("invoice", context);
        log.info("PDF generated. Size: {} bytes", pdfBytes.length);

        // Step 4: Upload to Cloudinary
        String cloudinaryUrl = cloudinaryService.uploadPdf(pdfBytes, invoiceNumber);
        log.info("PDF uploaded to Cloudinary: {}", cloudinaryUrl);

        // Step 5: Send URL back to booking service
        bookingApiClient.sendInvoiceUrl(booking.getId(), cloudinaryUrl);

        // Step 6: Return response
        return InvoiceResponseDTO.builder()
                .invoiceNumber(invoiceNumber)
                .cloudinaryUrl(cloudinaryUrl)
                .message("Invoice generated and delivered successfully!")
                .build();
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void validateBooking(BookingResponse booking) {
        if (booking == null) {
            throw new RuntimeException("Booking API returned empty response");
        }
        if (booking.getUser() == null) {
            throw new RuntimeException("Booking has no user information");
        }
        if (booking.getAmountBreakdown() == null) {
            throw new RuntimeException("Booking has no amount breakdown");
        }
    }

    private Context buildThymeleafContext(BookingResponse booking, String invoiceNumber) {
        BookingResponse.AmountBreakdown ab = booking.getAmountBreakdown();
        BookingResponse.UserInfo user      = booking.getUser();

        // Financial calculations
        BigDecimal amount        = safeAmount(booking);
        BigDecimal netAssessable = amount;
        BigDecimal gstRate       = new BigDecimal("0.18");
        BigDecimal gstAmount     = netAssessable.multiply(gstRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal invoiceTotal  = netAssessable.add(gstAmount);
        String totalInWords      = amountToWordsConverter.convert(invoiceTotal);

        // Single line item built from booking slot
        BookingLineItem lineItem = new BookingLineItem(
                booking.getServiceName() + " - " + booking.getResourceName(),
                booking.getStartTime() + " to " + booking.getEndTime(),
                1,
                ab.getSlotSubtotal() != null ? ab.getSlotSubtotal() : amount,
                BigDecimal.ZERO
        );

        Context context = new Context();

        // Invoice meta
        context.setVariable("invoiceNumber",       invoiceNumber);
        context.setVariable("invoiceDate",         booking.getBookingDate() != null ? booking.getBookingDate() : LocalDate.now());
        context.setVariable("bookingId",           booking.getId());
        context.setVariable("bookingReference",    booking.getReference());
        context.setVariable("documentType",        "INV");

        // Customer
        context.setVariable("customerName",        user.getName());
        context.setVariable("customerEmail",       user.getEmail());
        context.setVariable("customerPhone",       user.getPhone());
        context.setVariable("customerAddress",     null);

        // Venue / service
        context.setVariable("issuerName",          "HyperInvoice");
        context.setVariable("venueName",           booking.getResourceName());
        context.setVariable("serviceName",         booking.getServiceName());
        context.setVariable("venueGstin",          null);
        context.setVariable("venueAddress",        null);
        context.setVariable("venueState",          null);
        context.setVariable("placeOfSupply",       null);
        context.setVariable("gstin",               null);
        context.setVariable("sacCode",             null);

        // Slot details
        context.setVariable("startTime",           booking.getStartTime());
        context.setVariable("endTime",             booking.getEndTime());
        context.setVariable("bookingStatus",       booking.getStatus());
        context.setVariable("currency",            ab.getCurrency() != null ? ab.getCurrency() : "INR");

        // Financials
        context.setVariable("slotSubtotal",        ab.getSlotSubtotal());
        context.setVariable("platformFee",         ab.getPlatformFee());
        context.setVariable("platformFeePercent",  ab.getPlatformFeePercent());
        context.setVariable("onlineAmount",        ab.getOnlineAmount());
        context.setVariable("venueAmount",         ab.getVenueAmount());
        context.setVariable("amount",              amount);
        context.setVariable("discount",            BigDecimal.ZERO);
        context.setVariable("netAssessable",       netAssessable);
        context.setVariable("gstAmount",           gstAmount);
        context.setVariable("invoiceTotal",        invoiceTotal);
        context.setVariable("invoiceTotalInWords", totalInWords);
        context.setVariable("lineItems",           List.of(lineItem));

        return context;
    }

    private BigDecimal safeAmount(BookingResponse booking) {
        if (booking.getAmountBreakdown() != null
                && booking.getAmountBreakdown().getTotalAmount() != null) {
            return booking.getAmountBreakdown().getTotalAmount();
        }
        return BigDecimal.ZERO;
    }

    public record BookingLineItem(
            String description,
            String unitOfMeasure,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal discount
    ) {}
}
