package com.example.weboverlay;

// Ensure XiboWebClient is imported
import com.example.weboverlay.XiboWebClient;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

/**
 * Service responsible for displaying and managing a WebView overlay
 * on top of other applications, particularly designed to work with
 * camera apps displaying HDMI input.
 */
public class OverlayService extends Service {
    private static final String TAG = "OverlayService";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "overlay_service_channel";
    private static final long ERROR_RECOVERY_DELAY_MS = 5000; // 5 seconds
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private WindowManager windowManager;
    private FrameLayout overlayView;
    private WebView webView;
    private XiboWebClient xiboWebClient;
    private PowerManager.WakeLock wakeLock;
    private Handler handler;
    private int retryCount = 0;
    private boolean isOverlayVisible = false;

    // BroadcastReceiver for screen state changes
    private BroadcastReceiver screenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                handleScreenOff();
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                handleScreenOn();
            }
        }
    };

    // BroadcastReceiver for configuration changes
    private BroadcastReceiver configChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_CONFIGURATION_CHANGED.equals(intent.getAction())) {
                handleConfigurationChange();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate");
        
        handler = new Handler();
        
        // Initialize window manager
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        
        // Initialize power management
        setupWakeLock();
        
        // Register receivers for screen and config changes
        registerReceivers();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service onStartCommand");
        
        // Create and show notification to make this a foreground service
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        
        // Initialize and show overlay
        if (!isOverlayVisible) {
            initOverlay();
            showOverlay();
        }
        
        // If this service is killed, restart it
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not supporting binding
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service onDestroy");
        
        // Clean up
        hideOverlay();
        unregisterReceivers();
        releaseWakeLock();
        
        // Handle cleanup of any resources
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        
        super.onDestroy();
    }

    /**
     * Create notification channel for Android O and above
     */
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

    /**
     * Build the foreground service notification
     */
    private Notification buildNotification() {
        // Create an intent to launch the app's main activity when notification is tapped
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        // Build the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Overlay Active")
                .setContentText("WebView overlay is currently displayed")
                .setSmallIcon(R.drawable.ic_overlay_notification)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        return builder.build();
    }

    /**
     * Initialize the overlay with WebView
     */
