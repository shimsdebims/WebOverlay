package com.example.weboverlay;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;

public class XiboWebClient extends WebViewClient {
    private static final String TAG = "XiboWebClient";
    private final Context context;
    private final WebView webView;

    public XiboWebClient(Context context, WebView webView) {
        this.context = context;
        this.webView = webView;
        configureWebView();
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null);
        webView.setWebViewClient(this);
        
        // Enable console logging
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d(TAG, "Console: " + consoleMessage.message());
                return true;
            }
        });
    }

    public void loadContent() {
        Log.d(TAG, "Loading Xibo content from: " + Constants.XIBO_EMBED_URL);
        webView.loadUrl(Constants.XIBO_EMBED_URL);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        Log.d(TAG, "Page load finished: " + url);
        
        // Ensure transparency
        String js = "document.body.style.backgroundColor = 'transparent';" +
                   "document.documentElement.style.backgroundColor = 'transparent';";
        view.evaluateJavascript(js, null);
    }

    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        super.onReceivedError(view, request, error);
        Log.e(TAG, "Error loading content: " + error.getDescription());
        loadFallbackContent();
    }

    private void loadFallbackContent() {
        String html = "<html><body style='background: transparent;'>" +
                     "<h2 style='color: white;'>Xibo Content Unavailable</h2>" +
                     "<p style='color: white;'>Please check your connection and settings.</p>" +
                     "</body></html>";
        webView.loadData(html, "text/html", "UTF-8");
    }
}
