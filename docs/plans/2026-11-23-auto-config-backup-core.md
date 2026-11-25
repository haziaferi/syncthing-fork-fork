# Automatic config backup: core, implementation plan

**Goal:** while the feature is on, the app writes an encrypted, checked backup of its configuration about 10 minutes after each change, keeps the newest 5, and records its status. Nothing is visible on screen yet.
**Approach:** a new package `com.nutomic.syncthingandroid.backup` holds all the feature's logic in small classes that need no running Syncthing, so Robolectric can test them. A one-off `JobScheduler` job runs the backup off the main thread. Three hooks report changes: the `ConfigSaved` event, `ConfigXml.saveChanges()` and a shared-preferences listener. The archive is written with the manual export's `ConfigArchive.writeZip`, so the existing all-or-nothing import reads it unchanged.
**Stack:** Java (app code is Java, minSdk 23, no core-library desugaring), JUnit 4, Robolectric (`sdk = 37`), Truth, zip4j.
**Spec:** `docs/specs/auto-config-backup/SPEC.md` (R03 to R16, R19, R22). The screen items of R17 and the device run of R18 (R07, R17) belong to a second plan, written after the interface discussion (R02).
**Carry out with:** `dev-engineering-suite:plan-runner`; tick each step as it is done.

## Applies to every task

- Work on the branch `feat/auto-config-backup`, cut from `main` at `5eba5108`.
- Run Gradle as `ANDROID_HOME="$HOME/AppData/Local/Android/Sdk" ./gradlew --no-daemon ...` from the repository root. Send long output to `build-logs/`, never to the parent folder.
- Commits follow `CONTRIBUTING.md`: conventional commits in lower case, scope `backup` (or `spec`, `importexport` where named), `git commit -s`, and end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- No `java.time` and no `java.util.function` (both need API 24+ or desugaring, and lint flags them). Use `SimpleDateFormat` with `TimeZone.getTimeZone("UTC")` and `Locale.US`, and the `Clock` interface from Task 2.
- New production classes go in `app/src/main/java/com/nutomic/syncthingandroid/backup/`, their tests in `app/src/test/java/com/nutomic/syncthingandroid/backup/`. Tests run under `@RunWith(RobolectricTestRunner.class)` and `@Config(application = android.app.Application.class, sdk = 37)`, the setup `ConfigImporterTest` uses, with `TemporaryFolder` for files.
- File names inside the app's files folder come from `Constants`: `CONFIG_FILE`, `PUBLIC_KEY_FILE` (`cert.pem`), `PRIVATE_KEY_FILE` (`key.pem`), `HTTPS_CERT_FILE`, `HTTPS_KEY_FILE`, `SHARED_PREFS_FILE`, `INDEX_DB_FOLDER`.
- Name each test that proves a spec rule `<what>_<expected>` and put `// SPEC:Rnn` on the line above it, so speccheck can link the rule to its test.
- The automatic backup never calls `SyncthingService.exportConfig()` and never stops Syncthing (R07).
- Every successful `ConfigXml.saveChanges()` counts as a change, whether Syncthing runs or not. R13 names only the stopped case, but the 10-minute wait merges the extra notice with the `ConfigSaved` event that follows, so no extra backup results.

## Already failing before this plan

No tests were failing before this plan. `:app:testDebugUnitTest` on `main` (`5eba5108`), 2026-11-23: `BUILD SUCCESSFUL`, 97 tests, 7 skipped (the symbolic-link tests where the host cannot create links), 0 failures. The log is `build-logs/backup-plan-baseline.log`.

## Cases to watch

- `RunConditionMonitor.shutdown()` calls `JobUtils.cancelAllScheduledJobs`, which would also cancel a pending backup each time Syncthing stops. Task 7 limits it to the sync trigger job (test in Task 7).
- An empty `auto_backup_rel_folder` value falls back to the default folder, as the manual export does for its path (test in Task 2).
- `SharedPreferences.Editor.clear()` makes the listener fire with a `null` key, which counts as a change (test in Task 7).
- A change made while a backup is running still gets its own backup. The job clears the pending-change time when it starts, not when it ends (test in Task 7).
- `https-cert.pem` without `https-key.pem` puts neither file in the archive (test in Task 6).
- A manual `config.zip`, a `config-auto-` file with an unparsable stamp, or any other file in the backup folder is never deleted or counted (test in Task 3).
- The backup folder setting names an existing file instead of a folder: the backup fails with "folder not writable" (test in Task 6).
- The automatic backup writes its settings file into its own work folder, so a manual export running at the same moment cannot delete it mid-write (test in Task 6).
- Doze and app-standby can delay any job beyond its latency. The 60-minute limit is enforced on the latency the job is scheduled with; the tests check that value, not when Android runs it.

## Questions for you

