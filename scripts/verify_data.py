"""공개 자료 재확인 (배포 전). 문제가 있으면 1 로 끝나 배포하지 않는다.

1) 회귀 사례: data/registry/verify_cases.json 에 적은, 예전에 틀렸던·헷갈리기 쉬운 공고가 지금도 맞게 분류되는지
2) 중복: 학교를 안 고른 사람과 학교 전용 공고가 많은 학교 학생 각각에게 보이는 목록에 같은 장학이 두 줄 이상 없는지
3) 제한 문구: 모든 학생에게 보이는 학교 게시판 공고에 '본교 재학생'·'학과장 추천' 같은 제한이 제목·대상에 남아 있지 않은지
4) 학교 이름: 학교 전용 공고에 붙은 학교 이름이 앱의 학교 목록에 있는지 (경고만)
결과는 data/registry/verify.json 에 남긴다.
"""
import collections, json, re, sys
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from server.merge import RESTRICT, OPEN_DEEP, _core, _orgn, catalog_dup  # noqa: E402

rows = json.loads((ROOT / "web/data/scholarships.json").read_text())["items"]
cases = json.loads((ROOT / "data/registry/verify_cases.json").read_text())
idx = (ROOT / "web/index.html").read_text()
known = set()
for pat in (r"const UNIVS=(\[.*?\]);", r"const UNIV_PORTALS=(\[.*?\]);", r"const EXTRA_SCHOOLS=(\[.*?\]);"):
    m = re.search(pat, idx, re.S)
    if m:
        for x in json.loads(m.group(1)):
            known.add(re.sub(r"^국립|\s.*$", "", x[0] if isinstance(x, list) else x))
fails, warns = [], []


def base(n):
    return re.sub(r"^국립|\s.*$", "", n or "")


def visible(r, school):
    if r.get("level") == "고등학생":
        return False
    own = r.get("own") or []
    if not own:
        return True
    if not school:
        return False
    b, camp = school
    for o in own:
        ob = base(o); oc = (re.search(r"\s(\S+)캠퍼스$", o) or [None, ""])[1]
        if ob == b and (not oc or not camp or oc == camp):
            return True
    return False


# 1) 회귀 사례
for c in cases:
    hits = [r for r in rows if (c.get("title") is None or c["title"] in r["title"]) and (c.get("title_re") is None or re.search(c["title_re"], r["title"] + " " + (r.get("org") or "")))
            and (c.get("source") is None or r["source"] == c["source"]) and (c.get("source_re") is None or re.search(c["source_re"], r["source"]))]
    if c.get("min_count") and len(hits) < c["min_count"]:
        warns.append(f"수집 건수 부족(배포는 계속, 확인 필요): {c.get('source_re')} {len(hits)}건 (최소 {c['min_count']}) — {c.get('why', '')}")
        continue
    if c["expect"] == "any":
        continue
    if c["expect"] == "single":
        if len({(_core(r["title"]), r.get("end") or "") for r in hits}) < len(hits):
            fails.append(f"사례 불일치 [한 줄] {hits[0]['title'][:60]} {len(hits)}줄 " + str([(r['source'], r.get('own'), r.get('end')) for r in hits]) + f" — {c.get('why', '')}")
        continue
    if c["expect"] == "one_general":  # 모든 학생에게 보이는 줄이 하나뿐이어야 한다 (이름이 조금 다른 중복도 잡는다)
        gen = [r for r in hits if not r.get("own") and r.get("level") != "고등학생"]
        if len(gen) > 1:
            fails.append(f"사례 불일치 [한 줄] {gen[0]['title'][:50]} {len(gen)}줄 " + str([(r['source'], r['title'][:30], r.get('end')) for r in gen]) + f" — {c.get('why', '')}")
        elif not gen:
            warns.append(f"사례 없음(지금 자료에 해당 공고 없음): {c.get('title') or c.get('title_re')}")
        continue
    if c["expect"] == "end_before":  # 마감일이 이 날짜보다 앞이어야 한다 (연도를 잘못 넘긴 마감 잡기)
        bad = [r for r in hits if (r.get("end") or "") >= c["date"]]
        for r in bad:
            fails.append(f"사례 불일치 [마감 연도] {r['title'][:60]} 마감 {r.get('end')} — {c.get('why', '')}")
        continue
    if not hits:
        warns.append(f"사례 없음(지금 자료에 해당 공고 없음): {c.get('title') or c.get('title_re')}")
        continue
    for r in hits:
        own = r.get("own") or []
        ok = (c["expect"] == "general" and not own) or (c["expect"] == "own" and own) or \
             (c["expect"] == "own_campus" and any(c["campus"] in o for o in own))
        if not ok:
            fails.append(f"사례 불일치 [{c['expect']}] {r['title'][:60]} (own={own}) — {c.get('why', '')}")

# 2) 중복
schools = [s for s, _ in collections.Counter(o for r in rows for o in (r.get("own") or [])).most_common(30)]
views = [None] + [(base(s), (re.search(r"\s(\S+)캠퍼스$", s) or [None, ""])[1]) for s in schools]
dup_total = 0
for v in views:
    g = collections.defaultdict(list)
    for r in rows:
        if visible(r, v):
            c = _core(r["title"])
            if len(c) >= 4:
                g[(c, r.get("end") or "")].append(r)
    for (c, e), rs in g.items():
        if len(rs) > 1 and len({x["id"] for x in rs}) > 1:
            dup_total += 1
            if dup_total <= 20:
                fails.append(f"중복({v[0] if v else '학교 미선택'}): " + " / ".join(f"{x['title'][:40]} [{x['source']} own={x.get('own')} end={x.get('end')}]" for x in rs[:3]))

# 2-2) 정기 장학 목록과 한국장학재단 자료의 같은 장학
korg = {}
for r in rows:
    if r["source"] in ("k", "kp", "kh", "ka", "kc"):
        korg.setdefault(_orgn(r.get("org")), []).append(_core(r["title"]))
for r in rows:
    if r["source"] == "c" and catalog_dup(r, korg):
        fails.append(f"정기 장학 목록과 한국장학재단 자료 중복: {r['title'][:50]}")

# 3) 제한 문구
for r in rows:
    if r["source"] in ("b", "u1", "u2", "u3", "u4", "u5") and not r.get("own"):
        t = " ".join(str(r.get(k) or "") for k in ("title", "target"))
        m = RESTRICT.search(t)
        if m and not OPEN_DEEP.search(t + " " + (r.get("summary") or "")):
            fails.append(f"제한 문구가 있는데 모든 학생에게 보임: {r['title'][:60]} ({m.group(0)})")

# 4) 학교 이름
unknown = sorted({o for r in rows for o in (r.get("own") or []) if base(o) not in known})
if unknown:
    warns.append(f"앱 학교 목록에 없는 학교 이름 {len(unknown)}개: " + ", ".join(unknown[:15]))

rep = {"rows": len(rows), "fails": fails, "warns": warns}
prev = ROOT / "data/registry/verify.json"
old = json.loads(prev.read_text()) if prev.exists() else {}
rep["changed_by_recheck"] = old.get("changed_by_recheck", [])
prev.write_text(json.dumps(rep, ensure_ascii=False, indent=1))
print(f"재확인: {len(rows)}건 · 실패 {len(fails)} · 경고 {len(warns)}")
for x in fails[:30]:
    print("  ✗", x)
for x in warns[:10]:
    print("  !", x)
sys.exit(1 if fails else 0)
