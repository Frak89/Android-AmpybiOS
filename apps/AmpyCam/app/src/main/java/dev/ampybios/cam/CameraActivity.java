package dev.ampybios.cam;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.DngCreator;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Size;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.CompoundButton;
import android.widget.TextView;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

/**
 * First RAW-only AmpyCam prototype.
 * Captures single RAW_SENSOR stills as DNG to scoped MediaStore, using the
 * standard Camera2 API, with no root, Internet or background recording.
 * No Canon CR3 import: that belongs in the separate planned AmpyRaw editor.
 */
public final class CameraActivity extends Activity {
    private static final int REQUEST_CAMERA = 102;
    private static final int BACKGROUND = Color.rgb(6, 9, 22);
    private static final String[] THEMES = {"fusion", "cyberpunk", "futuristic", "minimal"};
    private static final String[] THEME_LABELS = {"Fusion", "Cyberpunk", "Futuristic", "Minimal"};

    private FrameLayout rootLayout;
    private TextView titleView;
    private Spinner themeSelector;
    private Switch followCore;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;
    private boolean settingThemeFromCore;
    private SharedPreferences themePreferences;
    private TextureView texture;
    private TextView status;
    private TextView details;
    private Button shutter;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private CameraDevice device;
    private CameraCaptureSession session;
    private CameraCharacteristics selectedCharacteristics;
    private ImageReader rawReader;
    private Surface previewSurface;
    private String selectedCameraId;
    private volatile boolean activityVisible;
    private boolean opening;
    private boolean capturePending;
    private long captureGeneration;
    private Image pendingRaw;
    private TotalCaptureResult pendingResult;

