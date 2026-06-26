package com.hunabku.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
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
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private TextToSpeech tts;
    private List<String> oraciones;
    private int indiceActual = 0;

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

        iniciarTTS();
        webView.addJavascriptInterface(new TTSBridge(), "AndroidTTS");

        webView.loadUrl("https://inter2.pythonanywhere.com");
    }

    private void iniciarTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS && tts != null) {
                int resultado = tts.setLanguage(new Locale("es", "MX"));
                if (resultado == TextToSpeech.LANG_MISSING_DATA || resultado == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(new Locale("es"));
                }
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {
                        indiceActual = parseIndice(utteranceId);
                    }

                    @Override
                    public void onDone(String utteranceId) {
                        avisarSiEsLaUltima(utteranceId);
                    }

                    @Override
                    public void onError(String utteranceId) {
                        avisarSiEsLaUltima(utteranceId);
                    }
                });
            }
        });
    }

    private int parseIndice(String utteranceId) {
        try {
            return Integer.parseInt(utteranceId.replace("hk_", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private void avisarSiEsLaUltima(String utteranceId) {
        int idx = parseIndice(utteranceId);
        if (oraciones != null && idx == oraciones.size() - 1) {
            runOnUiThread(() -> webView.evaluateJavascript(
                    "if(window.hkAndroidTTSEnded)window.hkAndroidTTSEnded();", null));
        }
    }

    private void encolarDesde(int desde) {
        if (tts == null || oraciones == null || desde >= oraciones.size()) return;
        boolean primero = true;
        for (int i = desde; i < oraciones.size(); i++) {
            Bundle params = new Bundle();
            int modo = primero ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;
            tts.speak(oraciones.get(i), modo, params, "hk_" + i);
            primero = false;
        }
    }

    private class TTSBridge {
        @JavascriptInterface
        public void speak(String oracionesJson) {
            runOnUiThread(() -> {
                try {
                    JSONArray arr = new JSONArray(oracionesJson);
                    List<String> lista = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) lista.add(arr.getString(i));
                    oraciones = lista;
                    indiceActual = 0;
                    encolarDesde(0);
                } catch (Exception e) {
                    android.util.Log.e("HK_TTS", "Error parseando oraciones", e);
                }
            });
        }

        @JavascriptInterface
        public void pause() {
            runOnUiThread(() -> {
                if (tts != null) tts.stop();
            });
        }

        @JavascriptInterface
        public void resume() {
            runOnUiThread(() -> encolarDesde(indiceActual));
        }

        @JavascriptInterface
        public void stop() {
            runOnUiThread(() -> {
                if (tts != null) tts.stop();
                oraciones = null;
                indiceActual = 0;
            });
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
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
