package ro.razvan.cursahud;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HudService extends AccessibilityService {

    static volatile HudService instance;

    private static final int GREEN = 0xFF3DDC84;
    private static final int YELLOW = 0xFFFFC233;
    private static final int RED = 0xFFFF5252;
    private static final Locale RO = new Locale("ro", "RO");
    private static final long AUTO_HIDE_MS = 45_000;
    private static final int MAX_NODES = 2000;

    private final Handler h = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private LinearLayout hud;
    private WindowManager.LayoutParams lp;
    private TextView tvTop, tvBig, tvMid, tvSmall, tvApp;
    private GradientDrawable bg, appBg;

    private String shownKey = null;
    private String dismissedKey = null;
    private Prefs prefs;

    private final Runnable scan = this::scanNow;
    private final Runnable autoHide = this::hide;

    @Override
    protected void onServiceConnected() {
        instance = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        prefs = Prefs.load(this);
    }

    @Override
    public void onDestroy() {
        hide();
        instance = null;
        super.onDestroy();
    }

    @Override
    public void onInterrupt() {}

    void reloadPrefs() {
        prefs = Prefs.load(this);
        shownKey = null;
    }

    private boolean isTarget(CharSequence pkg) {
        if (pkg == null) return false;
        String p = pkg.toString();
        if (p.equals(getPackageName())) return false;
        if (prefs != null && prefs.modTest) return true;
        return p.contains("mtakso") || p.contains("bolt") || p.contains("uber");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        CharSequence pkg = e.getPackageName();
        if (pkg == null || pkg.toString().equals(getPackageName())) return;
        if (isTarget(pkg)) {
            // Throttle (nu debounce): bara de timp din ofertă trimite evenimente continuu.
            if (!scanPending) {
                scanPending = true;
                h.postDelayed(scan, 250);
            }
        } else if (e.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && !pkg.toString().equals("com.android.systemui")) {
            // Altă aplicație în față → ascunde.
            hide();
        }
    }

    private boolean scanPending = false;

    private void scanNow() {
        scanPending = false;
        // Fiecare fereastră se citește separat, ca să știm din ce aplicație vine oferta
        // (șoferul poate avea Bolt și Uber pornite în același timp).
        OfferParser.Offer o = null;
        String app = null;
        boolean sawTargetText = false;
        try {
            List<AccessibilityNodeInfo> roots = new ArrayList<>();
            List<AccessibilityWindowInfo> wins = getWindows();
            if (wins != null && !wins.isEmpty()) {
                for (AccessibilityWindowInfo w : wins) {
                    AccessibilityNodeInfo r = w.getRoot();
                    if (r != null && isTarget(r.getPackageName())) roots.add(r);
                }
            } else {
                AccessibilityNodeInfo r = getRootInActiveWindow();
                if (r != null && isTarget(r.getPackageName())) roots.add(r);
            }
            for (AccessibilityNodeInfo r : roots) {
                StringBuilder sb = new StringBuilder(2048);
                collect(r, sb, new int[]{0}, 0);
                if (sb.length() > 0) sawTargetText = true;
                OfferParser.Offer found = OfferParser.parse(sb.toString(), true);
                if (found != null) {
                    o = found;
                    app = appName(r.getPackageName());
                    break;
                }
            }
        } catch (Exception ignored) {}

        if (o == null) {
            if (sawTargetText) hide(); // ecranul aplicației s-a schimbat, oferta a dispărut
            return;
        }
        o.app = app;
        String key = o.key();
        if (key.equals(dismissedKey)) return;
        if (!key.equals(shownKey)) show(o);
        h.removeCallbacks(autoHide);
        h.postDelayed(autoHide, AUTO_HIDE_MS);
    }

    /** Bolt Driver = ee.mtakso.driver, Uber Driver = com.ubercab.driver */
    static String appName(CharSequence pkg) {
        String p = pkg == null ? "" : pkg.toString().toLowerCase(Locale.ROOT);
        if (p.contains("uber")) return "UBER";
        if (p.contains("mtakso") || p.contains("bolt")) return "BOLT";
        return "TEST";
    }

    private void collect(AccessibilityNodeInfo n, StringBuilder sb, int[] count, int depth) {
        if (n == null || depth > 40 || count[0] > MAX_NODES) return;
        count[0]++;
        CharSequence t = n.getText();
        if (t != null && t.length() > 0) sb.append(t).append(" | ");
        CharSequence d = n.getContentDescription();
        if (d != null && d.length() > 0 && (t == null || !d.toString().contentEquals(t))) sb.append(d).append(" | ");
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo c = n.getChild(i);
            if (c != null) collect(c, sb, count, depth + 1);
        }
    }

    // ---------- Calcul ----------

    static final class Result {
        double net, fuel, netPerKm, grossPerKm, perHour, totalKm, totalMin;
        int color;
        String label;
    }

    static Result compute(OfferParser.Offer o, Prefs p) {
        Result r = new Result();
        r.totalKm = o.totalKm();
        r.totalMin = o.totalMin();
        r.fuel = r.totalKm * p.consum / 100.0 * p.pretComb;
        double afterCommission = o.price * (1 - p.comision / 100.0);
        r.net = afterCommission - r.fuel;
        r.netPerKm = r.net / r.totalKm;
        r.grossPerKm = afterCommission / r.totalKm;
        r.perHour = r.totalMin > 0 ? r.net / (r.totalMin / 60.0) : 0;
        if (r.netPerKm >= p.pragBun) { r.color = GREEN; r.label = "BUNĂ"; }
        else if (r.netPerKm >= p.pragOk) { r.color = YELLOW; r.label = "OK"; }
        else { r.color = RED; r.label = "SLABĂ"; }
        return r;
    }

    // ---------- HUD ----------

    private boolean demoUber = false;

    void showDemo() {
        OfferParser.Offer o = new OfferParser.Offer();
        o.price = 45.0; o.hasPickup = true;
        o.pickupKm = 3.5; o.pickupMin = 11; o.tripKm = 6.2; o.tripMin = 23; o.multiplier = 1.3;
        o.app = demoUber ? "UBER" : "BOLT";
        demoUber = !demoUber; // demo-ul alternează Bolt / Uber
        dismissedKey = null;
        show(o);
        h.removeCallbacks(autoHide);
        h.postDelayed(autoHide, 8000);
    }

    private void show(OfferParser.Offer o) {
        if (wm == null) return;
        if (hud == null) buildHud();
        Result r = compute(o, prefs);
        shownKey = o.key();

        String mult = o.multiplier > 0 ? String.format(RO, " · ↑%.1fx", o.multiplier) : "";
        tvTop.setText(String.format(RO, "%s · %.2f lei%s", r.label, o.price, mult));
        tvTop.setTextColor(r.color);
        String app = o.app == null ? "TEST" : o.app;
        tvApp.setText(app);
        if (app.equals("UBER")) {        // Uber: negru pe alb
            appBg.setColor(Color.WHITE);
            tvApp.setTextColor(Color.BLACK);
        } else if (app.equals("BOLT")) { // Bolt: verdele lor
            appBg.setColor(0xFF34D186);
            tvApp.setTextColor(0xFF0B0D10);
        } else {
            appBg.setColor(0xFF555B66);
            tvApp.setTextColor(Color.WHITE);
        }
        tvBig.setText(String.format(RO, "%.2f lei/km net", r.netPerKm));
        tvBig.setTextColor(r.color);
        tvMid.setText(String.format(RO, "%.1f km · %d min · %.0f lei/h · profit %.2f lei",
                r.totalKm, Math.round(r.totalMin), r.perHour, r.net));
        String small = o.hasPickup
                ? String.format(RO, "Client %.1f km · %d min  |  Cursă %.1f km · %d min  |  Comb. %.2f lei",
                    o.pickupKm, Math.round(o.pickupMin), o.tripKm, Math.round(o.tripMin), r.fuel)
                : String.format(RO, "Cursă %.1f km · %d min  |  Comb. %.2f lei",
                    o.tripKm, Math.round(o.tripMin), r.fuel);
        tvSmall.setText(small);
        bg.setStroke(dp(3), r.color);

        if (hud.getParent() == null) {
            try { wm.addView(hud, lp); } catch (Exception ignored) {}
        }
    }

    void hide() {
        h.removeCallbacks(autoHide);
        shownKey = null;
        if (hud != null && hud.getParent() != null) {
            try { wm.removeView(hud); } catch (Exception ignored) {}
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void buildHud() {
        hud = new LinearLayout(this);
        hud.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(14);
        hud.setPadding(pad, dp(10), pad, dp(10));
        bg = new GradientDrawable();
        bg.setColor(0xF2111418);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(3), GREEN);
        hud.setBackground(bg);

        tvTop = tv(16, true);
        tvApp = tv(14, true);
        tvApp.setPadding(dp(8), dp(3), dp(8), dp(3));
        tvApp.setLetterSpacing(0.08f);
        appBg = new GradientDrawable();
        appBg.setCornerRadius(dp(6));
        tvApp.setBackground(appBg);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(tvApp);
        LinearLayout.LayoutParams topLp = new LinearLayout.LayoutParams(-2, -2);
        topLp.leftMargin = dp(8);
        row.addView(tvTop, topLp);
        tvBig = tv(30, true);
        tvMid = tv(15, true);
        tvMid.setTextColor(Color.WHITE);
        tvSmall = tv(12, false);
        tvSmall.setTextColor(0xFFB8C0CC);
        hud.addView(row);
        hud.addView(tvBig);
        hud.addView(tvMid);
        hud.addView(tvSmall);

        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.horizontalMargin = 0.03f;
        lp.y = prefs.hudY >= 0 ? prefs.hudY : dp(90);

        // Trage în sus/jos ca să-l muți, atinge scurt ca să-l închizi.
        hud.setOnTouchListener(new View.OnTouchListener() {
            float startRawY; int startY; long downAt; boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent ev) {
                switch (ev.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startRawY = ev.getRawY(); startY = lp.y; downAt = SystemClock.uptimeMillis(); moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dy = ev.getRawY() - startRawY;
                        if (Math.abs(dy) > dp(8)) moved = true;
                        if (moved) {
                            lp.y = Math.max(0, startY + (int) dy);
                            try { wm.updateViewLayout(hud, lp); } catch (Exception ignored) {}
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (moved) {
                            Prefs.saveHudY(HudService.this, lp.y);
                            prefs.hudY = lp.y;
                        } else if (SystemClock.uptimeMillis() - downAt < 500) {
                            dismissedKey = shownKey;
                            hide();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private TextView tv(float sp, boolean bold) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setIncludeFontPadding(false);
        t.setPadding(0, dp(2), 0, dp(2));
        return t;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
