package com.example.weboverlay;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;

public class XiboWebClient {
    private static final String TAG = "XiboWebClient";
    private static final String DISPLAY_URL_FORMAT = "%s/display/embed/%s";
    private WebView webView;
    private String cmsUrl;
    private String displayKey;
    private Context context;

    public XiboWebClient(Context context, WebView webView, String cmsUrl, String displayKey) {
        this.context = context;
        this.webView = webView;
        this.cmsUrl = cmsUrl;
        this.displayKey = displayKey;

        setupWebView();
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        // Critical for transparency
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // Inject transparency CSS
                String css = "javascript:(function() {" +
                        "document.body.style.backgroundColor = 'transparent';" +
                        "var style = document.createElement('style');" +
                        "style.innerHTML = 'body { background: transparent !important; }';" +
                        "document.head.appendChild(style);" +
                        "})()";
                view.evaluateJavascript(css, null);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                Log.e(TAG, "Error loading: " + error.getDescription());
                // Implement retry logic here if needed
            }
        });
    }

    public void loadContent() {
        String displayUrl = String.format(DISPLAY_URL_FORMAT, cmsUrl, displayKey);
        Log.d(TAG, "Loading URL: " + displayUrl);
        webView.loadUrl(displayUrl);
    }

    public void refreshContent() {
        webView.reload();
    }
}
