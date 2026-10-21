"""정기 장학 목록 (운영기관 공식 홈페이지 기준).

data/registry/catalog.json: 장학 이름·운영기관·운영기관 공식 주소와, 이름에서 뽑은 분류(지원 종류·대상·지역·특성)만 담는다.
다른 장학 정보 서비스의 설명·일정은 옮기지 않는다. 모집 기간·자격은 운영기관 공지(게시판 수집)에서 읽어 오고,
같은 장학이 다른 자료(한국장학재단 공공데이터·운영기관 공지)에 있으면 merge 가 한 줄로 합친다.
"""
from __future__ import annotations

import calendar
import json
from datetime import date
from pathlib import Path

from ..model import finalize, make_id

CATALOG = Path(__file__).resolve().parents[2] / "data" / "registry" / "catalog.json"


def _last_and_next(windows, today: date):
    """해마다 모집하는 달(예: [(1,1),(7,7)] 또는 [(12,1)])로 지난 모집 기간과 다음 모집 예상 달을 낸다.
    아직 공고를 확인하지 못했으므로 '지금 모집 중'으로 표시하지 않는다: 지난 기간은 이미 끝난 회차만 쓴다."""
    past, nxt = None, None
    for m1, m2 in windows or []:
        for y in (today.year - 2, today.year - 1, today.year, today.year + 1):
            y2 = y + 1 if m2 < m1 else y
            s, e = date(y, m1, 1), date(y2, m2, calendar.monthrange(y2, m2)[1])
            if e < today and (past is None or e > past[1]):
                past = (s, e)
            if e >= today and (nxt is None or s < nxt):
                nxt = s
    return past, nxt


def collect(online: bool = True):
    rows = json.loads(CATALOG.read_text()) if CATALOG.exists() else []
    out = []
    today = date.today()
    for c in rows:
        past, nxt = _last_and_next([tuple(w) for w in c.get("windows") or []], today)
        tags = c.get("tags") or []
        target = " · ".join(([c["level"]] + (c.get("also") or [])) + tags)
        out.append(finalize({
            "id": make_id("catalog", c["org"], c["name"]), "source": "catalog", "source_name": c["org"],
            "title": c["name"], "org": c["org"], "org_type": "민간·기업·대학", "category": "장학금", "kind": "정기 장학",
            "level": c.get("level") or "대학생", "support_hint": c.get("support") or ["living"],
            "region": c.get("region", ""), "district": "", "target": target, "special": " · ".join(tags),
            "summary": "해마다 모집하는 장학금이에요. 모집 기간은 지난 회차를 바탕으로 한 예상이니, 올해 기간·자격·금액은 운영기관 홈페이지 공지에서 확인하세요.",
            "url": c["url"], "links": [{"name": "운영기관 홈페이지", "url": c["url"]}],
            "start": past[0].isoformat() if past else "", "end": past[1].isoformat() if past else "", "verified": False,
        }))
        if nxt:
            out[-1]["next_hint"] = nxt.strftime("%Y-%m")
    return out, [{"source": "catalog", "label": f"정기 장학 목록 {len(out)}건", "ok": True, "count": len(out), "mode": "registry"}]
