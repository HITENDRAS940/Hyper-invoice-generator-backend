package com.hyper.invoicebackend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InvoiceRequestDTO {

    @NotNull(message = "Booking ID is required")
    private Long bookingId;
}
