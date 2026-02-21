package com.hyper.invoicebackend.service;

import com.hyper.invoicebackend.exception.PdfGenerationException;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfGeneratorService {

    private final SpringTemplateEngine templateEngine;

    /**
     * Generates a PDF from a Thymeleaf template.
     *
     * @param templateName the name of the Thymeleaf template (without .html extension)
     * @param context      the Thymeleaf context containing template variables
     * @return byte array of the generated PDF
     */
    public byte[] generatePdf(String templateName, Context context) {
        log.info("[PdfGeneratorService] Starting PDF generation | template: '{}'", templateName);
        long totalStart = System.currentTimeMillis();
        try {
            // Step 1: Render Thymeleaf template to HTML
            log.debug("[PdfGeneratorService] Rendering Thymeleaf template: '{}'...", templateName);
            long renderStart = System.currentTimeMillis();
            String htmlContent = templateEngine.process(templateName, context);
            log.info("[PdfGeneratorService] Template '{}' rendered in {} ms | HTML size: {} chars",
                    templateName, System.currentTimeMillis() - renderStart, htmlContent.length());

            // Step 2: Convert HTML to PDF
            log.info("[PdfGeneratorService] Converting rendered HTML to PDF (OpenHTMLToPDF)...");
            long pdfStart = System.currentTimeMillis();
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                PdfRendererBuilder builder = new PdfRendererBuilder();
                builder.useFastMode();
                builder.withHtmlContent(htmlContent, null);
                builder.toStream(outputStream);
                log.debug("[PdfGeneratorService] PdfRendererBuilder configured, running renderer...");
                builder.run();
                byte[] pdfBytes = outputStream.toByteArray();
                log.info("[PdfGeneratorService] PDF conversion complete in {} ms | PDF size: {} bytes (~{} KB)",
                        System.currentTimeMillis() - pdfStart, pdfBytes.length, pdfBytes.length / 1024);
                log.info("[PdfGeneratorService] Total PDF generation time: {} ms", System.currentTimeMillis() - totalStart);
                return pdfBytes;
            }
        } catch (Exception e) {
            log.error("[PdfGeneratorService] PDF generation FAILED after {} ms | template: '{}' | error: {}",
                    System.currentTimeMillis() - totalStart, templateName, e.getMessage(), e);
            throw new PdfGenerationException("Failed to generate PDF: " + e.getMessage(), e);
        }
    }
}



