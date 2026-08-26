# Play store listing copy

The paste-ready source of truth for every Play Console listing field. `docs/PLAN.md` 3a records *why*
the title and short description are what they are; this file is what actually gets copied in.

**Scope is 1.6, rewritten at Phase 8** — the whole app: weight and its trend flag, observations,
care reminders and the watch, vet visits, medications and doses, scanned documents, photos, backup,
**in nine languages**. It was cut at 1.0 until 2026-08-17, describing an app with no care reminders,
no vet visits and no medications, because 1.0 was the only build a listing had ever been written for.

⚠️ **This copy must not go up before the build that carries these features is on the track it is
describing.** Play treats "advertises features the app does not have" as a listing violation, not a
rounding error, and the tracks are still on 1.0.0 / 1.3 (`DOD.md` §4). The listing and the release go
up together; that is the only rule this file has ever had, and it now points the other way from the way
it used to.

**Why nine locales were written in one sitting rather than one per release.** Each locale is indexed
separately and there is **no fallback for discovery** — Play will serve the default listing's
screenshots to a German browser, but an untranslated *description* means nobody searching in German
finds the app at all. So the text is not staged the way the app's strings were.

Two rules bend on purpose here:

- **"Rabbit" is used freely**, though `CONTEXT.md` reserves the vocabulary for code and UI. The listing
  is a *search surface*: title, short description and full description are the only indexed text Play
  has, and owners search both words. The title already makes this trade deliberately.
- **Nothing implies diagnosis.** The closing paragraph is not boilerplate — it is ADR-0001 stated to
  the person deciding whether to install, and it is also what keeps the listing clear of Play's
  medical-claims policy. Every locale carries it, and it is the paragraph to check first in any locale
  that gets rewritten.

**No locale is a translation of another.** Each carries its own keywords — Polish `królik`, `waga`,
`bobki`; German `Kaninchen`, `Köttel`, `Gesundheitstagebuch`; Czech `králík`, `bobky`, `zdraví` — and
the vocabulary each language's own file settled on ([`phase-8.md`](phase-8.md) holds the per-language
record and the fallback for every contested word). Where the app says *Köttel*, *cagarrutas*,
*crottes*, *palline*, *bolinhas*, *bobky*, *котяхи*, so does the listing: a listing that uses the
clinical word for the thing the app calls something else reads as a different app.

⚠️ **French and Italian have no headroom left** — 3992 and 3993 of 4000 characters, against English's
3628. Romance prose runs ~10% longer than the English it is written from, and French was 4134 on the first
pass and had to be trimmed by thirteen edits that cut words rather than claims. **A paragraph added to the
English description cannot simply be translated into those two**; something has to come out. The counts in
each heading below are measured, not estimated — `docs/store-listing.md` is checked by hand against Play's
limits (30 / 80 / 4000) whenever it changes.

**Second person is avoided or kept genderless in every locale.** Polish, Czech and Ukrainian past
tenses agree with the addressee and the app knows nobody's gender (`phase-8.md` §7.3) — so
`co zostało zapisane` rather than `co zapisałeś`, and the same discipline is applied everywhere else
for consistency rather than because those languages need it.

---

## Fields shared across both locales

| Field | Value |
| --- | --- |
| Privacy policy URL | `https://srednimax.github.io/binky-app/privacy-policy.html` |
| App icon (512²) | [`art/play-icon-512.png`](../art/play-icon-512.png) |
| Feature graphic (1024×500) | [`art/play-feature-graphic.png`](../art/play-feature-graphic.png) |
| Phone screenshots (1452×2582) | [`art/play-screenshots/`](../art/play-screenshots/) — **eight scenes × nine locales, light**, re-shot 2026-08-26. Upload in filename order: `1_home`, `2_weight`, `3_observations`, `4_timeline`, `5_care`, `6_medication-course`, `7_documents`, `8_backup`, each suffixed with its locale tag (`-en`, `-cs`, `-de`, `-es`, `-fr`, `-it`, `-pl`, `-pt-BR`, `-uk`) |
| App category | Lifestyle |
| Contact email | `binky.support@gmail.com` — the per-app support address, set in Store settings, not the account-level developer email |
| Website | `https://srednimax.github.io/binky-app/` — the Pages root 9e created, also in Store settings. The privacy-policy URL above is a page *under* it |

✅ **The screenshots were re-shot on 2026-08-26** and photograph the app as 1.9.0 builds it — 9f's
Home header, 10e's timeline and the Phase 7 light scheme included. **Eight per locale, not four**,
which is Play's maximum for phone screenshots. Entering them in the Console is a separate box
(`DOD.md` §4).

**Lifestyle, not Health & Fitness**, even though the app tracks health data. Health & Fitness is
oriented at *human* health, where Play applies closer scrutiny to anything that reads as a medical
claim — and this app deliberately makes none (ADR-0001). Pet-care apps conventionally sit in
Lifestyle, so it is also where owners browsing for one will look.

**The contact email is `binky.support@gmail.com` and cannot be anything else from 1.3.** Phase 6's
Support screen hardcodes that address in `SupportHandoff.kt` — it is what both mail buttons open and what
the screen renders as selectable text — and the privacy policy's *Contact* section defers to "the developer
email address listed on the app's Google Play listing". So this field is the hinge the other two hang on:
set it to anything else and the listing points a reader at a mailbox the app itself never uses. See
[`play-app-content.md`](play-app-content.md) and [`phase-6.md`](phase-6.md).

The privacy policy is served by GitHub Pages from `main` / `docs`, which is why `docs/_config.yml`
exists at all: Play requires a *hosted* URL and the app has no server by design. It rebuilds on every
push to `main`, so the policy can never drift from the repo. ⚠️ **The site root is no longer a 404.**
This paragraph said it returned one "on purpose" until 2026-08-21, which was true when the site served
exactly one page; **9e added `docs/index.md`**, so the root now answers 200 and is what the Website
field below points at.

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

### Full description — 3628/4000

