package com.example.weboverlay;

// This empty change is to ensure the package declaration is at the top of the file

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * XiboWebClient extends WebViewClient to handle Xibo CMS integration.
 * This class manages authentication, layout loading, error handling, and connection monitoring.
 */
public class XiboWebClient extends WebViewClient {
    private static final String TAG = "XiboWebClient";
    private static final String PREF_NAME = "XiboPrefs";
    private static final String PREF_TOKEN = "access_token";
    private static final String PREF_REFRESH_TOKEN = "refresh_token";
    private static final String PREF_TOKEN_EXPIRY = "token_expiry";
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 5000; // 5 seconds

    private Context context;
    private WebView webView;
    private String cmsUrl;
    private String clientId;
    private String clientSecret;
    private String username;
    private String password;
    private int currentRetryCount = 0;
    private String currentLayoutId = null;
    private boolean isConnected = false;
    private Handler connectionHandler = new Handler();
    private Runnable connectionChecker;
    private boolean allowSelfSignedCertificates = false;

    // Interface for communicating with the parent activity
    public interface XiboClientListener {
        void onAuthSuccess();
        void onAuthFailure(String reason);
        void onLayoutLoaded(String layoutId);
        void onLayoutLoadFailed(String layoutId, String reason);
        void onConnectionStateChanged(boolean connected);
    }

    private XiboClientListener listener;

    /**
     * Constructor for XiboWebClient.
     * 
     * @param context Application context
     * @param webView The WebView instance to be controlled
     * @param cmsUrl Base URL of the Xibo CMS
     * @param clientId OAuth client ID
     * @param clientSecret OAuth client secret
     * @param username CMS username
     * @param password CMS password
     * @param listener Callback listener for client events
     */
    public XiboWebClient(Context context, WebView webView, String cmsUrl, 
                         String clientId, String clientSecret, 
                         String username, String password,
                         XiboClientListener listener) {
        this.context = context;
        this.webView = webView;
        this.cmsUrl = cmsUrl.endsWith("/") ? cmsUrl : cmsUrl + "/";
        // Validate URL format
        if (!this.cmsUrl.startsWith("http://") && !this.cmsUrl.startsWith("https://")) {
            this.cmsUrl = "https://" + this.cmsUrl;
            Log.w(TAG, "URL protocol not specified, defaulting to HTTPS: " + this.cmsUrl);
        }
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.username = username;
        this.password = password;
        this.listener = listener;
        
        // Setup JavaScript interface
        webView.addJavascriptInterface(new XiboJavaScriptInterface(), "XiboAndroid");
        
        // Configure WebView for Xibo CMS
        configureWebView();
        
        // Start connection monitoring
        startConnectionMonitoring();
    }

    /**
     * Configure WebView settings optimized for Xibo CMS display
     */
    private void configureWebView() {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
    }

    /**
     * Enable or disable acceptance of self-signed certificates
     * Should only be used for development/testing
     */
    public void setAllowSelfSignedCertificates(boolean allow) {
        this.allowSelfSignedCertificates = allow;
        Log.w(TAG, "Self-signed certificates " + (allow ? "allowed" : "not allowed"));
    }

