# App and patch development map

This guide maps the PineDrama app HushPine modifies to the code and release checks that make a patch. Its version and patch counts were last synced with the source on 2026-11-07. The generated [patch catalog](../patches-list.json) is the current source of truth for patch names and compatibility.

## What HushPine ships

HushPine is a Morphe patch bundle, not a PineDrama APK. Morphe Manager takes a PineDrama bundle chosen by the user, applies the selected patches, and signs the result with the user's existing key. The patch definitions and the runtime code they inject live in separate Gradle modules.

Source snapshot: version 0.1.0, 100 patch entries. Those come from gradle.properties and patches-list.json.

Published bundle: version 0.1.0, 100 patches.

Those come from patches-bundle.json and the release it names, and 0.0.0 means nothing has shipped yet. The source runs ahead of the published bundle until the next release ships. The index moves only after a new bundle is published and verified, which is the release sequence described in CONTRIBUTING.md. Don't hand-edit the index to make the two match.

Keep every Unreleased bullet under a `###` group. The release cutter preserves those groups, and the index stage appends them to the bundle description so Morphe Manager can show the newest release's whole change list.

## Target app and version boundary

| Property | Current patch target |
| --- | --- |
| App | PineDrama |
| Package | com.ss.android.ttmd.video |
| Supported version | 47.6.3 |
| Version code | 2024706030 |
| APK signer SHA-256 | e97ca0c8835c5a9e4f7702a0382b103ede9c5d51e5194c8db54f4c8833f54e18 |
| APK minimum Android API | 23 |
| Native code | arm64-v8a and armeabi-v7a |
| Extension minimum Android API | 23 |

PineDrama is built from TikTok's code at the same version number, with `Drama*Impl` stub services in place of much of TikTok's social side and its own resource table. It's obfuscated separately, so the class layout runs parallel to TikTok's while the short names differ, and a name found in both builds can belong to a different member or view. Don't copy a TikTok name into a PineDrama patch: check return type, body size and callers in the dex first. It has no stories tray, no Friends tab and no "For You" label. Its bottom tabs are Home, Discover, Rewards, Inbox and Profile, and its top tabs are Following, Drama and one the server picks between them (Short Video or AI fiction so far). Its deep links use the `snssdk845221` scheme. Global TikTok is [Hushfeed](https://github.com/SysAdminDoc/hushfeed)'s target, not this repo's.

The target identity is centralized in [AppCompatibilities.kt](../patches/src/main/kotlin/app/morphe/patches/shared/compat/AppCompatibilities.kt). Patch definitions use that compatibility object instead of copying package names, signer hashes, or version codes into each patch. The README's [Supported target](../README.md#supported-target) section is the user-facing version record, and its install steps keep naming the build the published release patches, since that's what people install. When the target moves, update AppCompatibilities.kt from the reviewed APK and add one line under Supported target in the form ``- Next release: PineDrama X.Y.Z, [on APKMirror](link) since YYYY-MM-DD (version code ...), SHA-256 `...`.`` The release's index step moves the install steps onto that build and drops the line.

The file APKMirror offers is split, and the extracted base holds no native libraries. Don't install or qualify the base alone. Merge the whole `.apkm`, check that every required module made it in, and treat the merged APK as the fixture.

## Build and injection path

1. Kotlin patch declarations under patches/src/main/kotlin describe compatibility, dependencies, options, and changes to PineDrama's bytecode or resources.
2. Runtime hooks and settings under extensions are compiled as Morphe extension payloads.
3. The :patches:buildAndroid task packages the patch code and both extension payloads into patches-<version>.mpp.
4. PatchListGenerator loads the built bundle and writes patches-list.json. That JSON records patch descriptions, categories, dependencies, options, package compatibility, signer, and target versions for Manager.
5. BundleVerifier checks the bundle's version, DEX payloads, checksum, target description, and exact match with the generated patch catalog.
6. Fixture tests and the desktop patcher check that the selected patch set still applies to the declared PineDrama APK.

The delivered bundle contains classes.dex, extensions/pinedrama.mpe, and extensions/shared.mpe. It doesn't contain PineDrama's APK. The app package name and certificate are checked separately from the patch bundle's source version. A release bundle is also signed with the key behind morphe-signing.pub, and Manager pins the first key a source signs with.

## Gradle module map