```
Binky keeps a trustworthy record of your rabbit's health, so you and your vet can see what actually changed, and when.

Rabbits hide illness. By the time something is obvious it is often urgent. A written record turns "he's seemed a bit quiet lately" into a date, a weight and a note you can show someone.

WEIGHT, PLOTTED HONESTLY
• Enter weight in grams, which is what your scale shows
• Display in kilograms or grams, your choice, but changes are always shown in grams, because -40 g tells you what -0.04 kg hides
• The chart plots real dates rather than evenly spaced entries, so irregular weighing cannot flatter or fake a trend
• Each new weight is compared against a baseline drawn from several recent weighings, not just the last one
• A marked drop raises a flag, and so does a steady climb measured against where the rabbit was six months ago. Both are statements about the numbers, never a verdict about the rabbit

OBSERVATIONS, WHEN YOU NOTICE THEM
Record droppings — the amount, and as many sizes and shapes as the tray actually holds, with a photo of it when that says more than words — plus cecotropes, appetite, mood, activity, water, symptoms and free-text notes. Nothing is on a schedule. Log several in a day, or none.

Fields you do not touch mean "not checked", never "normal". A reassurance nobody verified is worse than no record at all.

Ordinary day? "Log a healthy day" records normal droppings, cecotropes eaten and no symptoms in a single tap.

CARE THAT COMES ROUND AGAIN
• Nail trims, tray cleans, hay orders, weigh-ins, or anything you name yourself, on whatever interval suits
• A reminder when one is due, and a record of when it was last done
• Hand any reminder to your own calendar if that is where you keep things
• Watch a rabbit closely for a few days after something looked off, and let the watch end by itself

VET, MEDICATIONS AND PAPERWORK
• The vets you use and the visits you make, with the weight taken at the visit kept alongside it
• Medication courses, with every dose worked out for you and a reminder before each one
• Record a dose as given, or as deliberately not given. The record says which, because those are different facts
• Scan discharge notes, results and invoices with the phone's camera and keep them with the visit they belong to

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

Nine languages, switchable inside the app without changing your phone: English, Polski, Deutsch, Español, Français, Italiano, Português (Brasil), Čeština, Українська.

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

### Full description — 3681/4000

```
Binky prowadzi wiarygodny dziennik zdrowia Twojego królika, żeby razem z weterynarzem było widać to, co naprawdę się zmieniło i kiedy.

Króliki ukrywają chorobę. Kiedy objawy stają się oczywiste, zwykle jest już pilnie. Zapisany dziennik zmienia "ostatnio jakiś cichy" w konkretną datę, wagę i notatkę, którą można komuś pokazać.

WAGA, POKAZANA UCZCIWIE
• Wagę wpisuje się w gramach, bo tyle pokazuje waga kuchenna
• Wyświetlanie w kilogramach albo gramach, do wyboru, ale zmiany zawsze w gramach, bo -40 g mówi to, co -0,04 kg ukrywa
• Wykres nanosi prawdziwe daty, a nie równo rozłożone wpisy, więc nieregularne ważenie nie zafałszuje trendu
• Każde nowe ważenie porównywane jest z linią odniesienia z kilku ostatnich pomiarów, nie tylko z poprzednim
• Wyraźny spadek podnosi flagę, a powolny wzrost mierzony wobec wagi sprzed pół roku tak samo. Jedno i drugie to zdanie o liczbach, nigdy wyrok o króliku

OBSERWACJE, KIEDY COŚ ZWRÓCI UWAGĘ
Zapisuj bobki — ilość oraz tyle wielkości i kształtów, ile naprawdę leży w kuwecie, ze zdjęciem kuwety, kiedy mówi więcej niż słowa — a do tego cekotrofy, apetyt, nastrój, aktywność, picie wody, objawy i własne notatki. Nic nie jest wymuszone harmonogramem. Może być kilka wpisów dziennie albo żaden.

Pola nietknięte znaczą "nie sprawdzono", nigdy "w normie". Zapewnienie, którego nikt nie sprawdził, jest gorsze niż brak wpisu.

Zwykły dzień? "Zapisz zdrowy dzień" jednym dotknięciem odnotowuje prawidłowe bobki, zjedzone cekotrofy i brak objawów.

OPIEKA, KTÓRA WRACA CO JAKIŚ CZAS
• Obcinanie pazurków, sprzątanie kuwety, zamawianie siana, ważenie albo cokolwiek nazwanego po swojemu, w wybranym odstępie
• Przypomnienie, kiedy termin nadchodzi, i zapis, kiedy było to robione ostatnio
• Każde przypomnienie można przekazać do własnego kalendarza
• Królika, z którym coś wyglądało nie tak, można wziąć na kilka dni pod baczną obserwację, a ta kończy się sama

WETERYNARZ, LEKI I DOKUMENTY
• Weterynarze i wizyty, razem z wagą zmierzoną na wizycie
• Kuracje lekowe z wyliczoną każdą dawką i przypomnieniem przed nią
• Dawkę można zapisać jako podaną albo jako świadomie pominiętą. Zapis mówi, która to była, bo to dwa różne fakty
• Wypisy, wyniki i faktury można zeskanować aparatem telefonu i trzymać przy właściwej wizycie

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

Dziewięć języków, przełączanych w samej aplikacji, bez zmieniania języka telefonu: English, Polski, Deutsch, Español, Français, Italiano, Português (Brasil), Čeština, Українська.

DZIENNIK, NIE DIAGNOZA
Binky nie powie, czy królik jest chory, i nigdy nie wnioskuje problemu z braku wpisów. Cisza znaczy, że nikt nie sprawdził, a nie że wszystko jest dobrze. Pokazuje to, co zostało zapisane, na tyle czytelnie, żeby zmiana rzucała się w oczy i trafiła do kogoś, kto się na tym zna. W razie niepokoju o królika — do weterynarza doświadczonego w leczeniu królików.
```

---

## German

### App name — 27/30

```
Binky: Kaninchen-Gesundheit
```

### Short description — 69/80

```
Gewicht, Gesundheit und Pflege des Kaninchens. Offline, ohne Werbung.
```

### Full description — 3901/4000

```
Binky führt ein verlässliches Gesundheitstagebuch für das Kaninchen, damit in der Tierarztpraxis zu sehen ist, was sich wirklich geändert hat und wann.

Kaninchen verbergen Krankheit. Wenn etwas offensichtlich wird, ist es oft schon dringend. Ein geschriebenes Tagebuch macht aus "war in letzter Zeit irgendwie ruhig" ein Datum, ein Gewicht und eine Notiz, die sich jemandem zeigen lässt.

GEWICHT, EHRLICH DARGESTELLT
• Eingabe in Gramm, weil die Waage Gramm anzeigt
• Anzeige wahlweise in Kilogramm oder Gramm, Änderungen aber immer in Gramm, denn -40 g sagt, was -0,04 kg verbirgt
• Die Kurve trägt echte Daten ab statt gleichmäßiger Abstände, damit unregelmäßiges Wiegen keinen Verlauf beschönigt
• Jedes neue Gewicht wird mit einem Ausgangswert aus mehreren letzten Wiegungen verglichen, nicht nur mit dem vorigen
• Ein deutlicher Rückgang wird hervorgehoben, ein stetiger Anstieg gegenüber dem Stand vor einem halben Jahr ebenso. Beides sind Aussagen über Zahlen, nie ein Urteil über das Tier

BEOBACHTUNGEN, WENN ETWAS AUFFÄLLT
Köttel festhalten — die Menge, und so viele Größen und Formen, wie wirklich im Klo liegen, mit einem Foto davon, wenn das mehr sagt als Worte —, dazu Blinddarmkot, Appetit, Stimmung, Aktivität, Wasser, Symptome und freie Notizen. Nichts folgt einem Zeitplan. Mehrere Einträge an einem Tag oder keiner.

Felder, die niemand angefasst hat, bedeuten "nicht geprüft", nie "in Ordnung". Eine Beruhigung, die niemand nachgesehen hat, ist schlechter als gar kein Eintrag.

Ganz normaler Tag? "Gesunden Tag eintragen" hält normale Köttel, gefressenen Blinddarmkot und keine Symptome mit einem Tippen fest.

