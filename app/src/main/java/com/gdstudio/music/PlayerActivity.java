package com.gdstudio.music;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class PlayerActivity extends AppCompatActivity {

    private TextView tvTitle, tvArtist, tvCurrentTime, tvTotalTime;
    private ImageButton btnPlayPause, btnPrev, btnNext, btnBack;
    private SeekBar seekBar;

    private MusicService musicService;
    private boolean isBound = false;
    private boolean isSeekBarTracking = false;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            musicService = ((MusicService.MusicBinder) binder).getService();
            isBound = true;
            updateUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            if (action.equals(MusicService.ACTION_PROGRESS_UPDATE)) {
                if (!isSeekBarTracking) {
                    int pos = intent.getIntExtra("position", 0);
                    int dur = intent.getIntExtra("duration", 0);
                    updateSeekBar(pos, dur);
                }
            } else {
                updateUI();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        tvTitle = findViewById(R.id.tvPlayerTitle);
        tvArtist = findViewById(R.id.tvPlayerArtist);
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        tvTotalTime = findViewById(R.id.tvTotalTime);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnPrev = findViewById(R.id.btnPrev);
        btnNext = findViewById(R.id.btnNext);
        btnBack = findViewById(R.id.btnBack);
        seekBar = findViewById(R.id.seekBar);

        btnBack.setOnClickListener(v -> finish());

        btnPlayPause.setOnClickListener(v -> {
            if (isBound) {
                musicService.togglePlayPause();
                updateUI();
            }
            sendBroadcast(new Intent("com.gdstudio.music.TOGGLE_PLAY"));
        });

        btnPrev.setOnClickListener(v ->
            sendBroadcast(new Intent("com.gdstudio.music.PREV_FROM_PLAYER")));

        btnNext.setOnClickListener(v ->
            sendBroadcast(new Intent("com.gdstudio.music.NEXT_FROM_PLAYER")));

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && isBound) {
                    int pos = (int) ((long) progress * musicService.getDuration() / 1000);
                    tvCurrentTime.setText(formatTime(pos));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) { isSeekBarTracking = true; }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                isSeekBarTracking = false;
                if (isBound) {
                    int pos = (int) ((long) sb.getProgress() * musicService.getDuration() / 1000);
                    musicService.seekTo(pos);
                    Intent i = new Intent("com.gdstudio.music.SEEK");
                    i.putExtra("position", pos);
                    sendBroadcast(i);
                }
            }
        });

        bindService(new Intent(this, MusicService.class), connection, Context.BIND_AUTO_CREATE);

        IntentFilter filter = new IntentFilter();
        filter.addAction(MusicService.ACTION_PLAYBACK_STATE_CHANGED);
        filter.addAction(MusicService.ACTION_SONG_CHANGED);
        filter.addAction(MusicService.ACTION_PROGRESS_UPDATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
    }

    private void updateUI() {
        if (!isBound) return;
        tvTitle.setText(musicService.getCurrentTitle().isEmpty() ? "正在播放" : musicService.getCurrentTitle());
        tvArtist.setText(musicService.getCurrentArtist().isEmpty() ? "未知艺术家" : musicService.getCurrentArtist());
        btnPlayPause.setImageResource(musicService.isPlaying() ? R.drawable.ic_pause_large : R.drawable.ic_play_large);
    }

    private void updateSeekBar(int position, int duration) {
        runOnUiThread(() -> {
            if (duration > 0) {
                seekBar.setProgress((int) ((long) position * 1000 / duration));
                tvCurrentTime.setText(formatTime(position));
                tvTotalTime.setText(formatTime(duration));
            }
        });
    }

    private String formatTime(int ms) {
        int total = ms / 1000;
        return String.format("%d:%02d", total / 60, total % 60);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(receiver); } catch (Exception ignored) {}
        if (isBound) { unbindService(connection); isBound = false; }
    }
}
