package dev.ampybios.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Locale;

/**
 * AmpyKernel Lab: real interactive, unprivileged AmpyCore-only performance UI.
 * Never writes a CPU governor, Power HAL, kernel sysfs, display settings or
 * platform power-save switches. The Window requests a preferred refresh rate.
 */
final class AmpyKernelLabView extends LinearLayout {
    static final String KEY_PROFILE = "performance.profile";
    static final String KEY_DISPLAY_HZ = "kernel.display_hz";
    static final String KEY_STANDBY = "kernel.standby_saver";
    static final String KEY_ADAPTIVE = "kernel.adaptive_thermal";

    private final SharedPreferences settings;
    private final Runnable onSettingsChanged;
    private final TextView headline, subheading, effectiveProfile, preferredHz;
    private final TextView diagnostic, scope;
    private final RadioGroup profiles;
    private final RadioButton[] profileButtons = new RadioButton[3];
    private final SeekBar hzSlider;
    private final Switch standbySwitch, adaptiveSwitch;
    private boolean building = true;

    AmpyKernelLabView(Context context, SharedPreferences settings, Runnable callback) {
        super(context);
        this.settings = settings;
        this.onSettingsChanged = callback;
        setOrientation(VERTICAL);
        setPadding(dp(16), dp(18), dp(16), dp(20));

        headline = text("AMPYKERNEL", 20, true);
        headline.setLetterSpacing(.14f);
        addView(headline);
        subheading = text("PIXEL 7 PRO  •  LAB DI PRESTAZIONI", 11, false);
        addView(subheading);
        space(14);

        addView(text("PROFILO", 12, true));
        profiles = new RadioGroup(context);
        profiles.setOrientation(HORIZONTAL);
        String[] names = {"Eco", "Balanced", "Fusion Extreme"};
        for (int i = 0; i < profileButtons.length; i++) {
            RadioButton button = new RadioButton(context);
            button.setId(android.view.View.generateViewId());
            button.setText(names[i]);
            button.setTextSize(12);
            button.setGravity(Gravity.CENTER);
            button.setSingleLine(true);
            button.setButtonDrawable((android.graphics.drawable.Drawable) null);
            button.setPadding(dp(3), 0, dp(3), 0);
            profileButtons[i] = button;
            profiles.addView(button, new RadioGroup.LayoutParams(0, dp(50), 1f));
        }
        int initial = getProfile();
        profileButtons[initial].setChecked(true);
        addView(profiles);
        effectiveProfile = text("Profilo effettivo: rilevamento…", 13, true);
        addView(effectiveProfile);
        space(8);

        addView(text("OBIETTIVO DISPLAY", 12, true));
        preferredHz = text(getTargetHz() + " Hz richiesti · frequenza effettiva: n/d", 13, true);
        addView(preferredHz);
        hzSlider = new SeekBar(context);
        hzSlider.setMax(2);
        hzSlider.setProgress((getTargetHz() - 60) / 30);
        hzSlider.setContentDescription("Frequenza obiettivo AmpyCore, da 60 a 120 Hz");
        addView(hzSlider, new LayoutParams(-1, dp(42)));
        LinearLayout ticks = new LinearLayout(context);
        ticks.setOrientation(HORIZONTAL);
        for (String label : new String[]{"60 Hz", "90 Hz", "120 Hz"}) {
            TextView tick = text(label, 11, false);
            tick.setGravity(Gravity.CENTER);
            ticks.addView(tick, new LayoutParams(0, dp(22), 1f));
        }
        addView(ticks);
        space(8);

        standbySwitch = toggle("Risparmio quando inattivo", isStandbyEnabled());
        addView(standbySwitch, new LayoutParams(-1, dp(52)));
        addView(text("Sospende l'animazione dopo 30 s senza tocchi e in background; "
                + "non cambia il risparmio energetico di Android.", 11, false));
        adaptiveSwitch = toggle("Adattamento termico preventivo", isThermalAdaptationEnabled());
        addView(adaptiveSwitch, new LayoutParams(-1, dp(52)));
        addView(text("Riduce gli effetti già con avvisi leggeri. Le protezioni Android "
                + "per temperatura e batteria restano SEMPRE attive.", 11, false));
        space(12);

        diagnostic = text("Monitor temperatura / batteria / frame in avvio…", 12, false);
        addView(diagnostic);
        space(10);
        scope = text("Solo AmpyCore · nessun overclock, root o modifica kernel", 11, false);
        addView(scope);

        profiles.setOnCheckedChangeListener((group, checkedId) -> {
            if (building) return;
            for (int i = 0; i < profileButtons.length; i++) {
                if (profileButtons[i].getId() == checkedId) {
                    settings.edit().putInt(KEY_PROFILE, i).apply();
                    notifyChanged();
                    return;
                }
            }
        });
        hzSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean user) {
                if (!user || building) return;
                int selected = 60 + Math.max(0, Math.min(2, value)) * 30;
                settings.edit().putInt(KEY_DISPLAY_HZ, selected).apply();
                notifyChanged();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        standbySwitch.setOnCheckedChangeListener((button, checked) -> {
            if (building) return;
            settings.edit().putBoolean(KEY_STANDBY, checked).apply();
            notifyChanged();
        });
        adaptiveSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (building) return;
            settings.edit().putBoolean(KEY_ADAPTIVE, checked).apply();
            notifyChanged();
        });
        building = false;
    }

    int getProfile() {
        return Math.max(PerformancePolicy.ECO,
                Math.min(PerformancePolicy.FUSION_EXTREME,
                        settings.getInt(KEY_PROFILE, PerformancePolicy.FUSION_EXTREME)));
    }

    int getTargetHz() {
        int value = settings.getInt(KEY_DISPLAY_HZ, 120);
        return value == 60 || value == 90 || value == 120 ? value : 120;
    }

    boolean isStandbyEnabled() {
        return settings.getBoolean(KEY_STANDBY, true);
    }

    boolean isThermalAdaptationEnabled() {
        return settings.getBoolean(KEY_ADAPTIVE, true);
    }

    void showState(PerformancePolicy.Budget budget, int desiredHz, float actualHz,
                   boolean standbyIdle, PerformanceMonitor.Snapshot snapshot) {
        effectiveProfile.setText("Richiesto: " + PerformancePolicy.name(budget.requestedProfile)
                + "  →  Effettivo: " + PerformancePolicy.name(budget.effectiveProfile)
                + "\n" + budget.reason + (standbyIdle ? " · Inattività: radar sospeso" : ""));
        String actual = actualHz > 0
                ? String.format(Locale.ITALIAN, "%.0f Hz", actualHz) : "n/d";
        preferredHz.setText(getTargetHz() + " Hz obiettivo · "
                + desiredHz + " Hz preferiti dalla finestra · Display " + actual);
        diagnostic.setText(snapshot == null
                ? "Monitor in avvio: nessun campione disponibile"
                : (standbyIdle ? "Monitor sospeso · Ultima lettura:\n" : "")
                        + snapshot.describe());
    }

    void applyPalette(ThemeProfile theme) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(theme.surface);
        background.setStroke(dp(1), theme.secondary);
        background.setCornerRadius(dp(18));
        setBackground(background);
        headline.setTextColor(theme.accent);
        subheading.setTextColor(theme.foreground);
        effectiveProfile.setTextColor(theme.accent);
        preferredHz.setTextColor(theme.accent);
        diagnostic.setTextColor(theme.foreground);
        scope.setTextColor(theme.secondary);
        for (RadioButton b : profileButtons) {
            boolean selected = b.isChecked();
            GradientDrawable shape = new GradientDrawable();
            shape.setColor(selected ? theme.accent : theme.background);
            shape.setStroke(dp(1), selected ? theme.accent : theme.secondary);
            shape.setCornerRadius(dp(12));
            b.setBackground(shape);
            b.setTextColor(selected ? theme.background : theme.foreground);
        }
        standbySwitch.setTextColor(theme.foreground);
        adaptiveSwitch.setTextColor(theme.foreground);
    }

    private void notifyChanged() {
        if (onSettingsChanged != null) onSettingsChanged.run();
    }

    private Switch toggle(String label, boolean checked) {
        Switch control = new Switch(getContext());
        control.setText(label);
        control.setTextSize(14);
        control.setTextColor(Color.WHITE);
        control.setChecked(checked);
        return control;
    }

    private TextView text(String label, int size, boolean bold) {
        TextView text = new TextView(getContext());
        text.setText(label);
        text.setTextSize(size);
        text.setTextColor(Color.WHITE);
        text.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    private void space(int h) {
        addView(new android.view.View(getContext()), new LayoutParams(1, dp(h)));
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