PFLEGE, DIE WIEDERKEHRT
• Krallen schneiden, Klo reinigen, Heu bestellen, wiegen oder etwas selbst Benanntes, im gewählten Abstand
• Eine Erinnerung, wenn etwas ansteht, und der Vermerk, wann es zuletzt gemacht wurde
• Jede Erinnerung lässt sich an den eigenen Kalender übergeben
• Ein Kaninchen ein paar Tage im Blick behalten, wenn etwas komisch wirkte — der Zeitraum endet von selbst

TIERARZT, MEDIKAMENTE UND UNTERLAGEN
• Praxen und Besuche, mit dem dort gemessenen Gewicht daneben
• Medikationen mit ausgerechneter Gabe und einer Erinnerung vor jeder einzelnen
• Eine Gabe als gegeben oder als bewusst ausgelassen eintragen. Der Eintrag sagt, welches von beidem, denn das sind zwei verschiedene Tatsachen
• Befunde, Ergebnisse und Rechnungen mit der Kamera scannen und beim passenden Besuch ablegen

MEHR ALS EIN KANINCHEN
• Beliebig viele, jedes mit eigenem Foto und eigener Geschichte
• Kaninchen, die sich ein Klo teilen, lassen sich als zusammenlebend kennzeichnen, und eine Beobachtung kann für alle gelten — Köttel aus einem geteilten Klo lassen sich einem Tier nicht ehrlich zuordnen
• Ein verstorbenes oder abgegebenes Kaninchen archivieren. Seine Einträge bleiben erhalten und lesbar, nichts verschwindet still

FOTOS
Eine Galerie je Kaninchen, für Bilder, die einfach bleiben sollen.

DIE DATEN BLEIBEN AUF DEM GERÄT
• Kein Konto, keine Anmeldung, kein Server. Es gibt nichts, wohin Daten gesendet würden
• Keine Werbung, kein Tracking, keine Analyse, keine In-App-Käufe
• Dauerhaft vollständig offline
• Eine Sicherung lässt sich jederzeit exportieren und wieder einspielen. Die Einträge sind mitnehmbar, nicht eingesperrt
• Kostenlos, ohne Bezahlversion und ohne Sperren

Neun Sprachen, in der App umschaltbar, ohne das Telefon umzustellen: English, Polski, Deutsch, Español, Français, Italiano, Português (Brasil), Čeština, Українська.

EIN TAGEBUCH, KEINE DIAGNOSE
Binky sagt nicht, ob ein Kaninchen krank ist, und schließt nie aus fehlenden Einträgen auf ein Problem. Stille heißt, dass niemand nachgesehen hat, nicht dass alles gut ist. Gezeigt wird, was eingetragen wurde — deutlich genug, um eine Veränderung zu bemerken und sie jemandem mit Fachwissen vorzulegen. Bei Sorge um das Tier gehört es in eine Tierarztpraxis mit Kaninchenerfahrung.
```

---

## Spanish

### App name — 23/30

```
Binky: Salud del Conejo
```

### Short description — 70/80

```
Peso, salud y cuidados del conejo. Sin conexión, sin anuncios, gratis.
```

### Full description — 3932/4000

```
Binky lleva un registro fiable de la salud del conejo, para que en la consulta veterinaria se vea qué cambió de verdad y cuándo.

Los conejos esconden la enfermedad. Cuando algo se hace evidente, suele ser ya urgente. Un registro escrito convierte "últimamente estaba algo apagado" en una fecha, un peso y una nota que se puede enseñar a alguien.

EL PESO, MOSTRADO CON HONESTIDAD
• El peso se anota en gramos, que es lo que marca la báscula
• Se muestra en kilogramos o en gramos, a elección, pero los cambios siempre en gramos, porque -40 g dice lo que -0,04 kg esconde
• La gráfica sitúa fechas reales en lugar de entradas repartidas por igual, así que pesar de forma irregular no puede maquillar una tendencia
• Cada peso nuevo se compara con una referencia tomada de varias pesadas recientes, no solo con la anterior
• Una bajada marcada levanta un aviso, y una subida sostenida frente al peso de hace seis meses también. Ambos son una afirmación sobre los números, nunca un veredicto sobre el animal

OBSERVACIONES, CUANDO ALGO LLAMA LA ATENCIÓN
Anota las cagarrutas — la cantidad, y tantos tamaños y formas como haya de verdad en la bandeja, con una foto de ella cuando diga más que las palabras — y además cecotrofos, apetito, ánimo, actividad, agua, síntomas y notas libres. Nada sigue un horario. Varias entradas en un día, o ninguna.

Los campos que nadie toca significan "sin comprobar", nunca "normal". Una tranquilidad que nadie verificó es peor que no tener registro.

¿Un día corriente? "Registrar un día sano" anota cagarrutas normales, cecotrofos comidos y ningún síntoma con un solo toque.

CUIDADOS QUE VUELVEN CADA CIERTO TIEMPO
• Cortar las uñas, limpiar la bandeja, pedir heno, pesar, o cualquier cosa con el nombre que se le quiera dar, en el intervalo que convenga
• Un aviso cuando toca, y la constancia de cuándo se hizo por última vez
• Cualquier aviso se puede pasar al calendario propio
• Un conejo se puede llevar en seguimiento unos días si algo no cuadró, y el seguimiento termina solo

VETERINARIO, MEDICACIÓN Y PAPELES
• Los veterinarios y las visitas, con el peso tomado en la visita junto a ella
• Tratamientos con cada dosis calculada y un aviso antes de cada una
• Una dosis se registra como dada o como no dada a propósito. El registro dice cuál de las dos, porque son hechos distintos
• Informes, resultados y facturas se escanean con la cámara del móvil y se guardan con la visita a la que pertenecen

MÁS DE UN CONEJO
• Tantos como se quiera, cada uno con su foto y su historial
• Los conejos que comparten bandeja se pueden marcar como convivientes, y una sola observación vale para todos, porque las cagarrutas de una bandeja compartida no se pueden atribuir con honestidad a un solo animal
• Un conejo que ha muerto o ha cambiado de casa se archiva. Sus registros se conservan y se siguen pudiendo leer, nada desaparece en silencio

FOTOS
Una galería por conejo, para fotos que se guardan simplemente porque sí.

LOS DATOS SE QUEDAN EN EL MÓVIL
• Sin cuenta, sin registro, sin servidor. No hay ningún sitio al que enviar nada
• Sin anuncios, sin rastreo, sin analítica, sin compras dentro de la aplicación
• Funciona totalmente sin conexión, siempre
• La copia de seguridad se exporta cuando se quiera y se restaura. Los registros son portátiles, no quedan atrapados
• Gratis, sin versión de pago y sin nada bloqueado

Nueve idiomas, que se cambian dentro de la aplicación sin tocar el idioma del teléfono: English, Polski, Deutsch, Español, Français, Italiano, Português (Brasil), Čeština, Українська.

