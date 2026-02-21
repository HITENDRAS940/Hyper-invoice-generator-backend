package com.hyper.invoicebackend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Maps the response from:
 * GET https://hyper-render-prod.onrender.com/services/booking/{bookingId}
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookingResponse {

    private Long id;
    private String reference;
    private Long serviceId;
    private String serviceName;
    private Long resourceId;
    private String resourceName;
    private String startTime;
    private String endTime;
    private LocalDate bookingDate;
    private OffsetDateTime createdAt;
    private AmountBreakdown amountBreakdown;
    private String bookingType;
    private String message;
    private List<String> childBookings;
    private String status;
    private UserInfo user;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AmountBreakdown {
        private BigDecimal slotSubtotal;
        private BigDecimal platformFeePercent;
        private BigDecimal platformFee;
        private BigDecimal totalAmount;
        private BigDecimal onlinePaymentPercent;
        private BigDecimal onlineAmount;
        private BigDecimal venueAmount;
        private Boolean venueAmountCollected;
        private String venuePaymentCollectionMethod;
        private String currency;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UserInfo {
        private Long id;
        private String name;
        private String email;
        private String phone;
    }
}

