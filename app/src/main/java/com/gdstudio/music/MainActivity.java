package com.gdstudio.music;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends AppCompatActivity {

    private static final String MUSIC_URL = "https://music.gdstudio.xyz/";

    private WebView webView;
    private SwipeRefreshLayout swipeRefresh;
    private ConstraintLayout playerBar;
    private ImageView albumArt;
    private TextView songTitle;
    private TextView songArtist;
    private ImageButton btnPlayPause;
    private ImageButton btnPrev;
    private ImageButton btnNext;
    private SeekBar seekBar;
    private TextView tvCurrentTime;
    private TextView tvTotalTime;
    private View noNetworkView;

    public MusicService musicService;
    public boolean isBound = false;
    private boolean isSeekBarTracking = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            musicService = ((MusicService.MusicBinder) binder).getService();
            isBound = true;
            updatePlayerUI();
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    private final BroadcastReceiver playerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            switch (action) {
                case MusicService.ACTION_PLAYBACK_STATE_CHANGED:
                case MusicService.ACTION_SONG_CHANGED:
                    updatePlayerUI();
                    break;
                case MusicService.ACTION_PROGRESS_UPDATE:
                    if (!isSeekBarTracking) {
                        int position = intent.getIntExtra("position", 0);
                        int duration = intent.getIntExtra("duration", 0);
                        updateSeekBar(position, duration);
                    }
                    break;
            }
        }
    };

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initViews();
        setupWebView();
        setupPlayerBar();
        bindMusicService();
        registerPlayerReceiver();
        if (isNetworkAvailable()) {
            loadWebsite();
        } else {
            showNoNetwork();
        }
    }

    private void initViews() {
        webView = findViewById(R.id.webView);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        playerBar = findViewById(R.id.playerBar);
        albumArt = findViewById(R.id.albumArt);
        songTitle = findViewById(R.id.songTitle);
        songArtist = findViewById(R.id.songArtist);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnPrev = findViewById(R.id.btnPrev);
        btnNext = findViewById(R.id.btnNext);
        seekBar = findViewById(R.id.seekBar);
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        tvTotalTime = findViewById(R.id.tvTotalTime);
        noNetworkView = findViewById(R.id.noNetworkView);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36");
        webView.addJavascriptInterface(new WebAppInterface(this), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                swipeRefresh.setRefreshing(true);
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                swipeRefresh.setRefreshing(false);
                injectJavaScript();
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                view.loadUrl(request.getUrl().toString());
                return true;
            }
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                swipeRefresh.setRefreshing(false);
                showNoNetwork();
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
        swipeRefresh.setOnRefreshListener(() -> {
            if (isNetworkAvailable()) {
                webView.reload();
            } else {
                swipeRefresh.setRefreshing(false);
                showNoNetwork();
            }
        });
        swipeRefresh.setColorSchemeResources(R.color.colorPrimary, R.color.colorAccent);
    }

    private void injectJavaScript() {
        String js = "javascript:(function(){" +
            "function hookAudio(){" +
            "var audios=document.querySelectorAll('audio');" +
            "audios.forEach(function(a){" +
            "if(a._hooked)return;a._hooked=true;" +
            "a.addEventListener('play',function(){" +
            "AndroidBridge.onPlaybackStateChanged(true,a.src||'');" +
            "var t=document.querySelector('.song-name,.title,.track-name,.player-title');" +
            "var ar=document.querySelector('.artist,.singer,.artist-name,.player-artist');" +
            "AndroidBridge.onSongChanged(t?t.innerText.trim():'正在播放',ar?ar.innerText.trim():'','');});" +
            "a.addEventListener('pause',function(){AndroidBridge.onPlaybackStateChanged(false,a.src||'');});" +
            "a.addEventListener('timeupdate',function(){" +
            "AndroidBridge.onProgressUpdate(Math.floor(a.currentTime*1000),Math.floor((a.duration||0)*1000));});" +
            "a.addEventListener('ended',function(){AndroidBridge.onSongEnded();});});}" +
            "hookAudio();" +
            "new MutationObserver(function(){hookAudio();}).observe(document.body,{childList:true,subtree:true});" +
            "window.AndroidSeekTo=function(ms){var a=document.querySelector('audio');if(a)a.currentTime=ms/1000;};" +
            "window.AndroidTogglePlay=function(){var a=document.querySelector('audio');if(a){if(a.paused)a.play();else a.pause();}};" +
            "})();";
        webView.loadUrl(js);
    }

    private void setupPlayerBar() {
        btnPlayPause.setOnClickListener(v -> {
            webView.loadUrl("javascript:window.AndroidTogglePlay&&window.AndroidTogglePlay();");
            if (isBound) { musicService.togglePlayPause(); updatePlayerUI(); }
        });
        btnPrev.setOnClickListener(v ->
            webView.loadUrl("javascript:(function(){var b=document.querySelector('.prev,[class*=prev],[class*=previous]');if(b)b.click();})();"));
        btnNext.setOnClickListener(v ->
            webView.loadUrl("javascript:(function(){var b=document.querySelector('.next,[class*=next]');if(b)b.click();})();"));
        playerBar.setOnClickListener(v -> startActivity(new Intent(this, PlayerActivity.class)));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && isBound) {
                    tvCurrentTime.setText(formatTime((int)((long)progress * musicService.getDuration() / 1000)));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { isSeekBarTracking = true; }
            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                isSeekBarTracking = false;
                if (isBound) {
                    int pos = (int)((long)sb.getProgress() * musicService.getDuration() / 1000);
                    webView.loadUrl("javascript:window.AndroidSeekTo&&window.AndroidSeekTo("+pos+");");
                    musicService.seekTo(pos);
                }
            }
        });
        View retryBtn = findViewById(R.id.btnRetry);
        if (retryBtn != null) {
            retryBtn.setOnClickListener(v -> {
                if (isNetworkAvailable()) { hideNoNetwork(); loadWebsite(); }
                else Toast.makeText(this, "网络不可用", Toast.LENGTH_SHORT).show();
            });
        }
    }

    public void updatePlayerUIFromBridge(String title, String artist, boolean isPlaying) {
        runOnUiThread(() -> {
            playerBar.setVisibility(View.VISIBLE);
            songTitle.setText(title.isEmpty() ? "正在播放" : title);
            songArtist.setText(artist.isEmpty() ? "未知艺术家" : artist);
            btnPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
            if (isBound) musicService.updateInfo(title, artist, isPlaying);
        });
    }

    private void updatePlayerUI() {
        if (!isBound) return;
        runOnUiThread(() -> {
            String title = musicService.getCurrentTitle();
            if (title != null && !title.isEmpty()) {
                playerBar.setVisibility(View.VISIBLE);
                songTitle.setText(title);
                songArtist.setText(musicService.getCurrentArtist() != null ? musicService.getCurrentArtist() : "");
                btnPlayPause.setImageResource(musicService.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
            }
        });
    }

    private void updateSeekBar(int position, int duration) {
        runOnUiThread(() -> {
            if (duration > 0) {
                seekBar.setProgress((int)((long)position * 1000 / duration));
                tvCurrentTime.setText(formatTime(position));
                tvTotalTime.setText(formatTime(duration));
            }
        });
    }

    private String formatTime(int ms) {
        int s = ms / 1000;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    private void loadWebsite() {
        noNetworkView.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        webView.loadUrl(MUSIC_URL);
    }

    private void showNoNetwork() {
        noNetworkView.setVisibility(View.VISIBLE);
        webView.setVisibility(View.GONE);
        swipeRefresh.setRefreshing(false);
    }

    private void hideNoNetwork() {
        noNetworkView.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    private void bindMusicService() {
        Intent intent = new Intent(this, MusicService.class);
        startService(intent);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void registerPlayerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(MusicService.ACTION_PLAYBACK_STATE_CHANGED);
        filter.addAction(MusicService.ACTION_SONG_CHANGED);
        filter.addAction(MusicService.ACTION_PROGRESS_UPDATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(playerReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(playerReceiver, filter);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(playerReceiver); } catch (Exception ignored) {}
        if (isBound) { unbindService(serviceConnection); isBound = false; }
        webView.destroy();
    }
}
