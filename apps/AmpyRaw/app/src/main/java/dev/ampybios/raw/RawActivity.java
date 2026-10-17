package dev.ampybios.raw;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * First unprivileged, non-destructive AmpyRaw editor.
 * Native Android decoder: JPEG / PNG / WebP / HEIF as supported by OS.
 * Canon CR3 and DNG RAW development are NOT implemented without a vetted
 * native LibRaw integration: never pretend otherwise.
 */
public final class RawActivity extends Activity {
    private static final int PICK_IMAGE = 101;
    private static final int EXPORT_JPEG = 102;
    private static final String[] THEMES = {"fusion", "cyberpunk", "futuristic", "minimal"};
    private static final String[] LABELS = {"Fusion", "Cyberpunk", "Futuristic", "Minimal"};
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicInteger jobToken = new AtomicInteger();
    private SharedPreferences preferences;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;
    private Spinner selector;
    private Switch followCore;
    private TextView info;
    private TextView heading;
    private ImageView preview;
    private LinearLayout layout;
    private Button openButton, exportButton;
    private SeekBar brightness, contrast, saturation;
    private Bitmap original;
    private Uri sourceUri;
    private String sourceName = "";
    private volatile boolean destroyed;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("ampyraw.theme", MODE_PRIVATE);
        buildUi();
        preferenceListener = (prefs, key) -> {
            if ("theme".equals(key) && prefs.getBoolean("follow_core", true)) {
                String theme = prefs.getString("theme", "fusion");
                selector.setSelection(themeIndex(theme));
                applyTheme(theme);
            }
        };
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(px(20), px(20), px(20), px(32));
        scroll.addView(layout);
        setContentView(scroll);

