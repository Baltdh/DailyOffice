package com.baltdh.dailyoffice;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;

public class MainActivity extends Activity {
    private static final int REQUEST_RECEIPT_IMAGE = 431;
    private static final String KIND_BUSINESS_REVENUE = "Receita empresarial";
    private static final String KIND_BUSINESS_EXPENSE = "Despesa empresarial";
    private static final String KIND_PERSONAL_REVENUE = "Receita pessoal";
    private static final String KIND_PERSONAL_EXPENSE = "Despesa pessoal";
    private static final String KIND_CAPITAL_CONTRIBUTION = "Aporte pessoal";
    private static final String KIND_BUSINESS_EXPENSE_PERSONAL = "Despesa da empresa paga pessoalmente";
    private static final String KIND_TRANSFER = "Transferência entre contas próprias";

    private SQLiteDatabase db;
    private TextView summary;
    private TextView receiptPreview;
    private EditText descriptionInput;
    private EditText amountInput;
    private Spinner kindInput;
    private Button receiptButton;
    private TextRecognizer textRecognizer;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        db = openOrCreateDatabase("dailyoffice.db", MODE_PRIVATE, null);
        db.execSQL("CREATE TABLE IF NOT EXISTS entries(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "kind TEXT NOT NULL," +
                "amount INTEGER NOT NULL," +
                "description TEXT NOT NULL)");
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        buildScreen();
        refreshSummary();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(18);
        root.setPadding(padding, padding, padding, padding);
        scroll.addView(root);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText("DailyOffice · Controle financeiro");
        title.setTextSize(21);
        title.setPadding(0, 0, 0, dp(10));
        root.addView(title);

        summary = new TextView(this);
        summary.setTextSize(15);
        summary.setTextIsSelectable(true);
        summary.setPadding(0, 0, 0, dp(14));
        root.addView(summary);

        receiptButton = new Button(this);
        receiptButton.setText("Ler nota ou comprovante (OCR)");
        receiptButton.setAllCaps(false);
        root.addView(receiptButton, matchWidth());
        receiptButton.setOnClickListener(v -> openReceiptPicker());

        TextView privacyHint = new TextView(this);
        privacyHint.setText("A leitura é feita no próprio aparelho. Confira os dados sugeridos antes de salvar.");
        privacyHint.setTextSize(12);
        privacyHint.setPadding(0, dp(4), 0, dp(8));
        root.addView(privacyHint);

        receiptPreview = new TextView(this);
        receiptPreview.setTextSize(12);
        receiptPreview.setPadding(dp(8), dp(8), dp(8), dp(8));
        receiptPreview.setMaxLines(12);
        root.addView(receiptPreview);

        descriptionInput = new EditText(this);
        descriptionInput.setHint("Descrição / estabelecimento");
        descriptionInput.setSingleLine(false);
        root.addView(descriptionInput, matchWidth());

        amountInput = new EditText(this);
        amountInput.setHint("Valor em R$ (ex.: 1.234,56)");
        amountInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        root.addView(amountInput, matchWidth());

