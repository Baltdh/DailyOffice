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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Rect;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
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
import android.widget.ImageView;
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
import java.io.InputStream;
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
    private ImageView receiptImagePreview;
    private LinearLayout receiptImageChoices;
    private Button scannedOptionButton, originalOptionButton, historyLoadMoreButton;
    private boolean showingOriginalReceipt = false;
    private LinearLayout historyContainer;
    private ScrollView mainScroll;
    private EditText descriptionInput, amountInput, paymentInput, detailsInput;
    private Spinner kindInput;
    private Button receiptButton;
    private EditText searchInput;
    private String historyFilter = "Todos";
    private int historyLoadedCount = 0;
    private static final int HISTORY_PAGE_SIZE = 50;
    private TextView pendingView, receivableView, personalExpensesView, meiUsedView, meiRemainingView, archivedCountView;
    private View meiProgressView;
    private static final long MEI_PERIOD_LIMIT_CENTS = 3375000L;
    private TextRecognizer textRecognizer;
    private Uri cameraPhotoUri;
    private Uri currentReceiptUri;
    private Bitmap currentReceiptBitmap;
    private Bitmap currentScannedBitmap;
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
        ensurePurchaseDetailsColumn();
        ensureReceiptImageColumn();
        ensureReceiptVariantsColumns();
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        if (state != null) {
            String savedUri = state.getString("camera_photo_uri");
            if (savedUri != null) cameraPhotoUri = Uri.parse(savedUri);
            String savedReceipt = state.getString("receipt_image_uri");
            if (savedReceipt != null) currentReceiptUri = Uri.parse(savedReceipt);
            String savedPath = state.getString("camera_photo_path");
            if (savedPath != null) cameraPhotoFile = new File(savedPath);
        }
        buildScreen();
        if (currentReceiptUri != null) showReceiptImage(currentReceiptUri);
        refreshSummary();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (cameraPhotoUri != null) outState.putString("camera_photo_uri", cameraPhotoUri.toString());
        if (currentReceiptUri != null) outState.putString("receipt_image_uri", currentReceiptUri.toString());
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

    /** Adds purchase details without deleting older database records. */
    private void ensurePurchaseDetailsColumn() {
        boolean found = false;
        Cursor columns = db.rawQuery("PRAGMA table_info(entries)", null);
        try {
            while (columns.moveToNext()) if ("purchase_details".equals(columns.getString(1))) { found = true; break; }
        } finally { columns.close(); }
        if (!found) db.execSQL("ALTER TABLE entries ADD COLUMN purchase_details TEXT NOT NULL DEFAULT ''");
    }

    /** Adds the private receipt-image path without altering existing entries. */
    private void ensureReceiptImageColumn() {
        boolean found = false;
        Cursor columns = db.rawQuery("PRAGMA table_info(entries)", null);
        try {
            while (columns.moveToNext()) if ("receipt_image_path".equals(columns.getString(1))) { found = true; break; }
        } finally { columns.close(); }
        if (!found) db.execSQL("ALTER TABLE entries ADD COLUMN receipt_image_path TEXT NOT NULL DEFAULT ''");
    }

    /** Adds separate paths for the unchanged original and the reading-optimized scan. */
    private void ensureReceiptVariantsColumns() {
        ensureTextColumn("receipt_original_path");
        ensureTextColumn("receipt_scanned_path");
    }

    private void ensureTextColumn(String columnName) {
        boolean found = false;
        Cursor columns = db.rawQuery("PRAGMA table_info(entries)", null);
        try {
            while (columns.moveToNext()) if (columnName.equals(columns.getString(1))) { found = true; break; }
        } finally { columns.close(); }
        if (!found) db.execSQL("ALTER TABLE entries ADD COLUMN " + columnName + " TEXT NOT NULL DEFAULT ''");
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
        Button cameraFab = button("📷", PRIMARY);
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
        detailsInput = new EditText(this);
        detailsInput.setSingleLine(false);
        detailsInput.setMinLines(3);
        detailsInput.setMaxLines(6);
        detailsInput.setGravity(Gravity.TOP | Gravity.START);
        detailsInput.setTextSize(14);
        detailsInput.setHint("Itens e detalhes identificados na compra");
        detailsInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        detailsInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable detailsBg = new GradientDrawable();
        detailsBg.setColor(Color.rgb(247,248,252)); detailsBg.setCornerRadius(dp(11));
        detailsBg.setStroke(dp(1), Color.rgb(230,233,241)); detailsInput.setBackground(detailsBg);
        form.addView(detailsInput, margin(0, 0, 0, 12));

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
        review.addView(text("Comprovante digitalizado", 15, INK, true));
        receiptImagePreview = new ImageView(this);
        receiptImagePreview.setAdjustViewBounds(true);
        receiptImagePreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        receiptImagePreview.setMaxHeight(dp(300));
        receiptImagePreview.setBackgroundColor(Color.rgb(247,248,252));
        receiptImagePreview.setVisibility(View.GONE);
        receiptImagePreview.setContentDescription("Pré-visualização do comprovante digitalizado. Toque para ampliar.");
        receiptImagePreview.setOnClickListener(v -> showReceiptFullScreen());
        review.addView(receiptImagePreview, margin(0, 10, 0, 8));
        LinearLayout imageChoices = new LinearLayout(this);
        imageChoices.setOrientation(LinearLayout.HORIZONTAL);
        scannedOptionButton = button("Digitalizada • alto contraste", PRIMARY);
        originalOptionButton = button("Foto original", Color.rgb(230,231,250));
        originalOptionButton.setTextColor(PRIMARY);
        imageChoices.addView(scannedOptionButton, new LinearLayout.LayoutParams(0, dp(42), 1));
        LinearLayout.LayoutParams originalParams = new LinearLayout.LayoutParams(0, dp(42), 1);
        originalParams.leftMargin = dp(8);
        imageChoices.addView(originalOptionButton, originalParams);
        scannedOptionButton.setOnClickListener(v -> setReceiptPreviewMode(false));
        originalOptionButton.setOnClickListener(v -> setReceiptPreviewMode(true));
        imageChoices.setVisibility(View.GONE);
        review.addView(imageChoices, margin(0, 0, 0, 8));
        receiptImageChoices = imageChoices;
        TextView readLabel = text("Leitura e dados identificados", 14, INK, true);
        review.addView(readLabel, margin(0, 4, 0, 0));
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
        archivedCountView = text("Histórico de lançamentos", 18, INK, true);
        history.addView(archivedCountView, margin(0, 0, 0, 10));
        historyContainer = new LinearLayout(this);
        historyContainer.setOrientation(LinearLayout.VERTICAL);
        history.addView(historyContainer);
        historyLoadMoreButton = button("Carregar mais lançamentos", Color.rgb(230,231,250));
        historyLoadMoreButton.setTextColor(PRIMARY);
        historyLoadMoreButton.setVisibility(View.GONE);
        historyLoadMoreButton.setOnClickListener(v -> loadMoreHistory());
        history.addView(historyLoadMoreButton, margin(0, 10, 0, 0));
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
        boolean unavailable = slot == 2 || slot == 3;
        TextView value = text(unavailable ? "Sem controle" : "R$ 0,00",
                unavailable ? 13 : 19, unavailable ? MUTED : INK, true);
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
        Uri selectedImage = data.getData();
        try {
            int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            if (flags != 0) getContentResolver().takePersistableUriPermission(selectedImage, flags);
        } catch (SecurityException ignored) { /* The current session can still read the selected image. */ }
        processReceiptImage(selectedImage);
    }

    private void processReceiptImage(Uri uri) {
        currentReceiptUri = uri;
        showReceiptImage(uri);
        // A new scan replaces previous suggestions so an old payment method or amount is never reused accidentally.
        descriptionInput.setText("");
        amountInput.setText("");
        paymentInput.setText("");
        detailsInput.setText("");
        receiptPreview.setText("Imagem carregada. Analisando o documento com OCR…");
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
    private static final class PositionedOcrLine {
        final String value;
        final Rect bounds;
        PositionedOcrLine(String value, Rect bounds) {
            this.value = value;
            this.bounds = bounds;
        }
    }

    /**
     * Rebuild OCR text using ML Kit line coordinates. Labels and amounts that
     * appear side by side are joined in visual left-to-right order.
     */
    private String extractStructuredOcrText(Text visionText) {
        List<PositionedOcrLine> lines = new ArrayList<>();
        for (Text.TextBlock block : visionText.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                String value = line.getText() == null ? "" : line.getText().trim();
                Rect bounds = line.getBoundingBox();
                if (!value.isEmpty() && bounds != null) {
                    lines.add(new PositionedOcrLine(value, new Rect(bounds)));
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
        List<PositionedOcrLine> row = new ArrayList<>();
        int rowCenter = Integer.MIN_VALUE;
        for (PositionedOcrLine line : lines) {
            int center = (line.bounds.top + line.bounds.bottom) / 2;
            int tolerance = Math.max(10, Math.max(1, line.bounds.height()) / 2);
            if (!row.isEmpty() && Math.abs(center - rowCenter) > tolerance) {
                appendOcrRow(ordered, row);
                row.clear();
            }
            if (row.isEmpty()) rowCenter = center;
            else rowCenter = (rowCenter * row.size() + center) / (row.size() + 1);
            row.add(line);
        }
        if (!row.isEmpty()) appendOcrRow(ordered, row);
        String result = ordered.toString().trim();
        return result.isEmpty() ? visionText.getText() : result;
    }

    private void appendOcrRow(StringBuilder output, List<PositionedOcrLine> row) {
        Collections.sort(row, Comparator.comparingInt(line -> line.bounds.left));
        for (PositionedOcrLine line : row) {
            if (output.length() > 0) output.append("  ");
            output.append(line.value);
        }
        output.append('\n');
    }

    private void showReceiptImage(Uri uri) {
        if (receiptImagePreview == null || uri == null) return;
        try {
            receiptImagePreview.setImageDrawable(null);
            recycleReceiptBitmaps();
            currentReceiptBitmap = decodeReceiptBitmap(uri, 2000);
            currentScannedBitmap = createScannedCopy(currentReceiptBitmap);
            showingOriginalReceipt = false;
            receiptImagePreview.setVisibility(View.VISIBLE);
            if (receiptImageChoices != null) receiptImageChoices.setVisibility(View.VISIBLE);
            setReceiptPreviewMode(false);
            receiptImagePreview.setOnClickListener(v -> showReceiptFullScreen());
        } catch (IOException | RuntimeException ex) {
            recycleReceiptBitmaps();
            receiptImagePreview.setImageDrawable(null);
            receiptImagePreview.setVisibility(View.GONE);
            if (receiptImageChoices != null) receiptImageChoices.setVisibility(View.GONE);
            Toast.makeText(this, "Não foi possível exibir a imagem, mas o OCR ainda pode ser consultado.", Toast.LENGTH_LONG).show();
        }
    }

    private void recycleReceiptBitmaps() {
        if (currentReceiptBitmap != null && !currentReceiptBitmap.isRecycled()) currentReceiptBitmap.recycle();
        if (currentScannedBitmap != null && !currentScannedBitmap.isRecycled()) currentScannedBitmap.recycle();
        currentReceiptBitmap = null;
        currentScannedBitmap = null;
    }

    /** Creates a grayscale, higher-contrast reading copy without modifying the source photo. */
    private Bitmap createScannedCopy(Bitmap source) {
        if (source == null || source.isRecycled()) throw new IllegalArgumentException("Foto original indisponível.");
        Bitmap scan = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(scan);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        ColorMatrix grayscale = new ColorMatrix();
        grayscale.setSaturation(0f);
        ColorMatrix contrast = new ColorMatrix(new float[]{
                1.38f, 0f, 0f, 0f, -42f,
                0f, 1.38f, 0f, 0f, -42f,
                0f, 0f, 1.38f, 0f, -42f,
                0f, 0f, 0f, 1f, 0f
        });
        grayscale.postConcat(contrast);
        paint.setColorFilter(new ColorMatrixColorFilter(grayscale));
        canvas.drawBitmap(source, 0f, 0f, paint);
        paint.setColorFilter(null);
        return scan;
    }

    private void setReceiptPreviewMode(boolean original) {
        if (currentReceiptBitmap == null || currentScannedBitmap == null || receiptImagePreview == null) return;
        showingOriginalReceipt = original;
        receiptImagePreview.setImageBitmap(original ? currentReceiptBitmap : currentScannedBitmap);
        receiptImagePreview.setContentDescription(original
                ? "Foto original do comprovante. Toque para ampliar."
                : "Cópia digitalizada em escala de cinza e alto contraste. Toque para ampliar.");
        styleReceiptChoice(scannedOptionButton, !original);
        styleReceiptChoice(originalOptionButton, original);
    }

    /** Decodes a downscaled copy to keep high-resolution phone photos from exhausting memory. */
    private Bitmap decodeReceiptBitmap(Uri uri, int maxDimension) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream stream = getContentResolver().openInputStream(uri)) {
            if (stream == null) throw new IOException("Imagem indisponível.");
            BitmapFactory.decodeStream(stream, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("Formato de imagem inválido.");
        int sample = 1;
        while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > maxDimension) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap bitmap;
        try (InputStream stream = getContentResolver().openInputStream(uri)) {
            if (stream == null) throw new IOException("Imagem indisponível.");
            bitmap = BitmapFactory.decodeStream(stream, null, options);
        }
        if (bitmap == null) throw new IOException("Falha ao decodificar imagem.");

        try (InputStream stream = getContentResolver().openInputStream(uri)) {
            if (stream != null) {
                android.media.ExifInterface exif = new android.media.ExifInterface(stream);
                int orientation = exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,
                        android.media.ExifInterface.ORIENTATION_NORMAL);
                float degrees = orientation == android.media.ExifInterface.ORIENTATION_ROTATE_90 ? 90f
                        : orientation == android.media.ExifInterface.ORIENTATION_ROTATE_180 ? 180f
                        : orientation == android.media.ExifInterface.ORIENTATION_ROTATE_270 ? 270f : 0f;
                if (degrees != 0f) {
                    Matrix matrix = new Matrix(); matrix.postRotate(degrees);
                    Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                    if (rotated != bitmap) bitmap.recycle();
                    bitmap = rotated;
                }
            }
        } catch (IOException | RuntimeException ignored) { /* Keep the decoded image even when EXIF metadata is malformed. */ }
        return bitmap;
    }

    private void styleReceiptChoice(Button choice, boolean selected) {
        if (choice == null) return;
        GradientDrawable background = new GradientDrawable();
        background.setColor(selected ? PRIMARY : Color.rgb(230, 231, 250));
        background.setCornerRadius(dp(12));
        background.setStroke(dp(1), selected ? PRIMARY : Color.rgb(220, 222, 240));
        choice.setBackground(background);
        choice.setTextColor(selected ? Color.WHITE : PRIMARY);
        choice.setEnabled(true);
    }

    private void showReceiptFullScreen() {
        Bitmap selected = showingOriginalReceipt ? currentReceiptBitmap : currentScannedBitmap;
        if (selected == null || selected.isRecycled()) return;
        showReceiptBitmapDialog(selected, showingOriginalReceipt
                ? "Foto original do comprovante" : "Cópia digitalizada • alto contraste");
    }

    private void showSavedReceipt(String path, String title) {
        if (path == null || path.trim().isEmpty()) return;
        File file = new File(path);
        if (!file.isFile()) {
            Toast.makeText(this, "O arquivo deste comprovante não está disponível.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Bitmap bitmap = decodeReceiptBitmap(Uri.fromFile(file), 3000);
            showReceiptBitmapDialog(bitmap, title);
        } catch (IOException | RuntimeException ex) {
            Toast.makeText(this, "Não foi possível abrir o comprovante salvo.", Toast.LENGTH_LONG).show();
        }
    }

    private void showReceiptBitmapDialog(Bitmap bitmap, String title) {
        ImageView enlarged = new ImageView(this);
        enlarged.setAdjustViewBounds(true);
        enlarged.setScaleType(ImageView.ScaleType.FIT_CENTER);
        enlarged.setImageBitmap(bitmap);
        ScrollView viewer = new ScrollView(this);
        viewer.setFillViewport(true);
        viewer.addView(enlarged, new ScrollView.LayoutParams(-1, -2));
        new AlertDialog.Builder(this).setTitle(title)
                .setView(viewer).setPositiveButton("Fechar", (dialog, which) -> dialog.dismiss()).show();
    }

    /** Copies source bytes unchanged; this is the archival original, not the OCR preview. */
    private File saveOriginalReceiptCopy() throws IOException {
        if (currentReceiptUri == null) return null;
        File directory = new File(getFilesDir(), "receipts");
        if (!directory.exists() && !directory.mkdirs()) throw new IOException("Não foi possível criar a pasta de comprovantes.");
        File file = File.createTempFile("receipt_original_", ".original", directory);
        try (InputStream input = getContentResolver().openInputStream(currentReceiptUri);
             FileOutputStream output = new FileOutputStream(file)) {
            if (input == null) throw new IOException("Imagem original indisponível.");
            byte[] buffer = new byte[32 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            output.flush();
            if (file.length() == 0L) throw new IOException("A imagem original está vazia.");
            return file;
        } catch (IOException | RuntimeException ex) {
            file.delete();
            throw ex;
        }
    }

    /** Saves the grayscale/contrast-enhanced derivative separately from the untouched original. */
    private File saveScannedReceiptCopy() throws IOException {
        Bitmap bitmap = currentScannedBitmap;
        boolean temporary = false;
        if (bitmap == null || bitmap.isRecycled()) {
            if (currentReceiptUri == null) return null;
            Bitmap source = decodeReceiptBitmap(currentReceiptUri, 2000);
            try { bitmap = createScannedCopy(source); }
            finally { source.recycle(); }
            temporary = true;
        }
        File directory = new File(getFilesDir(), "receipts");
        if (!directory.exists() && !directory.mkdirs()) {
            if (temporary) bitmap.recycle();
            throw new IOException("Não foi possível criar a pasta de comprovantes.");
        }
        File file = File.createTempFile("receipt_scanned_", ".jpg", directory);
        try (FileOutputStream output = new FileOutputStream(file)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 94, output)) {
                throw new IOException("Não foi possível gravar a cópia digitalizada.");
            }
            output.flush();
            if (file.length() == 0L) throw new IOException("A cópia digitalizada ficou vazia.");
            return file;
        } catch (IOException | RuntimeException ex) {
            file.delete();
            throw ex;
        } finally {
            if (temporary && !bitmap.isRecycled()) bitmap.recycle();
        }
    }

    private void scrollToReceiptPreview() {
        if (mainScroll == null || receiptPreview == null) return;
        View targetView = receiptImagePreview != null && receiptImagePreview.getVisibility() == View.VISIBLE
                ? receiptImagePreview : receiptPreview;
        targetView.post(() -> {
            int[] previewLocation = new int[2];
            int[] scrollLocation = new int[2];
            targetView.getLocationOnScreen(previewLocation);
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
        if (r.merchantName != null && !r.merchantName.isEmpty()) {
            out.append("ESTABELECIMENTO\n").append(r.merchantName).append('\n');
            descriptionInput.setText(r.merchantName);
        } else if (r.description != null && !r.description.isEmpty()) {
            descriptionInput.setText(r.description);
        }
        if (r.purchaseDate != null && !r.purchaseDate.isEmpty()) out.append("DATA DA COMPRA\n").append(r.purchaseDate).append('\n');
        if (r.documentType != null && !r.documentType.isEmpty()) out.append("TIPO DE DOCUMENTO\n").append(r.documentType).append('\n');
        StringBuilder editableDetails = new StringBuilder();
        if (r.purchaseDate != null && !r.purchaseDate.isEmpty()) editableDetails.append("Data: ").append(r.purchaseDate);
        if (r.itemDetails != null && !r.itemDetails.isEmpty()) {
            if (editableDetails.length() > 0) editableDetails.append('\n');
            editableDetails.append(r.itemDetails);
            out.append("ITENS IDENTIFICADOS\n").append(r.itemDetails).append('\n');
        }
        if (editableDetails.length() > 0) detailsInput.setText(editableDetails.toString());
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
        String raw = r.rawText == null ? "" : r.rawText.trim();
        if (raw.length() > 5000) raw = raw.substring(0, 5000) + "\n[texto reduzido; imagem completa disponível acima]";
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
        values.put("purchase_details", detailsInput.getText().toString().trim());
        values.put("created_at", System.currentTimeMillis());
        File savedOriginal = null;
        File savedScanned = null;
        boolean receiptCopyFailed = false;
        if (currentReceiptUri != null) {
            try {
                savedOriginal = saveOriginalReceiptCopy();
                savedScanned = saveScannedReceiptCopy();
                if (savedOriginal != null) values.put("receipt_original_path", savedOriginal.getAbsolutePath());
                if (savedScanned != null) {
                    values.put("receipt_scanned_path", savedScanned.getAbsolutePath());
                    // Keep the legacy field populated so older app code can still locate a receipt.
                    values.put("receipt_image_path", savedScanned.getAbsolutePath());
                }
            } catch (IOException | RuntimeException ex) {
                receiptCopyFailed = true;
                if (savedOriginal != null) savedOriginal.delete();
                if (savedScanned != null) savedScanned.delete();
                savedOriginal = null;
                savedScanned = null;
            }
        }
        try {
            db.insertOrThrow("entries", null, values);
            amountInput.setText(""); descriptionInput.setText(""); paymentInput.setText(""); detailsInput.setText(""); kindInput.setSelection(0);
            receiptPreview.setText("Lançamento salvo. A cópia digitalizada e a foto original ficam disponíveis no histórico.");
            refreshSummary();
            Toast.makeText(this, receiptCopyFailed
                    ? "Lançamento salvo, mas não foi possível anexar as imagens."
                    : "Lançamento e duas versões do comprovante salvos.", Toast.LENGTH_LONG).show();
        } catch (RuntimeException ex) {
            if (savedOriginal != null) savedOriginal.delete();
            if (savedScanned != null) savedScanned.delete();
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
                writer.write("ID;Data e hora;Categoria;Valor (R$);Descrição;Forma de pagamento;Detalhes da compra\r\n");
                c = db.rawQuery("SELECT id,created_at,kind,amount,description,payment_method,purchase_details FROM entries ORDER BY id ASC", null);
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
                    writer.write(';');
                    writer.write(CsvUtils.escape(c.getString(6)));
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

    /** Resets the list and renders its first page. */
    private void refreshHistory() {
        historyLoadedCount = 0;
        renderHistoryPage(true);
    }

    private void loadMoreHistory() {
        renderHistoryPage(false);
    }

    /**
     * Loads a bounded page at a time. The database has no total row cap; only
     * currently rendered rows are kept as Android views to avoid unbounded RAM use.
     */
    private void renderHistoryPage(boolean reset) {
        if (historyContainer == null) return;
        if (reset) {
            historyLoadedCount = 0;
            historyContainer.removeAllViews();
        }
        Cursor c = db.rawQuery(
                "SELECT id,kind,amount,description,created_at,payment_method,purchase_details,"
                        + "receipt_image_path,receipt_original_path,receipt_scanned_path "
                        + "FROM entries ORDER BY id DESC", null);
        int skipped = 0;
        int shown = 0;
        boolean hasMore = false;
        String query = searchInput == null ? ""
                : searchInput.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
        try {
            if (!c.moveToFirst()) {
                archivedCountView.setText("Histórico de lançamentos (0)");
                historyContainer.addView(text("Nenhum lançamento registrado ainda.", 13, MUTED, false));
                historyLoadMoreButton.setVisibility(View.GONE);
                return;
            }
            do {
                String kind = c.getString(1);
                String description = c.getString(3);
                boolean business = kind.equals(BUSINESS_REVENUE) || kind.equals(BUSINESS_EXPENSE)
                        || kind.equals(BUSINESS_EXPENSE_PERSONAL);
                boolean personal = kind.equals(PERSONAL_REVENUE) || kind.equals(PERSONAL_EXPENSE)
                        || kind.equals(CONTRIBUTION);
                boolean matchesFilter = historyFilter.equals("Todos")
                        || (historyFilter.equals("Empresa") && business)
                        || (historyFilter.equals("Pessoal") && personal)
                        || (historyFilter.equals("Mistos") && kind.equals(TRANSFER));
                String paymentMethod = c.getString(5);
                String purchaseDetails = c.getString(6);
                String legacyReceiptPath = c.getString(7);
                String originalReceiptPath = c.getString(8);
                String scannedReceiptPath = c.getString(9);
                if (scannedReceiptPath == null || scannedReceiptPath.isEmpty()) {
                    scannedReceiptPath = legacyReceiptPath;
                }
                boolean matchesQuery = query.isEmpty()
                        || description.toLowerCase(java.util.Locale.ROOT).contains(query)
                        || (purchaseDetails != null && purchaseDetails.toLowerCase(java.util.Locale.ROOT).contains(query))
                        || kind.toLowerCase(java.util.Locale.ROOT).contains(query)
                        || (paymentMethod != null && paymentMethod.toLowerCase(java.util.Locale.ROOT).contains(query))
                        || money(c.getLong(2)).toLowerCase(java.util.Locale.ROOT).contains(query);
                if (!matchesFilter || !matchesQuery) continue;

                if (skipped < historyLoadedCount) {
                    skipped++;
                    continue;
                }
                if (shown >= HISTORY_PAGE_SIZE) {
                    hasMore = true;
                    break;
                }

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
                if (paymentMethod != null && !paymentMethod.isEmpty()) {
                    info.addView(text("Pagamento: " + paymentMethod, 10, MUTED, false));
                }
                if (purchaseDetails != null && !purchaseDetails.isEmpty()) {
                    TextView detailsView = text(purchaseDetails, 10, MUTED, false);
                    detailsView.setMaxLines(3);
                    info.addView(detailsView);
                }
                if (scannedReceiptPath != null && !scannedReceiptPath.isEmpty()) {
                    TextView scanLink = text("Ver cópia digitalizada • alto contraste", 11, PRIMARY, true);
                    scanLink.setPadding(0, dp(5), 0, 0);
                    String pathForScan = scannedReceiptPath;
                    scanLink.setOnClickListener(v -> showSavedReceipt(pathForScan, "Cópia digitalizada • alto contraste"));
                    info.addView(scanLink);
                }
                if (originalReceiptPath != null && !originalReceiptPath.isEmpty()) {
                    TextView originalLink = text("Ver foto original", 11, PRIMARY, true);
                    originalLink.setPadding(0, dp(3), 0, 0);
                    String pathForOriginal = originalReceiptPath;
                    originalLink.setOnClickListener(v -> showSavedReceipt(pathForOriginal, "Foto original do comprovante"));
                    info.addView(originalLink);
                }
                row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
                Button remove = button("Excluir", Color.rgb(252, 235, 237));
                remove.setTextColor(Color.rgb(176, 55, 70));
                remove.setTextSize(11);
                remove.setOnClickListener(v -> confirmDelete(id, description));
                row.addView(remove, new LinearLayout.LayoutParams(-2, dp(38)));
                historyContainer.addView(row, margin(0, 0, 0, 8));
                View divider = new View(this);
                divider.setBackgroundColor(Color.rgb(235, 237, 243));
                historyContainer.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
            } while (c.moveToNext());

            historyLoadedCount += shown;
            archivedCountView.setText("Exibidos: " + historyLoadedCount);
            if (historyLoadedCount == 0 && query.length() > 0) {
                historyContainer.addView(text("Nenhum resultado encontrado.", 13, MUTED, false));
            }
            historyLoadMoreButton.setVisibility(hasMore ? View.VISIBLE : View.GONE);
            historyLoadMoreButton.setText("Carregar mais lançamentos");
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
                    String receiptPath = null;
                    Cursor receiptCursor = db.rawQuery("SELECT receipt_image_path FROM entries WHERE id=?", new String[]{String.valueOf(id)});
                    try { if (receiptCursor.moveToFirst()) receiptPath = receiptCursor.getString(0); }
                    finally { receiptCursor.close(); }
                    int deleted = db.delete("entries", "id=?", new String[]{String.valueOf(id)});
                    if (deleted > 0 && receiptPath != null && !receiptPath.isEmpty()) new File(receiptPath).delete();
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
