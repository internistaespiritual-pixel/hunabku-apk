package com.hunabku.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.List;
import java.util.Locale;

public class HunabAudioService extends Service {

    public static final String CHANNEL_ID = "hunabku_audio_channel";
    public static final int NOTIF_ID = 9001;

    public static final String ACTION_PLAY_PAUSE = "com.hunabku.app.PLAY_PAUSE";
    public static final String ACTION_NEXT = "com.hunabku.app.NEXT";
    public static final String ACTION_PREV = "com.hunabku.app.PREV";
    public static final String ACTION_STOP = "com.hunabku.app.STOP";

    private TextToSpeech tts;
    private MediaSessionCompat mediaSession;
    private List<String> oraciones;
    private int indiceActual = 0;
    private boolean hablando = false;
    private String tituloActual = "Hunab Ku";

    public interface ControlCallback {
        void onSiguienteSolicitado();
        void onAnteriorSolicitado();
        void onTerminoLectura();
    }
    private static ControlCallback callback;
    public static void setControlCallback(ControlCallback cb) { callback = cb; }

    public class LocalBinder extends Binder {
        HunabAudioService getService() { return HunabAudioService.this; }
    }
    private final IBinder binder = new LocalBinder();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return binder; }

    @Override
    public void onCreate() {
        super.onCreate();
        crearCanalNotificacion();
        iniciarTTS();
        iniciarSesionMedia();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_PLAY_PAUSE:
                    if (hablando) pausar(); else reanudar();
                    break;
                case ACTION_NEXT:
                    if (callback != null) callback.onSiguienteSolicitado();
                    break;
                case ACTION_PREV:
                    if (callback != null) callback.onAnteriorSolicitado();
                    break;
                case ACTION_STOP:
                    detener();
                    stopForeground(true);
                    stopSelf();
                    break;
                default:
                    break;
            }
        }
        return START_STICKY;
    }

    private void crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel canal = new NotificationChannel(
                    CHANNEL_ID, "Reproducción Hunab Ku", NotificationManager.IMPORTANCE_LOW);
            canal.setDescription("Controles de audio de los cursos Hunab Ku");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(canal);
        }
    }

    private void iniciarSesionMedia() {
        mediaSession = new MediaSessionCompat(this, "HunabKuAudio");
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override public void onPlay() { reanudar(); }
            @Override public void onPause() { pausar(); }
            @Override public void onSkipToNext() { if (callback != null) callback.onSiguienteSolicitado(); }
            @Override public void onSkipToPrevious() { if (callback != null) callback.onAnteriorSolicitado(); }
            @Override public void onStop() { detener(); stopForeground(true); stopSelf(); }
        });
        mediaSession.setActive(true);
    }

    private void actualizarPlaybackState() {
        if (mediaSession == null) return;
        int estado = hablando ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        PlaybackStateCompat pb = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE
                        | PlaybackStateCompat.ACTION_PLAY
                        | PlaybackStateCompat.ACTION_PAUSE
                        | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                        | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                        | PlaybackStateCompat.ACTION_STOP)
                .setState(estado, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build();
        mediaSession.setPlaybackState(pb);
    }

    private PendingIntent accionPendiente(String accion) {
        Intent intent = new Intent(this, HunabAudioService.class).setAction(accion);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getService(this, accion.hashCode(), intent, flags);
    }

    private Notification construirNotificacion() {
        actualizarPlaybackState();

        Intent abrirApp = new Intent(this, MainActivity.class);
        int flagsContent = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flagsContent |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, abrirApp, flagsContent);

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(tituloActual)
                .setContentText(hablando ? "Reproduciendo" : "En pausa")
                .setContentIntent(contentIntent)
                .setOnlyAlertOnce(true)
                .setOngoing(hablando)
                .addAction(android.R.drawable.ic_media_previous, "Anterior", accionPendiente(ACTION_PREV))
                .addAction(hablando ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        hablando ? "Pausa" : "Reproducir", accionPendiente(ACTION_PLAY_PAUSE))
                .addAction(android.R.drawable.ic_media_next, "Siguiente", accionPendiente(ACTION_NEXT))
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2));
        return b.build();
    }

    private void mostrarONotificar() {
        Notification n = construirNotificacion();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private void iniciarTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS && tts != null) {
                int resultado = tts.setLanguage(new Locale("es", "MX"));
                if (resultado == TextToSpeech.LANG_MISSING_DATA || resultado == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(new Locale("es"));
                }
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String utteranceId) { indiceActual = parseIndice(utteranceId); }
                    @Override public void onDone(String utteranceId) { avisarSiEsLaUltima(utteranceId); }
                    @Override public void onError(String utteranceId) { avisarSiEsLaUltima(utteranceId); }
                });
            }
        });
    }

    private int parseIndice(String utteranceId) {
        try { return Integer.parseInt(utteranceId.replace("hk_", "")); } catch (Exception e) { return 0; }
    }

    private void avisarSiEsLaUltima(String utteranceId) {
        int idx = parseIndice(utteranceId);
        if (oraciones != null && idx == oraciones.size() - 1) {
            hablando = false;
            actualizarPlaybackState();
            if (callback != null) callback.onTerminoLectura();
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

    public void hablar(List<String> nuevasOraciones, String titulo) {
        oraciones = nuevasOraciones;
        indiceActual = 0;
        tituloActual = (titulo != null && !titulo.trim().isEmpty()) ? titulo : "Hunab Ku";
        hablando = true;
        encolarDesde(0);
        mostrarONotificar();
    }

    public void pausar() {
        if (tts != null) tts.stop();
        hablando = false;
        if (oraciones != null) mostrarONotificar();
    }

    public void reanudar() {
        if (oraciones == null) return;
        hablando = true;
        encolarDesde(indiceActual);
        mostrarONotificar();
    }

    public void detener() {
        if (tts != null) tts.stop();
        oraciones = null;
        indiceActual = 0;
        hablando = false;
    }

    public boolean estaHablando() { return hablando; }

    @Override
    public void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (mediaSession != null) mediaSession.release();
        super.onDestroy();
    }
}
