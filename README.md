# AI Project Manager

AI Project Manager is an Android productivity app for managing multi-project work, tasks, roadmap planning, and GitHub-backed project context in one place.

## Stable release

- Version: 0.2.0
- Build: 5
- Package: com.karim.aiprojectmanager
- Status: released locally and visually accepted on the REDMAGIC 10 Pro physical device
- Approved splash footer: "AI Project Manager v0.2.0" / "© Karim S.K. Morsi 2026"

## Implemented functionality

- Multi-project task tracking with local persistence and recovery safeguards
- Project, roadmap, and blueprint editing with confirmation before destructive replacement
- GitHub import and connection flows for repository-aware project context
- Appearance and language settings with documented compatibility requirements
- Startup safety checks and retry flow for incomplete project model initialization

## Repository and release notes

- Current branch: feature/vnext3
- Current scope and release restrictions: [v0.2 release gate](docs/RELEASE_GATE_V0_2.md)
- Current implementation and verification notes: [Project status](docs/PROJECT_STATUS.md)
- Current priorities and deferred work: [Reconstructed roadmap](docs/ROADMAP.md)
- Contributor safety instructions: [Reconstructed AGENTS.md](AGENTS.md)

## Build and installation

1. Install Android SDK and JDK 17.
2. Ensure local.properties points to the installed Android SDK.
3. Build the release artifact:

```bash
./gradlew --offline --no-daemon --console=plain :app:assembleRelease
```

4. Use the signed release APK for the approved physical device installation. Do not treat this as a full production guarantee without the formal device and signing checks noted in the project status documentation.

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

Verified locally in this workspace:

- `./gradlew --no-daemon --console=plain :app:testDebugUnitTest` completed successfully.
- Release-oriented build and repository checks were performed only where required for the approved release workflow.

Unverified without separate device or signing acceptance work:

- Full physical-device compatibility beyond the approved installation
- Full production readiness claims
- Complete automated coverage of all release gates
