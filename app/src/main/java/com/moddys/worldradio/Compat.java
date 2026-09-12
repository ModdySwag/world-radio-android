package com.moddys.worldradio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.util.Log;
import android.webkit.WebView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Decides whether this device is supported, and says so out loud - once.
 *
 * The thresholds live in <code>assets/www/compat.json</code>, and the same numbers are what
 * the website's download section and the project README state. One file, so the
 * install-time requirement, this launch notice and the in-app Check panel cannot drift
 * apart.
 *
 * Two levels, on purpose:
 *
 * <ul>
 *   <li><b>hard</b> - the oldest version that can install the app at all. Android's own
 *       installer enforces it from <code>minSdkVersion</code>, which is why a device below
 *       it never gets as far as running this class; the text in "unsupported" exists for
 *       the case where an APK was sideloaded around the check.</li>
 *   <li><b>soft</b> - the version the app is tuned for. Below it the app runs, and the user
 *       gets one dismissible notice, because the honest answer to "will this work here?" is
 *       "probably, with rough edges, and here is exactly which part is at risk".</li>
 * </ul>
 *
 * The notice is deliberately informative rather than restrictive: on a device this old the
 * thing that actually decides whether the radio plays is the System WebView, which is not
 * something either the user or the app can choose independently of the device.
 */
final class Compat {

    private static final String TAG = "WorldRadio";
    private static final String PREFS = "worldradio-compat";
    private static final String KEY_SHOWN = "notice_shown_for";

    /** Loaded once from assets; null when compat.json is missing or unreadable. */
    private static Compat cached;
    private static boolean tried;

    private final int hardApi;
    private final int softApi;
    private final String hardLabel;
    private final String softLabel;
    private final String osName;
    private final String engineName;
    private final String engineLabel;
    private final int engineSoftMajor;
    private final String engineNote;
    private final String noticeTitle;
    private final String noticeBody;
    private final String positiveAction;
    private final String neutralAction;
    private final String unsupportedTitle;
    private final String unsupportedBody;

    private Compat(JSONObject o) {
        JSONObject os = o.optJSONObject("os");
        JSONObject hard = os == null ? null : os.optJSONObject("hard");
        JSONObject soft = os == null ? null : os.optJSONObject("soft");
        JSONObject eng = o.optJSONObject("engine");
        JSONObject notice = o.optJSONObject("notice");
        JSONObject unsup = o.optJSONObject("unsupported");

        hardApi = hard == null ? 26 : hard.optInt("api", 26);
        softApi = soft == null ? 26 : soft.optInt("api", hardApi);
        hardLabel = hard == null ? "a newer Android" : hard.optString("label", "a newer Android");
        softLabel = soft == null ? hardLabel : soft.optString("label", hardLabel);
        osName = os == null ? "Android" : os.optString("name", "Android");
        engineName = eng == null ? "Android System WebView" : eng.optString("name", "Android System WebView");
        engineLabel = eng == null ? "WebView" : eng.optString("label", "WebView");
        engineSoftMajor = eng == null ? 0 : eng.optInt("softMajor", 0);
        engineNote = eng == null ? "" : eng.optString("note", "");
        noticeTitle = notice == null ? "This device is older than this app is tuned for"
                : notice.optString("title", "This device is older than this app is tuned for");
        noticeBody = notice == null ? "" : notice.optString("body", "");
        positiveAction = notice == null ? "Continue" : notice.optString("positiveAction", "Continue");
        neutralAction = notice == null ? "" : notice.optString("neutralAction", "");
        unsupportedTitle = unsup == null ? "This Android version is not supported"
                : unsup.optString("title", "This Android version is not supported");
        unsupportedBody = unsup == null ? "" : unsup.optString("body", "");
    }

