package kr.scholarship.hunter;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;

/**
 * Android 15(API 35)부터 앱 화면은 상태 표시줄·내비게이션 막대 아래까지 그려지고, API 36 부터는 끌 수 없다.
 * 화면 내용을 시스템 막대·화면 구멍(카메라)·키보드만큼 안쪽으로 밀고, 막대 뒤에는 앱 색을 칠한다.
 */
final class EdgeToEdge {
    private EdgeToEdge() { }

    /** content 를 감싼 틀을 돌려준다. 위쪽 막대 자리는 topColor, 아래쪽은 bottomColor 로 칠한다. */
    static Holder wrap(Activity a, View content, int topColor, int bottomColor) {
        final FrameLayout frame = new FrameLayout(a);
        final View top = new View(a);
        final View bottom = new View(a);
        top.setBackgroundColor(topColor);
        bottom.setBackgroundColor(bottomColor);
        frame.setBackgroundColor(bottomColor);
        frame.addView(content, new FrameLayout.LayoutParams(-1, -1));
        frame.addView(top, new FrameLayout.LayoutParams(-1, 0, android.view.Gravity.TOP));
        frame.addView(bottom, new FrameLayout.LayoutParams(-1, 0, android.view.Gravity.BOTTOM));
        if (Build.VERSION.SDK_INT >= 30) a.getWindow().setDecorFitsSystemWindows(false);
        frame.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                int l, t, r, b;
                if (Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets s = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                    android.graphics.Insets ime = in.getInsets(WindowInsets.Type.ime());
                    l = s.left; t = s.top; r = s.right; b = Math.max(s.bottom, ime.bottom);
                } else {
                    l = t = r = b = 0;  // Android 10 이하는 시스템이 알아서 막대 아래로 내용을 둔다 (adjustResize 도 그대로)
                }
                FrameLayout.LayoutParams cp = (FrameLayout.LayoutParams) content.getLayoutParams();
                cp.setMargins(l, t, r, b);
                content.setLayoutParams(cp);
                ViewGroup.LayoutParams tp = top.getLayoutParams(); tp.height = t; top.setLayoutParams(tp);
                ViewGroup.LayoutParams bp = bottom.getLayoutParams(); bp.height = b; bottom.setLayoutParams(bp);
                return in;
            }
        });
        return new Holder(a, frame, top, bottom);
    }

    static final class Holder {
        final Activity a; final FrameLayout frame; final View top, bottom;
        Holder(Activity a, FrameLayout f, View t, View b) { this.a = a; frame = f; top = t; bottom = b; }

        /** 막대 색을 바꾸고, 밝은 바탕이면 아이콘을 어둡게 한다. */
        void colors(int topColor, int bottomColor, boolean lightBottom) {
            top.setBackgroundColor(topColor);
            bottom.setBackgroundColor(bottomColor);
            frame.setBackgroundColor(bottomColor);
            if (Build.VERSION.SDK_INT < 35) {
                a.getWindow().setStatusBarColor(topColor);
                if (Build.VERSION.SDK_INT >= 26) a.getWindow().setNavigationBarColor(bottomColor);
            }
            if (Build.VERSION.SDK_INT >= 30 && a.getWindow().getInsetsController() != null) {
                int nav = android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                int st = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
                a.getWindow().getInsetsController().setSystemBarsAppearance((lightBottom ? nav : 0), nav | st);
            }
        }
    }

    static int c(String hex) { return Color.parseColor(hex); }
}
