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
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements HunabAudioService.ControlCallback {

    private WebView webView;
    private HunabAudioService audioService;
    private boolean serviceVinculado = false;

    private final ServiceConnection conexion = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            HunabAudioService.LocalBinder lb = (HunabAudioService.LocalBinder) binder;
            audioService = lb.getService();
            serviceVinculado = true;
            HunabAudioService.setControlCallback(MainActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceVinculado = false;
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

        Intent intentServicio = new Intent(this, HunabAudioService.class);
        startService(intentServicio);
        bindService(intentServicio, conexion, Context.BIND_AUTO_CREATE);

        webView.loadUrl("https://inter2.pythonanywhere.com");
    }

    private void evaluarJS(String js) {
        runOnUiThread(() -> webView.evaluateJavascript(js, null));
    }

    @Override
    public void onSiguienteSolicitado() {
        evaluarJS("window.hkAudioState.continuar=true; if(typeof window.hkModuloSiguiente==='function')window.hkModuloSiguiente();");
    }

    @Override
    public void onAnteriorSolicitado() {
        evaluarJS("window.hkAudioState.continuar=true; if(typeof window.hkModuloAnterior==='function')window.hkModuloAnterior();");
    }

    @Override
    public void onTerminoLectura() {
        evaluarJS("if(window.hkAndroidTTSEnded)window.hkAndroidTTSEnded();");
    }

    @Override
    public void onEstadoCambiado(boolean hablando) {
        evaluarJS("window.hkAudioState.hablando=" + hablando + "; window.hkAudioState.enPausaAndroid=" + (!hablando) + "; if(typeof window.hkAudioRefrescarVisual==='function')window.hkAudioRefrescarVisual();");
    }

    private class TTSBridge {
        @JavascriptInterface
        public void speak(String payloadJson) {
            runOnUiThread(() -> {
                try {
                    JSONObject obj = new JSONObject(payloadJson);
                    String titulo = obj.optString("titulo", "Hunab Ku");
                    JSONArray arr = obj.getJSONArray("oraciones");
                    List<String> lista = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) lista.add(arr.getString(i));
                    if (audioService != null) audioService.hablar(lista, titulo);
                } catch (Exception e) {
                    android.util.Log.e("HK_TTS", "Error parseando oraciones", e);
                }
            });
        }

        @JavascriptInterface
        public void pause() {
            runOnUiThread(() -> { if (audioService != null) audioService.pausar(); });
        }

        @JavascriptInterface
        public void resume() {
            runOnUiThread(() -> { if (audioService != null) audioService.reanudar(); });
        }

        @JavascriptInterface
        public void stop() {
            runOnUiThread(() -> { if (audioService != null) audioService.detener(); });
        }
    }

    @Override
    protected void onDestroy() {
        if (serviceVinculado) {
            unbindService(conexion);
            serviceVinculado = false;
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
