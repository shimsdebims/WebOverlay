package com.example.weboverlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class OverlayService extends Service {
    private static final String TAG = "OverlayService";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "overlay_service_channel";

    private WindowManager windowManager;
    private FrameLayout overlayView;
    private WebView webView;
    private XiboWebClient xiboWebClient;
    private boolean isOverlayVisible = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate");
    
        // Initialize WebView and XiboWebClient
        webView = new WebView(this);
        String cmsUrl = "http://192.168.11.112:8080"; // Replace with your CMS URL
        String displayKey = "ac:db:da:64:12:89"; // Replace with your Display Key
        xiboWebClient = new XiboWebClient(this, webView, cmsUrl, displayKey);
    
        // Load Xibo content
        xiboWebClient.loadContent();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service onStartCommand");
        
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        
        initOverlay();
        
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service onDestroy");
        hideOverlay();
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Used to keep the overlay service running");
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
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
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Overlay Active")
            .setContentText("WebView overlay is currently displayed")
            .setSmallIcon(R.drawable.ic_overlay_notification)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    private void initOverlay() {
        try {
            LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
            overlayView = (FrameLayout) inflater.inflate(R.layout.overlay_layout, null);
            
            webView = overlayView.findViewById(R.id.overlay_webview);
            if (webView != null) {
                xiboWebClient = new XiboWebClient(
                    this,
                    webView,
                    "http://192.168.11.112:8080",
                    new XiboWebClient.XiboClientListener() {
                        @Override
                        public void onLayoutLoaded(String layoutId) {
                            Log.d(TAG, "Layout loaded: " + layoutId);
                            updateStatusText("Layout loaded successfully");
                        }

                        @Override
                        public void onLayoutLoadFailed(String layoutId, String reason) {
                            Log.e(TAG, "Layout load failed: " + reason);
                            updateStatusText("Failed to load layout: " + reason);
                        }
                    }
                );
                
                // Load layout
                xiboWebClient.loadLayout("1");  // Replace with your layout ID
            }
            
            showOverlay();
            
        } catch (Exception e) {
            Log.e(TAG, "Error initializing overlay", e);
            updateStatusText("Error: " + e.getMessage());
        }
    }

    private void showOverlay() {
        if (overlayView == null || isOverlayVisible) return;
        
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );
        
        params.gravity = Gravity.TOP | Gravity.START;
        
        try {
            windowManager.addView(overlayView, params);
            isOverlayVisible = true;
            Log.d(TAG, "Overlay shown successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error showing overlay", e);
        }
    }

    private void hideOverlay() {
        if (overlayView != null && isOverlayVisible) {
            try {
                windowManager.removeView(overlayView);
                isOverlayVisible = false;
                Log.d(TAG, "Overlay hidden successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error hiding overlay", e);
            }
        }
    }

    private void updateStatusText(final String text) {
        new Handler(Looper.getMainLooper()).post(() -> {
            TextView statusView = overlayView.findViewById(R.id.overlay_status);
            if (statusView != null) {
                statusView.setText(text);
            }
        });
    }
}
