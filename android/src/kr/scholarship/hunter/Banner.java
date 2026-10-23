package kr.scholarship.hunter;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.google.android.libraries.ads.mobile.sdk.MobileAds;
import com.google.android.libraries.ads.mobile.sdk.banner.*;
import com.google.android.libraries.ads.mobile.sdk.common.*;
import com.google.android.libraries.ads.mobile.sdk.initialization.*;

/**
 * 화면 아래 띠 광고 (애드몹 앵커 적응형 배너, GMA Next-Gen SDK).
 * 광고 ID 는 빌드할 때 넣는다(BuildConfig). 넣지 않으면 구글이 제공하는 테스트 광고 ID 로 빌드된다.
 * 광고를 못 불러오면 띠를 접어 빈 칸이 남지 않게 한다.
 */
final class Banner {
    private static final String TAG = "HunterBanner";
    private static volatile boolean started;
    private final Activity activity;
    private final FrameLayout box;
    private AdView adView;
    private volatile boolean loaded;

    Banner(Activity activity, FrameLayout box) {
        this.activity = activity;
        this.box = box;
        box.setVisibility(View.GONE);
    }

    /** SDK 를 백그라운드에서 한 번만 초기화한 뒤 배너를 불러온다. */
    void start() {
        if (BuildConfig.ADMOB_APP_ID.isEmpty() || BuildConfig.ADMOB_BANNER_ID.isEmpty()) return;
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    if (!started) {
                        started = true;
                        MobileAds.initialize(activity,
                                new InitializationConfig.Builder(BuildConfig.ADMOB_APP_ID).build(),
                                new OnAdapterInitializationCompleteListener() {
                                    @Override public void onAdapterInitializationComplete(InitializationStatus status) { }
                                });
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "init failed", t);
                }
                activity.runOnUiThread(new Runnable() { @Override public void run() { load(); } });
            }
        }).start();
    }

    private void load() {
        if (activity.isFinishing()) return;
        try {
            float density = activity.getResources().getDisplayMetrics().density;
            int widthDp = (int) (activity.getResources().getDisplayMetrics().widthPixels / density);
            AdSize size = AdSize.getLargeAnchoredAdaptiveBannerAdSize(activity, widthDp);
            adView = new AdView(activity);
            box.removeAllViews();
            box.addView(adView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            BannerAdRequest req = new BannerAdRequest.Builder(BuildConfig.ADMOB_BANNER_ID, size).build();
            adView.loadAd(req, new AdLoadCallback<BannerAd>() {
                @Override public void onAdLoaded(BannerAd ad) {
                    loaded = true;
                    activity.runOnUiThread(new Runnable() { @Override public void run() { if (!kbd) box.setVisibility(View.VISIBLE); } });
                }
                @Override public void onAdFailedToLoad(LoadAdError error) {
                    Log.d(TAG, "banner failed: " + error);
                    activity.runOnUiThread(new Runnable() { @Override public void run() { box.setVisibility(View.GONE); } });
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "banner load failed", t);
            box.setVisibility(View.GONE);
        }
    }

    /** 키보드가 올라오면 광고를 잠시 감춘다 (입력 칸을 가리지 않게). */
    private boolean kbd;
    void keyboard(boolean shown) {
        kbd = shown;
        box.setVisibility(!shown && loaded ? View.VISIBLE : View.GONE);
    }

    void destroy() {
        if (adView != null) {
            if (adView.getParent() instanceof ViewGroup) ((ViewGroup) adView.getParent()).removeView(adView);
            adView.destroy();
            adView = null;
        }
    }
}
