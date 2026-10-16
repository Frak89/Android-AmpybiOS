package dev.ampybios.core;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * AmpyCore M1 standalone, unprivileged UI prototype.
 * It never changes the bootloader, other apps' windows, SystemUI or kernel.
 * GrapheneOS integration comes only after separately reviewed OS patches.
 */
public final class MainActivity extends Activity {
    private static final String[] IDS = {"fusion", "cyberpunk", "futuristic", "minimal"};
    private static final String[] LABELS = {"Fusion", "Cyberpunk", "Futuristic", "Minimal"};
    private SharedPreferences settings;
    private LinearLayout layout;
    private ScrollView scroll;
    private Spinner selector;
    private RadarPreview preview;
    private Switch radarSwitch;
    private Switch reducedMotionSwitch;
    private SeekBar glowBar;
    private SeekBar radarSpeedBar;
    private final SeekBar[] cornerBars = new SeekBar[4];
    private static final String[] CORNERS = {
        "Angolo superiore sinistro", "Angolo superiore destro",
        "Angolo inferiore destro", "Angolo inferiore sinistro"
    };
    private TextView status;
    private TextView controlsDescription;
    private Button saveButton;
    private PerformanceMonitor performanceMonitor;
    private PerformanceMonitor.Snapshot performanceSnapshot;
    private Spinner performanceSelector;
    private TextView performanceStatus;
    private TextView performanceDiagnostics;
    private ThemeProfile theme;
    private boolean updating;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        settings = getSharedPreferences("ampycore.local.v1", MODE_PRIVATE);
        buildUI();
        performanceMonitor = new PerformanceMonitor(this, sample -> {
            performanceSnapshot = sample;
            if (theme != null) applyTheme();
        });
        String stored = settings.getString("theme", "fusion");
        int selected = indexFor(stored);
        selector.setSelection(selected);
        switchTheme(IDS[selected]);
    }

    private void buildUI() {
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(26), dp(18), dp(30));
        scroll.addView(layout);
        setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets edges = insets.getInsets(WindowInsets.Type.systemBars());
            layout.setPadding(dp(18) + edges.left, dp(24) + edges.top,
                    dp(18) + edges.right, dp(30) + edges.bottom);
            return insets;
        });

        TextView headline = label("AMPYCORE", 28, true);
        headline.setLetterSpacing(0.1f);
        layout.addView(headline);
        TextView subtitle = label("AMPYBIOS  /  FUSION STUDIO", 12, false);
        subtitle.setTextColor(Color.rgb(142, 162, 195));
        layout.addView(subtitle);
        spacer(24);

        TextView themeLabel = label("Skin del sistema", 16, true);
        layout.addView(themeLabel);
        selector = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, LABELS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        selector.setAdapter(adapter);
        layout.addView(selector, new LinearLayout.LayoutParams(-1, dp(54)));
        spacer(16);

        preview = new RadarPreview(this);
        layout.addView(preview, new LinearLayout.LayoutParams(-1, dp(220)));
        spacer(12);
        controlsDescription = label("Anteprima organica", 13, false);
        layout.addView(controlsDescription);
        spacer(26);

        radarSwitch = makeSwitch("AmpyRadar — contorno animato", (button, checked) -> persistControls());
        reducedMotionSwitch = makeSwitch("Riduci le animazioni", (button, checked) -> persistControls());
        layout.addView(label("Intensità glow", 15, true));
        glowBar = slider(100, value -> persistControls());
        layout.addView(label("Velocità radar", 15, true));
        radarSpeedBar = slider(360, value -> persistControls());
        spacer(12);

        layout.addView(label("Organic Corner Designer", 18, true));
        for (int i = 0; i < cornerBars.length; i++) {
            layout.addView(label(CORNERS[i], 13, false));
            cornerBars[i] = slider(96, value -> persistControls());
        }
        spacer(20);

        layout.addView(label("AmpyKernel · Performance Lab", 19, true));
        TextView note = label("Limita il rendering di AmpyCore, senza modificare kernel, CPU o GPU.", 12, false);
        note.setTextColor(Color.rgb(151, 170, 198));
        layout.addView(note);
        performanceSelector = new Spinner(this);
        ArrayAdapter<String> performanceAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"Eco", "Balanced", "Fusion Extreme"});
        performanceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        performanceSelector.setAdapter(performanceAdapter);
        int savedMode = settings.getInt("performance.profile", PerformancePolicy.BALANCED);
        performanceSelector.setSelection(Math.max(0, Math.min(2, savedMode)));
        layout.addView(performanceSelector, new LinearLayout.LayoutParams(-1, dp(55)));
        performanceStatus = label("Profilo in preparazione", 13, true);
        layout.addView(performanceStatus);
        spacer(10);
        performanceDiagnostics = label("Monitor termico e frame della finestra in avvio", 12, false);
        performanceDiagnostics.setTextColor(Color.rgb(146, 168, 198));
        layout.addView(performanceDiagnostics);
        spacer(18);

        saveButton = new Button(this);
        saveButton.setAllCaps(false);
        saveButton.setText("Applica skin su AmpyCore");
        saveButton.setOnClickListener(view -> {
            // Controls already persist as a local-only preview; this is explicit UX confirmation.
            persistControls();
            status.setText("Profilo salvato su questo dispositivo · Solo AmpyCore");
        });
        layout.addView(saveButton, new LinearLayout.LayoutParams(-1, dp(56)));
        Button reset = new Button(this);
        reset.setAllCaps(false);
        reset.setText("Ripristina i valori del tema");
        reset.setOnClickListener(view -> {
            if (theme != null) {
                SharedPreferences.Editor reset = settings.edit()
                        .remove(theme.id + ".radar")
                        .remove(theme.id + ".reduced")
                        .remove(theme.id + ".glow")
                        .remove(theme.id + ".speed");
                for (int i = 0; i < cornerBars.length; i++) {
                    reset.remove(theme.id + ".corner." + i);
                }
                reset.apply();
                switchTheme(theme.id);
            }
        });
        layout.addView(reset, new LinearLayout.LayoutParams(-1, dp(52)));
        spacer(12);

        status = label("Anteprima locale: nessuna modifica alle altre app", 12, false);
        status.setTextColor(Color.rgb(156, 176, 198));
        layout.addView(status);
        spacer(12);
        TextView securityNote = label("Nessun root, accesso rete o permesso privilegiato richiesto. "
                + "SystemUI, animazione boot e schermate di altre app richiedono integrazione ROM.", 12, false);
        layout.addView(securityNote);

        selector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switchTheme(IDS[position]);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        performanceSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                settings.edit().putInt("performance.profile", position).apply();
                if (theme != null) applyTheme();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private interface ProgressAction { void change(int value); }

    private SeekBar slider(int max, ProgressAction action) {
        SeekBar bar = new SeekBar(this);
        bar.setMax(max);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                if (fromUser) action.change(value);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        layout.addView(bar, new LinearLayout.LayoutParams(-1, dp(45)));
        return bar;
    }

    private Switch makeSwitch(String text, CompoundButton.OnCheckedChangeListener listener) {
        Switch control = new Switch(this);
        control.setText(text);
        control.setTextColor(Color.WHITE);
        control.setTextSize(14f);
        layout.addView(control, new LinearLayout.LayoutParams(-1, dp(54)));
        control.setOnCheckedChangeListener(listener);
        return control;
    }

    private void switchTheme(String id) {
        try (InputStream stream = getAssets().open(id + ".json")) {
            byte[] bytes = stream.readAllBytes();
            theme = ThemeProfile.parse(new JSONObject(new String(bytes, StandardCharsets.UTF_8)), getResources());
            updating = true;
            settings.edit().putString("theme", id).apply();
            radarSwitch.setChecked(settings.getBoolean(id + ".radar", theme.radarDefault));
            reducedMotionSwitch.setChecked(settings.getBoolean(id + ".reduced", theme.reducedMotion));
            glowBar.setProgress(settings.getInt(id + ".glow", theme.glowDefault));
            radarSpeedBar.setProgress(settings.getInt(id + ".speed", theme.speedDefault));
            float density = getResources().getDisplayMetrics().density;
            for (int i = 0; i < cornerBars.length; i++) {
                int defaultDp = Math.round(theme.radii[2 * i] / density);
                cornerBars[i].setProgress(Math.min(96, settings.getInt(
                        id + ".corner." + i, defaultDp)));
            }
            updating = false;
            applyTheme();
            status.setText("Tema " + theme.label + " caricato · Anteprima locale");
        } catch (Exception error) {
            updating = false;
            status.setText("Tema non disponibile: " + error.getClass().getSimpleName());
        }
    }

    private void persistControls() {
        if (updating || theme == null) return;
        SharedPreferences.Editor editor = settings.edit()
                .putBoolean(theme.id + ".radar", radarSwitch.isChecked())
                .putBoolean(theme.id + ".reduced", reducedMotionSwitch.isChecked())
                .putInt(theme.id + ".glow", glowBar.getProgress())
                .putInt(theme.id + ".speed", radarSpeedBar.getProgress());
        for (int i = 0; i < cornerBars.length; i++) {
            editor.putInt(theme.id + ".corner." + i, cornerBars[i].getProgress());
        }
        editor.apply();
        applyTheme();
    }

    private void applyTheme() {
        if (theme == null) return;
        getWindow().getDecorView().setBackgroundColor(theme.background);
        layout.setBackgroundColor(theme.background);
        int mode = settings.getInt("performance.profile", PerformancePolicy.BALANCED);
        int thermal = performanceSnapshot == null ? -1 : performanceSnapshot.thermal;
        boolean saver = performanceSnapshot != null && performanceSnapshot.batterySaver;
        int battery = performanceSnapshot == null ? -1 : performanceSnapshot.batteryPercent;
        boolean charging = performanceSnapshot != null && performanceSnapshot.charging;
        PerformancePolicy.Budget budget = PerformancePolicy.resolve(mode, thermal,
                saver, battery, charging);
        boolean reduceMotion = reducedMotionSwitch.isChecked() || budget.forceReducedMotion;
        preview.setProfile(theme, radarSwitch.isChecked(), reduceMotion,
                Math.min(glowBar.getProgress(), budget.maxGlowPercent) / 100f,
                Math.min(radarSpeedBar.getProgress(), budget.maxRadarSpeed));
        preview.setFrameRateLimit(budget.targetFps);
        float[] customRadii = new float[8];
        for (int i = 0; i < cornerBars.length; i++) {
            float radius = dp(cornerBars[i].getProgress());
            customRadii[2 * i] = radius;
            customRadii[2 * i + 1] = radius;
        }
        preview.setCornerRadii(customRadii);
        if (performanceStatus != null) {
            performanceStatus.setText("Richiesto: " + PerformancePolicy.name(budget.requestedProfile)
                    + "  →  Effettivo: " + PerformancePolicy.name(budget.effectiveProfile)
                    + " · " + budget.targetFps + " FPS max anteprima\n" + budget.reason);
        }
        if (performanceDiagnostics != null && performanceSnapshot != null) {
            performanceDiagnostics.setText(performanceSnapshot.describe());
        }
        saveButton.setTextColor(theme.background);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(theme.accent);
        drawable.setCornerRadii(customRadii);
        saveButton.setBackground(drawable);
        controlsDescription.setText(String.format(Locale.ROOT,
                "ANGOLI SVG: %.0f / %.0f / %.0f / %.0f dp  •  Radar %s",
                (float) cornerBars[0].getProgress(),
                (float) cornerBars[1].getProgress(),
                (float) cornerBars[2].getProgress(),
                (float) cornerBars[3].getProgress(),
                radarSwitch.isChecked() ? "ON" : "OFF"));
    }

    @Override protected void onPause() {
        if (performanceMonitor != null) performanceMonitor.stop();
        if (preview != null) preview.setVisibleAnimation(false);
        super.onPause();
    }
    @Override protected void onResume() {
        super.onResume();
        if (preview != null) preview.setVisibleAnimation(true);
        if (performanceMonitor != null) performanceMonitor.start();
    }

    private TextView label(String value, int sp, boolean bold) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextColor(Color.rgb(227, 235, 251));
        label.setTextSize(sp);
        if (bold) label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setGravity(Gravity.CENTER_VERTICAL);
        return label;
    }
    private void spacer(int dp) {
        View space = new View(this);
        layout.addView(space, new LinearLayout.LayoutParams(1, dp(dp)));
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private int indexFor(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
        return 0;
    }
}
