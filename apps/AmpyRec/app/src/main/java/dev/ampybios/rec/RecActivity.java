package dev.ampybios.rec;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.provider.DocumentsContract;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Safe user-confirmed AmpyRec local update controller.
 * No root, reboot API, block-device writes, recovery edits or OTA bypass.
 */
public final class RecActivity extends Activity {
    private static final int REQUEST_ZIP=301;
    private static final int REQUEST_RAM_ZIP=303;
    private static final int REQUEST_REFERENCE_ZIP=304;
    private static final int REQUEST_SOURCE_ZIP=305;
    private static final String LAUNCHER_PACKAGE="dev.ampybios.launcher";
    private static final int REQUEST_NOTIFICATIONS=302;
    private static final int BG=Color.rgb(6,9,22);
    private static final int ACCENT=Color.rgb(24,225,238);
    private static final int MUTED=Color.rgb(171,188,215);
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private TextView status, summary, sourceStatus;
    private Button choose, ramOnly, referenceOnly, now, later, background, clear;
    private volatile boolean destroyed;
    private boolean busy;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        buildUi();
        refreshButtons();
    }

    private int dp(int value) {
        return Math.round(value*getResources().getDisplayMetrics().density);
    }
    private TextView text(String value,int size,int color,boolean bold) {
        TextView t=new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(0,dp(8),0,dp(8));
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private Button button(LinearLayout parent,String label,View.OnClickListener click) {
        Button b=new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(BG);
        GradientDrawable shape=new GradientDrawable();
        shape.setColor(ACCENT);
        shape.setCornerRadius(dp(22));
        b.setBackground(shape);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(56));
        p.topMargin=dp(10);
        parent.addView(b,p);
        b.setOnClickListener(click);
        return b;
    }
    private void buildUi() {
        ScrollView scroll=new ScrollView(this);
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(22),dp(32),dp(22),dp(36));
        body.setBackgroundColor(BG);
        scroll.addView(body);
        setContentView(scroll);

        TextView title=text("AMPYREC",32,ACCENT,true);
        title.setLetterSpacing(.14f);
        title.setGravity(Gravity.CENTER);
        body.addView(title);
        body.addView(text("ZIP INSPECTOR  /  SAFE STAGING  /  LIVE UPDATES",
                12,MUTED,true));
        body.addView(text("AmpyRec è una cartella PRIVATA dell'app, non una "
                        +"partizione fisica. Analizza i file prima di applicare "
                        +"solo le modifiche supportate. Kernel, SystemUI, boot "
                        +"e firmware richiedono AmpyOTA di sistema con firma valida.",
                14,Color.WHITE,false));
        status=text("Nessuno ZIP selezionato",15,ACCENT,true);
        body.addView(status);
        summary=text("Nessuna modifica in attesa",13,MUTED,false);
        body.addView(summary);

        body.addView(text("SESSIONE GRAFICA AMPYBIOS · SORGENTI NON ESEGUIBILI",
                14,ACCENT,true));
        body.addView(text("Seleziona Downloads/AmpybiOS.zip. Il codice sorgente "
                +"viene analizzato, ma NON viene eseguito come ROM o kernel. "
                +"Per ottenere la Home AmpybiOS dopo il riavvio bisogna "
                +"installare separatamente AmpyLauncher e sceglierlo come Home.",
                13,Color.WHITE,false));
        sourceStatus=text("Nessun sorgente selezionato",13,MUTED,false);
        body.addView(sourceStatus);
        button(body,"Scegli Downloads/AmpybiOS.zip",v->pickSourceZip());
        button(body,"Ricontrolla integrità ZIP selezionato",v->checkSourceZip());
        button(body,"Configura AmpyLauncher come Home Android",v->chooseHome());
        button(body,"Apri AmpyLauncher ora",v->openLauncher());
        button(body,"Dimentica il riferimento al sorgente",v->{
            SourceSession.clear(this);
            sourceStatus.setText("Riferimento ZIP rimosso. Il file originale resta intatto.");
            refreshButtons();
        });

        referenceOnly=button(body,"Seleziona ZIP · conserva solo riferimento, nessuna copia",
                view->pickZip(REQUEST_REFERENCE_ZIP));
        choose=button(body,"Seleziona ZIP · copia privata per staging",view->pickZip(REQUEST_ZIP));
        ramOnly=button(body,"Applica LIVE in RAM · immediatamente",
                view->pickZip(REQUEST_RAM_ZIP));
        now=button(body,"2 · Applica LIVE adesso",view->applyNow());
        later=button(body,"3 · Rimanda al prossimo riavvio",view->deferInstall());
        background=button(body,"4 · Applica LIVE in background",view->schedule());
        clear=button(body,"Annulla aggiornamento in attesa",view->clearPending());

        body.addView(text("DOPO IL RIAVVIO: Android mostrerà una notifica AmpyRec "
                +"(se autorizzata). Potrai scegliere nuovamente tra installazione "
                +"immediata, rinvio oppure background. Nessun riavvio forzato.",
                12,MUTED,false));
        body.addView(text("MODALITÀ SYSTEM/KERNEL: sola analisi. Non installare "
                +"ZIP di sorgenti o firmware non firmati. L'aggiornamento "
                +"A/B richiederà un servizio di sistema dedicato e Verified Boot.",
                12,MUTED,false));
    }

    private void refreshButtons() {
        String state=RecState.prefs(this).getString("state","");
        boolean pending="staged".equals(state)||"deferred".equals(state)||
                "background".equals(state);
        now.setEnabled(pending&&!busy);
        later.setEnabled(pending&&!busy);
        background.setEnabled(pending&&!busy);
        clear.setEnabled(pending&&!busy);
        choose.setEnabled(!busy);
        ramOnly.setEnabled(!busy);
        referenceOnly.setEnabled(!busy);
        String mode=RecState.prefs(this).getString("mode","disk");
        if(sourceStatus!=null && !busy && SourceSession.isSelected(this)
                && "Nessun sorgente selezionato".contentEquals(sourceStatus.getText())) {
            sourceStatus.setText("AmpybiOS.zip già selezionato: riferimento persistente."
                    +" Ricontrolla prima di utilizzare.");
        }
        summary.setText("Stato: "+(state.isEmpty()?"nessuno":state)+
                "\nStaging: "+("reference".equals(mode)?"URI SAF, nessuna copia":
                        pending?"copia privata":"nessuno")+
                "\n"+RecState.prefs(this).getString("message","")+
                "\nFile privato: "+(pending&&"disk".equals(mode)?
                        "AmpyRec/staged.zip":"nessuno")+
                "\nNessuna scrittura nelle partizioni Android.");
    }
    private void show(String value) { status.setText(value); }

    private void pickSourceZip() {
        Intent open = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        open.addCategory(Intent.CATEGORY_OPENABLE);
        open.setType("*/*");
        open.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"application/zip","application/x-zip-compressed",
                        "application/octet-stream"});
        open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        // This is a picker hint, not unrestricted filesystem access.
        try {
            open.putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                    DocumentsContract.buildRootUri(
                            "com.android.providers.downloads.documents","downloads"));
        } catch (RuntimeException ignored) {}
        startActivityForResult(open,REQUEST_SOURCE_ZIP);
    }

    private void checkSourceZip() {
        if (busy) return;
        busy=true;
        refreshButtons();
        sourceStatus.setText("Rilettura dello ZIP originale e verifica SHA-256…");
        worker.execute(()->{
            String outcome;
            try {
                SourceZipInspector.Result result=SourceSession.recheck(
                        getApplicationContext());
                outcome="Sorgenti verificati ("+result.count+" file, SHA-256 "
                        +result.sha256.substring(0,16)+"). Solo Home grafica.";
            } catch(Exception error) {
                outcome="Riferimento non valido: "+error.getMessage();
            }
            final String message=outcome;
            runOnUiThread(()->{
                if(destroyed)return;
                sourceStatus.setText(message);
                busy=false;
                refreshButtons();
            });
        });
    }

    private String selectedFileName(Uri uri) {
        try (Cursor cursor=getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) {
            if(cursor!=null&&cursor.moveToFirst())return cursor.getString(0);
        } catch(RuntimeException ignored) {}
        return "";
    }

    private void chooseHome() {
        if (getPackageManager().getLaunchIntentForPackage(LAUNCHER_PACKAGE)==null) {
            sourceStatus.setText("AmpyLauncher non è installato: installa prima il suo APK.");
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        } catch(RuntimeException error) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void openLauncher() {
        Intent launch=getPackageManager().getLaunchIntentForPackage(LAUNCHER_PACKAGE);
        if(launch==null) {
            sourceStatus.setText("AmpyLauncher non è installato.");
            return;
        }
        startActivity(launch);
    }

    private void pickZip(int mode) {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"application/zip","application/octet-stream"});
        startActivityForResult(intent,mode);
    }

    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (request==REQUEST_SOURCE_ZIP) {
            if(result!=RESULT_OK||data==null||data.getData()==null)return;
            Uri selected=data.getData();
            String name=selectedFileName(selected);
            if(!"AmpybiOS.zip".equalsIgnoreCase(name)) {
                sourceStatus.setText("Seleziona il file chiamato AmpybiOS.zip "
                        +"nella cartella Downloads; scelto: "+name);
                return;
            }
            busy=true;refreshButtons();
            sourceStatus.setText("Analisi dei sorgenti senza estrazione né copia…");
            worker.execute(()->{
                String outcome;
                try {
                    SourceZipInspector.Result inspected=SourceSession.select(
                            getApplicationContext(),selected);
                    outcome="AmpybiOS.zip verificato: "+inspected.count+
                            " file, SHA-256 "+inspected.sha256.substring(0,16)+
                            ". Seleziona AmpyLauncher come Home per vederlo dopo il riavvio.";
                } catch(Exception failure) {
                    outcome="Sorgenti rifiutati: "+failure.getMessage();
                }
                final String message=outcome;
                runOnUiThread(()->{
                    if(destroyed)return;
                    sourceStatus.setText(message);
                    busy=false;refreshButtons();
                });
            });
            return;
        }
        if((request!=REQUEST_ZIP&&request!=REQUEST_RAM_ZIP&&
                request!=REQUEST_REFERENCE_ZIP)||result!=RESULT_OK||data==null||
                data.getData()==null) return;
        Uri uri=data.getData();
        final boolean memoryOnly=request==REQUEST_RAM_ZIP;
        final boolean zeroCopy=request==REQUEST_REFERENCE_ZIP;
        busy=true; refreshButtons(); show(memoryOnly
                ?"Analisi e applicazione direttamente in RAM…"
                :zeroCopy?"Verifica ZIP e permesso persistente, senza copia…"
                :"Copio e verifico lo ZIP privato…");
        worker.execute(()->{
            String message;
            try {
                if(memoryOnly) {
                    message=RecState.applyDirectFromRam(getApplicationContext(),uri);
                } else {
                    RecPackage.Report report=zeroCopy
                            ?RecState.stageReference(getApplicationContext(),uri)
                            :RecState.stage(getApplicationContext(),uri);
                    message=report.description+"\nSHA-256: "+report.zipSha256+
                            "\nDimensioni ZIP: "+report.archiveBytes+" byte";
                    if(report.kind!=RecPackage.Kind.LIVE_THEME)
                        message+="\nINSTALLAZIONE NON DISPONIBILE: solo analisi.";
                }
            } catch(Exception error) {
                message="ZIP rifiutato: "+error.getMessage();
            }
            final String result=message;
            runOnUiThread(()->{
                if(destroyed)return;
                busy=false; show(result); refreshButtons();
            });
        });
    }

    private void applyNow() {
        busy=true; refreshButtons(); show("Verifica integrità e applicazione LIVE…");
        worker.execute(()->{
            String message;
            try {
                message=RecState.apply(getApplicationContext());
                BootReminder.clear(this);
            } catch(Exception error) {
                message="Aggiornamento non applicato: "+error.getMessage();
            }
            final String result=message;
            runOnUiThread(()->{
                if(destroyed)return;
                busy=false; show(result); refreshButtons();
            });
        });
    }

    private void deferInstall() {
        RecState.cancelJob(this);
        RecState.defer(this);
        requestNotificationIfNeeded();
        BootReminder.notifyUser(this); // optional immediate notification preview
        show("Rinviato: AmpyRec lo ricorderà al prossimo riavvio.");
        refreshButtons();
    }

    private void requestNotificationIfNeeded() {
        if(Build.VERSION.SDK_INT>=33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=
                        PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS);
        }
    }
    @Override public void onRequestPermissionsResult(int requestCode,
            String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == REQUEST_NOTIFICATIONS && grants.length > 0 &&
                grants[0] == PackageManager.PERMISSION_GRANTED) {
            BootReminder.notifyUser(this);
        }
    }

    private void schedule() {
        if(RecState.scheduleBackground(this)) {
            BootReminder.clear(this);
            show("JobScheduler attivato. Android sceglie quando applicare i temi LIVE "
                    +"(batteria e spazio disponibili), anche dopo il riavvio.");
        } else {
            show("Impossibile pianificare: riprova adesso oppure al riavvio.");
        }
        refreshButtons();
    }
    private void clearPending() {
        RecState.cancelPending(this);
        BootReminder.clear(this);
        show("Aggiornamento in attesa annullato. I temi già applicati restano.");
        refreshButtons();
    }

    @Override protected void onResume() {
        super.onResume();
        if(status!=null)refreshButtons();
    }
    @Override protected void onDestroy() {
        destroyed=true;
        worker.shutdown();
        super.onDestroy();
    }
}
