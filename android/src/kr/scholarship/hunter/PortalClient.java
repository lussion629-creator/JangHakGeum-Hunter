package kr.scholarship.hunter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 학생이 직접 로그인한 학교 포털에서, 학생이 저장한 장학 화면의 목록만 읽는다.
 * 비밀번호·입력칸·본인의 신청 내역 표는 읽지 않고, 결과는 이 기기 안(앱 전용 저장소)에만 둔다.
 */
final class PortalClient {
    interface Listener {
        void onProgress(String text);
        void onLoginNeeded();
        void onDone(int count);
        void onFail(String text);
    }

    static final String NATIVE = "HunterPortalNative";
    private final Portal portal;
    private final Context ctx;
    private final WebView web;
    private final Listener ln;
    private final boolean silent;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final String token;
    private volatile String host = "";
    private boolean finished, injected;
    private int savedTries;
    private final java.util.ArrayList<String> clicks = new java.util.ArrayList<>();

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    PortalClient(Context ctx, WebView web, boolean silent, Portal portal, Listener ln) {
        this.ctx = ctx; this.web = web; this.silent = silent; this.ln = ln; this.portal = portal;
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSaveFormData(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (portal.pc) {
            s.setUserAgentString(Portal.DESKTOP_UA);
            s.setUseWideViewPort(true); s.setLoadWithOverviewMode(true);
            s.setBuiltInZoomControls(true); s.setDisplayZoomControls(false);
        }
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        // 다리 객체는 화면 속 작은 창(iframe)에서도 보이므로, 앱이 넣은 스크립트만 아는 일회용 열쇠가 있어야 받는다
        byte[] rnd = new byte[16];
        new java.security.SecureRandom().nextBytes(rnd);
        StringBuilder tk = new StringBuilder();
        for (byte b : rnd) tk.append(String.format("%02x", b));
        token = tk.toString();
        web.addJavascriptInterface(new Bridge(), NATIVE);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                Uri u = req.getUrl();
                String sc = u.getScheme() == null ? "" : u.getScheme();
                if ("https".equals(sc) && portal.schoolHost(u.getHost())) return false;
                if (silent) return true;
                try { // 2차 인증 앱 호출(intent:)이나 바깥 주소는 휴대폰의 앱·브라우저로 연다
                    Intent it = "intent".equals(sc) ? Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME) : new Intent(Intent.ACTION_VIEW, u);
                    it.addCategory(Intent.CATEGORY_BROWSABLE); it.setComponent(null); it.setSelector(null);
                    ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception ignored) { }
                return true;
            }

