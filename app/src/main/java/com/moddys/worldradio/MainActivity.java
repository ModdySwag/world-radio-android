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
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;

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

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        askForNotificationPermission();

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);        // favourites live in localStorage
        s.setAllowFileAccess(true);          // index.html loads countries.js + stations.js
        s.setLoadsImagesAutomatically(true);
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
        });

        setContentView(web, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (state == null) {
            web.loadUrl(PAGE);
        } else {
            web.restoreState(state);
        }
        pageRef = new WeakReference<>(web);
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
    }
}
