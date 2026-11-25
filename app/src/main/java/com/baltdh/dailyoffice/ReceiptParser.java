package com.baltdh.dailyoffice;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Contextual parser for purchase receipts, fiscal coupons and payroll receipts. */
public final class ReceiptParser {
    private static final Pattern AMOUNT = Pattern.compile(
            "(?<![0-9])(?:R\\s*\\$\\s*)?((?:[0-9]{1,3}(?:[.,\\s][0-9]{3})+|[0-9]+)(?:[.,][0-9]{1,2})?)(?![0-9])",
            Pattern.CASE_INSENSITIVE);

    private static final String[] NET_LABELS = {
            "TOTAL LIQUIDO", "VALOR LIQUIDO", "LIQUIDO A RECEBER", "LIQUIDO A PAGAR",
            "VALOR A RECEBER", "VALOR RECEBIDO"
    };
    private static final String[] PAYABLE_LABELS = {
            "VALOR A PAGAR", "TOTAL A PAGAR", "VALOR TOTAL", "TOTAL DA NOTA",
            "TOTAL GERAL", "TOTAL DA COMPRA", "TOTAL DO PEDIDO", "VALOR FINAL",
            "TOTAL PAGO"
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
    private static final String[] PAYMENT_METHODS = {
            "AMERICAN EXPRESS", "MASTER CARD", "MASTERCARD", "CARTAO DE DEBITO", "CARTAO DE CREDITO",
            "CARTAO DEBITO", "CARTAO CREDITO", "CARTAO DEBIT", "CARTAO DE CRED",
            "VALE ALIMENTACAO", "VALE REFEICAO", "MERCADO PAGO", "GOOGLE PAY", "APPLE PAY",
            "PAGAMENTO INSTANTANEO", "TRANSFERENCIA", "DINHEIRO", "ESPECIE", "VOUCHER",
            "HIPERCARD", "AMEX", "ALELO", "SODEXO", "TICKET", "PICPAY",
            "DEBITO", "CREDITO", "CARTAO", "VISA", "ELO", "PIX", "BOLETO", "CHEQUE"
    };
    private static final Pattern PURCHASE_DATE = Pattern.compile("(?<![0-9])(\\d{2}[./-]\\d{2}[./-]\\d{4}|\\d{4}-\\d{2}-\\d{2})(?![0-9])");

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
                StringBuilder contextBuilder = new StringBuilder(prefix);
                for (int back = 1; back <= 4 && lineIndex - back >= 0; back++) {
                    String prior = normalize(lines[lineIndex - back]);
                    if (prior.isEmpty()) continue;
                    // A payment block is a hard boundary: never let "VALOR A PAGAR"
                    // leak into the later "VALOR PAGO" or change candidates.
                    if (prior.contains("FORMA DE PAGAMENTO") || prior.contains("FORMA PAGAMENTO")
                            || prior.contains("MEIO DE PAGAMENTO")) break;
                    contextBuilder.insert(0, prior + " ");
                    if (containsAny(prior, NET_LABELS) || containsAny(prior, PAYABLE_LABELS)
                            || containsAny(prior, EXCLUDED_TERMS)) break;
                }
                String context = contextBuilder.toString();
                int score = scoreCandidate(context,
                        matcher.group().toUpperCase(Locale.ROOT).contains("R$"),
                        token.matches(".*[,.][0-9]{1,2}$"), lineIndex, lines.length, payroll);
                if (best == null || score > best.score
                        || (score == best.score && lineIndex > best.lineIndex)) {
                    best = new Candidate(cents, score, lineIndex, originalLine);
                }
            }
        }

        String merchantName = extractMerchant(lines, payroll);
        String description = payroll ? "Recebimento de férias / salário" : merchantName;
        if (description == null || description.isEmpty()) description = extractDescription(lines, payroll);
        String purchaseDate = extractPurchaseDate(lines);
        String itemDetails = extractItemDetails(lines, payroll);
        String documentType = detectDocumentType(source, payroll);
        String paymentMethod = extractPaymentMethod(lines);
        String suggestedKind = payroll ? "Receita pessoal" : "Despesa empresarial";
        if (best == null || best.score < 35) {
            return new Result(false, 0L, description, "", "Não identificado", 0,
                    source, payroll, suggestedKind, paymentMethod, merchantName, purchaseDate, itemDetails, documentType);
        }
        String confidence = best.score >= 145 ? "Alta" : best.score >= 85 ? "Média" : "Baixa";
        return new Result(true, best.cents, description, best.line, confidence, best.score,
                source, payroll, suggestedKind, paymentMethod, merchantName, purchaseDate, itemDetails, documentType);
    }

    private static int scoreCandidate(String context, boolean explicitCurrency, boolean hasDecimal,
                                      int lineIndex, int lineCount, boolean payroll) {
        int score = 0;
        int netIndex = lastLabelIndex(context, NET_LABELS);
        int payableIndex = lastLabelIndex(context, PAYABLE_LABELS);
        int weakTotalIndex = lastLabelIndex(context, WEAK_TOTAL_LABELS);
        int excludedIndex = lastLabelIndex(context, EXCLUDED_TERMS);

        if (netIndex >= 0) score += 230;
        else if (payableIndex >= 0) score += 190;
        else if (payroll) {
            if (lastLabelIndex(context, new String[]{"FERIAS"}) >= 0) score += 25;
            if (lastLabelIndex(context, new String[]{"PROVENTOS"}) >= 0) score += 35;
        } else if (weakTotalIndex >= 0) score += 100;
        else if (lastLabelIndex(context, new String[]{"VALOR"}) >= 0) score += 55;

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

    private static String extractPaymentMethod(String[] lines) {
        Set<String> methods = new LinkedHashSet<>();
        int heading = -1;
        for (int i = 0; i < lines.length; i++) {
            String n = normalize(lines[i]);
            if (n.contains("FORMA DE PAGAMENTO") || n.contains("FORMA PAGAMENTO") || n.contains("FORMA PAGTO")
                    || n.contains("MEIO DE PAGAMENTO") || n.contains("MEIO PAGTO")
                    || n.contains("TIPO DE PAGAMENTO") || n.contains("METODO DE PAGAMENTO")
                    || n.contains("METODO PAGAMENTO") || n.startsWith("PAGAMENTO VIA")
                    || n.startsWith("PAGAMENTO COM") || n.equals("PAGAMENTO")) {
                heading = i;
                break;
            }
        }

        if (heading >= 0) {
            for (int i = heading; i < lines.length && i <= heading + 8; i++) {
                String n = normalize(lines[i]);
                if (i > heading && (n.contains("TROCO") || n.contains("CHAVE DE ACESSO")
                        || n.contains("CONSULTE") || n.contains("ITENS DA COMPRA")
                        || n.contains("TOTAL GERAL") || n.contains("DOCUMENTO AUXILIAR"))) break;
                addPaymentMethods(methods, n);
            }
        }
        if (methods.isEmpty()) {
            for (String line : lines) {
                String n = normalize(line);
                if (n.equals("PIX") || n.equals("DINHEIRO") || n.equals("ESPECIE")
                        || n.equals("CARTAO DE DEBITO") || n.equals("CARTAO DE CREDITO")
                        || n.equals("DEBITO") || n.equals("CREDITO") || n.startsWith("PIX ")
                        || n.startsWith("CARTAO DE DEBITO ") || n.startsWith("CARTAO DE CREDITO ")) {
                    addPaymentMethods(methods, n);
                }
            }
        }
        boolean debit = methods.contains("CARTAO DE DEBITO") || methods.contains("CARTAO DEBITO")
                || methods.contains("CARTAO DE DEBIT") || methods.contains("DEBITO");
        boolean credit = methods.contains("CARTAO DE CREDITO") || methods.contains("CARTAO CREDITO")
                || methods.contains("CARTAO DE CRED") || methods.contains("CREDITO");
        boolean genericCard = methods.contains("CARTAO") || debit || credit;
        ArrayList<String> brands = new ArrayList<>();
        addBrand(brands, methods, "VISA", "Visa");
        addBrand(brands, methods, "MASTERCARD", "Mastercard");
        addBrand(brands, methods, "MASTER CARD", "Mastercard");
        addBrand(brands, methods, "ELO", "Elo");
        addBrand(brands, methods, "HIPERCARD", "Hipercard");
        addBrand(brands, methods, "AMEX", "American Express");
        addBrand(brands, methods, "AMERICAN EXPRESS", "American Express");
        methods.remove("CARTAO"); methods.remove("DEBITO"); methods.remove("CREDITO");
        methods.remove("CARTAO DE DEBITO"); methods.remove("CARTAO DEBITO"); methods.remove("CARTAO DE DEBIT");
        methods.remove("CARTAO DE CREDITO"); methods.remove("CARTAO CREDITO"); methods.remove("CARTAO DE CRED");
        ArrayList<String> formatted = new ArrayList<>();
        if (genericCard || !brands.isEmpty()) {
            String card = debit ? "Cartão de débito" : credit ? "Cartão de crédito" : "Cartão (modalidade não identificada)";
            if (!brands.isEmpty()) card += " (" + joinPaymentMethods(brands) + ")";
            formatted.add(card);
        }
        for (String method : methods) formatted.add(formatPaymentMethod(method));
        return joinPaymentMethods(formatted);
    }

    private static void addBrand(ArrayList<String> brands, Set<String> methods, String key, String label) {
        if (methods.remove(key) && !brands.contains(label)) brands.add(label);
    }

    private static void addPaymentMethods(Set<String> methods, String candidate) {
        String clean = candidate.replaceAll("[^A-Z0-9]+", " ").trim();
        String padded = " " + clean + " ";
        for (String method : PAYMENT_METHODS) {
            String token = " " + method.replaceAll("[^A-Z0-9]+", " ").trim() + " ";
            if (!token.equals("  ") && padded.contains(token)) methods.add(method);
        }
    }

    private static String joinPaymentMethods(ArrayList<String> methods) {
        StringBuilder out = new StringBuilder();
        for (String method : methods) {
            if (out.length() > 0) out.append(" + ");
            out.append(method);
        }
        return out.toString();
    }

    private static String formatPaymentMethod(String method) {
        switch (method) {
            case "CARTAO DE DEBITO": case "CARTAO DEBITO": case "CARTAO DE DEBIT": case "DEBITO":
                return "Cartão de débito";
            case "CARTAO DE CREDITO": case "CARTAO CREDITO": case "CARTAO DE CRED": case "CREDITO":
                return "Cartão de crédito";
            case "PIX": return "Pix";
            case "DINHEIRO": case "ESPECIE": return "Dinheiro";
            case "VALE ALIMENTACAO": return "Vale-alimentação";
            case "VALE REFEICAO": return "Vale-refeição";
            case "TRANSFERENCIA": return "Transferência";
            case "BOLETO": return "Boleto";
            case "CHEQUE": return "Cheque";
            case "VOUCHER": return "Voucher";
            case "CARTAO": return "Cartão (modalidade não identificada)";
            case "VISA": return "Cartão Visa (modalidade não identificada)";
            case "MASTERCARD": case "MASTER CARD": return "Cartão Mastercard (modalidade não identificada)";
            case "ELO": return "Cartão Elo (modalidade não identificada)";
            case "HIPERCARD": return "Cartão Hipercard (modalidade não identificada)";
            case "AMEX": case "AMERICAN EXPRESS": return "Cartão American Express";
            case "ALELO": case "SODEXO": case "TICKET": return "Vale-benefício " + method;
            case "PICPAY": return "PicPay";
            case "MERCADO PAGO": return "Mercado Pago";
            case "GOOGLE PAY": return "Google Pay";
            case "APPLE PAY": return "Apple Pay";
            case "PAGAMENTO INSTANTANEO": return "Pix";
            default: return method;
        }
    }

    private static boolean isPayrollDocument(String text) {
        String n = normalize(text);
        return n.contains("AVISO E RECIBO DE FERIAS") || n.contains("RECIBO DE FERIAS")
                || n.contains("HOLERITE") || n.contains("CONTRACHEQUE")
                || n.contains("FOLHA DE PAGAMENTO") || n.contains("DEMONSTRATIVO DE PAGAMENTO")
                || n.contains("TOTAL LIQUIDO") && (n.contains("PROVENTOS") || n.contains("DESCONTOS"));
    }

    private static String extractMerchant(String[] lines, boolean payroll) {
        if (payroll) return "";
        String[] excluded = {"DANFE", "NOTA FISCAL", "DOCUMENTO AUXILIAR", "CUPOM FISCAL", "COMPROVANTE", "RECIBO",
                "CONSUMIDOR", "CNPJ", "CPF", "INSCRICAO", "IE:", "DATA", "HORA",
                "EMISSAO", "PROTOCOLO", "CHAVE DE ACESSO", "CONSULTE", "ENDERECO",
                "TELEFONE", "FONE", "CEP", "SERIE", "NUMERO", "COO", "CCF"};
        for (int i = 0; i < Math.min(lines.length, 18); i++) {
            String line = lines[i].trim().replaceAll("\\s+", " ");
            if (line.length() < 3 || line.length() > 70 || !line.matches(".*[A-Za-zÀ-ÿ].*")) continue;
            Matcher possiblePrice = AMOUNT.matcher(line);
            boolean hasPrice = false;
            while (possiblePrice.find()) {
                if (possiblePrice.group(1).matches(".*[,.][0-9]{1,2}$")) { hasPrice = true; break; }
            }
            if (hasPrice) continue;
            String n = normalize(line);
            if (containsAny(n, excluded) || containsAny(n, EXCLUDED_TERMS)) continue;
            if (n.contains("FORMA DE PAGAMENTO") || n.contains("MEIO DE PAGAMENTO") || n.contains("VALOR A PAGAR")) continue;
            if (n.matches(".*\\b\\d{2}[./-]\\d{2}[./-]\\d{4}\\b.*")) continue;
            if (line.matches("^[0-9 .,/:-]+$")) continue;
            return line;
        }
        return "";
    }

    private static String extractPurchaseDate(String[] lines) {
        for (int i = 0; i < Math.min(lines.length, 24); i++) {
            String n = normalize(lines[i]);
            if (!(n.contains("DATA") || n.contains("EMISSAO") || n.contains("COMPRA") || i < 5)) continue;
            Matcher matcher = PURCHASE_DATE.matcher(lines[i]);
            if (matcher.find()) return matcher.group(1).replace('.', '/').replace('-', '/');
        }
        return "";
    }

    private static String extractItemDetails(String[] lines, boolean payroll) {
        if (payroll) return "";
        Set<String> found = new LinkedHashSet<>();
        String[] excluded = {"TOTAL", "SUBTOTAL", "DESCONTO", "TROCO", "PAGAMENTO", "FORMA DE",
                "MEIO DE", "VALOR A PAGAR", "VALOR PAGO", "DINHEIRO", "ESPECIE", "CARTAO",
                "CREDITO", "DEBITO", "PIX", "VISA", "MASTERCARD", "BOLETO", "CHEQUE",
                "ICMS", "ISS", "TRIBUTO", "IMPOSTO", "CNPJ", "CPF", "CHAVE DE ACESSO",
                "PROTOCOLO", "CONSULTE", "QTDE", "QUANTIDADE", "CODIGO", "CUPOM FISCAL",
                "NOTA FISCAL", "DOCUMENTO AUXILIAR", "VALOR APROXIMADO"};
        for (String raw : lines) {
            String line = raw.trim().replaceAll("\\s+", " ");
            if (line.length() < 5 || line.length() > 120) continue;
            String n = normalize(line);
            if (!line.matches(".*[A-Za-zÀ-ÿ].*") || containsAny(n, excluded) || containsAny(n, IDENTIFICATION_TERMS)) continue;
            Matcher matcher = AMOUNT.matcher(line);
            String lastValue = null;
            int lastStart = -1;
            while (matcher.find()) {
                String token = matcher.group(1);
                if (token.matches(".*[,.][0-9]{1,2}$")) { lastValue = token; lastStart = matcher.start(); }
            }
            if (lastValue == null || lastStart <= 0) continue;
            long cents;
            try { cents = BrazilianMoney.parseToCents(lastValue); }
            catch (IllegalArgumentException ex) { continue; }
            String item = line.substring(0, lastStart).replaceAll("^[0-9 .#-]+", "")
                    .replaceAll("\\s+", " ").replaceAll("[-:|]+$", "").trim();
            if (item.length() < 3 || !item.matches(".*[A-Za-zÀ-ÿ].*")) continue;
            if (item.length() > 62) item = item.substring(0, 62).trim();
            found.add(item + " — R$ " + BrazilianMoney.formatCents(cents));
            if (found.size() >= 40) break;
        }
        StringBuilder out = new StringBuilder();
        for (String item : found) {
            if (out.length() > 0) out.append('\n');
            out.append("• ").append(item);
        }
        return out.toString();
    }

    private static String detectDocumentType(String text, boolean payroll) {
        if (payroll) return "Recibo de pagamento / férias";
        String n = normalize(text);
        if (n.contains("NFC-E") || n.contains("CUPOM FISCAL ELETRONICO")) return "NFC-e / cupom fiscal";
        if (n.contains("DANFE") || n.contains("NF-E") || n.contains("NOTA FISCAL ELETRONICA")) return "NF-e / DANFE";
        if (n.contains("COMPROVANTE DE PAGAMENTO") || n.contains("COMPROVANTE CARTAO")) return "Comprovante de pagamento";
        if (n.contains("RECIBO")) return "Recibo";
        if (n.contains("CUPOM") || n.contains("TOTAL A PAGAR")) return "Comprovante / cupom de compra";
        return "";
    }

    private static String extractDescription(String[] lines, boolean payroll) {
        if (payroll) return "Recebimento de férias / salário";
        String merchant = extractMerchant(lines, false);
        return merchant.isEmpty() ? "Compra sem estabelecimento identificado" : merchant;
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
        public final String paymentMethod;
        public final String merchantName;
        public final String purchaseDate;
        public final String itemDetails;
        public final String documentType;

        Result(boolean hasAmount, long amountCents, String description, String selectedLine,
               String confidence, int score, String rawText, boolean payrollDocument,
               String suggestedKind, String paymentMethod, String merchantName,
               String purchaseDate, String itemDetails, String documentType) {
            this.hasAmount = hasAmount;
            this.amountCents = amountCents;
            this.description = description;
            this.selectedLine = selectedLine;
            this.confidence = confidence;
            this.score = score;
            this.rawText = rawText;
            this.payrollDocument = payrollDocument;
            this.suggestedKind = suggestedKind;
            this.paymentMethod = paymentMethod;
            this.merchantName = merchantName;
            this.purchaseDate = purchaseDate;
            this.itemDetails = itemDetails;
            this.documentType = documentType;
        }
    }
}