UN REGISTRO, NO UN DIAGNÓSTICO
Binky no dice si un conejo está enfermo, y nunca deduce un problema de las entradas que faltan. El silencio significa que nadie miró, no que todo vaya bien. Muestra lo que se anotó, con la claridad suficiente para notar un cambio y llevarlo a quien sepa. Ante la preocupación por un conejo, conviene acudir a un veterinario con experiencia en conejos.
```

---

## French

### App name — 22/30

```
Binky : Santé du Lapin
```

### Short description — 68/80

```
Poids, santé et soins du lapin. Hors ligne, sans publicité, gratuit.
```

### Full description — 3992/4000

```
Binky tient un carnet de santé fiable pour le lapin, afin que le vétérinaire voie ce qui a réellement changé, et quand.

Les lapins cachent la maladie. Quand quelque chose devient évident, c’est souvent déjà urgent. Un carnet écrit transforme « il était un peu éteint ces derniers temps » en une date, un poids et une note que l’on peut montrer à quelqu’un.

LE POIDS, PRÉSENTÉ HONNÊTEMENT
• Le poids se saisit en grammes, puisque c’est ce qu’affiche la balance
• Affichage en kilogrammes ou en grammes, au choix, mais les écarts toujours en grammes, parce que -40 g dit ce que -0,04 kg cache
• La courbe place de vraies dates plutôt que des points régulièrement espacés : une pesée irrégulière ne peut pas embellir une tendance
• Chaque nouveau poids est comparé à une référence tirée de plusieurs pesées récentes, et non de la seule précédente
• Une baisse nette est signalée, une hausse régulière mesurée face au poids d’il y a six mois également. Ce sont des constats sur les chiffres, jamais un verdict sur l’animal

OBSERVATIONS, QUAND QUELQUE CHOSE SE REMARQUE
Noter les crottes — la quantité, et autant de tailles et de formes qu’il y en a vraiment dans le bac, avec une photo quand elle en dit plus que les mots — ainsi que les caecotrophes, l’appétit, l’humeur, l’activité, l’eau, les symptômes et des notes libres. Rien ne suit un calendrier. Plusieurs entrées dans la journée, ou aucune.

Un champ que personne n’a touché veut dire « pas vérifié », jamais « normal ». Une réassurance que personne n’a contrôlée vaut moins que pas d’entrée du tout.

Une journée ordinaire ? « Noter une journée saine » enregistre des crottes normales, des caecotrophes mangés et aucun symptôme en une seule touche.

LES SOINS QUI REVIENNENT
• Couper les griffes, nettoyer le bac, commander du foin, peser, ou tout ce que l’on nomme soi-même, au rythme voulu
• Un rappel au moment voulu, et la trace de la dernière fois
• Chaque rappel peut être confié à son propre agenda
• Un lapin peut être placé en suivi rapproché quelques jours si quelque chose a paru anormal ; le suivi prend fin de lui-même

VÉTÉRINAIRE, MÉDICAMENTS ET DOCUMENTS
• Les vétérinaires et les consultations, avec le poids relevé sur place à côté
• Des traitements dont chaque prise est calculée, avec un rappel avant chacune
• Une prise se note comme donnée ou comme volontairement non donnée. Le carnet dit laquelle, parce que ce sont deux faits différents
• Comptes rendus, résultats et factures se scannent avec l’appareil photo et restent attachés à la consultation

PLUSIEURS LAPINS
• Autant que l’on veut, chacun avec sa photo et son historique
• Les lapins qui partagent un bac peuvent être signalés comme vivant ensemble, et une seule observation vaut pour tous : les crottes d’un bac partagé ne s’attribuent pas honnêtement à un seul animal
• Un lapin mort ou parti dans un autre foyer s’archive. Ses entrées sont conservées et restent lisibles, rien ne disparaît en silence

PHOTOS
Une galerie par lapin, pour des images gardées simplement pour le plaisir.

LES DONNÉES RESTENT SUR LE TÉLÉPHONE
• Aucun compte, aucune inscription, aucun serveur. Rien n’est envoyé nulle part
• Aucune publicité, aucun pistage, aucune analyse, aucun achat intégré
• Fonctionne entièrement hors ligne, en permanence
• Une sauvegarde s’exporte quand on veut et se restaure. Les entrées sont transportables, pas enfermées
• Gratuit, sans version payante et sans rien de verrouillé

Neuf langues, changeables dans l’application sans toucher à celle du téléphone : English, Polski, Deutsch, Español, Français, Italiano, Português (Brasil), Čeština, Українська.

UN CARNET, PAS UN DIAGNOSTIC
Binky ne dit pas si un lapin est malade, et ne déduit jamais un problème des entrées manquantes. Le silence signifie que personne n’a regardé, pas que tout va bien. Il montre ce qui a été noté, assez clairement pour qu’un changement se remarque et soit porté à quelqu’un de compétent. En cas d’inquiétude, consulter un vétérinaire habitué aux lapins.
```

---

## Italian

### App name — 26/30

```
Binky: Salute del Coniglio
```

### Short description — 68/80

```
Peso, salute e cure del coniglio. Offline, senza pubblicità, gratis.
```

### Full description — 3993/4000

```
Binky tiene un registro affidabile della salute del coniglio, così in ambulatorio si vede che cosa è cambiato davvero, e quando.

I conigli nascondono la malattia. Quando qualcosa diventa evidente, spesso è già urgente. Un registro scritto trasforma "ultimamente era un po' spento" in una data, un peso e una nota da mostrare a qualcuno.

IL PESO, MOSTRATO ONESTAMENTE
• Il peso si inserisce in grammi, perché è quello che segna la bilancia
• Visualizzazione in chilogrammi o in grammi, a scelta, ma le variazioni sempre in grammi, perché -40 g dice quello che -0,04 kg nasconde
• Il grafico riporta date reali invece di voci equidistanti, quindi una pesata irregolare non può abbellire un andamento
• Ogni nuovo peso viene confrontato con un riferimento ricavato da più pesate recenti, non solo con la precedente
• Un calo marcato viene segnalato, e così un aumento costante misurato rispetto a sei mesi prima. Entrambi sono affermazioni sui numeri, mai un verdetto sull'animale

OSSERVAZIONI, QUANDO QUALCOSA SALTA ALL'OCCHIO
Annota le palline — la quantità, e tutte le misure e le forme che ci sono davvero nella lettiera, con una foto quando dice più delle parole — e inoltre ciecotrofi, appetito, umore, attività, acqua, sintomi e note libere. Niente segue un calendario. Più voci in un giorno, oppure nessuna.

I campi che nessuno ha toccato significano "nessun controllo", mai "normale". Una rassicurazione che nessuno ha verificato vale meno di nessuna voce.

Giornata normale? "Registra una giornata sana" annota palline normali, ciecotrofi mangiati e nessun sintomo con un solo tocco.

LE CURE CHE TORNANO
• Tagliare le unghie, pulire la lettiera, ordinare il fieno, pesare, o qualsiasi cosa con il nome che si preferisce, all'intervallo che serve
• Un promemoria quando è il momento, e la traccia di quando è stato fatto l'ultima volta
• Ogni promemoria si può passare al proprio calendario
• Un coniglio si può tenere sotto controllo ravvicinato per qualche giorno se qualcosa non tornava, e il controllo finisce da solo

