package com.hyper.invoicebackend.client;

import com.hyper.invoicebackend.dto.BookingResponse;
import com.hyper.invoicebackend.dto.InvoiceReceiveRequest;
import com.hyper.invoicebackend.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class BookingApiClient {

    private final RestClient restClient;

    public BookingApiClient(
            RestClient.Builder builder,
            @Value("${booking.api.base-url:https://hyper-render-prod.onrender.com}") String baseUrl) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .build();
        log.info("[BookingApiClient] Initialized with baseUrl: {}", baseUrl);
    }

    /**
     * Fetches booking details from the external Hyper booking service.
     *
     * @param bookingId the booking ID
     * @return BookingResponse with full booking + user + amount data
     * @throws ResourceNotFoundException if the booking is not found (404)
     */
    public BookingResponse getBooking(Long bookingId) {
        log.info("[BookingApiClient] --> GET /services/booking/{} | Calling external booking API...", bookingId);
        long start = System.currentTimeMillis();
        try {
            BookingResponse response = restClient.get()
                    .uri("/services/booking/{bookingId}", bookingId)
                    .retrieve()
                    .body(BookingResponse.class);

            long elapsed = System.currentTimeMillis() - start;
            if (response != null) {
                log.info("[BookingApiClient] <-- GET /services/booking/{} responded in {} ms | " +
                                "reference: {}, status: {}, customer: {}, email: {}, totalAmount: {}",
                        bookingId, elapsed,
                        response.getReference(),
                        response.getStatus(),
                        response.getUser() != null ? response.getUser().getName() : "N/A",
                        response.getUser() != null ? response.getUser().getEmail() : "N/A",
                        response.getAmountBreakdown() != null ? response.getAmountBreakdown().getTotalAmount() : "N/A");
                log.debug("[BookingApiClient] Full booking details -> serviceName: {}, resourceName: {}, startTime: {}, endTime: {}",
                        response.getServiceName(), response.getResourceName(),
                        response.getStartTime(), response.getEndTime());
            } else {
                log.warn("[BookingApiClient] <-- GET /services/booking/{} responded in {} ms | body is NULL",
                        bookingId, elapsed);
            }

            return response;

        } catch (HttpClientErrorException.NotFound e) {
            log.error("[BookingApiClient] <-- GET /services/booking/{} responded in {} ms | 404 NOT FOUND",
                    bookingId, System.currentTimeMillis() - start);
            throw new ResourceNotFoundException("Booking", "id", bookingId);
        } catch (Exception e) {
            log.error("[BookingApiClient] <-- GET /services/booking/{} FAILED after {} ms | error: {}",
                    bookingId, System.currentTimeMillis() - start, e.getMessage(), e);
            throw new RuntimeException("Failed to fetch booking details: " + e.getMessage(), e);
        }
    }

    /**
     * Sends the generated Cloudinary invoice URL back to the booking service.
     * POST /invoice-receive  { "bookingId": 123, "invoiceUrl": "https://..." }
     */
    public void sendInvoiceUrl(Long bookingId, String invoiceUrl) {
        log.info("[BookingApiClient] --> POST /invoice-receive | bookingId: {}, invoiceUrl: {}", bookingId, invoiceUrl);
        long start = System.currentTimeMillis();
        try {
            restClient.post()
                    .uri("/invoice-receive")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new InvoiceReceiveRequest(bookingId, invoiceUrl))
                    .retrieve()
                    .toBodilessEntity();

            log.info("[BookingApiClient] <-- POST /invoice-receive responded in {} ms | invoice URL delivered successfully for bookingId: {}",
                    System.currentTimeMillis() - start, bookingId);
        } catch (Exception e) {
            log.error("[BookingApiClient] <-- POST /invoice-receive FAILED after {} ms | bookingId: {}, error: {}",
                    System.currentTimeMillis() - start, bookingId, e.getMessage(), e);
            throw new RuntimeException("Failed to deliver invoice URL to booking service: " + e.getMessage(), e);
        }
    }
}
