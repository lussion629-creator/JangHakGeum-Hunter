"""(일회성) 전국 대학 목록(공공데이터) 내려받기."""
import sys
sys.path.insert(0, ".")
from server.collectors.kosaf import download
for pk in ("15152261",):
    try:
        open(f"out/univ_{pk}.bin", "wb").write(download(pk))
    except Exception as e:
        open(f"out/univ_{pk}.err", "w").write(repr(e))