VETERINARIO, FARMACI E DOCUMENTI
• I veterinari e le visite, con accanto il peso rilevato durante la visita
• Terapie con ogni dose già calcolata e un promemoria prima di ciascuna
• Una dose si registra come somministrata oppure come deliberatamente non somministrata. Il registro dice quale delle due, perché sono fatti diversi
• Referti, esami e fatture si scansionano con la fotocamera del telefono e restano con la visita a cui appartengono

PIÙ DI UN CONIGLIO
• Quanti se ne vuole, ognuno con la sua foto e la sua storia
• I conigli che condividono la lettiera si possono indicare come conviventi, e una sola osservazione vale per tutti: le palline di una lettiera condivisa non si possono attribuire onestamente a un solo animale
• Un coniglio morto o andato in un'altra casa si archivia. Le sue voci restano e si possono ancora leggere, niente sparisce in silenzio

FOTO
Una galleria per ogni coniglio, per immagini tenute semplicemente perché si vuole.

I DATI RESTANO SUL TELEFONO
• Nessun account, nessuna registrazione, nessun server. Non c'è nessun posto a cui mandare qualcosa
• Nessuna pubblicità, nessun tracciamento, nessuna analisi, nessun acquisto in-app
• Funziona completamente offline, sempre
• Un backup si esporta quando si vuole e si ripristina. Le voci sono trasportabili, non intrappolate
• Gratis, senza versione a pagamento e senza nulla di bloccato

Nove lingue, cambiabili dentro l'app senza toccare quella del telefono: English, Polski, Deutsch, Español, Français, Italiano, Português (Brasil), Čeština, Українська.

UN REGISTRO, NON UNA DIAGNOSI
Binky non dice se un coniglio è malato, e non deduce mai un problema dalle voci mancanti. Il silenzio significa che nessuno ha guardato, non che vada tutto bene. Mostra quello che è stato annotato, abbastanza chiaramente da far notare un cambiamento e portarlo a chi se ne intende. In caso di preoccupazione per un coniglio, conviene rivolgersi a un veterinario esperto di conigli.
```

---

## Brazilian Portuguese

### App name — 22/30

```
Binky: Saúde do Coelho
```

### Short description — 66/80

```
Peso, saúde e cuidados do coelho. Offline, sem anúncios, de graça.
```

### Full description — 3855/4000

```
O Binky mantém um registro confiável da saúde do coelho, para que na consulta dê para ver o que mudou de verdade, e quando.

Coelhos escondem a doença. Quando alguma coisa fica evidente, em geral já é urgente. Um registro escrito transforma "andava meio quietinho esses dias" em uma data, um peso e uma anotação que dá para mostrar a alguém.

O PESO, MOSTRADO COM HONESTIDADE
• O peso entra em gramas, que é o que a balança marca
• Exibição em quilogramas ou em gramas, à escolha, mas as variações sempre em gramas, porque -40 g diz o que -0,04 kg esconde
• O gráfico marca datas reais em vez de pontos igualmente espaçados, então pesar de forma irregular não maquia a tendência
• Cada peso novo é comparado com uma referência tirada de várias pesagens recentes, não só da anterior
• Uma queda marcada levanta um aviso, e uma subida constante medida contra o peso de seis meses atrás também. Os dois são afirmações sobre os números, nunca um veredicto sobre o animal

OBSERVAÇÕES, QUANDO ALGO CHAMA A ATENÇÃO
Anote as bolinhas — a quantidade, e quantos tamanhos e formatos houver de verdade na caixa de areia, com uma foto dela quando disser mais do que as palavras — e ainda cecotrofos, apetite, humor, atividade, água, sintomas e anotações livres. Nada segue um cronograma. Vários registros no mesmo dia, ou nenhum.

Campos que ninguém tocou significam "não verificado", nunca "normal". Um alívio que ninguém conferiu vale menos do que registro nenhum.

Dia comum? "Registrar um dia saudável" anota bolinhas normais, cecotrofos comidos e nenhum sintoma com um toque só.

CUIDADOS QUE VOLTAM DE TEMPOS EM TEMPOS
• Cortar as unhas, limpar a caixa, comprar feno, pesar, ou qualquer coisa com o nome que se quiser dar, no intervalo que fizer sentido
• Um lembrete na hora certa, e o registro de quando foi feito pela última vez
• Qualquer lembrete pode ir para a agenda do celular
• Dá para deixar um coelho em acompanhamento por alguns dias quando algo não pareceu certo, e o acompanhamento termina sozinho

VETERINÁRIO, REMÉDIOS E DOCUMENTOS
• Os veterinários e as consultas, com o peso medido na consulta guardado junto
• Tratamentos com cada dose já calculada e um lembrete antes de cada uma
• Uma dose é registrada como dada ou como deliberadamente não dada. O registro diz qual das duas, porque são fatos diferentes
• Laudos, exames e notas fiscais são digitalizados com a câmera do celular e ficam com a consulta a que pertencem

MAIS DE UM COELHO
• Quantos quiser, cada um com sua foto e seu histórico
• Coelhos que dividem a mesma caixa de areia podem ser marcados como convivendo, e uma observação só vale para todos, porque as bolinhas de uma caixa compartilhada não dá para atribuir honestamente a um único animal
• Um coelho que morreu ou foi para outra casa vai para o arquivo. Os registros dele ficam e continuam legíveis, nada some calado

FOTOS
Uma galeria para cada coelho, para fotos guardadas simplesmente porque se quer.

OS DADOS FICAM NO CELULAR
• Sem conta, sem cadastro, sem servidor. Não existe para onde mandar nada
• Sem anúncios, sem rastreamento, sem análise de uso, sem compras dentro do app
• Funciona totalmente offline, sempre
• O backup se exporta quando se quiser e se restaura. Os registros são portáteis, não ficam presos
• De graça, sem versão paga e sem nada bloqueado

Nove idiomas, trocados dentro do app sem mexer no idioma do celular: English, Polski, Deutsch, Español, Français, Italiano, Português (Brasil), Čeština, Українська.

UM REGISTRO, NÃO UM DIAGNÓSTICO
O Binky não diz se um coelho está doente, e nunca deduz um problema a partir dos registros que faltam. O silêncio significa que ninguém olhou, não que está tudo bem. Ele mostra o que foi anotado, com clareza suficiente para uma mudança ser notada e levada a quem entende. Na dúvida sobre um coelho, procure um veterinário com experiência em coelhos.
```

---

## Czech

### App name — 21/30

```
Binky: Zdraví králíka
```

### Short description — 59/80

```
Váha, zdraví a péče o králíka. Offline, bez reklam, zdarma.
```

### Full description — 3458/4000

```
Binky vede spolehlivý zápis o zdraví králíka, aby bylo na veterině vidět, co se doopravdy změnilo a kdy.

Králíci nemoc skrývají. Když je něco zjevné, bývá už pozdě na klid. Psaný zápis promění "poslední dobou byl nějaký tichý" v datum, váhu a poznámku, kterou lze někomu ukázat.

