"""(일회성) 운영기관 공식 홈페이지에서 장학 공지가 올라오는 게시판을 찾는다. robots.txt 를 지킨다."""
import json, re, sys
from urllib.parse import urljoin, urlparse
sys.path.insert(0, ".")
from bs4 import BeautifulSoup
from server.collectors.boards import allowed, get, parse_html, KEY, Renderer
TODO = json.load(open("scripts/cc_todo.json"))
RD = Renderer()
SC = [(re.compile(r"장학\s*(공지|공고|소식|안내|사업)|공지\s*사항|^공지$|알림|소식|NEWS|Notice|게시판|공고", re.I), 5), (re.compile(r"장학"), 4)]
def fetch(u):
    if not allowed(u): return "", u
    try:
        r = get(u, timeout=15); h, f = r.text, r.url
    except Exception:
        h, f = "", u
    if len(h) < 3000 or h.count("<a") < 15:
        x = RD.html(u, 3000) if RD.browser else ""
        if len(x) > len(h): h = x
    return h, f
def n_posts(h, base):
    return sum(1 for it in parse_html(h, base) if KEY.search(it["title"]) and it.get("posted"))
res = {}
for org, host, url in TODO:
    try:
        h, f = fetch(url)
        if not h: res[org] = {"err": "unreachable", "url": url}; continue
        best = None
        if n_posts(h, f) >= 1 and re.search(r"bbs|board|notice|list|news", f, re.I): best = {"url": f, "n": n_posts(h, f)}
        cands = []
        for a in BeautifulSoup(h, "html.parser").find_all("a"):
            t = " ".join(a.get_text(" ").split()); href = a.get("href") or ""
            if not href or href.startswith(("javascript", "#", "mailto")) or len(t) > 20: continue
            u = urljoin(f, href)
            if (urlparse(u).hostname or "").replace("www.", "") != host.replace("www.", ""): continue
            sc = next((s for rx, s in SC if rx.search(t)), 0)
            if sc: cands.append((sc, u))
        seen = set()
        for sc, u in sorted(cands, key=lambda x: -x[0])[:8]:
            if u in seen: continue
            seen.add(u)
            h2, f2 = fetch(u)
            n = n_posts(h2, f2)
            if n and (not best or n > best["n"]): best = {"url": f2, "n": n}
        res[org] = best or {"err": "notfound", "url": url}
    except Exception as e:
        res[org] = {"err": repr(e)[:80], "url": url}
    json.dump(res, open("out/cc_boards.json", "w"), ensure_ascii=False, indent=0)
RD.close()
