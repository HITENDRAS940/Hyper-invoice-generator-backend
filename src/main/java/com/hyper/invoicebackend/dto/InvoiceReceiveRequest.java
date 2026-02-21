package com.hyper.invoicebackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceReceiveRequest {
    private Long bookingId;
    private String invoiceUrl;
}

