package com.hunabku.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class HunabKuMessagingService extends FirebaseMessagingService {

    private static final String CHANNEL_ID = "hunabku_channel";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        String title = "Hunab Ku";
        String body = "";
        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle() != null
                    ? remoteMessage.getNotification().getTitle() : title;
            body = remoteMessage.getNotification().getBody() != null
                    ? remoteMessage.getNotification().getBody() : body;
        } else if (remoteMessage.getData().size() > 0) {
            title = remoteMessage.getData().containsKey("title")
                    ? remoteMessage.getData().get("title") : title;
            body = remoteMessage.getData().containsKey("body")
                    ? remoteMessage.getData().get("body") : body;
        }
        mostrarNotificacion(title, body);
    }

    @Override
    public void onNewToken(String token) {
        android.util.Log.d("FCM_TOKEN", "Nuevo token: " + token);
    }

    private void mostrarNotificacion(String title, String body) {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Hunab Ku", NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(channel);
        }
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);
        manager.notify((int) System.currentTimeMillis(), builder.build());
    }
}
