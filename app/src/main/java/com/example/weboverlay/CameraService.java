package com.example.weboverlay;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Service for handling HDMI capture using Camera2 API through the LT6911UXC bridge chip.
 * This service treats the HDMI input as a camera source and manages capture sessions.
 */
public class CameraService extends Service {
    private static final String TAG = Constants.TAG_CAMERA;
    
    // Notification ID for foreground service
    private static final int NOTIFICATION_ID = Constants.NOTIFICATION_ID_CAMERA;
    private static final String CHANNEL_ID = Constants.CHANNEL_ID_CAMERA;
    
    // Default settings
    private static final int DEFAULT_CAMERA_ID = Constants.CameraDefaults.DEFAULT_CAMERA_ID;
    private static final int DEFAULT_WIDTH = Constants.CameraDefaults.DEFAULT_WIDTH;
    private static final int DEFAULT_HEIGHT = Constants.CameraDefaults.DEFAULT_HEIGHT;
    private static final int MAX_RETRY_ATTEMPTS = Constants.CameraDefaults.MAX_RETRY_ATTEMPTS;
    private static final long RETRY_DELAY_MS = Constants.CameraDefaults.RETRY_DELAY_MS;
    
    // Camera state
    private String cameraId;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private CaptureRequest.Builder captureRequestBuilder;
    private Size previewSize;
    private Surface previewSurface;
    
    // Background thread for camera operations
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    
    // Control flags
    private boolean isCapturing = false;
    private int retryCount = 0;
    private final Semaphore cameraOpenCloseLock = new Semaphore(1);
    
    // Listener interface for broadcasting camera events
    public interface CameraServiceListener {
        void onCameraStarted();
        void onCameraStopped();
        void onCameraError(String errorMessage);
    }
    
    private CameraServiceListener listener;
    
    // Surface provider callback for integrating with OverlayService
    public interface SurfaceProvider {
        void getSurface(SurfaceCallback callback);
    }
    
    public interface SurfaceCallback {
        void onSurfaceAvailable(Surface surface);
        void onSurfaceDestroyed();
    }
    
    private SurfaceProvider surfaceProvider;
    
    /**
     * Camera device state callback
     */
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.d(TAG, "Camera device opened");
            cameraOpenCloseLock.release();
            cameraDevice = camera;
            startCaptureSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.d(TAG, "Camera device disconnected");
            cameraOpenCloseLock.release();
            camera.close();
            cameraDevice = null;
            
            // Try to reconnect
            if (retryCount < MAX_RETRY_ATTEMPTS) {
                retryCount++;
                Log.d(TAG, "Attempting to reconnect to camera (attempt " + retryCount + ")");
                
                backgroundHandler.postDelayed(() -> {
                    openCamera();
                }, RETRY_DELAY_MS);
            } else {
                Log.e(TAG, "Max retry attempts reached. Stopping service.");
                if (listener != null) {
                    listener.onCameraError("Camera disconnected and could not be reconnected");
                }
                stopSelf();
            }
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.e(TAG, "Camera device error: " + error);
            cameraOpenCloseLock.release();
            camera.close();
            cameraDevice = null;
            
            String errorMsg;
            // Using constants from Constants.CameraError
            if (error == Constants.CameraError.ERROR_CAMERA_IN_USE) {
                errorMsg = "Camera is already in use";
            } else if (error == Constants.CameraError.ERROR_MAX_CAMERAS_IN_USE) {
                errorMsg = "Maximum number of cameras are already open";
            } else if (error == Constants.CameraError.ERROR_CAMERA_DISABLED) {
                errorMsg = "Camera is disabled";
            } else if (error == Constants.CameraError.ERROR_CAMERA_DEVICE) {
                errorMsg = "Camera device error";
            } else if (error == Constants.CameraError.ERROR_CAMERA_SERVICE) {
                errorMsg = "Camera service error";
            } else {
                errorMsg = "Unknown camera error: " + error;
            }
            
            if (listener != null) {
                listener.onCameraError(errorMsg);
            }
            
