package com.gdstudio.music;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.core.app.NotificationCompat;
import androidx.media.session.MediaButtonReceiver;

public class MusicService extends Service {

    public static final String ACTION_PLAYBACK_STATE_CHANGED = "com.gdstudio.music.PLAYBACK_STATE";
    public static final String ACTION_SONG_CHANGED = "com.gdstudio.music.SONG_CHANGED";
    public static final String ACTION_PROGRESS_UPDATE = "com.gdstudio.music.PROGRESS_UPDATE";

    private static final String CHANNEL_ID = "gdmusic_playback";
    private static final int NOTIFICATION_ID = 101;

    private final IBinder binder = new MusicBinder();
    private MediaSessionCompat mediaSession;

    private String currentTitle = "";
    private String currentArtist = "";
    private boolean isPlaying = false;
    private int currentPosition = 0;
    private int duration = 0;

    public class MusicBinder extends Binder {
        public MusicService getService() { return MusicService.this; }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        setupMediaSession();
    }

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) MediaButtonReceiver.handleIntent(mediaSession, intent);
        return START_STICKY;
    }

    private void setupMediaSession() {
        mediaSession = new MediaSessionCompat(this, "GDMusicSession");
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override public void onPlay() { broadcastTogglePlay(true); }
            @Override public void onPause() { broadcastTogglePlay(false); }
            @Override public void onSkipToNext() { sendBroadcast(new Intent("com.gdstudio.music.NEXT")); }
            @Override public void onSkipToPrevious() { sendBroadcast(new Intent("com.gdstudio.music.PREV")); }
            @Override public void onSeekTo(long pos) {
                Intent i = new Intent("com.gdstudio.music.SEEK");
                i.putExtra("position", (int) pos);
                sendBroadcast(i);
            }
        });
        mediaSession.setActive(true);
    }

    private void broadcastTogglePlay(boolean play) {
        isPlaying = play;
        sendBroadcast(new Intent(ACTION_PLAYBACK_STATE_CHANGED));
    }

    public void updateInfo(String title, String artist, boolean playing) {
        this.currentTitle = title != null ? title : "";
        this.currentArtist = artist != null ? artist : "";
        this.isPlaying = playing;
        updateMediaSessionMetadata();
        showNotification();
        sendBroadcast(new Intent(ACTION_SONG_CHANGED));
    }

    public void setPlaying(boolean playing) {
        this.isPlaying = playing;
        updatePlaybackState();
        showNotification();
        sendBroadcast(new Intent(ACTION_PLAYBACK_STATE_CHANGED));
    }

    public void togglePlayPause() { setPlaying(!isPlaying); }

    public void setProgress(int position, int duration) {
        this.currentPosition = position;
        this.duration = duration;
        Intent intent = new Intent(ACTION_PROGRESS_UPDATE);
        intent.putExtra("position", position);
        intent.putExtra("duration", duration);
        sendBroadcast(intent);
    }

    public void seekTo(int position) { this.currentPosition = position; }

    public boolean isPlaying() { return isPlaying; }
    public String getCurrentTitle() { return currentTitle; }
    public String getCurrentArtist() { return currentArtist; }
    public int getDuration() { return duration; }
    public int getCurrentPosition() { return currentPosition; }

    private void updateMediaSessionMetadata() {
        MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentArtist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
            .build();
        mediaSession.setMetadata(metadata);
        updatePlaybackState();
    }

    private void updatePlaybackState() {
        int state = isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        PlaybackStateCompat playbackState = new PlaybackStateCompat.Builder()
            .setState(state, currentPosition, 1.0f)
            .setActions(
                PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE |
                PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS | PlaybackStateCompat.ACTION_SEEK_TO)
            .build();
        mediaSession.setPlaybackState(playbackState);
    }

    private void showNotification() {
        if (currentTitle.isEmpty()) return;
        Intent mainIntent = new Intent(this, MainActivity.class);
        PendingIntent mainPending = PendingIntent.getActivity(this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent prevIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
            this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS);
        PendingIntent playIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
            this, PlaybackStateCompat.ACTION_PLAY_PAUSE);
        PendingIntent nextIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
            this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(currentTitle)
            .setContentText(currentArtist.isEmpty() ? "GD Music" : currentArtist)
            .setContentIntent(mainPending)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .addAction(R.drawable.ic_skip_previous, "上一首", prevIntent)
            .addAction(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play,
                isPlaying ? "暂停" : "播放", playIntent)
            .addAction(R.drawable.ic_skip_next, "下一首", nextIntent)
            .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2))
            .build();

        if (isPlaying) {
            startForeground(NOTIFICATION_ID, notification);
        } else {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIFICATION_ID, notification);
            stopForeground(false);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "音乐播放", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("GD Music 播放控制");
            channel.setSound(null, null);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
    }
}