| Module | Responsibility |
| --- | --- |
| :patches | Kotlin patch recipes, compatibility metadata, patch tests, patch-list generation, and the .mpp bundle |
| :patches:stub | Compile-time declarations for types supplied by the patcher or host app |
| :extensions:pinedrama | PineDrama's Java runtime hooks, settings, localization, and Robolectric tests |
| :extensions:pinedrama:stub | Compile-time declarations for the host classes the extension references |
| :extensions:shared | Shared runtime extension payload |
| :extensions:shared:library | Shared settings, utility, and diagnostic code used by extensions and tests |

The Morphe Gradle plugin and the default extension namespace are configured in settings.gradle.kts. The version catalog pins patcher 2.1.0 and the README holds the Manager floor at 2.3.0 (morphe-desktop 1.20.0 is on the same patcher). Keep those pins compatible, because Manager refuses a bundle built for a newer patcher. The PineDrama extension compiles for Java 11 with API 23 as its floor. The shared library compiles for Java 17, also with API 23 as its floor. Module build files declare which payload name each extension produces. The stub modules let source compile against host types, and they're no substitute for checking the real APK.

## Where to look first

| Work area | Source of truth |
| --- | --- |
| Supported package, signer, and version codes | patches/src/main/kotlin/app/morphe/patches/shared/compat/AppCompatibilities.kt |
| Patch declarations | patches/src/main/kotlin/app/morphe/patches/pinedrama/ |
| Shared patch helpers and failure contracts | patches/src/main/kotlin/app/morphe/util/ and patches/src/main/kotlin/app/morphe/patches/pinedrama/shared/ |
| Runtime extension hooks | extensions/pinedrama/src/main/java/app/morphe/extension/pinedrama/ |
| Shared runtime settings and utilities | extensions/shared/src/main and extensions/shared/library/src/main |
| In-app setting keys, types, defaults, and availability | extensions/pinedrama/src/main/java/app/morphe/extension/pinedrama/settings/Settings.java |
| Settings screen entry hook | extensions/pinedrama/src/main/java/app/morphe/extension/pinedrama/settings/PineDramaActivityHook.java |
| Settings page rows and categories | extensions/pinedrama/src/main/java/app/morphe/extension/pinedrama/settings/preference/ |
| Patch tests | patches/src/test/kotlin/ and patches/src/test/resources/ |
| Runtime tests | extensions/pinedrama/src/test/java/ |
| Generated patch metadata | patches-list.json |
| Build, fixture, device, and release helpers | scripts/ |

The runtime code is grouped by feature under extensions/pinedrama/src/main/java/app/morphe/extension/pinedrama/. Search the generated patch catalog by display name first, then follow its dependencies into the Kotlin and Java sources.

The current catalog's 100 patches are grouped as follows:

| Manager category | Patches |
| --- | ---: |
| Comments | 14 |
| Downloads | 5 |
| Feed | 9 |
| Inbox | 6 |
| Interaction | 7 |
| Interface | 4 |
| Performance | 7 |
| Playback | 16 |
| Privacy | 14 |
| Search | 4 |
| Settings | 14 |

The [patch catalog](../patches-list.json) has the complete names and plain-English descriptions. Keep the prose in that generated catalog aligned with the README's patch table and the in-app setting title.

## How the in-app settings connect

The settings patch is defined in patches/src/main/kotlin/app/morphe/patches/pinedrama/misc/settings/SettingsPatch.kt. It hooks the app's AdPersonalizationActivity and calls PineDramaActivityHook, which builds the extension's PineDramaPreferenceFragment inside that activity. Preference category classes build the pages and rows.

The hook checks the launch intent. It accepts the HushPine settings extra or the `morphe_settings` action, then returns to the app's original path for other launches. An activity dump naming `AdPersonalizationActivity` can therefore describe HushPine settings rather than PineDrama's ad controls. Record the intent and visible screen together.

Settings.java declares runtime setting keys, types, defaults, and availability rules. BaseSettings and Setting in extensions/shared/library provide storage and common behavior. SettingsStatus loads settings and exposes the values that bytecode hooks read. A setting key is persisted on the user's device, so keep existing keys stable across updates. A renamed key needs a migration. Many stored names and files still say `hushfeed` on purpose, so a copy of PineDrama patched by Hushfeed keeps its settings when it moves here.

An install-time Morphe patch option is different from an in-app setting. For example, Change app name takes its name while the APK is being built. Advanced downloads depends on the Settings patch and exposes runtime switches in the HushPine settings. The generated catalog lists those dependencies and patch options.

