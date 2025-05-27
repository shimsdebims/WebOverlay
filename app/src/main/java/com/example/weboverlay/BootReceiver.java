package com.example.weboverlay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import androidx.preference.PreferenceManager;

/**
 * Broadcast receiver for handling the BOOT_COMPLETED intent.
 * This allows the application to automatically start its services when the device boots up,
 * based on user preferences.
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";
    private static final String WAKE_LOCK_TAG = "com.example.weboverlay:BootWakeLock";
    private static final int WAKE_LOCK_TIMEOUT = 30000; // 30 seconds

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            Log.i(TAG, "Boot completed intent received");
            
            // Acquire a wake lock to make sure we can complete our work
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wakeLock = null;
            
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);
                wakeLock.acquire(WAKE_LOCK_TIMEOUT);
                Log.d(TAG, "Wake lock acquired");
            }
            
            try {
                // Get preferences
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
                boolean startOnBoot = preferences.getBoolean("start_on_boot", true);
                
                // Only proceed if auto-start is enabled
                if (startOnBoot) {
                    boolean startOverlay = preferences.getBoolean("overlay_auto_start", true);
                    boolean startCamera = preferences.getBoolean("hdmi_auto_start", true);
                    
                    // Launch MainActivity first to handle permissions
                    Intent mainActivityIntent = new Intent(context, MainActivity.class);
                    mainActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(mainActivityIntent);
                    
                    // Start overlay service if needed
                    if (startOverlay) {
                        startOverlayService(context);
                    }
                    
                    // Start camera service if needed
                    if (startCamera) {
                        startCameraService(context);
                    }
                    
                    Log.i(TAG, "Auto-start completed. Overlay: " + startOverlay + ", Camera: " + startCamera);
                } else {
                    Log.i(TAG, "Auto-start is disabled in preferences");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error during auto-start", e);
            } finally {
                // Release wake lock
                if (wakeLock != null && wakeLock.isHeld()) {
                    wakeLock.release();
                    Log.d(TAG, "Wake lock released");
                }
            }
        }
    }
    
    /**
     * Start the overlay service
     */
    private void startOverlayService(Context context) {
        try {
            Intent overlayServiceIntent = new Intent(context, OverlayService.class);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(overlayServiceIntent);
            } else {
                context.startService(overlayServiceIntent);
            }
            
            Log.i(TAG, "Overlay service started on boot");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start overlay service", e);
        }
    }
    
    /**
     * Start the camera service
     */
    private void startCameraService(Context context) {
        try {
            Intent cameraServiceIntent = new Intent(context, CameraService.class);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(cameraServiceIntent);
            } else {
                context.startService(cameraServiceIntent);
            }
            
            Log.i(TAG, "Camera service started on boot");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start camera service", e);
        }
    }
}