- **Does the core land as its own pull request?** Planned: yes. It is dormant until the second plan adds the switch, since nothing can turn it on. Waiting would keep it on the branch until the interface discussion is done and the screen is built, and the R18 device run would then cover one larger PR.
- **R13 needs two additions, both owner-decided rules:**
  1. Step 4 of R10 (retention fails) records a failure, but R13's failure table has no reason for it. Planned, provisionally: `RETENTION_FAILED`, shown as "old backups could not be removed".
  2. As written, R13 skips a backup whenever the content hash equals `auto_backup_last_hash`, even if the user has since deleted every backup in the folder. Planned, provisionally: skip only when the folder also still holds at least one automatic backup.

  Saying yes adds both rows to R13 in Task 1. Saying no drops the `RETENTION_FAILED` reason (Task 2) and the folder check before the skip (Task 6).

---

### Task 1: Settle R08 in the spec

**Files:** change `docs/specs/auto-config-backup/SPEC.md` and `SPEC-history.md`
**Depends on:** none
**Provides:** R08 as an inherited rule (`I-R08`) with its evidence

- [ ] Rewrite R08 through the spec's "Changing a rule" steps:
  - prefix `A-` becomes `I-`, and the Rule text stays;
  - **Evidence:** Syncthing v2.1.7 (submodule `d6d0181f`) saves through `osutil.CreateAtomic` (`lib/config/wrapper.go:508`), which writes a temporary file, syncs it and renames it over `config.xml` (`lib/osutil/atomic.go:78-126`). `ConfigXml.saveChanges()` does the same since `c58699b9`. Checked 2026-11-23;
  - **Acceptance:** `none: inherited behaviour of the Syncthing binary and the app; R10's check catches a torn copy if a later version changes it.`;
  - remove the "Not checked: ... (R08)" sentence from R07's Evidence.

  Add one history row: `- 2026-11-23 · R08 · kind · A → I, checked in Syncthing v2.1.7 · builder`. If the owner said yes to the R13 question, also add the two R13 rows (reason `retention failed` for step 4, and the skip only while a backup exists), with a history row `R13 · changed · ... · owner`.
- [ ] Run speccheck with `--write-index`, then the plain check with `--base HEAD`:

  ```bash
  SC="<dev-engineering-suite plugin>/skills/project-spec-writer/scripts/speccheck.py"
  python "$SC" docs/specs/auto-config-backup/SPEC.md --write-index
  python "$SC" docs/specs/auto-config-backup/SPEC.md --base HEAD
  ```

  Expected: no BLOCK or REPORT, and `open questions: 0`. An ASK on R13 is expected only when R13 changed, and the owner's yes answers it.
- [ ] Commit both files: `docs(spec): confirm atomic config writes in syncthing v2.1.7`.

### Task 2: Feature settings, status and runtime keys

**Files:** create `backup/Clock.java`, `backup/AutoBackupPrefs.java`; test in `backup/AutoBackupPrefsTest.java`
**Depends on:** none
**Provides:**
- `interface Clock { long nowMillis(); Clock SYSTEM = System::currentTimeMillis; }`
- `final class AutoBackupPrefs`, all static:
  - constants `KEY_ENABLED = "auto_backup_enabled"`, `KEY_REL_FOLDER = "auto_backup_rel_folder"`, `KEY_LAST_SUCCESS = "auto_backup_last_success"` (a long, epoch ms), `KEY_LAST_FAILURE = "auto_backup_last_failure"` (a string `<epoch ms>;<reason name>`), `KEY_LAST_HASH = "auto_backup_last_hash"`, `DEFAULT_REL_FOLDER = "backups/syncthing/auto"`;
  - `Set<String> STATUS_KEYS`: the last three keys;
  - `enum FailureReason { FOLDER_NOT_WRITABLE, NO_STORAGE_ACCESS, OUT_OF_SPACE, CHECK_FAILED, PASSWORD_REMOVED, RETENTION_FAILED }`;
  - `static final class Failure { public final long timeMillis; public final FailureReason reason; }`;
  - `boolean isRuntimeKey(@Nullable String key)`;
  - `boolean isEnabled(SharedPreferences prefs)`;
  - `boolean setEnabled(SharedPreferences prefs, boolean on)`;
  - `File backupFolder(SharedPreferences prefs, File sharedStorageRoot)`;
  - `void recordSuccess(SharedPreferences prefs, long timeMillis, String hash)`;
  - `void recordFailure(SharedPreferences prefs, long timeMillis, FailureReason reason)`;
  - `@Nullable Failure lastFailure(SharedPreferences prefs)`.

