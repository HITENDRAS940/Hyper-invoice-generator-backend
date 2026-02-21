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
        try {
            log.info("Rendering Thymeleaf template: {}", templateName);
            String htmlContent = templateEngine.process(templateName, context);

            log.info("Converting HTML to PDF...");
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                PdfRendererBuilder builder = new PdfRendererBuilder();
                builder.useFastMode();
                builder.withHtmlContent(htmlContent, null);
                builder.toStream(outputStream);
                builder.run();
                log.info("PDF generated successfully.");
                return outputStream.toByteArray();
            }
        } catch (Exception e) {
            log.error("Failed to generate PDF for template: {}", templateName, e);
            throw new PdfGenerationException("Failed to generate PDF: " + e.getMessage(), e);
        }
    }
}



