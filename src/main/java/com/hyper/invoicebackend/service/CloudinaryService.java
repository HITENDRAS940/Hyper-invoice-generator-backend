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
        log.info("[CloudinaryService] Starting upload | folder: 'invoices', publicId: '{}', fileSize: {} bytes (~{} KB)",
                publicId, pdfBytes.length, pdfBytes.length / 1024);
        log.debug("[CloudinaryService] Upload params -> resource_type: raw, folder: invoices, format: pdf");
        long start = System.currentTimeMillis();
        try {
            // For standard PDF upload (as image type), we don't need to append extension to public_id manually.
            // Cloudinary adds it on delivery.

            // Upload as default resource_type (image) so we can use transformations like fl_attachment
            Map<String, Object> uploadResult = cloudinary.uploader().upload(
                    pdfBytes,
                    ObjectUtils.asMap(
                            "folder",        "invoices",
                            "public_id",     publicId,
                            "resource_type", "auto"
                    )
            );

            long elapsed = System.currentTimeMillis() - start;
            Object bytes        = uploadResult.get("bytes");
            Object resourceType = uploadResult.get("resource_type");
            Object uploadedAt   = uploadResult.get("created_at");

            // We construct the URL with fl_attachment to force download
            // secure_url usually looks like: https://res.cloudinary.com/demo/image/upload/v1570979139/invoices/my_invoice.pdf
            // We want to inject /fl_attachment/ after /upload/
            String secureUrl = (String) uploadResult.get("secure_url");
            String downloadUrl;

            if (secureUrl != null && secureUrl.contains("/upload/")) {
                downloadUrl = secureUrl.replace("/upload/", "/upload/fl_attachment/");
            } else {
                downloadUrl = secureUrl;
            }

            log.info("[CloudinaryService] Upload SUCCESS in {} ms | downloadUrl: {}", elapsed, downloadUrl);
            log.debug("[CloudinaryService] Cloudinary response details -> public_id: '{}', bytes: {}, resource_type: {}, created_at: {}",
                    publicId, bytes, resourceType, uploadedAt);
            return downloadUrl;

        } catch (Exception e) {
            log.error("[CloudinaryService] Upload FAILED after {} ms | publicId: '{}' | error: {}",
                    System.currentTimeMillis() - start, fullPublicId, e.getMessage(), e);
            throw new CloudinaryUploadException("Failed to upload PDF to Cloudinary: " + e.getMessage(), e);
        }
    }
}