- [ ] Write the tests first. Each takes fresh default shared preferences (`clear().commit()` in `@Before`):
  - `// SPEC:auto-config-backup:R15` `isEnabled_freshPrefs_isFalse`.
  - `// SPEC:auto-config-backup:R05` `setEnabled_withoutPassword_isRefused`: returns false, and `KEY_ENABLED` stays unset. With `PREF_BACKUP_PASSWORD = "pw"` set it returns true, and `isEnabled` is true.
  - `// SPEC:auto-config-backup:R22` `isRuntimeKey_coversEveryRuntimeKey`: true for:
    - `PREF_EVENT_PROCESSOR_LAST_SYNC_ID`, `PREF_LAST_RUN_TIME`, `PREF_APP_START_COUNTER`;
    - `PREF_CACHE_DEVICE_LASTSEEN_PREFIX + "ABC-123"`;
    - `PREF_DEBUG_FACILITIES_AVAILABLE`, `PREF_LAST_BINARY_VERSION`, `PREF_BTNSTATE_FORCE_START_STOP`;
    - the three status keys.

    False for `PREF_RUN_ON_WIFI`, `PREF_BACKUP_PASSWORD`, `KEY_ENABLED`, `KEY_REL_FOLDER` and `null`.
  - `// SPEC:auto-config-backup:R09` `backupFolder_defaultAndChanged`: with no value it is `new File(root, "backups/syncthing/auto")`; with `"x/y"` it is `new File(root, "x/y")`; with `""` it is the default.
  - `recordFailure_roundTrips`: it records time `1234` with `OUT_OF_SPACE`, and `lastFailure` returns the same pair. A stored value `"1234;NO_SUCH_REASON"` or `"garbage"` reads as `null`.
- [ ] Run `./gradlew --no-daemon :app:testDebugUnitTest --tests '*AutoBackupPrefsTest'`. Expected: compilation fails with `cannot find symbol: class AutoBackupPrefs`.
- [ ] Write `Clock` and `AutoBackupPrefs` as listed. Write each value with `commit()`, not `apply()`, so a job that ends right after still saves it.
- [ ] Run the same command. Expected: `BUILD SUCCESSFUL`, 5 tests, 0 failures.
- [ ] Commit: `feat(backup): add automatic backup settings and status keys`.

### Task 3: Backup names, order and retention

**Files:** create `backup/BackupFolder.java`; test in `backup/BackupFolderTest.java`
**Depends on:** none
**Provides:** `final class BackupFolder`, all static:
- constants `PREFIX = "config-auto-"`, `SUFFIX = ".zip"`, `TMP_SUFFIX = ".tmp"`, `KEEP = 5`, `TMP_MAX_AGE_MS = 24 * 60 * 60 * 1000L`;
- `String stamp(long utcMillis)`, format `yyyyMMdd'T'HHmmss'Z'` in UTC;
- `File nextFile(File folder, long utcMillis)`: the first of `config-auto-<stamp>.zip`, `config-auto-<stamp>-2.zip`, `-3`, ... for which neither the `.zip` nor its `.zip.tmp` exists;
- `List<File> list(File folder)`: only names matching `config-auto-\d{8}T\d{6}Z(-[2-9]|-[1-9]\d+)?\.zip`, newest first, by the parsed stamp and then the counter (none counts as 1), never by raw name;
- `boolean prune(File folder, long nowMillis)`: deletes `list(folder)` beyond `KEEP`, and `config-auto-*.zip.tmp` files whose last-modified time is older than `TMP_MAX_AGE_MS`. Returns false if any deletion failed.

- [ ] Write the tests first:
  - `// SPEC:auto-config-backup:R12` `stamp_fixedClock`: `stamp(1763900000000L)` is `"20251123T121320Z"`.
  - `// SPEC:auto-config-backup:R12` `nextFile_sameSecond_getsSuffixAndOrdersAfter`: after creating `nextFile(f, t)`, `nextFile(f, t)` ends in `-2.zip`, and `list` puts `-2` first (newest).
  - `// SPEC:auto-config-backup:R12` `list_localClockBackAnHour_newestStillFirst`: write a backup at `t` with the default `TimeZone` set to `Europe/Berlin` in summer time, then one at `t + 30 min` after switching the default zone to `Etc/GMT-1`, so local time reads earlier. The second backup is still first in `list` and survives `prune`. Restore the default zone in `@After`.
  - `// SPEC:auto-config-backup:R11` `prune_keepsNewestFive_andOnlyItsOwnFiles`. Setup:
    - 6 backups at increasing stamps;
    - a `config-auto-…zip.tmp` with last-modified 25 hours before `now`;
    - a fresh `.tmp` (1 minute old);
    - `config.zip`, `notes.txt` and `config-auto-bad.zip`.

    After `prune(folder, now)`: the 5 newest backups remain, the oldest is gone, the old `.tmp` is gone, and the fresh `.tmp`, `config.zip`, `notes.txt` and `config-auto-bad.zip` are untouched. The 7th backup, which R11's acceptance counts, is the new backup that Task 6's test writes.
  - `list_missingFolder_isEmpty`.
