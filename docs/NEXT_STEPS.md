# Plan — AI Project Manager

## Core accepted decisions
- Independent multi-project manager, **not** specific to KARIM OS.
- Blueprint + Roadmap per project, with tasks and evidence.
- Local-first. GitHub is the source of code truth, while the app stores planning/decision records.
- A passing code-generation response is not verified completion.
- No unauthorized push, publishing, deletion, paid API, or remote agent execution.

## MVP 0.1
Functional source scaffold with multiple projects, blueprint, roadmap, and task state. Local JSON storage only.

## Next engineering milestone
1. Test compilation on Android Studio; record exact results.
2. Replace JSON with Room, schema migrations, entity relationships and backup.
3. Add evidence entity linked to task + commit + test run, before any VERIFIED transition.
4. Define repository-sync interface with read-only OAuth/App permission strategy.
5. Later add CI/build observer and optional Ubuntu companion.
6. Threat-model and privacy review before any agent command execution.

## Critical open questions
- Package/application identity and brand name remain provisional.
- GitHub authentication model and private-repo access have not been selected.
- Remote AI tool support must be checked per provider.
- Numerical progress must reflect demonstrated evidence, not guessed percentages.
