package com.baltdh.dailyoffice;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Heuristic receipt parser. It proposes a value but never saves a transaction automatically. */
public final class ReceiptParser {
    private static final Pattern AMOUNT = Pattern.compile(
            "(?<![0-9])(?:R\\s*\\$\\s*)?((?:[0-9]{1,3}(?:[.,\\s][0-9]{3})+|[0-9]+)(?:[.,][0-9]{1,2})?)(?![0-9])",
            Pattern.CASE_INSENSITIVE);

    private static final String[] HIGH_VALUE_LABELS = {
            "TOTAL A PAGAR", "VALOR A PAGAR", "VALOR TOTAL", "TOTAL DA NOTA",
            "TOTAL GERAL", "TOTAL DA COMPRA", "TOTAL DO PEDIDO", "VALOR FINAL",
            "TOTAL PAGO", "VALOR PAGO", "TOTAL"
    };
    private static final String[] EXCLUDED_TERMS = {
            "TROCO", "DESCONTO", "SUBTOTAL", "TRIBUTOS", "IMPOSTO", "ICMS",
            "PIS", "COFINS", "BASE DE CALCULO", "VALOR APROXIMADO DOS TRIBUTOS",
            "TAXA DE SERVICO", "JUROS", "MULTA", "TOTAL DE ITENS",
            "QUANTIDADE TOTAL", "QTD TOTAL", "NUMERO DE ITENS"
    };
    private static final String[] IDENTIFICATION_TERMS = {
            "CNPJ", "CPF", "CHAVE DE ACESSO", "PROTOCOLO", "INSCRICAO ESTADUAL",
            "CEP", "TELEFONE", "TELEFON", "DOCUMENTO", "SERIE", "DATA DE EMISSAO",
            "HORA DA EMISSAO", "CODIGO DE BARRAS"
    };

    private ReceiptParser() { }

    public static Result parse(String rawText) {
        String source = rawText == null ? "" : rawText;
        String[] lines = source.split("\\R");
        Candidate best = null;

        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String originalLine = lines[lineIndex].trim();
            if (originalLine.isEmpty()) continue;
            String normalized = normalize(originalLine);
            if (containsAny(normalized, IDENTIFICATION_TERMS)) continue;

            Matcher matcher = AMOUNT.matcher(originalLine);
            while (matcher.find()) {
                String token = matcher.group(1);
                long cents;
                try {
                    cents = BrazilianMoney.parseToCents(token);
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                String contextualPrefix = "";
                if (lineIndex > 0) {
                    String previousLine = normalize(lines[lineIndex - 1]);
                    if (!containsAny(previousLine, EXCLUDED_TERMS)
                            && (containsAny(previousLine, HIGH_VALUE_LABELS)
                            || lastWholeWordIndex(previousLine, "VALOR") >= 0)) {
                        contextualPrefix = previousLine + " ";
                    }
                }
                contextualPrefix += normalize(originalLine.substring(0, matcher.start()));
                int score = scoreCandidate(contextualPrefix, contextualPrefix.length(),
                        matcher.group().toUpperCase(Locale.ROOT).contains("R$"),
                        token.matches(".*[,.][0-9]{1,2}$"), lineIndex, lines.length);
                if (best == null || score > best.score || (score == best.score && lineIndex > best.lineIndex)) {
                    best = new Candidate(cents, score, lineIndex, originalLine);
                }
            }
        }

        String description = extractDescription(lines);
        if (best == null || best.score < 30) {
            return new Result(false, 0L, description, "", "Não identificado", 0, source);
        }
        String confidence = best.score >= 110 ? "Alta" : best.score >= 60 ? "Média" : "Baixa";
        return new Result(true, best.cents, description, best.line, confidence, best.score, source);
    }

    private static int scoreCandidate(String before, int prefixLength, boolean explicitCurrency,
                                      boolean hasDecimal, int lineIndex, int lineCount) {
        int score = 0;
        int nearestLabel = -1;
        int labelScore = 0;
        for (String label : HIGH_VALUE_LABELS) {
            int index = lastWholeWordIndex(before, label);
            if (index >= 0) {
                int currentScore = label.equals("TOTAL") ? 105 : 150;
                if (currentScore > labelScore || (currentScore == labelScore && index > nearestLabel)) {
                    nearestLabel = index;
                    labelScore = currentScore;
                }
            }
        }
        if (nearestLabel >= 0) {
            int distance = Math.max(0, prefixLength - nearestLabel);
            score += Math.max(45, labelScore - Math.min(55, distance / 2));
        } else if (lastWholeWordIndex(before, "VALOR") >= 0) {
            score += 70;
        }

        if (explicitCurrency || before.matches(".*R\\s*\\$.*")) score += 30;
        // An unlabelled integer is too often an item count, code or quantity rather than money.
        if (!hasDecimal && !explicitCurrency && labelScore <= 105) score -= 100;

        int latestPenalty = -1;
        for (String term : EXCLUDED_TERMS) {
            int index = before.lastIndexOf(term);
            if (index > latestPenalty) latestPenalty = index;
        }
        if (latestPenalty >= 0 && latestPenalty > nearestLabel) score -= 130;

        // A receipt's final payable amount is commonly near the bottom, but location alone
        // is deliberately too weak to override a labelled subtotal, tax, discount or change.
        if (lineCount > 1) score += (lineIndex * 8) / lineCount;
        return score;
    }

    private static int lastWholeWordIndex(String text, String word) {
        Matcher matcher = Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(text);
        int found = -1;
        while (matcher.find()) found = matcher.start();
        return found;
    }

    private static String extractDescription(String[] lines) {
        int inspected = 0;
        for (String raw : lines) {
            if (inspected++ >= 8) break;
            String line = raw.trim().replaceAll("\\s+", " ");
            if (line.length() < 3 || !line.matches(".*[A-Za-zÀ-ÿ].*")) continue;
            String normalized = normalize(line);
            if (containsAny(normalized, IDENTIFICATION_TERMS)) continue;
            if (normalized.startsWith("DATA") || normalized.startsWith("HORA")
                    || normalized.contains("NOTA FISCAL") || normalized.contains("DOCUMENTO AUXILIAR")
                    || normalized.contains("DANFE") || normalized.equals("CUPOM FISCAL")
                    || normalized.contains("CONSULTE PELA CHAVE")) continue;
            return line.length() > 70 ? line.substring(0, 70) : line;
        }
        return "Despesa via comprovante";
    }

    private static boolean containsAny(String text, String[] terms) {
        for (String term : terms) if (text.contains(term)) return true;
        return false;
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT);
    }

    private static final class Candidate {
        final long cents;
        final int score;
        final int lineIndex;
        final String line;
        Candidate(long cents, int score, int lineIndex, String line) {
            this.cents = cents;
            this.score = score;
            this.lineIndex = lineIndex;
            this.line = line;
        }
    }

    public static final class Result {
        public final boolean hasAmount;
        public final long amountCents;
        public final String description;
        public final String selectedLine;
        public final String confidence;
        public final int score;
        public final String rawText;

        Result(boolean hasAmount, long amountCents, String description, String selectedLine,
               String confidence, int score, String rawText) {
            this.hasAmount = hasAmount;
            this.amountCents = amountCents;
            this.description = description;
            this.selectedLine = selectedLine;
            this.confidence = confidence;
            this.score = score;
            this.rawText = rawText;
        }
    }
}
