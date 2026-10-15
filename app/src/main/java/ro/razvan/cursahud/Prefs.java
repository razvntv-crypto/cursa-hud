package ro.razvan.cursahud;

import android.content.Context;
import android.content.SharedPreferences;

/** Setările utilizatorului. */
public final class Prefs {
    public double consum;      // L/100km
    public double pretComb;    // lei/L
    public double comision;    // % scos din preț (Bolt arată deja NET → 0)
    public double pragBun;     // lei/km net
    public double pragOk;      // lei/km net
    public boolean modTest;    // citește din orice aplicație
    public int hudY;           // poziția verticală în px

    private static final String FILE = "cursahud";

    public static Prefs load(Context c) {
        SharedPreferences sp = c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        Prefs p = new Prefs();
        p.consum = sp.getFloat("consum", 7.0f);
        p.pretComb = sp.getFloat("pretComb", 7.5f);
        p.comision = sp.getFloat("comision", 0f);
        p.pragBun = sp.getFloat("pragBun", 3.0f);
        p.pragOk = sp.getFloat("pragOk", 2.0f);
        p.modTest = sp.getBoolean("modTest", false);
        p.hudY = sp.getInt("hudY", -1);
        return p;
    }

    public void save(Context c) {
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putFloat("consum", (float) consum)
                .putFloat("pretComb", (float) pretComb)
                .putFloat("comision", (float) comision)
                .putFloat("pragBun", (float) pragBun)
                .putFloat("pragOk", (float) pragOk)
                .putBoolean("modTest", modTest)
                .putInt("hudY", hudY)
                .apply();
    }

    public static void saveHudY(Context c, int y) {
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt("hudY", y).apply();
    }
}
