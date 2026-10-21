"""(일회성) 장학 정보 사이트의 생활비 장학 목록에서 장학 이름·운영기관·원본(운영기관) 링크만 모은다. robots.txt 를 지킨다."""
import json, re, sys, time
from urllib.parse import urljoin, urlparse
sys.path.insert(0, ".")
from bs4 import BeautifulSoup
from server.collectors.boards import allowed, get
BASE = "https://campuscholar.com/category/scholarships/external/living-cost-scholarships/"
out = {"robots_list": allowed(BASE), "posts": []}
if out["robots_list"]:
    urls = []
    for n in range(1, 40):
        u = BASE if n == 1 else f"{BASE}page/{n}/"
        try:
            h = get(u, timeout=20).text
        except Exception as e:
            out.setdefault("errs", []).append(f"{u} {e}"[:120]); break
        s = BeautifulSoup(h, "html.parser")
        found = [a["href"] for a in s.select("h2 a[href], h3 a[href], .entry-title a[href]") if "campuscholar.com/" in a["href"] and "/category/" not in a["href"]]
        if not found: break
        urls += [x for x in found if x not in urls]
        time.sleep(1)
    for u in urls:
        if not allowed(u):
            out["posts"].append({"url": u, "err": "robots"}); continue
        try:
            s = BeautifulSoup(get(u, timeout=20).text, "html.parser")
        except Exception as e:
            out["posts"].append({"url": u, "err": str(e)[:80]}); continue
        title = (s.select_one("h1") or s.title).get_text(" ", strip=True)
        body = s.select_one(".entry-content, article") or s
        ext = []
        for a in body.select("a[href]"):
            h = a["href"]; host = urlparse(h).hostname or ""
            if h.startswith("http") and "campuscholar" not in host and not re.search(r"facebook|twitter|kakao|naver\.me|instagram|youtube|google|band\.us|x\.com|t\.me|linkedin|pinterest|addtoany", host):
                ext.append({"text": a.get_text(" ", strip=True)[:40], "url": h})
        cats = [a.get_text(strip=True) for a in s.select("a[rel~=category], .cat-links a")]
        out["posts"].append({"url": u, "title": title[:120], "ext": ext[:8], "cats": cats[:6]})
        time.sleep(1)
json.dump(out, open("out/cc.json", "w"), ensure_ascii=False, indent=0)
print(len(out["posts"]))
