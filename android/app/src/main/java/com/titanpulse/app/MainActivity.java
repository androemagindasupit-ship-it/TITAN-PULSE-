package com.titanpulse.app;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.content.pm.PackageManager;
import android.os.Bundle;
import java.util.UUID;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.OnBackPressedCallback;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private TitanPulseBridge titanPulseBridge;
    private boolean configured = false;
    private String pendingDeepLinkScript = "";
    private OnBackPressedCallback backCallback;
    private final String bridgeToken = UUID.randomUUID().toString();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        CrashReporter.install(this);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        configureWebView();
        handleIntent(getIntent());
    }

    @Override public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override public void onStart() {
        super.onStart();
        if (titanPulseBridge != null) titanPulseBridge.setAppForegroundValue("1");
        com.titanpulse.app.sync.BackgroundSyncWorker.enqueuePeriodic(this);
        com.titanpulse.app.sync.BackgroundSyncWorker.enqueueNow(this);
    }

    @Override public void onPause() {
        unregisterBackHandler();
        super.onPause();
    }

    @Override public void onStop() {
        if (titanPulseBridge != null) titanPulseBridge.setAppForegroundValue("0");
        super.onStop();
    }

    @Override public void onResume() {
        super.onResume();
        configureWebView();
        if (titanPulseBridge != null) titanPulseBridge.setAppForegroundValue("1");
        registerBackHandler();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 731 && titanPulseBridge != null && grantResults != null && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            titanPulseBridge.flushQueuedNotifications();
        }
    }

    @Override public void onDestroy() {
        if (titanPulseBridge != null) titanPulseBridge.shutdown();
        titanPulseBridge = null;
        configured = false;
        super.onDestroy();
    }

    private void registerBackHandler() {
        if (backCallback == null) {
            backCallback = new OnBackPressedCallback(true) {
                @Override public void handleOnBackPressed() {
                    WebView w = getBridge() != null ? getBridge().getWebView() : null;
                    if (w != null && w.canGoBack()) w.goBack();
                    else { setEnabled(false); getOnBackPressedDispatcher().onBackPressed(); }
                }
            };
            getOnBackPressedDispatcher().addCallback(this, backCallback);
        }
        backCallback.setEnabled(true);
    }

    private void unregisterBackHandler() { if (backCallback != null) backCallback.setEnabled(false); }

    private void configureWebView() {
        if (configured || getBridge() == null || getBridge().getWebView() == null) return;
        WebView w = getBridge().getWebView();
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(false);
        if (Build.VERSION.SDK_INT >= 16) {
            s.setAllowFileAccessFromFileURLs(false);
            s.setAllowUniversalAccessFromFileURLs(false);
        }
        if (Build.VERSION.SDK_INT >= 21) s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setLoadWithOverviewMode(false);
        s.setUseWideViewPort(false);
        if (Build.VERSION.SDK_INT >= 29) s.setForceDark(WebSettings.FORCE_DARK_OFF);
        if (Build.VERSION.SDK_INT >= 19) WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);

        w.setBackgroundColor(Color.TRANSPARENT);
        w.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleNavigation(request == null ? null : request.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleNavigation(url == null ? null : Uri.parse(url));
            }
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.evaluateJavascript("window.__TITAN_BRIDGE_TOKEN=" + org.json.JSONObject.quote(bridgeToken) + ";void 0", null);
                if (!pendingDeepLinkScript.isEmpty()) {
                    String js = pendingDeepLinkScript;
                    pendingDeepLinkScript = "";
                    view.evaluateJavascript(js, null);
                }
            }
        });
        titanPulseBridge = new TitanPulseBridge(this, bridgeToken);
        w.addJavascriptInterface(titanPulseBridge, "AndroidBridge");
        configured = true;
    }

    private boolean handleNavigation(Uri uri) {
        if (uri == null) return true;
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        boolean local = ("https".equals(scheme) || "http".equals(scheme)) && "localhost".equals(host);
        if (local) return false;
        if ("http".equals(scheme) || "https".equals(scheme)) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) {}
        }
        return true;
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String jobId = intent.getStringExtra("jobId");
        String projectId = intent.getStringExtra("projectId");
        if (jobId == null && projectId == null) return;
        org.json.JSONObject o = new org.json.JSONObject();
        try { o.put("jobId", jobId == null ? "" : jobId).put("projectId", projectId == null ? "" : projectId); } catch (Exception ignored) {}
        final String script = "window.handleNativeDeepLink && window.handleNativeDeepLink(" + JSONObjectQuote(o.toString()) + ");";
        pendingDeepLinkScript = script;
        if (getBridge() != null && getBridge().getWebView() != null) {
            getBridge().getWebView().postDelayed(() -> {
                if (getBridge() != null && getBridge().getWebView() != null && getBridge().getWebView().getUrl() != null) {
                    getBridge().getWebView().evaluateJavascript(script, null);
                    pendingDeepLinkScript = "";
                }
            }, 700);
        }
    }

    private static String JSONObjectQuote(String s) { return org.json.JSONObject.quote(s == null ? "" : s); }
}