            // Try to recover
            if (retryCount < MAX_RETRY_ATTEMPTS) {
                retryCount++;
                Log.d(TAG, "Attempting to recover from camera error (attempt " + retryCount + ")");
                
                backgroundHandler.postDelayed(() -> {
                    openCamera();
                }, RETRY_DELAY_MS);
            } else {
                Log.e(TAG, "Max retry attempts reached. Stopping service.");
                stopSelf();
            }
        }
    };
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "CameraService onCreate");
        
        // Start background thread
        startBackgroundThread();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "CameraService onStartCommand");
        
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        
        // Reset retry counter
        retryCount = 0;
        
        // Initialize camera parameters from preferences
        initCameraParameters();
        
        // Start the camera capture process
        if (!isCapturing) {
            requestSurfaceAndOpenCamera();
        }
        
        return START_STICKY;
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not supporting binding
    }
    
    @Override
    public void onDestroy() {
        Log.d(TAG, "CameraService onDestroy");
        
        closeCamera();
        stopBackgroundThread();
        
        super.onDestroy();
    }
    
    /**
     * Create notification channel for Android O and above
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Camera Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Used to keep the camera service running");
            
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
                .setContentTitle("HDMI Capture Active")
                .setContentText("Capturing HDMI input")
                .setSmallIcon(R.drawable.ic_camera_notification)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        
        return builder.build();
    }
    
    /**
     * Initialize camera parameters from preferences
     */
    private void initCameraParameters() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        
        // Try to find the HDMI input camera by looking for specific characteristics
        cameraId = findHdmiInputCamera();
        if (cameraId == null) {
            // Fall back to preference or default
            cameraId = prefs.getString(Constants.Prefs.HDMI_CAMERA_ID, String.valueOf(DEFAULT_CAMERA_ID));
        }
        
        // Get preferred resolution
        int width = prefs.getInt(Constants.Prefs.HDMI_WIDTH, DEFAULT_WIDTH);
        int height = prefs.getInt(Constants.Prefs.HDMI_HEIGHT, DEFAULT_HEIGHT);
        previewSize = new Size(width, height);
        
        Log.d(TAG, "Initialized camera: id=" + cameraId + ", resolution=" + width + "x" + height);
    }
    
    /**
     * Start a background thread and its Handler for camera operations
     */
    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }
    
    /**
     * Stop the background thread
     */
    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                Log.e(TAG, "Error stopping background thread", e);
            }
        }
    }
    
    /**
     * Request a surface from the OverlayService and then open the camera
     */
    private void requestSurfaceAndOpenCamera() {
        // If we already have a surface provider (OverlayService), request a surface
        if (surfaceProvider != null) {
            surfaceProvider.getSurface(new SurfaceCallback() {
                @Override
                public void onSurfaceAvailable(Surface surface) {
                    previewSurface = surface;
                    openCamera();
                }
                
                @Override
                public void onSurfaceDestroyed() {
                    closeCamera();
                }
            });
        } else {
            // Create a dummy surface for testing or when no overlay service is available
            createDummySurface();
        }
    }
    
    /**
     * Create a dummy surface for testing purposes
     */
    private void createDummySurface() {
        Log.d(TAG, "Creating dummy surface (no overlay service available)");
        SurfaceTexture surfaceTexture = new SurfaceTexture(0);
        surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
        previewSurface = new Surface(surfaceTexture);
        openCamera();
    }
    
    /**
     * Attempt to find the camera that corresponds to the HDMI input
     * LT6911UXC bridge chip connects HDMI to MIPI CSI-2 which is seen as a camera
     */
    private String findHdmiInputCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                
                // Check if this is a hardware external camera (like HDMI input)
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                Integer hwLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                
                // External cameras often have EXTERNAL facing or LIMITED hardware level
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_EXTERNAL) {
                    Log.d(TAG, "Found external camera: " + cameraId);
                    return cameraId;
                }
                
                // Some devices might use a specific hardware level for HDMI inputs
                if (hwLevel != null && hwLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED) {
                    // Additional checks could be added here
                    Log.d(TAG, "Found potentially HDMI-connected camera: " + cameraId);
                    return cameraId;
                }
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to enumerate cameras", e);
        }
        
        return null;
    }
    
    /**
     * Open the camera with the selected ID
     */
    @SuppressLint("MissingPermission")
    private void openCamera() {
        if (cameraId == null) {
            Log.e(TAG, "No camera ID available");
            if (listener != null) {
                listener.onCameraError("No camera ID available for HDMI input");
            }
            stopSelf();
            return;
        }
        
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        
        try {
            // Check if we can acquire the camera lock
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            
            // Verify the camera supports our desired preview size
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            
            if (map == null) {
                Log.e(TAG, "Cannot get available preview/video sizes");
                if (listener != null) {
                    listener.onCameraError("Camera doesn't support video streaming");
                }
                cameraOpenCloseLock.release();
                stopSelf();
                return;
            }
            
            // Choose the optimal preview size
            previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), 
                    previewSize.getWidth(), previewSize.getHeight());
            
            Log.d(TAG, "Selected preview size: " + previewSize.getWidth() + "x" + previewSize.getHeight());
            
            // Open the camera
            manager.openCamera(cameraId, stateCallback, backgroundHandler);
            
        } catch (CameraAccessException e) {
            Log.e(TAG, "Cannot access the camera.", e);
            if (listener != null) {
                listener.onCameraError("Cannot access the camera: " + e.getMessage());
            }
            cameraOpenCloseLock.release();
            stopSelf();
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while trying to lock camera opening.", e);
            cameraOpenCloseLock.release();
            Thread.currentThread().interrupt();
            stopSelf();
        } catch (SecurityException e) {
            Log.e(TAG, "Missing camera permission.", e);
            if (listener != null) {
                listener.onCameraError("Missing camera permission");
            }
            cameraOpenCloseLock.release();
            stopSelf();
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error opening camera", e);
            if (listener != null) {
                listener.onCameraError("Unexpected error: " + e.getMessage());
            }
            cameraOpenCloseLock.release();
            stopSelf();
        }
    }
    
    /**
     * Start a capture session for camera preview
     */
    private void startCaptureSession() {
        if (cameraDevice == null || previewSurface == null) {
            Log.e(TAG, "Cannot create capture session: camera or surface not available");
            return;
        }
        
        try {
            closePreviewSession();
            
            // Create a CaptureRequest.Builder for previews
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(previewSurface);
            
            // Auto-exposure and auto-white-balance for better quality
            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
            
            // Create a capture session
            cameraDevice.createCaptureSession(Collections.singletonList(previewSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            Log.d(TAG, "Camera capture session configured");
                            cameraCaptureSession = session;
                            startPreview();
                        }
                        
                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e(TAG, "Failed to configure camera capture session");
                            if (listener != null) {
                                listener.onCameraError("Failed to configure camera session");
                            }
                            
                            // Try to recover
                            if (retryCount < MAX_RETRY_ATTEMPTS) {
                                retryCount++;
                                Log.d(TAG, "Retrying camera session configuration (attempt " + retryCount + ")");
                                
                                backgroundHandler.postDelayed(() -> {
                                    closeCamera();
                                    openCamera();
                                }, RETRY_DELAY_MS);
                            } else {
                                stopSelf();
                            }
                        }
                    }, backgroundHandler);
                    
        } catch (CameraAccessException e) {
            Log.e(TAG, "Cannot access camera for capture session", e);
            if (listener != null) {
                listener.onCameraError("Cannot access camera: " + e.getMessage());
            }
            stopSelf();
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error creating camera session", e);
            if (listener != null) {
                listener.onCameraError("Session error: " + e.getMessage());
            }
            stopSelf();
        }
    }
    
    /**
     * Start camera preview
     */
    private void startPreview() {
        if (cameraCaptureSession == null || captureRequestBuilder == null) {
            Log.e(TAG, "Cannot start preview: capture session or request builder is null");
            return;
        }
        
        try {
            // Start the preview
            CaptureRequest previewRequest = captureRequestBuilder.build();
            cameraCaptureSession.setRepeatingRequest(previewRequest, null, backgroundHandler);
            
            isCapturing = true;
            retryCount = 0; // Reset retry count on successful preview
            
            if (listener != null) {
                listener.onCameraStarted();
            }
            
            Log.d(TAG, "Camera preview started successfully");
            
        } catch (CameraAccessException e) {
            Log.e(TAG, "Cannot start camera preview", e);
            if (listener != null) {
                listener.onCameraError("Cannot start preview: " + e.getMessage());
            }
        }
    }
    
    /**
     * Close the current preview session
     */
    private void closePreviewSession() {
        if (cameraCaptureSession != null) {
            try {
                cameraCaptureSession.stopRepeating();
                cameraCaptureSession.close();
                cameraCaptureSession = null;
            } catch (CameraAccessException e) {
                Log.e(TAG, "Error closing preview session", e);
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error closing preview session", e);
            }
        }
    }
    
    /**
     * Close the camera and release resources
     */
    private void closeCamera() {
        try {
            cameraOpenCloseLock.acquire();
            
            closePreviewSession();
            
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
            
            if (previewSurface != null) {
                previewSurface.release();
                previewSurface = null;
            }
            
            isCapturing = false;
            
            if (listener != null) {
                listener.onCameraStopped();
            }
            
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while trying to lock camera closing.", e);
            Thread.currentThread().interrupt();
        } finally {
            cameraOpenCloseLock.release();
        }
    }
    
    /**
     * Choose the optimal size for camera preview
     */
    private Size chooseOptimalSize(Size[] choices, int width, int height) {
        if (choices == null || choices.length == 0) {
            Log.e(TAG, "No preview sizes available");
            return new Size(width, height);
        }
        
        // Try to find the requested size
        for (Size size : choices) {
            if (size.getWidth() == width && size.getHeight() == height) {
                return size;
            }
        }
        
        // If the exact size is not available, find the closest match
        List<Size> bigEnough = new ArrayList<>();
        
        for (Size option : choices) {
            // Only consider sizes close to our target aspect ratio
            double targetRatio = (double) width / height;
            double optionRatio = (double) option.getWidth() / option.getHeight();
            
            // Allow some margin of error for the aspect ratio
            if (Math.abs(optionRatio - targetRatio) < 0.1) {
                bigEnough.add(option);
            }
        }
        
        if (bigEnough.size() > 0) {
            // Return the closest size to our target
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            // If no appropriate sizes, just return the largest available
            return Collections.max(Arrays.asList(choices), new CompareSizesByArea());
        }
    }
    
    /**
     * Comparator for comparing sizes by area
     */
    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }
    
    /**
     * Set a listener to receive camera events
     */
    public void setListener(CameraServiceListener listener) {
        this.listener = listener;
    }
    
    /**
     * Set a surface provider for integrating with OverlayService
     */
    public void setSurfaceProvider(SurfaceProvider provider) {
        this.surfaceProvider = provider;
    }
}