VÁHA, ZOBRAZENÁ POCTIVĚ
• Váha se zadává v gramech, protože tolik ukazuje váha
• Zobrazení v kilogramech nebo v gramech, podle volby, ale změny vždy v gramech, protože -40 g řekne to, co -0,04 kg skryje
• Graf vynáší skutečná data, ne rovnoměrně rozložené body, takže nepravidelné vážení nemůže trend přikrášlit
• Každé nové vážení se porovnává se základem z několika posledních vážení, ne jen s tím předchozím
• Výrazný úbytek se označí a setrvalý nárůst měřený proti stavu před půl rokem také. Obojí je tvrzení o číslech, nikdy soud o zvířeti

POZOROVÁNÍ, KDYŽ NĚCO PADNE DO OKA
Zapisujte bobky — množství a tolik velikostí a tvarů, kolik jich na záchodku opravdu je, s fotkou, když řekne víc než slova — a k tomu cékotrofy, chuť, náladu, aktivitu, vodu, příznaky a vlastní poznámky. Nic nejede podle rozvrhu. Několik záznamů za den, nebo žádný.

Pole, kterých se nikdo nedotkl, znamenají "nekontrolováno", nikdy "v normě". Ujištění, které nikdo neověřil, je horší než žádný záznam.

Obyčejný den? "Zapsat zdravý den" jedním klepnutím zaznamená normální bobky, snědené cékotrofy a žádné příznaky.

PÉČE, KTERÁ SE VRACÍ
• Stříhání drápků, čištění záchodku, objednání sena, vážení, nebo cokoli s vlastním názvem, v takovém rozestupu, jaký sedí
• Připomenutí, když něco přijde na řadu, a záznam, kdy to bylo naposledy
• Každé připomenutí lze předat do vlastního kalendáře
• Králíka lze na pár dní vzít do bližšího sledování, když něco nesedělo, a sledování skončí samo

VETERINA, LÉKY A DOKUMENTY
• Veterináři a návštěvy, s váhou naměřenou na místě vedle nich
• Kúry s dopředu spočítanou každou dávkou a připomenutím před každou z nich
• Dávka se zapíše jako podaná, nebo jako vědomě nepodaná. Záznam řekne která, protože to jsou dvě různé skutečnosti
• Zprávy, výsledky a účtenky se naskenují fotoaparátem telefonu a zůstanou u návštěvy, ke které patří

VÍC NEŽ JEDEN KRÁLÍK
• Kolik jich je potřeba, každý s vlastní fotkou a vlastní historií
• Králíky, kteří sdílejí jeden záchodek, lze označit za spolubydlící a jedno pozorování pak platí pro všechny, protože bobky ze sdíleného záchodku nelze poctivě přiřadit jednomu zvířeti
• Králíka, který odešel nebo dostal nový domov, lze archivovat. Jeho záznamy zůstanou a dají se dál číst, nic nezmizí potichu

FOTKY
Galerie pro každého králíka, na fotky držené prostě proto, že se chce.

DATA ZŮSTÁVAJÍ V TELEFONU
• Bez účtu, bez registrace, bez serveru. Není kam cokoli posílat
• Bez reklam, bez sledování, bez analytiky, bez nákupů v aplikaci
• Funguje zcela offline, trvale
• Zálohu lze kdykoli vyexportovat a zase obnovit. Záznamy jsou přenosné, ne uvězněné
• Zdarma, bez placené verze a bez zámků

Devět jazyků, přepínatelných přímo v aplikaci bez zásahu do jazyka telefonu: English, Polski, Deutsch, Español, Français, Italiano, Português (Brasil), Čeština, Українська.

ZÁZNAM, NE DIAGNÓZA
Binky neřekne, jestli je králík nemocný, a nikdy nevyvozuje problém z chybějících záznamů. Ticho znamená, že se nikdo nedíval, ne že je vše v pořádku. Ukáže, co bylo zapsáno — dost zřetelně na to, aby si změny šlo všimnout a odnést ji někomu, kdo se v tom vyzná. Při obavách o králíka patří návštěva veterináře se zkušeností s králíky.
```

---

## Ukrainian

### App name — 23/30

```
Binky: Здоровʼя кролика
```

### Short description — 71/80

```
Вага, здоровʼя та догляд за кроликом. Офлайн, без реклами, безкоштовно.
```

### Full description — 3540/4000

```
Binky веде надійний запис про здоровʼя кролика, щоб у ветеринара було видно, що саме змінилося і коли.

Кролики приховують хворобу. Коли щось стає очевидним, зазвичай це вже терміново. Записаний щоденник перетворює «останнім часом якийсь тихий» на дату, вагу й нотатку, яку можна комусь показати.

ВАГА, ПОКАЗАНА ЧЕСНО
• Вагу вводять у грамах, бо саме стільки показують ваги
• Показ у кілограмах або в грамах, на вибір, але зміни завжди в грамах, бо -40 г каже те, що -0,04 кг ховає
• Графік відкладає справжні дати, а не рівномірні проміжки, тож нерегулярне зважування не прикрасить тенденцію
• Кожне нове зважування порівнюється з опорним значенням із кількох останніх, а не лише з попереднім
• Помітне зниження позначається, і стале зростання, виміряне проти ваги півроку тому, теж. І те, і те — твердження про числа, ніколи не вирок про тварину

СПОСТЕРЕЖЕННЯ, КОЛИ ЩОСЬ ЗВЕРНУЛО УВАГУ
Записуйте котяхи — кількість і стільки розмірів та форм, скільки їх насправді в лотку, з фото лотка, коли воно каже більше за слова — а ще цекотрофи, апетит, настрій, активність, воду, симптоми й власні нотатки. Ніщо не йде за розкладом. Кілька записів за день або жодного.

Поля, яких ніхто не торкався, означають «не перевіряли», ніколи «у нормі». Заспокоєння, якого ніхто не перевірив, гірше за відсутність запису.

Звичайний день? «Записати здоровий день» одним дотиком фіксує звичайні котяхи, зʼїдені цекотрофи й жодних симптомів.

ДОГЛЯД, ЩО ПОВЕРТАЄТЬСЯ
• Підрізати кігті, прибрати лоток, замовити сіно, зважити або будь-що з власною назвою, з таким проміжком, який пасує
• Нагадування, коли настає час, і запис про те, коли це робили востаннє
• Будь-яке нагадування можна передати у власний календар
• Кролика можна взяти під нагляд на кілька днів, якщо щось було не так, і нагляд завершиться сам

ВЕТЕРИНАР, ЛІКИ ТА ДОКУМЕНТИ
• Ветеринари й візити, а поруч — вага, виміряна на візиті
• Курси лікування з наперед порахованою кожною дозою та нагадуванням перед нею
• Дозу записують як дану або як свідомо не дану. Запис каже, що саме, бо це різні факти
• Виписки, результати й рахунки скануються камерою телефона й лишаються при тому візиті, до якого належать