    /**
     * Start the authentication process with Xibo CMS
     * Uses OAuth2 for secure authentication
     */
    public void authenticate() {
        // Check if we have a valid token already
        if (hasValidToken()) {
            if (listener != null) {
                listener.onAuthSuccess();
            }
            return;
        }

        OkHttpClient client = createSecureOkHttpClient();

        RequestBody formBody = new FormBody.Builder()
                .add("grant_type", "password")
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("username", username)
                .add("password", password)
                .build();

        String authUrl = cmsUrl + "api/oauth/access_token";
        Log.d(TAG, "Authenticating with URL: " + authUrl);

        Request request = new Request.Builder()
                .url(authUrl)
                .post(formBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Authentication failed: " + e.getMessage(), e);
                String errorMessage = "Network error: " + e.getMessage();
                if (e.getMessage() != null && e.getMessage().contains("Trust anchor for certification path not found")) {
                    errorMessage = "SSL Certificate error. The server's SSL certificate is not trusted.";
                    Log.e(TAG, "SSL Certificate validation failed. Consider using a valid certificate or enabling self-signed certificates for testing.");
                }
                if (listener != null) {
                    listener.onAuthFailure(errorMessage);
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (response.isSuccessful()) {
                        String responseData = response.body().string();
                        Log.d(TAG, "Auth response received, status: " + response.code());
                        JSONObject jsonObject = new JSONObject(responseData);
                        
                        // Save tokens to SharedPreferences
                        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = prefs.edit();
                        
                        String accessToken = jsonObject.getString("access_token");
                        String refreshToken = jsonObject.getString("refresh_token");
                        long expiresIn = jsonObject.getLong("expires_in");
                        long expiryTime = System.currentTimeMillis() + (expiresIn * 1000);
                        
                        editor.putString(PREF_TOKEN, accessToken);
                        editor.putString(PREF_REFRESH_TOKEN, refreshToken);
                        editor.putLong(PREF_TOKEN_EXPIRY, expiryTime);
                        editor.apply();
                        
                        if (listener != null) {
                            listener.onAuthSuccess();
                        }
                    } else {
                        String responseBody = response.body() != null ? response.body().string() : "No response body";
                        String errorMessage = "Auth failed with code: " + response.code();
                        Log.e(TAG, errorMessage + ", Response: " + responseBody);
                        
                        // Try to extract more detailed error information
                        try {
                            JSONObject errorJson = new JSONObject(responseBody);
                            if (errorJson.has("error") || errorJson.has("message")) {
                                String detailedError = errorJson.optString("error", "") + ": " + 
                                                      errorJson.optString("message", "");
                                errorMessage += " - " + detailedError;
                            }
                        } catch (JSONException e) {
                            // Not JSON or doesn't have expected fields, use the raw response
                            if (!responseBody.isEmpty() && responseBody.length() < 100) {
                                errorMessage += " - " + responseBody;
                            }
                        }
                        
                        if (listener != null) {
                            listener.onAuthFailure(errorMessage);
                        }
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "JSON parsing error", e);
                    if (listener != null) {
                        listener.onAuthFailure("Failed to parse response: " + e.getMessage());
                    }
                }
            }
        });
    }

    /**
     * Check if we have a valid access token
     */
    private boolean hasValidToken() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String token = prefs.getString(PREF_TOKEN, null);
        long expiry = prefs.getLong(PREF_TOKEN_EXPIRY, 0);
        
        // Add a 60-second buffer to ensure token doesn't expire during use
        return (token != null && System.currentTimeMillis() < (expiry - 60000));
    }

    /**
     * Refresh the access token using the refresh token
     */
    private void refreshToken() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String refreshToken = prefs.getString(PREF_REFRESH_TOKEN, null);
        
        if (refreshToken == null) {
            if (listener != null) {
                listener.onAuthFailure("No refresh token available");
            }
            return;
        }
        
        OkHttpClient client = createSecureOkHttpClient();

        RequestBody formBody = new FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("refresh_token", refreshToken)
                .build();

        Request request = new Request.Builder()
                .url(cmsUrl + "api/oauth/access_token")
                .post(formBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Token refresh failed: " + e.getMessage(), e);
                
                String errorMessage = "Network error during token refresh: " + e.getMessage();
                if (e.getMessage() != null && e.getMessage().contains("Trust anchor for certification path not found")) {
                    errorMessage = "SSL Certificate error during token refresh. The server's SSL certificate is not trusted.";
                    Log.e(TAG, "SSL Certificate validation failed during token refresh.");
                }
                
                // If refresh fails, try full authentication
                authenticate();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (response.isSuccessful()) {
                        String responseData = response.body().string();
                        JSONObject jsonObject = new JSONObject(responseData);
                        
                        // Save tokens to SharedPreferences
                        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = prefs.edit();
                        
                        String accessToken = jsonObject.getString("access_token");
                        String newRefreshToken = jsonObject.getString("refresh_token");
                        long expiresIn = jsonObject.getLong("expires_in");
                        long expiryTime = System.currentTimeMillis() + (expiresIn * 1000);
                        
                        editor.putString(PREF_TOKEN, accessToken);
                        editor.putString(PREF_REFRESH_TOKEN, newRefreshToken);
                        editor.putLong(PREF_TOKEN_EXPIRY, expiryTime);
                        editor.apply();
                        
                        // If we were trying to load a layout, retry now
                        if (currentLayoutId != null) {
                            loadLayout(currentLayoutId);
                        }
                    } else {
                        // If refresh fails, try full authentication
                        authenticate();
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "JSON parsing error during token refresh", e);
                    authenticate();
                }
            }
        });
    }

    /**
     * Load a layout from Xibo CMS
     * 
     * @param layoutId The ID of the layout to load
     */