        heading = text("AMPYRAW", 28, true);
        heading.setLetterSpacing(.13f);
        layout.addView(heading);
        layout.addView(text("EDITOR NON DISTRUTTIVO · SVILUPPO RAW IN PREPARAZIONE",
                11, false));
        selector = new Spinner(this);
        ArrayAdapter<String> themes = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, LABELS);
        selector.setAdapter(themes);
        selector.setSelection(themeIndex(preferences.getString("theme", "fusion")));
        selector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view,
                                                  int position, long id) {
                String requested = THEMES[position];
                // An asynchronous spinner callback after a synchronized update
                // must not silently disable Core following.
                boolean changed = !requested.equals(preferences.getString("theme", "fusion"));
                SharedPreferences.Editor edit = preferences.edit().putString("theme", requested);
                if (changed) {
                    edit.putBoolean("follow_core", false);
                    if (followCore != null) followCore.setChecked(false);
                }
                edit.apply();
                applyTheme(requested);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        layout.addView(selector);
        followCore = new Switch(this);
        followCore.setText("Segui tema AmpyCore");
        followCore.setTextColor(Color.WHITE);
        followCore.setChecked(preferences.getBoolean("follow_core", true));
        followCore.setOnCheckedChangeListener((button, follow) ->
                preferences.edit().putBoolean("follow_core", follow).apply());
        layout.addView(followCore);
        openButton = button("Apri fotografia");
        openButton.setOnClickListener(v -> openPicker());
        layout.addView(openButton);
        preview = new ImageView(this);
        preview.setBackgroundColor(Color.rgb(20, 24, 40));
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        layout.addView(preview, new LinearLayout.LayoutParams(-1, px(330)));
        info = text("Seleziona JPEG, PNG, WebP o HEIF. DNG e CR3 richiedono un decoder RAW.",
                13, false);
        info.setPadding(0, px(10), 0, px(14));
        layout.addView(info);

        layout.addView(text("Luminosità", 14, true));
        brightness = bar(200, 100);
        layout.addView(text("Contrasto", 14, true));
        contrast = bar(200, 100);
        layout.addView(text("Saturazione", 14, true));
        saturation = bar(200, 100);
        Button reset = button("Ripristina regolazioni");
        reset.setOnClickListener(v -> {
            brightness.setProgress(100);
            contrast.setProgress(100);
            saturation.setProgress(100);
            refreshPreview();
        });
        layout.addView(reset);
        exportButton = button("Esporta copia JPEG");
        exportButton.setEnabled(false);
        exportButton.setOnClickListener(v -> exportPicker());
        layout.addView(exportButton);
        layout.addView(text("Originale sempre intatto. Esportazione limitata all'anteprima "
                + "di massimo 2048 px; non equivale a uno sviluppo RAW a piena risoluzione.",
                12, false));
        applyTheme(preferences.getString("theme", "fusion"));
    }

    private int px(float dp) { return Math.round(dp * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(Color.WHITE);
        t.setPadding(0, px(7), 0, px(7));
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private Button button(String value) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(value);
        return b;
    }
    private SeekBar bar(int max, int start) {
        SeekBar b = new SeekBar(this);
        b.setMax(max);
        b.setProgress(start);
        b.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int value, boolean user) {
                if (user) refreshPreview();
            }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) {}
        });
        layout.addView(b);
        return b;
    }
    private int themeIndex(String id) {
        for (int i = 0; i < THEMES.length; i++) if (THEMES[i].equals(id)) return i;
        return 0;
    }
    private void applyTheme(String id) {
        RawTheme theme = RawTheme.load(this, id);
        layout.setBackgroundColor(theme.background);
        heading.setTextColor(theme.accent);
        for (Button button : new Button[]{openButton, exportButton}) {
            GradientDrawable shape = new GradientDrawable();
            shape.setColor(theme.accent);
            shape.setCornerRadii(theme.radiiDp(this));
            button.setBackground(shape);
            button.setTextColor(theme.background);
        }
    }

    private void openPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "image/jpeg", "image/png", "image/webp", "image/heic",
                "image/heif", "image/avif", "image/x-canon-cr3",
                "image/x-adobe-dng", "application/octet-stream"});
        startActivityForResult(intent, PICK_IMAGE);
    }

    private void exportPicker() {
        if (original == null) return;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/jpeg");
        intent.putExtra(Intent.EXTRA_TITLE, "AmpyRaw_edit.jpg");
        startActivityForResult(intent, EXPORT_JPEG);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == PICK_IMAGE) loadImage(uri);
        else if (requestCode == EXPORT_JPEG) exportImage(uri);
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (RuntimeException ignored) {}
        return "file";
    }

    private void loadImage(Uri uri) {
        int token = jobToken.incrementAndGet();
        original = null;
        preview.setImageDrawable(null);
        exportButton.setEnabled(false);
        sourceUri = uri;
        sourceName = displayName(uri);
        String filename = sourceName.toLowerCase(Locale.ROOT);
        if (filename.endsWith(".cr3") || filename.endsWith(".dng")
                || filename.endsWith(".cr2") || filename.endsWith(".nef")) {
            show("Formato RAW rilevato: il decoder CR3/DNG via LibRaw non è ancora integrato.");
            return;
        }
        show("Apertura fotografia…");
        worker.submit(() -> {
            Bitmap decoded = null;
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new IOException("Immagine non disponibile");
                    BitmapFactory.decodeStream(input, null, options);
                }
                if (options.outWidth <= 0 || options.outHeight <= 0)
                    throw new IOException("Formato immagine non supportato");
                int sample = 1;
                while (Math.max(options.outWidth, options.outHeight) / sample > 2048) {
                    sample *= 2;
                }
                options.inJustDecodeBounds = false;
                options.inSampleSize = sample;
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new IOException("Immagine non disponibile");
                    decoded = BitmapFactory.decodeStream(input, null, options);
                }
                if (decoded == null) throw new IOException("Il decoder Android non supporta questo file");
                final Bitmap result = decoded;
                runOnUiThread(() -> {
                    if (destroyed || token != jobToken.get()) {
                        result.recycle();
                        return;
                    }
                    original = result;
                    preview.setImageBitmap(result);
                    refreshPreview();
                    exportButton.setEnabled(true);
                    show("Aperta " + sourceName + " · anteprima "
                            + result.getWidth() + "×" + result.getHeight());
                });
            } catch (Exception exception) {
                final String message = exception.getMessage();
                runOnUiThread(() -> {
                    if (!destroyed && token == jobToken.get())
                        show("Apertura fallita: " + message);
                });
            }
        });
    }

    private ColorMatrix adjustments() {
        float c = contrast.getProgress() / 100f;
        float b = (brightness.getProgress() - 100) * 1.25f;
        float s = saturation.getProgress() / 100f;
        float offset = 128f * (1f - c) + b;
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(s);
        ColorMatrix contrastMatrix = new ColorMatrix(new float[]{
                c,0,0,0,offset, 0,c,0,0,offset, 0,0,c,0,offset, 0,0,0,1,0});
        matrix.postConcat(contrastMatrix);
        return matrix;
    }

    private void refreshPreview() {
        if (original == null) return;
        preview.setColorFilter(new ColorMatrixColorFilter(adjustments()));
    }

    private void exportImage(Uri uri) {
        if (original == null) return;
        final Bitmap snapshot = original;
        final float[] matrixValues = adjustments().getArray().clone();
        int token = jobToken.get();
        show("Esportazione JPEG in corso…");
        worker.submit(() -> {
            try {
                Bitmap result = Bitmap.createBitmap(snapshot.getWidth(),
                        snapshot.getHeight(), Bitmap.Config.ARGB_8888);
                try {
                    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                    paint.setColorFilter(new ColorMatrixColorFilter(matrixValues));
                    new Canvas(result).drawBitmap(snapshot, 0f, 0f, paint);
                    try (OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
                        if (output == null || !result.compress(Bitmap.CompressFormat.JPEG, 94, output))
                            throw new IOException("Impossibile salvare la copia JPEG");
                    }
                } finally {
                    result.recycle();
                }
                runOnUiThread(() -> {
                    if (!destroyed && token == jobToken.get()) show("Copia JPEG esportata.");
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    if (!destroyed && token == jobToken.get())
                        show("Esportazione fallita: " + ex.getClass().getSimpleName());
                });
            }
        });
    }

    private void show(String message) { info.setText(message); }

    @Override protected void onResume() {
        super.onResume();
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
        String theme = preferences.getString("theme", "fusion");
        selector.setSelection(themeIndex(theme));
        applyTheme(theme);
    }

    @Override protected void onPause() {
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener);
        super.onPause();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        jobToken.incrementAndGet();
        worker.shutdownNow();
        super.onDestroy();
    }
}
