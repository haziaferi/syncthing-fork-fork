# Automatic config backup spec

Scope: the app writes backups of its configuration by itself, keeps a bounded number of them, and lets the user restore one, in the haziaferi/syncthing-fork-fork build of Syncthing-Fork.

History: `SPEC-history.md`, one line per change. It is never a source of current rules.

Kinds: O owner rule · D decided · I inherited · A assumed · Q open.

## Rule index

| ID | Name | Section |
|---|---|---|
| D-R01 | Jobs and how often | 1. Purpose and jobs |
| D-R02 | Feature in scope, interface by discussion | 1. Purpose and jobs |
| D-R03 | Included files | 2. The archive |
| D-R04 | Same layout as the manual export | 2. The archive |
| D-R05 | Encryption of the private key | 2. The archive |
| D-R06 | Settings file format | 2. The archive |
| D-R07 | Backing up while Syncthing runs | 2. The archive |
| A-R08 | Atomic config writes | 2. The archive |
| D-R09 | Backup folder | 3. Storage |
| D-R10 | Writing and checking a backup | 3. Storage |
| D-R11 | Retention | 3. Storage |
| D-R12 | File names | 3. Storage |
| D-R13 | Trigger | 4. When a backup runs |
| D-R14 | No backup during import or export | 4. When a backup runs |
| D-R15 | Off by default | 4. When a backup runs |
| D-R16 | Feature settings and status | 5. Status and restore |
| D-R17 | Settings and restore screen | 5. Status and restore |
| D-R19 | Password removed or changed | 6. Edge cases |
| D-R18 | Test plan | 7. Test plan |
| D-R20 | Off-device upload | Out of scope |
| D-R21 | Backing up synced data | Out of scope |

## Glossary

| Term | Meaning |
|---|---|
| automatic backup | an archive the app writes without the user asking, under the rules of this spec |
| manual export | the archive written by "Export config" in the Import & Export screen, `SyncthingService.exportConfig()` |
| backup folder | the folder automatic backups are written to, relative to shared storage |
| backup password | the value of `Constants.PREF_BACKUP_PASSWORD`, shared by manual export, import and automatic backups |
| included files | the files an automatic backup holds, listed in R03 |
| content hash | a SHA-256 over the included files' names and bytes, in a fixed order |
| shared storage | `Environment.getExternalStorageDirectory()`, usually `/storage/emulated/0` |

## 1. Purpose and jobs

A phone that is lost, reset or replaced takes its Syncthing configuration with it: the device ID, the folder and device list, and the app's own settings. Today the only protection is "Export config", which the user has to remember to press, so the newest export is often weeks old. Upstream users asked for unattended backups in researchxxl/syncthing-android#240 and #413; upstream answered "PR welcome" and closed both.

### D-R01 · Jobs and how often

**Rule:** The feature serves the jobs below, for the users and as often as each row says.

**Values:**

| Job | Who | How often | Done when |
|---|---|---|---|
| As a user, I always have a recent backup of my configuration without thinking about it | every user who turns the feature on | continuously, after each configuration change | the newest automatic backup holds the configuration as it was at most one debounce period ago |
| As a user moving to a new phone, I restore my configuration from a backup that left the old phone through my own sync or copy | a user replacing or resetting a phone | once per phone change | the new install shows the old device ID, folders and devices |
| As a user who made a bad change, I go back to an earlier configuration | a user who broke a setting | rarely | the chosen backup is imported and Syncthing runs on it |

**Evidence:** researchxxl/syncthing-android#240 (daily, weekly or monthly export, mirrored to a PC by Syncthing) and #413 (periodic export, user-chosen destination); read 2026-11-23.

**Acceptance:** none: the jobs are checked through R03 to R18.

**Since:** 2026-11-23 · builder · own words

### D-R02 · Feature in scope, interface by discussion

**Rule:** Automatic config backup is built in this fork. Its screens and wording are settled with the owner in the planned interface discussion before they are built.

**Acceptance:** none: a scope and working-practice decision.

**Since:** 2026-11-23 · owner · agreed rewording

