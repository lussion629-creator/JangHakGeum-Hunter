"""배포 전 화면 점검: 만든 사이트(site/)를 실제 브라우저로 열어 모든 탭·상세·달력을 눌러 보고,
스크립트 오류·빈 달력·'D--' 같은 잘못된 표시가 있으면 1 로 끝나 배포하지 않는다."""
import functools, http.server, json, re, sys, threading
from pathlib import Path
from playwright.sync_api import sync_playwright

SITE = Path(__file__).resolve().parents[1] / "site"
class Quiet(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *a):
        pass


srv = http.server.ThreadingHTTPServer(("127.0.0.1", 0), functools.partial(Quiet, directory=str(SITE)))
threading.Thread(target=srv.serve_forever, daemon=True).start()
URL = f"http://127.0.0.1:{srv.server_port}/"
fails = []
# 가독성: 보이는 글자의 크기(12px 이상)와 바탕 대비(4.5:1, 큰 글자 3:1)를 잰다
READ_JS = """(()=>{
function rgb(c){if(c.startsWith('color(')){const m=c.match(/[\\d.]+/g).map(Number);return [m[0]*255,m[1]*255,m[2]*255,m.length>3?m[3]:1]}
 const m=c.match(/[\\d.]+/g);if(!m)return null;const v=m.map(Number);return [v[0],v[1],v[2],v.length>3?v[3]:1]}
function lum(m){const f=v=>{v/=255;return v<=.03928?v/12.92:((v+.055)/1.055)**2.4};return .2126*f(m[0])+.7152*f(m[1])+.0722*f(m[2])}
function bg(e){const st=[];while(e){const b=rgb(getComputedStyle(e).backgroundColor);if(b&&b[3]>0){st.push(b);if(b[3]>=.95)break}e=e.parentElement}
 let c=st.length&&st[st.length-1][3]>=.95?st.pop().slice(0,3):[255,255,255];while(st.length){const t=st.pop();c=c.map((v,i)=>v*(1-t[3])+t[i]*t[3])}return c}
const bad=[];const w=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT);let n;
while(n=w.nextNode()){const t=n.textContent.trim();if(!t)continue;const e=n.parentElement;if(!e.offsetParent&&getComputedStyle(e).position!=='fixed')continue;
 const r=e.getBoundingClientRect();if(!r.width||!r.height||r.top>innerHeight*3)continue;const s=getComputedStyle(e);if(s.visibility==='hidden'||+s.opacity<.3)continue;
 if(e.closest('.ev.g,.closed,.past,footer'))continue;
 const fs=parseFloat(s.fontSize),fg=rgb(s.color),b=bg(e);const f=fg.slice(0,3).map((v,i)=>v*fg[3]+b[i]*(1-fg[3]));
 const L1=lum(f),L2=lum(b),cr=(Math.max(L1,L2)+.05)/(Math.min(L1,L2)+.05);const big=fs>=18.6||(fs>=14&&+s.fontWeight>=700);
 if(fs<12)bad.push('작은 글자 '+fs+'px «'+t.slice(0,16)+'»');else if(cr<(big?3:4.5))bad.push('흐린 글자 '+cr.toFixed(1)+':1 «'+t.slice(0,16)+'»')}
return [...new Set(bad)].slice(0,6)})()"""
PROFILES = [None, {"schoolName": "한양대학교 ERICA캠퍼스", "status": "univ", "major": "사학과"}, {"schoolName": "부산대학교", "status": "univ"}]
with sync_playwright() as p:
    b = p.chromium.launch()
    for prof in PROFILES:
        for w, h in ((1280, 900), (390, 844), (320, 640)):
            pg = b.new_page(viewport={"width": w, "height": h}, color_scheme=("dark" if (w == 390 and prof and "부산" in prof.get("schoolName", "")) else "light"))
            errs = []
            pg.on("pageerror", lambda e, errs=errs: errs.append(str(e)))
            pg.add_init_script("localStorage.setItem('hunt:helpSeen','true');" + (f"localStorage.setItem('hunt:profile',{json.dumps(json.dumps(prof, ensure_ascii=False))});" if prof else ""))
            pg.goto(URL); pg.wait_for_timeout(2500)
            tag = f"{(prof or {}).get('schoolName', '학교 미선택')} {w}px"
            ov = pg.evaluate("(()=>{const n=document.querySelector('nav.tabs');const r=n.getBoundingClientRect();return n.scrollWidth>n.clientWidth+1||r.right>innerWidth+1})()")
            if ov:
                fails.append(f"{tag}: 탭 줄이 화면 밖으로 넘침 (마지막 탭·숫자 배지가 잘림)")
            cut = pg.evaluate("[...document.querySelectorAll('.due span')].filter(e=>e.offsetParent&&e.scrollWidth>e.clientWidth+1).map(e=>e.textContent).slice(0,2)")
            if cut:
                fails.append(f"{tag}: 마감일 칸 글자가 잘림 {cut}")
            if pg.locator(".list [data-id]").count() == 0:
                fails.append(f"{tag}: 찾기 목록이 비어 있음")
            for t in ("cal", "fav", "alert", "me", "doc", "src", "find"):
                pg.click(f'nav.tabs [data-tab="{t}"]'); pg.wait_for_timeout(500)
                if t == "cal" and pg.locator("#view-cal").inner_text().count("\n") < 30:
                    fails.append(f"{tag}: 마감 달력이 그려지지 않음")
            first = pg.locator(".list [data-id]").first
            if first.count():
                first.click(); pg.wait_for_timeout(400)
                if not pg.locator("#drawer").is_visible():
                    fails.append(f"{tag}: 상세가 열리지 않음")
                pg.keyboard.press("Escape")
            body = pg.locator("body").inner_text()
            for bad in re.findall(r"D--\d+|NaN|undefined|\[object Object\]", body)[:3]:
                fails.append(f"{tag}: 잘못된 표시 '{bad}'")
            if prof and pg.locator("#onlyBox").is_hidden():
                fails.append(f"{tag}: 내 정보에 학교를 넣었는데 우리 학교 칸이 안 보임")
            if not prof and pg.locator("#onlyBox").is_visible():
                fails.append(f"{tag}: 학교를 안 넣었는데 우리 학교 칸이 보임")
            if pg.locator("#dClose").is_visible():
                pg.click("#dClose"); pg.wait_for_timeout(300)
            if w < 1000:
                for v in ("find", "cal"):
                    pg.click(f'nav.tabs [data-tab="{v}"]'); pg.wait_for_timeout(400)
                    for x in pg.evaluate(READ_JS):
                        fails.append(f"{tag} {v}: {x}")
                pg.click('nav.tabs [data-tab="find"]'); pg.wait_for_timeout(300)
            for e in errs[:3]:
                fails.append(f"{tag}: 스크립트 오류 {e[:120]}")
            pg.close()
    b.close()
srv.shutdown()
print(f"화면 점검: 실패 {len(fails)}")
for f in fails:
    print("  ✗", f)
sys.exit(1 if fails else 0)
