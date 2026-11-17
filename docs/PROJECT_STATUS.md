# AI Project Manager — current project status

Updated: 2026-11-16 (Asia/Kathmandu).
Current authorized cycle: VNext 3 on `feature/vnext3`, starting HEAD `a9afb30`.
Release status: **NOT Ready for Release**.

## Version 0.2.0 configuration — 2026-11-16

Starting branch/HEAD: `feature/vnext3` /
`c35242563f7978ee193f79d866970a66ea891588`. This is a configuration-only
increment, not a built, installed, distributed, or accepted release.

Local evidence was inspected before choosing versionCode 3, without accessing
Android devices or remote services:

- Read-only `aapt dump badging` of existing official and Copilot APKs confirmed
  base Debug `0.1.0` / code 1 and isolated VNext `0.1.0-vnext3` / code 2.
  Retained output metadata agrees; Android-test APK codes are not app releases.
- Official local Git history affecting version settings records only app codes
  1 and 2. The Codex source configuration remains code 1. Inspected local status
  and release documentation did not identify a higher distributed app code.
- Copilot's existing `docs/VNEXT3_RELEASE_READINESS.md`, in its emulator acceptance
  section, records the prior authorized VNext in-place update from code 1 to 2.
  This is historical documentation, not a new installed-package inspection.

Code 3 is therefore the next code supported by available local evidence. This
does not establish the current version or signer on the owner's installation;
that remains a separate check before any approved installation. If a higher
installed/distributed code is later established, choose a code above it first.

| Variant | Configured versionName | Configured versionCode | Application ID |
|---|---|---|---|
| Debug | `0.2.0` | 3 | `com.karim.aiprojectmanager` |
| Release | `0.2.0` | 3 | `com.karim.aiprojectmanager` |
| VNext | `0.2.0-dev.1` | 3 | `com.karim.aiprojectmanager.vnext` |

Both the default code and the explicit VNext output override were advanced to 3.
The `vnext` build type, isolated application-ID suffix, signing behavior, source
sets, test build type, and GitHub Actions remain unchanged. Subsequent distributed
builds need increasing codes for the same package, including development builds;
update the base code and VNext override consistently. Version names follow
MAJOR.MINOR.PATCH, with `-dev.1` denoting this development candidate. No historical
release or Git tag was created, and historical artifact records remain unchanged.

Verification: exact diff review and `git diff --check` only. No build, test, Lint,
emulator/device action, install/uninstall, commit, push, or backup modification was
performed. Existing APKs and output metadata still describe the older builds;
there is no newly verified 0.2.0 APK. Future acceptance must check packaged metadata,
package/signature compatibility and non-destructive upgrade/data continuity,
along with pending startup/language, storage/restore and GitHub acceptance.
**NOT Ready for Release**.

## Finalization — static startup and two-option language UI — 2026-11-16

Starting branch/HEAD: `feature/vnext3` /
`1bc25c74a53e68764da2427cf47d39de89bd079e`. The uncommitted Phase 2B work
was preserved. No commit, remote operation, emulator/device action, installation,
uninstall/data clear, version/identity/signing change, or backup edit occurred.
The two temporary MainActivity copies remain untouched.

### Implementation

- Settings now displays exactly العربية and English. Automatic mode remains the
  default for missing/invalid values and remains compatible with stored `system`.
  The UI marks the effective device-resolved language without saving a preference;
  clicking either option records an explicit choice through the existing workflow.
  Existing `ar`/`en`, XML translations, RTL/LTR and Activity recreation remain.
  This supersedes Phase 2B's visible third option, not its persistence behavior.
- Added a static Compose splash with the existing approved launcher foreground,
  the byte-identical reference-derived background from the Copilot workspace,
  and the exact text `© Karim S.K. Morsi 2026`. Logo size/placement follows the
  reference implementation. The background does not animate; no Aurora layer
  was invented. Day/night launch windows use deep navy and API 31+ has native
  splash color/icon resources before the custom Compose frame.
- `StartupClock` requires at least 3,000 ms of resumed, displayed-frame time and
  completed initialization. Paused/background time does not count. Visible time
  survives Activity saved-state recreation. A completed startup does not impose
  another timed hold on recreation; pending loading still has visible feedback.
- Initialization loads appearance and project models off the main thread.
  `StartupLoader` prevents concurrent/duplicate reads after success and supports
  explicit retry after failure. A spinner and localized loading text are shown
  while pending; failure shows a localized explanation and Retry. Retry does not
  add another three-second hold if the required visible time already elapsed.
- Startup reads project storage with `readOnly = true`, using a plain input stream
  instead of AtomicFile recovery. Pending `.new`/`.bak` files are rejected and
  retained, never deleted or promoted during the splash. Normal storage APIs keep
  their existing default behavior. Loaded models are passed into ProjectManagerApp,
  avoiding its duplicate initial read; app navigation/GitHub initialization starts
  after the gate. The schema and existing write safeguards are unchanged.

### Executed verification

Installed Gradle 8.13 / JDK 17, offline:

```text
gradle --offline --no-daemon --console=plain :app:assembleDebug \
  :app:testDebugUnitTest \
  --tests com.karim.aiprojectmanager.StartupStateTest \
  --tests com.karim.aiprojectmanager.AppLanguageTest \
  --tests com.karim.aiprojectmanager.AppLanguagePreferencesTest \
  --tests com.karim.aiprojectmanager.AppPreferencesTest \
  --tests com.karim.aiprojectmanager.ThemePreferencesTest :app:lintDebug
```

- Final Debug assembly/resource processing succeeded. The final Debug artifact was
  rebuilt without the synthetic Client ID override used for compile-only VNext
  verification. Debug retains the original application ID: do not install it over
  the owner's protected application as an acceptance shortcut.
- JUnit XML confirms **33 passed, 0 failures/errors/skips**: startup 5, language 5,
  language preferences 4, preference adapter 7, appearance preferences 12.
  Startup tests cover the exact three-second boundary, slow readiness, foreground
  pauses, saved elapsed time, success reuse, failure/retry and concurrent requests.
- Lint succeeded: **0 errors, 19 warnings, 1 hint**. SDK XML compatibility and
  deprecated-API warnings remain. App Bundle language-delivery acceptance remains
  outside this local Debug check. `git diff --check` passed.
- `:app:compileVnextAndroidTestKotlin` was checked with the explicitly synthetic
  `-PgithubAppClientId=SyntheticBuildFixture`, for source/resource compilation only.
  No VNext APK from that fixture was installed or presented as login-ready.
- Added six Android acceptance cases in `StartupAcceptanceTest.kt` (presentation
  and storage classes): two visible language choices without preference writes,
  loading/copyright, Retry, unchanged project bytes/unknown fields, retained atomic
  sidecars, and malformed-byte preservation. These were compiled, **not executed**.

### Remaining acceptance and limits

No running Android screenshot or visual match is claimed. The supplied reference
and its existing background derivative were inspected, not a rendered app. Native
system-splash transition, crop on other aspect ratios, logo/copyright proportions,
large fonts, TalkBack, cold start, pause/recreation timing, pending editor/navigation
state, device-language changes and real preference durability need owner-authorized
Android acceptance. The existing derivative itself also needs visual comparison
with the original reference; byte equality does not establish visual acceptance.
Permanent corrupt data or pending atomic files may require separately approved
recovery; Retry intentionally does not repair/delete/overwrite those files.
Startup does not wait for online GitHub verification; existing GitHub behavior is
retained. Fatal platform/resource failures cannot be guaranteed recoverable by an
in-app Retry. Prepare a VNext build with the owner's genuine configured Client ID
and verify its isolated package/signature before any separately approved install.
**NOT Ready for Release**.

## Phase 2B — follow device language — 2026-11-16

Starting branch: `feature/vnext3`; HEAD:
`1bc25c74a53e68764da2427cf47d39de89bd079e`. The only starting untracked
files were the two temporary MainActivity backup copies; both remain untouched.

- Added `system` alongside the existing `ar` and `en` values in the existing
  `ui_preferences` / `language` preference. Missing or unsupported values resolve
  to Follow device language without automatically rewriting the stored value.
  Existing explicit Arabic/English selections retain their meanings.
- System mode resolves the device's primary `ar` language to Arabic and every
  other primary language to English. Locale resolution uses
  `Resources.getSystem().configuration`, independently of the Activity's
  overridden locale; copied configurations retain other settings and apply
  the resolved locale and RTL/LTR direction. Normal Android locale configuration
  recreation remains in use; no locale configChanges interception was added.
- Added the third radio option using the existing Settings design and XML
  translations. Successful selection still recreates the Activity. The Activity
  now retains one language preference controller so a failed disk commit does
  not falsely change its confirmed selection even if SharedPreferences changed
  its in-memory map. The existing failure notification and user retry remain.
- Reused the existing AppPreferences adapter for isolated JVM coverage. No
  new translation catalog, dependencies, application identity, signing, versions,
  appearance preferences, project data/schema, or GitHub logic was changed.

Verification actually executed with installed Gradle 8.13 / JDK 17, offline:

```text
gradle --offline --no-daemon --console=plain \
  :app:compileDebugKotlin :app:processDebugResources \
  :app:testDebugUnitTest \
  --tests com.karim.aiprojectmanager.AppLanguageTest \
  --tests com.karim.aiprojectmanager.AppLanguagePreferencesTest \
  --tests com.karim.aiprojectmanager.AppPreferencesTest \
  :app:lintDebug
```

- Gradle completed successfully. Debug Kotlin/Java compilation and Android
  resource processing succeeded; this was not an APK installation or runtime test.
- JUnit XML records **16 passed, 0 failed/errors/skipped**: 5 language tests,
  4 language preference tests, and 7 existing platform preference adapter tests.
  Coverage includes legacy values, missing/invalid values without writes,
  primary-language resolution, explicit overrides, save/reload through a new
  controller, failed commits including in-memory changes, and retry.
- Lint completed with **0 errors, 19 warnings, 1 hint**. In particular,
  `AppBundleLocaleChanges` warns about future language delivery in split App
  Bundles; no bundle/dependency change was made. SDK XML compatibility and
  deprecated API warnings were also emitted. `git diff --check` passed.

Deferred: actual Android preference durability after cold restart, system-language
change delivery, Arabic/English visual and RTL/LTR acceptance, Activity recreation
with pending edits/navigation/restore or GitHub state, and accessibility. JVM
save/reload tests use an isolated store and do not establish platform durability.
A failed platform commit can leave changed in-memory preferences; this controller
retains its confirmed choice, but behavior across later recreation/process restart
under storage failure still needs Android acceptance. No state-loss defect was
reproduced, so no alternative navigation/session architecture was introduced.
No emulator/device, instrumentation, commit, merge, push, or backup modification
was performed. **NOT Ready for Release**.

## VNext 3 daily-use stabilization — 2026-11-14

The owner-approved work started on the clean `feature/vnext3` baseline
`a9afb300b9efe8b3aea86ffd257998e5ee463959`. Existing project/task JSON and
application package identity remain unchanged. VNext alone advances to version
code 2 / `0.1.0-vnext3`; signing configuration and the existing debug signing
key were not changed.

Tasks now have confirmed deletion and an explicit reset-to-NOT_STARTED action.
Roadmap entries are parsed locally using conservative Markdown/imperative
rules; proposals are capped, deduplicated against existing tasks, editable,
selectable, and saved only after confirmation. Phases stay visible in the
proposal review. A restore flow now stages and validates the legacy JSON array,
previews its contents, requires a second explicit replace-all confirmation,
checks the candidate and live-file fingerprints, and keeps an exact private
safety copy before replacing existing bytes. Missing storage is supported;
corrupt existing bytes can be replaced only after explicit confirmation and
are snapshotted without reinterpretation. No schema, merge, or GitHub write
path was added.

Focused host regressions passed: **6/6** roadmap proposal and task deletion
tests. Debug/VNext APK assembly, VNext Android-test APK assembly, and Debug and
VNext Lint succeeded; Android-test sources compiled but were not executed.
Lint reports 0 errors, 27 warnings, and 1 hint. No emulator, device, install,
instrumentation, live GitHub request, commit, or push was performed. The VNext
APK metadata and hash, test artifacts, gate decisions, and remaining acceptance
work are recorded in [VNext 3 release readiness](VNEXT3_RELEASE_READINESS.md).

Storage/restore round-trip execution, task UI acceptance, backup restoration on
Android, offline use, live authenticated GitHub workflows, and installed-app
upgrade acceptance remain unverified. Release status remains **NOT Ready for
Release**.

## Phase 1 — GitHub planning-document import — 2026-11-14

Implemented on `feature/vnext2-document-import`, starting from clean HEAD
`c1ff5cfb2cd1ca17492e36bf39ac141d40911cff`. The preview detects exact root `README.md`,
`BLUEPRINT.md` and `ROADMAP.md` names at the displayed commit. README remains preview-only;
available Blueprint and Roadmap content is fetched at that commit, mapped in the confirmation
preview, and saved into a newly imported local project. Existing repository metadata, duplicate
checks, stale-write protection and local project data are preserved. Missing/unavailable files
are reported; planning documents over 64 KiB are skipped instead of truncated. No task extraction,
recursive discovery, manual file selection, local schema change or GitHub write was added.

The import action is now in a fixed preview footer. Final confirmation shows project/repository
identity and compact document statuses rather than repeating document text; its summary is
scrollable with confirmation actions outside it. English and Arabic labels were updated.
Focused Debug host tests passed: **8 tests, 8 passed** across
`GitHubPreparationTest` and `GitHubPlanningDocumentImportTest`. The focused Gradle command also
compiled the changed Debug app sources. The focused UI regression was updated but not run;
instrumentation and live GitHub/API validation were not performed. No emulator actions occurred.
These results do not verify real repository authorization, live response behavior, or Android UI
acceptance. Release status remains **NOT Ready for Release**.

## VNext 2 final UI improvements — 2026-11-14

