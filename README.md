# Unable to Locate XiboWebClient.java

I was unable to locate the XiboWebClient.java file in the specified directory structure:
- Attempted to find the file at `/Users/shimasarah/Desktop/Burkina projects/GENYX Internship/weboverlay/app/src/main/java/com/genyx/weboverlay/XiboWebClient.java`
- Searched for any Java files in the weboverlay directory
- Searched for any references to "XiboWebClient" or "xibo" in the project
- Checked for the existence of the directory structure mentioned

To fix the file structure as requested, I'll need:
1. The correct path to the XiboWebClient.java file, or
2. The content of the file provided directly

Once I have access to the file, I can:
- Remove the stray closing bracket at line 504
- Move the createSecureOkHttpClient() method inside the class
- Move the injectCustomJavaScript() method inside the class
- Move the XiboJavaScriptInterface inner class inside the class
- Ensure there's only one closing bracket at the end of the file

# WebOverlay - HDMI Capture & Xibo CMS Overlay

An Android application for capturing HDMI input and displaying Xibo CMS content as an overlay.

## Project Overview

WebOverlay is an Android application designed for digital signage scenarios where content from an HDMI input (such as live TV, video feeds, or other sources) needs to be displayed alongside overlay content managed by the Xibo CMS. The application:

- Captures HDMI input via the LT6911UXC bridge chip
- Connects to a Xibo CMS instance for overlay content
- Displays both the HDMI content and overlays simultaneously
- Provides configuration options for positioning and transparency
- Supports auto-start on device boot

This solution is designed for RockChip-based Android boxes (like those with RK3566 SoC) that include HDMI input capabilities.

## Setup Instructions

### Requirements

- Android device with:
  - RockChip SoC (tested on RK3566)
  - HDMI input port connected to LT6911UXC bridge chip
  - Android 9.0 or higher
- Xibo CMS account with:
  - API access credentials
  - Layouts configured for overlay display

### Installation

1. Clone this repository or download the source code
2. Open the project in Android Studio
3. Build and install the application on your target device
4. Alternatively, use the pre-built APK available in the releases section

### Permissions

On first launch, you'll need to grant the following permissions:
- Camera (for HDMI capture)
- Display over other apps (for overlay functionality)
- Internet access (for Xibo CMS communication)
- Start on boot (optional, for auto-start functionality)

## Configuration Guide

### Xibo CMS Settings

1. In the app, go to Settings → Xibo CMS Settings
2. Enter your CMS URL (e.g., `https://your-xibo-cms.com`)
3. Enter your Client ID and Client Secret
4. Enter your Username and Password
5. Enable "Auto-connect on startup" if you want to automatically connect when the app launches

### HDMI Capture Settings

1. Go to Settings → HDMI Capture Settings
2. Select the appropriate "HDMI Input Source" (the app will try to auto-detect the correct source)
3. Set the desired resolution (default is 1920×1080)
4. Enable "Auto-start capture" if you want to automatically start HDMI capture when the app launches
5. Enable "Maintain aspect ratio" to preserve the input's original aspect ratio

### Overlay Settings

1. Go to Settings → Overlay Settings
2. Adjust the position (X/Y) and dimensions (width/height) of the overlay
3. Set the opacity level for the overlay content
4. Enable "Auto-start overlay" to automatically start the overlay when the app launches

### Advanced Settings

1. Go to Settings → Advanced Settings
2. Enable "Start on boot" to automatically start the application when the device boots
3. Enable "Debug logging" for troubleshooting purposes

## Usage Instructions

### Basic Operation

1. Start the application
2. The main screen displays status information and control buttons
3. Click "Start Overlay" to begin displaying Xibo CMS content
4. Click "Start HDMI Capture" to begin capturing HDMI input
5. Both services can run independently or simultaneously
6. Use the preview area to see a small version of the current display

### Xibo Layout Management

1. Create layouts in Xibo CMS with transparent backgrounds for overlay sections
2. Configure the regions and content for your overlay
3. Use the Xibo layout scheduling system to control when different overlays appear

### Auto-Start Configuration

To have the application start automatically with both services running:
1. Enable "Start on boot" in Advanced Settings
2. Enable "Auto-start overlay" in Overlay Settings
3. Enable "Auto-start capture" in HDMI Capture Settings
4. Reboot the device to verify proper auto-start functionality

## Troubleshooting Tips

### HDMI Capture Issues

- **No HDMI source detected**: Ensure the HDMI source is powered on and properly connected
- **Black screen**: Try cycling the HDMI input connection or restart the app
- **Wrong resolution**: Adjust the resolution in HDMI Capture Settings to match your input source
- **Wrong camera ID**: If automatic detection fails, try different camera IDs in the settings

### Overlay Issues

- **Overlay not appearing**: Check Xibo CMS credentials and connection
- **Overlay positioning incorrect**: Adjust the X/Y position and dimensions in settings
- **Content not updating**: Check internet connectivity and refresh authentication

### Connection Issues

- **Cannot connect to Xibo CMS**: Verify your credentials and URL
- **Authentication failures**: Ensure your client ID and secret are correct
- **Connection timeouts**: Check network connectivity and firewall settings

### Performance Issues

- **High CPU usage**: Reduce resolution or adjust overlay update frequency
- **Lag in display**: Ensure no other intensive applications are running
- **Memory issues**: Restart the application or the device

## Technical Implementation Details

### HDMI Capture Implementation

The HDMI capture functionality works through a specific hardware chain present in RockChip-based Android boxes:

```
HDMI Input → LT6911UXC Bridge Chip → MIPI CSI-2 Interface → RockChip Camera Subsystem → Android Camera API
```

#### Key Components

- **RK3566 SoC**: The main processor (RockChip)
- **LT6911UXC**: HDMI to MIPI CSI-2 bridge chip
- **MIPI CSI-2**: Camera/video interface standard
- **Video Devices**: Multiple `/dev/videoX` nodes for camera input

#### Technical Advantages

1. **Hardware-Accelerated**: 
   - No software encoding/decoding needed
   - Zero CPU overhead for video processing
   - Native hardware conversion path

2. **Low Latency**:
   - Direct hardware path means minimal delay
   - No intermediate buffers or transcoding
   - Real-time display capability

3. **System Integration**:
   - Uses standard Android camera subsystem
   - Full hardware acceleration support
   - Native resolution support (1920×1080)

### Android Architecture

The application follows a standard Android architecture:

- **Activities**: UI components (MainActivity, SettingsActivity)
- **Services**: Background components (CameraService, OverlayService)
- **Broadcast Receivers**: System events (BootReceiver)
- **Preferences**: Configuration storage (SharedPreferences)

### Xibo Integration

The app integrates with Xibo CMS using:

- **OAuth2 Authentication**: Secure token-based authentication
- **RESTful API**: For layout and content retrieval
- **WebView**: For rendering Xibo content
- **JavaScript Interface**: For communication between Xibo layouts and the app

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- Xibo CMS for their digital signage platform
- RockChip for their SoC documentation
- LontiumSemi for the LT6911UXC bridge chip

