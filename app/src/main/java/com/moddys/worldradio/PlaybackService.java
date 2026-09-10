package com.moddys.worldradio;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.util.Log;

/**
 * Keeps the stream alive while the app is backgrounded or the screen is off, and owns
 * the notification plus lock-screen controls.
 *
 * Why a service at all: Android kills a backgrounded app, and when the process dies the
 * WebView (and therefore the audio) dies with it. Running as a mediaPlayback foreground
 * service is what makes "leave the app, keep listening" actually work.
 *
 * The service never touches audio itself - it forwards commands to the page and prints
 * the state the page reports. Live radio has no seek, so pause and stop are the same
 * thing: the stream is simply disconnected.
 *
 * <p><b>It does not ask for audio focus.</b> That is deliberate, and it is the fix for
 * "another app wants the sound" appearing when nothing else was playing. The WebView's
 * engine is what actually plays the stream, and it requests focus itself, exactly as any
 * media app does. Asking a second time from here created a second focus client inside this
 * one app: the framework then told us we had lost focus to ourselves, and on several
 * devices that arrives as a refusal or a permanent loss the moment playback starts. The
 * symptoms were a stream that died after a fraction of a second, or a dialog about another
 * app that was not there.
 *
 * <p>What it does instead is watch - {@code registerAudioPlaybackCallback}, filtered to
 * other apps' UIDs, so this app's own WebView playback can never be mistaken for a
 * competitor. That observation drives the page's "another app is playing" choice without
 * taking anything away from anyone.
 */
public class PlaybackService extends Service {

    private static final String TAG = "WRPlayback";

    static final String ACTION_PLAYING = "com.moddys.worldradio.action.PLAYING";
    static final String ACTION_STOPPED = "com.moddys.worldradio.action.STOPPED";
    static final String ACTION_PAUSE = "com.moddys.worldradio.action.PAUSE";
    static final String ACTION_RESUME = "com.moddys.worldradio.action.RESUME";
    static final String EXTRA_STATION = "station";

    /** Radio: it is music, and the session should look like music to the system. */
    private static final AudioAttributes ATTRS = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build();

    private static final String CHANNEL_ID = "playback";
    private static final int NOTIFICATION_ID = 4210;
    private static final long WAKE_TIMEOUT_MS = 12L * 60L * 60L * 1000L;

    private MediaSession session;
    private PowerManager.WakeLock wake;
    private AudioManager audio;
    private AudioManager.AudioPlaybackCallback playbackCb;
    private String station;
    private String pauseText;         // non-null while sitting in the paused state
    private boolean foreground = false;
    private boolean otherAppPlaying = false;