On `feature/vnext2-document-import`, the project-details navigation now keeps all four tabs
visible: one row when the available width and font scale allow it, otherwise two rows. The home
footer now has separate Import from GitHub and New Project actions, retains navigation/IME inset
handling, and reserves its measured height from the project list. The import action opens the
existing authenticated repository browser directly when connected, or routes to Settings to
connect. Settings retains GitHub status and Disconnect while removing its duplicate browse entry.
Arabic and English labels are provided. Import mapping, duplicate checks, read-only API calls,
and project persistence are unchanged.

Debug app assembly and VNext Android UI test-source compilation succeeded. The focused host tests
passed: **8 tests, 8 passed** across `GitHubPreparationTest` and
`GitHubPlanningDocumentImportTest`. The SDK emitted a non-fatal SDK-XML-version compatibility
warning; Kotlin reported deprecated clipboard and soft-input APIs. Updated Compose UI tests were
compiled but not executed; instrumentation, emulator, install, and live GitHub checks were not
performed. Release status remains **NOT Ready for Release**.

## GitHub access follow-up and APK preparation — 2026-11-14

Starting state was the owner-specified `feature/github-snapshot-preview` branch at
`3b59f664c72fc6d84d580ad732b89cd6b6679d4c`, with a clean worktree. The existing stash and
independent backup were preserved. The existing `CI_Android_35_Repro` API 35 AVD was already
booted with 6 GiB guest RAM and SwiftShader; it remained ADB-connected, so it was not restarted,
wiped, or reconfigured. No physical device was used.

VNext started a fresh GitHub App device flow. GitHub accepted the app-generated device code and
showed the consent page for **Karim AI Project Manager**, signed in as `karim-kmorsi`. The page
identified the requested identity/resource access, but its visible **Authorize** control was
disabled while the form reported valid; **Cancel** was the only enabled consent action. The
pending attempt was canceled. No user-access token was issued or persisted, and no token-bearing
API request was made from VNext during this follow-up.

The implementation still uses the expected GitHub App user-token flow (`ghu_` bearer token,
verified with `GET /user`) and enumerates `GET /user/installations` followed by
`GET /user/installations/{installation_id}/repositories`, with numbered pages and `Link`
pagination. The browser defaults to all visibility and applies search/privacy filters only to
loaded results; no durable repository cache or target-name exclusion was found. Prior live VNext
evidence still records only `AIAndroidApp` and `ZOOM-CCTV-Manager`. The owner’s separate `gh`
credential can read the private target but is not the VNext token; its installation-list request
was rejected, so it cannot establish VNext installation access. The exact live API response for
the VNext token, target visibility in its installation, and import/refresh/Actions/Issues remain
**BLOCKED** pending successful GitHub consent. No installation settings, permissions, repository
visibility, or remote contents were changed.

The focused repository API/UI instrumentation suite from the immediately preceding attempt ran
**9 tests: 9 passed, 0 failed, 0 skipped**; those responses were synthetic, not live GitHub
acceptance. This follow-up did not rerun instrumentation because its prior Gradle cleanup removed
the tested package. No app source defect was confirmed and no code fix was made.

`:app:assembleVnext` completed successfully. `app/build/outputs/apk/vnext/app-vnext.apk` is
package `com.karim.aiprojectmanager.vnext`, version `0.1.0-vnext` (code 1), SHA-256
`f021201b6852aa0074b8bf08eca60948d460d558a18dd9708527b10c8aaadd7c`, signing-certificate
SHA-256 `301a9e4f477d0e53116caaa7bb8856afce8fd721aa0e4897d4b8ce7139e6e65f`. The installed and
built APK signatures matched; the APK was update-installed without clearing app data and launched
on the emulator. No project or GitHub session existed in the fresh VNext app before the flow.

The GitHub access cause is **not resolved** because the normal consent action is externally
blocked; do not infer an installation-selection defect without the VNext token’s API response.
No source or remote-repository changes were made. Release status remains **NOT Ready for Release**.

## Final autonomous stabilization and delivery pass — 2026-11-14

Work was performed on the owner-specified `feature/github-snapshot-preview` branch at the
expected starting HEAD `13cb04503ec609e3670253624dba5e413f4c52df`. The existing stash was
preserved. The batch corrected an RTL document-body overlap and made UI tests use stable project
tab/back semantics and English/Arabic selectors rather than relying on one locale's literal
strings. Existing persistence and storage assertions were retained.

Final local Gradle checks passed: Debug/VNext assembly, Debug/VNext host tests, VNext Android-test
APK assembly, and Debug/VNext Lint. Each host-test variant ran **118 tests, 118 passed**. The full
API 35 VNext instrumentation report contains **220 tests: 215 passed, 0 failed, 5 skipped**;
skipped cases are not counted as passes. Debug and VNext Lint each reported zero errors, 27
warnings, and one hint. An older retained Debug instrumentation report dated 2026-11-12 records
206 tests with 16 failures and 5 skips, including locale-sensitive text and task-control
selectors. Those assertions were investigated and the updated source was subsequently exercised
by the full passing VNext suite; the old Debug report was not overwritten or counted as passing.
Instrumentation uses isolated synthetic data, not owner project data.

The `CI_Android_35_Repro` API 35 AVD (`emulator-5554`) booted and remained ADB/System-UI
responsive. Its configured guest RAM is 6 GiB; the run used SwiftShader. No AVD wipe, deletion,
or RAM increase occurred. At the final host snapshot, about 0.9 GiB RAM was available, 8.2 GiB
swap was in use, disk had 324 GiB free, and the emulator process briefly used about 313% CPU.
Do not increase AVD RAM without new evidence. The instrumentation runner's `uninstall_after_test`
setting explains why the package and private preferences disappeared after testing; the final
APK was reinstalled with `adb install -r` and launched. No project data was present afterward.

Appearance and language were visually checked in English/Arabic and Light/Dark. The final
installed app was set to Dark/English and retained those preferences after force-stop/relaunch.
The dashboard's in-app logo renders without the launcher halo. **The launcher logo defect is
not resolved:** the final adaptive icon still shows a black circular ring in the Pixel app
drawer (`app/build/acceptance-screenshots/final-launcher-adaptive.png`). A static-icon experiment
removed the black ring but produced a white circular backplate and an undersized mark, so that
approach was rejected and the adaptive resources were restored. Changing the background alpha
from zero to one did not change the rendered ring and was reverted. The asset's transparent
background alone did not establish a visually acceptable result. Dark/Light/Arabic/English and
launcher evidence is retained under `app/build/acceptance-screenshots/`.

The final VNext APK is `app/build/outputs/apk/vnext/app-vnext.apk`, package
`com.karim.aiprojectmanager.vnext`, version `0.1.0-vnext` (code 1). SHA-256:
`f021201b6852aa0074b8bf08eca60948d460d558a18dd9708527b10c8aaadd7c`. Signing-certificate
SHA-256: `301a9e4f477d0e53116caaa7bb8856afce8fd721aa0e4897d4b8ce7139e6e65f`. The APK was
update-installed and launched on the emulator. No physical device was targeted.

No new live GitHub authorization or API acceptance was performed in this batch. The prior live
repository browser results documented above exposed `AIAndroidApp` and `ZOOM-CCTV-Manager`, but
not `AI-Project-Manager`; the app is currently disconnected after instrumentation cleanup.
Target-repository access, its import/refresh/Actions/Issues workflows, launcher-halo acceptance,
physical-device checks, and other open release gates remain pending. No remote GitHub write,
push, merge, or main-branch change occurred. **NOT Ready for Release**.

## Final logo and appearance refinements — 2026-11-14

The dashboard logo's central cutout contained 29,822 low-alpha dark pixels in
the foreground image. Only those alpha values were cleared; RGB artwork,
dimensions, launcher background, and Compose layout were left unchanged.
Appearance now offers Light and Dark only. New or missing preferences default
to Dark, stored `SYSTEM` values migrate to Dark, explicit Light/Dark values
remain intact, and language preferences are unchanged.

Verification passed: 118 host tests, VNext APK and Android-test APK assembly,
VNext lint (0 errors, 26 warnings), and six focused Android tests on the API 35
SwiftShader AVD. Manual fresh-install checks showed Dark selected by default,
only Light/Dark controls, and Light persisted after force-stop/relaunch;
English remained selected. Filtered runtime-error logs were empty. Screenshots
are saved in the session artifacts under `files/ui-refinements/`.

After the connected test run, the emulator no longer reported the VNext package
installed. No explicit ADB uninstall or data-clear was issued; the APK was
installed again for manual checks, so the prior on-device preference was
unavailable.
Legacy `SYSTEM` migration is verified with isolated synthetic preferences in
host and Android UI tests, not against the previously installed emulator app.
The full instrumentation suite was not run. Release status remains
**NOT Ready for Release**.

## Live GitHub acceptance — 2026-11-14

On the existing API 35 `CI_Android_35_Repro` AVD (`emulator-5558`), the latest
VNext APK built and installed with `adb install -r`; no app data was cleared.
Normal GitHub OAuth completed in the emulator and VNext showed the account as
verified (`karim-kmorsi`). After force-stop and relaunch, the session remained
verified and the local project list still showed zero projects.

The authenticated repository browser successfully loaded and refreshed live
GitHub results, but returned only `karim-kmorsi/AIAndroidApp` and
`karim-kmorsi/ZOOM-CCTV-Manager`. `karim-kmorsi/AI-Project-Manager` remained
absent after the owner reported granting installation access and after further
refreshes. An unauthenticated public API request returned 404, which does not
distinguish a private repository from an unavailable name. No other repository
was imported, no local project data was changed, and no remote write was made.
Target preview/import, duplicate verification on that project, project
persistence, repository refresh, Actions, Issues, and change-baseline live
checks remain blocked until the VNext GitHub App installation actually exposes
the target in its repository list.

VNext Settings displayed Arabic RTL correctly; English was restored and showed
English LTR labels. Repository-browser navigation, scrolling, and dismissal
worked. The app launched successfully after relaunch; filtered emulator logs
showed no matching VNext/System UI crashes or EGL errors. No product defect
was confirmed and no app source was changed. The full instrumentation suite was
not run. The latest APK is at `app/build/outputs/apk/vnext/app-vnext.apk`.
Release status remains **NOT Ready for Release**.

## GitHub integration refresh and emulator verification — 2026-11-13

Linked projects now refresh by stable GitHub repository ID, so a remote rename
does not break the association or rewrite local project fields. Manual refresh
and a 15-minute throttled refresh when opening the linked project read the
default branch and latest commit, repository metadata, latest Actions run,
and open/closed Issue counts and recent issue details (excluding pull requests).
The overview separates those remote signals from locally marked task progress
and does not calculate an overall completion percentage.

Successful refreshes are stored in a versioned SharedPreferences sidecar,
separate from project JSON. A refresh becomes the new baseline only when all
requested sections and any required commit comparison are complete. Invalid,
partial, stale, or failed writes leave the previous successful snapshot intact.
The overview reports localized status, errors, last-success time, and changes
since the prior successful refresh in English and Arabic. No authentication,
credential, project JSON, signing, or application-ID configuration changed.

Final checks passed: Debug and VNext APK assembly, 115 host unit tests, VNext
Android-test APK assembly, and Debug/VNext Lint (0 errors, 26 existing
warnings). The project configures `testBuildType` as `vnext`, so
`:app:assembleDebugAndroidTest` is not a task; the matching
`:app:assembleVnextAndroidTest` task was used. Four focused mocked API, storage,
repository-browser/import, and overview test classes passed on API 35 (14 tests).
The installed VNext app opened to its dashboard and filtered logs showed no
app/System UI crashes.

The host-GPU emulator process later exited with code 139 after repeated
`eglMakeCurrent` failures. The same AVD was restarted without wiping data using
SwiftShader software rendering; the focused tests passed again and the AVD
remained responsive. No physical device was targeted. Live GitHub
authentication, repository browsing/import against GitHub, and an end-to-end
local project import/restart on the emulator were not performed; those require
available authorization and remain acceptance work. Mocked API/UI results are
not live GitHub evidence. The VNext APK is at
`app/build/outputs/apk/vnext/app-vnext.apk`. Release acceptance remains open.
**NOT Ready for Release**.

## GitHub repository import — 2026-11-13

The existing GitHub repository preview now offers an explicit Import project
action and a confirmation showing the proposed local record. Confirmation
rechecks repository-ID, project-name, and canonical GitHub URL duplicates
against the current project list. Any possible duplicate blocks import; no
existing project is overwritten or merged. A new project is appended through
`persistProjectChange()`, then selected and opened. Existing JSON storage and
authentication remain unchanged; synchronization is still not implemented.
Import labels, confirmation, and feedback are available in English and Arabic.

Debug assembly, all 112 host unit tests, VNext Android test APK assembly, and
Debug Lint passed (0 errors, 26 warnings). The focused GitHub preparation host
tests passed (5 tests). The new confirmation UI regression compiled but was not
run: no emulator or instrumentation tests were used. The VNext compile-only
build used a placeholder Gradle property to satisfy the existing build guard;
no APK was installed and no authentication credentials were changed. Release
acceptance remains open. **NOT Ready for Release**.

## UI/UX VNext continuation — 2026-11-12

Continued from `e7f198f` on `feature/github-snapshot-preview`. Overview now presents saved
description, manually recorded task progress, pending tasks, GitHub account connection state,
and actual task/document presence. Blueprint/Roadmap editor recovery/status chrome and text
editor use VNext panels with informational Markdown/line/word counts. Tasks adds its existing
manual progress and status summary to the header. Settings export and read-only backup preview
actions were restyled. Existing GitHub cards/dialogs and backup/recovery flows were retained.
No project data model, storage, GitHub authentication, signing, or logo/launcher assets changed.

