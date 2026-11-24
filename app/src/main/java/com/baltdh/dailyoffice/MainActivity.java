package com.baltdh.dailyoffice;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
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
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int REQUEST_RECEIPT_IMAGE = 431;
    private static final int REQUEST_EXPORT_CSV = 432;
    private static final String BUSINESS_REVENUE = "Receita empresarial";
    private static final String BUSINESS_EXPENSE = "Despesa empresarial";
    private static final String PERSONAL_REVENUE = "Receita pessoal";
    private static final String PERSONAL_EXPENSE = "Despesa pessoal";
    private static final String CONTRIBUTION = "Aporte pessoal";
    private static final String BUSINESS_EXPENSE_PERSONAL = "Despesa da empresa paga pessoalmente";
    private static final String TRANSFER = "Transferência entre contas próprias";

    private static final int INK = Color.rgb(29, 38, 55);
    private static final int MUTED = Color.rgb(111, 123, 142);
    private static final int PRIMARY = Color.rgb(83, 78, 210);
    private static final int BG = Color.rgb(246, 247, 251);
    private static final int GREEN = Color.rgb(24, 145, 105);

    private SQLiteDatabase db;
    private TextView summary, businessRevenueView, expenseView, resultView, receiptPreview;
    private LinearLayout historyContainer;
    private EditText descriptionInput, amountInput;
    private Spinner kindInput;
    private Button receiptButton;
    private TextRecognizer textRecognizer;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(246, 247, 251));
        getWindow().setNavigationBarColor(Color.rgb(246, 247, 251));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        db = openOrCreateDatabase("dailyoffice.db", MODE_PRIVATE, null);
        db.execSQL("CREATE TABLE IF NOT EXISTS entries(id INTEGER PRIMARY KEY AUTOINCREMENT,kind TEXT NOT NULL,amount INTEGER NOT NULL,description TEXT NOT NULL)");
        ensureCreatedAtColumn();
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        buildScreen();
        refreshSummary();
    }

    /** Adds the timestamp column without rebuilding or deleting existing user records. */
    private void ensureCreatedAtColumn() {
        boolean found = false;
        Cursor columns = db.rawQuery("PRAGMA table_info(entries)", null);
        try {
            while (columns.moveToNext()) {
                if ("created_at".equals(columns.getString(1))) { found = true; break; }
            }
        } finally {
            columns.close();
        }
        if (!found) db.execSQL("ALTER TABLE entries ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0");
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        final int baseLeft = dp(18), baseTop = dp(22), baseRight = dp(18), baseBottom = dp(28);
        root.setPadding(baseLeft, baseTop, baseRight, baseBottom);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int left, top, right, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(
                        android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());
                left = bars.left; top = bars.top; right = bars.right; bottom = bars.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(baseLeft + left, baseTop + top, baseRight + right, baseBottom + bottom);
            return insets;
        });
        scroll.addView(root);
        setContentView(scroll);

        TextView eyebrow = text("GESTÃO FINANCEIRA", 11, PRIMARY, true);
        root.addView(eyebrow);
        TextView title = text("DailyOffice", 29, INK, true);
        title.setPadding(0, dp(3), 0, 0);
        root.addView(title);
        TextView subtitle = text("Seu negócio e suas contas em ordem.", 14, MUTED, false);
        subtitle.setPadding(0, 0, 0, dp(18));
        root.addView(subtitle);

        LinearLayout hero = card(Color.rgb(37, 43, 83));
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));
        TextView heroLabel = text("RESULTADO EMPRESARIAL", 11, Color.rgb(211, 214, 255), true);
        hero.addView(heroLabel);
        resultView = text("R$ 0,00", 29, Color.WHITE, true);
        resultView.setPadding(0, dp(8), 0, dp(14));
        hero.addView(resultView);
        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.addView(metric("Faturamento", "R$ 0,00", true), new LinearLayout.LayoutParams(0, -2, 1));
        metrics.addView(metric("Despesas", "R$ 0,00", false), new LinearLayout.LayoutParams(0, -2, 1));
        hero.addView(metrics);
        root.addView(hero, margin(0, 0, 0, 16));

        TextView section = text("Lançamentos", 18, INK, true);
        root.addView(section, margin(0, 0, 0, 8));
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button scan = button("Ler documento", PRIMARY);
        receiptButton = scan;
        scan.setOnClickListener(v -> openReceiptPicker());
        actions.addView(scan, new LinearLayout.LayoutParams(0, dp(50), 1));
        Button manual = button("Novo manual", Color.rgb(230, 231, 250));
        manual.setTextColor(PRIMARY);
        manual.setOnClickListener(v -> {
            descriptionInput.requestFocus();
            Toast.makeText(this, "Preencha os dados do lançamento abaixo.", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams manualParams = new LinearLayout.LayoutParams(0, dp(50), 1);
        manualParams.leftMargin = dp(9);
        actions.addView(manual, manualParams);
        root.addView(actions, margin(0, 0, 0, 8));
        Button export = button("Exportar histórico (CSV)", Color.rgb(230, 231, 250));
        export.setTextColor(PRIMARY);
        export.setOnClickListener(v -> createCsvExport());
        root.addView(export, margin(0, 0, 0, 8));

        TextView privacy = text("OCR no próprio aparelho • você confere antes de salvar", 11, MUTED, false);
        root.addView(privacy, margin(2, 0, 0, 12));

        LinearLayout form = card(Color.WHITE);
        form.setPadding(dp(16), dp(16), dp(16), dp(16));
        form.addView(text("Conferir lançamento", 17, INK, true), margin(0, 0, 0, 12));
        descriptionInput = field("Descrição ou estabelecimento", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        form.addView(descriptionInput, margin(0, 0, 0, 9));
        amountInput = field("Valor em R$  •  ex.: 1.234,56", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        form.addView(amountInput, margin(0, 0, 0, 9));

        kindInput = new Spinner(this);
        String[] kinds = {BUSINESS_REVENUE, BUSINESS_EXPENSE, PERSONAL_REVENUE, PERSONAL_EXPENSE, CONTRIBUTION, BUSINESS_EXPENSE_PERSONAL, TRANSFER};
        kindInput.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, kinds));
        form.addView(kindInput, margin(0, 0, 0, 12));
        Button save = button("Salvar lançamento", GREEN);
        save.setOnClickListener(v -> saveEntry());
        form.addView(save, new LinearLayout.LayoutParams(-1, dp(50)));
        root.addView(form, margin(0, 0, 0, 14));

        LinearLayout review = card(Color.WHITE);
        review.setPadding(dp(14), dp(14), dp(14), dp(14));
        review.addView(text("Leitura do documento", 15, INK, true));
        receiptPreview = text("Nenhum documento importado. Toque em “Ler documento” para começar.", 12, MUTED, false);
        receiptPreview.setPadding(0, dp(8), 0, 0);
        review.addView(receiptPreview);
        root.addView(review, margin(0, 0, 0, 14));

        LinearLayout details = card(Color.WHITE);
        details.setPadding(dp(16), dp(15), dp(16), dp(15));
        details.addView(text("Visão geral", 16, INK, true), margin(0, 0, 0, 8));
        summary = text("Sem lançamentos.", 13, MUTED, false);
        details.addView(summary);
        root.addView(details, margin(0, 0, 0, 14));

        LinearLayout history = card(Color.WHITE);
        history.setPadding(dp(16), dp(15), dp(16), dp(15));
        history.addView(text("Últimos lançamentos", 16, INK, true), margin(0, 0, 0, 10));
        historyContainer = new LinearLayout(this);
        historyContainer.setOrientation(LinearLayout.VERTICAL);
        history.addView(historyContainer);
        root.addView(history);
    }

    private View metric(String label, String initial, boolean primary) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(text(label, 11, Color.rgb(206, 211, 239), false));
        TextView value = text(initial, 16, Color.WHITE, true);
        box.addView(value);
        if (primary) businessRevenueView = value; else expenseView = value;
        return box;
    }

    private void openReceiptPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(Intent.createChooser(intent, "Escolha uma nota ou comprovante"), REQUEST_RECEIPT_IMAGE);
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "Nenhum seletor de imagens disponível.", Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == REQUEST_EXPORT_CSV) { writeCsvExport(data.getData()); return; }
        if (requestCode != REQUEST_RECEIPT_IMAGE) return;
        Uri uri = data.getData();
        receiptPreview.setText("Analisando o documento…");
        receiptButtonState(false);
        try {
            InputImage image = InputImage.fromFilePath(this, uri);
            textRecognizer.process(image).addOnSuccessListener(visionText -> {
                if (!isFinishing()) applyReceiptResult(ReceiptParser.parse(visionText.getText()));
            }).addOnFailureListener(error -> {
                if (!isFinishing()) receiptPreview.setText("Não consegui ler esta imagem. Tente uma foto mais nítida e bem iluminada.");
            }).addOnCompleteListener(task -> { if (!isFinishing()) receiptButtonState(true); });
        } catch (IOException | RuntimeException ex) {
            receiptButtonState(true);
            receiptPreview.setText("Não foi possível abrir o arquivo selecionado.");
        }
    }

    private void receiptButtonState(boolean enabled) {
        // Keep both entry points available while the asynchronous OCR is running.
        if (receiptButton != null) receiptButton.setEnabled(enabled);
    }

    private void applyReceiptResult(ReceiptParser.Result r) {
        StringBuilder out = new StringBuilder();
        if (r.hasAmount) {
            amountInput.setText(BrazilianMoney.formatCents(r.amountCents));
            out.append("VALOR IDENTIFICADO\nR$ ").append(BrazilianMoney.formatCents(r.amountCents))
                    .append("  •  Confiança ").append(r.confidence)
                    .append("\nLinha: ").append(r.selectedLine)
                    .append("\nCategoria sugerida: ").append(r.suggestedKind)
                    .append("\n\nConfira o valor e a categoria antes de salvar.\n\n");
            String[] kinds = {BUSINESS_REVENUE, BUSINESS_EXPENSE, PERSONAL_REVENUE, PERSONAL_EXPENSE, CONTRIBUTION, BUSINESS_EXPENSE_PERSONAL, TRANSFER};
            for (int i = 0; i < kinds.length; i++) if (kinds[i].equals(r.suggestedKind)) kindInput.setSelection(i);
        } else {
            out.append("Não encontrei um total confiável. Preencha o valor manualmente e confira o texto abaixo.\n\n");
        }
        if (r.description != null && !r.description.isEmpty()) descriptionInput.setText(r.description);
        String raw = r.rawText == null ? "" : r.rawText.trim();
        if (raw.length() > 1400) raw = raw.substring(0, 1400) + "\n[prévia reduzida]";
        out.append("TEXTO RECONHECIDO\n").append(raw.isEmpty() ? "Nenhum texto legível." : raw);
        receiptPreview.setText(out.toString());
    }

    private void saveEntry() {
        String description = descriptionInput.getText().toString().trim();
        if (description.isEmpty()) { descriptionInput.setError("Informe uma descrição"); return; }
        final long cents;
        try { cents = BrazilianMoney.parseToCents(amountInput.getText().toString()); }
        catch (IllegalArgumentException ex) { amountInput.setError("Use 12,50 ou 1.234,56"); return; }
        ContentValues values = new ContentValues();
        values.put("kind", String.valueOf(kindInput.getSelectedItem()));
        values.put("amount", cents);
        values.put("description", description);
        values.put("created_at", System.currentTimeMillis());
        try {
            db.insertOrThrow("entries", null, values);
            amountInput.setText(""); descriptionInput.setText(""); kindInput.setSelection(0);
            receiptPreview.setText("Lançamento salvo. Importe outro documento ou registre uma nova movimentação.");
            refreshSummary();
            Toast.makeText(this, "Lançamento salvo.", Toast.LENGTH_SHORT).show();
        } catch (RuntimeException ex) {
            Toast.makeText(this, "Não foi possível salvar o lançamento.", Toast.LENGTH_LONG).show();
        }
    }


    private void createCsvExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, "DailyOffice-lancamentos.csv");
        try {
            startActivityForResult(intent, REQUEST_EXPORT_CSV);
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "Nenhum aplicativo disponível para salvar o arquivo.", Toast.LENGTH_LONG).show();
        }
    }

    private void writeCsvExport(Uri uri) {
        Cursor c = null;
        try {
            OutputStream stream = getContentResolver().openOutputStream(uri, "w");
            if (stream == null) throw new IOException("Não foi possível abrir o destino.");
            try (OutputStream output = stream;
                 OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
                writer.write("\uFEFF");
                writer.write("ID;Data e hora;Categoria;Valor (R$);Descrição\r\n");
                c = db.rawQuery("SELECT id,created_at,kind,amount,description FROM entries ORDER BY id ASC", null);
                while (c.moveToNext()) {
                    writer.write(String.valueOf(c.getLong(0)));
                    writer.write(';');
                    writer.write(c.getLong(1) > 0 ? CsvUtils.escape(EntryDate.format(c.getLong(1))) : "");
                    writer.write(';');
                    writer.write(CsvUtils.escape(c.getString(2)));
                    writer.write(';');
                    writer.write(CsvUtils.escape(BrazilianMoney.formatCents(c.getLong(3))));
                    writer.write(';');
                    writer.write(CsvUtils.escape(c.getString(4)));
                    writer.write("\r\n");
                }
                writer.flush();
            }
            Toast.makeText(this, "Histórico exportado em CSV.", Toast.LENGTH_LONG).show();
        } catch (IOException | RuntimeException ex) {
            Toast.makeText(this, "Falha ao exportar. Verifique o local escolhido e tente novamente.", Toast.LENGTH_LONG).show();
        } finally {
            if (c != null) c.close();
        }
    }

    private void refreshSummary() {
        long revenue = sum(BUSINESS_REVENUE);
        long expenses = safeAdd(sum(BUSINESS_EXPENSE), sum(BUSINESS_EXPENSE_PERSONAL));
        long personalIncome = sum(PERSONAL_REVENUE);
        long personalOut = sum(PERSONAL_EXPENSE);
        long contribution = sum(CONTRIBUTION);
        long result = revenue - expenses;
        businessRevenueView.setText(money(revenue));
        expenseView.setText(money(expenses));
        resultView.setText(money(result));
        summary.setText("PESSOAL\nReceitas: " + money(personalIncome) + "    Despesas: " + money(personalOut)
                + "\n\nAPORTES\nAportes pessoais: " + money(contribution)
                + "\nTransferências entre contas próprias ficam fora do faturamento."
                + "\n\nResultado empresarial = faturamento lançado − despesas empresariais lançadas."
                + "\nEste resumo não substitui conciliação bancária ou apuração fiscal.");
        refreshHistory();
    }

    private void refreshHistory() {
        if (historyContainer == null) return;
        historyContainer.removeAllViews();
        Cursor c = db.rawQuery("SELECT id,kind,amount,description,created_at FROM entries ORDER BY id DESC LIMIT 20", null);
        try {
            if (!c.moveToFirst()) {
                historyContainer.addView(text("Nenhum lançamento registrado ainda.", 13, MUTED, false));
                return;
            }
            do {
                long id = c.getLong(0);
                String kind = c.getString(1);
                long amount = c.getLong(2);
                String description = c.getString(3);
                long createdAt = c.getLong(4);
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout info = new LinearLayout(this);
                info.setOrientation(LinearLayout.VERTICAL);
                info.addView(text(description, 14, INK, true));
                info.addView(text(kind + "  •  " + money(amount), 11, MUTED, false));
                info.addView(text(EntryDate.formatOrUnknown(createdAt), 10, MUTED, false));
                row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
                Button remove = button("Excluir", Color.rgb(252, 235, 237));
                remove.setTextColor(Color.rgb(176, 55, 70));
                remove.setTextSize(11);
                remove.setOnClickListener(v -> confirmDelete(id, description));
                row.addView(remove, new LinearLayout.LayoutParams(-2, dp(38)));
                historyContainer.addView(row, margin(0, 0, 0, 8));
                if (c.getPosition() < c.getCount() - 1) {
                    View divider = new View(this);
                    divider.setBackgroundColor(Color.rgb(235, 237, 243));
                    historyContainer.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
                }
            } while (c.moveToNext());
        } finally {
            c.close();
        }
    }

    private void confirmDelete(long id, String description) {
        new AlertDialog.Builder(this)
                .setTitle("Excluir lançamento?")
                .setMessage("O registro \"" + description + "\" será removido e os totais serão atualizados.")
                .setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Excluir", (dialog, which) -> {
                    db.delete("entries", "id=?", new String[]{String.valueOf(id)});
                    refreshSummary();
                    Toast.makeText(this, "Lançamento excluído.", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private long sum(String kind) {
        Cursor c = db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM entries WHERE kind=?", new String[]{kind});
        try { return c.moveToFirst() ? c.getLong(0) : 0L; } finally { c.close(); }
    }
    private long safeAdd(long a, long b) { try { return Math.addExact(a, b); } catch (ArithmeticException e) { return Long.MAX_VALUE; } }
    private String money(long cents) { return "R$ " + BrazilianMoney.formatCents(cents); }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private LinearLayout card(int color) {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(color); bg.setCornerRadius(dp(18));
        l.setBackground(bg); l.setElevation(dp(2)); return l;
    }
    private EditText field(String hint, int inputType) {
        EditText e = new EditText(this); e.setSingleLine(true); e.setTextSize(14); e.setHint(hint);
        e.setInputType(inputType); e.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.rgb(247,248,252));
        bg.setCornerRadius(dp(11)); bg.setStroke(dp(1), Color.rgb(230,233,241)); e.setBackground(bg);
        return e;
    }
    private Button button(String label, int color) {
        Button b = new Button(this); b.setText(label); b.setTextSize(13); b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setTextColor(color == Color.rgb(230,231,250) ? PRIMARY : Color.WHITE);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(color); bg.setCornerRadius(dp(13)); b.setBackground(bg);
        b.setPadding(dp(8), 0, dp(8), 0); return b;
    }
    private LinearLayout.LayoutParams margin(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(dp(left), dp(top), dp(right), dp(bottom)); return p;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onDestroy() {
        if (textRecognizer != null) textRecognizer.close();
        if (db != null && db.isOpen()) db.close();
        super.onDestroy();
    }
}
