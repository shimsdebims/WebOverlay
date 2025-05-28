package com.example.weboverlay;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = Constants.TAG_MAIN;
    
    private Button btnStartOverlay;
    private Button btnStopOverlay;
    private Button btnStartCamera;
    private Button btnStopCamera;
    private Button btnSettings;
    private TextView txtStatus;
    
    private boolean isOverlayServiceRunning = false;
    private boolean isCameraServiceRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initializeUI();
        checkPermissions();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // Check if overlay is actually running
        isOverlayServiceRunning = getSharedPreferences("overlay_prefs", MODE_PRIVATE)
            .getBoolean("is_overlay_active", false);
        
        updateUIState();
    }
    
    private void initializeUI() {
        btnStartOverlay = findViewById(R.id.btn_start_overlay);
        btnStopOverlay = findViewById(R.id.btn_stop_overlay);
        btnStartCamera = findViewById(R.id.btn_start_camera);
        btnStopCamera = findViewById(R.id.btn_stop_camera);
        btnSettings = findViewById(R.id.btn_settings);
        txtStatus = findViewById(R.id.txt_status);
        
        btnStartOverlay.setOnClickListener(v -> startOverlayService());
        btnStopOverlay.setOnClickListener(v -> stopOverlayService());
        btnStartCamera.setOnClickListener(v -> startCameraService());
        btnStopCamera.setOnClickListener(v -> stopCameraService());
        btnSettings.setOnClickListener(v -> openSettings());
        
        updateUIState();
    }
    
    private void checkPermissions() {
        if (!checkCameraPermission()) {
            requestCameraPermission();
        }
        if (!checkOverlayPermission()) {
            requestOverlayPermission();
        }
    }
    
    private void startOverlayService() {
        if (!checkOverlayPermission()) {
            requestOverlayPermission();
            return;
        }
        
        // Start camera service first
        if (!isCameraServiceRunning) {
            startCameraService();
            new Handler().postDelayed(this::proceedWithOverlayStart, 1000);
        } else {
            proceedWithOverlayStart();
        }
    }
    
    private void proceedWithOverlayStart() {
        Intent intent = new Intent(this, OverlayService.class);
        startForegroundService(intent);
        isOverlayServiceRunning = true;
        
        // Save state
        getSharedPreferences("overlay_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean("is_overlay_active", true)
            .apply();
            
        updateUIState();
    }
    
    private void stopOverlayService() {
        Intent intent = new Intent(this, OverlayService.class);
        stopService(intent);
        isOverlayServiceRunning = false;
        
        // Save state
        getSharedPreferences("overlay_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean("is_overlay_active", false)
            .apply();
            
        updateUIState();
    }
    
    private void startCameraService() {
        if (!checkCameraPermission()) {
            requestCameraPermission();
            return;
        }
        
        Intent intent = new Intent(this, CameraService.class);
        startForegroundService(intent);
        isCameraServiceRunning = true;
        updateUIState();
    }
    
    private void stopCameraService() {
        Intent intent = new Intent(this, CameraService.class);
        stopService(intent);
        isCameraServiceRunning = false;
        updateUIState();
    }
    
    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }
    
    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            == PackageManager.PERMISSION_GRANTED;
    }
    
    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, 
            new String[]{Manifest.permission.CAMERA}, 
            Constants.REQUEST_CAMERA_PERMISSION);
    }
    
    private boolean checkOverlayPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || 
            Settings.canDrawOverlays(this);
    }
    
    private void requestOverlayPermission() {
        Intent intent = new Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + getPackageName())
        );
        startActivityForResult(intent, Constants.REQUEST_OVERLAY_PERMISSION);
    }
    
    private void updateUIState() {
        btnStartOverlay.setEnabled(!isOverlayServiceRunning);
        btnStopOverlay.setEnabled(isOverlayServiceRunning);
        btnStartCamera.setEnabled(!isCameraServiceRunning);
        btnStopCamera.setEnabled(isCameraServiceRunning);
        
        String status = "Status: ";
        if (isCameraServiceRunning) {
            status += "Camera Running";
        } else {
            status += "Camera Stopped";
        }
        status += " | ";
        if (isOverlayServiceRunning) {
            status += "Overlay Running";
        } else {
            status += "Overlay Stopped";
        }
        txtStatus.setText(status);
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == Constants.REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCameraService();
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Constants.REQUEST_OVERLAY_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    startOverlayService();
                } else {
                    Toast.makeText(this, "Overlay permission required", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
}