Focused `:app:compileDebugKotlin` passed twice with JDK 17. No tests, screenshots, connected
Android UI tests, or device operations were run per task scope. The approved visual references
were inspected, but final screen-by-screen layout comparison remains deferred. No commit or push.
See [continuation notes](UI_UX_VNEXT_CONTINUATION.md) for the next action. **NOT Ready for Release**.

## UI/UX VNext Package 01B — 2026-11-12

Focused local Dashboard refinement atop the intentionally uncommitted Package 01 baseline. Reduced header/search/card spacing, shortened manual-verification labels and docked Create in a measured Scaffold bottom area so no list content is covered. Removed only the Dashboard's separate launcher-background layer; existing transparent foreground and all 14 launcher assets remain unchanged. No JSON/model/storage, editor, GitHub, signing or version changes.

Configured Debug/test APK builds and Lint passed (0 errors, 13 warnings); 101 JVM tests and 91 scoped Android tests passed (6 focused + 85 existing, zero failures/skips in final runs). Owner explicitly approved disposable-emulator-only installation. Real screenshots cover Arabic/English Dark, Arabic Light and 200% font cases. Phone data/installation remain untouched. Light-mode white logo details and OEM/TalkBack/device behavior still need owner review.

See [Package 01B report](UI_UX_VNEXT_PACKAGE_01B_REPORT.md) for exact commands, incremental file inventory, genuine screenshots, APK identity and device checklist. No commits or pushes. **NOT Ready for Release**.

## UI/UX VNext Package 01 — 2026-11-12

Implemented locally on `feature/github-snapshot-preview`, starting at `f77c66d1ed6681920ad1747b1327dc5e17dba782`; no commit or push. Only shared visual foundation and Dashboard were redesigned. Existing logo/launcher assets, JSON/storage, editor behavior, GitHub logic, signing and version configuration remain unchanged. Project-status filters are deferred; displayed progress describes manually marked task statuses.

Configured Debug/test APK assembly and Lint passed (0 errors, 13 warnings); 101 JVM tests passed. The scoped Android batch produced 86 passes and five screenshot-fixture failures; after test-only lazy-list positioning corrections the six Dashboard tests passed, yielding 91 distinct scoped Android tests with passing results across the runs. This is not a full Android-suite or phone acceptance claim. Tests used a new disposable API 35 emulator and synthetic files. No physical-device install or data operations.

See [the complete package report](UI_UX_VNEXT_PACKAGE_01_REPORT.md) for exact commands, all changed files, development failures, evidence, shared-theme effects, APK signature and safe owner device-acceptance instructions. **NOT Ready for Release**.

## GitHub authentication build configuration

Hosted run 42745902995 published an APK with an empty GitHub App Client ID.
The installed REDMAGIC APK matched that artifact. The Connect button was
disabled by the existing configuration check.

The workflow now expects the public Actions repository variable
`APP_CLIENT_ID` and maps it to Gradle for assembly and instrumentation.
The CI guard rejects missing configuration. Unconfigured local builds remain
supported. The repository variable is set and verified. GitHub reserves the
`GITHUB_` variable prefix.

The required build rejected an empty ID. The unconfigured local build and
the configured debug and test APK builds succeeded. The configured APK
contains the previously verified public ID. All 18 focused host tests passed.
Lint reported 0 errors and 20 warnings. The Connect UI regression compiled.
The previous phone package is absent. The phone acceptance check uses only
the verified APK from a successful hosted run. Live authorization requires
separate owner approval.

## Tasks empty-state correction

Tasks now distinguishes a project with no tasks from a search or filter with
no matches. Both messages have English and Arabic resources. The existing
Add action and project format are unchanged.

Both debug APKs assembled. The 12 focused host tests passed. Four targeted
Android tests passed in English and again in Arabic on a disposable API 35
emulator. They covered both empty states, adding and saving the first task,
and search/filter behavior. Debug lint reported 0 errors and 20 warnings.

## Focused Settings polish — 2026-11-10

The dashboard Settings action has a labeled gear. Settings groups Connections,
Appearance, Data and backup, and Advanced. GitHub sign-in stays under
Connections. The public API probe is under Advanced in Diagnostics. Routine
tips and populated-project next steps were removed. Backup, manual verification,
read-only, and recovery messages remain.

The debug and instrumentation APKs built. All 101 host tests passed, including
23 GitHub tests. Debug lint found 0 errors and 20 warnings. A disposable API 35
emulator passed 37 targeted tests at the standard viewport and 9 at 320×640
mdpi with Arabic locale and 1.8× font scale. A compact UI check in Arabic and
English confirmed the Settings control mirrored correctly, measured at least
48 dp in both dimensions, and opened Settings and returned with Back. The
Settings tests checked fixture project bytes, and the installed app's project
file stayed absent. GitHub sign-in tests used synthetic responses. Hosted
verification is tracked in GitHub Actions.

## Hosted CI repair and GitHub acceptance — 2026-11-10