- [ ] Run `./gradlew --no-daemon :app:testDebugUnitTest --tests '*BackupFolderTest'`. Expected: compilation fails with `cannot find symbol: class BackupFolder`.
- [ ] Write `BackupFolder` as listed.
- [ ] Run the same command. Expected: `BUILD SUCCESSFUL`, 5 tests, 0 failures.
- [ ] Commit: `feat(backup): name, order and prune automatic backups`.

### Task 4: Content hash

**Files:** create `backup/ContentHash.java`; test in `backup/ContentHashTest.java`
**Depends on:** Task 2 (`AutoBackupPrefs.isRuntimeKey`)
**Provides:** `final class ContentHash` with `static String of(List<File> files, Map<String, ?> prefs) throws IOException`, which returns 64 lowercase hex characters (SHA-256).

The hash input is fixed so that the same content always gives the same hash:
- **Files:** for each file in list order, feed its name as UTF-8, a zero byte, its length as 8 big-endian bytes, then its bytes.
- **Settings:** then feed the settings, leaving out every key for which `isRuntimeKey` is true, sorted by key. For each entry, feed `key`, `\0`, the value's simple class name, `\0`, the value, `\n`. A string set is written as its items sorted, each followed by `\0`.

- [ ] Write the tests first:
  - `of_sameContent_sameHash`: the same files, and settings maps with the same entries inserted in different orders, give equal hashes.
  - `// SPEC:auto-config-backup:R22` `of_runtimeKeyChange_sameHash`: changing or adding `last_sync_id`, `device_lastseen_X` and `auto_backup_last_hash` leaves the hash unchanged.
  - `of_userSettingOrFileChange_newHash`: changing `run_on_wifi`, one byte of `config.xml`, or `true` to the string `"true"` changes the hash.
- [ ] Run `./gradlew --no-daemon :app:testDebugUnitTest --tests '*ContentHashTest'`. Expected: compilation fails with `cannot find symbol: class ContentHash`.
- [ ] Write `ContentHash.of`.
- [ ] Run the same command. Expected: `BUILD SUCCESSFUL`, 3 tests, 0 failures.
- [ ] Commit: `feat(backup): hash backup content without runtime keys`.

### Task 5: Import keeps the device's backup status

**Files:** change `util/ConfigArchive.java`, `util/ConfigImporter.java`; tests in `util/ConfigArchiveTest.java`, `util/ConfigImporterTest.java`
**Depends on:** Task 2 (`AutoBackupPrefs.STATUS_KEYS`)
**Provides:**
- `ConfigArchive.applyPrefs(SharedPreferences.Editor editor, Map<String, Object> importable, String keepRelPathToZip, String keepPassword, Map<String, ?> keepStatus)`, which re-puts `keepStatus` after the import's settings;
- `ConfigArchive.CACHE_PREFS` also holds `STATUS_KEYS`, so `importablePrefs` drops them;
- `public static List<String> ConfigImporter.configProblems(File config)`: the problems `checkConfig` finds, as an empty list when there are none. `stage()` keeps using it.

- [ ] Write the tests first:
  - `// SPEC:auto-config-backup:R16` `ConfigImporterTest.commit_keepsDeviceBackupStatus`. The device has `auto_backup_last_success = 5L` and `auto_backup_last_hash = "device"`. The staged archive's settings hold `auto_backup_last_success = 9L`, `auto_backup_last_hash = "archive"`, `auto_backup_last_failure = "1;CHECK_FAILED"` and `run_on_wifi = false`. After `commit`:
    - the device's two status values are unchanged;
    - `auto_backup_last_failure` is absent;
    - `run_on_wifi` is false.
  - `ConfigArchiveTest.importablePrefs_dropsStatusKeys`.
  - `ConfigImporterTest.configProblems_validAndMalformed`: `VALID_CONFIG` gives an empty list; `"<configuration>"` gives one problem.
  - Change the two existing `applyPrefs` calls in `ConfigArchiveTest` (lines 324 and 358) to pass `Collections.emptyMap()` as the last argument.
- [ ] Run `./gradlew --no-daemon :app:testDebugUnitTest --tests '*ConfigArchiveTest' --tests '*ConfigImporterTest'`. Expected: compilation fails (`applyPrefs` takes 4 arguments; `configProblems` not found).
- [ ] Make the changes. In `ConfigImporter.commit`, read the current values of `STATUS_KEYS` from `prefs.getAll()` before `applyPrefs` and pass them as `keepStatus`. The rollback snapshot is taken as today.
- [ ] Run the same command. Expected: `BUILD SUCCESSFUL`, 0 failures.
- [ ] Commit: `fix(importexport): keep the device's backup status on import`.

### Task 6: Writing one backup

