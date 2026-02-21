package com.hyper.invoicebackend.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.hyper.invoicebackend.exception.CloudinaryUploadException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinaryService {

    private final Cloudinary cloudinary;

    /**
     * Uploads a PDF (as byte array) to Cloudinary as a raw file.
     *
     * @param pdfBytes  the PDF content as byte array
     * @param publicId  the public ID / filename for Cloudinary (without extension)
     * @return the secure URL of the uploaded file
     */
    @SuppressWarnings("unchecked")
    public String uploadPdf(byte[] pdfBytes, String publicId) {
        try {
            log.info("Uploading PDF to Cloudinary with publicId: {}", publicId);

            Map<String, Object> uploadResult = cloudinary.uploader().upload(
                    pdfBytes,
                    ObjectUtils.asMap(
                            "resource_type", "raw",
                            "public_id", "invoices/" + publicId,
                            "format", "pdf"
                    )
            );

            String secureUrl = (String) uploadResult.get("secure_url");
            log.info("PDF uploaded successfully to Cloudinary: {}", secureUrl);
            return secureUrl;

        } catch (Exception e) {
            log.error("Failed to upload PDF to Cloudinary with publicId: {}", publicId, e);
            throw new CloudinaryUploadException("Failed to upload PDF to Cloudinary: " + e.getMessage(), e);
        }
    }
}