private void initOverlay() {
    try {
        // Create overlay layout
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        overlayView = (FrameLayout) inflater.inflate(R.layout.overlay_layout, null);
        
        // Check if Xibo is enabled
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean xiboEnabled = prefs.getBoolean("xibo_enabled", true);
        
        if (xiboEnabled) {
            // Find the WebView in the inflated layout
            webView = overlayView.findViewById(R.id.overlay_webview);
            
            // Configure WebView only if Xibo is enabled
            setupWebView();
            
            // Load Layout ID 3 specifically for testing
            if (xiboWebClient != null) {
                Log.d(TAG, "Loading test layout ID 3");
                xiboWebClient.loadLayout("3");
            }
        } else {
            // Simple overlay mode without Xibo
            Log.d(TAG, "Running in simple overlay mode (no Xibo)");
            
            // Make the WebView invisible to reduce resource usage
            WebView simpleWebView = overlayView.findViewById(R.id.overlay_webview);
            if (simpleWebView != null) {
                simpleWebView.setVisibility(View.GONE);
            }
            
            // Show a status indicator
            TextView statusView = overlayView.findViewById(R.id.overlay_status);
            if (statusView != null) {
                statusView.setText("Basic Overlay Mode");
                statusView.setVisibility(View.VISIBLE);
            }
        }
        
        // Reset retry counter on successful initialization
        retryCount = 0;
    } catch (Exception e) {
        Log.e(TAG, "Error initializing overlay: " + e.getMessage(), e);
        scheduleErrorRecovery();
    }
}

    /**
     * Configure the WebView with appropriate settings
     */
    private void setupWebView() {
        if (webView == null) return;
        
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        
        // Set transparent background
        webView.setBackgroundColor(Color.TRANSPARENT);
        
        // Get Xibo settings from SharedPreferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String cmsUrl = prefs.getString(Constants.Prefs.XIBO_CMS_URL, "");
        String clientId = prefs.getString(Constants.Prefs.XIBO_CLIENT_ID, "");
        String clientSecret = prefs.getString(Constants.Prefs.XIBO_CLIENT_SECRET, "");
        String username = prefs.getString(Constants.Prefs.XIBO_USERNAME, "");
        String password = prefs.getString(Constants.Prefs.XIBO_PASSWORD, "");
        
        // Initialize Xibo Web Client with all required parameters
        xiboWebClient = new XiboWebClient(
            this,               // Context
            webView,            // WebView
            cmsUrl,             // CMS URL
            clientId,           // Client ID
            clientSecret,       // Client Secret
            username,           // Username
            password,           // Password
            new XiboWebClient.XiboClientListener() {
                @Override
                public void onAuthSuccess() {
                    Log.d(TAG, "Xibo authentication successful");
                }

                @Override
                public void onAuthFailure(String reason) {
                    Log.e(TAG, "Xibo authentication failed: " + reason);
                    scheduleErrorRecovery();
                }

                @Override
                public void onLayoutLoaded(String layoutId) {
                    Log.d(TAG, "Xibo layout loaded: " + layoutId);
                }

                @Override
                public void onLayoutLoadFailed(String layoutId, String reason) {
                    Log.e(TAG, "Failed to load layout " + layoutId + ": " + reason);
                    scheduleErrorRecovery();
                }

                @Override
                public void onConnectionStateChanged(boolean connected) {
                    Log.d(TAG, "Xibo connection state changed: " + connected);
                    if (!connected) {
                        scheduleErrorRecovery();
                    }
                }
            }
        );
        
        webView.setWebViewClient(xiboWebClient);
        
        // Load content from Xibo CMS
        loadContent();
    }

    /**
     * Load content into the WebView
     */
    private void loadContent() {
        if (webView == null) return;
        
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            String url = prefs.getString("xibo_url", "");
            
            if (!url.isEmpty()) {
                webView.loadUrl(url);
            } else {
                // Fallback to local content or default URL
                webView.loadUrl("file:///android_asset/default_overlay.html");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading content: " + e.getMessage(), e);
        }
    }

    /**
     * Show the overlay window
     */
    public void showOverlay() {
        if (overlayView == null || isOverlayVisible) return;
        
        try {
            // Create window layout parameters
            WindowManager.LayoutParams params = createWindowLayoutParams();
            
            // Add the view to window manager
            windowManager.addView(overlayView, params);
            isOverlayVisible = true;
            
            // Acquire wake lock to keep screen on
            acquireWakeLock();
            
            Log.d(TAG, "Overlay shown successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error showing overlay: " + e.getMessage(), e);
            scheduleErrorRecovery();
        }
    }

    /**
     * Create window layout parameters for the overlay
     */
    private WindowManager.LayoutParams createWindowLayoutParams() {
        // Determine correct window type based on Android version
        int overlayType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            overlayType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            overlayType = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        } else {
            overlayType = WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
        }
        
        // Check if we want to use a simplified overlay mode
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean xiboEnabled = prefs.getBoolean("xibo_enabled", true);
        boolean useFullscreenOverlay = prefs.getBoolean("use_fullscreen_overlay", true);
        
        // Default to a smaller overlay size if not using fullscreen mode
        int width = WindowManager.LayoutParams.MATCH_PARENT;
        int height = WindowManager.LayoutParams.MATCH_PARENT;
        
        if (!useFullscreenOverlay && !xiboEnabled) {
            // Use smaller overlay size when in basic mode
            DisplayMetrics metrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getMetrics(metrics);
            width = metrics.widthPixels / 3;  // 1/3 of screen width
            height = metrics.heightPixels / 4; // 1/4 of screen height
        }
        
        // Set appropriate flags for overlay window
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |  // Allow touches to pass through
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL; // Allow touch events outside
        
        // Only use hardware acceleration if Xibo is enabled (reduces resource usage in basic mode)
        if (xiboEnabled) {
            flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        }
                    
        // Create layout parameters
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width,
                height,
                overlayType,
                flags,
                PixelFormat.TRANSLUCENT // Essential for transparency
        );
        
        // Set gravity - position in corner if not fullscreen
        if (useFullscreenOverlay) {
            params.gravity = Gravity.TOP | Gravity.START;
        } else {
            params.gravity = Gravity.TOP | Gravity.END; // Top-right corner
        }
        
        // Apply custom position and size from preferences if available
        int offsetX = prefs.getInt("overlay_offset_x", 0);
        int offsetY = prefs.getInt("overlay_offset_y", 0);
        
        params.x = offsetX;
        params.y = offsetY;
        
        // Set reduced alpha if in basic mode
        if (!xiboEnabled) {
            params.alpha = 0.7f; // Slightly transparent
        } else {
            params.alpha = 1.0f; // Fully opaque (transparency controlled by WebView)
        }
        
        Log.d(TAG, "Created window layout params: type=" + overlayType + 
              ", width=" + width + ", height=" + height);
        
        return params;
    }

    /**
     * Hide the overlay window
     */
    public void hideOverlay() {
        if (overlayView != null && isOverlayVisible) {
            try {
                windowManager.removeView(overlayView);
                isOverlayVisible = false;
                releaseWakeLock();
                Log.d(TAG, "Overlay hidden successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error hiding overlay: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Update the position of the overlay
     */
    public void updateOverlayPosition(int x, int y) {
        if (overlayView != null && isOverlayVisible) {
            try {
                WindowManager.LayoutParams params = (WindowManager.LayoutParams) overlayView.getLayoutParams();
                params.x = x;
                params.y = y;
                windowManager.updateViewLayout(overlayView, params);
                
                // Save position to preferences
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putInt("overlay_offset_x", x);
                editor.putInt("overlay_offset_y", y);
                editor.apply();
                
                Log.d(TAG, "Overlay position updated to x=" + x + ", y=" + y);
            } catch (Exception e) {
                Log.e(TAG, "Error updating overlay position: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Setup wake lock to keep screen on
     */
    private void setupWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "WebOverlay:WakeLock"
            );
            wakeLock.setReferenceCounted(false);
        }
    }

    /**
     * Acquire wake lock if needed
     */
    private void acquireWakeLock() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean keepScreenOn = prefs.getBoolean("keep_screen_on", true);
        
        if (keepScreenOn && wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(30*60*1000L); // 30 minutes max
            Log.d(TAG, "Wake lock acquired");
        }
    }

    /**
     * Release wake lock if held
     */
    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            Log.d(TAG, "Wake lock released");
        }
    }

    /**
     * Register broadcast receivers for screen and configuration changes
     */
    private void registerReceivers() {
        // Register for screen on/off events
        IntentFilter screenFilter = new IntentFilter();
        screenFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenFilter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenStateReceiver, screenFilter);
        
        // Register for configuration changes
        IntentFilter configFilter = new IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED);
        registerReceiver(configChangeReceiver, configFilter);
    }

    /**
     * Unregister broadcast receivers
     */
    private void unregisterReceivers() {
        try {
            unregisterReceiver(screenStateReceiver);
            unregisterReceiver(configChangeReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering receivers: " + e.getMessage(), e);
        }
    }

    /**
     * Handle screen off event
     */
    private void handleScreenOff() {
        Log.d(TAG, "Screen turned off");
        
        // Optionally hide overlay when screen is off to save resources
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean hideOnScreenOff = prefs.getBoolean("hide_on_screen_off", false);
        
        if (hideOnScreenOff) {
            hideOverlay();
        }
        
        // Release wake lock when screen is off
        releaseWakeLock();
    }

    /**
     * Handle screen on event
     */
    private void handleScreenOn() {
        Log.d(TAG, "Screen turned on");
        
        // Restore overlay if it was hidden due to screen off
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean hideOnScreenOff = prefs.getBoolean("hide_on_screen_off", false);
        
        if (hideOnScreenOff && !isOverlayVisible) {
            // Small delay to ensure system is ready
            handler.postDelayed(this::showOverlay, 1000);
        }
    }

    /**
     * Handle configuration changes (e.g., rotation)
     */
    private void handleConfigurationChange() {
        Log.d(TAG, "Configuration changed");
        
        if (isOverlayVisible) {
            // Recreate the overlay to adjust to the new configuration
            hideOverlay();
            initOverlay();
            showOverlay();
        }
    }

    /**
     * Schedule error recovery attempt
     */
    private void scheduleErrorRecovery() {
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            retryCount++;
            Log.d(TAG, "Scheduling error recovery attempt " + retryCount + " in " + ERROR_RECOVERY_DELAY_MS + "ms");
            
            handler.postDelayed(() -> {
                Log.d(TAG, "Executing error recovery attempt " + retryCount);
                
                try {
                    // Check if the service is still active
                    if (handler == null || windowManager == null) {
                        Log.e(TAG, "Service already destroyed, cannot recover");
                        return;
                    }
                    
                    // Clean up any existing views
                    hideOverlay();
                    
                    // Check if we should fallback to basic mode
                    if (retryCount >= 2) {
                        // On second retry, switch to basic mode without Xibo
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putBoolean("xibo_enabled", false);
                        editor.putBoolean("use_fullscreen_overlay", false);
                        editor.apply();
                        
                        Log.w(TAG, "Falling back to basic overlay mode due to errors");
                    }
                    
                    // Try to initialize and show again
                    initOverlay();
                    showOverlay();
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error during recovery attempt: " + e.getMessage(), e);
                    
                    // If we get an exception during recovery, try one more approach
                    if (retryCount == MAX_RETRY_ATTEMPTS) {
                        Log.w(TAG, "Final recovery attempt with minimal overlay");
                        try {
                            // Create a very simple overlay as last resort
                            createMinimalOverlay();
                        } catch (Exception e2) {
                            Log.e(TAG, "Even minimal overlay failed, stopping service", e2);
                            stopSelf();
                        }
                    } else {
                        // Schedule another attempt
                        handler.postDelayed(this::scheduleErrorRecovery, ERROR_RECOVERY_DELAY_MS);
                    }
                }
            }, ERROR_RECOVERY_DELAY_MS);
        } else {
            Log.e(TAG, "Maximum retry attempts reached. Stopping service.");
            stopSelf();
        }
    }
    
    /**
     * Create a minimal overlay as last resort when all else fails
     */
    private void createMinimalOverlay() {
        // Clean up any existing views
        hideOverlay();
        
        // Create a very simple overlay (just a colored box)
        TextView textView = new TextView(this);
        textView.setText("Overlay Active");
        textView.setTextColor(Color.WHITE);
        textView.setBackgroundColor(Color.argb(128, 0, 0, 0));
        textView.setPadding(20, 10, 20, 10);
        
        // Simple parameters for a small overlay
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = 20;
        params.y = 20;
        
        try {
            windowManager.addView(textView, params);
            isOverlayVisible = true;
            Log.d(TAG, "Minimal overlay displayed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to display minimal overlay", e);
            stopSelf();
        }
    }

    /**
     * Reload content in the WebView
     */
    public void reloadContent() {
        if (webView != null) {
            webView.reload();
        }
    }

    /**
     * Change the URL being displayed
     */
    public void changeUrl(String url) {
        if (webView != null && !url.isEmpty()) {
            webView.loadUrl(url);
            
            // Save to preferences
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("xibo_url", url);
            editor.apply();
        }
    }

    /**
     * Restart the WebView to recover from errors
     */
    public void restartWebView() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            
            // Re-initialize the overlay
            initOverlay();
            
            if (isOverlayVisible) {
                hideOverlay();
                showOverlay();
            }
        }
    }
}