**Files:** create `backup/AutoBackupWriter.java`; test in `backup/AutoBackupWriterTest.java`
**Depends on:** Tasks 2, 3, 4, 5
**Provides:** `final class AutoBackupWriter` with:
- `public interface ZipWriter { void write(File target, List<File> paths, String password) throws IOException; }`, whose default is `ConfigArchive::writeZip`;
- `public enum Outcome { WRITTEN, SKIPPED_UNCHANGED, FAILED }`;
- constants `WORK_DIR = "auto-backup-work"` (in the files folder), `MIN_FREE_BYTES = 1024 * 1024`, `REQUIRED_ENTRIES = [CONFIG_FILE, PUBLIC_KEY_FILE, PRIVATE_KEY_FILE]`;
- `public AutoBackupWriter(File filesDir, File sharedStorageRoot, SharedPreferences prefs, Clock clock, boolean haveStorageAccess, ZipWriter zipWriter)`;
- `public Outcome run()`;
- `static boolean isOutOfSpace(IOException e)`: true when the message contains `ENOSPC` or `No space left`.

`run()` takes these steps in order and stops at the first failure. Each failure calls `recordFailure` with the reason shown, deletes the `.tmp` file and the work folder, and returns `FAILED`; existing backups are never touched.

| Step | Action | Reason on failure |
|---|---|---|
| 1 | backup password empty: `setEnabled(prefs, false)` | `PASSWORD_REMOVED` |
| 2 | `haveStorageAccess` is false | `NO_STORAGE_ACCESS` |
| 3 | resolve `backupFolder`, `mkdirs()`; it must then be a writable directory | `FOLDER_NOT_WRITABLE` |
| 4 | included files (R03): `config.xml`, `cert.pem`, `key.pem`, then `https-cert.pem` and `https-key.pem` only if both exist; then the settings copy in `filesDir/auto-backup-work/<SHARED_PREFS_FILE>`, written by `ConfigArchive.writePrefs` from `prefs.getAll()` minus `STATUS_KEYS` | `FOLDER_NOT_WRITABLE` if the copy cannot be written |
| 5 | `ContentHash.of(files without the settings copy, prefs.getAll())` equals `KEY_LAST_HASH` and `BackupFolder.list` is not empty: return `SKIPPED_UNCHANGED`, writing nothing | (none) |
| 6 | `folder.getUsableSpace() < MIN_FREE_BYTES` | `OUT_OF_SPACE` |
| 7 | `target = BackupFolder.nextFile(folder, now)`; `zipWriter.write(<target>.tmp, files, password)` | `OUT_OF_SPACE` if `isOutOfSpace(e)`, else `FOLDER_NOT_WRITABLE` |
| 8 | `ConfigArchive.check(tmp, password, REQUIRED_ENTRIES, workDir) == OK`, then extract `config.xml` into the work folder and `ConfigImporter.configProblems` is empty | `CHECK_FAILED` |
| 9 | rename `.tmp` to `target` | `FOLDER_NOT_WRITABLE` |
| 10 | `recordSuccess(prefs, now, hash)` | (none) |
| 11 | `BackupFolder.prune(folder, now)`; on false, `recordFailure(RETENTION_FAILED)` but still return `WRITTEN` | `RETENTION_FAILED` |

The work folder is deleted when `run()` returns, on every path.

