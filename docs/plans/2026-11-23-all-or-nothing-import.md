# All-or-nothing config import: implementation plan

**Goal:** a config import either replaces the whole configuration or changes nothing (upstream issue researchxxl/syncthing-android#374). Every problem in an archive is reported in a single attempt, and an import interrupted by a crash is rolled back the next time the service starts.
**Approach:** a new `ConfigImporter` (in `util`, no `Context`) does the work in three phases:
1. **Stage:** extract the zip into `filesDir/import-staging/`.
2. **Validate:** check the staged files completely, without touching anything live. All problems are collected into one list.
3. **Commit:** move each live file aside into `filesDir/import-rollback/`, move each staged file into place, and apply the prefs. If any step fails, put everything back, prefs included.

A marker file written before the first move lets `SyncthingService.onCreate` finish an interrupted rollback. `SyncthingService.importConfig()` keeps its signature and stays in charge of the precheck, shutting down the core and restarting it.
**Stack:** Java (app module), JUnit 4 + Truth + Robolectric (SDK 37), zip4j, as set up by `docs/plans/2026-11-22-unit-test-foundation.md`.
**Spec:**
- The in-scope list agreed on 2026-11-22: "all-or-nothing config import" comes first, because automatic backup builds on it.
- The "Expected" section of #374: "A failed import should be a no-op. Either validate the archive's contents before replacing anything, or roll back on failure."
- #374's smaller point: list every missing item in one error.

**Carry out with:** `dev-engineering-suite:plan-runner`; tick each step as it is done.

## Applies to every task

- **Live files** are the six items an export writes: `config.xml`, `cert.pem`, `key.pem`, `https-cert.pem`, `https-key.pem` and `index-v2/`. The `sharedpreferences.dat` inside an archive is never a live file; it only feeds the prefs.
- Every move in the commit phase is `File.renameTo` within `filesDir`, which is one filesystem, so each move is atomic. A live file is always moved away before a staged file takes its place, which also works on Windows test hosts. No test needs the POSIX skip.
- `ConfigImporter` takes the `filesDir`, the zip, the password and a `SharedPreferences`. It never stops or starts Syncthing; that stays in `SyncthingService`.
- Unit tests use a `TemporaryFolder` as `filesDir`, and Robolectric only where a real `SharedPreferences` is needed. Configs and keys in tests are small fixture files: validation checks structure, not cryptography.
- No UI changes in this plan (UI work is being discussed separately). The import screen keeps its existing success and failure toasts; the full problem list goes to the log, which the failure toast already points to.
- Commits: conventional style (`feat(...)`, `fix(...)`, `test(...)`), each signed off with `git commit -s`. Work on a branch from `main` and land it through a PR; the `main` ruleset requires one.
- Run Gradle as `ANDROID_HOME="$HOME/AppData/Local/Android/Sdk" ./gradlew --no-daemon ...`. Push with `GIT_TERMINAL_PROMPT=0 GCM_INTERACTIVE=never timeout 90 git push ...`, so a hidden sign-in prompt fails fast instead of hanging.

## Already failing before this plan

No tests are failing on `main` at `c58699b9` (70 tests, 7 POSIX-only tests skipped on Windows).

## Cases to watch

- **The #374 archive.** A zip holding only `config.xml`, `cert.pem` and `key.pem` is accepted, and the current HTTPS pair is kept, matching the reporter's working workaround (provisional, see Question 1). The app's identity is replaced and nothing else breaks.
- **Missing required files.** A zip missing `cert.pem` **and** `key.pem` is rejected, both names appear in one problem list, and every live file stays byte-for-byte the same.
- **A bad `config.xml`.** Malformed, empty, or missing `<gui>` or `<options>`: rejected before anything changes, which today's import never checks.
- **Half an HTTPS pair.** `https-cert.pem` without `https-key.pem`, or the other way round: rejected, because the pair must match.
- **A rejected prefs file** (a class outside the allow-list, or not a map): the import is rejected before anything changes, and today's partial apply no longer happens.
- **A failure part-way through the commit** (for example the third move fails): every live file is back, byte-for-byte, and the prefs equal their snapshot.
- **A crash part-way through the commit:** `import-rollback/` holds a `COMMIT_IN_PROGRESS` marker and a prefs snapshot. On the next service start the files and prefs are restored before Syncthing runs, and the staging and rollback folders are deleted. The marker is written after the snapshot, so a marker never exists without one.
- **Leftovers from an earlier import.** A stale `import-staging/` is deleted before extraction, and a stale `import-rollback/` without the marker is deleted at start.
- **Index database.** It is replaced by the archive's `index-v2/` when present. Otherwise the live index is still moved out, so Syncthing starts with an empty index, as today's import does.
- **Prefs file absent.** The current prefs are kept except `PREF_LOCAL_DEVICE_ID`, which is removed, as today.
- **A zip entry that escapes the staging folder** (`../x`): rejected. Check every staged path's canonical form against the staging folder.

## Questions for you

1. **Archive without an HTTPS pair.** Planned (provisional): keep the current `https-cert.pem` / `https-key.pem`, as #374's working workaround does. The alternative is to delete them so Syncthing generates a fresh pair on its next start, which is always consistent but changes the certificate. That would change one branch of `ConfigImporter.commit` and one test.
2. **Import while Syncthing can't be stopped.** Today `shutdown(State.DISABLED)` is called with no check. Planned: unchanged. Gating the import on a confirmed stop would add a step to Task 4.

---

### Task 1: Stage and validate an archive without touching live files

**Files:** create `app/src/main/java/com/nutomic/syncthingandroid/util/ConfigImporter.java`; test in `app/src/test/java/com/nutomic/syncthingandroid/util/ConfigImporterTest.java`
**Depends on:** none
**Provides:**
- `static final String STAGING_DIR = "import-staging"`, `ROLLBACK_DIR = "import-rollback"`, `COMMIT_MARKER = "COMMIT_IN_PROGRESS"`
- `final class Staged { File dir; boolean hasHttpsPair; boolean hasIndex; @Nullable Map<String, Object> prefs; }`. `prefs` is the already-read and allow-listed map, or null when the archive has no prefs file.
- `static final class ImportException extends Exception { List<String> problems; }`
- `static Staged stage(File zip, String password, File filesDir) throws ImportException`: deletes any old staging folder, extracts into a fresh one (through `ConfigArchive.extractAll`), then validates. It throws with **every** problem found, and deletes the staging folder when it throws.

Validation rules, each adding one line to `problems`:
- a missing `config.xml`, `cert.pem` or `key.pem` (one line per file);
- a `config.xml` that doesn't parse as XML, or whose root isn't `<configuration>`, or that has no `<gui>` or no `<options>`;
- exactly one of `https-cert.pem` / `https-key.pem` present;
- a `sharedpreferences.dat` that `ConfigArchive.readPrefs` rejects (the exception message is included);
- any staged path whose canonical path is outside the staging folder.

- [ ] Write the tests first. Each builds a zip with `ConfigArchive.writeZip` from fixture files in a `TemporaryFolder`.
  - `stage_fullExport_isValid`: all six items plus prefs; `hasHttpsPair`, `hasIndex`, and `prefs` holds the written map.
  - `stage_threeFileArchive_isValid` (the #374 archive): `hasHttpsPair == false`, `prefs == null`.
  - `stage_missingCertAndKey_reportsBothInOneError`: `problems` contains both file names.
  - `stage_malformedConfig_rejected` and `stage_configWithoutGui_rejected`.
  - `stage_halfHttpsPair_rejected`.
  - `stage_disallowedPrefsClass_rejected`: a prefs file holding an `ArrayList`.
  - `stage_neverTouchesLiveFiles`: put sentinel live files in `filesDir`, stage a bad archive, and check every sentinel is byte-identical and no `import-staging/` is left.
  - `stage_removesStaleStagingDir`.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests '*ConfigImporterTest'`. Expected: compilation fails with `cannot find symbol: class ConfigImporter`.
- [ ] Write `ConfigImporter.stage` and the types above. For XML, use `DocumentBuilderFactory` with external entities and DTDs disabled (`disallow-doctype-decl` set to true).
- [ ] Run the same command. Expected: all pass.
- [ ] Commit: `feat(importexport): stage and validate a config archive before importing`.

### Task 2: Commit with rollback

**Files:** change `ConfigImporter.java` and `ConfigImporterTest.java`
**Depends on:** Task 1
**Provides:**
- `interface Mover { boolean move(File from, File to); }`. Production passes `File::renameTo`; tests pass one that fails on the Nth call.
- `static void commit(Staged staged, File filesDir, SharedPreferences prefs, Mover mover) throws ImportException`.

The commit phase, in order:
1. Take a snapshot of `prefs.getAll()`.
2. Create `import-rollback/`, write the snapshot there as `prefs-snapshot.dat` with `ConfigArchive.writePrefs`, then write `COMMIT_IN_PROGRESS` last.
3. For each live item the import replaces, move the live item, if it exists, into `import-rollback/`, then move the staged item into place. The items replaced are: config, cert, key, `index-v2/` (always moved out; moved in only if `hasIndex`), and the HTTPS pair only if `hasHttpsPair`.
4. Apply the prefs: if `staged.prefs != null`, use `ConfigArchive.applyPrefs` with the current backup path and password; otherwise remove `PREF_LOCAL_DEVICE_ID`.
5. Delete the marker, then `import-rollback/` and `import-staging/`.

On any failure in steps 3–4:
- move back every item already moved, in reverse order;
- restore the prefs snapshot (`clear()`, then typed puts, then `commit()`);
- delete the marker and `import-staging/`;
- throw `ImportException` naming the failed step.

- `static boolean recoverInterruptedImport(File filesDir, SharedPreferences prefs)`:
  - **Marker present:** move every item in `import-rollback/` back to `filesDir`, replacing whatever is there. Restore the prefs from `prefs-snapshot.dat`, read with `ConfigArchive.readPrefs`, so it goes through the allow-list. Then delete both folders and return true.
  - **No marker:** delete any stale folders and return false.

- [ ] Write the tests first:
  - `commit_fullExport_replacesEveryLiveItemAndPrefs`
  - `commit_threeFileArchive_keepsHttpsPairAndPrefs_removesLocalDeviceId`
  - `commit_failsOnThirdMove_restoresEveryLiveFileByteForByte`: compare a hash of every live file before and after.
  - `commit_failsWhileApplyingPrefs_restoresFilesAndPrefs`: use a `SharedPreferences` whose editor's `commit()` returns false. This is a small fake that wraps the Robolectric prefs.
  - `commit_success_leavesNoStagingOrRollbackDir`
  - `recover_withMarker_restoresRollbackItemsAndPrefs`: lay out a rollback folder by hand with the marker, a prefs snapshot and changed live files; afterwards the files and the prefs equal the snapshot.
  - `recover_withoutMarker_deletesStaleDirs_returnsFalse`
- [ ] Run `./gradlew :app:testDebugUnitTest --tests '*ConfigImporterTest'`. Expected: compilation fails with `cannot find symbol: method commit`.
- [ ] Implement `commit` and `recoverInterruptedImport`.
- [ ] Run the same command. Expected: all pass.
- [ ] Commit: `feat(importexport): commit a staged import with rollback`.

### Task 3: Use the importer in `SyncthingService`

**Files:** change `app/src/main/java/com/nutomic/syncthingandroid/service/SyncthingService.java`
**Depends on:** Task 2
**Uses:** `ConfigImporter.stage`, `commit`, `recoverInterruptedImport`, `ImportException`

- [ ] In `importConfig()`, keep the `ConfigArchive.check` precheck. Then:
  - call `stage(zip, password, getFilesDir())` **before** shutting the core down. On `ImportException`, log every problem with `Log.e`, one line each, and return false without stopping Syncthing.
  - Only after staging succeeds: shut down as today, then call `commit(staged, getFilesDir(), mPreferences, File::renameTo)`. On `ImportException`, log, then restart if `mLastDeterminedShouldRun` (the old config is back in place) and return false.
  - On success: `cleanupImportedFolderDatabases()` and the restart, as today.
  - Delete the old in-place extraction, the delete-index-first step, the after-the-fact file check, and `importConfigSharedPrefs`; they are superseded.
- [ ] In `onCreate()`, before anything reads `config.xml`, call `ConfigImporter.recoverInterruptedImport(getFilesDir(), mPreferences)` and log a warning when it returns true.
- [ ] Run `./gradlew :app:testDebugUnitTest :app:lintDebug`. Expected: `BUILD SUCCESSFUL`, all tests pass, no new lint errors.
- [ ] Commit: `fix(importexport): make config import all-or-nothing (#374)`.

### Task 4: Device check

**Files:** none
**Depends on:** Task 3, and the PR's CI-built debug APK

- [ ] Install the CI debug APK on the OnePlus 6T as `com.github.catfriend1.syncthingfork.debug`. Check the package name with `aapt2 dump badging` first, so the release app is never replaced. Point the debug app's backup path at `backups/syncthing-debug-test/config.zip` before any export.
- [ ] Check these on the device, reading `filesDir` with `run-as`:
  1. A full export, then an import: it succeeds, and the debug app restarts and reaches "Syncthing is running" when forced to start.
  2. A three-file archive (config, cert, key taken from a second export): it succeeds, the device ID changes to the archive's, the HTTPS pair is unchanged, and the web GUI loads (no `BAD_SIGNATURE` in logcat).
  3. An archive missing `key.pem`: the import fails, the log lists the problem, and `config.xml`, `cert.pem` and `key.pem` are unchanged (hashes taken before and after with `run-as ... sha256sum`).
- [ ] Uninstall the debug app, delete `backups/syncthing-debug-test/`, and confirm the release app's version and `lastUpdateTime` are unchanged.

## Proof for the whole plan

- `./gradlew :app:testDebugUnitTest :app:lintDebug` ends in `BUILD SUCCESSFUL`, and CI on the PR is green.
- `SyncthingService.importConfig` no longer calls `ConfigArchive.extractAll` on `getFilesDir()`.
- The three device checks in Task 4 pass.

## Progress

- Run started 2026-11-23 from `c58699b9` on branch `feat/all-or-nothing-import`, inline mode. User approved both provisional choices ("Run the plan").
- Task 1: done — commit fc3c0ec0 — `--tests '*ConfigImporterTest'` → 10 tests, 0 failures. `Constants.INDEX_DB_FOLDER` made public for the importer. On Android the parser may not support `disallow-doctype-decl`; that case is caught and logged (the Android parser doesn't load external entities).
- Task 2: done — commit fab4af5c — `:app:testDebugUnitTest` → 88 tests, 7 skipped, 0 failures. Extra test: `commit_noPreviousHttpsPair_thenFailure_removesTheNewPair` (absent-item markers). Planted bug (rollback skipped) caught by all 3 rollback tests, after making the live config in one test distinct (it hid a swapped config). Added `ConfigArchive.restorePrefs`.
- Task 3: done — commit 230b5c44 — `:app:testDebugUnitTest :app:lintDebug` → BUILD SUCCESSFUL, 87 tests, 7 skipped, 0 failures. Deviations: crash recovery runs in `SyncthingApp.onCreate`, not `SyncthingService.onCreate`, because activities read config.xml through `ConfigRouter` too; `ConfigArchive.importPrefs` and its 2 tests were removed (no caller left) and replaced by `restorePrefs` + a stronger commit test. `SyncthingService` no longer calls `extractAll`.
- Branch review (fresh reviewer, c58699b9..230b5c44): 1 Critical, 1 High, 3 Medium, 5 Low. Fixed in 6f53949a:
  - C1: symlink entries were extracted (zip4j default, confirmed in its bytecode), and the delete followed them into filesDir.
  - H1: a failed restore deleted the only saved copy.
  - M1: a stale marker could undo a good import later.
  - M2: a failed prefs restore discarded the snapshot.
  - M3: tests that couldn't fail.
  - L3: `<gui>`/`<options>` must be direct children.
  - Also: a new import is refused while a rollback is pending.
  - The new symlink test caught that canonical-path link detection doesn't work on Windows hosts; on API 26+ `Files.isSymbolicLink` is used instead.
  - `:app:testDebugUnitTest :app:lintDebug` → BUILD SUCCESSFUL, 94 tests, 7 skipped, 0 failures.
  - Left open: L1 (an unreadable snapshot can't be retried), L2 (no fsync), L4 (the Android XML parser's entity-expansion limit can't be determined from here), L5 (`hasIndex` kept; it is used by tests).
- Re-review of 6f53949a: every finding fixed; new N1 (Medium: Syncthing restarted on a half-restored config) and N2 (Low: pending-rollback check came too late, wrong advice) fixed in f537979f. Gate is in `launchStartupTask`, the single start path; not unit-tested (service-level), covered by `isRollbackPending` tests. `:app:testDebugUnitTest :app:lintDebug` → 94 tests, 7 skipped, 0 failures. The symlink tests ran on this Windows host (not skipped).
- CI on f537979f: 94 tests, 0 failures, 0 skipped on Linux (including both symlink tests). All 6 checks pass.
- Task 4: done, 2026-11-23, on the OnePlus 6T. The CI debug build was installed as `.debug`; the release app (2.1.6.0, last updated 2026-10-14) was untouched.
  1. Full export → import (identity A): succeeds; no staging or rollback folders left; Syncthing runs. Logcat shows the Android parser lacks `disallow-doctype-decl`, and the fallback works.
  2. 3-file archive (A's config/cert/key) into a fresh install (identity B): config, cert and key now equal A's (so the device ID changed), the HTTPS pair stays B's, the web GUI came online (STARTING → ACTIVE), and there were 0 BAD_SIGNATURE/SSL errors.
  3. Archive missing `key.pem`: refused at the precheck (`MISSING_REQUIRED_ENTRY`); the live file hashes are unchanged.
  - Debug app uninstalled; test backup folder removed.
- Pre-merge gap check (user asked "Fix both"): R8 release minify passes locally (IS_COPILOT=true). Fixed: precheck extracted cert.pem with symlinks on (crash on API 23-25) and logged only the first missing file (red, then green); migration wiki note updated (no more 'error but it worked'; HTTPS pair optional). `:app:testDebugUnitTest :app:lintDebug` → 97 tests, 7 skipped, 0 failures.
