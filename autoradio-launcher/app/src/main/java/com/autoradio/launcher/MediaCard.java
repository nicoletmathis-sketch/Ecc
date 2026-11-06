package com.autoradio.launcher;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/**
 * "Now playing" card. With notification access it shows the real title, artist,
 * cover and progress of whatever plays (Spotify, Bluetooth audio, ...); without it,
 * the buttons still work through media key events.
 */
final class MediaCard {

    interface Host {
        void openMusicApp();
    }

    private static final String LISTENER_SETTINGS = "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS";

    private final Activity activity;
    private final Host host;
    private final AudioManager audio;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final MediaSessionManager sessions;
    private final ComponentName listener;

    private final ImageView art;
    private final ImageView play;
    private final TextView title;
    private final TextView artist;
    private final TextView source;
    private final TextView elapsed;
    private final TextView remaining;
    private final ProgressBar progress;

    private MediaController controller;
    private boolean registered;
    private boolean resumed;
    private Bitmap artSource;

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged =
        new MediaSessionManager.OnActiveSessionsChangedListener() {
            @Override
            public void onActiveSessionsChanged(List<MediaController> controllers) {
                pick(controllers);
            }
        };

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            render();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            render();
        }

        @Override
        public void onSessionDestroyed() {
            refreshSessions();
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            renderProgress();
            if (resumed && isPlaying()) {
                handler.postDelayed(this, 1000);
            }
        }
    };

    MediaCard(Activity activity, View root, Host host) {
        this.activity = activity;
        this.host = host;
        audio = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        sessions = (MediaSessionManager) activity.getSystemService(Context.MEDIA_SESSION_SERVICE);
        listener = new ComponentName(activity, MediaListener.class);

        art = (ImageView) root.findViewById(R.id.media_art);
        play = (ImageView) root.findViewById(R.id.media_play);
        title = (TextView) root.findViewById(R.id.media_title);
        artist = (TextView) root.findViewById(R.id.media_artist);
        source = (TextView) root.findViewById(R.id.media_source);
        elapsed = (TextView) root.findViewById(R.id.media_elapsed);
        remaining = (TextView) root.findViewById(R.id.media_remaining);
        progress = (ProgressBar) root.findViewById(R.id.media_progress);

        art.setBackground(Ui.roundedSolid(activity, 0xFF2C2C2E, 10));
        art.setClipToOutline(true);

        root.findViewById(R.id.media_card).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onCardClick();
            }
        });
        bind(R.id.media_prev, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        bind(R.id.media_play, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        bind(R.id.media_next, KeyEvent.KEYCODE_MEDIA_NEXT);
    }

    void start() {
        resumed = true;
        refreshSessions();
        render();
    }

    void stop() {
        resumed = false;
        handler.removeCallbacks(tick);
    }

    void release() {
        stop();
        if (registered && sessions != null) {
            sessions.removeOnActiveSessionsChangedListener(sessionsChanged);
            registered = false;
        }
        if (controller != null) {
            controller.unregisterCallback(callback);
            controller = null;
        }
        handler.removeCallbacksAndMessages(null);
    }

    // ---- sessions -----------------------------------------------------------

    private boolean hasAccess() {
        String flat = Settings.Secure.getString(activity.getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(activity.getPackageName());
    }

    private void refreshSessions() {
        if (sessions == null || !hasAccess()) {
            setController(null);
            return;
        }
        try {
            if (!registered) {
                sessions.addOnActiveSessionsChangedListener(sessionsChanged, listener, handler);
                registered = true;
            }
            pick(sessions.getActiveSessions(listener));
        } catch (SecurityException e) {
            setController(null);
        }
    }

    private void pick(List<MediaController> list) {
        MediaController best = null;
        if (list != null) {
            for (MediaController c : list) {
                PlaybackState s = c.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) {
                    best = c;
                    break;
                }
                if (best == null) {
                    best = c;
                }
            }
        }
        setController(best);
    }

    private void setController(MediaController c) {
        if (controller != null && c != null && controller.getSessionToken().equals(c.getSessionToken())) {
            render();
            return;
        }
        if (controller != null) {
            controller.unregisterCallback(callback);
        }
        controller = c;
        if (c != null) {
            c.registerCallback(callback, handler);
        }
        render();
    }

    // ---- actions ------------------------------------------------------------

    private void bind(int id, final int keyCode) {
        activity.findViewById(id).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                press(keyCode);
            }
        });
    }

    private void press(int keyCode) {
        if (controller != null) {
            MediaController.TransportControls t = controller.getTransportControls();
            if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
                t.skipToPrevious();
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
                t.skipToNext();
            } else if (isPlaying()) {
                t.pause();
            } else {
                t.play();
            }
        } else {
            long now = SystemClock.uptimeMillis();
            audio.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
            audio.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
            // The player needs a moment before its state can be read back
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    render();
                }
            }, 600);
        }
    }

    private void onCardClick() {
        if (!hasAccess()) {
            try {
                activity.startActivity(new Intent(LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (RuntimeException ignored) {
                // Some head units hide this screen: fall back to the music app
            }
        }
        if (controller != null) {
            PendingIntent pi = controller.getSessionActivity();
            if (pi != null) {
                try {
                    pi.send();
                    return;
                } catch (PendingIntent.CanceledException ignored) {
                    // fall through
                }
            }
            Intent launch = activity.getPackageManager().getLaunchIntentForPackage(controller.getPackageName());
            if (launch != null) {
                activity.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            }
        }
        host.openMusicApp();
    }

    // ---- rendering ----------------------------------------------------------

    private boolean isPlaying() {
        if (controller != null) {
            PlaybackState s = controller.getPlaybackState();
            return s != null && s.getState() == PlaybackState.STATE_PLAYING;
        }
        return audio.isMusicActive();
    }

    private void render() {
        play.setImageResource(isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
        MediaMetadata m = controller != null ? controller.getMetadata() : null;
        if (m == null) {
            boolean access = hasAccess();
            title.setText(access ? R.string.media_idle_title : R.string.media_need_access);
            artist.setText(access ? R.string.media_idle_artist : R.string.media_need_access_hint);
            source.setText(controller != null ? appLabel(controller.getPackageName()) : "");
            showArt(null);
            progress.setProgress(0);
            elapsed.setText("");
            remaining.setText("");
            return;
        }
        CharSequence t = m.getText(MediaMetadata.METADATA_KEY_TITLE);
        CharSequence a = m.getText(MediaMetadata.METADATA_KEY_ARTIST);
        if (a == null) {
            a = m.getText(MediaMetadata.METADATA_KEY_ALBUM);
        }
        title.setText(t != null ? t : activity.getString(R.string.media_idle_title));
        artist.setText(a != null ? a : "");
        source.setText(appLabel(controller.getPackageName()));
        Bitmap b = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (b == null) {
            b = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        }
        if (b == null) {
            b = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        }
        showArt(b);
        renderProgress();
        handler.removeCallbacks(tick);
        if (resumed && isPlaying()) {
            handler.postDelayed(tick, 1000);
        }
    }

    private void renderProgress() {
        if (controller == null || controller.getMetadata() == null) {
            return;
        }
        long duration = controller.getMetadata().getLong(MediaMetadata.METADATA_KEY_DURATION);
        PlaybackState s = controller.getPlaybackState();
        if (duration <= 0 || s == null) {
            progress.setProgress(0);
            elapsed.setText("");
            remaining.setText("");
            return;
        }
        long pos = s.getPosition();
        if (s.getState() == PlaybackState.STATE_PLAYING) {
            pos += (long) ((SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime()) * s.getPlaybackSpeed());
        }
        pos = Math.max(0, Math.min(pos, duration));
        progress.setProgress((int) (pos * 1000 / duration));
        elapsed.setText(time(pos));
        remaining.setText("-" + time(duration - pos));
    }

    /** Covers can be huge: keep only a small copy, sized for the card. */
    private void showArt(Bitmap b) {
        if (b == null) {
            artSource = null;
            int pad = Ui.dp(activity, 18);
            art.setPadding(pad, pad, pad, pad);
            art.setImageResource(R.drawable.ic_note);
            return;
        }
        if (b == artSource) {
            return;
        }
        artSource = b;
        int size = Math.max(1, art.getLayoutParams().width);
        Bitmap small = (b.getWidth() > size || b.getHeight() > size)
            ? Bitmap.createScaledBitmap(b, size, size, true) : b;
        art.setPadding(0, 0, 0, 0);
        art.setImageBitmap(small);
    }

    private CharSequence appLabel(String pkg) {
        try {
            PackageManager pm = activity.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(info);
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    private static String time(long ms) {
        long s = ms / 1000;
        return String.format(Locale.FRANCE, "%d:%02d", s / 60, s % 60);
    }
}
