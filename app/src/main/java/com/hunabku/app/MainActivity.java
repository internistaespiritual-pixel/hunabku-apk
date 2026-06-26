package com.hunabku.app;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private HunabAudioService audioService;
    private boolean audioServiceBound = false;

    private final ServiceConnection audioConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            HunabAudioService.LocalBinder b = (HunabAudioService.LocalBinder) binder;
            audioService = b.getService();
            audioServiceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            audioServiceBound = false;
            audioService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        }

        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        android.util.Log.d("FCM_TOKEN", "Token: " + task.getResult());
                    }
                });

        webView = findViewById(R.id.webview);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());

        webView.addJavascriptInterface(new TTSBridge(), "AndroidTTS");

        HunabAudioService.setControlCallback(new HunabAudioService.ControlCallback() {
            @Override
            public void onSiguienteSolicitado() {
                ejecutarEnWeb("if(window.hkModuloSiguiente)window.hkModuloSiguiente();");
            }

            @Override
            public void onAnteriorSolicitado() {
                ejecutarEnWeb("if(window.hkModuloAnterior)window.hkModuloAnterior();");
            }

            @Override
            public void onTerminoLectura() {
                ejecutarEnWeb("if(window.hkAndroidTTSEnded)window.hkAndroidTTSEnded();");
            }
        });

        Intent servicioAudio = new Intent(this, HunabAudioService.class);
        startService(servicioAudio);
        bindService(servicioAudio, audioConnection, Context.BIND_AUTO_CREATE);

        webView.loadUrl("https://inter2.pythonanywhere.com");
    }

    private void ejecutarEnWeb(String js) {
        if (isFinishing() || isDestroyed()) return;
        runOnUiThread(() -> {
            if (webView != null) webView.evaluateJavascript(js, null);
        });
    }

    private class TTSBridge {
        @JavascriptInterface
        public void speak(String oracionesJson, String titulo) {
            runOnUiThread(() -> {
                try {
                    JSONArray arr = new JSONArray(oracionesJson);
                    List<String> lista = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) lista.add(arr.getString(i));
                    if (audioServiceBound && audioService != null) {
                        audioService.hablar(lista, titulo);
                    }
                } catch (Exception e) {
                    android.util.Log.e("HK_TTS", "Error parseando oraciones", e);
                }
            });
        }

        @JavascriptInterface
        public void pause() {
            runOnUiThread(() -> {
                if (audioServiceBound && audioService != null) audioService.pausar();
            });
        }

        @JavascriptInterface
        public void resume() {
            runOnUiThread(() -> {
                if (audioServiceBound && audioService != null) audioService.reanudar();
            });
        }

        @JavascriptInterface
        public void stop() {
            runOnUiThread(() -> {
                if (audioServiceBound && audioService != null) audioService.detener();
            });
        }
    }

    @Override
    protected void onDestroy() {
        if (audioServiceBound) {
            unbindService(audioConnection);
            audioServiceBound = false;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
