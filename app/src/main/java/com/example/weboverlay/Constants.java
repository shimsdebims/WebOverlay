package com.example.weboverlay;

/**
 * Constants class to hold all application-wide constant values.
 * This prevents hardcoding values throughout the application and
 * makes maintenance easier.
 */
public class Constants {
    
    // Log Tags
    public static final String TAG_MAIN = "WebOverlay_Main";
    public static final String TAG_CAMERA = "WebOverlay_Camera";
    public static final String TAG_OVERLAY = "WebOverlay_Overlay";
    public static final String TAG_XIBO = "WebOverlay_Xibo";
    
    // Camera Error Codes (matching Android Camera2 API constants)
    public static final class CameraError {
        public static final int ERROR_CAMERA_IN_USE = 1;
        public static final int ERROR_MAX_CAMERAS_IN_USE = 2;
        public static final int ERROR_CAMERA_DISABLED = 3;
        public static final int ERROR_CAMERA_DEVICE = 4;
        public static final int ERROR_CAMERA_SERVICE = 5;
    }
    
    // Camera States
    public static final class CameraState {
        public static final int STATE_PREVIEW = 0;
        public static final int STATE_WAITING_LOCK = 1;
        public static final int STATE_WAITING_PRECAPTURE = 2;
        public static final int STATE_WAITING_NON_PRECAPTURE = 3;
        public static final int STATE_PICTURE_TAKEN = 4;
    }
    
    // Notification IDs
    public static final int NOTIFICATION_ID_OVERLAY = 1001;
    public static final int NOTIFICATION_ID_CAMERA = 1002;
    
    // Notification Channel IDs
    public static final String CHANNEL_ID_OVERLAY = "overlay_service_channel";
    public static final String CHANNEL_ID_CAMERA = "camera_service_channel";
    
    // Default Camera Settings
    public static final class CameraDefaults {
        public static final int DEFAULT_CAMERA_ID = 0; // Usually the HDMI input
        public static final int DEFAULT_WIDTH = 1920;
        public static final int DEFAULT_HEIGHT = 1080;
        public static final int MAX_RETRY_ATTEMPTS = 3;
        public static final long RETRY_DELAY_MS = 3000; // 3 seconds
    }
    
    // Permission Request Codes
    public static final int REQUEST_CAMERA_PERMISSION = 100;
    public static final int REQUEST_OVERLAY_PERMISSION = 101;
    public static final int REQUEST_SETTINGS = 102;
    
    // Shared Preferences Keys
    public static final class Prefs {
        // Xibo CMS preferences
        public static final String XIBO_PREF_NAME = "XiboPrefs";
        public static final String XIBO_TOKEN = "access_token";
        public static final String XIBO_REFRESH_TOKEN = "refresh_token";
        public static final String XIBO_TOKEN_EXPIRY = "token_expiry";
        public static final String XIBO_CMS_URL = "xibo_cms_url";
        public static final String XIBO_CLIENT_ID = "xibo_client_id";
        public static final String XIBO_CLIENT_SECRET = "xibo_client_secret";
        public static final String XIBO_USERNAME = "xibo_username";
        public static final String XIBO_PASSWORD = "xibo_password";
        public static final String XIBO_AUTO_CONNECT = "xibo_auto_connect";
        
        // HDMI Capture preferences
        public static final String HDMI_CAMERA_ID = "hdmi_camera_id";
        public static final String HDMI_WIDTH = "hdmi_width";
        public static final String HDMI_HEIGHT = "hdmi_height";
        public static final String HDMI_AUTO_START = "hdmi_auto_start";
        public static final String HDMI_KEEP_ASPECT_RATIO = "hdmi_keep_aspect_ratio";
        
        // Overlay preferences
        public static final String OVERLAY_POSITION_X = "overlay_position_x";
        public static final String OVERLAY_POSITION_Y = "overlay_position_y";
        public static final String OVERLAY_WIDTH = "overlay_width";
        public static final String OVERLAY_HEIGHT = "overlay_height";
        public static final String OVERLAY_OPACITY = "overlay_opacity";
        public static final String OVERLAY_AUTO_START = "overlay_auto_start";
        public static final String OVERLAY_OFFSET_X = "overlay_offset_x";
        public static final String OVERLAY_OFFSET_Y = "overlay_offset_y";
        
        // Advanced preferences
        public static final String START_ON_BOOT = "start_on_boot";
        public static final String DEBUG_LOGGING = "debug_logging";
        public static final String KEEP_SCREEN_ON = "keep_screen_on";
        public static final String HIDE_ON_SCREEN_OFF = "hide_on_screen_off";
    }
    
    // Intent Actions
    public static final class IntentActions {
        public static final String ACTION_START_OVERLAY = "com.example.weboverlay.START_OVERLAY";
        public static final String ACTION_STOP_OVERLAY = "com.example.weboverlay.STOP_OVERLAY";
        public static final String ACTION_START_CAMERA = "com.example.weboverlay.START_CAMERA";
        public static final String ACTION_STOP_CAMERA = "com.example.weboverlay.STOP_CAMERA";
        public static final String ACTION_RESTART_SERVICES = "com.example.weboverlay.RESTART_SERVICES";
    }
    
    // Error Recovery Settings
    public static final long ERROR_RECOVERY_DELAY_MS = 5000; // 5 seconds
    
    // Wake Lock Timeout
    public static final long WAKE_LOCK_TIMEOUT = 30*60*1000L; // 30 minutes
    
    // Connection Monitoring
    public static final long CONNECTION_CHECK_INTERVAL = 30000; // 30 seconds
}

