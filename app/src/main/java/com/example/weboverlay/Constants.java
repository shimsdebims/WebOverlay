package com.example.weboverlay;

public class Constants {
    // Log Tags
    public static final String TAG_MAIN = "WebOverlay_Main";
    public static final String TAG_OVERLAY = "WebOverlay_Overlay";
    
    // Xibo CMS Configuration
    public static final String XIBO_CMS_URL = "http://192.168.1.12:8080";
    public static final String XIBO_DISPLAY_KEY = "acdbda641289";
    public static final String XIBO_EMBED_URL = XIBO_CMS_URL + "/web/displays/embed/" + XIBO_DISPLAY_KEY;
    
    // Service Configuration
    public static final String CHANNEL_ID_OVERLAY = "overlay_service_channel";
    public static final int NOTIFICATION_ID_OVERLAY = 1002;
    
    // Shared Preferences Keys
    public static final class Prefs {
        public static final String OVERLAY_WIDTH = "overlay_width";
        public static final String OVERLAY_HEIGHT = "overlay_height";
        public static final String OVERLAY_TRANSPARENT = "overlay_transparent";
    }
}
