package com.baltdh.dailyoffice;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Contextual receipt/payroll parser. Suggestions are reviewed by the user before saving. */
public final class ReceiptParser {
    private static final Pattern AMOUNT = Pattern.compile(
            "(?<![0-9])(?:R\\s*\\$\\s*)?((?:[0-9]{1,3}(?:[.,\\s][0-9]{3})+|[0-9]+)(?:[.,][0-9]{1,2})?)(?![0-9])",
            Pattern.CASE_INSENSITIVE);

    private static final String[] NET_LABELS = {
            "TOTAL LIQUIDO", "VALOR LIQUIDO", "LIQUIDO A RECEBER", "LIQUIDO A PAGAR",
            "VALOR A RECEBER", "VALOR RECEBIDO"
    };
    private static final String[] PAYABLE_LABELS = {
            "TOTAL A PAGAR", "VALOR A PAGAR", "VALOR TOTAL", "TOTAL DA NOTA",
            "TOTAL GERAL", "TOTAL DA COMPRA", "TOTAL DO PEDIDO", "VALOR FINAL", "TOTAL PAGO"
    };
    private static final String[] WEAK_TOTAL_LABELS = {"TOTAL"};
    private static final String[] EXCLUDED_TERMS = {
            "TROCO", "DESCONTO", "SUBTOTAL", "TRIBUTOS", "IMPOSTO", "ICMS",
            "PIS", "COFINS", "BASE DE CALCULO", "VALOR APROXIMADO DOS TRIBUTOS",
            "TAXA DE SERVICO", "JUROS", "MULTA", "TOTAL DE ITENS",
            "QUANTIDADE TOTAL", "QTD TOTAL", "NUMERO DE ITENS", "TOTAL DOS DESCONTOS",
            "TOTAL DE DESCONTOS", "TOTAL DOS PROVENTOS", "TOTAL DE PROVENTOS"
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
        boolean payroll = isPayrollDocument(source);
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
                String prefix = normalize(originalLine.substring(0, matcher.start()));
                // OCR engines frequently split labels and amounts into separate lines.
                // Inspect up to three preceding lines, but keep the nearest label explicit.
                StringBuilder contextBuilder = new StringBuilder(prefix);
                for (int back = 1; back <= 3 && lineIndex - back >= 0; back++) {
                    String prior = normalize(lines[lineIndex - back]);
                    if (prior.isEmpty()) continue;
                    contextBuilder.insert(0, prior + " ");
                    if (containsAny(prior, NET_LABELS) || containsAny(prior, PAYABLE_LABELS)
                            || containsAny(prior, EXCLUDED_TERMS)) break;
                }
                String context = contextBuilder.toString();
                int score = scoreCandidate(context, matcher.group().toUpperCase(Locale.ROOT).contains("R$"),
                        token.matches(".*[,.][0-9]{1,2}$"), lineIndex, lines.length, payroll);
                if (best == null || score > best.score || (score == best.score && lineIndex > best.lineIndex)) {
                    best = new Candidate(cents, score, lineIndex, originalLine);
                }
            }
        }

        String description = extractDescription(lines, payroll);
        String suggestedKind = payroll ? "Receita pessoal" : "Despesa empresarial";
        if (best == null || best.score < 35) {
            return new Result(false, 0L, description, "", "Não identificado", 0, source, payroll, suggestedKind);
        }
        String confidence = best.score >= 145 ? "Alta" : best.score >= 85 ? "Média" : "Baixa";
        return new Result(true, best.cents, description, best.line, confidence, best.score,
                source, payroll, suggestedKind);
    }

    private static int scoreCandidate(String context, boolean explicitCurrency, boolean hasDecimal,
                                      int lineIndex, int lineCount, boolean payroll) {
        int score = 0;
        int netIndex = lastLabelIndex(context, NET_LABELS);
        int payableIndex = lastLabelIndex(context, PAYABLE_LABELS);
        int weakTotalIndex = lastLabelIndex(context, WEAK_TOTAL_LABELS);
        int excludedIndex = lastLabelIndex(context, EXCLUDED_TERMS);

        if (netIndex >= 0) score += 230;
        else if (payroll) {
            if (lastLabelIndex(context, new String[]{"FERIAS"}) >= 0) score += 25;
            if (lastLabelIndex(context, new String[]{"PROVENTOS"}) >= 0) score += 35;
        } else if (payableIndex >= 0) score += 185;
        else if (weakTotalIndex >= 0) score += 105;
        else if (lastLabelIndex(context, new String[]{"VALOR"}) >= 0) score += 65;

        if (explicitCurrency) score += 25;
        if (hasDecimal) score += 12;
        if (!hasDecimal && !explicitCurrency && netIndex < 0 && payableIndex < 0) score -= 100;

        if (excludedIndex >= 0) {
            int positiveIndex = Math.max(netIndex, payableIndex);
            if (excludedIndex > positiveIndex) score -= 180;
        }
        if (lineCount > 1) score += (lineIndex * 6) / lineCount;
        return score;
    }

    private static int lastLabelIndex(String text, String[] labels) {
        int found = -1;
        for (String label : labels) {
            Matcher matcher = Pattern.compile("\\b" + Pattern.quote(label) + "\\b").matcher(text);
            while (matcher.find()) found = Math.max(found, matcher.start());
        }
        return found;
    }

    private static boolean isPayrollDocument(String text) {
        String n = normalize(text);
        return n.contains("AVISO E RECIBO DE FERIAS") || n.contains("RECIBO DE FERIAS")
                || n.contains("HOLERITE") || n.contains("CONTRACHEQUE")
                || n.contains("FOLHA DE PAGAMENTO") || n.contains("DEMONSTRATIVO DE PAGAMENTO")
                || n.contains("TOTAL LIQUIDO") && (n.contains("PROVENTOS") || n.contains("DESCONTOS"));
    }

    private static String extractDescription(String[] lines, boolean payroll) {
        if (payroll) return "Recebimento de férias / salário";
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
        return "Documento importado";
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
        final long cents; final int score; final int lineIndex; final String line;
        Candidate(long cents, int score, int lineIndex, String line) {
            this.cents = cents; this.score = score; this.lineIndex = lineIndex; this.line = line;
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
        public final boolean payrollDocument;
        public final String suggestedKind;

        Result(boolean hasAmount, long amountCents, String description, String selectedLine,
               String confidence, int score, String rawText, boolean payrollDocument, String suggestedKind) {
            this.hasAmount = hasAmount; this.amountCents = amountCents; this.description = description;
            this.selectedLine = selectedLine; this.confidence = confidence; this.score = score;
            this.rawText = rawText; this.payrollDocument = payrollDocument; this.suggestedKind = suggestedKind;
        }
    }
}
