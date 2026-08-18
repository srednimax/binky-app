# Changelog

## [1.6.0](https://github.com/srednimax/binky-app/compare/v1.5.0...v1.6.0) (2026-08-18)


### Features

* **i18n:** put the translation-report path in the language picker ([b34b3fc](https://github.com/srednimax/binky-app/commit/b34b3fc1a40486021b6c453b14ddafb120fc06a0))
* **i18n:** ship Brazilian Portuguese, where zero is a singular ([0f945bd](https://github.com/srednimax/binky-app/commit/0f945bd2f3320bc892ee4cd46a867cb6bb2acf8c))
* **i18n:** ship Czech, where zero lands where the language wants it ([7e973b4](https://github.com/srednimax/binky-app/commit/7e973b461cbfc79ba91d7c9130c78e4414e6a81a))
* **i18n:** ship French, where the name trap is elision rather than case ([e797e7a](https://github.com/srednimax/binky-app/commit/e797e7a03e02e8f67f8efc48c756a3c1286da7ff))
* **i18n:** ship German, which pays nothing to the gender trap ([72b1a42](https://github.com/srednimax/binky-app/commit/72b1a42050b499efbab9872657edcea5fb181487))
* **i18n:** ship Italian, where the name trap is nothing at all ([210572d](https://github.com/srednimax/binky-app/commit/210572d0c6f535b96385dbddaa23c51bdb06cb0e))
* **i18n:** ship Spanish, where the gender trap moves to the bunny ([ec9dbdc](https://github.com/srednimax/binky-app/commit/ec9dbdcb330956c47ebb35dd3028caf71934a3f1))
* **i18n:** ship Ukrainian, and with it all nine languages ([5dcbd25](https://github.com/srednimax/binky-app/commit/5dcbd250b9bb97f0c1e27560a8d195ea18a077a7))


### Bug Fixes

* **build:** let the test task run when nothing is staged for translation ([5e395c2](https://github.com/srednimax/binky-app/commit/5e395c29b7207a84259525ec96f2c167290be8e9))
* **i18n:** give French a status chip no gendered field can contradict ([408264b](https://github.com/srednimax/binky-app/commit/408264b0fcab0b791f7f3c11311a182f6b243f9a))
* **i18n:** say what the promoted files are, and quiet one false-positive rule ([2fe36f3](https://github.com/srednimax/binky-app/commit/2fe36f3c92d945f3a379e684c98793843a7bad6a))
* **release:** check every shipped locale reached the bundle, not just Polish ([0b30e43](https://github.com/srednimax/binky-app/commit/0b30e43606305d4a9868604d14526745e71965ae))
* **scripts:** give the capture driver both spellings of a locale ([fb0dd8c](https://github.com/srednimax/binky-app/commit/fb0dd8cae509428e59d3f09b11662e9cd511e00c))
* **test:** stop using French to stand for a language Binky does not ship ([f0e76bc](https://github.com/srednimax/binky-app/commit/f0e76bc5be999a364e6a09872aa2f79efbaef11d))
* **ui:** let a navigation label shrink where four languages clipped it ([9b46f41](https://github.com/srednimax/binky-app/commit/9b46f41061a8c59764fa5370cd538bf99607454e))

## [1.5.0](https://github.com/srednimax/binky-app/compare/v1.4.0...v1.5.0) (2026-08-16)


### Features

* **driver:** arm a live dose before every scene, so DND has something to suppress ([290000e](https://github.com/srednimax/binky-app/commit/290000eddcbb00d823eb13fbe016c5fcee97498c))
* **driver:** let a scene ask for a seed the sample data does not contain ([fc2adea](https://github.com/srednimax/binky-app/commit/fc2adea750a80bf105f8e935aea3dc19fd09f500))
* **driver:** resolve scene needles through the string resources, and tap the field itself ([75ae620](https://github.com/srednimax/binky-app/commit/75ae6208d233410d40411e3e15ea31b441b66247))
* **licences:** attribute every bundled dependency, and ship the licence text ([a838a6e](https://github.com/srednimax/binky-app/commit/a838a6eba1313dfe54f47e68b3fcc8afc8205521))
* **media:** settle both downsample specs on the phone, and add MediaKind.Observation ([d20378e](https://github.com/srednimax/binky-app/commit/d20378e293a634c6b8344bc3af557c99ac62d784))
* **observations:** droppings are multi-valued, and the tray is worth a photo ([ddb430a](https://github.com/srednimax/binky-app/commit/ddb430a63e45c42593d36960cad91c66df9c758e))
* **observations:** one way to record a day, and it is the "+" ([e26642d](https://github.com/srednimax/binky-app/commit/e26642df9e0662167f1da1013ead1e840007e3be))
* **ui:** draw a bunny where the avatar placeholder showed a person ([90dc9e9](https://github.com/srednimax/binky-app/commit/90dc9e953ffab3f573c19f92c443c6fe6df20df4))
* **weight:** a gain raises the same flag, against a six-month anchor ([c892fe3](https://github.com/srednimax/binky-app/commit/c892fe3f23e5a82c53e82897346ea2a76f1f0b76))


### Bug Fixes

* **driver:** isolate every scene at Home, and silence the phone for the run ([3e070dc](https://github.com/srednimax/binky-app/commit/3e070dc01cb8fe24ed208bbcbfef9cd8f62cd662))
* **driver:** tap Photos by text, a collision only Polish can express ([29d442d](https://github.com/srednimax/binky-app/commit/29d442db888285207e21446b6680b56554da7722))
* **home:** cap the housemates line, and bound it at two lines ([250c8f0](https://github.com/srednimax/binky-app/commit/250c8f09dfd99fa6833769028018d723c40e6cf8))
* **i18n:** correct nine Polish strings no mechanical check could see ([ddbd58a](https://github.com/srednimax/binky-app/commit/ddbd58ae0f982c259258eb0e23bda68d822df7b5))
* let an update migrate instead of refusing to open the records ([c0bc2d7](https://github.com/srednimax/binky-app/commit/c0bc2d7b3a9caa0ebce02e68b29cfebbcdbdc67a))
* let background work migrate rather than sit out an update ([e781818](https://github.com/srednimax/binky-app/commit/e781818e85a9d5c931af83bd6050e53330d97063))

## [1.4.0](https://github.com/srednimax/binky-app/compare/v1.3.0...v1.4.0) (2026-08-14)


### Features

* **art:** redraw the launcher mark and move the identity onto the Phase 7 palette ([ad721e0](https://github.com/srednimax/binky-app/commit/ad721e0d035ef53d44474e831a1070f72111a4fd))
* convert the last eleven dialogs to the BinkyDialog idiom ([91f524b](https://github.com/srednimax/binky-app/commit/91f524bd5648370b14f7420541bddb52a2e12456))
* give Binky its own palette, type scale and spacing rhythm ([61abe63](https://github.com/srednimax/binky-app/commit/61abe63ef2cb5f61e42e2ca748c5dc4095495656))
* let Material You be switched back on in Settings ([a7ca363](https://github.com/srednimax/binky-app/commit/a7ca363e9d03b05b390081566de6f8dd4c1f195e))
* redraw Archived bunnies against 4f/4g/4h ([957ec73](https://github.com/srednimax/binky-app/commit/957ec730d60c23c6617dd4d32edc3418ffa8b441))
* redraw Backup & restore against 6c/6d ([c4d3764](https://github.com/srednimax/binky-app/commit/c4d376426374a619460426c05781cd4f7a51af22))
* redraw Care & Meds against 3a/3b and 3c/3d ([de9445d](https://github.com/srednimax/binky-app/commit/de9445db7217357cae82a72a288f6f81032a51c1))
* redraw Documents and Photos against 10a-10d ([0ba20d9](https://github.com/srednimax/binky-app/commit/0ba20d92af943fc724d24ef98021360bc1082a44))
* redraw Home against its mockups ([fbbec31](https://github.com/srednimax/binky-app/commit/fbbec3125ab18bf8b5062b51868c165206522910))
* redraw More against 6a/6b ([834db70](https://github.com/srednimax/binky-app/commit/834db70ff670b62bf10b113c16e82d87b30aefee))
* redraw New course against 3e and Record a dose against 3f/3g ([72019eb](https://github.com/srednimax/binky-app/commit/72019eb073eb4a4eaa6e031f12e7b8e544ac0301))
* redraw Observations against 2a/2b ([13340b7](https://github.com/srednimax/binky-app/commit/13340b73700f2b91b4b733547b1466491c452cc6))
* redraw Record a weighing against 6e/6f and adopt the last-five line ([d0e0c14](https://github.com/srednimax/binky-app/commit/d0e0c146ae6b98ad53ecdcd5d6a95eb7d3a3308b))
* redraw Record an observation against 2c ([e50c37e](https://github.com/srednimax/binky-app/commit/e50c37e4bf8c0e260d56529ea7d3d018174240de))
* redraw Settings, the first route with no drawing ([b734332](https://github.com/srednimax/binky-app/commit/b734332c833f1bfaa4c0e8d3d25bfde995a0b164))
* redraw Support against 9c/9d ([3d4ec76](https://github.com/srednimax/binky-app/commit/3d4ec766d3c99df6098d79f45ad5bf4d3b667e9e))
* redraw the bunny editor against 4e ([fe1f958](https://github.com/srednimax/binky-app/commit/fe1f9581c508ad3749caa08c1e443c51fc81ee00))
* redraw the bunny switcher against its mockup ([9a18cc6](https://github.com/srednimax/binky-app/commit/9a18cc676b9265578b2e33bea6cbe5314e74878c))
* redraw the Care detail screens against 10k-10n ([909cfd9](https://github.com/srednimax/binky-app/commit/909cfd9bfa760fbc3a68a5c9ce5ab0d0d08653c2))
* redraw the chart empty states and watch expiry against 8a-8e ([c7d2e76](https://github.com/srednimax/binky-app/commit/c7d2e7616581663bd1137854b8319ae5183ef94f))
* redraw the setup wizard and schema mismatch against 10e-10j ([ea35c2e](https://github.com/srednimax/binky-app/commit/ea35c2e53db37e1a979c373cf23aef7256bf452b))
* redraw Vets against 5a/5b and move deleting a vet to the editor ([d854a9a](https://github.com/srednimax/binky-app/commit/d854a9a4e0bffbecdf7277e127bc90cb1c05304f))
* redraw Weight against its mockups ([28cda95](https://github.com/srednimax/binky-app/commit/28cda959ceff8bfc7e7116c79a95094bd01238ab))


### Bug Fixes

* correct Settings against 9a, which arrived after it shipped ([9a8bdb6](https://github.com/srednimax/binky-app/commit/9a8bdb670ed6970dcdee82f97414beaf58228ded))
* paint the trend flag apricot rather than error red ([e825017](https://github.com/srednimax/binky-app/commit/e82501711a449411e8850a458b5c32366cd25697))
* **scripts:** make the edge-to-edge matrix produce honest evidence ([011a07d](https://github.com/srednimax/binky-app/commit/011a07d865be6291562422e7d23865fc27f94eee))
* stop the weigh-in reminder opening both completion dialogs by itself ([f6aa148](https://github.com/srednimax/binky-app/commit/f6aa148a68721f5983cf8279c293a505e567ae26))
* three device-pass corrections on the redrawn Backup screen ([f736e4d](https://github.com/srednimax/binky-app/commit/f736e4df95684e87eba832f3b685ff968707f7dc))

## [1.3.0](https://github.com/srednimax/binky-app/compare/v1.2.0...v1.3.0) (2026-08-06)


### Features

* support mail and Play listing hand-offs ([c74a3d5](https://github.com/srednimax/binky-app/commit/c74a3d57c999bc4a6f0bab51d219ae23f1a38cc7))
* support screen with bug, feature and rating hand-offs ([1171098](https://github.com/srednimax/binky-app/commit/1171098956a90046ede1edd86b0eb8261d2a8e74))


### Bug Fixes

* send the support mail's subject and body in the mailto query ([c2f0815](https://github.com/srednimax/binky-app/commit/c2f081516a61d0cbdf151eb32e52f0f24c3d2f3b))

## [1.2.0](https://github.com/srednimax/binky-app/compare/v1.1.0...v1.2.0) (2026-08-05)


### Features

* admit documents into Auto Backup under a ceiling, and say what was left out ([9602c5e](https://github.com/srednimax/binky-app/commit/9602c5ea55208ec0f703b762c6df0b3bbacb59e0))
* arm the one dose alarm from the courses table ([753d910](https://github.com/srednimax/binky-app/commit/753d910627198f59b77d5e7c08c6b3d2ea9a7e8d))
* carry Phase 5's whole schema forward to version 6 ([01a769e](https://github.com/srednimax/binky-app/commit/01a769e1883277d88f9e818c1831010af090cd27))
* give medication courses a data layer and derive their doses ([51abbf7](https://github.com/srednimax/binky-app/commit/51abbf78bc291d029a912d32a806ac2cd5d1db56))
* prove the exact-alarm path while nothing depends on it ([a22ee51](https://github.com/srednimax/binky-app/commit/a22ee5110f22a15c2ba6ae90ada987e544a558db))
* put medication courses and their doses on screen ([d20a39c](https://github.com/srednimax/binky-app/commit/d20a39c032b375128a8225046d9aa541ba9746ac))
* put visits and the vet directory on screen ([d4608c0](https://github.com/srednimax/binky-app/commit/d4608c0afed8a53e15e2a427f061e570cf4a1ab8))
* scan documents, read them, and attach them to visits ([e6ce297](https://github.com/srednimax/binky-app/commit/e6ce297f3b6b41b93b3f770f19a3a6d9a783d551))

## [1.1.0](https://github.com/srednimax/binky-app/compare/v1.0.1...v1.1.0) (2026-08-03)


### Features

* carry care reminders in by migration, not by wipe ([80b0412](https://github.com/srednimax/binky-app/commit/80b04127b2043139f7455b869c0dab168e222501))
* give a worried owner a watch, and the flag something to offer ([01a20e6](https://github.com/srednimax/binky-app/commit/01a20e63e577f2774f08822b5125390811847953))
* give Care a screen, and the tab back ([fb774cd](https://github.com/srednimax/binky-app/commit/fb774cd548d90caf24ae1ae5e690527b128c0c6e))
* give exports a destination worth remembering, and a nudge to use it ([3cbdef2](https://github.com/srednimax/binky-app/commit/3cbdef2e9af4b66c314d4ad04afd7384d1f3e8a9))
* schedule, notify and ask, on an empty database ([77e1116](https://github.com/srednimax/binky-app/commit/77e1116e0599de57cb3a8a80a46f336e882c9c85))


### Bug Fixes

* report the saved backup's own name, not the folder it landed in ([9929134](https://github.com/srednimax/binky-app/commit/99291343168669d54d3e52035e8bb4cd7ed5ea96))
* say why the healthy day left a watched housemate out ([8c9ecd0](https://github.com/srednimax/binky-app/commit/8c9ecd0e074518e7459941e2da8e1fd1c55da436))
* stop the keyboard panning the window, and let the sheet scroll ([e043d01](https://github.com/srednimax/binky-app/commit/e043d01a7982739854874199b942a0df14759f35))
* take lint back to zero, and delete three strings nothing says ([a9fe8bf](https://github.com/srednimax/binky-app/commit/a9fe8bfc76ef00cc0669696c1352f80eced937dc))

## [1.0.1](https://github.com/srednimax/binky-app/compare/v1.0.0...v1.0.1) (2026-07-29)


### Bug Fixes

* ship Polish, so the device language actually reaches the app ([a537e51](https://github.com/srednimax/binky-app/commit/a537e51e2139adbebf8c28dea8e60b4d401d7501))
* ship Polish, so the device language actually reaches the app ([1a6be8f](https://github.com/srednimax/binky-app/commit/1a6be8f912b8c8cba0f746ae8b1a552afe7f0aef))

## [1.0.0](https://github.com/srednimax/binky-app/compare/v0.8.1...v1.0.0) (2026-07-29)


### Documentation

* record what 3h proved and found, and cut 1.0 ([e8f36ad](https://github.com/srednimax/binky-app/commit/e8f36adeeee7a2b0a4a84309606c00ed289d0ba6))

## [0.8.1](https://github.com/srednimax/binky-app/compare/v0.8.0...v0.8.1) (2026-07-29)


### Bug Fixes

* stop the welcome screen describing features 1.0 does not have ([909900b](https://github.com/srednimax/binky-app/commit/909900bff15ed3868310d18ec630faf08aaf5486))

## [0.8.0](https://github.com/srednimax/binky-app/compare/v0.7.0...v0.8.0) (2026-07-29)


### ⚠ BREAKING CHANGES

* applicationId changes from app.binky.tracker to binky.bunny.and.rabbit.tracker, so an existing debug install is orphaned rather than upgraded and has to be uninstalled by hand. Nothing has been published to Play under either id, which is the only reason this is still free.

### Build System

* move the applicationId to binky.bunny.and.rabbit.tracker ([c4e3753](https://github.com/srednimax/binky-app/commit/c4e375328227911e98cda6daee5672deed4be74d))

## [0.7.0](https://github.com/srednimax/binky-app/compare/v0.6.0...v0.7.0) (2026-07-29)


### Features

* first-run setup, hide Care & Meds, add the language switcher ([32b28e9](https://github.com/srednimax/binky-app/commit/32b28e9d79ac8d6d2e77a88fa2648e2be9dea058))
* first-run setup, hide Care & Meds, add the language switcher ([375ff08](https://github.com/srednimax/binky-app/commit/375ff08eeeb8a36d1e1757ea19198757133db234))


### Bug Fixes

* name both versions when a backup is too new to read ([1ac9970](https://github.com/srednimax/binky-app/commit/1ac9970ef3b6c8226441343cdb0972d233083d30))
* name both versions when a backup is too new to read ([969a164](https://github.com/srednimax/binky-app/commit/969a164e3f718e42ab7b5322e15af9e63d3b896b))
* refuse a backup carrying a traversal entry instead of crashing ([2e5e5ff](https://github.com/srednimax/binky-app/commit/2e5e5ff8b95bc0357b8e092f721f67520a1d1a4f))
* refuse a traversal entry on every API, not just 14+ ([c908f6f](https://github.com/srednimax/binky-app/commit/c908f6f04f6067c5f4a36a7b677f1838cfeb8899))
* say why a photo could not be imported ([4db3d12](https://github.com/srednimax/binky-app/commit/4db3d12399f6554656c248271e12d02c56dfec1f))
* say why a photo could not be imported ([0b1f840](https://github.com/srednimax/binky-app/commit/0b1f840ace750d1e5357c898396856ef5e2ba696))
* **test:** clear the app locale where the clear can actually land ([35be671](https://github.com/srednimax/binky-app/commit/35be6718241efc8d94441f390b8d223866530a33))

## [0.6.0](https://github.com/srednimax/binky-app/compare/v0.5.0...v0.6.0) (2026-07-28)


### Features

* add the per-bunny photo gallery ([2f4d615](https://github.com/srednimax/binky-app/commit/2f4d61568c57013dfb12eea65615755e52b2547b))
* keep the photo gallery out of Android Auto Backup ([1e2d8b8](https://github.com/srednimax/binky-app/commit/1e2d8b851c98eda3e5056ad01ee40a5e28f649fb))
* manual export at three scopes, and restore ([5d9099c](https://github.com/srednimax/binky-app/commit/5d9099cc156734489bbea6c0177d14087622fd57))
* manual export at three scopes, and restore ([5612b7a](https://github.com/srednimax/binky-app/commit/5612b7a2ed973bcf1a19b075d2348d495a367e41))
* photos — the gallery, schema 4, and the last planned wipe ([bee3bae](https://github.com/srednimax/binky-app/commit/bee3baebe52954514875334dae0741b7ca6e9654))
* read the capture date out of the media pipeline ([d06ef8a](https://github.com/srednimax/binky-app/commit/d06ef8aa25a9104c13ca1fd88e35edba3a8e92e4))
* rebuild the app shell on AppCompat for the locale backport ([6a52c22](https://github.com/srednimax/binky-app/commit/6a52c22e9c89d6525bab6c0f9988bd03f2204ea9))
* rebuild the app shell on AppCompat for the locale backport ([26435e7](https://github.com/srednimax/binky-app/commit/26435e79aed64bfda537a4ac9208493bd120139a))
* seed sample photos in the debug fixture ([557a9b9](https://github.com/srednimax/binky-app/commit/557a9b9e5a74d2e6a020091aa5594da5ce965408))
* store a bunny's photos and stop wiping release databases ([f4d9121](https://github.com/srednimax/binky-app/commit/f4d91215937fe2cd3cfdf589c1c5b1b1ac5212f1))
* take control of Android Auto Backup with a custom agent ([7e455d9](https://github.com/srednimax/binky-app/commit/7e455d908f458b1ba073754791050fe5f31391fc))
* take control of Android Auto Backup with a custom agent ([e368212](https://github.com/srednimax/binky-app/commit/e368212beac93bd75293a9904aa306d5a4b5f1e2))


### Bug Fixes

* keep the photo viewer open when the photo it opened on is deleted ([f2c0370](https://github.com/srednimax/binky-app/commit/f2c037077afceb82f240be5b5ee60392a9e56f6f))

## [0.5.0](https://github.com/srednimax/binky-app/compare/v0.4.0...v0.5.0) (2026-07-27)


### Features

* prove the release path, finish the listing assets, and draw an original icon ([100093e](https://github.com/srednimax/binky-app/commit/100093e6a3b918722314739b27777860fac3440d))
* replace the Noto Emoji icon with original art ([6075bec](https://github.com/srednimax/binky-app/commit/6075bec1e9d583a736ab2f9a04e26bff71a1fe4a))
* replace the template robot with a rabbit launcher icon ([025320f](https://github.com/srednimax/binky-app/commit/025320ffbe79a473963cab92e8e1dd7cb1b4efd6))
* replace the template robot with a rabbit launcher icon ([369ac54](https://github.com/srednimax/binky-app/commit/369ac54b438564479bccf5538ca8a758293a7f1b))

## [0.4.0](https://github.com/srednimax/bunny-app/compare/v0.3.0...v0.4.0) (2026-07-27)


### ⚠ BREAKING CHANGES

* applicationId changes from app.bunny.tracker to app.binky.tracker, so an existing install is orphaned rather than upgraded and has to be uninstalled by hand.

### Code Refactoring

* rename the app to Binky ([30c871b](https://github.com/srednimax/bunny-app/commit/30c871be16dbfdc815b75439ef63d58125bb630b))

## [0.3.0](https://github.com/srednimax/bunny-app/compare/v0.2.0...v0.3.0) (2026-07-26)


### Features

* add the weight data layer and ADR-0007's wipe-consent guard ([26c0729](https://github.com/srednimax/bunny-app/commit/26c0729bed337539df7abd3c2a5ecfa3bdcabe83))
* enter, correct and read weights, with the flag surfaced ([75f43ca](https://github.com/srednimax/bunny-app/commit/75f43cad03272801a7cc74983cde4525e299abf6))
* enter, correct and read weights, with the flag surfaced ([50463be](https://github.com/srednimax/bunny-app/commit/50463bef6a467d21633904eb0965aca48e3f47c5))
* evaluate the trend flag from the series and its watermark ([8b3b46f](https://github.com/srednimax/bunny-app/commit/8b3b46fff79022f4d6ea0eb55c6cb1df55576d27))
* evaluate the trend flag from the series and its watermark ([c23bcd9](https://github.com/srednimax/bunny-app/commit/c23bcd9ebcb83aab30c461627c0ccd473879cfc9))
* plot weight over time on a real date axis ([a6f9cfc](https://github.com/srednimax/bunny-app/commit/a6f9cfc43063999d16ffaa3125f4f9d00d81b4b0))
* plot weight over time on a real date axis ([139748e](https://github.com/srednimax/bunny-app/commit/139748e77ed2da5ecd8a86a27c6cdf6481ee9567))
* record and review observations, and log a healthy day ([90f7775](https://github.com/srednimax/bunny-app/commit/90f77758a7be84153740f65e64849c7c4c61de55))
* record and review observations, and log a healthy day ([67f1936](https://github.com/srednimax/bunny-app/commit/67f193680bc4fed9347275a794f8af114bb22ade))
* record observations, shared across a fluffle ([130861f](https://github.com/srednimax/bunny-app/commit/130861f34bac3ee00f9a03985f9c7a27095bfb76))
* record observations, shared across a fluffle ([2616ba7](https://github.com/srednimax/bunny-app/commit/2616ba719e6ae7e01001b7559d558c94cbec426a))
* weight data layer and ADR-0007's wipe-consent guard (checkpoint 2a) ([b0285fb](https://github.com/srednimax/bunny-app/commit/b0285fb2295ecd62bcaaa19a2f324ee1e49f930a))


### Bug Fixes

* pluralize the preserved copy's file count ([2de1bc3](https://github.com/srednimax/bunny-app/commit/2de1bc3b12177b07139b38dded1bf073f598ffd9))

## [0.2.0](https://github.com/srednimax/bunny-app/compare/v0.1.0...v0.2.0) (2026-07-25)


### Features

* add the Phase 1a data layer ([94667ed](https://github.com/srednimax/bunny-app/commit/94667ed3e166de679e77c33596d8ffb6ebd184d1))
* add the Phase 1b media pipeline ([9cfc12c](https://github.com/srednimax/bunny-app/commit/9cfc12c4035cceb5c7d0871154be2e8dcc4b9b6f))
* add the Phase 1b media pipeline ([39037f3](https://github.com/srednimax/bunny-app/commit/39037f3640c7760c667f68023e1662e572ee1b0a))
* add the Phase 1c navigation shell ([ecc4540](https://github.com/srednimax/bunny-app/commit/ecc4540adb6cbe918a0f3eb7d6ed82b88b8a9c73))
* add the Phase 1c navigation shell ([e9e49a3](https://github.com/srednimax/bunny-app/commit/e9e49a36a8a2a414293d531aa24629891bb8afbb))
* add the Phase 1d bunny CRUD ([d26ded0](https://github.com/srednimax/bunny-app/commit/d26ded0f5e7fc6228de21349ebef396879d9cdf4))
* Phase 1a — data layer ([175e36e](https://github.com/srednimax/bunny-app/commit/175e36efa7107d687a086e0f9b64cbad6d4b6358))


### Bug Fixes

* scope ViewModels to their navigation entry ([fcde05f](https://github.com/srednimax/bunny-app/commit/fcde05fd347996f9b7aebb7e23bd5a75982c9075))
