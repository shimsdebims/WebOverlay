package com.example.weboverlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;
import org.json.JSONException;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class XiboDisplayManager {
    private static final String TAG = Constants.TAG_XIBO;
    
    // API Endpoints
    private static final String ENDPOINT_REGISTER = "/display/register";
    private static final String ENDPOINT_STATUS = "/display/status";
    private static final String ENDPOINT_SCHEDULE = "/display/schedule";
    
    // Update intervals
    private static final long STATUS_UPDATE_INTERVAL = TimeUnit.MINUTES.toMillis(1);
    private static final long SCHEDULE_CHECK_INTERVAL = TimeUnit.MINUTES.toMillis(5);
    private static final long RETRY_INTERVAL = TimeUnit.SECONDS.toMillis(30);
    
    private final Context context;
    private final String cmsUrl;
    private final Handler mainHandler;
    private final OkHttpClient httpClient;
    private final SharedPreferences prefs;
    private final DisplayStateListener stateListener;
    
    // Display state
    private final String hardwareKey;
    private String displayId;
    private boolean isRegistered = false;
    private String currentLayoutId = null;
    private int currentStatus = 1; // 1 = Running, 2 = Pending, 3 = Error
    
    public interface DisplayStateListener {
        void onDisplayRegistered(String displayId);
        void onDisplayError(String error);
        void onLayoutChanged(String layoutId);
        void onStatusChanged(int status);
    }
    
    public XiboDisplayManager(Context context, String cmsUrl, DisplayStateListener listener) {
        this.context = context;
        this.cmsUrl = cmsUrl;
        this.stateListener = listener;
        
        mainHandler = new Handler(Looper.getMainLooper());
        httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
            
        prefs = context.getSharedPreferences("xibo_display", Context.MODE_PRIVATE);
        
        // Load or use configured hardware key
        hardwareKey = Constants.XIBO_DISPLAY_KEY;
        displayId = prefs.getString("display_id", null);
        isRegistered = prefs.getBoolean("is_registered", false);
        currentLayoutId = prefs.getString("current_layout_id", null);
    }
    
    public void start() {
        if (!isRegistered || displayId == null) {
            registerDisplay();
        } else {
            startDisplayLoop();
        }
    }
    
    public void stop() {
        setStatus(2); // Pending/Stopped
        mainHandler.removeCallbacksAndMessages(null);
    }
    
    public void forceUpdate() {
        checkSchedule();
    }
    
    private void setStatus(int newStatus) {
        if (currentStatus != newStatus) {
            currentStatus = newStatus;
            if (stateListener != null) {
                mainHandler.post(() -> stateListener.onStatusChanged(currentStatus));
            }
        }
    }
    
    private void registerDisplay() {
        try {
            JSONObject registerData = new JSONObject()
                .put("serverKey", Constants.XIBO_SERVER_KEY)
                .put("hardwareKey", hardwareKey)
                .put("displayName", "Android Overlay Display")
                .put("clientType", "android")
                .put("clientVersion", "1.0")
                .put("clientCode", 1);
            
            RequestBody body = RequestBody.create(
                MediaType.parse("application/json"),
                registerData.toString()
            );
            
            Request request = new Request.Builder()
                .url(cmsUrl + ENDPOINT_REGISTER)
                .post(body)
                .build();
                
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Display registration failed", e);
                    setStatus(3); // Error
                    scheduleRetry(XiboDisplayManager.this::registerDisplay, RETRY_INTERVAL);
                    
                    if (stateListener != null) {
                        mainHandler.post(() -> 
                            stateListener.onDisplayError("Registration failed: " + e.getMessage())
                        );
                    }
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        if (!response.isSuccessful()) {
                            throw new IOException("Registration failed: " + response.code());
                        }
                        
                        String responseBody = response.body().string();
                        JSONObject json = new JSONObject(responseBody);
                        
                        displayId = json.getString("displayId");
                        isRegistered = true;
                        setStatus(1); // Running
                        
                        // Save state
                        prefs.edit()
                            .putString("display_id", displayId)
                            .putBoolean("is_registered", true)
                            .apply();
                            
                        if (stateListener != null) {
                            mainHandler.post(() -> 
                                stateListener.onDisplayRegistered(displayId)
                            );
                        }
                        
                        startDisplayLoop();
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to process registration response", e);
                        setStatus(3); // Error
                        scheduleRetry(XiboDisplayManager.this::registerDisplay, RETRY_INTERVAL);
                        
                        if (stateListener != null) {
                            mainHandler.post(() -> 
                                stateListener.onDisplayError("Registration failed: " + e.getMessage())
                            );
                        }
                    }
                }
            });
            
        } catch (JSONException e) {
            Log.e(TAG, "Failed to create registration data", e);
            setStatus(3); // Error
        }
    }
    
    private void startDisplayLoop() {
        updateStatus();
        checkSchedule();
    }
    
    private void updateStatus() {
        try {
            JSONObject statusData = new JSONObject()
                .put("displayId", displayId)
                .put("hardwareKey", hardwareKey)
                .put("currentLayoutId", currentLayoutId != null ? currentLayoutId : "")
                .put("status", currentStatus)
                .put("macAddress", "00:00:00:00:00:00") // Placeholder
                .put("clientVersion", "1.0")
                .put("clientCode", 1);
            
            RequestBody body = RequestBody.create(
                MediaType.parse("application/json"),
                statusData.toString()
            );
            
            Request request = new Request.Builder()
                .url(cmsUrl + ENDPOINT_STATUS)
                .post(body)
                .addHeader("X-Display-Key", hardwareKey)
                .build();
                
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.w(TAG, "Failed to update status", e);
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        Log.w(TAG, "Status update failed: " + response.code());
                    }
                }
            });
            
        } catch (JSONException e) {
            Log.e(TAG, "Failed to create status data", e);
        }
        
        mainHandler.postDelayed(this::updateStatus, STATUS_UPDATE_INTERVAL);
    }
    
    private void checkSchedule() {
        Request request = new Request.Builder()
            .url(String.format("%s%s/%s", cmsUrl, ENDPOINT_SCHEDULE, displayId))
            .addHeader("X-Display-Key", hardwareKey)
            .build();
            
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.w(TAG, "Failed to check schedule", e);
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        throw new IOException("Schedule check failed: " + response.code());
                    }
                    
                    String responseBody = response.body().string();
                    JSONObject json = new JSONObject(responseBody);
                    
                    String newLayoutId = json.optString("layoutId", null);
                    if (newLayoutId != null && !newLayoutId.equals(currentLayoutId)) {
                        currentLayoutId = newLayoutId;
                        prefs.edit().putString("current_layout_id", currentLayoutId).apply();
                        
                        if (stateListener != null) {
                            mainHandler.post(() -> 
                                stateListener.onLayoutChanged(currentLayoutId)
                            );
                        }
                    }
                    
                } catch (Exception e) {
                    Log.e(TAG, "Failed to process schedule response", e);
                }
            }
        });
        
        mainHandler.postDelayed(this::checkSchedule, SCHEDULE_CHECK_INTERVAL);
    }
    
    private void scheduleRetry(Runnable task, long delay) {
        mainHandler.postDelayed(task, delay);
    }
    
    // Public getters
    public boolean isRegistered() {
        return isRegistered;
    }
    
    public String getDisplayId() {
        return displayId;
    }
    
    public String getHardwareKey() {
        return hardwareKey;
    }
    
    public String getCurrentLayoutId() {
        return currentLayoutId;
    }
    
    public int getCurrentStatus() {
        return currentStatus;
    }
}
