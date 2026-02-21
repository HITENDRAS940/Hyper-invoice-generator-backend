package com.hyper.invoicebackend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceReceiveRequest {
    private Long bookingId;

    @JsonProperty("invoiceURL")
    private String invoiceUrl;
}