            @Override
            public void onPageFinished(final WebView v, final String url) {
                Uri u = Uri.parse(url);
                host = u.getHost() == null ? "" : u.getHost();
                CookieManager.getInstance().flush();
                if (finished) return;
                final boolean main = portal.isMain(host);
                if (main) v.evaluateJavascript(recorder(), null);
                if (!main) { if (!silent) ln.onProgress(portal.name + "에 로그인해 주세요. 비밀번호는 앱에 저장되지 않습니다."); return; }
                v.evaluateJavascript(Portal.loginProbe(), new android.webkit.ValueCallback<String>() {
                    @Override public void onReceiveValue(String r) { if (!finished) route(v, url, !"\"in\"".equals(r)); }
                });
            }
        });
    }

    private boolean atSaved(String url) {
        if (portal.saved.isEmpty()) return false;
        return url.split("#")[0].equals(portal.saved.split("#")[0]);
    }

    private void route(WebView v, String url, boolean login) {
        boolean hasSaved = !portal.saved.isEmpty();
        if (login) {
            if (silent) { finish(); ln.onLoginNeeded(); } // 로그인이 끝났으면 조용히 멈춘다 (자동 로그인은 하지 않는다)
            else ln.onProgress(portal.name + "에 로그인해 주세요. 비밀번호는 앱에 저장되지 않습니다.");
            return;
        }
        if (silent && hasSaved && !atSaved(url) && savedTries < 2) { savedTries++; v.loadUrl(portal.saved); return; }
        if (atSaved(url) || (silent && hasSaved)) {
            if (!injected) { injected = true; ln.onProgress("장학 화면을 읽는 중…"); v.evaluateJavascript(script(), null); }
            return;
        }
        if (silent) { finish(); ln.onFail("NOSAVED"); return; }
        ln.onProgress(hasSaved ? "저장한 장학 화면을 여는 중이에요. 다른 화면이면 장학 화면을 열고 「이 화면 저장」을 눌러 주세요."
                : "로그인한 뒤 장학 공지나 장학 신청·장학 일정 화면을 열고 「이 화면 저장」을 눌러 주세요.");
    }

    void start() { web.loadUrl(portal.start); }

    private String recorder() {
        return "(function(N,k){function hook(d){try{if(d.__jkr)return;d.__jkr=1;d.addEventListener('click',function(e){var el=e.target&&e.target.closest?e.target.closest('a,button,li,span,td,div'):null;if(!el)return;"
                + "var t=(el.innerText||el.textContent||'').replace(/\\s+/g,' ').trim();if(t&&t.length<=20)N.click(k,t);},true);"
                + "var f=d.querySelectorAll('iframe,frame');for(var i=0;i<f.length;i++){try{if(f[i].contentDocument)hook(f[i].contentDocument);}catch(x){}}}catch(x){}}"
                + "hook(document);setInterval(function(){hook(document)},3000);})(window." + NATIVE + ",'" + token + "');";
    }

    /** 지금 화면을 '장학 화면'으로 저장하고 바로 읽는다. */
    void saveHere() {
        String url = web.getUrl();
        if (url == null || !portal.isMain(Uri.parse(url).getHost())) { ln.onFail("학교 포털 화면에서 눌러 주세요."); return; }
        org.json.JSONArray m = new org.json.JSONArray();
        synchronized (clicks) { int from = Math.max(0, clicks.size() - 4); for (int i = from; i < clicks.size(); i++) m.put(clicks.get(i)); }
        ctx.getSharedPreferences(Portal.MY, Context.MODE_PRIVATE).edit().putString("saved", url).putString("menus", m.toString()).apply();
        injected = true;
        ln.onProgress("이 화면의 장학 목록을 읽는 중…");
        web.evaluateJavascript("window.__portalReaderRunning=false;window.__portalNow=true;" + script(), null);
    }

    private void finish() {
        finished = true;
        ui.post(new Runnable() { @Override public void run() { try { web.stopLoading(); } catch (Exception ignored) { } } });
    }

    private String script() {
        try (InputStream in = ctx.getAssets().open("www/tools/portal-reader.js")) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
            String wrap = "(function(N,k){var w={progress:function(t){N.progress(k,String(t))},done:function(t){N.done(k,String(t))},fail:function(t){N.fail(k,String(t))}};"
                    + "window.PortalBridge=w;})(window." + NATIVE + ",'" + token + "');";
            return wrap + "window.__portalSilent=" + silent + ";" + portal.cfg() + "\n" + bo.toString("UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    static String read(File f) throws Exception {
        byte[] b = new byte[(int) Math.min(f.length(), 6_000_000)];
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) { int off = 0, n; while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n; }
        return new String(b, "UTF-8");
    }

    class Bridge {
        private boolean ok(String k) { return token.equals(k) && portal.isMain(host) && !finished; }

        @JavascriptInterface
        public void click(String k, String t) {
            if (!token.equals(k) || t == null) return;
            synchronized (clicks) { if (clicks.isEmpty() || !clicks.get(clicks.size() - 1).equals(t)) clicks.add(t.substring(0, Math.min(20, t.length()))); while (clicks.size() > 12) clicks.remove(0); }
        }

        @JavascriptInterface
        public void progress(String k, final String t) {
            if (!ok(k)) return;
            ui.post(new Runnable() { @Override public void run() { ln.onProgress(t == null ? "" : t.substring(0, Math.min(120, t.length()))); } });
        }

        @JavascriptInterface
        public void done(String k, String json) {
            if (!ok(k) || json == null || json.length() > 6_000_000) return;
            int count;
            try {
                org.json.JSONObject o = new org.json.JSONObject(json);
                org.json.JSONArray rs = o.getJSONArray("records");
                count = rs.length();
                File f = Portal.file(ctx);
                if (silent && f.exists()) try { // 전에 없던 장학이 생겼으면 알린다
                    java.util.HashSet<String> old = new java.util.HashSet<>();
                    org.json.JSONArray prev = new org.json.JSONObject(read(f)).getJSONArray("records");
                    for (int i = 0; i < prev.length(); i++) old.add(prev.getJSONObject(i).optString("name"));
                    java.util.ArrayList<String> fresh = new java.util.ArrayList<>();
                    for (int i = 0; i < rs.length(); i++) { String n = rs.getJSONObject(i).optString("name"); if (!n.isEmpty() && !old.contains(n)) fresh.add(n); }
                    if (!fresh.isEmpty() && !old.isEmpty()) Alerts.simple(ctx, 7290, portal.name + "에 새 장학 " + fresh.size() + "건", fresh.get(0));
                } catch (Exception ignored) { }
                o.put("savedAt", System.currentTimeMillis());
                try (FileOutputStream out = new FileOutputStream(f)) { out.write(o.toString().getBytes(StandardCharsets.UTF_8)); }
            } catch (Exception e) {
                ui.post(new Runnable() { @Override public void run() { ln.onFail("불러온 자료를 저장하지 못했어요."); } });
                return;
            }
            finish();
            final int c = count;
            ui.post(new Runnable() { @Override public void run() { ln.onDone(c); } });
        }

        @JavascriptInterface
        public void fail(String k, final String t) {
            if (!ok(k)) return;
            if ("LOGIN".equals(t)) {
                if (silent) { finish(); ui.post(new Runnable() { @Override public void run() { ln.onLoginNeeded(); } }); }
                else ui.post(new Runnable() { @Override public void run() { ln.onProgress(portal.name + "에 로그인해 주세요. 로그인하면 자동으로 불러옵니다."); } });
                return;
            }
            if ("NOTFOUND".equals(t)) { Portal.setState(ctx, "notfound"); finish(); }
            ui.post(new Runnable() { @Override public void run() { ln.onFail(t == null ? "" : t); } });
        }
    }
}
