"""(일회성) 정기 장학 32건의 모집 기간이 어디에 적혀 있는지 확인한다: 운영기관 공식 페이지와 목록 사이트 글."""
import json, re, sys
sys.path.insert(0, ".")
from bs4 import BeautifulSoup
from server.collectors.boards import allowed, get, Renderer, text_of_html
RD = Renderer()
DATE = re.compile(r"(모집|신청|접수|선발|공고)[^\n]{0,30}?(기간|일정|시기|마감)?[^\n]{0,60}?(20\d\d[.\-년]\s*\d{1,2}[.\-월]\s*\d{1,2}|\d{1,2}\s*월(\s*\d{1,2}\s*일)?|\d{1,2}\.\s*\d{1,2}\.?)[^\n]{0,60}")
def page(u):
    if not u or not allowed(u): return ""
    h = ""
    try: h = get(u, timeout=20).text
    except Exception: pass
    if len(h) < 3000 and RD.browser: h = RD.html(u, 3000) or h
    return text_of_html(h) if h else ""
res = []
for o in json.load(open("scripts/chk.json")):
    t1 = page(o["official"]); t2 = page(o["post"])
    res.append({"title": o["title"], "official_dates": [m.group(0)[:120] for m in DATE.finditer(t1)][:4], "official_len": len(t1),
                "post_dates": [m.group(0)[:140] for m in DATE.finditer(t2)][:4]})
    json.dump(res, open("out/chk.json", "w"), ensure_ascii=False, indent=1)
RD.close()
