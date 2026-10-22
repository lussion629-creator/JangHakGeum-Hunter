#!/usr/bin/env bash
# 구글 플레이용 AAB 빌드 (서명 전). 서명은 키가 있는 곳에서 jarsigner 로 따로 한다.
# 필요: JDK 17+, ANDROID_HOME(platforms;android-36, build-tools;36.0.0), BUNDLETOOL(jar 경로)
set -euo pipefail
cd "$(dirname "$0")"
BT=${BUILD_TOOLS:-$ANDROID_HOME/build-tools/36.0.0}
ANDROID_JAR=${ANDROID_JAR:-$ANDROID_HOME/platforms/android-36/android.jar}
VERSION=$(grep -o 'versionName="[^"]*"' AndroidManifest.xml | cut -d'"' -f2)
OUT=build-aab; rm -rf $OUT; mkdir -p $OUT/classes $OUT/assets/www $OUT/mod/manifest $OUT/mod/dex $OUT/mod/assets
python3 ../scripts/build_site.py >/dev/null
cp -r ../site/. $OUT/assets/www/ && rm -f $OUT/assets/www/data/*.gz && cp -r ../web/tools $OUT/assets/www/tools
$BT/aapt2 compile --dir res -o $OUT/res.zip
$BT/aapt2 link --proto-format -o $OUT/proto.zip -I $ANDROID_JAR --manifest AndroidManifest.xml -A $OUT/assets $OUT/res.zip \
  --min-sdk-version 24 --target-sdk-version 36 --auto-add-overlay
javac -source 8 -target 8 -bootclasspath $ANDROID_JAR -classpath $ANDROID_JAR -d $OUT/classes -nowarn -Xlint:-options $(find src -name '*.java')
java -cp $BT/lib/d8.jar com.android.tools.r8.D8 --release --min-api 24 --lib $ANDROID_JAR --output $OUT/mod/dex $(find $OUT/classes -name '*.class')
( cd $OUT && unzip -q proto.zip -d p && mv p/AndroidManifest.xml mod/manifest/ && mv p/assets/* mod/assets/ && mv p/resources.pb mod/ && mv p/res mod/ && cd mod && zip -qr ../base.zip . )
java -jar "$BUNDLETOOL" build-bundle --modules=$OUT/base.zip --output=$OUT/scholarship-hunter-$VERSION-unsigned.aab
java -jar "$BUNDLETOOL" validate --bundle=$OUT/scholarship-hunter-$VERSION-unsigned.aab | head -5
echo "완료: $OUT/scholarship-hunter-$VERSION-unsigned.aab"
