package dev.ampybios.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Unprivileged launcher UI: never replaces Android SystemUI or controls device
 * settings. The user may select it as HOME in system default apps settings.
 */
public final class LauncherActivity extends Activity {
    private static final String[] IDS = {"fusion", "cyberpunk", "futuristic", "minimal"};
    private static final String[] LABELS = {"Fusion", "Cyberpunk", "Futuristic", "Minimal"};
    private SharedPreferences preferences;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;
    private LinearLayout layout;
    private GridLayout grid;
    private TextView title, battery;
    private Spinner themeSelector;
    private Switch followCore;
    private SeekBar iconSize;
    private EditText search;
    private LauncherTheme theme;
    private int iconDp = 64;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            battery.setText(scale > 0 && level >= 0
                    ? "BAT  " + Math.round(100f * level / scale) + "%"
                    : "BAT  --%");
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences("ampylauncher.theme", MODE_PRIVATE);
        iconDp = Math.max(48, Math.min(96, preferences.getInt("icon_dp", 64)));
        buildUi();
        preferenceListener = (p, key) -> {
            if ("theme".equals(key) && p.getBoolean("follow_core", true)) {
                String id = p.getString("theme", "fusion");
                themeSelector.setSelection(themeIndex(id));
                applyTheme(id);
            }
        };
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(px(18), px(36), px(18), px(32));
        scroll.addView(layout);
        setContentView(scroll);
        title = label("AMPYBIOS", 29);
        title.setLetterSpacing(.14f);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.addView(title);
        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm:ss");
        clock.setFormat12Hour("hh:mm:ss a");
        clock.setTextSize(39);
        clock.setTextColor(Color.WHITE);
        clock.setGravity(Gravity.CENTER);
        layout.addView(clock);
        battery = label("BAT --%", 14);
        battery.setGravity(Gravity.CENTER);
        layout.addView(battery);

        themeSelector = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, LABELS);
        themeSelector.setAdapter(adapter);
        themeSelector.setSelection(themeIndex(preferences.getString("theme", "fusion")));
        themeSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int index, long id) {
                String selected = IDS[index];
                boolean manual = !selected.equals(preferences.getString("theme", "fusion"));
                SharedPreferences.Editor editor = preferences.edit().putString("theme", selected);
                if (manual) {
                    editor.putBoolean("follow_core", false);
                    if (followCore != null) followCore.setChecked(false);
                }
                editor.apply();
                applyTheme(selected);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        layout.addView(themeSelector);
        followCore = new Switch(this);
        followCore.setText("Segui skin AmpyCore");
        followCore.setTextColor(Color.WHITE);
        followCore.setChecked(preferences.getBoolean("follow_core", true));
        followCore.setOnCheckedChangeListener((v, checked) ->
                preferences.edit().putBoolean("follow_core", checked).apply());
        layout.addView(followCore);
        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Cerca applicazione…");
        search.setHintTextColor(Color.LTGRAY);
        search.setTextColor(Color.WHITE);
        layout.addView(search);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int c, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                populate(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        layout.addView(label("DIMENSIONE ICONE", 13));
        iconSize = new SeekBar(this);
        iconSize.setMax(96 - 48);
        iconSize.setProgress(iconDp - 48);
        iconSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean user) {
                if (user) {
                    iconDp = 48 + value;
                    preferences.edit().putInt("icon_dp", iconDp).apply();
                    populate(search.getText().toString());
                }
            }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) {}
        });
        layout.addView(iconSize);

        grid = new GridLayout(this);
        grid.setColumnCount(4);
        layout.addView(grid);
        Button settingsButton = new Button(this);
        settingsButton.setText("Impostazioni Android / Home");
        settingsButton.setAllCaps(false);
        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_HOME_SETTINGS);
            try { startActivity(intent); }
            catch (RuntimeException error) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });
        layout.addView(settingsButton);
        applyTheme(preferences.getString("theme", "fusion"));
        populate("");
    }

    private int px(float d) { return Math.round(d * getResources().getDisplayMetrics().density); }
    private TextView label(String label, int size) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(size);
        t.setTextColor(Color.WHITE);
        t.setPadding(0, px(6), 0, px(6));
        return t;
    }
    private int themeIndex(String id) {
        for (int i=0; i<IDS.length; i++) if (IDS[i].equals(id)) return i;
        return 0;
    }
    private void applyTheme(String id) {
        theme = LauncherTheme.load(this, id);
        layout.setBackgroundColor(theme.background);
        title.setTextColor(theme.accent);
        if (grid != null) populate(search.getText().toString());
    }

    private void populate(String query) {
        if (grid == null || theme == null) return;
        grid.removeAllViews();
        Intent discover = new Intent(Intent.ACTION_MAIN);
        discover.addCategory(Intent.CATEGORY_LAUNCHER);
        PackageManager manager = getPackageManager();
        List<ResolveInfo> apps = new ArrayList<>(manager.queryIntentActivities(discover, 0));
        Collections.sort(apps, (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
                a.loadLabel(manager).toString(), b.loadLabel(manager).toString()));
        int shown = 0;
        for (ResolveInfo app : apps) {
            if (app.activityInfo == null) continue;
            String name = app.loadLabel(manager).toString();
            if (!name.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) continue;
            if (shown++ >= 160) break;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            GridLayout.LayoutParams cell = new GridLayout.LayoutParams();
            cell.width = px(82);
            cell.height = px(iconDp + 52);
            cell.setMargins(px(1), px(7), px(1), px(7));
            item.setLayoutParams(cell);
            ImageView image = new ImageView(this);
            image.setImageDrawable(app.loadIcon(manager));
            image.setContentDescription(name);
            GradientDrawable frame = new GradientDrawable();
            frame.setColor(theme.surface);
            frame.setStroke(px(1), theme.secondary);
            frame.setCornerRadii(theme.radiiDp(this));
            image.setBackground(frame);
            image.setPadding(px(5), px(5), px(5), px(5));
            item.addView(image, new LinearLayout.LayoutParams(px(iconDp), px(iconDp)));
            TextView caption = label(name, 11);
            caption.setSingleLine(true);
            caption.setGravity(Gravity.CENTER);
            caption.setTextColor(theme.foreground);
            item.addView(caption, new LinearLayout.LayoutParams(-1, px(32)));
            item.setOnClickListener(v -> launch(app));
            item.setOnLongClickListener(v -> {
                // Native app detail screen provides safe uninstall/permissions actions.
                Intent details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:" + app.activityInfo.packageName));
                startActivity(details);
                return true;
            });
            grid.addView(item);
        }
    }

    private void launch(ResolveInfo info) {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setComponent(new ComponentName(
                    info.activityInfo.packageName, info.activityInfo.name));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            // Animate only the launcher decoration, never overlay another app.
            title.setScaleX(.90f);
            title.setScaleY(.90f);
            title.animate().scaleX(1f).scaleY(1f).setDuration(260)
                    .setInterpolator(new OvershootInterpolator(.75f)).start();
        } catch (RuntimeException e) {
            Toast.makeText(this, "Impossibile avviare questa app", Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
        String themeName = preferences.getString("theme", "fusion");
        themeSelector.setSelection(themeIndex(themeName));
        applyTheme(themeName);
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        populate(search.getText().toString());
    }
    @Override protected void onPause() {
        unregisterReceiver(batteryReceiver);
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener);
        super.onPause();
    }
}
