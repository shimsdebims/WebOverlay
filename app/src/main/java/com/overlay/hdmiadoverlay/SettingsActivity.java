package com.overlay.hdmiadoverlay;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {
    private EditText xiboUrlInput;
    private EditText displayKeyInput;
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        preferences = getSharedPreferences("XiboSettings", MODE_PRIVATE);
        
        xiboUrlInput = findViewById(R.id.xiboUrlInput);
        displayKeyInput = findViewById(R.id.displayKeyInput);
        Button saveButton = findViewById(R.id.saveButton);
        Button testButton = findViewById(R.id.testButton);

        // Load saved settings
        xiboUrlInput.setText(preferences.getString("xibo_url", ""));
        displayKeyInput.setText(preferences.getString("display_key", ""));

        saveButton.setOnClickListener(v -> saveSettings());
        testButton.setOnClickListener(v -> testConnection());
    }

    private void saveSettings() {
        String xiboUrl = xiboUrlInput.getText().toString();
        String displayKey = displayKeyInput.getText().toString();

        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("xibo_url", xiboUrl);
        editor.putString("display_key", displayKey);
        editor.apply();

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
    }

    private void testConnection() {
        // Implement Xibo CMS connection test
        Toast.makeText(this, "Testing connection...", Toast.LENGTH_SHORT).show();
    }
}