    /** The compat.json shipped in this APK, or null if it could not be read. */
    static synchronized Compat get(Context ctx) {
        if (cached != null || tried) return cached;
        tried = true;
        InputStream in = null;
        try {
            in = ctx.getAssets().open("www/compat.json");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            cached = new Compat(new JSONObject(out.toString("UTF-8")));
        } catch (Exception e) {
            Log.w(TAG, "compat.json not usable: " + e);
            cached = null;
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) { }
        }
        return cached;
    }

    boolean supported() { return Build.VERSION.SDK_INT >= hardApi; }

    boolean recommended() { return Build.VERSION.SDK_INT >= softApi; }

    /** "Android 9 (API 28)" - what the device actually reports, in the same shape as the label. */
    String system() {
        return osName + " " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
    }

    String engineVersion(Context ctx) {
        try {
            PackageInfo p = WebView.getCurrentWebViewPackage();
            return p == null ? "unknown" : (p.packageName + " " + p.versionName);
        } catch (Throwable t) {
            return "unknown";
        }
    }

    /** True when the WebView is old enough to be worth mentioning, never a blocker. */
    private boolean engineIsOld(Context ctx) {
        if (engineSoftMajor <= 0) return false;
        try {
            String v = engineVersion(ctx);
            String[] parts = v.split(" ");
            String num = parts.length > 1 ? parts[1] : "";
            int dot = num.indexOf('.');
            int major = Integer.parseInt(dot > 0 ? num.substring(0, dot) : num);
            return major < engineSoftMajor;
        } catch (Exception e) {
            return false;
        }
    }

    /** The verdict, as the page sees it: the shell renders this, it does not decide it. */
    JSONObject forPage(Context ctx) {
        JSONObject o = new JSONObject();
        try {
            o.put("supported", supported());
            o.put("recommended", recommended());
            o.put("system", system());
            o.put("hardLabel", hardLabel);
            o.put("softLabel", softLabel);
            o.put("engineNote", engineNote);
            if (engineIsOld(ctx)) {
                o.put("engineNote", engineNote + " - and this device's WebView is old enough ("
                        + engineSoftMajor + "+ is expected) that some stations will not decode.");
            }
        } catch (Exception e) {
            Log.w(TAG, "compat forPage: " + e);
        }
        return o;
    }

    /** Fill {system} / {hard} / {soft} / {engine} in the notice copy. */
    private String fill(Context ctx, String s) {
        return s.replace("{system}", system())
                .replace("{hard}", hardLabel)
                .replace("{soft}", softLabel)
                .replace("{engine}", engineName)
                .replace("{webview}", engineVersion(ctx));
    }

    /**
     * Put the notice in front of the user once per app version, when this device is below
     * the version the app is tuned for. Below the hard floor - which Android's installer
     * should have prevented - say so plainly instead.
     *
     * "Once" is deliberate: the information is worth having, a nag is not, and the Check
     * panel keeps the verdict visible from then on.
     */
    void maybeWarn(final Activity activity) {
        try {
            final boolean ok = supported();
            if (ok && recommended()) return;

            String version = versionName(activity);
            String key = version + "/" + hardApi + "/" + softApi + "/" + Build.VERSION.SDK_INT;
            SharedPreferences prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            if (key.equals(prefs.getString(KEY_SHOWN, null))) return;

            String title = fill(activity, ok ? noticeTitle : unsupportedTitle);
            String body = fill(activity, ok ? noticeBody : unsupportedBody);
            if (body.isEmpty()) return;

            AlertDialog.Builder b = new AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setMessage(body)
                    .setPositiveButton(fill(activity, positiveAction), null);
            if (ok && !neutralAction.isEmpty()) {
                b.setNeutralButton(fill(activity, neutralAction), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        MainActivity.pageCommand("window.__wr&&window.__wr.compat&&window.__wr.compat()");
                    }
                });
            }
            b.show();
            prefs.edit().putString(KEY_SHOWN, key).apply();
        } catch (Exception e) {
            /* a compatibility notice must never be the reason the app fails to start */
            Log.w(TAG, "compat notice failed: " + e);
        }
    }

    private static String versionName(Context ctx) {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }
}