## 2. The archive

### D-R03 · Included files

**Rule:** An automatic backup holds the files in the table below, and nothing else. Most notably, it never holds the index database.

**Why:** The index describes files on this phone. Restored onto a reset phone, it puts every folder into an error state (researchxxl/syncthing-android#210). It is also the largest item by far, and copying it consistently would mean stopping Syncthing.

**Values:**

| File | In the archive as | When |
|---|---|---|
| `config.xml` | `config.xml` | always |
| `cert.pem` | `cert.pem` | always |
| `key.pem` | `key.pem` | always |
| `https-cert.pem`, `https-key.pem` | the same names | both present on the device, else neither |
| app settings | `Constants.getSharedPrefsFile()` name | always, written by `ConfigArchive.writePrefs` |
| `index-v2/` | not included | never |

**Evidence:** `SyncthingService.java:872` lists the manual export's files, `index-v2` included. `ConfigImporter.java:128` treats the index as optional on import. Read 2026-11-23.

**Acceptance:** A unit test opens a fresh automatic backup and finds exactly the entries of this table for a device with and without the HTTPS pair, and no `index-v2/` entry.

**Not:** Including the index, as the manual export does: see Why.

**Since:** 2026-11-23 · builder · own words

### D-R04 · Same layout as the manual export

**Rule:** An automatic backup is a zip with the manual export's entry names and encryption. The existing import accepts it unchanged.

**Why:** One import path serves both, and the all-or-nothing import (merged 2026-11-23, `b32d7772`) already validates and rolls back.

**Acceptance:** A unit test passes a fresh automatic backup to `ConfigImporter.stage()` with the backup password and gets a `Staged` result with no problems.

**Since:** 2026-11-23 · builder · own words

### D-R05 · Encryption of the private key

**Rule:** Automatic backups can only be turned on while a backup password is set, and every automatic backup is AES-256 encrypted with it through `ConfigArchive.writeZip`. No Android Keystore entry is added for the password.

**Why:** An unencrypted backup in shared storage lets any app with storage access, or anyone holding a synced copy, take the device's identity. The password already sits in the app's private settings beside `key.pem` itself, so the Keystore would protect nothing that is not already exposed once that storage is read.

**Acceptance:** A unit test opens a fresh automatic backup without the password and fails, and opens it with the password and succeeds. Turning the feature on with no password set is refused.

**Not:** Unencrypted automatic backups: see Why.

**Since:** 2026-11-23 · owner · agreed rewording

### D-R06 · Settings file format

**Rule:** The app-settings entry is written with allow-listed Java serialization (`ConfigArchive.writePrefs`), the format the manual export writes and the import reads through `AllowListObjectInputStream`.

**Why:** One format keeps manual and automatic archives identical, and the allow-list has already closed the deserialization risk.

**Acceptance:** Covered by R04's test: the import reads the settings entry of a fresh automatic backup.

**Not:** JSON inside this feature: it would give the two kinds of archive different contents. A move to JSON is a separate change for both kinds together, and must still read the old format.

**Since:** 2026-11-23 · owner · agreed rewording

### D-R07 · Backing up while Syncthing runs

**Rule:** An automatic backup never stops, pauses or restarts Syncthing. It reads the included files while Syncthing runs, then checks the archive it wrote (R10) before keeping it.

**Why:** The manual export stops Syncthing for its whole run. A backup after every change must not interrupt syncing.

**Evidence:** `SyncthingService.java:853` shuts Syncthing down in `exportConfig()`. Not checked: that a reader never sees a half-written `config.xml` (R08).

**Acceptance:** A device test changes a folder setting with Syncthing running. The automatic backup that follows leaves the service state `ACTIVE` throughout, as the service's state log shows.

**Not:** Reusing `exportConfig()` as it is: it stops Syncthing and includes the index.

**Since:** 2026-11-23 · builder · own words

### A-R08 · Atomic config writes

**Rule:** Syncthing and `ConfigXml.saveChanges()` both replace `config.xml` by writing a temporary file and renaming it. So a reader sees either the old or the new file, never a mix.

**Evidence:** `ConfigXml.saveChanges()` writes a temp file, syncs it, and renames it (fixed in `c58699b9`). Not checked: the Syncthing binary's own config writer. R07 relies on this; R10's check catches a torn copy if it is wrong.

**Acceptance:** none: confirm by reading the Syncthing version's config save code before R07 is built.

**Since:** 2026-11-23 · builder · own words

## 3. Storage

### D-R09 · Backup folder

**Rule:** Automatic backups are written to `backups/syncthing/auto/` in shared storage, beside the manual export's default `backups/syncthing/config.zip`. The user can change it in settings as a path relative to shared storage, the way the manual export path works. A missing folder is created; a folder that cannot be written is a failure (R13).

**Why:** The folder survives an uninstall and a data wipe, the very events backups exist for, and the user can add it to a Syncthing folder to send backups off the phone (the pattern in researchxxl/syncthing-android#240). Other apps with storage access can read it, which is why R05 encrypts every backup.

**Acceptance:** A unit test with the default setting writes the backup under `backups/syncthing/auto/`, and with a changed setting under the new folder.

**Not:** App-private storage: it is deleted with the app's data. A folder picker (Storage Access Framework): the app already holds all-files access for syncing.

**Since:** 2026-11-23 · owner · agreed rewording

### D-R10 · Writing and checking a backup

**Rule:** A backup goes through the steps below in order, and stops at the first that fails. A file named `*.zip` in the backup folder is always a complete, checked backup.

**Values:**

| Step | Action | On failure |
|---|---|---|
| 1 | write the archive to `config-auto-<stamp>.zip.tmp` in the backup folder | delete the `.tmp` file, record a failure (R13) |
| 2 | run `ConfigArchive.check` on it with the backup password, and parse its `config.xml` | delete the `.tmp` file, record a failure (R13) |
| 3 | rename the `.tmp` file to `config-auto-<stamp>.zip` | delete the `.tmp` file, record a failure (R13) |
| 4 | apply retention (R11) | keep the new backup, record a failure (R13) |

**Acceptance:** Unit tests make step 2 fail (a truncated archive, a malformed `config.xml`). Each time, no new `.zip` appears and the existing backups are unchanged.

**Since:** 2026-11-23 · builder · own words

### D-R11 · Retention

**Rule:** The newest 5 automatic backups are kept. Pruning runs only after a new backup passes R10, deletes the oldest `config-auto-*.zip` files beyond 5 and `config-auto-*.zip.tmp` files older than one day, and never touches any other file.

**Why:** Too few backups lose the chance to go back before a bad change that was itself backed up. Too many fill a folder that Syncthing may also be syncing. Five backups take a few hundred KB.

**Acceptance:** A unit test with 6 backups, a day-old `.tmp` file and a manual `config.zip` in the folder writes a new backup. 5 backups remain, the oldest two and the `.tmp` file are gone, and `config.zip` is untouched. A failed backup deletes nothing.

**Not:** A user setting for the count: no request asked for one.

**Since:** 2026-11-23 · owner · agreed rewording

### D-R12 · File names

**Rule:** An automatic backup is named `config-auto-<stamp>.zip`. `<stamp>` is the local time the backup started, as `yyyyMMdd-HHmmss`. If that name exists, `-2`, `-3` and so on are added before `.zip`.

**Why:** Names that sort by time let retention and the restore list order backups without reading them. The `config-auto-` prefix keeps them apart from manual exports.

**Acceptance:** A unit test checks the name for a fixed clock, and a second backup in the same second gets `-2`.

**Since:** 2026-11-23 · builder · own words

## 4. When a backup runs

### D-R13 · Trigger

**Rule:** A backup runs 10 minutes after the last change, through a one-off `JobScheduler` job that each change schedules again, and is skipped when the content hash equals `auto_backup_last_hash`. A failure records its time and reason (R16) and leaves the existing backups untouched.

**Why:** A change trigger keeps the newest backup current without writing identical copies every day, and needs no interval setting. The 10-minute wait turns a burst of edits into one backup. The app already schedules work with `JobScheduler` (`JobUtils.java`).

**Values:**

| Event | Counts as a change |
|---|---|
| `ConfigSaved` event from Syncthing (`EventProcessor.java:113`) | yes |
| `ConfigXml.saveChanges()` while Syncthing is stopped | yes |
| a shared-preferences change | yes, except the status keys of R16 |
| a failure, from the table below | no |

| Failure | Recorded reason |
|---|---|
| backup folder missing and not creatable, or read-only | folder not writable |
| storage permission missing | no storage access |
| out of space while writing | out of space |
| R10's check fails | backup failed its check |

**Acceptance:** Unit tests: three changes within 10 minutes schedule one job; a run with an unchanged hash writes nothing; each failure row records its reason and leaves the existing backups as they were.

**Not:** A daily, weekly or monthly interval, as researchxxl/syncthing-android#240 and #413 asked: it leaves up to a full period of changes unprotected and writes identical copies. Backing up inside `ConfigXml.saveChanges()` itself: it runs on the caller's thread, often the UI.

**Since:** 2026-11-23 · owner · agreed rewording

### D-R14 · No backup during import or export

**Rule:** No automatic backup runs while an import, a pending rollback (`ConfigImporter.isRollbackPending()`) or a manual export is in progress. A job that finds one of these reschedules itself for 10 minutes later.

**Why:** A backup taken mid-import could capture a half-restored configuration and then prune a good backup in its favour.

**Acceptance:** A unit test with a pending rollback marker runs the backup job. It writes no file and schedules itself again.

**Since:** 2026-11-23 · builder · own words

### D-R15 · Off by default

**Rule:** The feature is off on a new install and stays off after an update. The user turns it on in the Import & Export screen, which first asks for a backup password if none is set (R05).

**Why:** It needs a password nobody has set yet, and it writes files to shared storage that the user did not ask for.

**Acceptance:** A unit test reads `auto_backup_enabled` on fresh preferences and gets false; no job is scheduled until it is turned on.

**Not:** On by default: it would protect users who never open the settings, but it would write unencrypted or unasked-for files.

**Since:** 2026-11-23 · owner · agreed rewording

## 5. Status and restore

### D-R16 · Feature settings and status

**Rule:** The feature keeps the settings below in the app's shared preferences. A backup excludes the keys marked as status, and an import never overwrites them, as it already does for the backup path and password (`ConfigArchive.java:295`).

**Values:**

| Key | Holds | Kind |
|---|---|---|
| `auto_backup_enabled` | on or off (R15) | setting |
| `auto_backup_rel_folder` | the backup folder (R09) | setting |
| `auto_backup_last_success` | time of the last good backup | status |
| `auto_backup_last_failure` | time and reason of the last failure | status |
| `auto_backup_last_hash` | content hash of the newest backup | status |

**Acceptance:** A unit test imports an archive whose settings hold the status keys, and the device's own status values are unchanged.

**Since:** 2026-11-23 · builder · own words

### D-R17 · Settings and restore screen

**Rule:** The Import & Export screen holds the items below. Their layout and wording are settled in the interface discussion (R02) before they are built.

**Why:** Restoring today means copying a backup onto the manual export path by hand.

**Values:**

| Item | Does |
|---|---|
| switch | turns the feature on or off (R15) |
| backup folder | shows and edits the folder (R09) |
| status line | "Last automatic backup: <date>", or the last failure's reason |
| "Restore automatic backup" | lists the backups newest first and imports the chosen one through the all-or-nothing import |
| notification | shown only while the feature is on and no backup has succeeded for 7 days with a change waiting |

**Acceptance:** On the device run (R18): turn the feature on, see the status line update after a change, and restore the second-newest backup from the list onto a cleared debug install.

**Since:** 2026-11-23 · owner · agreed rewording

## 6. Edge cases

### D-R19 · Password removed or changed

**Rule:** If the backup password is cleared while the feature is on, the feature turns itself off and records the reason as a failure. If the password changes, new backups use the new password, and older backups keep the old one.

**Acceptance:** Unit tests clear the password (the feature is off, and the failure reason names the password) and change it (the next backup opens only with the new password).

**Since:** 2026-11-23 · builder · own words

## 7. Test plan

### D-R18 · Test plan

**Rule:** Each level below passes before the feature's pull request is merged.

**Values:**

| Level | Covers | Rules | Runs |
|---|---|---|---|
| Unit (Robolectric) | contents, layout, write-check-rename, names, retention, skip-unchanged, import exclusion, password changes | R03 R04 R10 R11 R12 R13 R14 R16 R19 | every commit, in CI's `testDebugUnitTest` |
| Device (`.debug` build on the owner's phone, release untouched) | backup while syncing, restore onto a cleared debug install | R07 R17 | once before merge |

**Acceptance:** CI's Build / Build Debug check passes on the pull request, and the device run is logged in the plan's progress section.

**Since:** 2026-11-23 · builder · own words

## Acceptance list

- R03 · Included files: A unit test opens a fresh automatic backup and finds exactly the entries of this table for a device with and without the HTTPS pair, and no `index-v2/` entry.
- R04 · Same layout as the manual export: A unit test passes a fresh automatic backup to `ConfigImporter.stage()` with the backup password and gets a `Staged` result with no problems.
- R05 · Encryption of the private key: A unit test opens a fresh automatic backup without the password and fails, and opens it with the password and succeeds. Turning the feature on with no password set is refused.
- R06 · Settings file format: Covered by R04's test: the import reads the settings entry of a fresh automatic backup.
- R07 · Backing up while Syncthing runs: A device test changes a folder setting with Syncthing running. The automatic backup that follows leaves the service state `ACTIVE` throughout, as the service's state log shows.
- R09 · Backup folder: A unit test with the default setting writes the backup under `backups/syncthing/auto/`, and with a changed setting under the new folder.
- R10 · Writing and checking a backup: Unit tests make step 2 fail (a truncated archive, a malformed `config.xml`). Each time, no new `.zip` appears and the existing backups are unchanged.
- R11 · Retention: A unit test with 6 backups, a day-old `.tmp` file and a manual `config.zip` in the folder writes a new backup. 5 backups remain, the oldest two and the `.tmp` file are gone, and `config.zip` is untouched. A failed backup deletes nothing.
- R12 · File names: A unit test checks the name for a fixed clock, and a second backup in the same second gets `-2`.
- R13 · Trigger: Unit tests: three changes within 10 minutes schedule one job; a run with an unchanged hash writes nothing; each failure row records its reason and leaves the existing backups as they were.
- R14 · No backup during import or export: A unit test with a pending rollback marker runs the backup job. It writes no file and schedules itself again.
- R15 · Off by default: A unit test reads `auto_backup_enabled` on fresh preferences and gets false; no job is scheduled until it is turned on.
- R16 · Feature settings and status: A unit test imports an archive whose settings hold the status keys, and the device's own status values are unchanged.
- R17 · Settings and restore screen: On the device run (R18): turn the feature on, see the status line update after a change, and restore the second-newest backup from the list onto a cleared debug install.
- R19 · Password removed or changed: Unit tests clear the password (the feature is off, and the failure reason names the password) and change it (the next backup opens only with the new password).
- R18 · Test plan: CI's Build / Build Debug check passes on the pull request, and the device run is logged in the plan's progress section.

## Out of scope

### D-R20 · Off-device upload

**Rule:** The app does not upload backups anywhere. Getting them off the phone is left to the user, for example by syncing the backup folder with Syncthing itself.

**Why:** Cloud accounts and a network uploader do not belong in a sync app that already moves files between the user's own devices.

**Acceptance:** none: a scope boundary.

**Since:** 2026-11-23 · builder · own words

### D-R21 · Backing up synced data

**Rule:** Automatic backups cover the configuration only, never the files in synced folders or the index database.

**Why:** Synced data is recovered from the other devices. The index is excluded by R03.

**Acceptance:** none: a scope boundary, checked through R03's test.

**Since:** 2026-11-23 · builder · own words
