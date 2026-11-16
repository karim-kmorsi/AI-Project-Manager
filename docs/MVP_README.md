# AI Project Manager — Android MVP 0.1

This is the **first source-code scaffold**, not a tested APK. It is a standalone multi-project Android application, independent of KARIM OS.

## Implemented in source
- Local creation and selection of multiple projects.
- Per-project Blueprint and Roadmap editable as text.
- Per-project task list (create tasks, review statuses).
- Verified progress is calculated only for explicitly verified tasks.
- JSON storage in app-private files. No cloud, account, or GitHub access.
- Arabic-first Compose interface.

## Safety and limitations
- This MVP does **not** inspect repositories, execute terminal commands, invoke AI agents, or claim real verification.
- The `VERIFIED` status is deliberately not available through the MVP's quick-status buttons; future evidence-based verifier must control it.
- Data is stored locally without encryption; do not enter credentials.
- No automatic backups or migration yet. Do not rely on this build as a production source of truth.
- No permissions for internet, filesystem-wide scanning, or command execution.

## Open in Android Studio
1. Open the repository folder.
2. Allow Gradle sync to resolve dependencies; if Android SDK 35 is missing, install it using SDK Manager.
3. Run on an emulator or Android device.

Gradle and Android dependency versions have **not been compiled or integration-tested** in the present environment.
Future architecture: Room + structured Blueprint / Roadmap models, evidence ledger, GitHub read-only adapter, CI results, and a permission-limited local inspector.