[Hosted run 42677974723](https://github.com/karim-kmorsi/AI-Project-Manager/actions/runs/42677974723)
reported missing `VERIFIED` and `Layout project` nodes. Its logs showed a
320×640 mdpi viewport. An isolated local API 35 emulator reproduced both
failures at that viewport with animations disabled. The tests now scroll
the lazy lists to the project and task controls before tapping, and check the
stored status and the visible status after each action. They retain the
progress, storage preservation, and layout assertions. Android Actions
uploads test reports even when instrumentation fails.

Repository discovery has a manual refresh. A successful refresh restarts
pagination and replaces the previous page set. An interrupted refresh retains
the existing rows and offers retry. GitHub's cooldown still applies. Synthetic
snapshot tests cover a changed head, missing repositories, denied access, and
an interrupted branch read. The [import and synchronization proposal](GITHUB_IMPORT_SYNC_SAFETY.md)
records the decisions required before any write path.

Local checks passed 23 focused GitHub host tests, 114 total host tests, and
35 targeted Android tests on the 320×640 API 35 emulator, including both
formerly failing cases. Both debug APKs built. Debug Lint reported 0 errors
and 20 warnings. No project file was created. GitHub Actions is the source
for hosted results. Live discovery and previews still require browser approval
on the owner emulator.

## GitHub integration Phases 2D and 2E — read-only preparation

Import preview proposes a project ID based on GitHub's repository ID, the
repository name, and a description with the canonical URL. It flags existing
IDs, matching names, and matching URL lines as possible duplicates. Blueprint,
roadmap, and task fields start empty in the proposal. The preview has no project
write path. Owner review of mapping and preservation rules is required before
enabling an import.

Manual comparison refreshes repository metadata and the current default-branch
commit. It compares that remote snapshot and the local project against the
baseline held while the preview is open. It reports metadata, branch, and commit
changes, possible conflicts, and projects without a confirmed ID link. A name
or URL match alone does not establish a link. The baseline stays in memory.
Background sync, persistent bindings, and conflict application remain deferred.

Final local verification passed 22 focused GitHub host tests, all 113 host tests,
and 27 targeted API 35 instrumentation tests. The Android cases covered paging,
rate limits, preview limits, Arabic resources and RTL layout, and synthetic
import and comparison with unchanged project bytes. Both APKs assembled and
Debug Lint reported 0 errors and 20 warnings. The emulator's project file
remained absent. Live authenticated discovery and previews require a fresh
owner-approved session.

The Android workflow now triggers on pushes to this feature branch. The first
run built both APKs and reported two failures in existing task UI tests during
the broader connected suite. Local GitHub checks passed. See the GitHub Actions
run for the status of each pushed revision.

## GitHub integration Phase 2C — repository preview

From discovery, open a read-only repository preview with the default branch,
protection status, latest commit metadata, plain-text README, and up to 30 root
entries. The README request is pinned to the displayed commit and the tree to
its tree SHA. The app bounds the README download to 64 KiB and marks partial
content. Missing files, access errors, and empty repositories have explicit
states. The app neither clones repositories nor writes to GitHub.

Both debug APKs assembled. Four targeted API 35 tests passed across preview API
behavior and discovery UI. The tests used synthetic responses. The emulator's
project file remained absent.

## GitHub integration Phase 2B — repository discovery

Settings offers an authenticated repository browser. It pages through GitHub App
installations and their accessible repositories, deduplicates by repository ID,
and filters loaded entries by search and visibility. It displays repository
name, description, default branch, visibility, and archive status. The screen
uses English and Arabic resources and the existing Compose theme.

The app registration shows read-only Metadata, Contents, Issues, and Pull
requests permissions. Discovery uses the intersection of the signed-in user's
access and the app installation. It handles rate limits with a manual retry
after the cooldown and clears the session on HTTP 401. The user token stays in
memory.

Verified with 109 host tests and 12 targeted Android tests on API 35, including
synthetic authenticated pages, private visibility, rate limits, and Settings UI.
Both debug APKs assembled. Live discovery with the owner's GitHub authorization
remains pending. The emulator's project file remained absent.

## GitHub integration Phase 2A — final audit, 2026-11-10

Audited on `feature/github-snapshot-preview` against
`ba392b0d2a400f475f17a62ddf7e3451e89e1b68`. Settings starts GitHub App device
authorization, opens GitHub in the browser, and verifies the account with
authenticated `GET /user` before showing the login. The existing unauthenticated
public API check remains available.

Supply the GitHub App Client ID through the Gradle property `githubAppClientId`,
for example through `ORG_GRADLE_PROJECT_githubAppClientId`. A build without the
property disables sign-in. The Client ID is a public identifier and the build
places it in the APK. Tokens are held in memory, and refresh tokens are discarded.
Leaving Settings cancels an active attempt. Recreating the activity or restarting
the process clears its local session. Disconnect clears the local session.
GitHub authorization is managed separately in GitHub account settings.

### DNS diagnosis and mitigation

The API 35 emulator received HTTP 200 for the device challenge and four pending
token polls. A subsequent poll failed to resolve `github.com` before sending the
POST while Chrome was in the foreground. The first repair allowed two retries
for pre-send DNS failures, doubling the polling interval each time. A second
browser handoff exhausted that retry budget. The emulator shell resolved GitHub
after the app returned. The cause of the resolver failure remains unconfirmed.

Polling and account verification now wait for the activity to resume. Polling
respects GitHub's interval and slow-down responses, the original device-code
deadline, and cancellation. The two bounded retries apply to pre-send DNS
failures during token polling. Stage-specific errors omit credentials and raw
exception details. The final APK excludes temporary diagnostic logging.

### Verification and acceptance

The prior live run on `AI_PM_Play_API35` verified browser approval, successful
authenticated `GET /user`, the `karim-kmorsi` account display, and disconnect.
Reconnection obtained a new challenge and was cancelled before a second browser
approval. The final audit used synthetic Android tests and relied on that prior
run for live GitHub evidence.

The audit reran the focused GitHub host suite (18 passed) and complete host suite
(109 passed, including those 18). Debug and Android test APK packaging and
assembly succeeded with JDK 17, Gradle 8.13, and the verified Client ID. Debug
Lint reported 0 errors and 20 warnings. All 16 targeted tests passed on
`AI_PM_Play_API35` (API 35), covering GitHub sign-in UI, HTTPS transport, the public
API check, and Settings integration. In-place installation and tests left
`projects.json` absent on this emulator.

Phase 2A is ready for owner acceptance on the emulator. A second end-to-end
authorization and physical-device validation remain outstanding.
**NOT Ready for Release**.

### Phase 2B prerequisites

Owner acceptance of Phase 2A and separate approval of the Phase 2B scope come
first. Confirm the GitHub operations and minimum permissions required, decide
how to handle token expiry and reconnection, and agree on verification and data
preservation requirements before implementation.

## GitHub integration phase 1

Settings now runs an unauthenticated HTTPS metadata request for the public
`octocat/Hello-World` repository on demand. It reports request outcomes and
discloses that private repository access is unavailable. No credentials are
requested or stored.

Debug and Android test APK assembly and Debug Lint passed. Six targeted tests
passed on the API 37 emulator. The manual API check returned
`octocat/Hello-World · master`, and the bundled Backlog preview opened.
The saved projects file had the same SHA-256 before installation and after
testing. **NOT Ready for Release**.

## Read-only Backlog snapshot proof of concept

The feature branch previews the bundled snapshot from publication commit
`90826e09f62b61ec6f881dd6271fe17623a1e797` in Settings. The app has no existing
GitHub authentication, so private fetching remains blocked. The preview never
writes to project storage. The parser compiled against API 35 and passed the
published fixture plus six rejection cases on the host using the installed
Android JSON reader source. Compose compilation and Android instrumentation
remain unverified because the local Gradle toolchain is unavailable.
**NOT Ready for Release**.

## Saved-state completion attempt — 2026-11-07 (infrastructure blocked)

Starting HEAD: `08dccaaef8fd58d3439ae11f34d28a67506e49fe`, clean on the expected branch
and GitHub origin. Required documents and previous run evidence were reviewed. The separate
read-only diagnosis is recorded in the owner conversation; no standalone diagnostic report was
created. Evidence: `/home/karim/.codex/ai-pm-state-completion-2ktn49hf/`.

Added only instrumentation phase logging for editor readiness, pending input, state capture,
recreation and restoration assertions, including the accumulated-context case. The existing
readiness correction and every safety assertion remain intact. Prepared an external single-test
runner that records wall-clock timestamps and treats a nonzero instrumentation exit or missing
result as an abort/error, rather than interpreting zero cases as success. Its abort path has not
been exercised by instrumentation in this attempt. Minimum offline `:app:assembleDebugAndroidTest`
passed (1 s). No production changes, full host-suite, full build or Lint repetition.

Preflight: 6.4 GiB available RAM, 2.5 GiB free swap, 359 GiB available disk and zero memory-pressure
averages; no connected Android devices/emulators. Used the installed API 35 Google Play x86_64
revision 9 image, with no downloads. The unique `AI_PM_State_Completion_2ktn49hf` AVD used dedicated
AVD/user homes, 2,560 MiB guest RAM, 1080×1920 / 480 dpi, no snapshots, headless graphics auto and
serial `emulator-5584`. Kernel console output, continuous logcat and host-resource samples were
retained. Recorded available RAM during collection stayed at least 2.84 GiB.

After identity/API/boot completion and package absence checks, the candidate app APK certificate
verified locally. The first explicitly targeted installation returned exit 1, `adb: device offline`.
No installation retry or instrumentation execution occurred: the owner's transport-loss stop rule
was applied. A later diagnostic package query remained empty. Initial and diagnostic boot IDs
match; kernel output shows startup adbd service restarts, not a demonstrated guest reboot or kernel
fault. These events suggest startup transport instability, but do not conclusively identify the
failed installation's cause. This is distinct from the previous run's guest kernel-fault evidence.
No graphics, kernel or host configuration adjustment was made to work around it.

All six requested checks are **BLOCKED, not executed** (durations unavailable):

1. `TaskContextRestorationUiTest.accumulatedProjectContextsMeasureActivityBundleAndRestoreIndependently`.
2. `SavedStatePayloadUiTest.largePendingDocumentActivityBundle`.
3. `ProjectMetadataUiTest.transientFailureKeepsMetadataAndRetrySucceeds`.
4. `ProjectMetadataUiTest.conflictReloadRequiresConfirmationAndAllowsEditAgainstNewBase`.
5. `TaskTitleUiTest.failedSaveRetainsInputAndAllowsRetryWithoutChangingStatus`.
6. `TaskTitleUiTest.conflictReloadRetainsInputUntilConfirmedAndPreservesNewStatus`.

Machine-readable blocked-test records and an infrastructure-error XML report distinguish this
from PASS or SKIP. No new Bundle measurements were obtained. The six prior passes were not rerun
or invalidated. Newly added phase logging is compiled only; pending-document and accumulated-
context full Activity measurements, stale-conflict and failed-save execution remain unfinished.
No production defect was established and no repair/retest attempt was made after the stop trigger.

Cleanup stopped only the verified owned emulator/collectors and removed its dedicated AVD/user
homes. Protected existing AVD file inventories (size/mtime and small configuration hashes) match
before/after; evidence remains outside the repository. No physical-device, existing-AVD, real-data,
process-death, import/restore/merge, main or push operations occurred. TalkBack remains deferred.
Recommended next step: separately authorize a bounded disposable-emulator startup/ADB stability
check, with a sustained-ready gate before installation; retain current settings until evidence
justifies a change. Do not repeat the six previously successful tests. All release gates remain.
**NOT Ready for Release**.

## Saved-state Android verification — 2026-11-07 (incomplete)

Starting HEAD: `49ada6e0f49e67d7a42a83da720e815e6ca68e0b`, verified clean on the expected
branch with the expected GitHub origin. All required current documents were present. Evidence:
`/home/karim/.codex/ai-pm-state-android-vu8v_ck_/` (initial-seven XML/JSON/logs, interrupted
retry, APK hashes/certificates, payload logcat, kernel/boot diagnostics, screenshots and cleanup).

Preflight: 6.6 GiB available RAM, 2.5 GiB free of 4 GiB swap, 359 GiB available disk; memory
pressure averages were zero. No Android device/emulator was connected. Installed API 35 Google
Play x86_64 revision 9 and emulator 37.2.12 were used without downloads. A dedicated temporary
AVD/home, `AI_PM_State_vu8v_ck_` / `emulator-5588`, used 2,560 MiB guest RAM, 1080×1920 at
480 dpi, no snapshots and headless graphics auto. Identity/API/boot and fresh package absence
were checked before installing the existing matching-signed Debug/app-test APKs. The six source
hashes match the previous successful build evidence; this is provenance evidence, not a new build.

**Initial seven Android regressions:** 6 passed, 1 failed, none skipped; runner wall time 25.043 s.

| Class / method | Result | Seconds |
| --- | --- | ---: |
| SavedStatePayloadUiTest.largeTaskTitleActivityBundle | PASS | 5.378 |
| SavedStatePayloadUiTest.parcelSlicesCharacterizeBaselineAndPendingInputSeparately | PASS | 1.149 |
| SavedStatePayloadUiTest.largeMetadataActivityBundleWithPendingDescription | PASS | 5.861 |
| SavedStatePayloadUiTest.smallMetadataActivityBundle | PASS | 1.506 |
| SavedStatePayloadUiTest.largePendingDocumentActivityBundle | FAIL; retry blocked | 1.345 |
| TaskContextRestorationUiTest.queryFilterAndScrollSurviveActivityRecreationAndMenuRestoresClosed | PASS | 2.905 |
| TaskContextRestorationUiTest.tabRoundTripAndProjectSwitchesRetainIndependentContextsWithoutWrites | PASS | 4.793 |

The document test paused the Compose clock before the editor field appeared, failing node lookup
before input. One test-only repair adds the existing-pattern original-content assertion before
freezing time; all original safety assertions remain. A new focused test measures a full Activity
Bundle after two project task contexts and checks both contexts after recreation. Minimum offline
`:app:assembleDebugAndroidTest` passed (1 s); no production code, full host suite or Lint rerun.
Both test changes are **compiled, not runtime accepted**.

The affected-test retry plus the new measurement and four targeted metadata/title conflict/failure
checks was interrupted in its first case after 12.300 s, with zero completed results. The guest
rebooted without a requested restart. Retained Android DropBox SYSTEM_LAST_KMSG includes kernel
fault stacks (`_raw_spin_lock`, memory-fault paths) and CPU shutdown; new boot/uptime records
corroborate reboot. No system_server_crash entry was found. This establishes infrastructure failure,
not its trigger or an application defect. Further execution stopped; no second repair was tried.
The retry XML's zero cases is **incomplete**, not a passing suite. The new context measurement and
four stale-conflict/failed-save checks remain blocked, not skipped or passed.

**Actual Android measurements:** `Parcel.writeBundle` / `dataSize`, in bytes:

| Measured scope / fixture | Legacy slice | Compact slice | Compact slice with pending input |
| --- | ---: | ---: | ---: |
| Slice: each document 64 UTF-16 units, 3 tasks, pending 32 units | 848 | 408 | 472 |
| Slice: each document 262,144 units, 2,000 tasks, pending 32 units | 1,208,160 | 408 | 472 |
| Slice: each document 262,144 units, 2,000 tasks, pending 131,072 units | 1,208,160 | 408 | 262,552 |

| Full Activity saved-instance-state Bundle | Bytes |
| --- | ---: |
| Metadata: 64 units/document, 3 tasks, 32 pending units | 4,304 |
| Metadata: 262,144 units/document, 200 tasks, 131,072 pending units | 266,384 |
| Task title: 262,144 units/document, 200 tasks, 32 pending units | 5,248 |

Slices include baseline/pending strings, project/task identifiers and a confirmation marker, not
Compose/window registries. Full Activity Bundles use the real onSaveInstanceState callback and
include its registered state, but not app files, recovery-draft contents or a complete Binder
transaction. Full Activity fixtures contain **200**, not 2,000 tasks; the 2,000-task case was only
a slice. Large pending-document and accumulated-context Activity sizes were not obtained. These
are Android bytes, distinct from earlier Java serialization figures. No Binder failure threshold
or process-death safety is established; no deliberate failure stress/process death was requested.

Passing cases confirm pending metadata/title restoration, exact task query/filter/scroll retention,
closed restored menus, independent contexts across tabs/projects and byte-identical synthetic
project JSON. Legacy baseline compatibility remains supported by the existing passing host XML
and source review, not a new Android legacy-restoration execution. Current-run stale-write and
failed-save acceptance remains unverified; historical tests are not new passes. No import/restore/
merge, TalkBack, physical-device, existing-AVD, main or push operations occurred. No real user data
was accessed. The unexpected disposable-guest reboot is not controlled process-death acceptance.

Cleanup stopped only the verified owned emulator and removed only its dedicated AVD/home data;
evidence was retained. Protected AVD file sizes/mtimes and small configuration hashes match before
and after. No unrelated processes were terminated. Remaining work: diagnose the guest kernel fault
in a separately approved stable environment, then run only unfinished cases. Do not repeat the six
successful cases without a concrete reason. Raw pending text and many retained contexts still risk
large saved state. TalkBack stays owner-deferred; all other open release gates remain open.
**NOT Ready for Release**.

## Saved-state reliability development batch — 2026-11-07

Starting HEAD: `75b8de7ab481dcd00565d4b231fda6c491fae871`, verified clean on the continuing
branch. Implementation/source-and-test ending commit:
`e3d9d7263050d9193c52eb42e010baa525f76fe9`. This documentation closure follows that commit;
its own final Git SHA is recorded in the batch report. All six required documents were present.

**Task A — measured characterization:** `SavedStatePayloadTest` uses JDK 17 ObjectOutputStream
to serialize the actual Saver's ArrayList and a deliberately limited form-state model. These
are actual **Java serialization bytes**, not Android Bundle/Parcel sizes, memory usage or Binder
limits. Baseline measurements ran before compaction. Reproducible fixtures use 64 or 262,144
UTF-16 units in each document; 3 or 2,000 tasks have deterministic IDs, titles with 64-character
suffixes and alternating FUTURE/VERIFIED statuses. Pending strings have 32 or 131,072 units.

| Scenario | Units per document / tasks / pending units | Legacy baseline Java bytes | Compact baseline Java bytes | Current modeled form Java bytes |
| --- | --- | ---: | ---: | ---: |
| Small | 64 / 3 / 32 | 516 | 143 | 286 |
| Large | 262,144 / 2,000 / 32 | 797,567 | 143 | 286 |
| Large pending input | 262,144 / 2,000 / 131,072 | 797,567 | 143 | 150,052 |

The old large baseline contains 6,006 strings / 1,404,176 logical raw UTF-16 bytes. This measured,
avoidable duplication justified Task B. Java serialization uses reference sharing and modified
UTF encoding; its numbers are **not** an Android byte-budget estimate. The form model excludes
Compose registries/window envelopes and other screens. No Binder failure or crash was reproduced.
New Android tests would measure real Parcel slices and full Activity saved-state Bundles for
small/large metadata, task-title and pending-document scenarios, followed by recreation and
unchanged-data checks. They were **compiled only**, so no Android size results are claimed.

**Task B — compact conflict baseline:** immutable EditorSnapshot now retains a SHA-256 token.
Canonical input includes the same project fields, IDs, document text, task count, ordered task
IDs/titles/statuses, field counts and lengths. Exact UTF-16 code units preserve field boundaries,
empty strings, Unicode distinctions and unpaired surrogates. Model fields remain non-null;
malformed restored null values fail closed. The ArrayList<String> Saver container is retained;
previous full-field lists restore to the equivalent fingerprint. Unsupported/corrupt tokens stay
invalid rather than silently rebasing pending input. Confirmed reload is still the recovery path.
Fingerprint equality has a nonzero SHA-256 collision risk, unlike literal list equality; this
tradeoff is explicit, and the token is not authentication. No text is normalized, truncated or
written to project JSON. Existing storage CAS, reload/discard and failed-save callbacks remain.

**Task C — per-project task context:** a project-ID-keyed Compose SaveableStateHolder owns the
task destination across temporary tab/project switches; query/status are saveable and the list
uses explicit rememberLazyListState. Each visited project retains its own context across saved-
state restoration. The dropdown uses ordinary remember and restores closed. Existing saveable
task-entry/title-form state also remains within this owner. Search/filter logic, order, statuses,
labels, styling and project data are unchanged. Runtime acceptance remains pending.

Verification actually executed, offline with installed JDK 17 / Gradle 8.13:

- Pre-change characterization: 1 host test passed. Focused snapshot/measurement/search/filter
  checks passed; Android test source compilation passed. No failures or repair retries occurred.
- Final full invocation ran once after the final source change: `:app:assembleDebug
  :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug` — **BUILD SUCCESSFUL**,
  14 s reported. **91 host tests passed**, 18 classes, zero failures/errors/skips. Debug and test
  APKs assembled. Lint: **0 errors, 12 existing warnings**, without suppressions.
- Four additional snapshot host tests cover a pinned canonical digest, bounded saver size,
  exact Unicode/boundaries, invalid-state rejection and legacy marker interpretation; all
  existing snapshot assertions remain. The characterization test records the measured sizes.
- **Seven new Android tests compiled, none executed:** five payload/Activity measurement cases
  and two task-context cases covering recreation, tab return, project isolation, exact query/
  filter retention, scroll restoration, closed popups and unchanged synthetic project bytes.
  Existing metadata/title stale-save and failed-save UI regressions compiled; they were not rerun.

Evidence: `/home/karim/.codex/ai-pm-saved-state-i6o5yp64/` (before/after measurement XML/logs,
compaction decision, final host XML, Lint, source invariants and reviewed diff). Full diff reviewed.
ProjectStorage/persistProjectChange and the document editor are byte-identical to the starting
implementation. No project schema/migration, signing, Application ID, dependency, release-config,
launcher, backup-policy or approved styling changes. No real user data was accessed or changed.
No emulator/AVD/ADB, instrumentation execution, actual process death, device installation,
physical-device, main or push operations occurred.

Remaining risks: pending descriptions/documents/new-task text and retained contexts for many
visited projects can still grow total saved state; compaction does not bound the whole Activity
Bundle. Fingerprint computation remains proportional to compared content. Real Android sizes,
runtime restoration and platform compatibility await focused disposable-data verification.
TalkBack remains owner-deferred; process death, backup/transfer and other release gates stay open.
**NOT Ready for Release**.

## Caret-visibility regression coverage and interrupted-batch closure — 2026-11-07

Continuing baseline: `1e792af4d85ffae3b49a5f7ae75213553d9dc7f1`. The interrupted batch left
only the new `CaretVisibilityUiTest.kt` untracked; branch and HEAD still matched on resumption.
Existing instructions, status, roadmap and release gate were present. The owner explicitly
deferred TalkBack acceptance; no TalkBack testing was resumed.

Four new Android regressions exercise the existing Blueprint/Roadmap implementation in LTR/RTL,
both themes, actual 2.0x system fonts and real keyboard show/hide/show transitions. Each checks
beginning/middle/end cursor positions and movement after internal document scrolling, retained
selection/focus, visible Save/heading, unchanged text and byte-identical synthetic project data.
They use the rendered cursor-color vertical run inside the ancestor-clipped field intersected
with the visible Android window frame. The decor-view transform maps window to screenshot
coordinates; geometry is refreshed during bounded Compose-clock blink sampling. Text-layout
cursor height supplies the expected height only, not proof of visibility. One physical pixel
of height tolerance and small RGB tolerance account for rasterization; field outlines are
excluded. This is a rendered-caret observable for the approved palettes, not a direct OS
insertion-marker API or proof for every keyboard, window size or accessibility overlay.

Initial test-authoring failures are retained in the evidence: a missing experimental-test API
opt-in caused compilation failure; coordinate-origin mismatch and stale geometry during focus
scrolling produced false caret failures. Those were corrected in the test harness. Another
gesture scrolled into the separate guidance region; the internal-scroll scenario was corrected
to start within the document before moving the cursor. Failure diagnostics also needed to handle
multiple Compose roots. No confirmed production defect or production repair was required.

Completed evidence reviewed on resumption:

- Four distinct caret tests passed: Blueprint LTR 18.179 s / RTL 17.086 s, Roadmap LTR 18.732 s /
  RTL 16.775 s. The eight existing `AccessibilityLayoutUiTest` cases also passed. These were two
  focused runs (1 + 11 tests), not a full instrumentation suite.
- The final offline Debug/host/test-APK/Lint invocation succeeded in 8 s. The host task was
  **UP-TO-DATE**: retained XML records **86 passing host tests**, 17 classes, no failures/errors/
  skips; this is reused evidence, not 86 freshly executed tests. Lint: **0 errors, 12 warnings**.
- Closure review found that the actual-font prerequisite would fail ordinary normal-font CI runs.
  Added an API-35 minimum test filter and a JUnit assumption for the dedicated 2x environment;
  all caret and data-safety assertions remain. This is a test-only prerequisite correction.
  Only affected test-APK assembly and Lint were repeated: successful, 0 errors/12 warnings.
  Then **all four caret cases passed again** (74.822 s combined wall time), and a normal-font
  run produced **four expected assumption skips**, zero failures (4.624 s wall time). The prior
  eight passing regressions and full host suite were not repeated. Production APK hash is unchanged.

Evidence: `/home/karim/.codex/ai-pm-caret-talkback-zqxw2u1h/`, including `caret-ltr-pass/`,
`final-focused/`, `closure-2x/`, `closure-normal-font/`, archived host XML, build/Lint logs,
artifact signatures/hashes, initial failure diagnostics and cleanup verification.

The single owned API 35 AVD `AI_PM_Caret_TalkBack_zqxw2u1h` / `emulator-5588` remained running
after interruption. Its identity and process were verified; TalkBack was disabled for cleanup,
the owned emulator stopped, and only its dedicated AVD/Android-home directories removed.
Protected existing AVD inventory is unchanged. Synthetic fixtures and prior limited programmatic
node-observation evidence remain outside the repository; those observations do not establish
TalkBack gesture traversal or spoken-output acceptance. **TalkBack remains deferred by owner**.

No production, persistence, Save/draft/navigation, schema, signing, Application ID, dependency,
theme, launcher or release-configuration changes. No physical-device access, main changes or push.
Other keyboards/devices/window constraints, TalkBack, process death, backup/transfer and remaining
release gates are still open. **NOT Ready for Release**.

## Editor accessibility recovery — 2026-11-06

Starting HEAD: `f588fe5bf8d9f17984b2586efce891e883567e53`, verified clean on the continuing
branch. This section supersedes the earlier pending editor-readability and inset-test outcomes
only for the scenarios verified below; broader release acceptance remains outstanding.

Baseline evidence was reviewed from `/home/karim/.codex/ai-pm-a11y-final-y9s6a7zr/` and reproduced
before editing. With actual 2x fonts and the keyboard visible, the old input had only 109px of
visible height on the disposable 1080x1920 / 480 DPI phone: its oversized measured minimum was
clipped by the remaining viewport, so internal scrolling could not reliably expose the caret.
Save and heading geometry already worked. Of the two previously failing inset tests, this run's
baseline LTR case passed and RTL failed the same heading assertion. Window evidence shows that
their ComponentActivity host used `adjustPan`; a late real keyboard moved the host in addition
to the fixture's injected IME/status insets. This was a test-host defect, not evidence for changing
the already-correct production heading or resize policy.

One repair attempt per defect was used (maximum two authorized); no second repair was needed:

- `ScrollableDocumentBody` retains normal spacing and Save above the editor when space permits.
  It first reclaims vertical padding, then places the existing Save action beside the input only
  when the remaining height cannot fit one themed text line, label and field padding. Relative
  placement mirrors RTL. The field is capped to its actual visible viewport so internal scrolling
  can expose the active line/caret; its composition and state ownership remain stable. Guidance,
  recovery feedback and document hints remain scrollable, with no essential content removed.
- The synthetic inset fixtures explicitly disable native host adjustment, preventing a real IME
  from applying a second pan/resize. Existing assertions remain; new assertions check readable
  field height, IME separation and Save/input non-overlap. MainActivity's native resize policy
  is unchanged and was verified separately with the actual keyboard.
- Five host sizing regressions cover normal spacing, compact action placement, padding reclamation,
  insufficient width and overflow safety. Existing viewport expectations now reject externally
  oversized fields. No font-scale breakpoint, device-specific offset or dependency was added.

Verification actually executed, offline using installed JDK 17 / Gradle 8.13:

- Focused host checks: **14 passed**. Debug and Android test APK assembly passed.
- All eight `AccessibilityLayoutUiTest` cases: **8 passed, 0 failed/skipped**, 17.341 s, including
  both corrected heading/Save cases, existing toolbar/creation cases and selection/text assertions.
- Final full invocation ran once after the last source change: `:app:assembleDebug
  :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug` — **BUILD SUCCESSFUL**,
  8.489 s measured; **86 host tests passed**, 17 classes, zero failures/errors/skips. All Android
  tests compiled. Final APK hashes match the APKs exercised by the focused UI tests.
- Lint: **0 errors, 12 existing warnings** — ApplySharedPref 1, OldTargetApi 1,
  GradleDependency 1, ObsoleteSdkInt 1, IconLauncherShape 5, UseKtx 3; no suppression added.
- Live API 35 checks used one isolated Google Play x86_64 disposable AVD at 360x640dp, actual
  system font scale 2.0 and the real keyboard: **all eight editor combinations passed** (Blueprint
  / Roadmap × English LTR / Arabic RTL × Light / Dark). Active text and carets were readable,
  Save remained directly tappable, and the long heading stayed below status icons. The compact
  field viewport increased from 109px to 385px on this configuration; longer content scrolls
  internally. Cursor/selection handles and scrolling were inspected, including English word
  selection and Arabic-context internal scrolling; native selection menus can temporarily overlay
  controls. Automated LTR/RTL tests additionally verify selection retention and replacement.
- Creation-dialog regression checks passed at actual 2x fonts with the keyboard in both languages
  and themes: name/description retained through scrolling, Create/Cancel reachable, Cancel usable.
  Both cancellation runs left project bytes unchanged. Eight intended single-character editor
  insertions were verified exactly, preserving all other fields, task/project IDs, statuses and
  unknown legacy fields. No crash-buffer entries or ANR were recorded.

Evidence: `/home/karim/.codex/ai-pm-editor-recovery-qh560rpi/` (baseline and repaired JUnit/logs,
screenshots/window dumps, host/Lint XML, APK hashes, attempt records and data-integrity checks).
The owned `AI_PM_Editor_Recovery_qh560rpi` / `emulator-5588` was stopped; only its dedicated AVD
and Android-home directories were removed. Protected existing AVD file inventory is unchanged.
No physical devices or real user data were accessed. The first locale-reorder gesture did not
apply; it was corrected and the actual system locale was checked before Arabic verification.

Complete diff reviewed. Production changes are confined to editor layout allocation/padding;
toolbar/creation implementations and all editor save, draft, conflict, input and navigation logic
are unchanged. No schema, serialization, backup policy, signing, Application ID, SDK, dependency,
version, theme or launcher changes; no main changes or push. TalkBack spoken-output acceptance,
other devices/keyboards/window sizes, process death, backup/transfer and physical-device release
checks remain deferred. This focused pass does not close the broader release gate.

## Accessibility follow-up: native IME policy and reachable Save — 2026-11-06

Starting HEAD: `81cbf8e688f78d5dcc1545ce39af2fb3fdb97d16`, verified clean on the continuing
branch. Implementation/checks below do **not** constitute passed live keyboard acceptance.

Prior re-verification evidence: `/home/karim/.codex/ai-pm-a11y-reverify-6hj84wd7/REPORT.md`.
At that checkpoint all six `AccessibilityLayoutUiTest` cases passed (13.328 s, no failures/skips),
and creation worked with actual 2x fonts, keyboard, both directions/themes. The original
normal-font hidden editor text and project-list title clipping were resolved. Extended 2x editor
checks still found Save unreached by attempted scrolling and project headings overlapping status
icons. These later results supersede the earlier section's pending/compiled-only statements only
for that tested commit; they do not establish acceptance of the follow-up code.

Diagnosis from source and retained window/screenshots:

- The manifest had no explicit soft-input policy; both failing window dumps report `adjust=pan`.
  With the IME open, heading top moved from y=136 to y=15/16 into a 136px status-bar region.
  The shared app bar already reserved system-bar insets and wrapped the heading. Whole-window
  displacement bypassed that protection; missing title padding was not the evidenced problem.
- Save shared the outer scroller with guidance/recovery and a minimum-height, internally scrolling
  field. When available space dropped below that field's measured minimum, reaching Save depended
  on scrolling away from the focused input. The live gestures did not achieve that at 2x. Window
  panning and this shared-scroll structure are evidenced contributors; the exact focus/IME gesture
  arbitration was not independently reproduced in this emulator-free implementation batch.

Implemented minimal corrections:

- MainActivity explicitly requests `adjustResize`; existing Compose IME/system-bar padding and
  consumption remain. No extra offsets, font-scale breakpoints or Activity recreation were added.
- `ScrollableDocumentBody` measures the existing Save action in a separate trailing-aligned row
  above the scroller. Guidance, heading, recovery feedback and the readable field scroll below it;
  field viewport measurement uses the space left after Save. This prevents content/focus scrolling
  from carrying Save off-screen. Save moves from beside the document hint to its own row, retaining
  the same label, theme button, enabled condition and callback. RTL uses end alignment.
- The same text field remains composed; state, cursor/selection ownership, draft/autosave/manual-save,
  conflict/recovery and navigation guards are unchanged. Creation dialog source is byte-identical.
  App bar measurement is unchanged; its existing inset value is injectable for focused tests.

Verification actually executed, offline with JDK 17 / installed Gradle 8.13:

- Focused host checks: **9 passed** (eight sizing regressions plus explicit manifest-resize policy).
  Focused Android UI compilation passed.
- Final full invocation ran **once**: `:app:assembleDebug :app:testDebugUnitTest
  :app:assembleDebugAndroidTest :app:lintDebug` — **BUILD SUCCESSFUL**, 14 s (14.247 s measured).
  **81 host tests passed**, 17 classes, zero failures/errors/skips. Debug and test APKs assembled.
  Packaged manifest inspection confirms `windowSoftInputMode=0x10` (`adjustResize`).
- Lint: **0 errors, 12 existing warnings** — ApplySharedPref 1, OldTargetApi 1,
  GradleDependency 1, ObsoleteSdkInt 1, IconLauncherShape 5, UseKtx 3. No warning suppression.
- Two new LTR/RTL UI cases compile for a constrained phone at injected 2x font/IME/status insets:
  focused selection retention, replacement, pending input across Light/Dark, direct Save visibility
  and callback, long-heading top boundary/no overflow and Back callback. Existing editor cases now
  assert Save is displayed directly, without scrolling it into view; all original text/selection/
  glyph assertions remain. Existing creation and toolbar cases are retained.
  **All eight UI cases compiled only in this batch; none executed.** Simulated inset geometry is
  not proof of real window/keyboard, nonlinear system fonts, TalkBack or OEM behavior.

Evidence: `/home/karim/.codex/ai-pm-a11y-followup-e3o3f4lf/` (focused/final logs, JUnit XML,
Lint XML, packaged manifest, artifact hashes and source-invariant checks). Complete diff reviewed.
Models, persistence/serialization and provider callbacks are unchanged; Save logic and its enabled
condition match exactly ignoring indentation. Manifest change is limited to resize policy. No schema,
backup, signing, Application ID, SDK, dependency, version, launcher or user-data changes. No emulator,
AVD, ADB, installation, physical-device, process-death, PD/DU, push or main operations.

Remaining acceptance: actual 2x keyboard-visible Save and text/cursor/selection in Blueprint and
Roadmap; long headings below the status bar in RTL/LTR and both themes; normal-font and creation
regressions after the native policy change. Extremely constrained input space and IME/focus-driven
scrolling still require live observation. TalkBack/focus order and all other release gates remain
open. **NOT Ready for Release**. Next: separately authorize focused disposable-emulator verification.

## Focused accessibility layout corrections — 2026-11-06

Starting HEAD: `94757c60744a66d0c5a514090f304f287851738b`, verified clean on the continuing
branch. These are development corrections, **not passed live accessibility acceptance**.

The preceding disposable-API-35 acceptance evidence is retained outside Git at
`/home/karim/.codex/ai-pm-accessibility-retxqbrm/REPORT.md`. Seven selected Android tests and
all eight real font/locale/theme preview cases passed, but actual IME checks exposed creation
controls behind the keyboard, hidden Blueprint glyphs and a clipped large-font toolbar title.
TalkBack speech and reliable focus-order/no-trap acceptance remained blocked.

Implemented within the three-fix scope:

- **Creation:** the form previously used a non-scrolling Column and did not explicitly reserve
  the IME area. The extracted `ProjectCreationDialog` retains its strings, controls, validation
  and callbacks; its edge-to-edge dialog consumes safe drawing/IME insets in its own window.
  The form scrolls independently of Create/Cancel. Name/description state and the exact save
  callback remain owned by `ProjectManagerApp`, including pending input after failed saves.
- **Blueprint/Roadmap:** fixed guidance/status/recovery content could consume almost all available
  height, leaving a weighted field too short for its label and text. `ScrollableDocumentBody`
  measures that chrome and retains the remaining field height when space permits. In compact
  space it preserves a typography-scaled minimum (three content lines, label and padding),
  scrolling the body instead of collapsing the field. Save/guidance/recovery controls can scroll
  in that compact layout and remain reachable. Scaffold padding is consumed before child IME
  padding. The same field stays composed; no new text state, selection reset or editor key was
  introduced. Draft, autosave, manual-save, conflict and dirty-navigation callbacks are unchanged.
- **Toolbar:** fixed single-row app-bar height and actions crowded the title at large fonts.
  `AccessibleAppBar` measures actual themed text/buttons, moves actions to a wrapping row only
  when needed and lets heading height grow. Existing labels, callbacks, minimum bar height,
  theme typography/colors and traversal grouping are retained; placement uses start/end semantics.

Verification actually executed:

- Eight focused `LayoutSizingTest` host regressions passed, covering inline/wrapped toolbar
  allocation, navigation, enlarged typography, compact editor space and large measurement values.
- Six new `AccessibilityLayoutUiTest` cases **compiled only; not executed**. They cover LTR/RTL
  creation with injected bottom insets at 2x fonts, blank-name validation and retained failed input;
  drawable mixed-text glyph bounds and retained selection under compact viewport resizing; and
  large-font title/action separation and existing callbacks. These are simulated constraints,
  not actual Android IME delivery, nonlinear system font scaling or TalkBack evidence.
- Final full offline invocation ran once: `:app:assembleDebug :app:testDebugUnitTest
  :app:assembleDebugAndroidTest :app:lintDebug` — **BUILD SUCCESSFUL**, 24 s. All **80 host tests**
  passed across 16 classes, zero failures/errors/skips. Both Debug APK assemblies passed.
- Final review then changed only the dialog modifier's native-inset lookup to resolve within the
  dialog window. Affected Debug/test APK assembly and Lint were checked again; the full host suite
  was not repeated after this UI-only modifier correction. Host sizing/persistence logic is unchanged.
  Affected checks passed (12 s). Final Lint: **0 errors, 12 existing warnings** — ApplySharedPref 1,
  OldTargetApi 1, GradleDependency 1, ObsoleteSdkInt 1, IconLauncherShape 5, UseKtx 3. No new
  warnings or warning suppressions were introduced.
- Source comparisons confirm byte-identical models, Activity/provider callbacks, ProjectStorage/
  safe-update code, draft/autosave logic and the creation save callback. No existing application
  project files or drafts were accessed by this batch. No schema, serialization, manifest, signing,
  Application ID, SDK, dependency, version, backup-policy or launcher changes.

Evidence: `/home/karim/.codex/ai-pm-a11y-fixes-z637k82z/` (focused/full logs, host XML, Lint,
source-invariant checks and final diff). No emulator/AVD/device operations, Android test execution,
PD/DU, process-death, backup transport, GitHub push or main changes occurred.

All three original live failures remain **pending separately authorized manual re-verification**.
Check actual keyboard insets, cursor/selection, scrolling and reachable Save/Create/Cancel under
normal/maximum Android fonts, both locale directions and themes. Spoken TalkBack, focus order,
OEM/device compatibility and every other release gate remain open. **NOT Ready for Release**.

## Approved v0.2 stabilization batch — completed locally, 2026-11-06

Starting HEAD: `c70a72f2593a5e9b3394bedd81f121de06ff4b14`. Local implementation commits:

- `903809df07e1d217844e3b6daa1a401b96d747b3`: reconstructed current AGENTS.md/roadmap,
  README pointers and release-gate reconciliation. Both new documents explicitly state that they
  are newly authored reconstructions, not recovered historical originals. The documentation
  checkpoint below records the initial state before the UI tasks were implemented.
- `85b2c2e0d6665f8a706f7d3824d17e2c234fb97b`: preview text now scrolls inside the existing
  AlertDialog; title/Close stay outside that scroll area. All displayed entries, cap notice and
  read-only disclaimer are reachable. The 30-entry limit, counts, validation and Close callback
  are unchanged; error text uses the same scrollable body. No import/restore action was added.
- `b044800849834ac695b9442ec2476102309cf8a7`: the existing creation FAB has semantic name
  `إنشاء مشروع` (Create project). Its + text, button role, appearance, placement and click handler
  remain. Existing + selectors and new accessible-name navigation both work.

Verification actually executed for this batch:

- Focused production/Android test Kotlin compilation passed before final validation.
- Final full offline invocation ran **once**: `:app:assembleDebug :app:testDebugUnitTest
  :app:assembleDebugAndroidTest :app:lintDebug` — **BUILD SUCCESSFUL** (25 s reported by Gradle).
  Debug and Android test APK assembly passed. All **72 host tests passed**, 15 classes,
  zero failures/errors/skips. Lint: **0 errors, 12 existing warnings** (ApplySharedPref 1,
  OldTargetApi 1, GradleDependency 1, ObsoleteSdkInt 1, IconLauncherShape 5, UseKtx 3).
- **7 focused Android tests passed**, zero failures/skips, 15.01 s command wall time on the
  verified existing `REDMAGIC_10_Pro_Test`, `emulator-5554`, API 35. Two new
  `BackupPreviewAccessibilityUiTest` cases inject an effective narrow viewport (<=320x640dp)
  and 2x Compose font scale in LTR/RTL: each scrolls to all 30 displayed entries and both
  disclaimers, excludes entry 31, checks stationary/accessible Close, dismisses and compares bytes.
  One new `ProjectCreationAccessibilityUiTest` checks accessible name/role/click, unchanged +,
  existing validation and cancellation without writes. Existing `RestorePreviewTest` (2) and
  the two relevant creation/preview-error `ProjectWorkflowUiTest` methods also passed.
- Both candidate APKs matched the installed signing certificates and were updated in place only
  after identity/boot/foreground checks; no uninstall/clear/reset was performed. Existing project
  JSON and application preferences remained byte-for-byte unchanged and private file inventory
  was unchanged. Android's runtime profile marker changed after the APK update/startup.
- Complete diff/whitespace reviewed. Changes are limited to reconstructed/reconciled documentation,
  preview scrolling, creation semantics and focused test sources. Persistence/model/validation,
  schema, signing configuration, Application ID, dependencies, version, backup rules, icon assets,
  main and GitHub were not changed. No verification failure or retry occurred.

Evidence retained outside the repository: `/home/karim/.codex/ai-pm-v02-stabilization-o5bbr1vq/`
(build/compile logs, host XML, Lint XML, focused instrumentation XML/log, signing and data hashes).

Remaining verification:

- The complete Android instrumentation/acceptance suite was **not** rerun; only the seven focused
  cases ran. All Android test sources compiled. No heavy emulator, PD/DU, physical-device or actual
  process-death tests ran. No emulator settings, graphics, AVDs, locale or theme settings changed;
  viewport/direction/font conditions were injected into test composition only.
- Automated semantics and simulated font/viewport checks do not establish manual TalkBack speech,
  real platform nonlinear large-font scaling, every OEM/window size or broad accessibility acceptance.
  Existing lifecycle/process-death, backup transport/OEM, signing/update/data-continuity and release
  gates remain open. **NOT Ready for Release**.

## Approved v0.2 stabilization batch — documentation checkpoint, 2026-11-06

Starting HEAD: `c70a72f2593a5e9b3394bedd81f121de06ff4b14`, verified clean on the continuing
development branch. The owner approved documentation recovery, long read-only preview accessibility
and an accessible name for project creation. No branch reconciliation/merge is needed.

- Created newly authored, dated `AGENTS.md` and `docs/ROADMAP.md`. Both explicitly identify
  themselves as reconstructed current documents, not recovered historical originals. Earlier
  missing-document statements below remain accurate for their original checkpoints.
- Reviewed README, current implementation, release gate, broader plan and JSON compatibility policy.
  README now points to current status/roadmap; the release gate distinguishes implemented Settings,
  themes and RTL/Bidi work from historical deferrals and remaining acceptance. No historical
  requirements, Gate 0, Phase 1 or reduced-scope safety conditions were waived.
- Preview scrolling and project-creation semantics are pending at this documentation checkpoint.
  Their final implementation/results will be recorded after verification. Actual process death,
  heavy emulator/PD/DU, physical-device work and release readiness remain deferred.

Prior focused RTL/Bidi closure (not rerun for documentation recovery):

- Retained evidence at `/home/karim/.codex/ai-pm-final-bidi-verification-64kcjtc6/` records 3/4
  targeted Android regressions passing on `abf9f58`; mixed editing failed because insertion used
  the old selection while automatic Compose frames were paused.
- Commit `c70a72f2593a5e9b3394bedd81f121de06ff4b14` adds one frame and an explicit selected-range
  assertion. The previously failing method then passed **1/1**, 2.675 s runner time, on the existing
  API 35 emulator. XML/log evidence: `/home/karim/.codex/ai-pm-bidi-selection-sync-j1ts5k9_/`.
  All original assertions remain and existing private-file hashes were unchanged. This was a
  test-only correction, not a full-suite rerun or broad IME/process-death acceptance.

## Targeted editable-text / numeric bidi corrections — 2026-11-06

Owner-authorized development-only correction, starting at
`b00e9cd31950cf08b95446ee0acb9c5677111c04`. Implementation/tests commit:
`7b1d85edbea9bab651e32edec0d4a14c39fd62f2`.
**NOT Ready for Release**; existing acceptance and release gates remain open.

Evidence motivating this batch:

- The previous verification of **b00e9cd** passed all **15 Android UI tests** (6 locale-direction,
  9 theme, including the corrected dropdown selector). Its retained XML, logs and screenshots are
  under `/home/karim/.codex/ai-pm-rtl-verification-lyvy421e/`. This is prior-candidate evidence;
  no emulator verification of the corrections below was performed.
- Manual screenshots showed Latin-only Blueprint input right-aligned under Arabic locale, and
  logical project count `0 / 1` displayed visually as `1 / 0`. No baseline comparison established
  when those behaviors originated. The earlier batch's compiled-only statements below remain
  historical records of that batch, rather than the later Android verification.

Implemented corrections:

- Shared `contentTextStyle` explicitly requests `TextDirection.Content` and `TextAlign.Start`,
  preserving the supplied typography. `ParagraphText` and all nine existing text-field call sites
  use this policy, including the shared Blueprint/Roadmap editor, metadata/task inputs and search.
  Paragraph direction follows content; empty/neutral text retains the locale fallback. Surrounding
  field labels and control ordering retain Android locale direction. No text transformation,
  selection reset, new input state, Activity recreation or language selector was introduced.
- `taskCountExpression` wraps only the displayed numerator / denominator with Unicode LTR isolate
  and matching pop-directional-isolate (U+2066 / U+2069). The existing VERIFIED count and total
  calculations are unchanged. Its only production caller is the project-card display; directional
  controls are not inserted into project models, editable values, JSON or export data.
- Three host regressions cover Arabic/English surrounding text, both paragraph base directions,
  zero/nonzero/multiple-digit counts and balanced isolation. Four additional locale UI regressions
  cover English Blueprint alignment under Arabic locale, Arabic Roadmap alignment under English
  locale, mixed Arabic/English/JSON editing with selection replacement, and actual count glyph order
  in both locales. Existing assertions remain; fixtures check unchanged project bytes while pending.

Verification actually executed:

- Focused host tests: **11 passed** (`TaskCountDisplayTest`, `TaskStatusSummaryTest`,
  `EditorSnapshotTest`). The initial combined check took **4.34 s**; host tests passed but UI-test
  compilation required explicit opt-in for Compose's experimental selection-testing API.
  Added the annotation only to that test method; affected UI compilation then passed in **1.54 s**.
- Final full offline invocation ran once: `:app:assembleDebug :app:testDebugUnitTest
  :app:assembleDebugAndroidTest :app:lintDebug` — **BUILD SUCCESSFUL**, **8.60 s**.
  **72 host tests passed**, 15 classes, zero failures/errors/skips; Debug/test APK assembly passed.
  Lint: **0 errors, 12 existing warnings** (ApplySharedPref 1, OldTargetApi 1,
  GradleDependency 1, ObsoleteSdkInt 1, IconLauncherShape 5, UseKtx 3).
- Complete diff/whitespace reviewed. MainActivity changes are limited to nine text-style arguments
  and one display formatter; count arithmetic, editor callbacks, draft/dirty-navigation/stale-save
  protection and persistence code are unchanged. No schema, migration, dependency, identity,
  signing, version, launcher, emulator/device, installation, push or main-branch operations.
- New Android regressions **compiled only; not executed**. JVM bidi results do not establish
  Android glyph placement, IME composition, selection handles or TalkBack behavior. Repeat focused
  disposable-emulator testing and visual inspection of both reported issues before acceptance;
  broader dark/large-font/lifecycle/OEM and existing release-gate checks remain deferred.
- Logs, XML and timings: `/home/karim/.codex/ai-pm-bidi-correction-nci_hkif/`.
  Start: **2026-11-06 02:23:00 +05:45** (Asia/Kathmandu).
  `AGENTS.md` and `docs/ROADMAP.md` remain missing; no contents were assumed.

## Arabic RTL / English LTR correction batch — 2026-11-06

Owner-authorized bounded correction, starting at
`37a29fa254f02fe4711562a158ddb6b1f5c2c0ad`. Implementation and tests:
`0256051` (`fix(ui): align bilingual paragraphs and scope theme dropdown test`).
**NOT Ready for Release**; no Gate 0, Phase 1 or release acceptance is closed by this batch.

Implementation inspected and corrected:

- Android already supplies locale-aware Compose layout direction, and the manifest already has
  `supportsRtl=true`. Existing Material fields, navigation, dialogs, radio rows, tabs and task
  FlowRow actions use inherited start/end semantics. These mechanisms were retained without
  forcing a global direction based on keyboard language or project content.
- New shared `ParagraphText` gives block text the available width, `TextDirection.Content` and
  `TextAlign.Start`: first-strong Arabic paragraphs start at the right and English at the left;
  neutral-only paragraphs use the inherited layout direction. Standard bidi resolution handles
  mixed content without changing strings, JSON, identifiers, statuses or numbers.
- Applied to project-list/card/overview text, task titles/status text, Settings/storage/appearance
  disclosures, Blueprint/Roadmap headings and recovery messages, and creation/edit/preview/error/
  confirmation dialog text. Inline button, tab, dropdown and field labels retain intrinsic sizing.
  Existing controls already mirror with the Android locale; no extra Activity recreation,
  configuration interception, editor keys or navigation/state changes were introduced.
- `ThemeUiTest.taskCreationSearchAndStatusFilterSurviveThemeChange` now scopes `قيد التنفيذ`
  to a popup ancestor, distinguishing the dropdown option from the task action. All later
  assertions remain. Retained prior execution evidence (`ai-pm-theme-verification-iqaxmdd7`)
  records 8/9 passing and this selector failure before the theme change; that is historical
  evidence, not a passing rerun of the corrected test.
- Six new `LocaleDirectionUiTest` cases cover Arabic/English paragraph geometry, mixed JSON/
  identifiers/numbers, appearance-control order, metadata pending-input/discard confirmation,
  and document pending-input/dirty-navigation protection across injected locale direction changes.
  Fixtures use only isolated synthetic files and simulated Android configurations.

Verification actually executed:

- Focused host tests: **18 passed** (`EditorSnapshotTest`, `ProjectMetadataTest`, `TaskTitleTest`,
  `TaskListSearchTest`); Android UI Kotlin compilation passed. Combined invocation: **19.50 s**.
- Final full offline invocation ran once: `:app:assembleDebug :app:testDebugUnitTest
  :app:assembleDebugAndroidTest :app:lintDebug` — **BUILD SUCCESSFUL**, **21.96 s**.
  Debug/test APK assembly passed; **69 host tests passed**, 14 classes, zero failures/errors/skips.
  Lint: **0 errors, 12 existing warnings** (ApplySharedPref 1, OldTargetApi 1,
  GradleDependency 1, ObsoleteSdkInt 1, IconLauncherShape 5, UseKtx 3).
- Complete diff and whitespace reviewed. Existing screen changes are only block-text call
  substitutions; strings, colors, spacing values, field callbacks, storage, drafts and stale-write
  logic remain unchanged. No manifest, dependency, identity, signing, version, launcher, backup,
  schema or migration changes. No emulator/device operations, UI-test execution, push or main changes.
- Logs, host XML, Lint XML and timings retained outside the repository at
  `/home/karim/.codex/ai-pm-rtl-batch-esb9041k/`. Start: **2026-11-06 02:00:54 +05:45**
  (Asia/Kathmandu). No build/test failure required diagnosis or re-execution.

Remaining acceptance gaps:

- New UI tests and the corrected theme test **compiled only; not executed in this batch**.
  Actual Arabic/English device-locale delivery, all-screen visual layout, narrow screens/large fonts,
  keyboard/mixed-text editing, dialog ordering and actual Activity lifecycle behavior remain unverified.
  Injected composition tests do not establish real locale-change/recreation or process-death safety.
- There is still no in-app language selector or full English UI translation; existing Android-locale
  behavior and Arabic labels are preserved. This correction does not add that separate feature.
- `AGENTS.md` and `docs/ROADMAP.md` are missing; no contents were assumed. Existing release
  gates, backup/OEM checks and previously deferred lifecycle/physical-device acceptance remain open.

## Phase 2 theme batch — 2026-11-06

Owner-authorized **development-only** theme implementation, starting at
`1584ec5f62176f477ceef454103db111300ac7a9`. Implementation/tests commit:
`86b5da7d753562bce666ac0b2ccd0c23878b8ee8`. **NOT Ready for Release**. Gate 0 remains open,
Phase 1 acceptance remains incomplete, and reduced v0.2 release scope is unchanged. This specifically authorized
increment does not approve release/distribution or language/localization work. Earlier settings
checkpoint statements below describe their original scope and evidence.

Implemented locally:

- Exactly SYSTEM / LIGHT / DARK, with SYSTEM (Follow System) as the default and fallback for
  missing/unsupported stored strings. Invalid values are not automatically rewritten.
- Stable `AppTheme` at the existing application root; system appearance comes from
  `isSystemInDarkTheme()`. Explicit modes ignore system appearance. There is no theme-keyed
  app subtree, intentional Activity recreation, or dynamic-color path. MainActivity handles only
  `uiMode` configuration changes; other configuration/recreation behavior is not intercepted.
- Approved Dark backgrounds/cards/text (`#111418`, `#2A2F36`, white, `#C4CCD6`) and Light
  backgrounds/cards/text (`#F6F8FB`, white, `#17263D`, `#536276`), with explicit semantic
  surfaces, containers, error, outline and inverse roles. Accessible text/focus primary is cyan
  `#00C2FF` in Dark and `#0B5ED7` in Light. Existing Save/Add filled actions remain
  `#0D6EFD` with white content through a shared helper. Black interaction layers darken blue
  instead of lowering white-text contrast. Existing typography, shapes, spacing, labels and
  navigation remain; launcher assets are unchanged.
- Existing private `application_preferences` store, one key `appearance_mode`, with stable
  SYSTEM/LIGHT/DARK serialization. `ThemePreferences` exposes observable state, opens/reads/
  commits off the main thread, rejects conflicting requests while saving and applies a choice
  only after successful persistence. Missing/wrong-type/unreadable strings use the adapter's safe
  default. Initialization errors show a retryable fallback; rejected/exceptional saves retain the
  confirmed mode and requested selection for Retry, without treating in-memory values as durable.
- A compact accessible radio group in Settings: Follow System / Light / Dark, loading/pending
  feedback, inline live-region errors and Retry. Existing storage disclosures and export/preview
  callbacks remain. The appearance section is injected through a composable slot; it does not
  depend on project storage or expose Settings inside active editors.
- Minimal light/night platform window resources and runtime system-bar foreground handling via
  WindowCompat. Existing API 35 enforced edge-to-edge and pre-35 decor-fitting policy are retained;
  no navigation-bar contrast protection, IME handling or content inset behavior is disabled.

Verification actually executed:

- Production Kotlin compilation: **passed**.
- Focused host tests: **20 passed** (9 new `ThemePreferencesTest`, 4 new `ThemeColorsTest`,
  7 existing `AppPreferencesTest`). These cover serialization/fallback without rewriting,
  system resolution, save/reload, failed commit and retry, initialization/opening exceptions,
  repeated/conflicting requests and off-caller-thread writes. Color checks cover normal text,
  outlines, error/inverse/container pairs and sampled interaction overlays. Approved blue/white
  contrast is approximately 4.501:1; visual and composited-state review remains necessary.
- Android source compilation: **passed**. Nine new `ThemeUiTest` cases compile with isolated
  synthetic files, fake preferences and injected system appearance. They cover selection/reload,
  explicit/system resolution, failed save/Retry, project search/list scroll, Settings scroll/Back,
  Blueprint/Roadmap pending drafts/guards, metadata reload and task-title discard dialogs, and
  new-task/search/status-filter state plus unchanged project bytes. **None were executed.**
  They do not establish actual Android configuration callbacks, Activity identity, real preference
  filesystem durability, cold relaunch behavior, system bars or OEM/runtime visual acceptance.
- Final full offline invocation ran once: `:app:assembleDebug :app:testDebugUnitTest
  :app:assembleDebugAndroidTest :app:lintDebug`. Debug and test APK assembly passed, and all
  **69 host tests passed**, 14 classes, zero failures/errors/skips. Lint initially failed on two
  API-27-only `windowLightNavigationBar` XML attributes under minSdk 26. Those attributes were
  removed; the existing runtime compatibility controller supplies navigation-icon appearance.
- Only affected final checks were repeated after that resource correction:
  `:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` — **BUILD SUCCESSFUL**.
  The complete host suite was not rerun after the XML-only correction. Final Lint: **0 errors,
  12 warnings**: ApplySharedPref (1), OldTargetApi (1), GradleDependency (1), ObsoleteSdkInt (1),
  approved legacy IconLauncherShape (5), and UseKtx (3). Confirmed `commit()` results are intentional;
  switching to fire-and-forget apply would lose the required failure acknowledgement. SDK/launcher
  and convenience cleanup findings remain deferred; no warning suppression was added for them.
- An initial UI-fixture compile error (a theme read inside non-composable SideEffect) was corrected
  by reading the color in composition and capturing it in the effect. Subsequent compilation passed.
- Complete implementation/test diff and whitespace reviewed. `ProjectStorage` and stale writes
  compare unchanged; document/task editor logic is unchanged except for filled-button styling.
  Manifest changes are limited to the app-owned window theme and MainActivity `uiMode` handling.
  No schema/migration, project persistence API, backup rule, signing, application ID, SDK/version,
  dependency, launcher asset or release-gate scope changes. No emulator/AVD/physical-device,
  installation, PD/DU, GitHub push or main-branch operations occurred.

Current generated evidence (not committed): `app/build/test-results/testDebugUnitTest/`,
`app/build/reports/lint-results-debug.xml`, Debug APK and Android test APK under `app/build/outputs/`.
Historical counts and previous Settings UI evidence below are not reruns of this theme candidate.

Deferred/known limits:

- Actual system-night-mode delivery without Activity recreation, real preference persistence/
  relaunch, system-bar contrast, large fonts, disabled/pressed states, keyboard/insets and broad
  mixed-language/visual acceptance need separately authorized runtime verification.
- A brief system-colored cold start before an explicit stored mode loads is owner-accepted.
  Pending theme requests/errors are in memory, not a new durable recovery record.
- Existing task search/filter and some transient warning/error state use ordinary remember and
  can reset on unrelated Activity recreation. Full process death and large saved-state payloads
  remain pre-existing gaps; this batch does not claim to solve them or change their storage.
- `AGENTS.md` and `docs/ROADMAP.md` remain missing. Language selection is not implemented.

## Phase 2 Batch 1 — Core Application Settings — 2026-11-06

Owner authorization explicitly permits this bounded **development-only** increment, starting at
`b723b3b6698ad323e159c21c3cdd53847bb83b33`. Implementation/tests commit:
`09a0604dd16a9e1bb1090afeb6b2b69bde0ae380`. This does **not** close Gate 0, declare Phase 1
fully accepted, expand the reduced v0.2 release scope, or approve release/distribution.
**NOT Ready for Release**.
The historical phase ordering and requirements remain recorded; this is a specific authorization
to develop the settings foundation while their acceptance conditions remain open.

Implemented locally:

- Project-list Settings entry and a Material3 informational destination. Toolbar/system Back
  returns to the list. Search query and list scroll state are hoisted outside the destination
  and use saved state; filtered results are derived from the retained query. Settings is not
  accessible from active project editors; their existing dirty-navigation guards and
  save/reload/discard behavior are unchanged.
- Storage disclosure states that projects live in private local app storage, manual JSON export
  creates a user-controlled external copy, and preview does not import, merge or replace data.
  It explains configured automatic cloud/device-transfer exclusions, unverified manufacturer/
  transfer behavior, and loss risk after uninstall/private-data loss without an external export.
  It promises neither automatic external backup nor guaranteed recovery. Settings reuses the
  existing export and read-only preview callbacks; no import/reset/delete action was added.
- A small `AppPreferences` interface and private Android `SharedPreferences` adapter, separate
  from project JSON and without new dependencies. Missing, incorrectly typed or unreadable
  strings use the caller's default; rejected/exceptional writes return failure. Synchronous
  commits require a background dispatcher when used. No preference keys, stored values, language
  choices or theme choices were introduced. The informational screen does not instantiate/open
  the store because there is no legitimate preference value to save in this batch.

Verification actually executed:

- Focused production Kotlin compilation: **passed**.
- Focused host tests: **10 passed** (7 new `AppPreferencesTest`, 3 existing `BackupPolicyTest`).
  Preference tests use a fake platform interface: defaults without writes, Unicode round-trip,
  wrong-type fallback without mutation, read failure, rejected commit, write exception and blank
  key rejection. They do not establish real Android filesystem durability.
- Android test source compilation: **passed**. Four new `SettingsUiTest` cases cover toolbar Back,
  system Back after saved-state restoration, retained search/results and unchanged project bytes,
  disclosure/existing callback routing, and continued dirty-document navigation protection.
  **These tests have not been executed**; compilation is not runtime acceptance. Fixtures are
  isolated and include legacy unknown fields, IDs and VERIFIED status.
- Final offline validation ran once: `:app:assembleDebug :app:testDebugUnitTest
  :app:assembleDebugAndroidTest` — **BUILD SUCCESSFUL**. Complete host suite: **56 passed**,
  12 classes, zero failures/errors/skips. Both Debug and test APK assembly passed.
  Current JUnit evidence: `app/build/test-results/testDebugUnitTest/` (generated, not committed).
- Complete code/test diff and whitespace checks passed. `ProjectStorage`, stale-write comparison,
  document/task editors and their save paths were compared with the starting implementation and
  are unchanged. No JSON schema/migration, project-data mutation path, manifest, icon, backup
  exclusion, SDK/dependency/build configuration, signing, application ID or version changes.
  No emulator, physical-device, PD/DU, Lint, GitHub push or main-branch operations ran.

Remaining limitations: Settings navigation/restoration, disclosure layout on narrow/large-font
screens and existing editor guards require the compiled UI tests plus separately authorized
runtime review. Real preference platform durability remains unverified and no preference feature
is active yet. Actual OEM backup/device-transfer behavior and all existing release gates remain
open. `AGENTS.md` and `docs/ROADMAP.md` are missing; no contents were assumed.
Language and theme selection require separate owner authorization and were not started.

## Current implementation

Existing metadata/title editing, project/task search, task-status filters, responsive status
buttons, overview summaries, guidance and tips remain implemented. Confirmed reload/discard,
pending-input retention after failures, and Blueprint/Roadmap dirty-navigation protection remain.

The preceding security/state maintenance batch added:

- Explicit platform backup exclusions while retaining `allowBackup=false`. API 26–30 uses
  `fullBackupContent`; API 31+ uses `dataExtractionRules` with separate cloud-backup and
  device-transfer sections. Every supported app-data domain is excluded, including credential-
  and device-protected storage and app-specific external files. Project JSON, atomic sidecars,
  recovery drafts and preserved drafts fall under these exclusions. Cache exports are already
  excluded by Android. User-requested document export remains available and unchanged.
- An immutable `EditorSnapshot` value with private, defensively copied fields and no mutation API.
  Both editors use an explicit Compose state Saver. It saves/restores detached lists using the
  previous ArrayList<String> representation, retaining exact field boundaries, IDs, statuses,
  order and the original stale-edit baseline. Only saved UI state uses this representation;
  project JSON is unchanged. Reload still replaces the baseline only after confirmation.
- Host configuration checks for manifest references, legacy exclusions and each modern transport;
  saver tests for round-trip equality, stale-base retention, old representation compatibility,
  mixed-language input and independence from mutable saved/restored lists.

No application storage contents were changed. There is no new backup/import/restore mechanism,
JSON migration, signing/identity/version change, SDK change or UI redesign. Existing persistence
and stale-write comparison paths are retained.

## Icon inspection

**Approved Android launcher icon integration: complete locally.** Integration commit:
`4583d33c7ed762b873558f9263c74551c104dd6e` (`feat(android): integrate approved launcher icon`),
2026-11-05. Documentation-only closure: 2026-11-06.

The integration added fourteen supplied icon resources (two adaptive layers, two adaptive XML
resources and ten legacy density PNGs) and updated only `android:icon` and `android:roundIcon` in
AndroidManifest.xml. The approved package is `AI_Project_Manager_New_Logo_Android_Icons.zip`,
SHA-256 `c8211813c907ffb2618b4f8f7065d1cd1714608cfabf2f787a0cc5ef8c2e17a6`.
All fourteen resource files match the approved ZIP byte-for-byte. All twelve packaged PNGs retain
identical decoded pixels; compiled adaptive XML and manifest references resolve correctly.
No artwork, backup policy, application logic, identity, signing, JSON schema or version was changed.
The earlier absence of an approved asset recorded at the security/state checkpoint is superseded
by this owner-approved integration; no monochrome design was invented.

Verification and evidence reviewed for this closure:

- Debug build and Android resource compilation **passed in the integration execution report**
  (`:app:assembleDebug`). A standalone build transcript was not preserved; the existing APK and
  compiled resources support the packaging result, but the build was not independently rerun.
- Three existing `BackupPolicyTest` configuration tests **passed**. The retained JUnit XML records
  3 tests, zero failures/errors/skips, timestamp `2026-11-05T18:07:40.241Z`.
- Static circle, squircle and rounded-square mask checks **passed**, including 48px previews.
  The saved contact sheet shows the ribbon, checkmark and both sparkles without visible clipping.
  Reinspection of the packaged foreground confirms maximum radial extent **320.765491px**
  around canvas center (540,540), within the **330px** safe region. At alpha >=128 the maximum is
  315.585963px; zero pixels exceed the safe radius at either threshold. Distances use pixel centers
  and the intended 1080px-to-108dp layer mapping. This is static geometry, not launcher animation.
- Pixel Launcher inspection **passed** on a newly created disposable API 35 AVD,
  `AI_PM_Icon_Disposable` / `emulator-5580`, x86_64, 1080×1920 at 420 dpi. The retained screenshot
  and launcher record support correct app-drawer rendering without obvious clipping or an inner
  background tile. This establishes one launcher/configuration only.
- The execution report records that this disposable AVD was stopped and removed after inspection.
  The retained emulator log records shutdown and its isolated `avds/` directory is now absent.
  Existing AVDs and physical devices were not modified according to the execution scope record;
  this closure did not access devices to independently audit that operational claim.
- No GitHub push or main-branch changes were performed according to the integration report.
  This closure performs only local documentation work; no remote or main operations are used.

Local evidence is outside Git at
`/home/karim/.codex/ai-pm-icon-integration-ayi_va9g/`: `packaged-verification.json`,
`launcher-verification.json`, `emulator.log`, `previews/static-mask-contact-sheet.png`, and
`previews/disposable-launcher-app-drawer.png`. The retained APK is
`app/build/outputs/apk/debug/app-debug.apk`, SHA-256
`5dd42657d36a1ee9cf91bb70e32888de1b980b2f834037c5c86e0c07bb2cc0d7`, matching the saved
verification record. Test XML is
`app/build/test-results/testDebugUnitTest/TEST-com.karim.aiprojectmanager.BackupPolicyTest.xml`.
These local/generated files are not committed and may be removed by later cleanup/builds.
No builds, automated tests or emulator/physical-device operations were run for this closure.

## Verification for the previous security/state maintenance batch

- Focused host checks: **8 passed** (5 editor-snapshot tests, 3 backup-policy tests).
- Production and Android test source compilation: **passed**.
- Two existing isolated Compose UI regressions: **2 passed**, zero failures/errors/skips,
  8.115 seconds of instrumentation time. Methods:
  `ProjectMetadataUiTest#conflictReloadRequiresConfirmationAndAllowsEditAgainstNewBase` and
  `TaskTitleUiTest#conflictReloadRetainsInputUntilConfirmedAndPreservesNewStatus`.
  These check restored pending input, stale-save blocking, reload confirmation/cancellation,
  unchanged bytes during reload, and subsequent safe saving with newer data retained.
- Emulator only: `emulator-5554`, AVD `REDMAGIC_10_Pro_Test`, Android 15/API 35, x86_64,
  320×640 at 160 dpi. No physical-device operations or full instrumentation/PD/DU suite ran.
- Final offline validation ran once: `:app:assembleDebug :app:testDebugUnitTest
  :app:assembleDebugAndroidTest :app:lintDebug` — **BUILD SUCCESSFUL** (8 seconds).
- Complete host JVM suite: **49 passed**, 11 classes, zero failures/errors/skips.
- Merged Debug manifest retains all three backup-policy attributes and correct resource references.
- Lint: **0 errors, 4 warnings**. `DataExtractionRules` and both
  `MutableCollectionMutableState` findings are gone without suppressions.
- Complete diff reviewed and whitespace check passed. No push or cloud CI run.

Remaining Lint warnings at that checkpoint: `OldTargetApi` (targetSdk 35), `GradleDependency` (compileSdk 35),
`MissingApplicationIcon`, and debug-fixture `UseKtx`. SDK values are intentionally unchanged;
icon integration was then pending and is now complete locally as recorded above. KTX convenience
cleanup is deferred. No post-icon-integration Lint run or updated warning count is claimed.

Local reports: `app/build/test-results/testDebugUnitTest/`,
`app/build/outputs/androidTest-results/connected/debug/`, and `app/build/reports/`.
Generated reports are not committed. Platform policy references:
[Android backup configuration](https://developer.android.com/identity/data/autobackup) and
[Android 12 backup/transfer changes](https://developer.android.com/about/versions/12/behavior-changes-12#backup-restore).

## Previous focused UI checkpoint

Before this maintenance batch, seven distinct added UI tests passed across focused runs: initial
5/7 passed, followed by diagnosed test-only lazy-list scrolling fixes and successful retries.
Commit `d8fb04f` records that fix. This is historical evidence, not a rerun of all seven against
this maintenance candidate. Earlier full 107-test/DU evidence remains commit-specific.

## Deferred verification and risks

- Other Android launchers and manufacturer-specific masks, launcher animation behavior, and
  themed monochrome icon support (if required for release) remain pending. Static masks and one
  Pixel Launcher inspection do not constitute broader launcher acceptance.
- Actual cloud-backup/device-transfer transport behavior across Android versions and OEMs is not
  verified by static rules or Lint. No transport was invoked and no real data was transferred.
- Full process death, large saved-state payloads, broad visual/RTL/accessibility, scale/provider
  lifecycle, stable signing and non-destructive physical-device update/data continuity remain open.
- Reload discards pending text only after confirmation; there is no automatic merge or edit history.
  Pending form state uses Android saved state, not a new disk draft.
- `AGENTS.md` and `docs/ROADMAP.md` remain missing; their contents have not been invented.

## Recommended next step

Separately authorize the nine focused theme UI tests and visual review on disposable data,
including actual system-mode changes, native bars, keyboard/insets and state preservation.
Do not treat compiled UI coverage or static contrast checks as runtime appearance acceptance.
Keep language/localization and other release gates under separate authorization.

The following Core Settings recommendation is historical; its request for theme authorization
is superseded only for this explicitly authorized development batch:

For this development increment, separately authorize focused Settings UI execution using disposable
data before accepting navigation/restoration and visual behavior. Scope language/theme work only
with separate owner approval; do not treat the preferences foundation as those features.

Review the explicit backup opt-out policy and separately scope platform/OEM transport verification
using disposable data. Separately scope other-launcher/mask and animation checks, and decide
whether themed monochrome support is required. Preserve all remaining
[release gates](RELEASE_GATE_V0_2.md).

## Physical visual acceptance — 2026-11-12

Owner-authorized acceptance on REDMAGIC 10 Pro serial `FY2433810B9D` used an isolated
`com.karim.aiprojectmanager.vnext` build and synthetic project/task/export data. The original
`com.karim.aiprojectmanager` package remained installed and untouched. Actual screens reviewed
include Dashboard, Project Overview, Blueprint, Roadmap, Tasks, Settings, GitHub public probe,
create/edit dialogs, export, read-only backup preview and Backlog preview in the listed locales.
Recovery/error states and authenticated GitHub discovery were inaccessible or unsafe to force;
see [physical acceptance report](UI_UX_VNEXT_PHYSICAL_ACCEPTANCE_2026-11-12.md) for evidence and
prioritized findings.

The review fixed shared dark-theme heading/empty-state contrast, row text width that had wrapped
progress/editor counts vertically, and English/Arabic Overview, project edit, tab and task status
strings. `:app:assembleVnext` succeeded after edits and `git diff --check` passed. No tests were run.
The result is **FAIL — CORRECTIONS REQUIRED** pending reference alignment and inaccessible-flow
review; this is not release acceptance. No commit or push.

## VNext UI correction phase 1 — 2026-11-12

The approved visual correction and localization work is recorded in
[the phase 1 correction report](UI_UX_VNEXT_CORRECTION_PHASE1_2026-11-12.md). The isolated
`com.karim.aiprojectmanager.vnext` build compiled successfully and passed focused GitHub auth and
connection unit tests (18 total); no full 101-test rerun or Android instrumentation suite was run.
Physical REDMAGIC 10 Pro checks used synthetic test data. The original
`com.karim.aiprojectmanager` installation and data remain untouched. No commit or push was made.
Arabic/English screen evidence is under `docs/audit-evidence/ui-vnext-correction-2026-11-12/`.
Authenticated repository discovery, recovery/error/process-death states and broader device/accessibility
coverage remain open. **NOT Ready for Release.**

## VNext Phase 1B — final visual alignment and manual language — 2026-11-12

Implemented an app-local Arabic/English selector under Settings → Appearance, English default,
persistent preference, and immediate RTL/LTR application without changing system locale. Tightened
the Tasks creation layout on normal-width screens while preserving narrow/enlarged-text behavior.
The isolated VNext unit suite passed all 103 tests, VNext APK assembled and package ID was verified.
Manual REDMAGIC 10 Pro checks confirmed bidirectional language changes, restart/update persistence,
and unchanged synthetic project data. The original app package was untouched. Visual comparison
against the six approved references rates all five main screens PARTIAL MATCH because the references
show additional artwork/metadata/actions and richer backdrop styling; details and evidence are in
[the Phase 1B report](UI_UX_VNEXT_PHASE1B_REPORT_2026-11-12.md). Authenticated GitHub, recovery,
process-death, and broader accessibility/device checks remain open. No commit or push. **NOT Ready
for Release.**

## GitHub session Phase 2A — 2026-11-13

Implemented Android Keystore AES-GCM persistence for GitHub sessions in VNext private
no-backup storage, startup token verification, safe retention on transient failures, and
credential deletion on disconnect. VNext build configuration now enforces a Client ID for
VNext tasks while leaving ordinary Debug builds unaffected. See
[the Phase 2A report](GITHUB_SESSION_PHASE2A_REPORT_2026-11-13.md).

Verification: all 112 VNext host unit tests passed; Debug, VNext, and VNext Android test APK
assembly passed. Empty Client ID behavior was checked for Debug and VNext. Twelve focused
instrumentation tests passed on REDMAGIC 10 Pro serial `FY2433810B9D` against only
`com.karim.aiprojectmanager.vnext`, covering encrypted persistence, HTTP classification, and
the sign-in UI. Both app package paths were verified afterward; the original package/data
were untouched. No live GitHub authorization was performed. Physical restart persistence and
authenticated repository access remain **NOT TESTED** until owner participation in the device
authorization flow. Work is uncommitted; no push. **NOT Ready for Release.**
