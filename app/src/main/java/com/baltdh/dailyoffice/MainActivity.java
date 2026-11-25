package com.baltdh.dailyoffice;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.ClipData;
import android.content.Intent;
import android.provider.MediaStore;
import androidx.core.content.FileProvider;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int REQUEST_RECEIPT_IMAGE = 431;
    private static final int REQUEST_EXPORT_CSV = 432;
    private static final int REQUEST_CAPTURE_PHOTO = 433;
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
    private ScrollView mainScroll;
    private EditText descriptionInput, amountInput, paymentInput;
    private Spinner kindInput;
    private Button receiptButton;
    private EditText searchInput;
    private String historyFilter = "Todos";
    private TextView pendingView, receivableView, personalExpensesView, meiUsedView, meiRemainingView, archivedCountView;
    private View meiProgressView;
    private static final long MEI_PERIOD_LIMIT_CENTS = 3375000L;
    private TextRecognizer textRecognizer;
    private Uri cameraPhotoUri;
    private File cameraPhotoFile;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(246, 247, 251));
        getWindow().setNavigationBarColor(Color.rgb(246, 247, 251));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        db = openOrCreateDatabase("dailyoffice.db", MODE_PRIVATE, null);
        db.execSQL("CREATE TABLE IF NOT EXISTS entries(id INTEGER PRIMARY KEY AUTOINCREMENT,kind TEXT NOT NULL,amount INTEGER NOT NULL,description TEXT NOT NULL)");
        ensureCreatedAtColumn();
        ensurePaymentMethodColumn();
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        if (state != null) {
            String savedUri = state.getString("camera_photo_uri");
            if (savedUri != null) cameraPhotoUri = Uri.parse(savedUri);
            String savedPath = state.getString("camera_photo_path");
            if (savedPath != null) cameraPhotoFile = new File(savedPath);
        }
        buildScreen();
        refreshSummary();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (cameraPhotoUri != null) outState.putString("camera_photo_uri", cameraPhotoUri.toString());
        if (cameraPhotoFile != null) outState.putString("camera_photo_path", cameraPhotoFile.getAbsolutePath());
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

    private void ensurePaymentMethodColumn() {
        boolean found = false;
        Cursor columns = db.rawQuery("PRAGMA table_info(entries)", null);
        try {
            while (columns.moveToNext()) if ("payment_method".equals(columns.getString(1))) { found = true; break; }
        } finally { columns.close(); }
        if (!found) db.execSQL("ALTER TABLE entries ADD COLUMN payment_method TEXT NOT NULL DEFAULT ''");
    }

    private void buildScreen() {
        FrameLayout screen = new FrameLayout(this);
        ScrollView scroll = new ScrollView(this);
        mainScroll = scroll;
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        final int baseLeft = dp(18), baseTop = dp(22), baseRight = dp(18), baseBottom = dp(112);
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
        screen.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        // Persistent floating camera action, centered at the bottom as in the earlier layout.
        Button cameraFab = button("●", PRIMARY);
        receiptButton = cameraFab;
        cameraFab.setTextSize(25);
        cameraFab.setGravity(Gravity.CENTER);
        GradientDrawable fabBackground = new GradientDrawable();
        fabBackground.setShape(GradientDrawable.OVAL);
        fabBackground.setColor(PRIMARY);
        cameraFab.setBackground(fabBackground);
        cameraFab.setElevation(dp(8));
        cameraFab.setContentDescription("Tirar foto do documento");
        cameraFab.setOnClickListener(v -> captureReceiptPhoto());
        FrameLayout.LayoutParams fabParams = new FrameLayout.LayoutParams(dp(64), dp(64), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        fabParams.bottomMargin = dp(18);
        screen.addView(cameraFab, fabParams);
        setContentView(screen);

        TextView eyebrow = text("GESTÃO FINANCEIRA", 11, PRIMARY, true);
        root.addView(eyebrow);
        TextView title = text("DailyOffice", 29, INK, true);
        title.setPadding(0, dp(3), 0, 0);
        root.addView(title);
        TextView subtitle = text("Seu negócio e suas contas em ordem.", 14, MUTED, false);
        subtitle.setPadding(0, 0, 0, dp(18));
        root.addView(subtitle);

        root.addView(text("Visão geral", 22, INK, true), margin(0, 0, 0, 12));
        LinearLayout rowOne = new LinearLayout(this); rowOne.setOrientation(LinearLayout.HORIZONTAL);
        rowOne.addView(dashboardTile("Receitas", 0), new LinearLayout.LayoutParams(0, -2, 1));
        rowOne.addView(dashboardTile("Despesas empresa", 1), new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(rowOne, margin(0, 0, 0, 10));
        LinearLayout rowTwo = new LinearLayout(this); rowTwo.setOrientation(LinearLayout.HORIZONTAL);
        rowTwo.addView(dashboardTile("Pendentes", 2), new LinearLayout.LayoutParams(0, -2, 1));
        rowTwo.addView(dashboardTile("A receber", 3), new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(rowTwo, margin(0, 0, 0, 10));
        root.addView(dashboardTile("Gastos pessoais identificados", 4), margin(0, 0, 0, 10));
        LinearLayout resultCard = card(Color.rgb(255,255,255));
        resultCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        resultCard.addView(text("Resultado empresarial", 13, INK, true));
        resultView = text("R$ 0,00", 19, INK, true);
        resultView.setPadding(0, dp(5), 0, 0);
        resultCard.addView(resultView);
        root.addView(resultCard, margin(0, 0, 0, 10));
        LinearLayout meiCard = card(Color.WHITE); meiCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        meiCard.addView(text("Limite MEI", 17, INK, true));
        meiUsedView = text("R$ 0,00 de R$ 33.750,00", 19, INK, true);
        meiUsedView.setPadding(0, dp(8), 0, dp(8)); meiCard.addView(meiUsedView);
        android.widget.ProgressBar meiBar = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        meiBar.setMax(10000); meiBar.setProgress(0); meiProgressView = meiBar;
        meiCard.addView(meiBar, new LinearLayout.LayoutParams(-1, dp(7)));
        meiRemainingView = text("Restante: R$ 33.750,00", 14, INK, true);
        meiRemainingView.setPadding(0, dp(8), 0, 0); meiCard.addView(meiRemainingView);
        root.addView(meiCard, margin(0, 0, 0, 12));
        root.addView(text("Pendências e contas a receber ainda não possuem controle próprio. O limite exibido é o valor proporcional configurado para este período.", 10, MUTED, false), margin(2, 0, 0, 14));


        TextView section = text("Lançamentos", 18, INK, true);
        root.addView(section, margin(0, 0, 0, 8));
        LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        Button choose = button("Galeria", Color.rgb(230, 231, 250)); choose.setTextColor(PRIMARY);
        choose.setOnClickListener(v -> openReceiptPicker());
        actions.addView(choose, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button export = button("Exportar CSV", Color.rgb(230, 231, 250)); export.setTextColor(PRIMARY);
        export.setOnClickListener(v -> createCsvExport());
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(0, dp(48), 1); ep.leftMargin = dp(8);
        actions.addView(export, ep); root.addView(actions, margin(0, 0, 0, 12));

        TextView privacy = text("OCR no próprio aparelho • você confere antes de salvar", 11, MUTED, false);
        root.addView(privacy, margin(2, 0, 0, 12));

        LinearLayout form = card(Color.WHITE);
        form.setPadding(dp(16), dp(16), dp(16), dp(16));
        form.addView(text("Conferir lançamento", 17, INK, true), margin(0, 0, 0, 12));
        descriptionInput = field("Descrição ou estabelecimento", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        form.addView(descriptionInput, margin(0, 0, 0, 9));
        amountInput = field("Valor em R$  •  ex.: 1.234,56", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        form.addView(amountInput, margin(0, 0, 0, 9));
        paymentInput = field("Forma de pagamento (Pix, débito, crédito...)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        form.addView(paymentInput, margin(0, 0, 0, 9));

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
        receiptPreview = text("Nenhum documento importado. Tire uma foto ou escolha uma imagem para começar.", 12, MUTED, false);
        receiptPreview.setPadding(0, dp(8), 0, 0);
        review.addView(receiptPreview);
        root.addView(review, margin(0, 0, 0, 14));

        LinearLayout details = card(Color.WHITE);
        details.setPadding(dp(16), dp(15), dp(16), dp(15));
        details.addView(text("Visão geral", 16, INK, true), margin(0, 0, 0, 8));
        summary = text("Sem lançamentos.", 13, MUTED, false);
        details.addView(summary);
        root.addView(details, margin(0, 0, 0, 14));

        searchInput = field("Buscar comprovante", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        root.addView(searchInput, margin(0, 0, 0, 8));
        LinearLayout filters = new LinearLayout(this); filters.setOrientation(LinearLayout.HORIZONTAL);
        String[] filterNames = {"Todos", "Empresa", "Pessoal", "Mistos"};
        for (String filter : filterNames) {
            Button chip = button(filter, Color.rgb(230,231,250)); chip.setTextSize(11); chip.setTextColor(PRIMARY);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(40), 1); cp.setMargins(0,0,dp(4),0);
            filters.addView(chip, cp); chip.setOnClickListener(v -> { historyFilter = filter; refreshHistory(); });
        }
        root.addView(filters, margin(0, 0, 0, 12));
        LinearLayout history = card(Color.WHITE); history.setPadding(dp(16), dp(15), dp(16), dp(15));
        archivedCountView = text("Comprovantes arquivados (0)", 18, INK, true);
        history.addView(archivedCountView, margin(0, 0, 0, 10));
        historyContainer = new LinearLayout(this);
        historyContainer.setOrientation(LinearLayout.VERTICAL);
        history.addView(historyContainer);
        root.addView(history);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refreshHistory(); }
            @Override public void afterTextChanged(Editable e) {}
        });
    }

    private View dashboardTile(String label, int slot) {
        LinearLayout tile = card(Color.WHITE);
        tile.setPadding(dp(14), dp(12), dp(14), dp(12));
        tile.addView(text(label, 13, INK, true));
        TextView value = text("R$ 0,00", 19, INK, true);
        value.setPadding(0, dp(5), 0, 0);
        tile.addView(value);
        if (slot == 0) businessRevenueView = value;
        else if (slot == 1) expenseView = value;
        else if (slot == 2) pendingView = value;
        else if (slot == 3) receivableView = value;
        else if (slot == 4) personalExpensesView = value;
        return tile;
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

    private void captureReceiptPhoto() {
        try {
            cameraPhotoFile = File.createTempFile("dailyoffice_receipt_", ".jpg", getCacheDir());
            cameraPhotoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", cameraPhotoFile);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri);
            // Some camera apps require ClipData as well as URI grant flags for EXTRA_OUTPUT.
            intent.setClipData(ClipData.newUri(getContentResolver(), "DailyOffice photo", cameraPhotoUri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (intent.resolveActivity(getPackageManager()) == null) {
                Toast.makeText(this, "Nenhum aplicativo de câmera disponível.", Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(this, "Tire a foto e confirme para iniciar a leitura.", Toast.LENGTH_SHORT).show();
            startActivityForResult(intent, REQUEST_CAPTURE_PHOTO);
        } catch (IOException | RuntimeException ex) {
            cameraPhotoUri = null;
            cameraPhotoFile = null;
            Toast.makeText(this, "Não foi possível preparar a captura: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
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
        if (requestCode == REQUEST_EXPORT_CSV) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) writeCsvExport(data.getData());
            return;
        }
        if (requestCode == REQUEST_CAPTURE_PHOTO) {
            if (resultCode != RESULT_OK) {
                receiptPreview.setText("A captura foi cancelada. Nenhum lançamento foi criado.");
                Toast.makeText(this, "Foto cancelada.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (cameraPhotoFile == null || !cameraPhotoFile.exists() || cameraPhotoFile.length() == 0L) {
                Object extra = data != null && data.getExtras() != null ? data.getExtras().get("data") : null;
                Bitmap thumbnail = extra instanceof Bitmap ? (Bitmap) extra : null;
                if (thumbnail != null) {
                    try {
                        if (cameraPhotoFile == null) cameraPhotoFile = File.createTempFile("dailyoffice_receipt_", ".jpg", getCacheDir());
                        try (FileOutputStream output = new FileOutputStream(cameraPhotoFile)) {
                            thumbnail.compress(Bitmap.CompressFormat.JPEG, 95, output);
                        }
                        cameraPhotoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", cameraPhotoFile);
                    } catch (IOException | RuntimeException ex) {
                        receiptPreview.setText("A câmera confirmou a foto, mas não entregou o arquivo para leitura. Tente novamente ou use Galeria.");
                        Toast.makeText(this, "Não recebi o arquivo da câmera.", Toast.LENGTH_LONG).show();
                        return;
                    }
                } else {
                    receiptPreview.setText("A câmera confirmou a foto, mas o arquivo recebido está vazio. Tente novamente ou use Galeria.");
                    Toast.makeText(this, "A foto não chegou ao DailyOffice.", Toast.LENGTH_LONG).show();
                    return;
                }
            }
            if (cameraPhotoUri == null) {
                receiptPreview.setText("Não consegui localizar a imagem capturada. Tente novamente.");
                return;
            }
            Toast.makeText(this, "Foto recebida. Iniciando reconhecimento de texto…", Toast.LENGTH_SHORT).show();
            processReceiptImage(cameraPhotoUri);
            return;
        }
        if (requestCode != REQUEST_RECEIPT_IMAGE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        processReceiptImage(data.getData());
    }

    private void processReceiptImage(Uri uri) {
        receiptPreview.setText("Foto recebida. Analisando o documento com OCR…");
        scrollToReceiptPreview();
        receiptButtonState(false);
        try {
            InputImage image = InputImage.fromFilePath(this, uri);
            textRecognizer.process(image)
                    .addOnSuccessListener(visionText -> {
                        if (isFinishing()) return;
                        String recognized = visionText == null ? "" : extractStructuredOcrText(visionText);
                        if (recognized == null || recognized.trim().isEmpty()) {
                            receiptPreview.setText("A imagem foi aberta, mas o OCR não encontrou texto legível. Tente enquadrar a nota inteira, aproximar a câmera e melhorar a iluminação.");
                            Toast.makeText(this, "Nenhum texto foi identificado. Tente outra foto.", Toast.LENGTH_LONG).show();
                        } else {
                            applyReceiptResult(ReceiptParser.parse(recognized));
                            Toast.makeText(this, "Leitura concluída. Confira o valor antes de salvar.", Toast.LENGTH_LONG).show();
                        }
                        scrollToReceiptPreview();
                    })
                    .addOnFailureListener(error -> {
                        if (!isFinishing()) {
                            String detail = error.getLocalizedMessage();
                            receiptPreview.setText("Falha no reconhecimento da imagem."
                                    + (detail == null ? "" : "\nDetalhe: " + detail)
                                    + "\nTente novamente ou selecione a foto pela Galeria.");
                            Toast.makeText(this, "Falha ao processar a foto.", Toast.LENGTH_LONG).show();
                            scrollToReceiptPreview();
                        }
                    })
                    .addOnCompleteListener(task -> { if (!isFinishing()) receiptButtonState(true); });
        } catch (IOException | RuntimeException ex) {
            receiptButtonState(true);
            receiptPreview.setText("Não foi possível abrir a foto para leitura."
                    + (ex.getLocalizedMessage() == null ? "" : "\nDetalhe: " + ex.getLocalizedMessage()));
            Toast.makeText(this, "Não consegui abrir a imagem.", Toast.LENGTH_LONG).show();
            scrollToReceiptPreview();
        }
    }

    /**
     * Rebuild OCR text using the physical positions of ML Kit lines.
     * Lines sharing the same visual row are joined from left to right, which
     * keeps labels and amounts together when a receipt uses two columns.
     */
    private String extractStructuredOcrText(Text visionText) {
        final class PositionedLine {
            final String value;
            final Rect bounds;
            PositionedLine(String value, Rect bounds) {
                this.value = value;
                this.bounds = bounds;
            }
        }

        List<PositionedLine> lines = new ArrayList<>();
        for (Text.TextBlock block : visionText.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                String value = line.getText() == null ? "" : line.getText().trim();
                Rect bounds = line.getBoundingBox();
                if (!value.isEmpty() && bounds != null) {
                    lines.add(new PositionedLine(value, new Rect(bounds)));
                }
            }
        }
        if (lines.isEmpty()) return visionText.getText();

        Collections.sort(lines, (a, b) -> {
            int vertical = Integer.compare(a.bounds.top, b.bounds.top);
            if (Math.abs(a.bounds.top - b.bounds.top) <= 8) {
                return Integer.compare(a.bounds.left, b.bounds.left);
            }
            return vertical;
        });

        StringBuilder ordered = new StringBuilder();
        List<PositionedLine> row = new ArrayList<>();
        int rowCenter = Integer.MIN_VALUE;
        int rowTolerance = 10;
        for (PositionedLine line : lines) {
            int center = (line.bounds.top + line.bounds.bottom) / 2;
            int height = Math.max(1, line.bounds.height());
            int tolerance = Math.max(rowTolerance, height / 2);
            if (!row.isEmpty() && Math.abs(center - rowCenter) > tolerance) {
                appendOcrRow(ordered, row);
                row.clear();
            }
            if (row.isEmpty()) rowCenter = center;
            else rowCenter = (rowCenter * (row.size()) + center) / (row.size() + 1);
            row.add(line);
        }
        if (!row.isEmpty()) appendOcrRow(ordered, row);
        String result = ordered.toString().trim();
        return result.isEmpty() ? visionText.getText() : result;
    }

    private void appendOcrRow(StringBuilder output, List<?> positionedLines) {
        // The list is built in left-to-right order after the visual-row grouping.
        for (Object item : positionedLines) {
            try {
                java.lang.reflect.Field valueField = item.getClass().getDeclaredField("value");
                valueField.setAccessible(true);
                String value = (String) valueField.get(item);
                if (output.length() > 0) output.append("  ");
                output.append(value);
            } catch (ReflectiveOperationException ignored) {
                // The fallback below is provided by ML Kit's original full text.
            }
        }
        output.append('\n');
    }

    private void scrollToReceiptPreview() {
        if (mainScroll == null || receiptPreview == null) return;
        receiptPreview.post(() -> {
            int[] previewLocation = new int[2];
            int[] scrollLocation = new int[2];
            receiptPreview.getLocationOnScreen(previewLocation);
            mainScroll.getLocationOnScreen(scrollLocation);
            int target = mainScroll.getScrollY() + previewLocation[1] - scrollLocation[1] - dp(70);
            mainScroll.smoothScrollTo(0, Math.max(0, target));
        });
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
                    .append("\nForma de pagamento: ").append(r.paymentMethod.isEmpty() ? "Não identificada" : r.paymentMethod)
                    .append("\nCategoria sugerida: ").append(r.suggestedKind)
                    .append("\n\nConfira o valor e a categoria antes de salvar.\n\n");
            if (!r.paymentMethod.isEmpty()) paymentInput.setText(r.paymentMethod);
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
        values.put("payment_method", paymentInput.getText().toString().trim());
        values.put("created_at", System.currentTimeMillis());
        try {
            db.insertOrThrow("entries", null, values);
            amountInput.setText(""); descriptionInput.setText(""); paymentInput.setText(""); kindInput.setSelection(0);
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
                writer.write("ID;Data e hora;Categoria;Valor (R$);Descrição;Forma de pagamento\r\n");
                c = db.rawQuery("SELECT id,created_at,kind,amount,description,payment_method FROM entries ORDER BY id ASC", null);
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
                    writer.write(';');
                    writer.write(CsvUtils.escape(c.getString(5)));
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
        personalExpensesView.setText(money(personalOut));
        resultView.setText(money(result));
        long remaining = Math.max(0L, MEI_PERIOD_LIMIT_CENTS - revenue);
        meiUsedView.setText(money(revenue) + " de " + money(MEI_PERIOD_LIMIT_CENTS));
        meiRemainingView.setText("Restante: " + money(remaining));
        if (meiProgressView instanceof android.widget.ProgressBar) {
            int progress = (int)Math.min(10000L, revenue * 10000L / MEI_PERIOD_LIMIT_CENTS);
            ((android.widget.ProgressBar)meiProgressView).setProgress(progress);
        }
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
        Cursor c = db.rawQuery("SELECT id,kind,amount,description,created_at,payment_method FROM entries ORDER BY id DESC", null);
        int shown = 0;
        String query = searchInput == null ? "" : searchInput.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
        try {
            if (!c.moveToFirst()) {
                archivedCountView.setText("Comprovantes arquivados (0)");
                historyContainer.addView(text("Nenhum lançamento registrado ainda.", 13, MUTED, false));
                return;
            }
            do {
                String kind = c.getString(1);
                String description = c.getString(3);
                boolean business = kind.equals(BUSINESS_REVENUE) || kind.equals(BUSINESS_EXPENSE) || kind.equals(BUSINESS_EXPENSE_PERSONAL);
                boolean personal = kind.equals(PERSONAL_REVENUE) || kind.equals(PERSONAL_EXPENSE) || kind.equals(CONTRIBUTION);
                boolean matchesFilter = historyFilter.equals("Todos") || (historyFilter.equals("Empresa") && business)
                        || (historyFilter.equals("Pessoal") && personal) || (historyFilter.equals("Mistos") && kind.equals(TRANSFER));
                boolean matchesQuery = query.isEmpty() || description.toLowerCase(java.util.Locale.ROOT).contains(query)
                        || kind.toLowerCase(java.util.Locale.ROOT).contains(query);
                if (!matchesFilter || !matchesQuery) continue;
                if (shown >= 20) break;
                shown++;
                long id = c.getLong(0);
                long amount = c.getLong(2);
                long createdAt = c.getLong(4);
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout info = new LinearLayout(this);
                info.setOrientation(LinearLayout.VERTICAL);
                info.addView(text(description, 14, INK, true));
                info.addView(text(kind + "  •  " + money(amount), 11, MUTED, false));
                info.addView(text(EntryDate.formatOrUnknown(createdAt), 10, MUTED, false));
                String paymentMethod = c.getString(5);
                if (paymentMethod != null && !paymentMethod.isEmpty()) info.addView(text("Pagamento: " + paymentMethod, 10, MUTED, false));
                row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
                Button remove = button("Excluir", Color.rgb(252, 235, 237));
                remove.setTextColor(Color.rgb(176, 55, 70));
                remove.setTextSize(11);
                remove.setOnClickListener(v -> confirmDelete(id, description));
                row.addView(remove, new LinearLayout.LayoutParams(-2, dp(38)));
                historyContainer.addView(row, margin(0, 0, 0, 8));
                if (shown < 20) {
                    View divider = new View(this);
                    divider.setBackgroundColor(Color.rgb(235, 237, 243));
                    historyContainer.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
                }
            } while (c.moveToNext());
            archivedCountView.setText("Comprovantes arquivados (" + shown + ")");
            if (shown == 0) historyContainer.addView(text("Nenhum resultado encontrado.", 13, MUTED, false));
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
