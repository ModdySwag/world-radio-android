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
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

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
 * It does own audio focus, because that is the part the page cannot do: asking the system
 * for the output, and telling the page what the system decided. The decision itself still
 * lives in the page shell (window.__wr.focus), so there is one place that knows what
 * "paused" means.
 */
public class PlaybackService extends Service {

    static final String ACTION_PLAYING = "com.moddys.worldradio.action.PLAYING";
    static final String ACTION_STOPPED = "com.moddys.worldradio.action.STOPPED";
    static final String ACTION_PAUSE = "com.moddys.worldradio.action.PAUSE";
    static final String ACTION_RESUME = "com.moddys.worldradio.action.RESUME";
    static final String EXTRA_STATION = "station";

    /** Radio: it is music, and it should be treated as the thing the user is listening to. */
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
    private AudioFocusRequest focusRequest;
    private String station;
    private String pauseText;         // non-null while sitting in the paused state
    private boolean foreground = false;
    private boolean hasFocus = false;
    private boolean pausedByFocus = false;

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
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(ATTRS)
                /* the radio ducks itself under a navigation prompt, so the system should not
                   pause it on our behalf */
                .setWillPauseWhenDucked(false)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(new FocusListener())
                .build();

        session = new MediaSession(this, "WorldRadio");
        session.setPlaybackToLocal(ATTRS);
        session.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                if (requestFocus()) {
                    tellPage("play");
                } else {
                    showPaused(getString(R.string.notification_held));
                }
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

        /* The user asked for the radio back (from the notification, the lock screen or the
           app): take the output first, and only start if the system agrees. */
        if (ACTION_RESUME.equals(action)) {
            if (requestFocus()) {
                tellPage("play");
            } else {
                tellFocus("refused");
                showPaused(getString(R.string.notification_held));
            }
            return START_NOT_STICKY;
        }

        // The page reports it has stopped on its own (user tapped pause in the app).
        if (ACTION_STOPPED.equals(action)) {
            if (pausedByFocus) {
                /* We stopped it because someone else needed the output. Leave the
                   notification up, offering to reconnect, instead of vanishing on the
                   user mid-call. */
                pausedByFocus = false;
                showPaused(getString(R.string.notification_paused));
            } else {
                teardown();
            }
            return START_NOT_STICKY;
        }

        if (ACTION_PLAYING.equals(action)) {
            String s = intent.getStringExtra(EXTRA_STATION);
            if (s != null && !s.trim().isEmpty()) station = s.trim();

            /* Another app may already be the thing the user is listening to. Ask for the
               output: if it is refused, stop rather than play over it, and let the page ask
               the user what they want (continue / pause / stop). */
            if (!requestFocus()) {
                tellFocus("refused");
                showPaused(getString(R.string.notification_held));
                return START_NOT_STICKY;
            }

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
        abandonFocus();
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

    /** Ask the page to disconnect, then take the shell down regardless of its reply. */
    private void pauseAndStop() {
        tellPage("pause");
        teardown();
    }

    private void teardown() {
        releaseWake();
        abandonFocus();
        pauseText = null;
        pausedByFocus = false;
        setPlaybackState(PlaybackState.STATE_STOPPED);
        stopForegroundCompat();
        stopSelf();
    }

    /* ---------------------------------------------------------- audio focus ---- */

    /**
     * Ask the system to make this app the thing the user is hearing. False means another
     * app holds the output, in which case the radio stays quiet rather than playing over it.
     */
    private boolean requestFocus() {
        if (audio == null || focusRequest == null) return true;   // no AudioManager: never block playback
        if (hasFocus) return true;
        hasFocus = audio.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        return hasFocus;
    }

    private void abandonFocus() {
        if (hasFocus && audio != null && focusRequest != null) {
            audio.abandonAudioFocusRequest(focusRequest);
        }
        hasFocus = false;
    }

    /** What the system decided, forwarded to the page - which owns what to do about it. */
    private class FocusListener implements AudioManager.OnAudioFocusChangeListener {
        @Override
        public void onAudioFocusChange(int change) {
            switch (change) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    hasFocus = true;
                    tellFocus("gain");              // the page resumes if it armed a resume
                    break;

                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    tellFocus("duck");              // a prompt wants to be heard over us
                    break;

                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    pausedByFocus = true;           // a call: we will be wanted back
                    tellFocus("lossTransient");
                    break;

                case AudioManager.AUDIOFOCUS_LOSS:
                default:
                    pausedByFocus = true;           // someone else owns the output now
                    hasFocus = false;
                    tellFocus("loss");
                    break;
            }
        }
    }

    /** The state to show while the page is playing and the output is ours. */
    private void showPlaying() {
        pauseText = null;
        pausedByFocus = false;
        session.setMetadata(metadata(getString(R.string.notification_tagline)));
        startForegroundCompat();
        acquireWake();
        setPlaybackState(PlaybackState.STATE_PLAYING);
    }

    /**
     * Keep the notification, drop the stream. A focus loss must not make the player vanish:
     * the user still needs a way back to it, and a phone call is not a decision to stop
     * listening.
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
