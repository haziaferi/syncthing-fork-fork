# Automatic config backup spec history

Not a source of current rules: `SPEC.md` is. One line per change, newest at the bottom. Retired wording is in git.

- 2026-11-23 · R01 · added · jobs and how often · builder
- 2026-11-23 · R02 · added · in scope, interface by discussion · owner
- 2026-11-23 · R03 · added · included files, no index · builder
- 2026-11-23 · R04 · added · same layout as manual export · builder
- 2026-11-23 · R05 · added · key encryption question · builder
- 2026-11-23 · R06 · added · settings format question · builder
- 2026-11-23 · R07 · added · back up without stopping Syncthing · builder
- 2026-11-23 · R08 · added · atomic config writes, unchecked · builder
- 2026-11-23 · R09 · added · backup folder question · builder
- 2026-11-23 · R10 · added · write, check, rename · builder
- 2026-11-23 · R11 · added · retention question · builder
- 2026-11-23 · R12 · added · file names · builder
- 2026-11-23 · R13 · added · trigger question · builder
- 2026-11-23 · R14 · added · no backup during import or export · builder
- 2026-11-23 · R15 · added · default on or off question · builder
- 2026-11-23 · R16 · added · feature settings and status keys · builder
- 2026-11-23 · R17 · added · settings and restore screen question · builder
- 2026-11-23 · R18 · added · test plan · builder
- 2026-11-23 · R19 · added · password removed or changed · builder
- 2026-11-23 · R20 · added · no off-device upload · builder
- 2026-11-23 · R21 · added · no synced data · builder
- 2026-11-23 · R05 · kind · Q → D, required password, AES-256, no Keystore · owner
- 2026-11-23 · R06 · kind · Q → D, keep allow-listed Java serialization · owner
- 2026-11-23 · R09 · kind · Q → D, backups/syncthing/auto/ in shared storage · owner
- 2026-11-23 · R11 · kind · Q → D, keep newest 5 · owner
- 2026-11-23 · R13 · kind · Q → D, 10 min after last change, skip unchanged · owner
- 2026-11-23 · R15 · kind · Q → D, off by default · owner
- 2026-11-23 · R17 · kind · Q → D, items in Import & Export screen · owner
- 2026-11-23 · R04 · changed · acceptance names the ImportException · builder
- 2026-11-23 · R12 · changed · UTC stamp, order by parsed stamp · builder
- 2026-11-23 · R13 · changed · 60-minute limit, runtime keys ignored · owner
- 2026-11-23 · R22 · added · runtime preference keys · builder
- 2026-11-23 · R18 · changed · unit tests cover R05 R09 R15 R22 · builder
- 2026-11-24 · R08 · kind · A → I, checked in Syncthing v2.1.7 · builder
- 2026-11-24 · R13 · changed · retention failure reason, skip only while a backup exists · owner
- 2026-11-24 · R12 · changed · name no earlier than the newest backup, wall clock can run back · builder