    private final TextureView.SurfaceTextureListener textureListener =
            new TextureView.SurfaceTextureListener() {
        @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int w, int h) {
            openCameraIfReady();
        }
        @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int w, int h) {}
        @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            Handler handler = cameraHandler;
            if (handler != null) handler.post(CameraActivity.this::closeCamera);
            else closeCamera();
            return true;
        }
        @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        themePreferences = getSharedPreferences("ampycam.theme", MODE_PRIVATE);
        makeUi();
        preferenceListener = (prefs, key) -> {
            if ("theme".equals(key) && prefs.getBoolean("follow_core", true)) {
                String value = prefs.getString("theme", "fusion");
                settingThemeFromCore = true;
                themeSelector.setSelection(findTheme(value));
                applyTheme(CamTheme.load(CameraActivity.this, value));
                settingThemeFromCore = false;
            }
        };
    }

    private void makeUi() {
        rootLayout = new FrameLayout(this);
        FrameLayout root = rootLayout;
        root.setBackgroundColor(BACKGROUND);
        texture = new TextureView(this);
        texture.setSurfaceTextureListener(textureListener);
        root.addView(texture, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout upper = new LinearLayout(this);
        upper.setOrientation(LinearLayout.VERTICAL);
        upper.setPadding(px(20), px(38), px(20), px(16));
        GradientDrawable upperBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xf8060916, 0x00060916});
        upper.setBackground(upperBg);
        titleView = label("AMPYCAM", 25, Color.CYAN, true);
        titleView.setLetterSpacing(.12f);
        upper.addView(titleView);
        details = label("RAW DNG  •  Camera2", 13, Color.WHITE, false);
        upper.addView(details);
        themeSelector = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, THEME_LABELS);
        themeSelector.setAdapter(adapter);
        upper.addView(themeSelector, new LinearLayout.LayoutParams(-1, px(44)));
        themeSelector.setSelection(findTheme(themePreferences.getString("theme", "fusion")));
        themeSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view,
                                                  int position, long id) {
                String name = THEMES[position];
                SharedPreferences.Editor editor = themePreferences.edit()
                        .putString("theme", name);
                // Spinner callback for a programmatic synchronization arrives
                // asynchronously: do not interpret it as a manual override.
                if (!settingThemeFromCore &&
                        !name.equals(themePreferences.getString("theme", "fusion"))) {
                    editor.putBoolean("follow_core", false);
                    if (followCore != null) followCore.setChecked(false);
                }
                editor.apply();
                applyTheme(CamTheme.load(CameraActivity.this, name));
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        followCore = new Switch(this);
        followCore.setText("Segui tema AmpyCore");
        followCore.setTextColor(Color.WHITE);
        followCore.setChecked(themePreferences.getBoolean("follow_core", true));
        followCore.setOnCheckedChangeListener((button, isChecked) ->
                themePreferences.edit().putBoolean("follow_core", isChecked).apply());
        upper.addView(followCore, new LinearLayout.LayoutParams(-1, px(42)));
        root.addView(upper, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        LinearLayout lower = new LinearLayout(this);
        lower.setOrientation(LinearLayout.VERTICAL);
        lower.setPadding(px(18), px(20), px(18), px(34));
        lower.setGravity(Gravity.CENTER_HORIZONTAL);
        GradientDrawable lowerBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x00060916, 0xf8060916});
        lower.setBackground(lowerBg);
        status = label("Inizializzazione fotocamera", 13, Color.WHITE, false);
        status.setGravity(Gravity.CENTER);
        lower.addView(status);
        shutter = new Button(this);
        shutter.setText("●   SCATTA RAW DNG");
        shutter.setAllCaps(false);
        shutter.setTextSize(17f);
        shutter.setTextColor(BACKGROUND);
        shutter.setEnabled(false);
        shutter.setOnClickListener(v -> {
            Handler handler = cameraHandler;
            if (handler != null) handler.post(this::takeRawPicture);
        });
        LinearLayout.LayoutParams button = new LinearLayout.LayoutParams(-1, px(66));
        button.topMargin = px(16);
        lower.addView(shutter, button);
        root.addView(lower, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        setContentView(root);
        applyTheme(CamTheme.load(this, THEMES[themeSelector.getSelectedItemPosition()]));
    }

    private int findTheme(String id) {
        for (int i = 0; i < THEMES.length; i++) {
            if (THEMES[i].equals(id)) return i;
        }
        return 0;
    }

    private void applyTheme(CamTheme theme) {
        rootLayout.setBackgroundColor(theme.background);
        titleView.setTextColor(theme.accent);
        details.setTextColor(theme.foreground);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(theme.accent);
        bg.setStroke(px(2), theme.secondary);
        bg.setCornerRadii(theme.radiiDp(this));
        shutter.setTextColor(theme.background);
        shutter.setBackground(bg);
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(size);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }
    private int px(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
    private void show(String message) {
        runOnUiThread(() -> status.setText(message));
    }
    private void canCapture(boolean enabled) {
        runOnUiThread(() -> shutter.setEnabled(enabled));
    }

    @Override protected void onResume() {
        super.onResume();
        activityVisible = true;
        themePreferences.registerOnSharedPreferenceChangeListener(preferenceListener);
        String syncedTheme = themePreferences.getString("theme", "fusion");
        settingThemeFromCore = true;
        themeSelector.setSelection(findTheme(syncedTheme));
        applyTheme(CamTheme.load(this, syncedTheme));
        settingThemeFromCore = false;
        cameraThread = new HandlerThread("AmpyCam-Camera2");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
        if (texture.isAvailable()) openCameraIfReady();
    }

    @Override protected void onPause() {
        activityVisible = false;
        themePreferences.unregisterOnSharedPreferenceChangeListener(preferenceListener);
        canCapture(false);
        // Serialize cleanup after any already queued image/capture callbacks.
        Handler handler = cameraHandler;
        if (handler != null) handler.post(this::closeCamera);
        else closeCamera();
        if (cameraThread != null) {
            cameraThread.quitSafely();
            cameraThread = null;
            cameraHandler = null;
        }
        super.onPause();
    }

    private void openCameraIfReady() {
        if (!activityVisible || opening || device != null || !texture.isAvailable() ||
                cameraHandler == null) return;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
            return;
        }
        opening = true;
        try {
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            selectedCameraId = null;
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics c = manager.getCameraCharacteristics(id);
                Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                int[] caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                if (facing == null || facing != CameraCharacteristics.LENS_FACING_BACK ||
                        caps == null) continue;
                boolean raw = false;
                for (int cap : caps) {
                    if (cap == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW) raw = true;
                }
                StreamConfigurationMap map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (!raw || map == null || map.getOutputSizes(ImageFormat.RAW_SENSOR) == null) continue;
                selectedCameraId = id;
                selectedCharacteristics = c;
                break;
            }
            if (selectedCameraId == null) {
                opening = false;
                show("Nessuna fotocamera posteriore con RAW_SENSOR disponibile");
                return;
            }
            manager.openCamera(selectedCameraId, cameraState, cameraHandler);
        } catch (CameraAccessException | SecurityException | IllegalArgumentException error) {
            opening = false;
            show("Fotocamera non disponibile: " + error.getClass().getSimpleName());
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                                       int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == REQUEST_CAMERA) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) {
                openCameraIfReady();
            } else show("Permesso fotocamera necessario per acquisire DNG");
        }
    }

    private final CameraDevice.StateCallback cameraState = new CameraDevice.StateCallback() {
        @Override public void onOpened(CameraDevice camera) {
            opening = false;
            if (!activityVisible) {
                camera.close();
                return;
            }
            device = camera;
            startPreview();
        }
        @Override public void onDisconnected(CameraDevice camera) {
            camera.close();
            device = null;
            opening = false;
            closeCamera();
            show("Fotocamera scollegata");
        }
        @Override public void onError(CameraDevice camera, int error) {
            camera.close();
            device = null;
            opening = false;
            closeCamera();
            show("Errore Camera2: " + error);
        }
    };

    private void startPreview() {
        if (device == null || cameraHandler == null || !texture.isAvailable()) return;
        try {
            StreamConfigurationMap map = selectedCharacteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes = map.getOutputSizes(ImageFormat.RAW_SENSOR);
            // Select a supported RAW size; max ensures the actual full-sensor output.
            Size raw = sizes[0];
            for (Size size : sizes) {
                if ((long) size.getWidth() * size.getHeight() >
                        (long) raw.getWidth() * raw.getHeight()) raw = size;
            }
            final Size selectedRawSize = raw;
            rawReader = ImageReader.newInstance(raw.getWidth(), raw.getHeight(),
                    ImageFormat.RAW_SENSOR, 2);
            rawReader.setOnImageAvailableListener(this::onRawAvailable, cameraHandler);
            SurfaceTexture st = texture.getSurfaceTexture();
            Size[] previewSizes = map.getOutputSizes(SurfaceTexture.class);
            if (previewSizes == null || previewSizes.length == 0) throw new IllegalStateException(
                    "Camera preview is unavailable");
            // RAW + PREVIEW combinations may reject huge TextureView surfaces.
            // Prefer the largest standard preview size up to Full HD.
            Size previewSize = null;
            for (Size size : previewSizes) {
                if (size.getWidth() <= 1920 && size.getHeight() <= 1080 &&
                        (previewSize == null ||
                         (long) size.getWidth() * size.getHeight() >
                         (long) previewSize.getWidth() * previewSize.getHeight())) {
                    previewSize = size;
                }
            }
            if (previewSize == null) previewSize = previewSizes[0];
            st.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            previewSurface = new Surface(st);
            device.createCaptureSession(Arrays.asList(previewSurface, rawReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override public void onConfigured(CameraCaptureSession configured) {
                            if (!activityVisible || device == null) {
                                configured.close();
                                return;
                            }
                            session = configured;
                            try {
                                CaptureRequest.Builder preview = device.createCaptureRequest(
                                        CameraDevice.TEMPLATE_PREVIEW);
                                preview.addTarget(previewSurface);
                                preview.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                configured.setRepeatingRequest(preview.build(), null, cameraHandler);
                                canCapture(true);
                                runOnUiThread(() -> details.setText(
                                        selectedRawSize.getWidth() + " × " +
                                        selectedRawSize.getHeight() +
                                        " · DNG · senza cloud"));
                                show("Pronta: salvataggio in Pictures/AmpyCam");
                            } catch (CameraAccessException | IllegalStateException e) {
                                canCapture(false);
                                show("Anteprima non disponibile");
                            }
                        }
                        @Override public void onConfigureFailed(CameraCaptureSession failed) {
                            failed.close();
                            closeCamera();
                            show("Configurazione fotocamera RAW non riuscita");
                        }
                    }, cameraHandler);
        } catch (CameraAccessException | RuntimeException e) {
            closeCamera();
            show("RAW non disponibile: " + e.getClass().getSimpleName());
        }
    }

    private void takeRawPicture() {
        if (!activityVisible || session == null || device == null ||
                cameraHandler == null || capturePending) return;
        final Handler callbackHandler = cameraHandler;
        capturePending = true;
        final long generation = ++captureGeneration;
        canCapture(false);
        show("Acquisizione RAW in corso...");
        try {
            CaptureRequest.Builder shot = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            shot.addTarget(rawReader.getSurface());
            shot.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            session.capture(shot.build(), new CameraCaptureSession.CaptureCallback() {
                @Override public void onCaptureCompleted(CameraCaptureSession session,
                        CaptureRequest request, TotalCaptureResult result) {
                    pendingResult = result;
                    saveIfReady();
                }
                @Override public void onCaptureFailed(CameraCaptureSession session,
                        CaptureRequest request, android.hardware.camera2.CaptureFailure failure) {
                    clearPending("Scatto RAW non riuscito");
                }
            }, callbackHandler);
            // Recover the shutter even when a device delivers neither a result
            // nor a failure callback. Executed on the same camera worker queue.
            callbackHandler.postDelayed(() -> {
                if (capturePending && captureGeneration == generation) {
                    clearPending("Timeout acquisizione RAW");
                }
            }, 8000);
        } catch (CameraAccessException | RuntimeException error) {
            clearPending("Impossibile scattare: " + error.getClass().getSimpleName());
        }
    }

    private void onRawAvailable(ImageReader reader) {
        Image frame = null;
        try {
            frame = reader.acquireNextImage();
            if (frame == null) return;
            if (!capturePending || pendingRaw != null) {
                frame.close();
                return;
            }
            pendingRaw = frame;
            saveIfReady();
        } catch (RuntimeException error) {
            if (frame != null && frame != pendingRaw) frame.close();
            clearPending("Lettura RAW fallita");
        }
    }

    private void saveIfReady() {
        if (!capturePending || pendingRaw == null || pendingResult == null) return;
        Long sensorTs = pendingResult.get(android.hardware.camera2.CaptureResult.SENSOR_TIMESTAMP);
        if (sensorTs == null || pendingRaw.getTimestamp() != sensorTs.longValue()) {
            clearPending("I metadati RAW non corrispondono al frame");
            return;
        }
        Uri output = null;
        boolean saved = false;
        try {
            ContentValues values = new ContentValues();
            String filename = "AmpyCam_" + new SimpleDateFormat(
                    "yyyyMMdd_HHmmss_SSS", Locale.ROOT).format(new Date()) + ".dng";
            values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/x-adobe-dng");
            values.put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/AmpyCam");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
            output = getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (output == null) throw new IOException("MediaStore rejected RAW file");
            try (OutputStream stream = getContentResolver().openOutputStream(output, "w");
                 DngCreator dng = new DngCreator(selectedCharacteristics, pendingResult)) {
                if (stream == null) throw new IOException("Cannot open MediaStore output");
                dng.writeImage(stream, pendingRaw);
            }
            ContentValues complete = new ContentValues();
            complete.put(MediaStore.Images.Media.IS_PENDING, 0);
            getContentResolver().update(output, complete, null, null);
            saved = true;
            show("DNG salvato in Pictures/AmpyCam");
        } catch (Exception e) {
            show("Salvataggio fallito: " + e.getClass().getSimpleName());
        } finally {
            if (!saved && output != null) {
                try {
                    getContentResolver().delete(output, null, null);
                } catch (RuntimeException ignored) {
                    // Cleanup is best-effort; still release RAW memory and shutter state.
                }
            }
            clearPending(null);
        }
    }

    private void clearPending(String message) {
        if (pendingRaw != null) {
            pendingRaw.close();
            pendingRaw = null;
        }
        pendingResult = null;
        capturePending = false;
        captureGeneration++;
        canCapture(device != null && session != null);
        if (message != null) show(message);
    }

    private void closeCamera() {
        clearPending(null);
        canCapture(false);
        if (session != null) { session.close(); session = null; }
        if (device != null) { device.close(); device = null; }
        if (rawReader != null) { rawReader.close(); rawReader = null; }
        if (previewSurface != null) { previewSurface.release(); previewSurface = null; }
        opening = false;
    }
}
