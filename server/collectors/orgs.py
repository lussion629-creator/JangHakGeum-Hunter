"""운영기관 이름 → 원본(공지 게시판 또는 홈페이지) 주소 찾기."""
from __future__ import annotations

import json
import re
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def _homepages() -> dict[str, str]:
    """운영기관 이름 → 원본(공지 게시판 또는 홈페이지) 주소."""
    out: dict[str, str] = {}
    db = ROOT / "data" / "scholarships.db"
    if db.exists():
        con = sqlite3.connect(db)
        for (d,) in con.execute("select data from scholarships where source like 'kosaf%'"):
            r = json.loads(d)
            if r.get("org") and r.get("url"):
                out.setdefault(_norm(r["org"]), r["url"])
        con.close()
    reg = ROOT / "data" / "registry" / "sources.json"
    if reg.exists():
        for s in json.loads(reg.read_text()):
            u = s.get("notice_url") or s.get("homepage")
            if u:
                out[_norm(s["name"])] = u
    return out


def _norm(name: str) -> str:
    return re.sub(r"\(.*?\)|재단법인|사단법인|\s", "", name or "")
