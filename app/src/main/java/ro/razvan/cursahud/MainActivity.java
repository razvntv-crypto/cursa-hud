package ro.razvan.cursahud;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {

    private static final int BG = 0xFF0B0D10;
    private static final int CARD = 0xFF161A20;
    private static final int GREEN = 0xFF3DDC84;
    private static final int MUTED = 0xFF9AA4B2;

    private TextView status;
    private EditText eConsum, ePret, eComision, eBun, eOk;
    private Switch sTest;
    private Prefs prefs;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs = Prefs.load(this);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(18);
        root.setPadding(p, dp(28), p, dp(28));
        sv.addView(root);

        TextView title = text("Cursa HUD", 28, Color.WHITE, true);
        root.addView(title);
        root.addView(text("Lei/km net, km total și timp total peste oferta Bolt/Uber.", 14, MUTED, false));

        // Status serviciu
        LinearLayout c1 = card(root);
        status = text("", 16, Color.WHITE, true);
        c1.addView(status);
        c1.addView(text("Pe Android 13+, la APK-uri instalate manual: dacă butonul serviciului e gri, intră la Info aplicație → ⋮ (sus-dreapta) → „Permite setări restricționate”, apoi activează din nou.", 13, MUTED, false));
        c1.addView(button("Activează serviciul", true, v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        c1.addView(button("Info aplicație (setări restricționate)", false, v ->
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())))));

        // Setări
        LinearLayout c2 = card(root);
        c2.addView(text("Mașina", 18, Color.WHITE, true));
        eConsum = field(c2, "Consum (L/100 km)", prefs.consum);
        ePret = field(c2, "Preț combustibil (lei/L)", prefs.pretComb);
        eComision = field(c2, "Comision extra (%) — Bolt arată deja NET, lasă 0", prefs.comision);

        LinearLayout c3 = card(root);
        c3.addView(text("Praguri (lei/km net)", 18, Color.WHITE, true));
        eBun = field(c3, "Verde de la", prefs.pragBun);
        eOk = field(c3, "Galben de la (sub asta e roșu)", prefs.pragOk);

        LinearLayout c4 = card(root);
        sTest = new Switch(this);
        sTest.setText("Mod test: citește din orice aplicație");
        sTest.setTextColor(Color.WHITE);
        sTest.setChecked(prefs.modTest);
        c4.addView(sTest);
        c4.addView(text("Pentru probă: scrie într-o aplicație de notițe „45,00 lei NET 11 min • 3.5 km 23 min • 6.2 km Acceptă” și HUD-ul ar trebui să apară. Oprește-l după.", 13, MUTED, false));
        sTest.setOnCheckedChangeListener((CompoundButton cb, boolean on) -> save(false));

        root.addView(button("Salvează", true, v -> save(true)));
        root.addView(button("Arată HUD demo", false, v -> {
            save(false);
            HudService s = HudService.instance;
            if (s == null) Toast.makeText(this, "Activează întâi serviciul", Toast.LENGTH_SHORT).show();
            else s.showDemo();
        }));

        root.addView(text("HUD: trage-l sus/jos ca să-l muți, atinge-l ca să-l închizi. Nu apasă nimic în Bolt/Uber și nu trimite date nicăieri.", 12, MUTED, false));

        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean on = isServiceEnabled();
        status.setText(on ? "● Serviciu activ" : "● Serviciu oprit");
        status.setTextColor(on ? GREEN : 0xFFFF5252);
    }

    private void save(boolean toast) {
        prefs.consum = num(eConsum, prefs.consum);
        prefs.pretComb = num(ePret, prefs.pretComb);
        prefs.comision = num(eComision, prefs.comision);
        prefs.pragBun = num(eBun, prefs.pragBun);
        prefs.pragOk = num(eOk, prefs.pragOk);
        prefs.modTest = sTest.isChecked();
        prefs.save(this);
        HudService s = HudService.instance;
        if (s != null) s.reloadPrefs();
        if (toast) Toast.makeText(this, "Salvat", Toast.LENGTH_SHORT).show();
    }

    private boolean isServiceEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        String me = new ComponentName(this, HudService.class).flattenToString();
        String meShort = new ComponentName(this, HudService.class).flattenToShortString();
        for (String s : enabled.split(":")) {
            if (s.equalsIgnoreCase(me) || s.equalsIgnoreCase(meShort)) return true;
        }
        return false;
    }

    // ---------- UI helpers ----------

    private LinearLayout card(LinearLayout parent) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        c.setPadding(p, p, p, p);
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(16));
        c.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(14);
        parent.addView(c, lp);
        return c;
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private EditText field(LinearLayout parent, String label, double value) {
        parent.addView(text(label, 13, MUTED, false));
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setText(String.format(Locale.US, "%s", trim(value)));
        e.setTextColor(Color.WHITE);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        parent.addView(e);
        return e;
    }

    private Button button(String label, boolean primary, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(primary ? 0xFF0B0D10 : Color.WHITE);
        b.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(primary ? GREEN : 0xFF242A33);
        g.setCornerRadius(dp(14));
        b.setBackground(g);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.topMargin = dp(12);
        b.setLayoutParams(lp);
        return b;
    }

    private static String trim(double v) {
        String s = String.valueOf((float) v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    private static double num(EditText e, double fallback) {
        try {
            return Double.parseDouble(e.getText().toString().trim().replace(',', '.'));
        } catch (Exception ex) {
            return fallback;
        }
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
