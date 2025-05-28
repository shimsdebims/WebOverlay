package com.example.weboverlay;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.view.WindowManager;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


public class MainActivity extends AppCompatActivity implements CameraService.CameraServiceListener {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private static final int REQUEST_OVERLAY_PERMISSION = 101;
    private static final int REQUEST_SETTINGS = 1001;
    
    // Service connections
    private CameraService cameraService;
    private CameraService.SurfaceProvider surfaceProvider;
    
    // UI Components
    private Button btnStartOverlay;
    private Button btnStopOverlay;
    private Button btnStartCamera;
    private Button btnStopCamera;
    private Button btnSettings;
    private Button btnEmergencyStop;
    private TextView txtStatus;
    
    // Service states
    private boolean isOverlayServiceRunning = false;
    private boolean isCameraServiceRunning = false;
    private boolean isXiboAuthenticated = false;
    
    // Xibo client
    private XiboWebClient xiboWebClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        Log.d(TAG, "MainActivity created");
        
        // Initialize UI components
        initializeUI();
        
        // Check permissions on startup
        checkInitialPermissions();
        
        // Initialize Xibo client if credentials are available
        initializeXiboClient();
        
        // Update UI state
        updateUIState();
    }
    
    private void initializeUI() {
        // Find UI components
        btnStartOverlay = findViewById(R.id.btn_start_overlay);
        btnStopOverlay = findViewById(R.id.btn_stop_overlay);
        btnStartCamera = findViewById(R.id.btn_start_camera);
        btnStopCamera = findViewById(R.id.btn_stop_camera);
        btnSettings = findViewById(R.id.btn_settings);
        txtStatus = findViewById(R.id.txt_status);
        btnEmergencyStop = findViewById(R.id.btn_emergency_stop);
        
        // Set click listeners
        btnStartOverlay.setOnClickListener(v -> startOverlayService());
        btnStopOverlay.setOnClickListener(v -> stopOverlayService());
        btnStartCamera.setOnClickListener(v -> startCameraService());
        btnStopCamera.setOnClickListener(v -> stopCameraService());
        btnSettings.setOnClickListener(v -> openSettings());
        btnEmergencyStop.setOnClickListener(v -> emergencyStop());
        
        // Set initial status
        txtStatus.setText("Ready - Configure settings first");
        
        Log.d(TAG, "UI initialized");
    }
    
    private void checkInitialPermissions() {
        // Check camera permission
        if (!checkCameraPermission()) {
            Log.w(TAG, "Camera permission not granted");
        }
        
        // Check overlay permission
        if (!checkOverlayPermission()) {
            Log.w(TAG, "Overlay permission not granted");
        }
        
        // Check if HDMI input is available
        if (!isHDMIInputAvailable()) {
            Log.w(TAG, "HDMI input not detected");
            txtStatus.setText("HDMI input not detected - Check connections");
        }
    }
    
    private void initializeXiboClient() {
        // Show initial status
        txtStatus.setText("Checking Xibo configuration...");
        
        // Run the initialization in a background thread to prevent UI freezing
        new Thread(() -> {
            try {
                // Get saved credentials from preferences
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                String cmsUrl = prefs.getString("xibo_cms_url", "");
                String clientId = prefs.getString("xibo_client_id", "");
                String clientSecret = prefs.getString("xibo_client_secret", "");
                String username = prefs.getString("xibo_username", "");
                String password = prefs.getString("xibo_password", "");
                
                // Check if Xibo is disabled in preferences
                boolean xiboEnabled = prefs.getBoolean("xibo_enabled", true);
                if (!xiboEnabled) {
                    Log.d(TAG, "Xibo is disabled in preferences");
                    runOnUiThread(() -> {
                        txtStatus.setText("Xibo integration disabled - Will use basic overlay");
                        isXiboAuthenticated = false;
                        updateUIState();
                    });
                    return;
                }
            
            // Only attempt authentication if we have credentials
            if (!cmsUrl.isEmpty() && !clientId.isEmpty() && !clientSecret.isEmpty() 
                    && !username.isEmpty() && !password.isEmpty()) {
                    
                Log.d(TAG, "Initializing Xibo client with saved credentials");
                
                // Create a temporary WebView just for authentication
                android.webkit.WebView tempWebView = new android.webkit.WebView(this);
                
                // Set a timeout for authentication
                final Handler timeoutHandler = new Handler();
                final Runnable timeoutRunnable = new Runnable() {
                    @Override
                    public void run() {
                        if (xiboWebClient != null && !isXiboAuthenticated) {
                            Log.w(TAG, "Xibo authentication timed out");
                            runOnUiThread(() -> {
                                txtStatus.setText("Xibo authentication timed out - Check network");
                                Toast.makeText(MainActivity.this, 
                                        "Xibo authentication timed out", 
                                        Toast.LENGTH_LONG).show();
                                // Clean up
                                tempWebView.destroy();
                            });
                        }
                    }
                };
                
                // Set a 10-second timeout
                timeoutHandler.postDelayed(timeoutRunnable, 10000);
                
                // Create Xibo client with listener
                xiboWebClient = new XiboWebClient(
                    this,
                    tempWebView,
                    cmsUrl,
                    clientId,
                    clientSecret,
                    username,
                    password,
                    new XiboWebClient.XiboClientListener() {
                        @Override
                        public void onAuthSuccess() {
                            // Remove timeout
                            timeoutHandler.removeCallbacks(timeoutRunnable);
                            
                            runOnUiThread(() -> {
                                isXiboAuthenticated = true;
                                updateUIState();
                                Toast.makeText(MainActivity.this, 
                                        "Successfully authenticated with Xibo CMS", 
                                        Toast.LENGTH_SHORT).show();
                                txtStatus.setText("Xibo authenticated - Ready to start overlay");
                                
                                // Clean up temporary WebView
                                tempWebView.destroy();
                            });
                        }
                        
                        @Override
                        public void onAuthFailure(String reason) {
                            // Remove timeout
                            timeoutHandler.removeCallbacks(timeoutRunnable);
                            
                            runOnUiThread(() -> {
                                isXiboAuthenticated = false;
                                updateUIState();
                                
                                // Offer to proceed without Xibo
                                new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("Xibo Authentication Failed")
                                    .setMessage("Error: " + reason + "\n\nWould you like to continue without Xibo?")
                                    .setPositiveButton("Yes, Continue", (dialog, which) -> {
                                        // Proceed without Xibo
                                        txtStatus.setText("Running without Xibo - Basic overlay mode");
                                    })
                                    .setNegativeButton("No, Fix Settings", (dialog, which) -> {
                                        txtStatus.setText("Xibo authentication failed - Check settings");
                                    })
                                    .show();
                                
                                // Clean up temporary WebView
                                tempWebView.destroy();
                            });
                        }
                        
                        @Override
                        public void onLayoutLoaded(String layoutId) {
                            Log.d(TAG, "Layout loaded: " + layoutId);
                        }
                        
                        @Override
                        public void onLayoutLoadFailed(String layoutId, String reason) {
                            Log.e(TAG, "Layout load failed: " + layoutId + ", reason: " + reason);
                        }
                        
                        @Override
                        public void onConnectionStateChanged(boolean connected) {
                            Log.d(TAG, "Connection state changed: " + connected);
                        }
                    }
                );
                
                    // Start authentication (already on background thread)
                    xiboWebClient.authenticate();
                    
                } else {
                    Log.d(TAG, "No Xibo credentials found - user needs to configure settings");
                    runOnUiThread(() -> {
                        txtStatus.setText("Configure Xibo settings to continue");
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error initializing Xibo client", e);
                runOnUiThread(() -> {
                    txtStatus.setText("Error initializing Xibo client");
                    Toast.makeText(MainActivity.this, 
                            "Error initializing Xibo: " + e.getMessage(), 
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    private void startOverlayService() {
        Log.d(TAG, "Starting overlay service");
        
        // Check overlay permission first
        if (!checkOverlayPermission()) {
            Toast.makeText(this, "Overlay permission required", Toast.LENGTH_SHORT).show();
            requestOverlayPermission();
            return;
        }
        
        // Check if Xibo is authenticated (optional - can work without Xibo)
        if (!isXiboAuthenticated) {
            new AlertDialog.Builder(this)
                .setTitle("Xibo Not Configured")
                .setMessage("Xibo CMS is not configured. Start overlay anyway with test content?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    // Start camera service first to ensure proper HDMI capture
                    if (!isCameraServiceRunning) {
                        startCameraService();
                        
                        // Add a small delay to ensure camera service is properly started
                        new Handler().postDelayed(() -> {
                            proceedWithOverlayStart();
                        }, 1000);
                    } else {
                        proceedWithOverlayStart();
                    }
                })
                .setNegativeButton("No", (dialog, which) -> {
                    Toast.makeText(this, "Please configure Xibo settings first", Toast.LENGTH_SHORT).show();
                })
                .show();
            return;
        }
        
        // Start camera service first to ensure proper HDMI capture
        if (!isCameraServiceRunning) {
            startCameraService();
            
            // Add a small delay to ensure camera service is properly started
            new Handler().postDelayed(() -> {
                proceedWithOverlayStart();
            }, 1000);
        } else {
            proceedWithOverlayStart();
        }
    }
    
    private void proceedWithOverlayStart() {
        try {
            // Stop any existing overlay service first
            stopOverlayService();
            
            txtStatus.setText("Starting overlay service...");
            
            // Small delay before starting new service to ensure cleanup
            Handler handler = new Handler();
            handler.postDelayed(() -> {
                try {
                    Intent intent = new Intent(this, OverlayService.class);
                    startForegroundService(intent);
                    isOverlayServiceRunning = true;
                    updateUIState();
                    txtStatus.setText("Overlay service started successfully");
                    
                    // Connect the services for HDMI overlay
                    connectServices();
                    
                    Toast.makeText(this, "Overlay started - Check top-right corner", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e(TAG, "Error starting overlay service", e);
                    txtStatus.setText("Failed to start overlay service");
                    Toast.makeText(this, "Failed to start overlay: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }, 500);
            
        } catch (Exception e) {
            Log.e(TAG, "Error in overlay start procedure", e);
            Toast.makeText(this, "Failed to start overlay: " + e.getMessage(), Toast.LENGTH_LONG).show();
            txtStatus.setText("Overlay start failed");
        }
    }
    
    /**
     * Connect camera and overlay services for proper HDMI capture and overlay
     */
    private void connectServices() {
        if (isCameraServiceRunning && isOverlayServiceRunning) {
            Log.d(TAG, "Connecting camera and overlay services");
            
            // Create a surface provider for camera service
            surfaceProvider = new CameraService.SurfaceProvider() {
                @Override
                public void getSurface(CameraService.SurfaceCallback callback) {
                    Log.d(TAG, "Surface requested by camera service");
                    try {
                        // Send broadcast to camera service to request surface from overlay
                        Intent surfaceIntent = new Intent("REQUEST_OVERLAY_SURFACE");
                        surfaceIntent.putExtra("callback_id", System.currentTimeMillis());
                        sendBroadcast(surfaceIntent);
                        
                        // The actual surface sharing happens through broadcast receivers
                        // in each service
                    } catch (Exception e) {
                        Log.e(TAG, "Error providing surface to camera", e);
                    }
                }
            };
            
            // Send broadcast to set up connection between services
            Intent connectIntent = new Intent("CONNECT_SERVICES");
            sendBroadcast(connectIntent);
            
            Log.d(TAG, "Services connection initialized");
        } else {
            Log.w(TAG, "Cannot connect services - one or both services not running");
        }
    }
    
    private void stopOverlayService() {
        Log.d(TAG, "Stopping overlay service");
        
        try {
            // Disconnect services first
            disconnectServices();
            
            Intent intent = new Intent(this, OverlayService.class);
            stopService(intent);
            isOverlayServiceRunning = false;
            updateUIState();
            txtStatus.setText("Overlay service stopped");
            
            // Force garbage collection to free up memory
            System.gc();
            
            Toast.makeText(this, "Overlay stopped", Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            Log.e(TAG, "Error stopping overlay service", e);
            txtStatus.setText("Error stopping overlay");
        }
    }
    
    /**
     * Disconnect services to clean up resources
     */
    private void disconnectServices() {
        if (surfaceProvider != null) {
            // Send broadcast to disconnect services
            Intent disconnectIntent = new Intent("DISCONNECT_SERVICES");
            sendBroadcast(disconnectIntent);
            
            surfaceProvider = null;
            Log.d(TAG, "Services disconnected");
        }
    }
    
    private void startCameraService() {
        Log.d(TAG, "Starting camera service");
        
        if (!checkCameraPermission()) {
            requestCameraPermission();
            return;
        }
        
        // Check if HDMI input is available
        if (!isHDMIInputAvailable()) {
            Toast.makeText(this, "HDMI input not detected. Please check HDMI connection.", Toast.LENGTH_LONG).show();
            return;
        }
        
        try {
            // Register this activity as a listener for camera events
            Intent intent = new Intent(this, CameraService.class);
            intent.putExtra("register_listener", true);
            startForegroundService(intent);
            isCameraServiceRunning = true;
            
            // Start HDMI capture after service is running
            Handler handler = new Handler();
            handler.postDelayed(() -> {
                Intent captureIntent = new Intent("START_HDMI_CAPTURE");
                captureIntent.putExtra("from_activity", true);
                sendBroadcast(captureIntent);
            }, 1000);
            
            updateUIState();
            txtStatus.setText("HDMI capture started");
            Toast.makeText(this, "Camera service started", Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting camera service", e);
            Toast.makeText(this, "Failed to start camera: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    // CameraServiceListener interface implementation
    @Override
    public void onCameraStarted() {
        Log.d(TAG, "Camera started callback received");
        runOnUiThread(() -> {
            Toast.makeText(this, "HDMI capture active", Toast.LENGTH_SHORT).show();
            txtStatus.setText("HDMI capture active and ready for overlay");
        });
    }

    @Override
    public void onCameraStopped() {
        Log.d(TAG, "Camera stopped callback received");
        runOnUiThread(() -> {
            Toast.makeText(this, "HDMI capture stopped", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onCameraError(String errorMessage) {
        Log.e(TAG, "Camera error: " + errorMessage);
        runOnUiThread(() -> {
            Toast.makeText(this, "HDMI error: " + errorMessage, Toast.LENGTH_LONG).show();
            txtStatus.setText("HDMI error: " + errorMessage);
        });
    }
    
    private void stopCameraService() {
        Log.d(TAG, "Stopping camera service");
        
        try {
            Intent intent = new Intent(this, CameraService.class);
            stopService(intent);
            isCameraServiceRunning = false;
            updateUIState();
            txtStatus.setText("Camera service stopped");
            
            Toast.makeText(this, "Camera service stopped", Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            Log.e(TAG, "Error stopping camera service", e);
        }
    }
    
    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }
    
    private void emergencyStop() {
        Log.w(TAG, "Emergency stop initiated");
        
        new AlertDialog.Builder(this)
            .setTitle("Emergency Stop")
            .setMessage("This will force stop all services and clear overlays. Continue?")
            .setPositiveButton("YES", (dialog, which) -> {
                performEmergencyStop();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void performEmergencyStop() {
        try {
            txtStatus.setText("Emergency stop in progress...");
            
            // Stop all services forcefully
            stopService(new Intent(this, OverlayService.class));
            stopService(new Intent(this, CameraService.class));
            
            // Reset service states
            isOverlayServiceRunning = false;
            isCameraServiceRunning = false;
            
            // Clear any system overlays (if possible)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                // Note: You can't directly remove other app's overlays, but stopping services should help
                Log.d(TAG, "Overlay permission available for cleanup");
            }
            
            // Force garbage collection multiple times
            for (int i = 0; i < 3; i++) {
                System.gc();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            // Update UI
            updateUIState();
            txtStatus.setText("Emergency stop completed - All services stopped");
            
            Toast.makeText(this, "Emergency stop completed successfully", Toast.LENGTH_LONG).show();
            
        } catch (Exception e) {
            Log.e(TAG, "Error during emergency stop", e);
            txtStatus.setText("Emergency stop encountered errors");
            Toast.makeText(this, "Emergency stop completed with errors: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private boolean isHDMIInputAvailable() {
        try {
            CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            String[] cameraIds = cameraManager.getCameraIdList();
            
            Log.d(TAG, "Checking HDMI input availability. Found " + cameraIds.length + " cameras");
            
            for (String cameraId : cameraIds) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Size[] outputSizes = map.getOutputSizes(ImageFormat.YUV_420_888);
                
                if (outputSizes.length > 0) {
                    Log.d(TAG, "HDMI input detected on camera: " + cameraId);
                    return true;
                }
            }
            
            Log.d(TAG, "No HDMI input detected");
            return false;
            
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error accessing camera manager", e);
            return false;
        }
    }
    
    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }
    
    private boolean checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this);
        }
        return true;
    }
    
    private void requestOverlayPermission() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Overlay Permission Required");
        builder.setMessage("This app needs permission to display over other apps for the overlay functionality.");
        builder.setPositiveButton("Grant Permission", (dialog, which) -> {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            Toast.makeText(
                    MainActivity.this,
                    "Overlay permission is required for this app to function properly",
                    Toast.LENGTH_LONG
            ).show();
        });
        builder.show();
    }
    
    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.CAMERA}, 
                REQUEST_CAMERA_PERMISSION);
    }
    
    private void updateUIState() {
        btnStartOverlay.setEnabled(!isOverlayServiceRunning);
        btnStopOverlay.setEnabled(isOverlayServiceRunning);
        
        btnStartCamera.setEnabled(!isCameraServiceRunning && isXiboAuthenticated);
        btnStopCamera.setEnabled(isCameraServiceRunning);
        
        // Update status text
        StringBuilder status = new StringBuilder("Status: ");
        if (isXiboAuthenticated) {
            status.append("Authenticated to Xibo CMS | ");
        } else {
            status.append("Not authenticated to Xibo CMS | ");
        }
        
        if (isOverlayServiceRunning) {
            status.append("Overlay: Running | ");
        } else {
            status.append("Overlay: Stopped | ");
        }
        
        if (isCameraServiceRunning) {
            status.append("Camera: Running");
        } else {
            status.append("Camera: Stopped");
        }
        
        txtStatus.setText(status.toString());
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // Check if services are running and update UI
        // This is a simplified check and might need a more robust implementation
        isOverlayServiceRunning = isServiceRunning(OverlayService.class.getName());
        isCameraServiceRunning = isServiceRunning(CameraService.class.getName());
        
        updateUIState();
    }
    
    /**
     * Simplified check if a service is running
     * Note: This is not 100% reliable and might need improvement
     */
    private boolean isServiceRunning(String serviceClassName) {
        // For a more robust implementation, you would use ActivityManager
        // This is a placeholder for demonstration
        return false; // Replace with actual implementation
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (allGranted) {
                Toast.makeText(this, "Camera permission granted", Toast.LENGTH_SHORT).show();
                // Can now start camera service if needed
            } else {
                Toast.makeText(this, 
                        "Camera permission is required for HDMI capture", 
                        Toast.LENGTH_LONG).show();
            }
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "Overlay permission granted", Toast.LENGTH_SHORT).show();
                    // Can now start overlay service if needed
                } else {
                    Toast.makeText(this, 
                            "Overlay permission denied. Functionality will be limited.", 
                            Toast.LENGTH_LONG).show();
                }
            }
        } else if (requestCode == REQUEST_SETTINGS) {
            // Re-initialize components based on new settings
            initializeXiboClient();
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.action_settings) {
            openSettings();
            return true;
        } else if (id == R.id.action_refresh_auth) {
            // Re-authenticate with Xibo CMS
            if (xiboWebClient != null) {
                xiboWebClient.authenticate();
                txtStatus.setText("Status: Re-authenticating with Xibo CMS...");
            }
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Disconnect services
        disconnectServices();
        
        // Clean up resources
        if (xiboWebClient != null) {
            xiboWebClient.cleanup();
        }
        
        // Unregister as listener
        if (isCameraServiceRunning) {
            Intent intent = new Intent("UNREGISTER_CAMERA_LISTENER");
            sendBroadcast(intent);
        }
    }

}