- [ ] Write the tests first. A helper fills `filesDir` with `ConfigImporterTest.VALID_CONFIG`, `cert.pem`, `key.pem` and an `index-v2/` folder holding one file. It sets the backup password `"pw"` and enables the feature. Shared storage is a temporary folder.
  - `// SPEC:auto-config-backup:R03` `run_archiveHoldsExactlyTheIncludedFiles`: entries are `{config.xml, cert.pem, key.pem, <SHARED_PREFS_FILE>}`, with no `index-v2/` entry. With both HTTPS files added there are 6 entries. With only `https-cert.pem` there are 4. Read entry names with zip4j `getFileHeaders()`.
  - `// SPEC:auto-config-backup:R04` `run_backupStagesThroughImport`: `ConfigImporter.stage(backup, "pw", otherFilesDir)` returns a `Staged` whose `prefs` holds `run_on_wifi` as set (R06) and no status key.
  - `// SPEC:auto-config-backup:R05` `run_backupNeedsThePassword`: zip4j reports the archive as encrypted with AES-256. Extracting it without a password throws `ZipException`. `ConfigArchive.check` with `"pw"` gives `OK`. (A `check` with `""` is not a valid test of this: it rejects only an unencrypted archive when a password is set, not the reverse.)
  - `// SPEC:auto-config-backup:R09` `run_writesIntoConfiguredFolder`: the default folder is under `backups/syncthing/auto/`; after setting `KEY_REL_FOLDER = "elsewhere"`, the next backup is under `elsewhere/`.
  - `// SPEC:auto-config-backup:R10` `run_truncatedArchive_keepsExistingBackups`: a `ZipWriter` writes the real zip and then truncates it to half its length. Result `FAILED`, reason `CHECK_FAILED`, no new `.zip`, no `.tmp` left, and earlier backups are byte-identical.
  - `// SPEC:auto-config-backup:R10` `run_malformedConfig_keepsExistingBackups`: the same, with `config.xml` = `"<configuration>"`.
  - `// SPEC:auto-config-backup:R11` `run_withSixBackups_leavesFiveAndOwnFilesOnly`: as in `BackupFolderTest`, plus the run itself. 5 backups remain, the newest is the new one, the day-old `.tmp` file is gone, and `config.zip` is untouched. A run that fails at step 8 deletes nothing.
  - `// SPEC:auto-config-backup:R13` `run_unchangedContent_writesNothing`: the second `run()` with no change returns `SKIPPED_UNCHANGED`, and the folder listing is unchanged. After deleting every backup, a third `run()` returns `WRITTEN`.
  - `// SPEC:auto-config-backup:R13` `run_failureRows_recordReasonAndKeepBackups`, one case each:
    - the folder setting points at an existing file: `FOLDER_NOT_WRITABLE`;
    - `haveStorageAccess = false`: `NO_STORAGE_ACCESS`;
    - a `ZipWriter` throwing `IOException("write failed: ENOSPC (No space left on device)")`: `OUT_OF_SPACE`;
    - the truncating writer: `CHECK_FAILED`.

    Each case leaves the earlier backup byte-identical.
  - `// SPEC:auto-config-backup:R19` `run_emptyPassword_turnsFeatureOff`: result `FAILED`, reason `PASSWORD_REMOVED`, `isEnabled` false.
  - `// SPEC:auto-config-backup:R19` `run_changedPassword_newBackupUsesNewPassword`: back up with `"pw"`, change to `"pw2"`, change a setting, back up again. The new archive passes `check` with `"pw2"` only; the older one still passes with `"pw"`.
  - `run_concurrentManualExportFile_isLeftAlone`: a file at `filesDir/<SHARED_PREFS_FILE>` (where the manual export writes) exists before and after `run()`, unchanged.
- [ ] Run `./gradlew --no-daemon :app:testDebugUnitTest --tests '*AutoBackupWriterTest'`. Expected: compilation fails with `cannot find symbol: class AutoBackupWriter`.
- [ ] Write `AutoBackupWriter` as specified above.
- [ ] Run the same command. Expected: `BUILD SUCCESSFUL`, 12 tests, 0 failures.
- [ ] Commit: `feat(backup): write and check one automatic backup`.

### Task 7: Trigger, job and hooks

**Files:**
- create `backup/AutoBackupScheduler.java`, `backup/AutoBackupGate.java`, `backup/AutoBackupJob.java`, `backup/AutoBackupJobService.java`;
- change `util/JobUtils.java`, `service/RunConditionMonitor.java`, `service/EventProcessor.java`, `util/ConfigXml.java`, `SyncthingApp.java`, `service/SyncthingService.java`, `AndroidManifest.xml`;
- tests in `backup/AutoBackupSchedulerTest.java` and `backup/AutoBackupJobTest.java`.

**Depends on:** Tasks 2 and 6
**Provides:**
- `AutoBackupScheduler`, all static:
  - constants `JOB_ID = 1001`, `DEBOUNCE_MS = 10 * 60 * 1000L`, `MAX_WAIT_MS = 60 * 60 * 1000L`, `STATE_PREFS = "auto_backup_state"` (a private preferences file, never backed up and never watched), `KEY_FIRST_CHANGE = "first_change_at"`;
  - `long delayMillis(long now, long firstChangeAt)`, which is `max(0, min(now + DEBOUNCE_MS, firstChangeAt + MAX_WAIT_MS) - now)`;
  - `void noteChange(Context context, Clock clock)`: does nothing while the feature is off. Otherwise it sets `first_change_at` to now if it is 0, then schedules `JOB_ID` with `setMinimumLatency(delayMillis(...))` and `setPersisted(true)`, which replaces any pending one;
  - `void scheduleIn(Context context, long delayMs)`;
  - `void cancel(Context context)`, which cancels `JOB_ID` and clears `first_change_at`;
  - `void clearPendingChange(Context context)`;
  - `void onPreferenceChanged(Context context, SharedPreferences prefs, @Nullable String key, Clock clock)`;
  - `void register(Context appContext)`, which keeps the listener in a static field because `SharedPreferences` holds listeners weakly.
