package kr.scholarship.hunter;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** 웹앱(assets/www)을 https://app.hunter.local/ 주소로 띄우는 WebView 셸. 외부 링크는 브라우저로 연다. */
public class MainActivity extends Activity {
    static final String HOST = "app.hunter.local";
    static final String ORIGIN = "https://" + HOST + "/";
    private WebView web;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(Color.parseColor("#2d6a3e"));
        web = new WebView(this);
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.addView(web, new android.widget.FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setSupportMultipleWindows(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setGeolocationEnabled(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSaveFormData(false);
        s.setTextZoom(100);
        s.setSupportZoom(true);            // 두 손가락으로 벌려 확대·축소
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);   // 화면에 +/- 버튼은 띄우지 않는다
        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (HOST.equals(u.getHost()) && "https".equals(u.getScheme())) return false;
                // 외부 링크는 http/https 만 기본 브라우저로 연다 (intent:, file:, javascript: 등 차단)
                String sc = u.getScheme();
                if ("http".equals(sc) || "https".equals(sc)) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, u).addCategory(Intent.CATEGORY_BROWSABLE)); } catch (Exception ignored) { }
                }
                return true;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (!HOST.equals(u.getHost())) return null;
                String path = u.getPath();
                if (path == null || path.equals("/") || path.isEmpty()) path = "/index.html";
                if (path.contains("..")) return new WebResourceResponse("text/plain", "utf-8", 403, "Forbidden", new HashMap<String, String>(), null);
                try {
                    AssetManager am = getAssets();
                    InputStream in = am.open("www" + path);
                    Map<String, String> h = new HashMap<>();
                    h.put("Access-Control-Allow-Origin", "*");
                    h.put("Cache-Control", "no-cache");
                    return new WebResourceResponse(mime(path), "utf-8", 200, "OK", h, in);
                } catch (Exception e) {
                    return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", new HashMap<String, String>(), null);
                }
            }
        });
        if (saved != null) web.restoreState(saved); else web.loadUrl(ORIGIN + "index.html");
        try { AlertJob.schedule(this); } catch (Exception ignored) { }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
    }

    static String mime(String p) {
        if (p.endsWith(".html")) return "text/html";
        if (p.endsWith(".json")) return "application/json";
        if (p.endsWith(".js")) return "application/javascript";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".webmanifest")) return "application/manifest+json";
        return "application/octet-stream";
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration c) {
        super.onConfigurationChanged(c);
        if (web != null) web.evaluateJavascript("window.__sysTheme&&window.__sysTheme()", null);
    }

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.__back&&window.__back()", new android.webkit.ValueCallback<String>() {
            @Override public void onReceiveValue(String r) {
                if (!"true".equals(r)) { if (web.canGoBack()) web.goBack(); else finish(); }
            }
        });
    }

    /** 페이지가 호출: 관심 장학금 중 마감 임박 항목을 알림으로 띄운다. */
    class Bridge {
        @JavascriptInterface
        public void notifyDeadlines(String json) {
            try {
                JSONArray arr = new JSONArray(json);
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (Build.VERSION.SDK_INT >= 26) {
                    nm.createNotificationChannel(new NotificationChannel("deadline", "장학 마감 알림", NotificationManager.IMPORTANCE_DEFAULT));
                }
                PendingIntent pi = PendingIntent.getActivity(MainActivity.this, 0, new Intent(MainActivity.this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
                for (int i = 0; i < arr.length() && i < 5; i++) {
                    JSONObject o = arr.getJSONObject(i);
                    Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(MainActivity.this, "deadline") : new Notification.Builder(MainActivity.this);
                    b.setSmallIcon(android.R.drawable.ic_popup_reminder)
                     .setContentTitle(o.optString("label") + " · " + o.optString("title"))
                     .setContentText(o.optString("org"))
                     .setAutoCancel(true).setContentIntent(pi);
                    nm.notify(o.optString("id").hashCode(), b.build());
                }
            } catch (Exception ignored) { }
        }

        @JavascriptInterface
        public String platform() { return "android"; }

        /** 휴대폰이 어두운 화면(다크 모드)인지. */
        @JavascriptInterface
        public boolean isDark() {
            return (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        }

        /** 페이지 밝기에 맞춰 상단 상태 표시줄 색을 바꾼다. */
        @JavascriptInterface
        public void setDark(final boolean dark) {
            runOnUiThread(new Runnable() { @Override public void run() {
                getWindow().setStatusBarColor(Color.parseColor(dark ? "#0e1216" : "#2d6a3e"));
                if (Build.VERSION.SDK_INT >= 29) web.getSettings().setForceDark(WebSettings.FORCE_DARK_OFF);
            } });
        }

        /** 알림 조건 저장(이 기기에만) 후 바로 한 번 확인한다. */
        @JavascriptInterface
        public void setAlerts(String json) {
            try { new JSONObject(json); } catch (Exception e) { return; }
            Alerts.save(MainActivity.this, json);
            new Thread(new Runnable() { @Override public void run() { try { Alerts.check(MainActivity.this); } catch (Exception ignored) { } } }).start();
        }
    }
}
