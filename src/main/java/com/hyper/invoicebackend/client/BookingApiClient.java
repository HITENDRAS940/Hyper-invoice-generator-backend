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
    }

    /**
     * Fetches booking details from the external Hyper booking service.
     *
     * @param bookingId the booking ID
     * @return BookingResponse with full booking + user + amount data
     * @throws ResourceNotFoundException if the booking is not found (404)
     */
    public BookingResponse getBooking(Long bookingId) {
        log.info("Fetching booking details for bookingId: {}", bookingId);
        try {
            BookingResponse response = restClient.get()
                    .uri("/services/booking/{bookingId}", bookingId)
                    .retrieve()
                    .body(BookingResponse.class);

            log.info("Successfully fetched booking: reference={}, customer={}",
                    response != null ? response.getReference() : "null",
                    response != null && response.getUser() != null ? response.getUser().getName() : "null");

            return response;

        } catch (HttpClientErrorException.NotFound e) {
            log.error("Booking not found for bookingId: {}", bookingId);
            throw new ResourceNotFoundException("Booking", "id", bookingId);
        } catch (Exception e) {
            log.error("Failed to fetch booking for bookingId: {}", bookingId, e);
            throw new RuntimeException("Failed to fetch booking details: " + e.getMessage(), e);
        }
    }

    /**
     * Sends the generated Cloudinary invoice URL back to the booking service.
     * POST /invoice-receive  { "bookingId": 123, "invoiceUrl": "https://..." }
     */
    public void sendInvoiceUrl(Long bookingId, String invoiceUrl) {
        log.info("Sending invoice URL to /invoice-receive for bookingId: {}", bookingId);
        try {
            restClient.post()
                    .uri("/invoice-receive")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new InvoiceReceiveRequest(bookingId, invoiceUrl))
                    .retrieve()
                    .toBodilessEntity();

            log.info("Invoice URL sent successfully for bookingId: {}", bookingId);
        } catch (Exception e) {
            log.error("Failed to send invoice URL for bookingId: {}", bookingId, e);
            throw new RuntimeException("Failed to deliver invoice URL to booking service: " + e.getMessage(), e);
        }
    }
}
