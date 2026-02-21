package com.hyper.invoicebackend.util;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Converts a BigDecimal amount to Indian English words.
 * Example: 1180.50 → "One Thousand One Hundred Eighty Rupees and Fifty Paise Only"
 */
@Component
public class AmountToWordsConverter {

    private static final String[] ONES = {
            "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
            "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen",
            "Sixteen", "Seventeen", "Eighteen", "Nineteen"
    };

    private static final String[] TENS = {
            "", "", "Twenty", "Thirty", "Forty", "Fifty",
            "Sixty", "Seventy", "Eighty", "Ninety"
    };

    public String convert(BigDecimal amount) {
        if (amount == null) return "Zero Rupees Only";

        long rupees = amount.longValue();
        int paise   = amount.subtract(BigDecimal.valueOf(rupees))
                            .multiply(BigDecimal.valueOf(100))
                            .intValue();

        StringBuilder result = new StringBuilder();

        if (rupees == 0) {
            result.append("Zero");
        } else {
            result.append(convertToWords(rupees));
        }

        result.append(" Rupees");

        if (paise > 0) {
            result.append(" and ").append(convertToWords(paise)).append(" Paise");
        }

        result.append(" Only");
        return result.toString();
    }

    private String convertToWords(long number) {
        if (number == 0) return "";

        if (number < 20) {
            return ONES[(int) number];
        }

        if (number < 100) {
            return TENS[(int) (number / 10)]
                    + (number % 10 != 0 ? " " + ONES[(int) (number % 10)] : "");
        }

        if (number < 1_000) {
            return ONES[(int) (number / 100)] + " Hundred"
                    + (number % 100 != 0 ? " " + convertToWords(number % 100) : "");
        }

        if (number < 1_00_000) {           // up to 99,999 → Thousands
            return convertToWords(number / 1_000) + " Thousand"
                    + (number % 1_000 != 0 ? " " + convertToWords(number % 1_000) : "");
        }

        if (number < 1_00_00_000) {        // up to 99,99,999 → Lakhs
            return convertToWords(number / 1_00_000) + " Lakh"
                    + (number % 1_00_000 != 0 ? " " + convertToWords(number % 1_00_000) : "");
        }

        // Crores
        return convertToWords(number / 1_00_00_000) + " Crore"
                + (number % 1_00_00_000 != 0 ? " " + convertToWords(number % 1_00_00_000) : "");
    }
}

