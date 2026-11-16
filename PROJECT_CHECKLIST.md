# AI Project Manager — Project Checklist

This file is a human-reviewable status snapshot for the Android companion app.
Update it when work is completed and verified; do not mark tasks done based solely on commits.

## Existing capabilities

- [x] GitHub account sign-in and session persistence (previous emulator acceptance)
- [x] Repository browsing and local import (previously tested with accessible repositories)
- [x] Project tabs and manual task editing (existing implementation)
- [x] Repository commit, Issues, and Actions snapshot display (implemented and covered by focused tests)

## GitHub checklist integration

- [x] Parse Markdown checklist tasks and prevent local task overwrites (implementation committed)
- [x] Fetch checklist and recent Issues with a README fallback (implementation committed; live acceptance pending)
- [x] Preview and apply new task suggestions inside the Android Tasks tab (implementation committed; live acceptance pending)
- [ ] Confirm GitHub Actions build and Android instrumentation tests pass
- [ ] Confirm live checklist import, persistence, and refresh on an accessible repository
- [ ] Complete physical-device acceptance and visual review

Checked items describe implementation or prior evidence as qualified in parentheses, not a release certificate.
