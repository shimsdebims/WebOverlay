package com.example.weboverlay;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String TAG = Constants.TAG_MAIN;
    private static final int OVERLAY_PERMISSION_REQUEST_CODE = 1;
    
    private Button startButton;
    private Button stopButton;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Set dark theme
        setTheme(android.R.style.Theme_DeviceDefault);
        
        setContentView(R.layout.activity_main);
        
        // Initialize controls
        startButton = findViewById(R.id.start_overlay);
        stopButton = findViewById(R.id.stop_overlay);
        
        startButton.setOnClickListener(v -> checkPermissionAndStartOverlay());
        stopButton.setOnClickListener(v -> stopOverlay());
        
        updateButtonStates();
    }
    
    private void checkPermissionAndStartOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())
            );
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE);
        } else {
            startOverlay();
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Settings.canDrawOverlays(this)) {
                startOverlay();
            } else {
                Toast.makeText(this, "Overlay permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void startOverlay() {
        Intent intent = new Intent(this, OverlayService.class);
        startForegroundService(intent);
        updateButtonStates();
    }
    
    private void stopOverlay() {
        stopService(new Intent(this, OverlayService.class));
        updateButtonStates();
    }
    
    private void updateButtonStates() {
        boolean isRunning = OverlayService.isRunning;
        startButton.setEnabled(!isRunning);
        stopButton.setEnabled(isRunning);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        updateButtonStates();
    }
}
