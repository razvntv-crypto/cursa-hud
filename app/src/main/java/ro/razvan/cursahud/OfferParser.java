package ro.razvan.cursahud;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scoate preț, km și minute din textul ecranului de ofertă (Bolt / Uber). Fără dependențe Android. */
public final class OfferParser {

    public static final class Offer {
        public double price;
        public double pickupKm, pickupMin;
        public double tripKm, tripMin;
        public double multiplier; // 0 dacă nu există
        public boolean hasPickup;
        public String app; // "BOLT" / "UBER" / "TEST"

        public double totalKm() { return pickupKm + tripKm; }
        public double totalMin() { return pickupMin + tripMin; }

        public String key() {
            return app + "|" + price + "|" + pickupKm + "|" + pickupMin + "|" + tripKm + "|" + tripMin;
        }
    }

    private static final String NUM = "(\\d{1,4}(?:[.,]\\d{1,2})?)";
    private static final String SEP = "\\s*[\\u2022\\u00B7\\u2219\\u22C5.,\\-|/]?\\s*\\(?\\s*";

    // „45,00 lei” / „45.00 RON”
    private static final Pattern PRICE_AFTER = Pattern.compile(NUM + "\\s*(?:lei|ron)\\b");
    // „RON 25,30” / „lei 25,30”
    private static final Pattern PRICE_BEFORE = Pattern.compile("\\b(?:ron|lei)\\s*" + NUM);
    // „11 min • 3.5 km”, „5 min (1.2 km)”
    private static final Pattern LEG_MIN_KM = Pattern.compile(
            "(\\d{1,3})\\s*min\\w*" + SEP + "(\\d{1,3}(?:[.,]\\d{1,2})?)\\s*km\\b");
    // „3.5 km • 11 min”
    private static final Pattern LEG_KM_MIN = Pattern.compile(
            "(\\d{1,3}(?:[.,]\\d{1,2})?)\\s*km\\w*" + SEP + "(\\d{1,3})\\s*min\\b");
    private static final Pattern MULT = Pattern.compile("(\\d[.,]\\d{1,2})\\s*x\\b");

    private static final String[] OFFER_WORDS = {"accept", "refuz", "declin", "respinge", "ignora"};

    private OfferParser() {}

    public static String normalize(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT).replace(' ', ' ');
    }

    /** @param raw textul tuturor nodurilor, despărțit prin " | " */
    public static Offer parse(String raw, boolean requireOfferWords) {
        if (raw == null || raw.isEmpty()) return null;
        String t = normalize(raw);

        if (requireOfferWords) {
            boolean any = false;
            for (String w : OFFER_WORDS) if (t.contains(w)) { any = true; break; }
            if (!any) return null;
        }

        double price = findPrice(t);
        if (price <= 0) return null;

        List<double[]> legs = findLegs(t); // fiecare: {min, km}
        if (legs.isEmpty()) return null;

        Offer o = new Offer();
        o.price = price;
        if (legs.size() >= 2) {
            o.hasPickup = true;
            o.pickupMin = legs.get(0)[0];
            o.pickupKm = legs.get(0)[1];
            o.tripMin = legs.get(1)[0];
            o.tripKm = legs.get(1)[1];
        } else {
            o.tripMin = legs.get(0)[0];
            o.tripKm = legs.get(0)[1];
        }
        if (o.totalKm() <= 0) return null;

        Matcher m = MULT.matcher(t);
        if (m.find()) {
            double x = num(m.group(1));
            if (x > 1.0 && x < 5.0) o.multiplier = x;
        }
        return o;
    }

    private static double findPrice(String t) {
        // Preferă prețul urmat de „net” (cum scrie Bolt), altfel primul găsit.
        double first = -1;
        int firstPos = Integer.MAX_VALUE;
        for (Pattern p : new Pattern[]{PRICE_AFTER, PRICE_BEFORE}) {
            Matcher m = p.matcher(t);
            while (m.find()) {
                double v = num(m.group(1));
                if (v < 3 || v > 5000) continue;
                String after = t.substring(m.end(), Math.min(t.length(), m.end() + 12));
                if (after.contains("net")) return v;
                if (m.start() < firstPos) { firstPos = m.start(); first = v; }
            }
        }
        return first;
    }

    private static List<double[]> findLegs(String t) {
        List<int[]> pos = new ArrayList<>();
        List<double[]> legs = new ArrayList<>();
        Matcher a = LEG_MIN_KM.matcher(t);
        while (a.find()) {
            legs.add(new double[]{num(a.group(1)), num(a.group(2))});
            pos.add(new int[]{a.start()});
        }
        if (legs.isEmpty()) {
            Matcher b = LEG_KM_MIN.matcher(t);
            while (b.find()) {
                legs.add(new double[]{num(b.group(2)), num(b.group(1))});
                pos.add(new int[]{b.start()});
            }
        }
        return legs;
    }

    private static double num(String s) {
        try {
            return Double.parseDouble(s.replace(',', '.'));
        } catch (Exception e) {
            return 0;
        }
    }
}
