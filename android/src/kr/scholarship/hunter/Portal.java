package kr.scholarship.hunter;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;

/**
 * 학생이 직접 고른 자기 학교 포털 (어느 학교든 같은 방식).
 * 학생이 직접 로그인하고 장학 화면에서 「이 화면 저장」을 누르면, 그 화면의 장학 목록을 이 기기에만 저장한다.
 * 비밀번호는 저장하지 않고 자동 로그인도 하지 않는다. 읽은 내용은 서버로 보내지 않는다.
 */
final class Portal {
    static final String MY = "myportal";
    static final String DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    final String name, domain, start, saved, menus, mainHost;
    final boolean pc;

    private Portal(String name, String domain, String entry, String saved, String menus, boolean pc) {
        this.name = name; this.domain = domain; this.saved = saved == null ? "" : saved;
        this.menus = menus == null || menus.isEmpty() ? "[]" : menus; this.pc = pc;
        this.start = !this.saved.isEmpty() ? this.saved : entry;
        String h = android.net.Uri.parse(this.start).getHost();
        this.mainHost = h == null ? domain : h;
    }

    /** 학생이 고른 학교 포털 (없으면 null). */
    static Portal get(Context c) {
        android.content.SharedPreferences sp = c.getSharedPreferences(MY, Context.MODE_PRIVATE);
        String d = sp.getString("domain", ""), e = sp.getString("entry", "");
        if (d.isEmpty() || e.isEmpty()) return null;
        return new Portal(sp.getString("name", "학교") + " 포털", d, e, sp.getString("saved", ""), sp.getString("menus", "[]"), sp.getBoolean("pc", false));
    }

    boolean isMain(String h) { return h != null && (h.equals(domain) || h.endsWith("." + domain) || h.equals(mainHost)); }

    /** 학교 통합 로그인(SSO)이 학교의 다른 주소로 넘어가는 경우까지 앱 안에서 연다. 그 밖의 주소는 열지 않는다. */
    boolean schoolHost(String h) { return h != null && (isMain(h) || h.endsWith(".ac.kr")); }

    String cfg() {
        return "window.__portalCfg={host:" + JSONObject.quote(domain) + ",menus:[" + menus + "],generic:true,hint:'장학 공지나 장학 신청·장학 일정 화면을 연 뒤 위의 「이 화면 저장」을 눌러 주세요.'};";
    }

    /** 화면에 보이는 비밀번호 칸이 있으면 로그인 화면으로 본다. */
    static String loginProbe() {
        return "(function(){function v(d){try{return Array.prototype.some.call(d.querySelectorAll('input[type=password]'),function(e){return e.offsetParent!==null;})}catch(x){return false}}"
                + "if(v(document))return 'login';var f=document.querySelectorAll('iframe,frame');for(var i=0;i<f.length;i++){try{if(f[i].contentDocument&&v(f[i].contentDocument))return 'login';}catch(x){}}return 'in';})()";
    }

    static File file(Context c) { return new File(c.getFilesDir(), "my_portal.json"); }

    static void setState(Context c, String s) { c.getSharedPreferences("portal_my", Context.MODE_PRIVATE).edit().putString("state", s).apply(); }

    static String state(Context c) { return c.getSharedPreferences("portal_my", Context.MODE_PRIVATE).getString("state", ""); }
}
