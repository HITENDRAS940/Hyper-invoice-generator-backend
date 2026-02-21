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
        long totalStart = System.currentTimeMillis();
        log.info("========== [InvoiceService] START generateInvoice ==========");
        log.info("[InvoiceService] Input -> bookingId: {}", request.getBookingId());

        // ── Step 1: Fetch booking ────────────────────────────────────────────
        log.info("[InvoiceService] Step 1/6 -> Fetching booking data for bookingId: {}", request.getBookingId());
        long step1Start = System.currentTimeMillis();
        BookingResponse booking = bookingApiClient.getBooking(request.getBookingId());
        log.info("[InvoiceService] Step 1/6 -> Booking fetched in {} ms | id={}, reference={}, status={}, customer={}",
                System.currentTimeMillis() - step1Start,
                booking.getId(), booking.getReference(), booking.getStatus(),
                booking.getUser() != null ? booking.getUser().getName() : "N/A");

        // ── Validate booking ─────────────────────────────────────────────────
        log.debug("[InvoiceService] Validating booking data...");
        validateBooking(booking);
        log.info("[InvoiceService] Booking validation passed for bookingId: {}", booking.getId());

        // ── Step 2: Generate invoice number ──────────────────────────────────
        log.info("[InvoiceService] Step 2/6 -> Generating invoice number...");
        long step2Start = System.currentTimeMillis();
        String invoiceNumber = invoiceNumberGenerator.generate();
        log.info("[InvoiceService] Step 2/6 -> Invoice number generated in {} ms | invoiceNumber: {}",
                System.currentTimeMillis() - step2Start, invoiceNumber);

        // ── Step 3: Build Thymeleaf context ───────────────────────────────────
        log.info("[InvoiceService] Step 3a/6 -> Building Thymeleaf context...");
        long step3aStart = System.currentTimeMillis();
        Context context = buildThymeleafContext(booking, invoiceNumber);
        log.info("[InvoiceService] Step 3a/6 -> Thymeleaf context built in {} ms | variables count: {}",
                System.currentTimeMillis() - step3aStart, context.getVariableNames().size());
        log.debug("[InvoiceService] Step 3a/6 -> Context variables: {}", context.getVariableNames());

        // ── Step 3b: Render HTML → PDF ────────────────────────────────────────
        log.info("[InvoiceService] Step 3b/6 -> Rendering template 'invoice' and generating PDF...");
        long step3bStart = System.currentTimeMillis();
        byte[] pdfBytes = pdfGeneratorService.generatePdf("invoice", context);
        log.info("[InvoiceService] Step 3b/6 -> PDF generated in {} ms | size: {} bytes (~{} KB)",
                System.currentTimeMillis() - step3bStart,
                pdfBytes.length, pdfBytes.length / 1024);

        // ── Step 4: Upload to Cloudinary ──────────────────────────────────────
        log.info("[InvoiceService] Step 4/6 -> Uploading PDF to Cloudinary | publicId: invoices/{}",
                invoiceNumber);
        long step4Start = System.currentTimeMillis();
        String cloudinaryUrl = cloudinaryService.uploadPdf(pdfBytes, invoiceNumber);
        log.info("[InvoiceService] Step 4/6 -> PDF uploaded to Cloudinary in {} ms | url: {}",
                System.currentTimeMillis() - step4Start, cloudinaryUrl);

        // ── Step 5: Deliver URL to booking service ────────────────────────────
        log.info("[InvoiceService] Step 5/6 -> Sending invoice URL to booking service | bookingId: {}",
                booking.getId());
        long step5Start = System.currentTimeMillis();
        bookingApiClient.sendInvoiceUrl(booking.getId(), cloudinaryUrl);
        log.info("[InvoiceService] Step 5/6 -> Invoice URL delivered in {} ms",
                System.currentTimeMillis() - step5Start);

        // ── Step 6: Build and return response ────────────────────────────────
        log.info("[InvoiceService] Step 6/6 -> Building response DTO...");
        InvoiceResponseDTO response = InvoiceResponseDTO.builder()
                .invoiceNumber(invoiceNumber)
                .cloudinaryUrl(cloudinaryUrl)
                .message("Invoice generated and delivered successfully!")
                .build();

        log.info("[InvoiceService] Total invoice generation time: {} ms", System.currentTimeMillis() - totalStart);
        log.info("========== [InvoiceService] END generateInvoice | invoiceNumber: {} ==========", invoiceNumber);
        return response;
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void validateBooking(BookingResponse booking) {
        log.debug("[InvoiceService] Validating booking -> checking for null booking...");
        if (booking == null) {
            log.error("[InvoiceService] Booking validation FAILED -> API returned empty/null response");
            throw new RuntimeException("Booking API returned empty response");
        }
        log.debug("[InvoiceService] Validating booking -> checking for user info...");
        if (booking.getUser() == null) {
            log.error("[InvoiceService] Booking validation FAILED -> no user info for bookingId: {}", booking.getId());
            throw new RuntimeException("Booking has no user information");
        }
        log.debug("[InvoiceService] Validating booking -> checking for amount breakdown...");
        if (booking.getAmountBreakdown() == null) {
            log.error("[InvoiceService] Booking validation FAILED -> no amount breakdown for bookingId: {}", booking.getId());
            throw new RuntimeException("Booking has no amount breakdown");
        }
        log.debug("[InvoiceService] Booking validation OK -> id={}, user={}, totalAmount={}",
                booking.getId(),
                booking.getUser().getName(),
                booking.getAmountBreakdown().getTotalAmount());
    }

    private Context buildThymeleafContext(BookingResponse booking, String invoiceNumber) {
        log.debug("[InvoiceService] Building Thymeleaf context for invoiceNumber: {}", invoiceNumber);
        BookingResponse.AmountBreakdown ab = booking.getAmountBreakdown();
        BookingResponse.UserInfo user      = booking.getUser();

        // Financial calculations
        log.debug("[InvoiceService] Calculating financial values...");
        BigDecimal amount        = safeAmount(booking);
        BigDecimal netAssessable = amount;
        BigDecimal gstRate       = new BigDecimal("0.18");
        BigDecimal gstAmount     = netAssessable.multiply(gstRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal invoiceTotal  = netAssessable.add(gstAmount);
        log.debug("[InvoiceService] Financials -> baseAmount: {}, gstRate: {}%, gstAmount: {}, invoiceTotal: {}",
                amount, gstRate.multiply(new BigDecimal("100")).toPlainString(), gstAmount, invoiceTotal);

        String totalInWords = amountToWordsConverter.convert(invoiceTotal);
        log.debug("[InvoiceService] Invoice total in words: \"{}\"", totalInWords);

        // Single line item built from booking slot
        BookingLineItem lineItem = new BookingLineItem(
                booking.getServiceName() + " - " + booking.getResourceName(),
                booking.getStartTime() + " to " + booking.getEndTime(),
                1,
                ab.getSlotSubtotal() != null ? ab.getSlotSubtotal() : amount,
                BigDecimal.ZERO
        );
        log.debug("[InvoiceService] Line item -> description: '{}', slot: '{}', unitPrice: {}",
                lineItem.description(), lineItem.unitOfMeasure(), lineItem.unitPrice());

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

        log.debug("[InvoiceService] Thymeleaf context populated -> customer: {}, service: {}, resource: {}, currency: {}, total: {}",
                user.getName(), booking.getServiceName(), booking.getResourceName(),
                ab.getCurrency() != null ? ab.getCurrency() : "INR", invoiceTotal);

        return context;
    }

    private BigDecimal safeAmount(BookingResponse booking) {
        if (booking.getAmountBreakdown() != null
                && booking.getAmountBreakdown().getTotalAmount() != null) {
            BigDecimal amount = booking.getAmountBreakdown().getTotalAmount();
            log.debug("[InvoiceService] safeAmount -> using totalAmount from amountBreakdown: {}", amount);
            return amount;
        }
        log.warn("[InvoiceService] safeAmount -> totalAmount is null for bookingId: {}; defaulting to ZERO", booking.getId());
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