        kindInput = new Spinner(this);
        String[] kinds = {
                KIND_BUSINESS_REVENUE,
                KIND_BUSINESS_EXPENSE,
                KIND_PERSONAL_REVENUE,
                KIND_PERSONAL_EXPENSE,
                KIND_CAPITAL_CONTRIBUTION,
                KIND_BUSINESS_EXPENSE_PERSONAL,
                KIND_TRANSFER
        };
        kindInput.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, kinds));
        root.addView(kindInput, matchWidth());

        Button saveButton = new Button(this);
        saveButton.setText("Salvar lançamento");
        saveButton.setAllCaps(false);
        root.addView(saveButton, matchWidth());
        saveButton.setOnClickListener(v -> saveEntry());
    }

    private void openReceiptPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(Intent.createChooser(intent, "Selecione a foto da nota ou comprovante"),
                    REQUEST_RECEIPT_IMAGE);
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "Nenhum aplicativo disponível para selecionar imagens", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_RECEIPT_IMAGE || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri imageUri = data.getData();
        receiptButton.setEnabled(false);
        receiptPreview.setText("Lendo a imagem e procurando o valor total...");

        try {
            InputImage image = InputImage.fromFilePath(this, imageUri);
            textRecognizer.process(image)
                    .addOnSuccessListener(visionText -> {
                        if (isFinishing()) return;
                        applyReceiptResult(ReceiptParser.parse(visionText.getText()));
                    })
                    .addOnFailureListener(error -> {
                        if (!isFinishing()) {
                            receiptPreview.setText("Não foi possível ler esta imagem. Tente uma foto mais nítida, bem iluminada e enquadrada.");
                            Toast.makeText(this, "Falha na leitura: " + safeMessage(error), Toast.LENGTH_LONG).show();
                        }
                    })
                    .addOnCompleteListener(task -> {
                        if (!isFinishing()) receiptButton.setEnabled(true);
                    });
        } catch (IOException | RuntimeException ex) {
            receiptButton.setEnabled(true);
            receiptPreview.setText("Não foi possível abrir a imagem selecionada.");
            Toast.makeText(this, "Erro ao abrir imagem: " + safeMessage(ex), Toast.LENGTH_LONG).show();
        }
    }

    private void applyReceiptResult(ReceiptParser.Result result) {
        StringBuilder preview = new StringBuilder();
        if (result.hasAmount) {
            preview.append("Valor total sugerido: R$ ")
                    .append(BrazilianMoney.formatCents(result.amountCents))
                    .append("\nConfiança da seleção: ").append(result.confidence)
                    .append(" · confira antes de salvar\nLinha identificada: ")
                    .append(result.selectedLine).append("\n\n");
            amountInput.setText(BrazilianMoney.formatCents(result.amountCents));
            // A nota importada normalmente representa uma compra. O usuário ainda pode alterar o tipo.
            kindInput.setSelection(1);
        } else {
            preview.append("Não encontrei um total com confiança suficiente. Digite o valor manualmente e confira o texto reconhecido.\n\n");
        }
        if (result.description != null && !result.description.isEmpty()
                && (descriptionInput.getText() == null || descriptionInput.getText().toString().trim().isEmpty())) {
            descriptionInput.setText(result.description);
        }
        preview.append("Texto reconhecido:\n");
        String raw = result.rawText == null ? "" : result.rawText.trim();
        if (raw.length() > 1800) raw = raw.substring(0, 1800) + "\n[texto restante omitido na prévia]";
        preview.append(raw.isEmpty() ? "Nenhum texto legível encontrado." : raw);
        receiptPreview.setText(preview.toString());
    }

    private void saveEntry() {
        String description = descriptionInput.getText().toString().trim();
        String rawAmount = amountInput.getText().toString().trim();
        if (description.isEmpty()) {
            Toast.makeText(this, "Informe uma descrição", Toast.LENGTH_LONG).show();
            return;
        }
        final long cents;
        try {
            cents = BrazilianMoney.parseToCents(rawAmount);
        } catch (IllegalArgumentException ex) {
            Toast.makeText(this, "Informe um valor válido, por exemplo 12,50 ou 1.234,56", Toast.LENGTH_LONG).show();
            return;
        }

        String kind = String.valueOf(kindInput.getSelectedItem());
        ContentValues values = new ContentValues();
        values.put("kind", kind);
        values.put("amount", cents);
        values.put("description", description);
        try {
            db.insertOrThrow("entries", null, values);
            amountInput.setText("");
            descriptionInput.setText("");
            receiptPreview.setText("");
            kindInput.setSelection(0);
            refreshSummary();
            Toast.makeText(this, "Lançamento salvo", Toast.LENGTH_SHORT).show();
        } catch (RuntimeException ex) {
            Toast.makeText(this, "Não foi possível salvar o lançamento", Toast.LENGTH_LONG).show();
        }
    }

    private void refreshSummary() {
        long businessRevenue = sumForKind(KIND_BUSINESS_REVENUE);
        long businessExpenses = safeAdd(sumForKind(KIND_BUSINESS_EXPENSE),
                sumForKind(KIND_BUSINESS_EXPENSE_PERSONAL));
        long personalRevenue = sumForKind(KIND_PERSONAL_REVENUE);
        long personalExpenses = sumForKind(KIND_PERSONAL_EXPENSE);
        long contributions = sumForKind(KIND_CAPITAL_CONTRIBUTION);
        long operatingResult = businessRevenue - businessExpenses;

        StringBuilder text = new StringBuilder();
        text.append("RESUMO EMPRESARIAL\n")
                .append("Faturamento bruto: ").append(formatMoney(businessRevenue)).append("\n")
                .append("Despesas da empresa: ").append(formatMoney(businessExpenses)).append("\n")
                .append("Resultado dos lançamentos: ").append(formatMoney(operatingResult)).append("\n\n")
                .append("RESUMO PESSOAL\n")
                .append("Receitas pessoais: ").append(formatMoney(personalRevenue)).append("\n")
                .append("Despesas pessoais: ").append(formatMoney(personalExpenses)).append("\n\n")
                .append("MOVIMENTAÇÕES FORA DO FATURAMENTO\n")
                .append("Aportes pessoais: ").append(formatMoney(contributions)).append("\n")
                .append("Transferências entre contas próprias não entram como receita ou despesa.\n\n")
                .append("TOTAIS POR CATEGORIA\n");

        Cursor cursor = db.rawQuery("SELECT kind, COALESCE(SUM(amount), 0) FROM entries GROUP BY kind ORDER BY kind", null);
        boolean hasEntries = false;
        try {
            while (cursor.moveToNext()) {
                hasEntries = true;
                text.append(cursor.getString(0)).append(": ")
                        .append(formatMoney(cursor.getLong(1))).append("\n");
            }
        } finally {
            cursor.close();
        }
        if (!hasEntries) text.append("Nenhum lançamento cadastrado.\n");
        summary.setText(text.toString());
    }

    private long sumForKind(String kind) {
        Cursor cursor = db.rawQuery("SELECT COALESCE(SUM(amount), 0) FROM entries WHERE kind = ?",
                new String[]{kind});
        try {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0L;
        } finally {
            cursor.close();
        }
    }

    private long safeAdd(long first, long second) {
        try {
            return Math.addExact(first, second);
        } catch (ArithmeticException ex) {
            return first >= 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    private String formatMoney(long cents) {
        return "R$ " + BrazilianMoney.formatCents(cents);
    }

    private String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? "erro desconhecido" : message;
    }

    private LinearLayout.LayoutParams matchWidth() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        if (textRecognizer != null) textRecognizer.close();
        if (db != null && db.isOpen()) db.close();
        super.onDestroy();
    }
}
