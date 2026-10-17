package dev.ampybios.core;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Synchronous, signature-permission protected AmpyRec importer.
 *
 * A release is prepared in a private versioned directory and becomes visible
 * only after a single committed preference pointer. No global system paths.
 * Returning a Bundle is a positive application acknowledgement, NOT an OTA.
 */
public final class ThemeImportProvider extends ContentProvider {
    public static final String AUTHORITY="dev.ampybios.core.themes";
    public static final String PERMISSION="dev.ampybios.permission.SKIN_SYNC";

    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        if (!"install-v1".equals(method))
            throw new IllegalArgumentException("Unsupported importer method");
        Context context=getContext();
        if (context==null || extras==null) throw new SecurityException("No caller context");
        int uid=Binder.getCallingUid();
        if (context.checkPermission(PERMISSION, Binder.getCallingPid(), uid)
                !=PackageManager.PERMISSION_GRANTED)
            throw new SecurityException("Signature permission required");
        String[] packages=context.getPackageManager().getPackagesForUid(uid);
        boolean rec=false;
        if (packages!=null) for (String pkg : packages)
            if ("dev.ampybios.rec".equals(pkg)) rec=true;
        if (!rec || context.getPackageManager().checkSignatures(
                "dev.ampybios.rec", context.getPackageName())
                !=PackageManager.SIGNATURE_MATCH)
            throw new SecurityException("Only signed AmpyRec may import");
        String revision=extras.getString("revision","");
        ArrayList<String> ids=extras.getStringArrayList("ids");
        ArrayList<String> data=extras.getStringArrayList("json");
        if (!revision.matches("[a-f0-9]{64}") || ids==null || data==null ||
                ids.isEmpty() || ids.size()>4 || ids.size()!=data.size())
            throw new IllegalArgumentException("Invalid release manifest");
        Set<String> unique=new HashSet<>();
        long combined=0;
        for (int i=0;i<ids.size();i++) {
            String id=ids.get(i), json=data.get(i);
            if (id==null || !id.matches("(fusion|cyberpunk|futuristic|minimal)") ||
                    !unique.add(id) || json==null || json.length()>24*1024)
                throw new IllegalArgumentException("Unexpected skin or duplicate");
            combined += json.length();
            if (combined>96*1024) throw new IllegalArgumentException("Theme payload too big");
            try {
                JSONObject obj=new JSONObject(json);
                if (obj.getInt("schema")!=1 || !id.equals(obj.getString("id")))
                    throw new IllegalArgumentException("Theme schema mismatch");
                JSONObject colors=obj.getJSONObject("colors");
                for (String key : new String[]{"background","surface","foreground",
                        "accent","secondary","radar"}) {
                    if (!colors.getString(key).matches("#[A-Fa-f0-9]{6}"))
                        throw new IllegalArgumentException("Invalid theme color");
                }
                JSONArray radii=obj.getJSONArray("corner_radii_dp");
                if (radii.length()!=4) throw new IllegalArgumentException("Invalid corners");
                for (int j=0;j<4;j++) {
                    double radius=radii.getDouble(j);
                    if (!Double.isFinite(radius)||radius<0||radius>96)
                        throw new IllegalArgumentException("Invalid corner radius");
                }
                ThemeProfile profile=ThemeProfile.parse(obj,context.getResources());
                if (profile.glowDefault<0||profile.glowDefault>100 ||
                        profile.speedDefault<0||profile.speedDefault>360)
                    throw new IllegalArgumentException("Invalid theme effects");
            } catch (org.json.JSONException invalid) {
                throw new IllegalArgumentException("Malformed skin JSON",invalid);
            } catch (Exception invalid) {
                throw new IllegalArgumentException("Skin validation failed",invalid);
            }
        }
        synchronized (ThemeImportProvider.class) {
            File parent=new File(context.getFilesDir(),"AmpyRecThemes/releases");
            if (!parent.isDirectory()&&!parent.mkdirs())
                throw new IllegalStateException("Theme releases directory unavailable");
            File release=new File(parent,revision);
            SharedPreferences settings=context.getSharedPreferences(
                    "ampycore.local.v1", Context.MODE_PRIVATE);
            if (release.isDirectory()&&revision.equals(
                    settings.getString("ampyrec.active",""))) return ack(revision,ids.size());
            File staging=new File(parent,revision+".staging");
            if (staging.exists()) removeTree(staging);
            if (!staging.mkdir()) throw new IllegalStateException("Cannot stage release");
            try {
                for(int i=0;i<ids.size();i++) {
                    File destination=new File(staging,ids.get(i)+".json");
                    try(FileOutputStream out=new FileOutputStream(destination)) {
                        out.write(data.get(i).getBytes(StandardCharsets.UTF_8));
                        out.getFD().sync();
                    }
                }
                if (release.exists()) removeTree(release);
                if (!staging.renameTo(release))
                    throw new IllegalStateException("Cannot commit release files");
                int generation=settings.getInt("ampyrec.import_version",0);
                if (!settings.edit().putString("ampyrec.active",revision)
                        .putInt("ampyrec.import_version",generation+1).commit())
                    throw new IllegalStateException("Cannot commit release pointer");
                return ack(revision,ids.size());
            } catch(java.io.IOException failure) {
                throw new IllegalStateException("Failed writing theme release",failure);
            } finally {
                if (staging.exists()) removeTree(staging);
            }
        }
    }

    private static Bundle ack(String revision,int count) {
        Bundle response=new Bundle();
        response.putBoolean("applied",true);
        response.putString("revision",revision);
        response.putInt("count",count);
        return response;
    }
    private static void removeTree(File file) {
        if (file.isDirectory()) {
            File[] children=file.listFiles();
            if (children!=null) for(File child:children) removeTree(child);
        }
        if (!file.delete()&&file.exists())
            throw new IllegalStateException("Cannot clean staging");
    }

    @Override public Cursor query(Uri uri,String[] projection,String selection,
                                  String[] args,String sortOrder) {
        throw new UnsupportedOperationException("No queries");
    }
    @Override public Uri insert(Uri uri,ContentValues values) {
        throw new UnsupportedOperationException("No inserts");
    }
    @Override public int delete(Uri uri,String selection,String[] args) {
        throw new UnsupportedOperationException("No deletes");
    }
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args) {
        throw new UnsupportedOperationException("No updates");
    }
    @Override public String getType(Uri uri) { return null; }
}
