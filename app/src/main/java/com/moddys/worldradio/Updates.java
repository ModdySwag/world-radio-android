package com.moddys.worldradio;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/**
 * Keeps a sideloaded app current.
 *
 * <p>This app is not distributed through Play, so Play's own in-app update flow is not available
 * and there is nothing else to do the job: the check is one small JSON file on the site and the
 * install is the same APK download the user did the first time, carried out by DownloadManager and
 * handed to the system installer.
 *
 * <p>Nothing here runs on the UI thread, and nothing installs without the user's tap: the shell
 * shows the notice, the tap calls back in, the download runs, and the system installer asks for
 * its own confirmation.
 */
final class Updates {

    static final String TAG = "WorldRadio";

    /** The feed. A timestamp is added so no cache can hand back a stale answer. */
    private static final String FEED = "https://moddys.net/updates.json";
    private static final String APK_MIME = "application/vnd.android.package-archive";
    private static final long CHECK_AGAIN_AFTER_MS = 10 * 60 * 1000L;

    private static long lastCheckedAt = 0;

    private Updates() { }

    /** What the feed says about this platform, once compared with this build. */
    static final class Info {
        final boolean available;
        final int code;
        final String version, url, sha256, notes;

        Info(boolean available, int code, String version, String url, String sha256, String notes) {
            this.available = available;
            this.code = code;
            this.version = version == null ? "" : version;
            this.url = url == null ? "" : url;
            this.sha256 = sha256 == null ? "" : sha256;
            this.notes = notes == null ? "" : notes;
        }

        /** The shape the shell reads: window.__wrUpdate.available({...}). */
        String json() {
            JSONObject o = new JSONObject();
            try {
                o.put("version", version);
                o.put("url", url);
                o.put("sha256", sha256);
                o.put("notes", notes);
                o.put("code", code);
                o.put("platform", "android");
            } catch (Exception e) {
                Log.w(TAG, "info json: " + e);
            }
            return o.toString();
        }
    }

    interface Callback {
        /** Exactly one of the two is non-null. */
        void onResult(Info info, String error);
    }

    /** Ask the site what the newest build is. Never throws; failures arrive as an error. */
    static void check(final Context ctx, final boolean manual, final Callback cb) {
        if (!manual && System.currentTimeMillis() - lastCheckedAt < CHECK_AGAIN_AFTER_MS) {
            return;                      // a launch check already ran recently; do not nag the site
        }
        lastCheckedAt = System.currentTimeMillis();
        final Context app = ctx.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                Info info = null;
                String error = null;
                try {
                    info = fetch(app);
                } catch (Exception e) {
                    error = e.toString();
                }
                final Info result = info;
                final String failure = error;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() { cb.onResult(result, failure); }
                });
            }
        }, "update-check").start();
    }

    private static Info fetch(Context ctx) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(FEED + "?t=" + System.currentTimeMillis()).openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(6000);
            c.setRequestProperty("Cache-Control", "no-cache");
            c.setRequestProperty("Accept", "application/json");
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            JSONObject all = new JSONObject(sb.toString());
            JSONObject a = all.optJSONObject("android");
            if (a == null) throw new IllegalStateException("no android entry in the feed");
            int theirs = a.optInt("versionCode", 0);
            int mine = versionCode(ctx);
            return new Info(theirs > mine, theirs, a.optString("versionName"),
                    a.optString("url"), a.optString("sha256"), a.optString("notes"));
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** This build's version code, without depending on generated BuildConfig. */
    static int versionCode(Context ctx) {
        try {
            PackageInfo p = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= 28) return (int) p.getLongVersionCode();
            return p.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Download the new APK and hand it to the system installer. One tap from the shell reaches
     * here; the system's own confirmation is the last step, and it is not skippable by design.
     */
    static void downloadAndInstall(final Activity act, String url, String version, final String sha256) {
        if (url == null || url.trim().isEmpty()) {
            toast(act, "The update feed has no download link yet.");
            return;
        }
        try {
            final DownloadManager dm = (DownloadManager) act.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) { toast(act, "This device has no download service."); return; }
            String name = "world-radio-" + (version == null || version.isEmpty() ? "update" : version) + ".apk";
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setTitle("World Radio " + version);
            req.setDescription("Downloading the update");
            req.setMimeType(APK_MIME);
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalFilesDir(act, null, name);
            final long id = dm.enqueue(req);

            BroadcastReceiver done = new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent i) {
                    long finished = i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                    if (finished != id) return;
                    try { c.getApplicationContext().unregisterReceiver(this); } catch (Exception e) { }
                    install(act, dm, id, sha256);
                }
            };
            IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            if (Build.VERSION.SDK_INT >= 33) {
                act.getApplicationContext().registerReceiver(done, filter, Context.RECEIVER_EXPORTED);
            } else {
                act.getApplicationContext().registerReceiver(done, filter);
            }
            toast(act, "Downloading the update - your browser will ask to install it when it lands.");
        } catch (Exception e) {
            Log.w(TAG, "update download: " + e);
            toast(act, "Could not start the download: " + e);
        }
    }

    private static void install(Activity act, DownloadManager dm, long id, String sha256) {
        try {
            Uri apk = dm.getUriForDownloadedFile(id);
            if (apk == null) { toast(act, "The downloaded update could not be opened."); return; }

            /* The feed carries a checksum, so verify what arrived before offering it: a truncated
               download must not reach the installer pretending to be an app. */
            if (sha256 != null && !sha256.isEmpty() && !matches(act, apk, sha256)) {
                toast(act, "The downloaded file did not match the published one - not installing.");
                return;
            }

            /* Android 8+ makes "install unknown apps" a per-app permission. Installing an update
               over the running app needs it, and only the user can grant it. */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    && !act.getPackageManager().canRequestPackageInstalls()) {
                toast(act, "Allow \"install unknown apps\" for World Radio, then tap Update again.");
                Intent allow = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + act.getPackageName()));
                allow.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                act.startActivity(allow);
                return;
            }

            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(apk, APK_MIME);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            act.startActivity(i);
        } catch (Exception e) {
            Log.w(TAG, "update install: " + e);
            toast(act, "Could not start the installer: " + e);
        }
    }

    private static boolean matches(Context ctx, Uri apk, String expected) {
        InputStream in = null;
        try {
            in = ctx.getContentResolver().openInputStream(apk);
            if (in == null) return false;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString().equalsIgnoreCase(expected.trim());
        } catch (Exception e) {
            Log.w(TAG, "update verify: " + e);
            return false;
        } finally {
            try { if (in != null) in.close(); } catch (Exception e) { }
        }
    }

    private static void toast(final Activity act, final String message) {
        act.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(act, message, Toast.LENGTH_LONG).show();
            }
        });
    }
}
