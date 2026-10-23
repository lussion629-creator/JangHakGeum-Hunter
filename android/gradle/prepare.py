"""Gradle 빌드 준비: 웹앱을 assets/www 로 복사하고, Gradle 이 받지 않는 매니페스트 속성(package·uses-sdk·버전)을 뺀 사본을 만든다."""
import re, shutil, subprocess, sys
from pathlib import Path
A = Path(__file__).resolve().parents[1]          # android/
OUT = A / "build-gradle"
shutil.rmtree(OUT, ignore_errors=True)
(OUT / "assets").mkdir(parents=True)
subprocess.run([sys.executable, str(A.parent / "scripts" / "build_site.py")], check=True, stdout=subprocess.DEVNULL)
shutil.copytree(A.parent / "site", OUT / "assets" / "www")
for gz in (OUT / "assets" / "www" / "data").glob("*.gz"):
    gz.unlink()
shutil.copytree(A.parent / "web" / "tools", OUT / "assets" / "www" / "tools")
m = (A / "AndroidManifest.xml").read_text()
m = re.sub(r'\s+package="[^"]*"', "", m)
m = re.sub(r'\s+android:version(Code|Name)="[^"]*"', "", m)
m = re.sub(r'\s*<uses-sdk[^>]*/>', "", m)
(OUT / "AndroidManifest.xml").write_text(m)
print("prepared", OUT)
