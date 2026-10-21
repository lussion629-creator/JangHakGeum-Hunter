"""(일회성) 한양대 공지 수집 실패 원인 확인."""
import sys, traceback, json
sys.path.insert(0, ".")
from server.collectors import hanyang
from server.collectors.boards import allowed, get
out = {}
u = hanyang.LIST.format(p=hanyang.PID, page=1, cat=hanyang.CAT_SCH)
out["url"] = u; out["allowed"] = allowed(u)
try:
    r = get(u, timeout=25); out["status"] = r.status_code; out["len"] = len(r.text); out["head"] = r.text[:300]
except Exception as e:
    out["get_err"] = repr(e)[:300]
try:
    rows = hanyang.fetch(1, hanyang.CAT_SCH); out["rows"] = len(rows); out["sample"] = rows[:2]
except Exception:
    out["fetch_err"] = traceback.format_exc()[-1500:]
json.dump(out, open("out/hy.json", "w"), ensure_ascii=False, indent=1)
