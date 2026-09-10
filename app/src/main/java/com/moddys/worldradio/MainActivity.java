package com.moddys.worldradio;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;

import org.json.JSONObject;

/**
 * Hosts the existing MODDYS World Radio web app in a WebView and connects it to the
 * Android shell it never had: a foreground playback service, lock-screen controls and
 * permission for the ~8,330 cleartext streams in the catalogue.
 *
 * Nothing about playback logic is re-implemented. The page owns the stream (its own
 * single-owner model); this class only forwards state and commands. State comes from
 * the page's own debug API (window.__dbg) where available, and from listening to the
 * media element otherwise, so playback still works if that API ever changes.
 */
public class MainActivity extends Activity {

    static final String TAG = "WorldRadio";
    private static final String PAGE = "file:///android_asset/www/index.html";
    private static final int REQ_NOTIFICATIONS = 1001;

    /** The page owns playback; PlaybackService reaches it through this handle. */
    private static WeakReference<WebView> pageRef = new WeakReference<>(null);

    private WebView web;
    private String shim;
    private ViewGroup root;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        askForNotificationPermission();

        root = new FrameLayout(this);
        setContentView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (!buildWeb()) {
            /* Some tablets ship with no usable WebView at all (disabled, or never
               updated). Say so instead of crashing on launch. */
            web = null;
            showNoWebView();
            return;
        }
        if (state == null) {
            web.loadUrl(PAGE);
        } else {
            web.restoreState(state);
        }
        pageRef = new WeakReference<>(web);
    }

    /** Build the WebView and its client. False when this device has no usable one. */
    private boolean buildWeb() {
        try {
            web = new WebView(this);
        } catch (Throwable t) {
            Log.w(TAG, "no usable WebView on this device: " + t);
            return false;
        }
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);        // favourites live in localStorage
        s.setAllowFileAccess(true);          // index.html loads countries.js + stations.js
        s.setLoadsImagesAutomatically(true);
        /* The app leaves this off so the shell (notification, focus regain) can start a
           stream without a tap. Devices whose WebView ignores it fall back to needing one,
           which the shell detects and adapts to. */
        s.setMediaPlaybackRequiresUserGesture(false);
        // the page is a file:// origin and the streams are http:// - never block them
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        web.setBackgroundColor(Color.parseColor("#0B0B0F"));
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.addJavascriptInterface(new Bridge(), "WorldRadioJs");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                /* Re-assert this per load: a device that loses the setting between loads is
                   one of the ways "some tablets" end up unable to start a stream. */
                try { view.getSettings().setMediaPlaybackRequiresUserGesture(false); } catch (Exception ignored) { }
                String js = shim();
                if (!js.isEmpty()) view.evaluateJavascript(js, null);
            }

            /** Safety net: this app is a single page, so any attempt to navigate it
             *  somewhere else is a station's stream or homepage and belongs in the
             *  phone's browser. Without this the player is replaced by a web page and
             *  only BACK brings it back. */
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("file:///android_asset")) return false;
                openExternal(url);
                return true;
            }

            /** A failed load used to be silent: log it so a device report has something. */
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        android.webkit.WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    Log.w(TAG, "main frame failed: " + request.getUrl() + " - " + error.getDescription());
                }
            }

            /** Low-memory tablets kill the renderer, which takes the page and the audio with
             *  it. Rebuild rather than leaving a blank screen and a dead player. */
            @Override
            public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                Log.w(TAG, "renderer gone (crashed=" + (detail == null ? "?" : detail.didCrash()) + ") - rebuilding");
                pageRef = new WeakReference<>(null);
                if (web != null) {
                    root.removeView(web);
                    try { web.destroy(); } catch (Exception ignored) { }
                    web = null;
                }
                if (buildWeb()) {
                    pageRef = new WeakReference<>(web);
                    web.loadUrl(PAGE);
                }
                return true;                     // handled: Android must not kill the app
            }
        });

        root.addView(web, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return true;
    }

    /** The one thing this app cannot work without, so say it plainly rather than die. */
    private void showNoWebView() {
        TextView tv = new TextView(this);
        tv.setText(R.string.no_webview);
        tv.setTextSize(15);
        tv.setPadding(48, 96, 48, 48);
        tv.setBackgroundColor(Color.parseColor("#0B0B0F"));
        tv.setTextColor(Color.parseColor("#F4F1EA"));
        root.addView(tv, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (web != null) web.saveState(out);
    }

    @Override
    protected void onDestroy() {
        pageRef = new WeakReference<>(null);
        super.onDestroy();
    }

    /** Open a station's stream or homepage in whatever app the phone uses for it. */
    private void openExternal(String url) {
        if (url == null || url.isEmpty()) return;
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Log.w(TAG, "nothing can open " + url + ": " + e);
        }
    }

    /** Back navigates the app's own history (it is hash-routed) before leaving. */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && web != null && web.canGoBack()) {
            web.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /** Run JS in the page on the UI thread. Used by the playback service. */
    static void pageCommand(final String js) {
        final WebView w = pageRef.get();
        if (w == null) return;
        w.post(new Runnable() {
            @Override
            public void run() {
                try {
                    w.evaluateJavascript(js, null);
                } catch (Exception ignored) {
                    // page torn down between the check and the call
                }
            }
        });
    }

    private String shim() {
        if (shim != null) return shim;
        try (InputStream in = getAssets().open("www/_android_shim.js")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            shim = out.toString("UTF-8");
        } catch (Exception e) {
            Log.w(TAG, "shim not loaded: " + e);
            shim = "";
        }
        return shim;
    }

    private void askForNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},
                    REQ_NOTIFICATIONS);
        }
    }

    /** Receives playback state from the page. Public: WebView finds these methods by
     *  reflection, and a non-public class can fail that lookup. */
    public class Bridge {

        @JavascriptInterface
        public void state(boolean playing, String station, String error) {
            String label = (station == null || station.trim().isEmpty())
                    ? getString(R.string.app_name) : station.trim();
            Intent i = new Intent(MainActivity.this, PlaybackService.class);
            i.setAction(playing ? PlaybackService.ACTION_PLAYING
                    : PlaybackService.ACTION_STOPPED);
            i.putExtra(PlaybackService.EXTRA_STATION, label);
            try {
                if (playing) {
                    startForegroundService(i);
                } else {
                    startService(i);
                }
            } catch (Exception e) {
                Log.w(TAG, "service start refused: " + e);
            }
            if (error != null && !error.isEmpty()) {
                Log.i(TAG, "stream error: " + error);
            }
        }

        @JavascriptInterface
        public void log(String message) {
            Log.i(TAG, "page: " + message);
        }

        /** The page asking for a station's website or stream to be opened properly. This
         *  arrives on the WebView's JS thread, so hop to the UI thread to start it. */
        @JavascriptInterface
        public void url(final String address) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    openExternal(address);
                }
            });
        }

        /** What the shell needs to describe this device in its own check panel - and what a
         *  user can paste into a bug report when a tablet misbehaves. */
        @JavascriptInterface
        public String device() {
            JSONObject o = new JSONObject();
            try {
                o.put("manufacturer", Build.MANUFACTURER);
                o.put("model", Build.MODEL);
                o.put("api", Build.VERSION.SDK_INT);
                o.put("release", Build.VERSION.RELEASE);
                o.put("app", versionName());
                o.put("webview", webViewVersion());
                o.put("notifications", notificationsGranted());
            } catch (Exception e) {
                Log.w(TAG, "device(): " + e);
            }
            return o.toString();
        }
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    /** The WebView package and version: the most useful single fact in a device report,
     *  because it is what decides which streams can play and whether a tap is needed. */
    private String webViewVersion() {
        try {
            android.content.pm.PackageInfo p = WebView.getCurrentWebViewPackage();
            return p == null ? "unknown" : (p.packageName + " " + p.versionName);
        } catch (Throwable t) {
            return "unknown";
        }
    }

    private boolean notificationsGranted() {
        return Build.VERSION.SDK_INT < 33
                || checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                   == PackageManager.PERMISSION_GRANTED;
    }
}