БІЛЬШЕ НІЖ ОДИН КРОЛИК
• Скільки завгодно, кожен зі своїм фото та своєю історією
• Кроликів, які ділять один лоток, можна позначити як таких, що живуть разом, і одне спостереження охопить усіх — котяхи зі спільного лотка неможливо чесно приписати одній тварині
• Кролика, який пішов або переїхав у новий дім, можна архівувати. Його записи лишаються і їх далі можна читати, ніщо не зникає тихцем

ФОТО
Галерея для кожного кролика, для світлин, які зберігають просто тому, що хочеться.

ДАНІ ЛИШАЮТЬСЯ В ТЕЛЕФОНІ
• Без облікового запису, без реєстрації, без сервера. Немає куди щось надсилати
• Без реклами, без стеження, без аналітики, без покупок у застосунку
• Працює повністю офлайн, постійно
• Резервну копію можна експортувати будь-коли й відновити. Записи переносні, а не замкнені
• Безкоштовно, без платної версії та без замків

Девʼять мов, які перемикаються в самому застосунку, без зміни мови телефона: English, Polski, Deutsch, Español, Français, Italiano, Português (Brasil), Čeština, Українська.

ЗАПИС, А НЕ ДІАГНОЗ
Binky не скаже, чи кролик хворий, і ніколи не робить висновку про проблему з того, чого не записали. Тиша означає, що ніхто не дивився, а не що все гаразд. Він показує те, що записали, — досить виразно, щоб зміну помітили й віднесли до того, хто на цьому знається. Якщо є тривога за кролика, варто звернутися до ветеринара з досвідом роботи з кроликами.
```

---

## Release notes (Play's "What's new", 500 chars per locale)

**Don't paste these nine by hand.** The Console takes all languages in one textarea, tagged by
locale, and `scripts/play-whatsnew.py` renders exactly that from the blocks below:

```bash
python3 scripts/play-whatsnew.py                 # newest version here, to stdout
python3 scripts/play-whatsnew.py --version 1.8.0 # a named one
```

It reformats and never retypes, so this document stays the only copy. It exits non-zero if a note
is over 500 characters or if a language the **app** ships has no note at all — both being things
Play accepts in silence and an owner then reads.

Per release, per locale, and pasted at upload time. 1.0 had none — it was the first build on the track
and there was nothing to be new against. **1.0.1**:

English:

```
Binky now speaks Polish. Switch the language inside the app, without changing your phone's language.
```

Polish:

```
Binky mówi teraz po polsku. Język przełączasz w samej aplikacji, bez zmieniania języka telefonu.
```

Both describe the *only* user-visible change in 1.0.1, which is what the release is for. Neither
mentions the schema, because nothing about it changed for the person reading this — what changed is
what the project owes from here (PLAN.md 3j, ADR-0023).

### 1.8.0 — the release that carries 1.1 through 1.7 with it

⚠️ **The build these notes ship with is 1.8.0**, not the 1.7 this heading named until 2026-08-22;
1.8.0 is 1.7 plus 9k, a driver fix and docs, none of which an owner can see, so **the nine note bodies
below stand unchanged and need no re-translation** — only the version they are entered against moved.

⚠️ **This is not a note about one release, and it cannot be.** Nothing since 1.0.1 has ever been
uploaded, so whoever takes this update sees this note and no other — everything 1.1 through 1.7
changed is new *to them*. A note scoped to 1.7's own commits would describe the housemates sheet and
nothing else, which would be true and useless. These nine cover the span, ordered by what a 1.0
install gains rather than by which release added it.

**Written from the English**, per [`translator-brief.md`](translator-brief.md), and each locale quotes
its **own** UI labels — the *Lives with* line and the Home destination exactly as `values-*/strings.xml`
spells them (`Start`, `Domů`, `Accueil`, `Головна`, …) — because the last bullet asks the reader to go
and tap one. Second person stays genderless everywhere, and each locale keeps the register its full
description already set: infinitives in German and French, informal singular in Spanish, Italian,
Polish and Portuguese, formal plural in Czech and Ukrainian.

The counts below are measured and deliberately hold headroom. The first draft ran 464 in English and
put French, Italian, German, Polish and Portuguese **over** 500 — Romance and German prose run longer
than the English they are written from, and Play truncates rather than warns. The vet bullet lost its
directory and the weight bullet lost a clause; nothing lost a claim.

**English** — 416/500:

```
• Nine languages, switched inside the app
• Vet visits, medication courses and dose reminders
• Scan documents and keep them with the visit
• Care reminders for anything that recurs
• Weight changes flagged for a closer look, up or down
• Droppings: every shape in the tray, and a photo of it
• A new look; Material You optional
• Tap "Lives with" on Home to see the whole group

Still no account, no ads, no server.
```

**Polish** — 462/500:

```
• Dziewięć języków, przełączanych w samej aplikacji
• Wizyty u weterynarza, kuracje i przypomnienia o dawkach
• Skanowanie dokumentów i trzymanie ich przy wizycie
• Przypomnienia o opiece, która się powtarza
• Zmiany wagi, którym warto się przyjrzeć — w dół i w górę
• Bobki: tyle kształtów, ile jest w kuwecie, i zdjęcie
• Nowy wygląd; Material You do wyboru
• Dotknij „Mieszka z” na ekranie Start, żeby zobaczyć grupę

Nadal bez konta, bez reklam, bez serwera.
```

**German** — 462/500:

```
• Neun Sprachen, direkt in der App umschaltbar
• Tierarztbesuche, Medikamentenkuren und Dosis-Erinnerungen
• Unterlagen scannen und beim Besuch aufbewahren
• Pflege-Erinnerungen für alles, was wiederkehrt
• Ab- und Zunahmen markiert für einen zweiten Blick
• Köttel: so viele Formen, wie im Klo liegen, mit Foto
• Neue Gestaltung; Material You optional
• „Lebt mit“ auf Start antippen und die ganze Gruppe sehen

Weiterhin kein Konto, keine Werbung, kein Server.
```

**Spanish** — 476/500:

```
• Nueve idiomas, que se cambian dentro de la app
• Visitas al veterinario, tratamientos y avisos de cada dosis
• Escanea documentos y guárdalos junto a la visita
• Avisos de cuidados que vuelven cada cierto tiempo
• Bajadas y subidas de peso señaladas para mirarlas de cerca
• Cagarrutas: todas las formas que haya en la bandeja, con foto
• Aspecto nuevo; Material You opcional
• Toca «Vive con» en Inicio para ver todo el grupo

Sigue sin cuenta, sin anuncios y sin servidor.
```

**French** — 450/500:

```
• Neuf langues, à changer dans l’app même
• Visites vétérinaires, traitements et rappel par dose
• Scanner les documents et les garder avec la visite
• Rappels pour les soins qui reviennent
• Baisses et hausses de poids signalées à regarder de près
• Crottes : toutes les formes du bac, avec une photo
• Nouvelle allure ; Material You au choix
• Toucher « Vit avec » sur Accueil pour voir tout le groupe

