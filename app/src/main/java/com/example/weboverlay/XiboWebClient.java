package com.example.weboverlay;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.JavascriptInterface;
import android.os.Handler;
import android.os.Looper;
import android.widget.FrameLayout;

import androidx.webkit.WebViewClientCompat;

public class XiboWebClient extends WebViewClientCompat {
    private static final String TAG = "XiboWebClient";
    
    // Animation duration in milliseconds
    private static final long TRANSITION_DURATION = 800;
    
    private final Context context;
    private final String cmsUrl;
    private final XiboClientListener listener;
    private final Handler mainHandler;
    
    // WebView references
    private WebView primaryWebView;
    private WebView secondaryWebView;
    private WebView activeWebView;
    private WebView inactiveWebView;
    private WebView webView;
    
    private String currentLayoutId = null;
    private boolean isTransitioning = false;
    
    public interface XiboClientListener {
        void onLayoutLoaded(String layoutId);
        void onLayoutLoadFailed(String layoutId, String reason);
        void onTransitionStart(String fromLayoutId, String toLayoutId);
        void onTransitionComplete(String layoutId);
    }
    
    public XiboWebClient(Context context, WebView primaryWebView, WebView secondaryWebView, 
                        String cmsUrl, XiboClientListener listener) {
        this.context = context;
        this.cmsUrl = cmsUrl;
        this.listener = listener;
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        this.primaryWebView = primaryWebView;
        this.secondaryWebView = secondaryWebView;
        this.activeWebView = primaryWebView;
        this.inactiveWebView = secondaryWebView;
        
        setupWebViews();
    }

    public XiboWebClient(Context context, WebView webView) {
        this.context = context;
        this.webView = webView;

        configureWebView(webView);
    }
    
    private void setupWebViews() {
        // Configure both WebViews
        configureWebView(primaryWebView);
        configureWebView(secondaryWebView);
    }
    
    private void configureWebView(WebView webView) {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowContentAccess(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setWebViewClient(this);
        webView.addJavascriptInterface(new XiboJavaScriptInterface(), "AndroidXibo");
    }
    
    public void loadLayout(String layoutId) {
        if (layoutId == null || layoutId.isEmpty()) {
            if (listener != null) {
                listener.onLayoutLoadFailed("null", "Invalid layout ID");
            }
            return;
        }
        
        // Don't start new transition if one is in progress
        if (isTransitioning) {
            Log.w(TAG, "Layout transition already in progress, queuing: " + layoutId);
            mainHandler.postDelayed(() -> loadLayout(layoutId), TRANSITION_DURATION);
            return;
        }
        
        final String oldLayoutId = currentLayoutId;
        currentLayoutId = layoutId;
        
        String layoutUrl = String.format("%s/layout/render/%s", cmsUrl, layoutId);
        
        // Start loading in the inactive WebView
        mainHandler.post(() -> {
            // Inject required display parameters
            String javascript = String.format(
                "window.xiboDisplay = {" +
                "   displayId: '%s'," +
                "   hardwareKey: '%s'," +
                "   layoutId: '%s'" +
                "};",
                Constants.XIBO_DISPLAY_KEY,
                Constants.XIBO_DISPLAY_KEY,
                layoutId
            );
            
            inactiveWebView.evaluateJavascript(javascript, null);
            inactiveWebView.loadUrl(layoutUrl);
            
            if (listener != null) {
                listener.onTransitionStart(oldLayoutId, layoutId);
            }
        });
    }

    public void loadDisplayContent() {
        String displayUrl = Constants.XIBO_DISPLAY_URL;
        Log.d(TAG, "Loading display URL: " + displayUrl);
        webView.loadUrl(displayUrl);
    }
    
    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        // Add display authentication headers to all requests to CMS
        if (request.getUrl().toString().startsWith(cmsUrl)) {
            try {
                okhttp3.Request.Builder builder = new okhttp3.Request.Builder()
                    .url(request.getUrl().toString())
                    .addHeader("X-Display-Key", Constants.XIBO_DISPLAY_KEY);
                
                // Copy original request headers
                for (String header : request.getRequestHeaders().keySet()) {
                    builder.addHeader(header, request.getRequestHeaders().get(header));
                }
                
                okhttp3.Request okRequest = builder.build();
                okhttp3.Response response = new okhttp3.OkHttpClient().newCall(okRequest).execute();
                
                return new WebResourceResponse(
                    response.header("Content-Type", "text/html"),
                    response.header("Content-Encoding", "utf-8"),
                    response.body().byteStream()
                );
            } catch (Exception e) {
                Log.e(TAG, "Failed to intercept request", e);
            }
        }
        return super.shouldInterceptRequest(view, request);
    }
    
    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        
        if (url.contains("/layout/render/") && view == inactiveWebView) {
            // Inject layout-specific JavaScript
            String javascript = 
                "javascript:(function() {" +
                "   window.xiboIC = window.xiboIC || {};" +
                "   window.xiboIC.ready = true;" +
                "   window.xiboIC.layoutComplete = function() {" +
                "       AndroidXibo.onLayoutReady();" +
                "   };" +
                "})()";
            
            view.evaluateJavascript(javascript, null);
        }
    }
    
    private void startTransition() {
        isTransitioning = true;
        
        // Make inactive WebView visible but transparent
        inactiveWebView.setVisibility(View.VISIBLE);
        inactiveWebView.setAlpha(0f);
        
        // Fade in inactive WebView
        inactiveWebView.animate()
            .alpha(1f)
            .setDuration(TRANSITION_DURATION)
            .setListener(null);
            
        // Fade out active WebView
        activeWebView.animate()
            .alpha(0f)
            .setDuration(TRANSITION_DURATION)
            .setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    // Swap WebViews
                    activeWebView.setVisibility(View.INVISIBLE);
                    WebView temp = activeWebView;
                    activeWebView = inactiveWebView;
                    inactiveWebView = temp;
                    
                    // Clear old content
                    inactiveWebView.loadUrl("about:blank");
                    
                    isTransitioning = false;
                    
                    if (listener != null) {
                        listener.onTransitionComplete(currentLayoutId);
                    }
                }
            });
    }
    
    @Override
    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
        super.onReceivedError(view, errorCode, description, failingUrl);
        if (listener != null) {
            listener.onLayoutLoadFailed(currentLayoutId, description);
        }
    }
    
    private class XiboJavaScriptInterface {
        @JavascriptInterface
        public void onLayoutReady() {
            mainHandler.post(() -> {
                if (listener != null) {
                    listener.onLayoutLoaded(currentLayoutId);
                }
                startTransition();
            });
        }
        
        @JavascriptInterface
        public void onLayoutError(String error) {
            mainHandler.post(() -> {
                if (listener != null) {
                    listener.onLayoutLoadFailed(currentLayoutId, error);
                }
            });
        }

        @JavascriptInterface
        public void onError(String error) {
            Log.e(TAG, "Error reported from layout: " + error);
        }
    }
}