The detailed in-app settings pages are separate from Morphe Manager's patch categories. Each is one row on the home page and opens one level below it:

| Settings page | Category class |
| --- | --- |
| Feed filter | FeedFilterPreferenceCategory |
| Feed screen | InterfacePreferenceCategory |
| Playback | PlaybackPreferenceCategory |
| Comments | CommentsPreferenceCategory |
| Inbox | InboxPreferenceCategory |
| Downloads | DownloadsPreferenceCategory |
| Share sheet | SharePreferenceCategory |
| Privacy | PrivacyPreferenceCategory |
| App | ExtensionPreferenceCategory |
| Region | SimSpoofPreferenceCategory |
| Diagnostics | DebugPreferenceCategory |

FeedNavigationPreferenceCategory (Feed tabs) and ScreenTimePreferenceCategory (Screen time) are still in the source, but their isAvailable() returns false because their hooks don't exist on PineDrama. Their settings stay in Settings.java and do nothing until those pages come back.

PineDramaPreferenceFragment.createMasterMenu builds the home page. Each section shows only if its page is available from the installed patches, so a small patch selection never shows an empty row. SettingsHeaderPreference holds the title and compact status, with SettingsSearchEntryPreference beneath it. Backup and restore and Feature Gate Lab sit after the pages, then the HushPine heading holds the status card, Pause, Diagnostics, About, Report a problem and the Ko-fi row. Search indexes individual controls and checklist members, including retained aliases for renamed settings, and a result opens the existing page and scrolls to the matching row.

Translations live in extensions/pinedrama/src/main/l10n/. The generator scripts/gen-l10n.py refreshes the English base table and generated translation class. Run it after editing translation tables. The tests compare the sources and generated output.

## Adding or changing a patch

1. Start from the exact patch name in patches-list.json. Read its Kotlin declaration, dependencies, runtime hook, tests, and the matching README entry.
2. Confirm the behavior against the declared APK. Record the package, version, and signer.
3. Put the patch in the closest existing feature package. Use a narrow fingerprint and validate the exact number or shape of matches. PatchContracts.kt provides helpers that fail with a patch-specific message when a match disappears, becomes ambiguous, or changes shape.
4. Decide whether the control belongs in Manager at patch time or in HushPine settings at runtime. For a runtime switch, add its stable key and default in Settings.java, connect it to the category row, and inject a hook that reads the setting. Add translation entries when new setting text is introduced.
5. Add a patch test for the selected bytecode or resource contract. Add a runtime test beside the Java feature when behavior can be exercised without the app's private runtime. Keep APK-dependent tests tied to the external fixture directory.
6. Regenerate patches-list.json from the built Morphe bundle. Don't hand-edit generated rows.
7. Run the README's test and bundle sequence. The all-patches verifier invokes the Morphe desktop patcher against the declared fixture. Unit tests alone don't prove that the patcher can apply a patch to PineDrama.
8. For UI or device-specific behavior, install a signed build only on a test device where the existing PineDrama signing key matches. The device helper's -Replace option removes PineDrama and its local data.

## Moving to a newer PineDrama target

Treat a target update as a new reverse-engineering pass. PineDrama can reuse short resource names for unrelated views, change obfuscated method shapes, alter server-backed defaults, or move a feature into a dynamic module.

