package com.baltdh.dailyoffice;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Parses and formats monetary values without using binary floating-point. */
public final class BrazilianMoney {
    private BrazilianMoney() { }

    /**
     * Parses Brazilian and common OCR currency forms into integer cents.
     * Examples: 12,50; R$ 1.234,56; 1,234.56; 12.5; 1.234.
     */
    public static long parseToCents(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("Valor vazio");
        }

        String value = input.trim();
        if (value.indexOf('-') >= 0 || value.indexOf('+') >= 0) {
            throw new IllegalArgumentException("Sinal não permitido");
        }
        value = value.replaceAll("(?i)R\\s*\\$|BRL", "")
                .replaceAll("\\s+", "")
                .replaceAll("[^0-9,.]", "");
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Valor inválido");
        }

        boolean hasComma = value.indexOf(',') >= 0;
        boolean hasDot = value.indexOf('.') >= 0;
        String whole;
        String fraction = "";

        if (hasComma && hasDot) {
            int decimalPosition = Math.max(value.lastIndexOf(','), value.lastIndexOf('.'));
            int decimals = value.length() - decimalPosition - 1;
            if (decimals < 1 || decimals > 2) {
                throw new IllegalArgumentException("Casas decimais inválidas");
            }
            whole = normalizeIntegerPart(value.substring(0, decimalPosition));
            fraction = value.substring(decimalPosition + 1);
        } else if (hasComma || hasDot) {
            char separator = hasComma ? ',' : '.';
            int decimalPosition = value.lastIndexOf(separator);
            int trailingDigits = value.length() - decimalPosition - 1;
            if (trailingDigits >= 1 && trailingDigits <= 2) {
                whole = normalizeIntegerPart(value.substring(0, decimalPosition));
                fraction = value.substring(decimalPosition + 1);
            } else if (trailingDigits == 3 && isThousandsGrouping(value, separator)) {
                whole = normalizeIntegerPart(value);
            } else {
                throw new IllegalArgumentException("Formato monetário ambíguo");
            }
        } else {
            whole = value;
        }

        if (whole == null || whole.isEmpty() || !whole.matches("\\d+") || (!fraction.isEmpty() && !fraction.matches("\\d{1,2}"))) {
            throw new IllegalArgumentException("Formato monetário inválido");
        }
        try {
            BigDecimal amount = new BigDecimal(whole + (fraction.isEmpty() ? "" : "." + fraction))
                    .setScale(2, RoundingMode.HALF_UP);
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("O valor deve ser positivo");
            }
            return amount.movePointRight(2).longValueExact();
        } catch (NumberFormatException | ArithmeticException ex) {
            throw new IllegalArgumentException("Valor fora do limite permitido", ex);
        }
    }

    /** Validates grouping before removing thousands separators. */
    private static String normalizeIntegerPart(String part) {
        if (part == null || part.isEmpty()) return null;
        if (part.matches("\\d+")) return part;
        char separator = part.indexOf('.') >= 0 ? '.' : ',';
        if (part.indexOf(separator == '.' ? ',' : '.') >= 0) return null;
        String[] groups = part.split(java.util.regex.Pattern.quote(String.valueOf(separator)), -1);
        if (groups.length < 2 || groups[0].length() < 1 || groups[0].length() > 3
                || !groups[0].matches("\\d+")) return null;
        StringBuilder digits = new StringBuilder(groups[0]);
        for (int i = 1; i < groups.length; i++) {
            if (!groups[i].matches("\\d{3}")) return null;
            digits.append(groups[i]);
        }
        return digits.toString();
    }

    private static boolean isThousandsGrouping(String value, char separator) {
        String[] groups = value.split(java.util.regex.Pattern.quote(String.valueOf(separator)), -1);
        if (groups.length < 2 || groups[0].length() < 1 || groups[0].length() > 3 || !groups[0].matches("\\d+")) {
            return false;
        }
        for (int i = 1; i < groups.length; i++) {
            if (groups[i].length() != 3 || !groups[i].matches("\\d{3}")) {
                return false;
            }
        }
        return true;
    }

    /** Formats integer cents as a Brazilian number, e.g. 123456 -> 1.234,56. */
    public static String formatCents(long cents) {
        boolean negative = cents < 0;
        BigDecimal value = BigDecimal.valueOf(cents).abs().movePointLeft(2).setScale(2);
        String plain = value.toPlainString();
        int decimalPosition = plain.indexOf('.');
        String whole = decimalPosition >= 0 ? plain.substring(0, decimalPosition) : plain;
        String fraction = decimalPosition >= 0 ? plain.substring(decimalPosition + 1) : "00";
        StringBuilder grouped = new StringBuilder();
        for (int i = 0; i < whole.length(); i++) {
            if (i > 0 && (whole.length() - i) % 3 == 0) grouped.append('.');
            grouped.append(whole.charAt(i));
        }
        return (negative ? "-" : "") + grouped + "," + fraction;
    }
}
