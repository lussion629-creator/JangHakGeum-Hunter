"""정기 장학 목록 (운영기관 공식 홈페이지 기준).

data/registry/catalog.json: 장학 이름·운영기관·운영기관 공식 주소와, 이름에서 뽑은 분류(지원 종류·대상·지역·특성)만 담는다.
다른 장학 정보 서비스의 설명·일정은 옮기지 않는다. 모집 기간·자격은 운영기관 공지(게시판 수집)에서 읽어 오고,
같은 장학이 다른 자료(한국장학재단 공공데이터·운영기관 공지)에 있으면 merge 가 한 줄로 합친다.
"""
from __future__ import annotations

import json
from pathlib import Path

from ..model import finalize, make_id

CATALOG = Path(__file__).resolve().parents[2] / "data" / "registry" / "catalog.json"


def collect(online: bool = True):
    rows = json.loads(CATALOG.read_text()) if CATALOG.exists() else []
    out = []
    for c in rows:
        tags = c.get("tags") or []
        target = " · ".join(([c["level"]] + (c.get("also") or [])) + tags)
        out.append(finalize({
            "id": make_id("catalog", c["org"], c["name"]), "source": "catalog", "source_name": c["org"],
            "title": c["name"], "org": c["org"], "org_type": "민간·기업·대학", "category": "장학금", "kind": "정기 장학",
            "level": c.get("level") or "대학생", "support_hint": c.get("support") or ["living"],
            "region": c.get("region", ""), "district": "", "target": target, "special": " · ".join(tags),
            "summary": "해마다 모집하는 장학금이에요. 올해 모집 기간·자격·금액은 운영기관 홈페이지 공지에서 확인하세요.",
            "url": c["url"], "links": [{"name": "운영기관 홈페이지", "url": c["url"]}], "start": "", "end": "", "verified": False,
        }))
    return out, [{"source": "catalog", "label": f"정기 장학 목록 {len(out)}건", "ok": True, "count": len(out), "mode": "registry"}]
