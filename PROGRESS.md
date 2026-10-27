# Earmark v0.1 build log

Start: Tue Oct 27 11:27:46 IST 2026 (budget 3 h, feature freeze 2:30 → 13:57, end 14:27)

- 11:35 Phase 0 gate: grill answered (14 decisions), GLOSSARY.md + 10 ADRs written. Cut: nothing.
- 12:05 Phase 1 gate (+8 min over: Gradle and Google Maven downloads ran at ~0.1–0.2 MB/s per connection, so I fetched the Gradle zip with parallel range requests; Compose 1.12 needed compileSdk 37). Works: :core:test, minified release APK installs and launches on the API 36 emulator. Tagged scaffold. Cut: nothing.
