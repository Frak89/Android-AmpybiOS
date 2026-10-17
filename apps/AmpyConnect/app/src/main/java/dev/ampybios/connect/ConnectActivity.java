package dev.ampybios.connect;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;

/**
 * Home Assistant API status viewer. NEVER sends state-changing requests.
 * No cloud proxy, no automated PLC or equipment commands, no token disk storage.
 */
public final class ConnectActivity extends Activity {
    private static final String[] IDS = {"fusion", "cyberpunk", "futuristic", "minimal"};
    private static final String[] LABELS = {"Fusion", "Cyberpunk", "Futuristic", "Minimal"};
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SharedPreferences preferences;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;
    private LinearLayout layout;
    private TextView heading, status, devices;
    private EditText endpoint, token;
    private Button query;
    private Spinner themeSelector;
    private Switch follow;
    private int requestNumber;
    private volatile boolean closed;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("ampyconnect.theme", MODE_PRIVATE);
        buildUi();
        preferenceListener = (prefs, key) -> {
            if ("theme".equals(key) && prefs.getBoolean("follow_core", true)) {
                String id = prefs.getString("theme", "fusion");
                themeSelector.setSelection(themeIndex(id));
                applyTheme(id);
            }
        };
    }

    private void buildUi() {
        ScrollView scroller = new ScrollView(this);
        layout = new LinearLayout(this);
        layout.setPadding(px(22), px(26), px(22), px(42));
        layout.setOrientation(LinearLayout.VERTICAL);
        scroller.addView(layout);
        setContentView(scroller);
        heading = text("AMPYCONNECT", 27, true);
        heading.setLetterSpacing(.11f);
        layout.addView(heading);
        layout.addView(text("HOME ASSISTANT · VISUALIZZATORE SICURO", 12, false));

        themeSelector = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, LABELS);
        themeSelector.setAdapter(adapter);
        themeSelector.setSelection(themeIndex(preferences.getString("theme", "fusion")));
        themeSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int index, long id) {
                String selected = IDS[index];
                boolean manuallyChanged = !selected.equals(
                        preferences.getString("theme", "fusion"));
                SharedPreferences.Editor editor = preferences.edit().putString("theme", selected);
                if (manuallyChanged) {
                    editor.putBoolean("follow_core", false);
                    if (follow != null) follow.setChecked(false);
                }
                editor.apply();
                applyTheme(selected);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        layout.addView(themeSelector);
        follow = new Switch(this);
        follow.setText("Segui skin AmpyCore");
        follow.setTextColor(Color.WHITE);
        follow.setChecked(preferences.getBoolean("follow_core", true));
        follow.setOnCheckedChangeListener((button, checked) ->
                preferences.edit().putBoolean("follow_core", checked).apply());
        layout.addView(follow);

        endpoint = field("https://home.example.org");
        endpoint.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        layout.addView(text("Server Home Assistant (HTTPS)", 14, true));
        layout.addView(endpoint);
        token = field("Token accesso a lungo termine (non viene salvato)");
        token.setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(text("Token Home Assistant", 14, true));
        layout.addView(token);
        query = new Button(this);
        query.setText("Leggi stati dei dispositivi");
        query.setAllCaps(false);
        query.setOnClickListener(v -> refresh());
        layout.addView(query);
        status = text("Nessuna connessione effettuata", 13, false);
        layout.addView(status);
        devices = text("Nessun dispositivo caricato", 14, false);
        layout.addView(devices);
        layout.addView(text("Solo GET /api/states. Nessuna automazione, "
                + "nessun comando verso attuatori o PLC. Certificati TLS verificati.",
                12, false));
        applyTheme(preferences.getString("theme", "fusion"));
    }

    private int px(float d) { return Math.round(d * getResources().getDisplayMetrics().density); }
    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Color.WHITE);
        t.setPadding(0, px(8), 0, px(8));
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.LTGRAY);
        e.setHint(hint);
        return e;
    }
    private int themeIndex(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
        return 0;
    }
    private void applyTheme(String id) {
        ConnectTheme style = ConnectTheme.load(this, id);
        layout.setBackgroundColor(style.background);
        heading.setTextColor(style.accent);
        GradientDrawable button = new GradientDrawable();
        button.setColor(style.accent);
        button.setCornerRadii(style.radiiDp(this));
        query.setBackground(button);
        query.setTextColor(style.background);
    }

    private void refresh() {
        final int seq = ++requestNumber;
        String host = endpoint.getText().toString().trim();
        String bearer = token.getText().toString().trim();
        final URL target;
        try {
            URI uri = new URI(host);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null
                    || uri.getFragment() != null || !("".equals(uri.getPath())
                    || "/".equals(uri.getPath()))) {
                throw new IllegalArgumentException("Inserisci esclusivamente l'URL HTTPS del server");
            }
            if (bearer.isEmpty() || bearer.contains("\n") || bearer.contains("\r")) {
                throw new IllegalArgumentException("Token non valido");
            }
            target = uri.resolve("/api/states").toURL();
        } catch (Exception e) {
            status.setText("Configurazione non valida: " + e.getMessage());
            return;
        }
        status.setText("Connessione TLS e richiesta in corso…");
        query.setEnabled(false);
        executor.execute(() -> {
            String response;
            try {
                HttpsURLConnection connection = (HttpsURLConnection) target.openConnection();
                connection.setConnectTimeout(6500);
                connection.setReadTimeout(9000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + bearer);
                try {
                    int statusCode = connection.getResponseCode();
                    if (statusCode != HttpURLConnection.HTTP_OK) {
                        throw new IllegalStateException("Risposta HTTP " + statusCode);
                    }
                    ByteArrayOutputStream content = new ByteArrayOutputStream();
                    byte[] bytes = new byte[4096];
                    try (InputStream stream = connection.getInputStream()) {
                        int n;
                        while ((n = stream.read(bytes)) != -1) {
                            content.write(bytes, 0, n);
                            if (content.size() > 1024 * 1024) {
                                throw new IllegalStateException("Risposta oltre 1 MiB");
                            }
                        }
                    }
                    JSONArray objects = new JSONArray(new String(
                            content.toByteArray(), StandardCharsets.UTF_8));
                    StringBuilder formatted = new StringBuilder();
                    int limit = Math.min(objects.length(), 60);
                    for (int i = 0; i < limit; i++) {
                        JSONObject obj = objects.getJSONObject(i);
                        formatted.append(obj.optString("entity_id", "sconosciuto"))
                                .append("  ·  ")
                                .append(obj.optString("state", "n/d")).append('\n');
                    }
                    response = "Stati disponibili: " + objects.length()
                            + "\n\n" + formatted;
                } finally {
                    connection.disconnect();
                }
            } catch (Exception e) {
                response = "Connessione non riuscita: " + e.getClass().getSimpleName()
                        + " (controlla HTTPS, certificato, token e server)";
            }
            final String result = response;
            runOnUiThread(() -> {
                if (closed || seq != requestNumber) return;
                devices.setText(result);
                query.setEnabled(true);
                status.setText("Richiesta completata");
            });
        });
    }

    @Override protected void onResume() {
        super.onResume();
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
        String theme = preferences.getString("theme", "fusion");
        themeSelector.setSelection(themeIndex(theme));
        applyTheme(theme);
    }
    @Override protected void onPause() {
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener);
        super.onPause();
    }
    @Override protected void onDestroy() {
        closed = true;
        ++requestNumber;
        executor.shutdownNow();
        super.onDestroy();
    }
}