- `AutoBackupGate`: `static void beginManualOperation()`, `static void endManualOperation()` (an `AtomicInteger` counter), `static boolean isBlocked(File filesDir)`. It is true while the counter is above 0 or `ConfigImporter.isRollbackPending(filesDir)`.
- `AutoBackupJob`: `enum Result { DISABLED, DEFERRED, WRITTEN, SKIPPED_UNCHANGED, FAILED }` and `static Result execute(Context context, Clock clock, AutoBackupWriter.ZipWriter zipWriter)`. In order:
  - feature off: `cancel`, then `DISABLED`;
  - `isBlocked`: `scheduleIn(DEBOUNCE_MS)`, then `DEFERRED` (R14);
  - otherwise `clearPendingChange`, then `new AutoBackupWriter(context.getFilesDir(), Environment.getExternalStorageDirectory(), defaultPrefs, clock, PermissionUtil.haveStoragePermission(context), zipWriter).run()`, mapped to its `Result`.
- `AutoBackupJobService extends JobService`: `onStartJob` runs `execute(..., Clock.SYSTEM, ConfigArchive::writeZip)` on a new background thread, calls `jobFinished(params, false)` and returns true. `onStopJob` returns false.
- `JobUtils.SYNC_TRIGGER_JOB_ID = 0` and `JobUtils.cancelSyncTriggerJob(Context)`. `scheduleSyncTriggerServiceJob` uses the constant.

`onPreferenceChanged` rules, first match wins:

| Key | Action |
|---|---|
| `PREF_BACKUP_PASSWORD`, new value empty, feature on | write `KEY_ENABLED = false`, `recordFailure(PASSWORD_REMOVED)`, `cancel` (R19) |
| `KEY_ENABLED`, now true | `noteChange` |
| `KEY_ENABLED`, now false | `cancel` |
| `isRuntimeKey(key)` | nothing (R22) |
| any other key, `null` included | `noteChange` (which does nothing while off) |

- [ ] Write the tests first. Use a fake `Clock` with a settable time. Pending jobs come from `context.getSystemService(JobScheduler.class).getAllPendingJobs()`, filtered to `JOB_ID`.
  - `// SPEC:auto-config-backup:R13` `threeChangesWithinTenMinutes_scheduleOneJob`: changes at 0, 3 and 6 minutes leave one pending job with latency 10 minutes.
  - `// SPEC:auto-config-backup:R13` `changeEveryFiveMinutes_runsWithinSixtyMinutes`: changes every 5 minutes from 0 to 55. After each, the job's latency plus the change time is at most 60 minutes, and after the change at 55 it is exactly 60.
  - `// SPEC:auto-config-backup:R15` `featureOff_changeSchedulesNothing`; then `setEnabled(true)` with a password schedules a job.
  - `// SPEC:auto-config-backup:R22` `runtimeKeyChange_schedulesNothing`: with the feature on and nothing pending, writing each R22 key (a status key and a `device_lastseen_` key among them) schedules nothing. Writing `PREF_RUN_ON_WIFI` schedules the job.
  - `clearAll_countsAsChange`: `onPreferenceChanged(..., null, ...)` schedules the job.
  - `// SPEC:auto-config-backup:R19` `passwordCleared_turnsFeatureOff`: the feature is off, the pending job is cancelled, and `lastFailure().reason` is `PASSWORD_REMOVED`.
  - `cancelSyncTriggerJob_leavesBackupJob`: with both jobs pending, `JobUtils.cancelSyncTriggerJob` leaves `JOB_ID` pending.
  - `// SPEC:auto-config-backup:R14` `AutoBackupJobTest.pendingRollback_writesNothingAndReschedules`: with `filesDir/import-rollback/COMMIT_IN_PROGRESS` present, `execute` returns `DEFERRED`, the backup folder is empty, and a job is pending with latency 10 minutes. The same holds inside `beginManualOperation()` … `endManualOperation()`.
  - `AutoBackupJobTest.changeDuringRun_getsItsOwnBackup`: a `ZipWriter` that calls `noteChange` before writing. After `execute`, `first_change_at` is that change's time and a job is pending.
- [ ] Run `./gradlew --no-daemon :app:testDebugUnitTest --tests '*AutoBackupSchedulerTest' --tests '*AutoBackupJobTest'`. Expected: compilation fails with `cannot find symbol: class AutoBackupScheduler`.
- [ ] Write the four classes and wire the hooks:
  - `RunConditionMonitor.shutdown()` calls `JobUtils.cancelSyncTriggerJob(mContext)` in place of `cancelAllScheduledJobs`; remove `cancelAllScheduledJobs` if nothing else calls it;
  - `EventProcessor`'s `ConfigSaved` case calls `AutoBackupScheduler.noteChange(mContext, Clock.SYSTEM)` after `reloadConfig()`;
  - `ConfigXml.saveChanges()` calls it after the temp file has been renamed over `config.xml`, and only then;
  - `SyncthingApp.onCreate()` calls `AutoBackupScheduler.register(this)` after the existing crash recovery;
  - `SyncthingService.exportConfig()` and `importConfig()` wrap their bodies in `AutoBackupGate.beginManualOperation()` / `try … finally endManualOperation()`;
  - the manifest declares `.backup.AutoBackupJobService` with `android:permission="android.permission.BIND_JOB_SERVICE"` and `android:exported="false"`, next to `SyncTriggerJobService`.