    @Override
    public void onCreate() {
        super.onCreate();
        station = getString(R.string.app_name);

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.channel_desc));
        channel.setShowBadge(false);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(channel);

        audio = getSystemService(AudioManager.class);

        session = new MediaSession(this, "WorldRadio");
        session.setPlaybackToLocal(ATTRS);
        session.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                tellPage("play");
            }

            @Override
            public void onPause() {
                pauseAndStop();
            }

            @Override
            public void onStop() {
                pauseAndStop();
            }
        });
        session.setActive(true);

        watchOtherApps();

        PowerManager pm = getSystemService(PowerManager.class);
        if (pm != null) {
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WorldRadio:playback");
            wake.setReferenceCounted(false);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = (intent == null) ? null : intent.getAction();

        // From the notification / lock screen: tear down immediately rather than waiting
        // for the page to confirm. While the app is backgrounded a page-driven intent
        // cannot legally start a service, so a failure there would strand the
        // notification and leave the stream playing with no way to stop it.
        if (ACTION_PAUSE.equals(action)) {
            pauseAndStop();
            return START_NOT_STICKY;
        }

        /* The user asked for the radio back, from the notification, the lock screen or the
           app. Nothing to negotiate: the page plays, and the WebView's own focus request
           handles the system from there. */
        if (ACTION_RESUME.equals(action)) {
            tellPage("play");
            return START_NOT_STICKY;
        }

        // The page reports it has stopped on its own (user tapped pause in the app).
        if (ACTION_STOPPED.equals(action)) {
            if (otherAppPlaying) {
                /* Something else is playing and the page stopped for it. Keep the
                   notification up, offering the way back, instead of vanishing. */
                showPaused(getString(R.string.notification_other));
            } else {
                teardown();
            }
            return START_NOT_STICKY;
        }

        if (ACTION_PLAYING.equals(action)) {
            String s = intent.getStringExtra(EXTRA_STATION);
            if (s != null && !s.trim().isEmpty()) station = s.trim();
            showPlaying();
            return START_NOT_STICKY;
        }

        // Nothing is playing (e.g. a restart with no intent): leave no empty
        // notification and no stray wake lock behind.
        stopSelf();
        return START_NOT_STICKY;
    }

    /** Swiping the app away kills its WebView, so the notification must go with it. */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        releaseWake();
        stopForegroundCompat();
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        releaseWake();
        if (audio != null && playbackCb != null) {
            try {
                audio.unregisterAudioPlaybackCallback(playbackCb);
            } catch (Throwable t) {
                Log.w(TAG, "unregister playback callback: " + t);
            }
            playbackCb = null;
        }
        if (session != null) {
            session.setActive(false);
            session.release();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /* ------------------------------------------------------- who else is playing ---- */

    /**
     * Watch what else is playing, without competing for anything. The callback is filtered
     * to other apps' UIDs: this app's own playback (the WebView) shares our UID, so it can
     * never be reported as a competing app, which is exactly the false positive that used
     * to produce "another app wants the sound" on a device where nothing else was playing.
     */
    private void watchOtherApps() {
        if (audio == null || Build.VERSION.SDK_INT < 26) return;
        try {
            playbackCb = new AudioManager.AudioPlaybackCallback() {
                @Override
                public void onPlaybackConfigChanged(java.util.List<AudioPlaybackConfiguration> configs) {
                    boolean other = false;
                    if (configs != null) {
                        for (AudioPlaybackConfiguration c : configs) {
                            if (c == null || !c.isActive()) continue;
                            int uid;
                            try {
                                uid = c.getClientUid();
                            } catch (Throwable t) {
                                continue;                    // not ours to read: skip it
                            }
                            if (uid > 0 && uid != Process.myUid()) {
                                other = true;
                                break;
                            }
                        }
                    }
                    setOtherAppPlaying(other);
                }
            };
            audio.registerAudioPlaybackCallback(playbackCb, new Handler(Looper.getMainLooper()));
        } catch (Throwable t) {
            /* Some devices restrict this. Nothing is lost: the choice is simply never
               offered, and the notification still carries Play and Stop. */
            Log.w(TAG, "cannot watch other apps' playback: " + t);
            playbackCb = null;
        }
    }

    private void setOtherAppPlaying(boolean other) {
        if (other == otherAppPlaying) return;
        otherAppPlaying = other;
        Log.i(TAG, "another app " + (other ? "started" : "stopped") + " playing");
        /* The page owns the decision; it knows whether the user is looking at the app and
           whether anything was actually playing to interrupt. */
        tellFocus(other ? "otherApp" : "otherAppGone");
    }

    /* ------------------------------------------------------------------ states ---- */

    /** Ask the page to disconnect, then take the shell down regardless of its reply. */
    private void pauseAndStop() {
        tellPage("pause");
        teardown();
    }

    private void teardown() {
        releaseWake();
        pauseText = null;
        setPlaybackState(PlaybackState.STATE_STOPPED);
        stopForegroundCompat();
        stopSelf();
    }

    /** The state to show while the page is playing. */
    private void showPlaying() {
        pauseText = null;
        session.setMetadata(metadata(getString(R.string.notification_tagline)));
        startForegroundCompat();
        acquireWake();
        setPlaybackState(PlaybackState.STATE_PLAYING);
    }

    /**
     * Keep the notification, drop the stream. Losing the output must not make the player
     * vanish: the user still needs a way back to it.
     */
    private void showPaused(String why) {
        pauseText = why;
        releaseWake();                              // nothing is playing, so nothing to keep awake
        session.setMetadata(metadata(why));
        startForegroundCompat();
        setPlaybackState(PlaybackState.STATE_PAUSED);
    }

    private MediaMetadata metadata(String subtitle) {
        return new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, station)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, subtitle)
                .build();
    }

    private void tellPage(String method) {
        MainActivity.pageCommand("window.__wr&&window.__wr." + method + "()");
    }

    private void tellFocus(String kind) {
        MainActivity.pageCommand("window.__wr&&window.__wr.focus&&window.__wr.focus('" + kind + "')");
    }

    private void setPlaybackState(int state) {
        long actions = PlaybackState.ACTION_PLAY
                | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_STOP;
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(actions)
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                .build());
    }

    private void startForegroundCompat() {
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
        foreground = true;
    }

    private void stopForegroundCompat() {
        if (foreground) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE);
            foreground = false;
        }
    }

    private Notification buildNotification() {
        /* Paused: the first action becomes "Play" - the way back is one tap - and the
           subtitle says who stopped it. */
        boolean paused = pauseText != null;

        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent content = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        PendingIntent primary = PendingIntent.getService(this, 1,
                new Intent(this, PlaybackService.class).setAction(paused ? ACTION_RESUME : ACTION_PAUSE),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stopAll = PendingIntent.getService(this, 2,
                new Intent(this, PlaybackService.class).setAction(ACTION_PAUSE),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Action primaryAction = new Notification.Action.Builder(
                Icon.createWithResource(this, paused ? R.drawable.ic_play : R.drawable.ic_pause),
                paused ? "Play" : "Pause", primary).build();
        Notification.Action stopAction = new Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_stop), "Stop", stopAll).build();

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_radio)
                .setContentTitle(station)
                .setContentText(paused ? pauseText : getString(R.string.notification_tagline))
                .setContentIntent(content)
                /* A paused radio should be dismissible: swiping it away stops it. While it
                   is actually playing the notification stays put, as a live stream does. */
                .setOngoing(!paused)
                .setDeleteIntent(stopAll)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setStyle(new Notification.MediaStyle()
                        .setMediaSession(session.getSessionToken())
                        .setShowActionsInCompactView(0, 1))
                .addAction(primaryAction)
                .addAction(stopAction)
                .build();
    }

    private void acquireWake() {
        if (wake != null && !wake.isHeld()) {
            wake.acquire(WAKE_TIMEOUT_MS);
        }
    }

    private void releaseWake() {
        if (wake != null && wake.isHeld()) {
            wake.release();
        }
    }
}
