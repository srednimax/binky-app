# Play store listing copy

The paste-ready source of truth for every Play Console listing field. `docs/PLAN.md` 3a records *why*
the title and short description are what they are; this file is what actually gets copied in.

**Scope is 1.0 — the end of Phase 3.** Weight, observations, photos, backup, two languages. Care
reminders are 1.1 and vet visits, medications and documents are 1.2, so they must not be described as
present. Play treats "advertises features the app does not have" as a listing violation, not a
rounding error.

Two rules bend on purpose here:

- **"Rabbit" is used freely**, though `CONTEXT.md` reserves the vocabulary for code and UI. The listing
  is a *search surface*: title, short description and full description are the only indexed text Play
  has, and owners search both words. The title already makes this trade deliberately.
- **Nothing implies diagnosis.** The closing paragraph is not boilerplate — it is ADR-0001 stated to
  the person deciding whether to install, and it is also what keeps the listing clear of Play's
  medical-claims policy.

Polish is not a translation. Each locale is indexed separately, so the Polish copy carries its own
keywords (`królik`, `waga`, `bobki`, `dzienniczek zdrowia`, `weterynarz`) and its own phrasing.
Polish second-person forms are gendered, so the copy avoids them — `co zostało zapisane` rather than
`co zapisałeś`, which would address only half the audience.

---

## Fields shared across both locales

| Field | Value |
| --- | --- |
| Privacy policy URL | `https://srednimax.github.io/binky-app/privacy-policy.html` |
| App icon (512²) | [`art/play-icon-512.png`](../art/play-icon-512.png) — placeholder, see [`art/README.md`](../art/README.md) |
| App category | Lifestyle |
| Contact email | the per-app support address, set in Store settings — not the account-level developer email |

**Lifestyle, not Health & Fitness**, even though the app tracks health data. Health & Fitness is
oriented at *human* health, where Play applies closer scrutiny to anything that reads as a medical
claim — and this app deliberately makes none (ADR-0001). Pet-care apps conventionally sit in
Lifestyle, so it is also where owners browsing for one will look.

The privacy policy is served by GitHub Pages from `main` / `docs`, which is why `docs/_config.yml`
exists at all: Play requires a *hosted* URL and the app has no server by design. It rebuilds on every
push to `main`, so the policy can never drift from the repo. The site root has no `index.md` and
returns 404 on purpose — this site serves one page.

---

## English (default listing language)

### App name — 29/30

```
Binky: Bunny & Rabbit Tracker
```

### Short description — 70/80

```
Track your rabbit's weight, health and care. Private, offline, no ads.
```

### Full description — 2396/4000

```
Binky keeps a trustworthy record of your rabbit's health, so you and your vet can see what actually changed, and when.

Rabbits hide illness. By the time something is obvious it is often urgent. A written record turns "he's seemed a bit quiet lately" into a date, a weight and a note you can show someone.

WEIGHT, PLOTTED HONESTLY
• Enter weight in grams, which is what your scale shows
• Display in kilograms or grams, your choice, but changes are always shown in grams, because -40 g tells you what -0.04 kg hides
• The chart plots real dates rather than evenly spaced entries, so irregular weighing cannot flatter or fake a trend
• Each new weight is compared against a baseline drawn from several recent weighings, not just the last one

OBSERVATIONS, WHEN YOU NOTICE THEM
Record droppings (amount, size, form), cecotropes, appetite, mood, activity, water, symptoms and free-text notes. Nothing is on a schedule. Log several in a day, or none.

Fields you do not touch mean "not checked", never "normal". A reassurance nobody verified is worse than no record at all.

Ordinary day? "Log a healthy day" records normal droppings, cecotropes eaten and no symptoms in a single tap.

MORE THAN ONE RABBIT
• Track as many as you like, each with its own photo and history
• Rabbits that share a litter tray can be marked as living together, and one observation can cover all of them, because droppings from a shared tray cannot honestly be attributed to one animal
• Archive a rabbit that has died or been rehomed. Its records are kept and stay readable, never quietly deleted

PHOTOS
A gallery for each rabbit, for pictures kept simply because you want them.

YOUR DATA STAYS YOURS
• No account, no sign-up, no server. There is no backend to send anything to
• No ads, no tracking, no analytics, no in-app purchases
• Works fully offline, permanently
• Export a backup whenever you want and restore it. Your records are portable, not trapped
• Free, with no paid tier and nothing locked

English and Polish, switchable inside the app.

A RECORD, NOT A DIAGNOSIS
Binky will not tell you whether your rabbit is ill, and it never infers a problem from missing entries. Silence means nobody looked, not that everything is fine. It shows you what you recorded, clearly enough to notice a change and take it to someone qualified. If you are worried about your rabbit, see a vet experienced with rabbits.
```

