package com.example.weboverlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.FrameLayout;
import androidx.preference.PreferenceManager;
import androidx.core.app.NotificationCompat;

public class OverlayService extends Service {
    private static final String TAG = Constants.TAG_OVERLAY;
    public static boolean isRunning = false;
    
    private WindowManager windowManager;
    private FrameLayout overlayView;
    private WebView webView;
    private XiboWebClient xiboWebClient;
    private SharedPreferences prefs;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate");
        isRunning = true;
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service onStartCommand");
        createNotificationChannel();
        startForeground(Constants.NOTIFICATION_ID_OVERLAY, buildNotification());
        
        if (windowManager == null) {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            initOverlay();
        }
        
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service onDestroy");
        isRunning = false;
        if (overlayView != null && overlayView.isAttachedToWindow()) {
            windowManager.removeView(overlayView);
            overlayView = null;
            webView = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void initOverlay() {
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        overlayView = (FrameLayout) inflater.inflate(R.layout.overlay_layout, null);
        webView = overlayView.findViewById(R.id.primary_webview);
        
        int width = prefs.getInt(Constants.Prefs.OVERLAY_WIDTH, 200);
        int height = prefs.getInt(Constants.Prefs.OVERLAY_HEIGHT, 150);
        
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            width,
            height,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        );
        
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = 16;
        params.y = 16;
        
        try {
            windowManager.addView(overlayView, params);
            xiboWebClient = new XiboWebClient(this, webView);
            xiboWebClient.loadContent();
            Log.d(TAG, "Overlay initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing overlay", e);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                Constants.CHANNEL_ID_OVERLAY,
                "Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        );
        
        return new NotificationCompat.Builder(this, Constants.CHANNEL_ID_OVERLAY)
            .setContentTitle("Xibo Overlay")
            .setContentText("Displaying content")
            .setSmallIcon(R.drawable.ic_overlay_notification)
            .setContentIntent(pendingIntent)
            .build();
    }
}
