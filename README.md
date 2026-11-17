# AI Project Manager

AI Project Manager is an Android productivity app for managing multi-project work, tasks, roadmap planning, and GitHub-backed project context in one place.

## Current local candidate — v0.3.0

- Version: **0.3.0**, versionCode **7**, build variant `localCandidate`.
- Package: `com.karim.aiprojectmanager.vnext` (separate from the original app).
- Non-debuggable APK, without Debug-only test activities/providers.
- Signed with the existing **Android Debug certificate**. This is an owner-test
  candidate, **not an approved production signing configuration**.
- Local packaging is complete with documented limits. REDMAGIC 10 Pro installation
  and compatibility with its actual installed signer have **not** been verified.
- Current evidence, artifact identity, deferred checks and installation rules:
  [v0.3.0 local candidate report](docs/releases/v0.3.0/LOCAL_CANDIDATE.md).

The historical v0.2.0/build 5 record below is not acceptance of this new candidate.

## Implemented functionality

- Multi-project task tracking with local persistence and recovery safeguards
- Project, roadmap, and blueprint editing with confirmation before destructive replacement
- GitHub import and connection flows for repository-aware project context
- Appearance and language settings with documented compatibility requirements
- Startup safety checks and retry flow for incomplete project model initialization

## Repository and release notes

- Current branch: `main`
- Current candidate: [v0.3.0 evidence and limits](docs/releases/v0.3.0/LOCAL_CANDIDATE.md)
- Historical requirements: [v0.2 release gate](docs/RELEASE_GATE_V0_2.md)
- Current implementation and verification notes: [Project status](docs/PROJECT_STATUS.md)
- Current priorities and deferred work: [Reconstructed roadmap](docs/ROADMAP.md)
- Contributor safety instructions: [Reconstructed AGENTS.md](AGENTS.md)

## Build and installation

Use the existing JDK 17, Android SDK and cached Gradle 8.13. Supply the existing
GitHub App Client ID as the `githubAppClientId` Gradle property for the process
only (for example, `ORG_GRADLE_PROJECT_githubAppClientId` in its environment).
Do not commit that machine configuration or use a placeholder for this candidate.

```bash
./gradlew --offline --no-daemon :app:assembleLocalCandidate :app:lintLocalCandidate
```

This variant requires the existing local signing key and refuses to create a
replacement when it is missing. Its output is
`app/build/outputs/apk/localCandidate/app-localCandidate.apk`. The verified
candidate is retained separately under `/home/karim/Backups/AI-Project-Manager/`;
see the candidate report before rebuilding or installing.

Do not assume `assembleRelease` creates a signed APK. Do not install Debug over
the original app. A same-package update requires the actual installed certificate
to match and a compatible version code; never uninstall or clear data to bypass
an Android signature/version rejection. Phone installation requires owner approval.

## GitHub integration

The app includes GitHub connection and import logic for project context and repository-aware workflows. GitHub authentication and app-client setup remain environment-specific and must be validated separately before production distribution.

## Known limitations

- The app is not treated as fully production-ready without the remaining release-gate acceptance checks.
- Startup, language, device lifecycle, and backup/restore behavior require explicit acceptance validation on target devices.
- GitHub authorization and release distribution depend on the configured credentials and repository state.

## Release history

- v0.2.0 (Build 5): final approved release for the personal REDMAGIC 10 Pro installation, matching the accepted splash and stable package configuration.

## Future development

- Continue release-gate acceptance work on startup/language lifecycle behavior and device-side validation.
- Keep project storage and restore protections intact while improving operational reliability.
- Extend GitHub and project-import flows only with explicit scope review.

## Verification status

- CI at `d1fba747`, run `44120662731` attempt 2: **226 Android tests passed,
  6 skipped, 0 failed/errors**, verified from the original JUnit XML.
- Isolated local upgrade and real DocumentsUI export/restore acceptance passed.
- Current candidate packaging, signature, metadata and targeted runtime checks
  are recorded in the [candidate report](docs/releases/v0.3.0/LOCAL_CANDIDATE.md).
- Skipped tests remain deferred; physical-device upgrade, production signing and
  unrestricted production-readiness claims are not established.