---

## Polish

### App name — 22/30

```
Binky: Zdrowie Królika
```

### Short description — 74/80

```
Dzienniczek zdrowia królika: waga, obserwacje, bobki. Offline, bez reklam.
```

### Full description — 2543/4000

```
Binky prowadzi wiarygodny dziennik zdrowia Twojego królika, żebyś razem z weterynarzem widział to, co naprawdę się zmieniło i kiedy.

Króliki ukrywają chorobę. Kiedy objawy stają się oczywiste, zwykle jest już pilnie. Zapisany dziennik zmienia "ostatnio jakiś cichy" w konkretną datę, wagę i notatkę, którą można komuś pokazać.

WAGA, POKAZANA UCZCIWIE
• Wagę wpisuje się w gramach, bo tyle pokazuje waga kuchenna
• Wyświetlanie w kilogramach albo gramach, do wyboru, ale zmiany zawsze w gramach, bo -40 g mówi to, co -0,04 kg ukrywa
• Wykres nanosi prawdziwe daty, a nie równo rozłożone wpisy, więc nieregularne ważenie nie zafałszuje trendu
• Każde nowe ważenie porównywane jest z linią odniesienia z kilku ostatnich pomiarów, nie tylko z poprzednim

OBSERWACJE, KIEDY COŚ ZWRÓCI TWOJĄ UWAGĘ
Zapisuj bobki (ilość, wielkość, kształt), cekotrofy, apetyt, nastrój, aktywność, picie wody, objawy i własne notatki. Nic nie jest wymuszone harmonogramem. Może być kilka wpisów dziennie albo żaden.

Pola nietknięte znaczą "nie sprawdzono", nigdy "w normie". Zapewnienie, którego nikt nie sprawdził, jest gorsze niż brak wpisu.

Zwykły dzień? "Zapisz zdrowy dzień" jednym dotknięciem odnotowuje prawidłowe bobki, zjedzone cekotrofy i brak objawów.

WIĘCEJ NIŻ JEDEN KRÓLIK
• Tyle królików, ile trzeba, każdy ze swoim zdjęciem i historią
• Króliki dzielące kuwetę można oznaczyć jako mieszkające razem, a jedna obserwacja obejmie je wszystkie, bo bobków ze wspólnej kuwety nie da się uczciwie przypisać jednemu zwierzęciu
• Archiwizuj królika, który odszedł albo trafił do nowego domu. Jego zapisy zostają i nadal można je czytać, nic nie znika po cichu

ZDJĘCIA
Galeria dla każdego królika, na zdjęcia trzymane po prostu dlatego, że się chce.

TWOJE DANE ZOSTAJĄ U CIEBIE
• Bez konta, bez rejestracji, bez serwera. Nie ma dokąd wysyłać danych
• Bez reklam, bez śledzenia, bez analityki, bez zakupów w aplikacji
• Działa w pełni offline, na stałe
• Kopię zapasową można wyeksportować w dowolnej chwili i przywrócić. Zapisy są przenośne, nie uwięzione
• Za darmo, bez wersji płatnej i bez blokad

Polski i angielski, przełączane w aplikacji.

DZIENNIK, NIE DIAGNOZA
Binky nie powie Ci, czy Twój królik jest chory, i nigdy nie wnioskuje problemu z braku wpisów. Cisza znaczy, że nikt nie sprawdził, a nie że wszystko jest dobrze. Pokazuje to, co zostało zapisane, na tyle czytelnie, żeby zmiana rzucała się w oczy i trafiła do kogoś, kto się na tym zna. Jeśli martwisz się o swojego królika, zgłoś się do weterynarza doświadczonego w leczeniu królików.
```

---

## Open

- **Screenshots** are placeholders until 3g. Two of whatever exists is what 3a calls for; real 1.0
  screenshots are taken once the app has stopped changing.
- **Feature graphic** (1024×500) is still to be made.
- **The icon is a placeholder and blocks the upload until the app can show a licence notice.** It is
  Noto Emoji's rabbit under the OFL, which requires the licence text to reach the user; there is no
  licences screen yet. Either that screen exists before the AAB goes up, or the art is replaced with
  something original. [`art/README.md`](../art/README.md) has the reasoning.
- **A roadmap line** naming 1.1 and 1.2 features was deliberately left out. It manages expectations
  for reviewers who might otherwise mark the app down for having no reminders, but it also puts
  unreleased features in indexed listing text. Add it later if reviews ask for it, not before.