1. Verify the exact merged APK and the split bundle it came from. Record the package, version name, version code, signer, and APK hash. Update AppCompatibilities.kt and any patch that reads a version code.
2. Re-record the content-marker corpus from real videos with tools/verification-probe/record-markers.ps1. Refresh ContentMarkerCorpusTest inputs and the Feature Gate Lab catalog with :patches:generateGateCatalog.
3. Re-read the target's view tree and dynamic-module owners. Update patches/src/test/resources/view-id-anchors.txt with the owning class and the matching code. Replace stale resource names instead of retaining fallbacks that can resolve to a different view.
4. Review resource optimizer inventories, language-pack files, and resource IDs against the new APK. Keep the path and digest checks version-specific.
5. Set HUSHPINE_FIXTURE_DIR to the reviewed APK, run the patch and runtime tests, then apply the complete patch set with scripts/verify-all-patches.ps1 and the Morphe desktop CLI. Tests don't invoke the patcher, so passing tests alone isn't target approval.
6. Run the [patch audit](#patch-audit) on a phone with the full catalog applied, fix what fails, and commit the new results file next to the old one. Then update the README target details, changelog, generated catalog, and release facts as one reviewed target move.

## Verification layers

| Check | What it proves |
| --- | --- |
| :extensions:pinedrama:test | Runtime Java behavior through Robolectric |
| :extensions:pinedrama:lint and :extensions:shared:library:lint | Android API-floor calls and guards |
| :patches:test | Patch logic, fingerprints against the fixture, and catalog consistency |
| :patches:generatePatchesList | Catalog generated from the compiled patch bundle |
| :patches:buildAndroid plus :patches:verifyBundle | Bundle payloads, manifest version, checksum, compatibility description, and catalog match |
| scripts/verify-all-patches.ps1 | Each patch applies through the Morphe desktop patcher to the declared APK fixture |
| audit/run-audit.ps1 | Every patch does what its description says on a phone running the full catalog |
| scripts/walk-app.ps1 | Every top-level screen and settings page opens on a phone with no crash |
| scripts/verify-injected-registers.ps1 | The Android runtime's own verifier accepts the patched app |

The APK fixture is a vendor file outside the repository. Set HUSHPINE_FIXTURE_DIR to its folder. Set HUSHPINE_DESKTOP_JAR to the Morphe desktop CLI jar for the apply-all verification. CONTRIBUTING.md documents the other script inputs and the pre-push checks.

PineDrama's code is ARM only. Translated on an x86_64 emulator it pegs the CPU, screen captures come back black and uiautomator never goes idle, so device checks need a real phone.

## Patch audit

The verifier above proves each patch applies. The audit proves each one still works once PineDrama runs. audit/catalog.json has one entry per patch: its settings with their defaults, where it shows, and the checks that prove it. A check is a list of steps the runner carries out in order. Some read the patched APK (an emptied library, a manifest line, a string in the dex). Others drive the phone through the verification probe: flip a HushPine setting, open the feed or a search, ask the probe what's on screen and hold the answer to a regex, wait for a log line, or save a screenshot with the exact thing a person should look for. Most checks compare the switch off with the switch on, so a hider that hides nothing fails instead of passing quietly.

Results go to `audit/results/pinedrama-<version>.json`, one file per PineDrama version, with the APK hash, the HushPine commit, the phone's model and Android version (never its serial), and each check's status (pass, fail, review, inconclusive, error or skipped), why it failed and which evidence it left. Inconclusive means a precondition never held, like no video with comments turning up in eight swipes, so it says nothing about the patch. The evidence (screenshots and probe answers) goes to `-OutDir`, a temp folder unless you pass one. The results file names each piece by its file name in that folder and never by a path on your machine, since it gets committed. The results file in the repo today was written before the split, against Hushfeed's bundle.

To audit a new PineDrama version:

1. Patch the full catalog with scripts/patch-for-device.ps1, signed with the key of the PineDrama already installed on the test device. For installation, supply `-Serial`, `-LeaseDir` and `-ChatIdentity`. A local build without `-Serial` needs no lease.
2. Build and install the probe with tools/verification-probe/build.ps1, signed with the same key. Its installation and uninstall paths require the same lease arguments.
3. Run `audit/run-audit.ps1 -Apk <patched apk> -BuildReceipt <patched apk>.audit-build.json -Bundle <mpp> -Serial <phone> -LeaseDir <shared lease folder> -ChatIdentity <task identifier> -OutDir <evidence folder> -Install`. The device builder writes the receipt beside its signed APK. The runner verifies that receipt against the APK, bundle and clean source manifest, then compares the installed base APK hash before starting checks. The Windows device scripts also accept `HUSHPINE_DEVICE_LEASE_DIR` and `HUSHPINE_CHAT_IDENTITY`. They hold an exclusive lease through their device operations and stop if ownership expires or is lost. Existing leases are never reclaimed automatically. `-Only 'Patch name,category:Feed'` reruns a part, and `-NoDevice` runs just the APK checks.
4. Look at every screenshot a review check saved, judged against its review text. Write the verdicts as `{"Patch name/check-id": {"status": "pass", "note": "...", "receiptId": "<check receiptId>"}}` and merge them with `-ApplyVerdicts <file> -Results <results file> -OutDir <evidence folder>`. Only undecided review checks accept `pass` or `fail`. The importer checks the definition, execution receipt and actual evidence hashes before changing any results.
5. Fix each failing patch, add a test for what broke, and rerun that patch with `-Only` until it passes.
6. Diff the new results file against the previous version's. A check that passed before and fails now is the first thing to look at.

Run `audit/run-audit.ps1 -ValidateResults -Results <results file> -OutDir <evidence folder>` to check saved evidence without rewriting it, and `audit/run-audit.ps1 -Validate` to check the catalog with no device. Add `-Apk <expected apk>` to qualify evidence against another artifact.

The verification probe sends diagnostic reports in encoded frames with byte counts and a SHA-256 digest. Missing or damaged frames leave the check inconclusive. Rebuild the probe before rerunning these report checks. A missing current video is also inconclusive when the check declares that video as a prerequisite, while a probe exception or an observed expectation failure still fails.

A whole-app walk writes a `walk-receipt.json` with the bundle's hash, the installed APK and the result. `scripts/release/release.ps1` won't publish until the walk comes back PASS.

A patch whose screen moved or whose setting changed needs its catalog entry changed in the same commit as the fix. The runner restores every setting a patch's checks touched and turns Log diagnostics on only for the run. It never taps anything labeled Follow, Message, Send, Post or the like, so checks can't reach other people. Keep new checks inside the same limits: no posting, liking, following, messaging or going LIVE, and no strangers' profiles. Opening a creator's profile with Ghost mode off sends them a profile view, so a check that needs a profile uses your own, and one that could land on a profile if its patch broke (a swipe left) turns Ghost mode on too. Never tap anything on the Rewards tab: it's a web page whose dialogs can accept terms for the account.

A `tap` step finds its node in a uiautomator dump by `text`, `desc` or `label` (either of the two, for Compose pages such as Settings and privacy, which put a row's words in its description and leave its text empty). uiautomator can't dump a screen while a video plays behind it, which includes the comment sheet. Inside the sheet, press through the probe instead: a `findtext` probe step with `"match": "regex"` finds a label by pattern, `"click": "true"` clicks it, `"hold": "900"` holds a finger on it for a long-press menu, and `"sibling": "next"` moves to the view right after the label for an icon with no text (the sort glyph beside "189 comments"). An empty field matches by its hint. The probe drops the invisible direction marks the app puts around numbers, so a pattern can start at a digit. Ids are renamed every build, so a route by id is a last resort.

A few habits keep checks honest on any phone. Match text sizes in sp (the probe's `textSizeSp` and `textSp`), never pixels, since pixels follow the screen's density. A settings list only builds the rows on screen, so a `findtext` for a row lower down takes `"retrySwipe": 4`, which scrolls and asks again. A `log` step only sees lines written after the step before it started, so a line logged at startup goes straight after the `restart`. A theme check switches the phone with `{"night": "yes"}` or `"no"`, and the runner puts the phone's own mode back when that patch is done. `{"open": "appinfo"}` shows Android's App info page, the same name and icon the launcher uses, on a page that looks the same on every phone. `{"vibrations": "none"}` or `"some"` reads Android's own record of what it played for the app since the check started (or since the last such step), so a haptics check doesn't depend on a log line. An animation that Android plays (a screen sliding in) can't be read from the app at all, so `{"record": "start"}` films the steps after it and `{"record": "stop", "transition": "still"}` or `"animated"` counts the frames part way between the first screen and the last (it needs ffmpeg). A test phone often keeps Android's animation scales at 0, where nothing slides and no animator runs, so such a check starts with `{"animations": "on"}`, which sets all three scales to 1 until that patch is done, when the runner puts the phone's own values back. When a check needs a certain kind of video (one with 10,000+ likes or comments, one 15 seconds or longer), let `videoinfo` pick it with `retrySwipe` and `"precondition": true`, so a feed that never serves one reads inconclusive instead of failing. A switch flipped while the app is open only reaches what it draws after that. The next video is usually laid out already and a profile header keeps its icons, so read a few swipes later or after a `restart`, and make a switch that only acts as the app draws something a restart switch.

The test commands are in [README.md](../README.md#building-from-source). The release procedure and fixture setup live in [CONTRIBUTING.md](../CONTRIBUTING.md). Update those instructions when the Gradle task graph or release steps change.

## Updating this guide

Refresh the target table when a new PineDrama build is inspected. Update the module map when payload boundaries change. Patch counts are a dated snapshot, and patches-list.json remains the authoritative list. Keep device observations tied to the exact PineDrama version and mark any behavior that wasn't reached without an account.
