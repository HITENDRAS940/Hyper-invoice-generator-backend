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
        String fullPublicId = "invoices/" + publicId;
        log.info("[CloudinaryService] Starting upload | publicId: '{}', fileSize: {} bytes (~{} KB)",
                fullPublicId, pdfBytes.length, pdfBytes.length / 1024);
        log.debug("[CloudinaryService] Upload params -> resource_type: raw, format: pdf");
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> uploadResult = cloudinary.uploader().upload(
                    pdfBytes,
                    ObjectUtils.asMap(
                            "resource_type", "raw",
                            "public_id", fullPublicId,
                            "format", "pdf",
                            "flags", "attachment"
                    )
            );

            long elapsed = System.currentTimeMillis() - start;
            String rawUrl      = (String) uploadResult.get("secure_url");
            Object uploadedAt  = uploadResult.get("created_at");
            Object bytes       = uploadResult.get("bytes");
            Object resourceType = uploadResult.get("resource_type");

            // Append fl_attachment to force browser download when the URL is opened
            String secureUrl = rawUrl.replace("/raw/upload/", "/raw/upload/fl_attachment/");

            log.info("[CloudinaryService] Upload SUCCESS in {} ms | downloadUrl: {}", elapsed, secureUrl);
            log.debug("[CloudinaryService] Cloudinary response details -> public_id: '{}', bytes: {}, resource_type: {}, created_at: {}",
                    fullPublicId, bytes, resourceType, uploadedAt);
            return secureUrl;

        } catch (Exception e) {
            log.error("[CloudinaryService] Upload FAILED after {} ms | publicId: '{}' | error: {}",
                    System.currentTimeMillis() - start, fullPublicId, e.getMessage(), e);
            throw new CloudinaryUploadException("Failed to upload PDF to Cloudinary: " + e.getMessage(), e);
        }
    }
}

