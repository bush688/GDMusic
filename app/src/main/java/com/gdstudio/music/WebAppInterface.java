package com.gdstudio.music;

import android.webkit.JavascriptInterface;

public class WebAppInterface {
    private final MainActivity activity;

    public WebAppInterface(MainActivity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public void onPlaybackStateChanged(boolean isPlaying, String audioSrc) {
        if (activity.isBound && activity.musicService != null) {
            activity.musicService.setPlaying(isPlaying);
        }
        activity.updatePlayerUIFromBridge(
            activity.musicService != null ? activity.musicService.getCurrentTitle() : "",
            activity.musicService != null ? activity.musicService.getCurrentArtist() : "",
            isPlaying
        );
    }

    @JavascriptInterface
    public void onSongChanged(String title, String artist, String albumArtUrl) {
        if (activity.isBound && activity.musicService != null) {
            activity.musicService.updateInfo(title, artist, true);
        }
        activity.updatePlayerUIFromBridge(title, artist, true);
    }

    @JavascriptInterface
    public void onProgressUpdate(int positionMs, int durationMs) {
        activity.runOnUiThread(() -> {
            if (activity.isBound && activity.musicService != null) {
                activity.musicService.setProgress(positionMs, durationMs);
            }
        });
    }

    @JavascriptInterface
    public void onSongEnded() {
        if (activity.isBound && activity.musicService != null) {
            activity.musicService.setPlaying(false);
        }
    }
}
