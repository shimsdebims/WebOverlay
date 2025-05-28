package com.example.weboverlay;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.util.Log;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import android.os.Handler;
import android.os.Looper;

public class XiboWebClient extends WebViewClient {
    private static final String TAG = "XiboWebClient";
    private Context context;
    private WebView webView;
    private String cmsUrl;
    private String currentLayoutId;
    private XiboClientListener listener;

    public interface XiboClientListener {
        void onLayoutLoaded(String layoutId);
        void onLayoutLoadFailed(String layoutId, String reason);
    }

    public XiboWebClient(Context context, WebView webView, String cmsUrl, XiboClientListener listener) {
        this.context = context;
        this.webView = webView;
        this.cmsUrl = cmsUrl;
        this.listener = listener;

        if (!this.cmsUrl.startsWith("http://") && !this.cmsUrl.startsWith("https://")) {
            this.cmsUrl = "http://" + this.cmsUrl;
        }
        
        configureWebView();
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null);
    }

    public void loadLayout(String layoutId) {
        this.currentLayoutId = layoutId;
        
        // Use the correct preview URL format for Xibo CMS 3.3.3
        String previewUrl = String.format("%s/playlist/preview/%s?preview=1&isPreview=1", cmsUrl, layoutId);
        Log.d(TAG, "Loading layout: " + previewUrl);
        
        new Handler(Looper.getMainLooper()).post(() -> {
            webView.clearCache(true);
            webView.clearHistory();
            webView.loadUrl(previewUrl);
        });
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        super.onPageStarted(view, url, favicon);
        Log.d(TAG, "Starting to load layout page: " + url);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        Log.d(TAG, "Finished loading layout page: " + url);
        
        // Make background transparent
        String js = "document.body.style.backgroundColor = 'transparent';" +
                   "document.documentElement.style.backgroundColor = 'transparent';";
        view.evaluateJavascript(js, null);
        
        if (listener != null && currentLayoutId != null) {
            listener.onLayoutLoaded(currentLayoutId);
        }
    }

    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        super.onReceivedError(view, request, error);
        
        if (request.isForMainFrame() && listener != null && currentLayoutId != null) {
            String errorDescription = "Error loading layout";
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                errorDescription = error.getDescription().toString();
            }
            listener.onLayoutLoadFailed(currentLayoutId, errorDescription);
            loadFallbackContent();
        }
    }

    private void loadFallbackContent() {
        new Handler(Looper.getMainLooper()).post(() -> {
            String html = "<html><body style='background: transparent;'>" +
                         "<h2 style='color: white;'>Basic Overlay Mode</h2>" +
                         "<p style='color: white;'>Xibo content unavailable</p>" +
                         "</body></html>";
            webView.loadData(html, "text/html", "UTF-8");
        });
    }
}
