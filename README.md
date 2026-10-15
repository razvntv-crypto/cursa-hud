# Cursa HUD

Aplicație Android care afișează un HUD peste oferta de cursă din Bolt/Uber:

- **lei/km net** (preț NET minus combustibil, împărțit la km total)
- **km total** (până la client + cursă) și **timp total**
- lei/h, profit, multiplicator, culoare verde / galben / roșu după pragurile tale

Doar afișare: nu acceptă și nu refuză curse, nu trimite date nicăieri.

## Instalare

1. Descarcă `CursaHUD.apk` din [Releases → latest](../../releases/tag/latest) direct pe telefon.
2. Instalează (permite instalarea din surse necunoscute pentru browser).
3. Deschide aplicația → **Activează serviciul** → Cursa HUD → pornit.
   - Dacă e gri (Android 13+): Info aplicație → ⋮ → **Permite setări restricționate**, apoi din nou.
4. Setează consumul și prețul combustibilului, apasă **Arată HUD demo** ca să verifici.

## Cum funcționează

Un Accessibility Service citește textul ofertei (`45,00 lei (NET…)`, `11 min • 3.5 km`, `23 min • 6.2 km`),
calculează și afișează un overlay. Fiecare `git push` pe `main` construiește APK-ul automat prin GitHub Actions.