- [ ] Run the same command. Expected: `BUILD SUCCESSFUL`, 9 tests, 0 failures.
- [ ] Run `./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug > build-logs/backup-task7.log 2>&1`. Expected: `BUILD SUCCESSFUL`, the baseline count plus this plan's new tests, 0 failures, and no new lint errors (a `NewApi` error means a `java.time` or `java.util.function` use slipped in).
- [ ] Commit: `feat(backup): schedule automatic backups after changes`.

## Proof for the whole plan

- `./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug` ends in `BUILD SUCCESSFUL`, with the baseline tests plus about 40 new ones and 0 failures.
- `grep -rn "SPEC:R" app/src/test/java/com/nutomic/syncthingandroid/` lists at least one test each for R03, R04, R05, R09, R10, R11, R12, R13, R14, R15, R16, R19 and R22, the unit row of R18.
- speccheck on the spec reports no BLOCK or REPORT.
- Not in this plan, carried to the second plan: R17's screen items, a `SyncthingService.importConfig(File)` overload for restoring a chosen backup, and the R18 device run (R07, R17).

## Progress

- Run started 2026-11-24 on `feat/auto-config-backup` from `5eba5108`, inline mode.
- User decided (2026-11-24): the core lands as its own PR; both R13 additions accepted.
- Task 1: done — commit 84f1c075 — speccheck --base HEAD → 0 BLOCK, 1 ASK (R13, owner-approved), 0 REPORT
- Task 2: done — commit 4b1ec18d — :app:testDebugUnitTest --tests '*AutoBackupPrefsTest' → BUILD SUCCESSFUL, 5 tests, 0 failures
- Task 3: done — commit e5540e6f — --tests '*BackupFolderTest' → BUILD SUCCESSFUL, 5 tests, 0 failures
- Task 4: done — commit d2ae803c — --tests '*ContentHashTest' → BUILD SUCCESSFUL, 3 tests, 0 failures
- Task 5: done — commit 50fa5db8 — --tests '*ConfigArchiveTest' --tests '*ConfigImporterTest' → BUILD SUCCESSFUL, 22 + 27 tests, 0 failures
- Task 6: done — commit 21ba7d82 — --tests '*AutoBackupWriterTest' → BUILD SUCCESSFUL, 13 tests (plan's 12 + isOutOfSpace), 0 failures. Plan gap settled: an unreadable included file while hashing (e.g. config.xml missing) records CHECK_FAILED. ConfigImporter.deleteQuietly made public for the work folder.
- Task 7: done — commit b8f3c67e — --tests '*AutoBackupSchedulerTest' --tests '*AutoBackupJobTest' → 8 + 3 tests, 0 failures; :app:testDebugUnitTest :app:lintDebug → BUILD SUCCESSFUL, 137 tests, 7 skipped, 0 failures. Deviations: all five cancelAllScheduledJobs calls in RunConditionMonitor (not only shutdown) now cancel only the sync trigger job, and cancelAllScheduledJobs is removed; AutoBackupJob gained a package-private execute(..., haveStorageAccess) overload because Robolectric cannot grant MANAGE_EXTERNAL_STORAGE; 11 tests instead of 9 (delayMillis_neverNegative, featureOff_returnsDisabledAndCancels).
- Final review: 0 Critical, 1 High, 3 Medium, 4 Low. H1 (wall clock set back pruned the new backup while recording success) fixed in 5477d04c, test failed first; R12 updated. Full run → BUILD SUCCESSFUL, 139 tests, 7 skipped, 0 failures; speccheck clean. Open: M1 (schedule() during a run stops the running job on a device), M2 (R14 gate checked once; an import starting mid-run can mix old and new keys), M3 (a failed run drops the pending change), L1-L4.
- Review fixes M1-M3: done — commit db0c24e1, 5 new tests failed first; :app:testDebugUnitTest :app:lintDebug → BUILD SUCCESSFUL, 143 tests, 7 skipped, 0 failures; speccheck clean. L2 (unsynchronized first_change_at) fixed with it. A failed run is not retried on its own (no loop); the next change runs it at once. Still open: L1 (main-thread commit and JobScheduler call), L3 (ShareActivity key not in R22), L4 (clear() on API 23-29).
- Gap fixes (owner: fix all four): done — commit 96f38045; share keys added to R22, apply() for first_change_at, full R22 hash test, injected clock. 143 tests, 7 skipped, 0 failures; lint and speccheck clean. Pushed to PR #6.
