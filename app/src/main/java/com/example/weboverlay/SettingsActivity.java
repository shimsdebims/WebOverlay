package com.example.weboverlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Settings activity for the Web Overlay application.
 * Manages configuration for Xibo CMS, HDMI capture, and overlay display.
 */
public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        
        // Add settings fragment
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        
        // Setup back button in action bar
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.settings_title);
        }
    }

    /**
     * Settings Fragment containing all preference categories and items.
     */
    public static class SettingsFragment extends PreferenceFragmentCompat implements
            SharedPreferences.OnSharedPreferenceChangeListener {

        private Map<String, String> cameraMap = new HashMap<>();

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.settings_activity, rootKey);
            
            // Initialize preference summaries
            initializeSummaries();
            
            // Setup input types for EditTextPreferences
            setupInputTypes();
            
            // Populate camera list
            populateCameraList();
            
            // Setup validation listeners
            setupValidation();
        }
        
        /**
         * Initialize summaries for preferences that show current value
         */
        private void initializeSummaries() {
            SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
            
            // Xibo CMS Settings
            setSummary("xibo_cms_url", sharedPreferences);
            setSummary("xibo_client_id", sharedPreferences);
            setSummary("xibo_username", sharedPreferences);
            // Don't show password or client secret in summary
            
            // HDMI Capture Settings
            updateResolutionSummary(sharedPreferences);
            
            // Overlay Settings
            Preference overlayPositionX = findPreference("overlay_position_x");
            if (overlayPositionX != null) {
                int value = sharedPreferences.getInt("overlay_position_x", 0);
                overlayPositionX.setSummary(value + "%");
            }
            
            Preference overlayPositionY = findPreference("overlay_position_y");
            if (overlayPositionY != null) {
                int value = sharedPreferences.getInt("overlay_position_y", 0);
                overlayPositionY.setSummary(value + "%");
            }
            
            Preference overlayWidth = findPreference("overlay_width");
            if (overlayWidth != null) {
                int value = sharedPreferences.getInt("overlay_width", 100);
                overlayWidth.setSummary(value + "%");
            }
            
            Preference overlayHeight = findPreference("overlay_height");
            if (overlayHeight != null) {
                int value = sharedPreferences.getInt("overlay_height", 100);
                overlayHeight.setSummary(value + "%");
            }
            
            Preference overlayOpacity = findPreference("overlay_opacity");
            if (overlayOpacity != null) {
                int value = sharedPreferences.getInt("overlay_opacity", 100);
                overlayOpacity.setSummary(value + "%");
            }
        }
        
        /**
         * Set summary for a text preference
         */
        private void setSummary(String key, SharedPreferences sharedPreferences) {
            Preference preference = findPreference(key);
            if (preference != null) {
                String value = sharedPreferences.getString(key, "");
                if (!TextUtils.isEmpty(value)) {
                    preference.setSummary(value);
                } else {
                    preference.setSummary(R.string.not_set);
                }
            }
        }
        
        /**
         * Update the resolution summary based on width and height values
         */
        private void updateResolutionSummary(SharedPreferences sharedPreferences) {
            int width = sharedPreferences.getInt("hdmi_width", 1920);
            int height = sharedPreferences.getInt("hdmi_height", 1080);
            
            Preference resolutionPref = findPreference("hdmi_resolution");
            if (resolutionPref != null) {
                resolutionPref.setSummary(width + " × " + height);
            }
        }
        
        /**
         * Setup proper input types for EditTextPreferences
         */
        private void setupInputTypes() {
            // URL input type
            EditTextPreference urlPref = findPreference("xibo_cms_url");
            if (urlPref != null) {
                urlPref.setOnBindEditTextListener(editText -> {
                    editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
                    editText.setSelection(editText.getText().length());
                });
            }
            
            // Password input types
            EditTextPreference passwordPref = findPreference("xibo_password");
            if (passwordPref != null) {
                passwordPref.setOnBindEditTextListener(editText -> {
                    editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    editText.setSelection(editText.getText().length());
                });
            }
            
            EditTextPreference clientSecretPref = findPreference("xibo_client_secret");
            if (clientSecretPref != null) {
                clientSecretPref.setOnBindEditTextListener(editText -> {
                    editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    editText.setSelection(editText.getText().length());
                });
            }
        }
        
        /**
         * Populate camera list with available cameras
         */
        private void populateCameraList() {
            ListPreference cameraIdPref = findPreference("hdmi_camera_id");
            if (cameraIdPref == null) return;
            
            CameraManager cameraManager = (CameraManager) requireContext().getSystemService(Context.CAMERA_SERVICE);
            try {
                String[] cameraIds = cameraManager.getCameraIdList();
                List<String> entries = new ArrayList<>();
                List<String> entryValues = new ArrayList<>();
                
                for (String id : cameraIds) {
                    CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                    Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                    Integer level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                    
                    String facingStr = "Unknown";
                    if (facing != null) {
                        switch (facing) {
                            case CameraCharacteristics.LENS_FACING_FRONT:
                                facingStr = "Front";
                                break;
                            case CameraCharacteristics.LENS_FACING_BACK:
                                facingStr = "Back";
                                break;
                            case CameraCharacteristics.LENS_FACING_EXTERNAL:
                                facingStr = "External (HDMI)";
                                break;
                        }
                    }
                    
                    String levelStr = "Unknown";
                    if (level != null) {
                        switch (level) {
                            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY:
                                levelStr = "Legacy";
                                break;
                            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED:
                                levelStr = "Limited";
                                break;
                            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL:
                                levelStr = "Full";
                                break;
                            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3:
                                levelStr = "Level 3";
                                break;
                        }
                    }
                    
                    String entry = "Camera " + id + " (" + facingStr + ", " + levelStr + ")";
                    entries.add(entry);
                    entryValues.add(id);
                    cameraMap.put(id, entry);
                }
                
                if (entries.size() > 0) {
                    cameraIdPref.setEntries(entries.toArray(new String[0]));
                    cameraIdPref.setEntryValues(entryValues.toArray(new String[0]));
                    
                    // Set default if not already set
                    if (cameraIdPref.getValue() == null) {
                        cameraIdPref.setValue(entryValues.get(0));
                    }
                    
                    // Update summary
                    String currentValue = cameraIdPref.getValue();
                    if (currentValue != null && cameraMap.containsKey(currentValue)) {
                        cameraIdPref.setSummary(cameraMap.get(currentValue));
                    }
                } else {
                    cameraIdPref.setEnabled(false);
                    cameraIdPref.setSummary("No cameras available");
                }
                
            } catch (CameraAccessException e) {
                Log.e(TAG, "Failed to access cameras", e);
                cameraIdPref.setEnabled(false);
                cameraIdPref.setSummary("Error accessing cameras");
            }
        }
        
        /**
         * Setup validation for input fields
         */
        private void setupValidation() {
            // Validate URL format
            EditTextPreference urlPref = findPreference("xibo_cms_url");
            if (urlPref != null) {
                urlPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    String url = (String) newValue;
                    if (TextUtils.isEmpty(url)) {
                        return true; // Allow empty for now
                    }
                    
                    // Basic URL validation
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        Toast.makeText(getContext(), 
                                "URL should start with http:// or https://", 
                                Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    
                    return true;
                });
            }
            
            // Handle resolution preference click
            Preference resolutionPref = findPreference("hdmi_resolution");
            if (resolutionPref != null) {
                resolutionPref.setOnPreferenceClickListener(preference -> {
                    showResolutionDialog();
                    return true;
                });
            }
            
            // Update seekbar summaries in real time
            setupSeekBarSummaryUpdater("overlay_position_x", "%");
            setupSeekBarSummaryUpdater("overlay_position_y", "%");
            setupSeekBarSummaryUpdater("overlay_width", "%");
            setupSeekBarSummaryUpdater("overlay_height", "%");
            setupSeekBarSummaryUpdater("overlay_opacity", "%");
        }
        
        /**
         * Setup a listener to update a SeekBar preference summary in real time
         */
        private void setupSeekBarSummaryUpdater(String key, String suffix) {
            SeekBarPreference seekBar = findPreference(key);
            if (seekBar != null) {
                seekBar.setOnPreferenceChangeListener((preference, newValue) -> {
                    int value = (int) newValue;
                    preference.setSummary(value + suffix);
                    return true;
                });
            }
        }
        
        /**
         * Show a dialog to configure resolution
         */
        private void showResolutionDialog() {
            // This would normally create a custom dialog with options for width and height
            // For simplicity, we'll just show a toast message
            Toast.makeText(getContext(), 
                    "Resolution dialog would appear here with width/height options", 
                    Toast.LENGTH_SHORT).show();
            
            // In a real implementation, you would show a dialog with number pickers
            // and update the hdmi_width and hdmi_height preferences accordingly
        }
        
        @Override
        public void onResume() {
            super.onResume();
            getPreferenceManager().getSharedPreferences()
                    .registerOnSharedPreferenceChangeListener(this);
        }
        
        @Override
        public void onPause() {
            getPreferenceManager().getSharedPreferences()
                    .unregisterOnSharedPreferenceChangeListener(this);
            super.onPause();
        }
        
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            // Update summaries when preferences change
            switch (key) {
                case "xibo_cms_url":
                case "xibo_client_id":
                case "xibo_username":
                    setSummary(key, sharedPreferences);
                    break;
                    
                case "hdmi_width":
                case "hdmi_height":
                    updateResolutionSummary(sharedPreferences);
                    break;
                    
                case "hdmi_camera_id":
                    ListPreference cameraIdPref = findPreference(key);
                    if (cameraIdPref != null) {
                        String value = cameraIdPref.getValue();
                        if (value != null && cameraMap.containsKey(value)) {
                            cameraIdPref.setSummary(cameraMap.get(value));
                        }
                    }
                    break;
            }
        }
    }
}