Toujours sans compte, sans pub, sans serveur.
```

**Italian** — 455/500:

```
• Nove lingue, da cambiare nell’app stessa
• Visite dal veterinario, cure e promemoria per ogni dose
• Scansiona i documenti e tienili con la visita
• Promemoria per le cure che tornano
• Cali e aumenti di peso segnalati da guardare da vicino
• Palline: tutte le forme che ci sono nella lettiera, con foto
• Aspetto nuovo; Material You a scelta
• Tocca «Vive con» su Inizio per vedere tutto il gruppo

Sempre senza account, senza pubblicità, senza server.
```

**Brazilian Portuguese** — 480/500:

```
• Nove idiomas, trocados dentro do próprio app
• Consultas veterinárias, tratamentos e lembretes de dose
• Digitalize documentos e guarde-os junto da consulta
• Lembretes de cuidados que voltam de tempos em tempos
• Quedas e ganhos de peso sinalizados para olhar de perto
• Bolinhas: todos os formatos que houver na caixa, com foto
• Visual novo; Material You opcional
• Toque em "Mora com" na tela Início para ver o grupo inteiro

Continua sem conta, sem anúncios e sem servidor.
```

**Czech** — 446/500:

```
• Devět jazyků, přepínatelných přímo v aplikaci
• Návštěvy veterináře, léčebné kúry a připomínky dávek
• Naskenujte dokumenty a nechte je u návštěvy
• Připomínky péče, která se vrací
• Poklesy i vzestupy hmotnosti označené k bližšímu pohledu
• Bobky: všechny tvary, které na záchodku jsou, i s fotkou
• Nový vzhled; Material You volitelně
• Klepnutím na „Spolubydlící“ na obrazovce Domů zobrazíte skupinu

Stále bez účtu, bez reklam, bez serveru.
```

**Ukrainian** — 448/500:

```
• Девʼять мов, які перемикаються в самому застосунку
• Візити до ветеринара, курси ліків і нагадування про дози
• Скануйте документи й тримайте їх при візиті
• Нагадування про догляд, що повертається
• Зниження та набір ваги позначені, щоб придивитися
• Котяхи: усі форми, які є в лотку, і фото
• Новий вигляд; Material You за бажанням
• Торкніться «Живе з» на Головній, щоб побачити всю групу

Досі без облікового запису, без реклами, без сервера.
```

**Every bullet is a feature this build actually has**, checked against `CHANGELOG.md` rather than
against memory: nine languages (1.6), vet visits and medication courses with a reminder per dose (1.2),
document scanning attached to the visit (1.2), recurring care reminders (1.1), the trend flag that a
*gain* raises too (1.1, extended at 1.5), multi-valued droppings with a photo of the tray (1.5), the
redesign with Material You as a toggle (1.4), and the housemates sheet (1.7, 9f).

**No note implies a diagnosis and none infers a problem from silence** — the same two rules that
outrank fluency in the descriptions. "Flagged for a closer look" is the app's own framing
(`trend_flag_title`, ADR-0001) and is why the bullet does not simply say "flagged", which would read
as the app having decided something.

⚠️ **The support screen, the licence attribution and the backup destination are not mentioned.** They
shipped in 1.3, 1.5 and 1.1 and every one of them is real; 500 characters is the constraint and a note
that lists everything gets read as none of it. Add them only if something comes out.

---

## Open

- ~~**The screenshots are 1.0's and are stale.**~~ **Re-shot 2026-08-26**, nine locales × eight
  scenes, **light only**, and this time committed: `art/play-screenshots/`, flat, latest-only. The
  filename carries both the upload position and the locale (`4_timeline-pt-BR.png`), which is what
  lets one flat directory hold all nine sets without a folder per language to lose them in.

  **The arithmetic changed with the crop.** Captured at the phone's native 1220×2712, the top
  **130px** cropped off, then padded to **1452×2582**. Play's screenshot aspect limit is 2:1 and the
  raw capture is 2.22:1, so padding is a requirement rather than a style choice; the crop is what
  removes the status bar. The fill is the app's own surface, so the bands are invisible —
  **`#FFF8EF`** for the light set. ⚠️ This bullet said `#121318` until 2026-08-21: that is the
  *pre*-Phase-7 dark, and it had outlived the palette by four releases. It was never load-bearing —
  `art/pad-screenshot.py` samples the image's own edge rather than reading a constant — which is
  exactly why nothing caught it.

  ⚠️ **The status bar is cropped, and it is the driver that made that necessary.** `screenshots.py`
  holds the phone in Do Not Disturb for the length of a run — `set_dnd` explains why, and why
  revoking `POST_NOTIFICATIONS` instead would be worse — and Zen puts a crossed bell in the status
  bar of every frame it captures. It cannot be suppressed on the device: HyperOS ignores SystemUI
  demo mode outright (`sysui_demo_allowed` plus the clock/battery/notifications/zen broadcasts change
  nothing), so `--crop-status-bar` on `art/pad-screenshot.py` is the only place it can go. 130px is
  the portrait `statusBars` inset measured by the edge-to-edge matrix, and it coincides with the
  punch-hole cutout. The clock and battery go with it, which is the point rather than a side effect.
  **The 9g set kept them, bell included.**

  ⚠️ **The listing takes the LIGHT set from 2026-08-24.** This file said *dark is the set to upload,
  because it is what the store already shows* for the whole of Phase 9, and 1.8.0 went up under that
  rule. The rule is now light-only, and it is a decision about **the Console alone** — the app still
  ships both themes, `screenshots.py` still captures both cells, and Phase 10 adds an in-app
  light/dark override.

  **Eight, and the eight are chosen.** Play allows eight phone screenshots and the set now uses all
  of them: `home, weight, observations, timeline, care, medication-course, documents, backup`. The
  in-app appearance override (10f) was the one left out. ⚠️ **The gallery shot is still dropped**,
  for the reason 3h found: the sample seeder writes
  solid-colour JPEGs (`SampleData.writeSampleJpeg`) because the fixture exists to exercise the media
  pipeline, not to look like anything, so the gallery photographs as four flat rectangles. Shipping
  that would read as a broken app; staging it would have meant putting real photos in. Play requires a
  minimum of two. Revisit when there are real photos worth showing — the full description's PHOTOS
  section is currently the only place that claim is made.

  **Localised screenshots are per listing**, and Play falls back to the default listing's set for a
  language that has none — so the nine sets are nine separate uploads in the Console, not one. All
  nine exist in the repo; which of them actually go up is a Console decision, and a language with no
  installs loses nothing by falling back to English until it has some.
- **A roadmap line** naming unreleased features was deliberately left out. It manages expectations
  for reviewers who might otherwise mark the app down for something missing, but it also puts
  unreleased features in indexed listing text. Add it later if reviews ask for it, not before.
- **The nine descriptions have had no native read-through**, exactly as the app's strings have not —
  [ADR-0030](adr/0030-a-language-ships-on-an-audit-not-a-native-read-through.md) covers this text too.
  The rules that outrank fluency are checked here the same way: no locale claims a feature the build
  lacks, no locale implies diagnosis, no locale infers a problem from silence, and the closing
  paragraph is present in all nine. **Listing copy is the cheaper half to be wrong in** — it is edited
  in the Console without a release, so a reported wording is fixed the same day rather than at the next
  upload.