public void loadLayout(String layoutId) {
    currentLayoutId = layoutId;
    currentRetryCount = 0;
    
    if (!hasValidToken()) {
        Log.d(TAG, "No valid token for loading layout, refreshing token first");
        refreshToken();
        return;
    }
    
    SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    String token = prefs.getString(PREF_TOKEN, null);
    
    try {
        // URL encode parameters to prevent special character issues
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8.toString());
        String encodedLayoutId = URLEncoder.encode(layoutId, StandardCharsets.UTF_8.toString());
        
        // Use the correct API endpoint format with properly encoded parameters
        final String layoutUrl = cmsUrl + "api/layout/render/" + encodedLayoutId + "?preview=1&token=" + encodedToken;
        
        // Log the URL for debugging (mask token for security)
        String maskedUrl = layoutUrl.replaceAll("token=[^&]*", "token=XXXXX");
        Log.d(TAG, "Loading layout with URL: " + maskedUrl);
        
        // Load the layout in the WebView
        webView.post(() -> {
            webView.loadUrl(layoutUrl);
            Log.d(TAG, "Loading layout: " + layoutId);
        });
    } catch (Exception e) {
        Log.e(TAG, "Error encoding layout URL parameters", e);
        if (listener != null) {
            listener.onLayoutLoadFailed(layoutId, "URL encoding error: " + e.getMessage());
        }
    }
}

    /**
     * Retry loading the current layout after a delay
     */
    private void retryLoadingLayout() {
        if (currentLayoutId == null) {
            return;
        }
        
        if (currentRetryCount < MAX_RETRIES) {
            currentRetryCount++;
            Log.d(TAG, "Retry " + currentRetryCount + " for layout " + currentLayoutId);
            
            new Handler().postDelayed(() -> loadLayout(currentLayoutId), RETRY_DELAY_MS);
        } else {
            Log.e(TAG, "Max retries reached for layout " + currentLayoutId);
            if (listener != null) {
                listener.onLayoutLoadFailed(currentLayoutId, "Max retries reached");
            }
            currentRetryCount = 0;
        }
    }

    /**
     * Start monitoring the network connection status
     */
    private void startConnectionMonitoring() {
        connectionChecker = new Runnable() {
            @Override
            public void run() {
                boolean newConnectionState = isNetworkAvailable();
                if (isConnected != newConnectionState) {
                    isConnected = newConnectionState;
                    if (listener != null) {
                        listener.onConnectionStateChanged(isConnected);
                    }
                    
                    // If connection was restored and we have a layout, try loading it
                    if (isConnected && currentLayoutId != null) {
                        loadLayout(currentLayoutId);
                    }
                }
                
                // Schedule the next check
                connectionHandler.postDelayed(this, 30000); // Check every 30 seconds
            }
        };
        
        // Start checking immediately
        connectionHandler.post(connectionChecker);
    }
    
    /**
     * Stop connection monitoring
     */
    public void stopConnectionMonitoring() {
        connectionHandler.removeCallbacks(connectionChecker);
    }
    
    /**
     * Check if network is available
     */
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) 
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        }
        return false;
    }

    /**
     * Clean up resources when the client is no longer needed
     */
    public void cleanup() {
        stopConnectionMonitoring();
        webView.removeJavascriptInterface("XiboAndroid");
    }

    // WebViewClient overrides

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        Log.d(TAG, "Page loading started: " + url);
        super.onPageStarted(view, url, favicon);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        Log.d(TAG, "Page loading finished: " + url);
        super.onPageFinished(view, url);
        
        // If this is a layout URL, notify the listener
        if (url.contains("/layout/preview/") && listener != null) {
            listener.onLayoutLoaded(currentLayoutId);
        }
        
        // Inject any custom JavaScript if needed
        injectCustomJavaScript();
    }

    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        super.onReceivedError(view, request, error);
        
        // Only handle main frame errors
        if (request.isForMainFrame()) {
            String errorDescription;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                errorDescription = "Error code: " + error.getErrorCode() + ", Description: " + error.getDescription();
            } else {
                errorDescription = "Error loading page";
            }
            Log.e(TAG, "Error loading page: " + errorDescription + ", URL: " + request.getUrl());
            
            // If this was a layout loading error, retry
            if (request.getUrl().toString().contains("/layout/preview/")) {
                retryLoadingLayout();
                retryLoadingLayout();
            }
        }
    }

    /**
     * Creates a secure OkHttpClient with proper SSL/TLS configuration
     */
    private OkHttpClient createSecureOkHttpClient() {
    OkHttpClient.Builder builder = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS);
    
    if (allowSelfSignedCertificates) {
        try {
            // Create a trust manager that accepts all certificates
            // WARNING: This is insecure and should only be used for development/testing
            final TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                    }

                    @Override
                    public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                    }

                    @Override
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new java.security.cert.X509Certificate[]{};
                    }
                }
            };

            // Install the all-trusting trust manager
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            
            // Create an SSL socket factory with our all-trusting manager
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true; // Accept all hostnames
                }
            });
            
            Log.w(TAG, "Using insecure SSL configuration that accepts all certificates. NOT FOR PRODUCTION USE!");
        } catch (Exception e) {
            Log.e(TAG, "Error setting up SSL configuration", e);
        }
    }
    
    return builder.build();
}

    /**
     * Inject custom JavaScript into the page
     */
    private void injectCustomJavaScript() {
        // Example: inject code to handle transparent background
        String js = "document.body.style.backgroundColor = 'transparent';" +
                    "document.documentElement.style.backgroundColor = 'transparent';";
        webView.evaluateJavascript(js, null);
    }
    
    /**
     * JavaScript interface for communication between WebView and Android code
     */
    private class XiboJavaScriptInterface {
        
        @JavascriptInterface
        public void layoutEvent(String eventType, String data) {
            Log.d(TAG, "Layout event: " + eventType + " - " + data);
            // Handle various events from the layout (can be expanded based on needs)
        }
        
        @JavascriptInterface
        public void reportError(String errorMessage) {
            Log.e(TAG, "JS Error: " + errorMessage);
            // Can be used by layouts to report errors to Android
        }
        
        @JavascriptInterface
        public String getDeviceInfo() {
            // Return device info as JSON
            try {
                JSONObject info = new JSONObject();
                info.put("model", android.os.Build.MODEL);
                info.put("manufacturer", android.os.Build.MANUFACTURER);
                info.put("os", "Android " + android.os.Build.VERSION.RELEASE);
                info.put("appVersion", "1.0.0"); // Replace with actual version
                return info.toString();
            } catch (JSONException e) {
                Log.e(TAG, "Error creating device info", e);
                return "{}";
            }
        }
        
        @JavascriptInterface
        public void setLayerVisibility(String layerId, boolean visible) {
            // Can be used to control visibility of overlay elements
            Log.d(TAG, "Set layer " + layerId + " visibility to " + visible);
            // Implement layer visibility logic if needed
        }
    }
}

