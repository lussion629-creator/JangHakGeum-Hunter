"""(일회성) 목록 사이트 글에서 '모집 시기' 의 달(月)만 확인한다. 문장은 저장하지 않는다."""
import json, re, sys, time
sys.path.insert(0, ".")
from bs4 import BeautifulSoup
from server.collectors.boards import allowed, get
res = {}
for p in json.load(open("scripts/cc_posts.json"))["posts"]:
    u = p["url"]
    if not p.get("title") or not allowed(u): continue
    try:
        t = BeautifulSoup(get(u, timeout=20).text, "html.parser").get_text("\n", strip=True)
    except Exception as e:
        res[u] = {"err": str(e)[:60]}; continue
    m = re.search(r"모집\s*시기[^\n]{0,4}\n?\s*\[([^\]]{1,30})\]", t) or re.search(r"모집\s*시기\s*[:：]?\s*([^\n]{1,40})", t)
    seg = m.group(1) if m else ""
    months = sorted({int(x) for x in re.findall(r"(\d{1,2})\s*월", seg) if 1 <= int(x) <= 12})
    rng = bool(re.search(r"\d{1,2}\s*월?\s*[~～\-]\s*\d{1,2}\s*월", seg))
    res[u] = {"seg": seg[:40]}
    time.sleep(0.5)
json.dump(res, open("out/months.json", "w"), ensure_ascii=False, indent=0)
