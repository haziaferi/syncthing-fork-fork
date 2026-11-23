# Unit test foundation: implementation plan

**Goal:** the app gets its first JVM unit tests, covering the three areas the upcoming work changes: the run-condition decision, `ConfigXml`, and config import/export. CI runs them on every build. Three small fixes the tests expose land with them.
**Approach:** add JUnit 4, Truth and Robolectric. Test `ConfigXml` as it is, under Robolectric. For run conditions and import/export, first move the decision logic and the zip/prefs handling out into small classes with no service or `Context` dependency, without changing behaviour, then test those classes directly. These classes are the seams the later work plugs into: the network-callback rewrite only replaces how `DeviceConditions` is filled in, and the all-or-nothing import and automatic backup build on `ConfigArchive`. Then make three deliberate fixes, each in its own commit with its own test:
- bad interval prefs fall back to their defaults (Task 4)
- `ConfigXml.saveChanges()` never replaces `config.xml` after a failed write (Task 5)
- imported prefs are read with a class allow-list (Task 7)

**Stack:** Java 21 and Kotlin 2.4.21, AGP 9.5.0, compileSdk/targetSdk 37, minSdk 23. Tests: JUnit 4.13.2, Truth 1.4.5, Robolectric 4.18.1, androidx.test:core 1.7.0.
**Spec:** the scope agreed with the owner on 2026-11-22:
- tests for the run-condition logic, `ConfigXml` and import/export
- a unit-test step in `common-build.yaml`
- Robolectric at SDK 37
- the interval fallback and the `saveChanges()` fix
- an allow-list for prefs deserialisation (the format stays the same; JSON is deferred to the automatic-backup work)

**Carry out with:** `dev-engineering-suite:plan-runner`; tick each step as it is done.

## Applies to every task

- **No behaviour change, except in Tasks 4, 5 and 7.** Every extraction keeps today's behaviour exactly, quirks included. Where a test pins a quirk that looks wrong, name it `..._currentBehaviour` and add a `// Pinned: <one line why>` comment. The three fix tasks each change one behaviour, named in the task, in a commit of its own.
- Unit tests must not need the Go toolchain. `:app:testDebugUnitTest` must not trigger `:syncthing:buildNative`; Task 1 checks this.
- New production classes are Java, in the package of the class they come out of, matching the files they live beside. Test classes may be Java or Kotlin; use Java, so they read the same way as the code they test.
- Robolectric tests run with `@Config(application = android.app.Application.class, sdk = 37)`, so the real `SyncthingApp.onCreate` (Dagger, `RecentTasksController`, StrictMode) never runs in tests. If Robolectric 4.18.1 can't run SDK 37, Task 1 falls back to 36 and records why.
- **Windows hosts.** Android, and CI on `ubuntu-latest`, rename a file over an existing one atomically. Java's `File.renameTo` on Windows refuses when the target exists. Any test that calls `ConfigXml.saveChanges()` and then reloads starts with `assumeFalse("rename over an existing file needs a POSIX host", isWindowsHost())`, where `isWindowsHost()` is a helper in `TestHosts.java` (Task 5). These tests are skipped locally on Windows and run in CI. Production code keeps `renameTo`; no code is added only to make tests run on Windows.
- Tests go under `app/src/test/java/com/nutomic/syncthingandroid/...`, mirroring the main source tree. Fixtures go under `app/src/test/resources/`.
- Commits: conventional style as in `git log` (`test(...)`, `refactor(...)`, `fix(...)`, `ci: ...`), each signed off with `git commit -s` (CONTRIBUTING.md requires the DCO).
- Run Gradle with `ANDROID_HOME` pointing at `%LOCALAPPDATA%\Android\Sdk` (there is no `local.properties`), for example from Git Bash: `ANDROID_HOME="$HOME/AppData/Local/Android/Sdk" ./gradlew --no-daemon :app:testDebugUnitTest`. Below this is written as `./gradlew :app:testDebugUnitTest`.

## Order and independence

Task 1 comes first. After it, three chains share no files and can run in parallel:
- Tasks 2 → 3 → 4 (run conditions)
- Tasks 5 → 6 (`ConfigXml`)
- Task 7 (import/export)

Task 8 (CI) lands last.

## Already failing before this plan

- The project has no tests. The baseline `:app:testDebugUnitTest` result is recorded under "Baseline" at the end of this file.

## Cases to watch

- **Force start/stop beats everything.** `BTNSTATE_FORCE_START` returns true even with no network, on battery-only and in power-save mode (Task 2).
- **Order of checks.** Time schedule, then power source, then power saving, then auto-sync, then mobile data, then Wi-Fi, then flight mode. Changing the order changes the decision; a test per step pins it (Task 2).
- **Flight mode with Wi-Fi on.** No active network, Wi-Fi allowed and "run in flight mode" on: runs. With flight mode off: doesn't run (Task 2).
- **Ethernet counts as unmetered Wi-Fi**, even when the system reports the network as metered (Task 2).
- **Whitelist with no location permission.** An SSID of `null` or `"<unknown ssid>"` gives the "location unavailable" reason, not "not on whitelisted Wi-Fi" (Task 2).
- **Per-object conditions have no roaming option.** No `DYN_PREF_OBJECT_*_ROAMING` pref exists. That is safe because the global decision runs first: with global roaming off, the service isn't running on roaming data at all. Pin that gate (Task 2).
- **Explanation text.** Mobile-data reasons have no leading newline while Wi-Fi reasons do. Force and time-frame reasons replace the text instead of appending to it. The Settings screen shows this text, so Task 3 pins it exactly.
- **Bad interval prefs.** Values such as `"abc"`, `""`, `"0"` or `"99999"` for `PREF_SLEEP_INTERVAL_MINUTES` or `PREF_SYNC_DURATION_MINUTES` can only arrive through an imported backup, because the settings screen checks its input. After Task 4 they fall back to the defaults (60 and 5) instead of crashing. That covers all three places that parse them: the constructor, `updateShouldRunDecision`, and the evaluator.
- **Failed config write.** A read-only leftover `config.xml.tmp` (or any failed write) must leave `config.xml` byte-for-byte unchanged (Task 5).
- **Unicode folder labels and paths** (#136, #150): Korean, emoji and combining characters survive parse, save and parse again unchanged (Task 6).
- **`<folder>` under `<defaults>`** is not returned by `getFolders()` (Task 6).
- **Export overwrites; it doesn't append.** A second export to the same path holds one copy of each entry (Task 7).
- **Wrong zip password**, an unencrypted zip while a password is set, and a missing `key.pem` each give their own result, and nothing is extracted (Task 7).
- **Prefs import** drops every deprecated key and every cache key on the lists, keeps `Set<String>` values, drops unknown value types, and always restores the current backup path and password (Task 7).
- **A crafted prefs file** holding any class outside the allow-list is rejected before `editor.clear()` runs, so the current settings survive (Task 7).
- **Old backups still import.** A prefs file written by today's `exportConfig` passes the allow-list (Task 7).

## Questions for you

- None open. Deferred to the automatic-backup plan: replacing the Java-serialised prefs file with JSON.

---

### Task 1: Test dependencies and a smoke test

**Files:** change `gradle/libs.versions.toml` and `app/build.gradle.kts`; create `app/src/test/java/com/nutomic/syncthingandroid/SmokeTest.java` and `app/src/test/java/com/nutomic/syncthingandroid/RobolectricSmokeTest.java`
**Depends on:** none
**Provides:** the test dependencies `libs.junit`, `libs.truth`, `libs.robolectric` and `libs.androidx.test.core`, and `testOptions.unitTests.isIncludeAndroidResources = true`

- [ ] In `libs.versions.toml`, add versions `junit = "4.13.2"`, `truth = "1.4.5"`, `robolectric = "4.18.1"`, `androidx-test-core = "1.7.0"` and the library entries `junit`, `truth`, `robolectric` and `androidx-test-core` (`androidx.test:core`). In `app/build.gradle.kts`, add the four as `testImplementation`, and inside `android { }` add `testOptions { unitTests { isIncludeAndroidResources = true } }`.
- [ ] Write the tests first:

```java
// SmokeTest.java
public class SmokeTest {
    @Test public void jvmTestsRun() { assertThat(1 + 1).isEqualTo(2); }
}

// RobolectricSmokeTest.java
@RunWith(RobolectricTestRunner.class)
@Config(application = android.app.Application.class, sdk = 37)
public class RobolectricSmokeTest {
    @Test public void appResourcesResolve() {
        Context context = ApplicationProvider.getApplicationContext();
        assertThat(context.getString(R.string.reason_force_start)).isNotEmpty();
    }
}
```

- [ ] Run `./gradlew :app:testDebugUnitTest`. Expected: `BUILD SUCCESSFUL`, with 2 tests in `app/build/test-results/testDebugUnitTest/`.
- [ ] If `RobolectricSmokeTest` fails at `sdk = 37` because the SDK is unsupported (for example `java.lang.IllegalArgumentException: failed to configure ... sdk 37`), change it to `sdk = 36`, use 36 in every later task, and record the error under "Baseline".
- [ ] Run `./gradlew :app:testDebugUnitTest --dry-run | grep -c buildNative`. Expected: `0`. If it isn't 0, make the `merge*JniLibFolders` → `buildNative` hook in `app/build.gradle.kts` skip unit-test-only task graphs. Don't use the `IS_COPILOT` variable for this.
- [ ] Commit: `test: add JUnit, Truth and Robolectric with smoke tests`.

### Task 2: Pull the run decision into `RunConditionEvaluator`

**Files:** create `app/src/main/java/com/nutomic/syncthingandroid/service/DeviceConditions.java` and `.../service/RunConditionEvaluator.java`; test in `app/src/test/java/com/nutomic/syncthingandroid/service/RunConditionEvaluatorTest.java`
**Depends on:** Task 1
**Provides:**
- `DeviceConditions`: an immutable value with a builder. Fields:
  - `boolean charging`, `boolean powerSaving`, `boolean masterSyncEnabled`
  - `Transport transport`, where `enum Transport { NO_ACTIVE_NETWORK, DISCONNECTED, WIFI, ETHERNET, MOBILE, OTHER }`. `NO_ACTIVE_NETWORK` is today's `getActiveNetworkInfo() == null`, which the code treats as flight mode. WIMAX maps to `WIFI`. BLUETOOTH, MOBILE, MOBILE_DUN and MOBILE_HIPRI map to `MOBILE`.
  - `boolean activeNetworkMetered`, `boolean roaming`
  - `boolean wifiInfoAvailable`, `@Nullable String wifiSsid`
- `RunConditionEvaluator.Reason`: an enum with one value per `R.string.reason_*` that `decideShouldRun` uses today. Each value has `@StringRes int stringRes` and `boolean newlineBefore`, matching the `"\n" +` prefixes in today's code exactly.
- `RunConditionEvaluator.Decision`: `boolean shouldRun`, `boolean replacesExplanation` (true for force start/stop and the time frame), `List<Reason> reasons`, `boolean timeConditionMatch`, `int minutesSinceLastRun`.
- `static Decision decide(SharedPreferences prefs, DeviceConditions device, long nowElapsedMs, boolean timeConditionMatch)`
- `static boolean checkObjectConditions(SharedPreferences prefs, DeviceConditions device, String objectPrefixAndId)`

The evaluator imports no `Context`, `Resources`, `ConnectivityManager` or `SystemClock`. It takes the time and the device state as arguments.

- [ ] Write the tests first, under Robolectric (using real `SharedPreferences` from `ApplicationProvider`; each test clears them in `@Before`). Name one test per step in the decision order, each asserting `shouldRun` and the first `Reason`:
  - `forceStart_runsWithNoNetworkAndPowerSave`
  - `forceStop_neverRuns`
  - `timeSchedule_outsideFrame_doesNotRun` (last run 10 min ago, interval 60, `minutesSinceLastRun == 10`, reason `NOT_WITHIN_TIME_FRAME`)
  - `timeSchedule_lastRunOlderThanInterval_setsTimeConditionMatch`
  - `powerSourceChargerOnly_onBattery_doesNotRun`
  - `powerSourceBatteryOnly_charging_doesNotRun`
  - `respectPowerSaving_inPowerSave_doesNotRun`
  - `respectMasterSync_autoSyncOff_doesNotRun`
  - `mobileDataAllowed_notRoaming_runs`
  - `mobileDataAllowed_roamingDisallowed_fallsThroughToWifiCheck`
  - `globalRoamingDisallowed_onRoamingDataOnly_doesNotRun` (the gate that per-object conditions rely on)
  - `wifiAllowed_unmetered_runs`
  - `wifiAllowed_meteredDisallowed_doesNotRun`
  - `ethernet_reportedMetered_stillRuns`
  - `whitelistOn_ssidListed_runs` (SSIDs are compared with their quotes, for example `"\"Home\""`, as `WifiInfo.getSSID()` returns them)
  - `whitelistOn_ssidUnknown_reasonLocationUnavailable` (cover both `null` and `"<unknown ssid>"`)
  - `whitelistOn_wifiInfoMissing_doesNotRun`
  - `flightModeAllowed_noActiveNetwork_runs`
  - `noConditionMatched_doesNotRun`
  - `objectConditions_usesObjectPrefNames` (set the global Wi-Fi pref to false and the object's to true: runs)
- [ ] Run `./gradlew :app:testDebugUnitTest --tests '*RunConditionEvaluatorTest'`. Expected: compilation fails with `cannot find symbol: class RunConditionEvaluator`.
- [ ] Write `DeviceConditions` and `RunConditionEvaluator` by moving the bodies of `decideShouldRun`, `checkObjectSyncConditions`, the five `checkConditionSyncOn*` methods and `wifiWhitelistConditionMet` from `RunConditionMonitor.java:436-726`. Replace each `isX()` call with a `DeviceConditions` field, `res.getString(...)` with a `Reason`, and `SystemClock.elapsedRealtime()` with `nowElapsedMs`. Keep the `Integer.parseInt` of the interval pref as it is; Task 4 replaces it. `RunConditionMonitor` stays unchanged in this task.
- [ ] Run the same command. Expected: all tests pass.
- [ ] Commit: `test(runconditions): extract RunConditionEvaluator and pin decision order`.

### Task 3: Switch `RunConditionMonitor` to the evaluator

**Files:** change `app/src/main/java/com/nutomic/syncthingandroid/service/RunConditionMonitor.java`; test in `app/src/test/java/com/nutomic/syncthingandroid/service/RunConditionExplanationTest.java`
**Depends on:** Task 2
**Uses:** `DeviceConditions`, `RunConditionEvaluator.decide`, `checkObjectConditions`, `Reason`, `Decision`
**Provides:**
- `static String RunConditionMonitor.formatExplanation(Resources res, RunConditionEvaluator.Decision d)`
- `private DeviceConditions readDeviceConditions()`: the only place left that calls `ConnectivityManager`, `WifiManager`, `PowerManager`, `ContentResolver.getMasterSyncAutomatically()` and the battery intent. The later network-callback work replaces just this method.

- [ ] Write the test first. It builds `Decision`s by hand and checks that `formatExplanation` produces exactly the string today's code produces for that path:
  - `forceStart_isExactlyForceStartString`
  - `timeFrame_usesPluralsAndZeroMinuteString` (0 minutes → `reason_not_within_time_frame_0_min`, 5 minutes → the plural inside `reason_not_within_time_frame_2`)
  - `mobileThenWifiReasons_newlinesMatchLegacy` (for example `reason_mobile_data_disallowed + "\n" + reason_wifi_disallowed`, with no leading newline before the mobile string)
  - `flightMode_appendsWithNewline`
- [ ] Run `./gradlew :app:testDebugUnitTest --tests '*RunConditionExplanationTest'`. Expected: fails with `cannot find symbol: method formatExplanation`.
- [ ] In `RunConditionMonitor`:
  - `decideShouldRun()` becomes `readDeviceConditions()`, then `RunConditionEvaluator.decide(mPreferences, device, SystemClock.elapsedRealtime(), mTimeConditionMatch)`, then storing `d.timeConditionMatch` into `mTimeConditionMatch` and `formatExplanation(res, d)` into `mRunDecisionExplanation`.
  - `checkObjectSyncConditions` delegates to the evaluator.
  - Delete the moved private methods.
  - `readDeviceConditions()` holds today's `isCharging`, `isPowerSaving`, `isFlightMode`, `isMeteredNetworkConnection`, `isMobileDataConnection`, `isRoamingNetworkConnection`, `isWifiOrEthernetConnection` and `WifiManager.getConnectionInfo()` logic, unchanged.
  - Public signatures stay the same: `updateShouldRunDecision()`, `getRunDecisionExplanation()`, `checkObjectSyncConditions(String)`, `shutdown()`.
- [ ] Run `./gradlew :app:testDebugUnitTest :app:lintDebug`. Expected: `BUILD SUCCESSFUL`, all tests pass, no new lint errors (`abortOnError = true`).
- [ ] Manual check on an existing emulator (run `emulator -list-avds` and reuse one; don't create a new AVD). With Wi-Fi on, then mobile data only, then airplane mode, the run-condition explanation in the app reads the same as on a build of `main`.
- [ ] Commit: `refactor(runconditions): RunConditionMonitor delegates to RunConditionEvaluator`.

### Task 4: Fix: bad interval prefs fall back to their defaults

**Behaviour change:** a non-numeric or out-of-range `PREF_SYNC_DURATION_MINUTES` or `PREF_SLEEP_INTERVAL_MINUTES` uses the default instead of throwing `NumberFormatException`.
**Files:** change `.../service/RunConditionEvaluator.java` and `.../service/RunConditionMonitor.java`; test in `RunConditionEvaluatorTest.java`
**Depends on:** Task 3
**Provides:**
- `static int RunConditionEvaluator.minutesPref(SharedPreferences prefs, String key, int defaultMinutes, int min, int max)`: returns the parsed value when it is an integer within `min..max`; otherwise it logs one `Log.w` line naming the key and returns `defaultMinutes`.
- `static final int SYNC_DURATION_DEFAULT = 5, SYNC_DURATION_MAX = 1440, SLEEP_INTERVAL_DEFAULT = 60, SLEEP_INTERVAL_MAX = 30240`, with a minimum of 1 for both. These match the ranges in `SettingsRunConditionsScreen.kt:277-305`.

- [ ] Write the tests first:
  - `minutesPref_validValue_returned` (`"15"` → 15)
  - `minutesPref_nonNumeric_returnsDefault` (`"abc"` and `""`)
  - `minutesPref_outOfRange_returnsDefault` (`"0"`, `"-5"`, `"30241"` for the sleep interval)
  - `minutesPref_missing_returnsDefault`
  - `decide_nonNumericSleepInterval_usesDefault` (`"abc"`, last run 10 min ago: same decision as with `"60"`)
- [ ] Run `./gradlew :app:testDebugUnitTest --tests '*RunConditionEvaluatorTest'`. Expected: fails with `cannot find symbol: method minutesPref`.
- [ ] Replace each `Integer.parseInt(mPreferences.getString(PREF_SLEEP_INTERVAL_MINUTES | PREF_SYNC_DURATION_MINUTES, ...))` with `minutesPref(...)`. That means one place in `RunConditionEvaluator.decide` and three in `RunConditionMonitor` (the constructor at today's line 173 and `updateShouldRunDecision` at today's lines 370-371). Afterwards, `grep -n "parseInt" app/src/main/java/com/nutomic/syncthingandroid/service/RunCondition*.java` prints nothing.
- [ ] Run `./gradlew :app:testDebugUnitTest`. Expected: all pass.
- [ ] Commit: `fix(runconditions): fall back to defaults on invalid interval prefs`.

### Task 5: Fix: `saveChanges()` never replaces `config.xml` after a failed write

**Behaviour change:** today an `IOException` or `FileNotFoundException` while writing `config.xml.tmp` still renames the temp file over `config.xml` (`ConfigXml.java:1295-1306`; only `TransformerException` returns). After the fix:
- any failure deletes the temp file, logs it, and leaves `config.xml` untouched;
- a successful write calls `getFD().sync()` before the rename;
- a `renameTo` that returns false is logged with `Log.w` and the temp file is deleted.

The method signature (`public void saveChanges()`) is unchanged.
**Files:** change `app/src/main/java/com/nutomic/syncthingandroid/util/ConfigXml.java`; create `app/src/test/java/com/nutomic/syncthingandroid/util/ConfigXmlTestSupport.java`, `app/src/test/java/com/nutomic/syncthingandroid/TestHosts.java`, `app/src/test/java/com/nutomic/syncthingandroid/util/ConfigXmlSaveTest.java` and `app/src/test/resources/config/basic.xml`
**Depends on:** Task 1
**Provides:**
- `static boolean TestHosts.isWindowsHost()`
- `static ConfigXml ConfigXmlTestSupport.loadFixture(Context ctx, String name)`: copies `config/<name>` to `Constants.getConfigFile(ctx)`, sets `PREF_LOCAL_DEVICE_ID` to the fixture's local device ID (so `getLocalDeviceIDfromPref` never starts the Syncthing binary), and calls `loadConfig()`.
- the `basic.xml` fixture, which has:
  - two folders, labelled "b" and with an empty label (id "a")
  - one `<folder>` under `<defaults>`
  - the local device plus two remote devices, one of them unnamed
  - an options element holding an `unackedNotificationID` of `fsWatcherNotification`

- [ ] Write the tests first:
  - `saveChanges_tempFileNotWritable_keepsConfigByteForByte`: create `Constants.getConfigTempFile(ctx)` with the content `garbage`, call `setWritable(false)` on it, then call `saveChanges()`. `config.xml` must still equal the fixture bytes, and the temp file must be gone afterwards. Start the test with `assumeFalse(..., TestHosts.isWindowsHost())`, because on a POSIX host today's code renames the garbage file over the config, which is the bug.
  - `saveChanges_success_replacesConfigAndLeavesNoTemp` (also POSIX-only)
- [ ] Run `./gradlew :app:testDebugUnitTest --tests '*ConfigXmlSaveTest'` on a POSIX host (CI, or WSL if available). Expected: `saveChanges_tempFileNotWritable_keepsConfigByteForByte` fails, with `config.xml` containing `garbage`. On Windows both tests are reported as skipped; note that, and rely on CI for the red/green check.
- [ ] Make the change in `saveChanges()` as described above.
- [ ] Run the same command. Expected: both pass on POSIX and are skipped on Windows.
- [ ] Commit: `fix(configxml): never replace config.xml after a failed write`.

### Task 6: `ConfigXml` characterisation tests

**Files:** test in `app/src/test/java/com/nutomic/syncthingandroid/util/ConfigXmlTest.java`; fixtures `app/src/test/resources/config/unicode.xml`, `v27.xml` and `malformed.xml`
**Depends on:** Task 5
**Uses:** `ConfigXmlTestSupport.loadFixture`, `TestHosts.isWindowsHost`, `basic.xml`

- [ ] Fixtures:
  - `unicode.xml` has a folder labelled `사진 📷 é` (the `é` written as `e` + U+0301), with a path under `/storage/emulated/0/사진`.
  - `v27.xml` is `basic.xml` with `version="27"`.
  - `malformed.xml` is cut off in the middle of an element.
- [ ] Write the tests. No production change is expected, so they should pass at once; any test that fails has found a real difference and is reported, not "fixed" in the code. Tests marked (POSIX) save and reload, and start with the `assumeFalse` guard.
  - `getFolders_sortedByLabelFallingBackToId_excludesDefaults`
  - `getDevices_excludeLocal_and_sortedByNameFallingBackToId`
  - `loadConfig_setsIgnorePermsAndHashersOnEveryFolder_andSaves` (POSIX)
  - `loadConfig_removesFsWatcherUnackedNotification` (POSIX)
  - `loadConfig_v27_enablesFsWatcherAndBumpsVersionTo28` (POSIX)
  - `unicodeLabelAndPath_surviveSaveAndReload` (POSIX; compare the exact code points)
  - `missingFile_throwsOpenConfigException`
  - `malformedXml_throwsOpenConfigException`
  - `getWebGuiBindPort_badAddress_returnsDefaultPort`
  - `updateFolder_thenReload_roundTripsFields` (POSIX; label, path, type, `fsWatcherDelayS`)
- [ ] Run `./gradlew :app:testDebugUnitTest --tests '*ConfigXmlTest'`. Expected: all pass (the POSIX tests are skipped on Windows), or each failure is written up with what was expected and what was found.
- [ ] Commit: `test(configxml): characterise parsing, migration and save`.

### Task 7: Pull zip and prefs handling into `ConfigArchive`, then add the allow-list

**Files:** create `app/src/main/java/com/nutomic/syncthingandroid/util/ConfigArchive.java`; change `app/src/main/java/com/nutomic/syncthingandroid/service/SyncthingService.java`; test in `app/src/test/java/com/nutomic/syncthingandroid/util/ConfigArchiveTest.java`
**Depends on:** Task 1
**Provides**, all static and none taking a `Context` or service:
- `void writeZip(File targetZip, List<File> includePaths, String password) throws IOException`: deletes any existing target first. Paths that don't exist are skipped. Directories are added recursively. A non-empty password means AES-256.
- `enum Check { OK, MISSING_ZIP, NOT_ENCRYPTED_BUT_PASSWORD_SET, MISSING_REQUIRED_ENTRY, CANNOT_OPEN }`, and `Check check(File zip, String password, List<String> requiredEntries, File scratchDir)`. `CANNOT_OPEN` covers a wrong password and a corrupt zip; it is today's `ZipException` path, including the trial extraction of `PUBLIC_KEY_FILE`.
- `void extractAll(File zip, String password, File destDir) throws IOException`
- `void writePrefs(Map<String, ?> prefs, File file) throws IOException` and `Map<String, Object> readPrefs(File file) throws IOException, ClassNotFoundException`: today's `ObjectOutputStream`/`ObjectInputStream` format, unchanged, so old backups still load.
- `Map<String, Object> importablePrefs(Map<?, ?> raw)`: drops the 14 deprecated keys and 7 cache keys listed in `SyncthingService.java:1424-1447`, and drops values that aren't Boolean, String, Integer, Float, Long or `Set<String>`.
- `boolean applyPrefs(SharedPreferences.Editor editor, Map<String, Object> importable, String keepRelPathToZip, String keepPassword)`: `clear()`, then the typed puts, then the two kept values, then `commit()`.
- (allow-list step) `static final Set<String> PREFS_ALLOWED_CLASSES`: `java.util.HashMap`, `java.util.HashSet`, `java.lang.Boolean`, `java.lang.Integer`, `java.lang.Long`, `java.lang.Float`, `java.lang.Number`. `Number` is needed because serialisation resolves the superclass of each boxed number. Strings are written without going through `resolveClass`, so `java.lang.String` doesn't need to be listed.

**Part A, refactor (no behaviour change):**

- [ ] Write the tests first (Robolectric for `applyPrefs`; plain JUnit with a `TemporaryFolder` for the rest):
  - `writeZip_thenCheck_ok_withAndWithoutPassword`
  - `writeZip_twice_overwritesInsteadOfAppending` (entry count equal after the second write)
  - `writeZip_includesIndexDbFolderRecursively_skipsMissingPaths`
  - `check_missingZip`, `check_wrongPassword_cannotOpen`, `check_unencryptedZipWithPasswordSet`, `check_missingKeyPem`
  - `check_neverWritesOutsideScratchDir`
  - `prefs_roundTrip_preservesAllSupportedTypes` (including a `Set<String>`, starting from a real `SharedPreferences.getAll()` under Robolectric)
  - `importablePrefs_dropsEveryDeprecatedAndCacheKey` (parameterise over all 21 keys)
  - `importablePrefs_dropsUnknownValueType`
  - `applyPrefs_clearsOldKeys_andKeepsBackupPathAndPassword`
- [ ] Run `./gradlew :app:testDebugUnitTest --tests '*ConfigArchiveTest'`. Expected: compilation fails with `cannot find symbol: class ConfigArchive`.
- [ ] Write `ConfigArchive` by moving code out of `exportConfig`, `importConfig` and `importConfigSharedPrefs` (`SyncthingService.java:845-1090` and `1399-1499`). Then change those three methods to call it. Keep their order of steps and their failure flags exactly as they are, including today's partial apply on a failed extraction (#374; fixing it is the next piece of work). `exportConfig()` and `importConfig()` keep their signatures and return values.
- [ ] Run `./gradlew :app:testDebugUnitTest :app:lintDebug`. Expected: `BUILD SUCCESSFUL`.
- [ ] Commit: `refactor(importexport): extract ConfigArchive and cover it with tests`.

**Part B, fix (behaviour change):** `readPrefs` refuses any class outside `PREFS_ALLOWED_CLASSES`.

- [ ] Write the tests first:
  - `readPrefs_rejectsClassOutsideAllowList`: a map holding a `java.util.ArrayList` value, and a separate file holding a `java.util.Date`. Both throw `java.io.InvalidClassException`.
  - `readPrefs_rejectsNonMapTopLevel` (a serialised `HashSet` at the top level)
  - `readPrefs_acceptsFileWrittenByWritePrefs`: write the map from `prefs_roundTrip...` and read it back unchanged. This shows old backups still import, because `writePrefs` is today's export format.
  - `import_rejectedPrefsFile_leavesCurrentPrefsUntouched` (Robolectric): seed prefs, run the import path on a rejected file, and check that the seeded values are still there. This shows the rejection happens before `editor.clear()`.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests '*ConfigArchiveTest'`. Expected: `readPrefs_rejectsClassOutsideAllowList` fails (no exception is thrown).
- [ ] In `ConfigArchive`, add a private static `AllowListObjectInputStream extends ObjectInputStream` whose `resolveClass(ObjectStreamClass desc)` throws `new InvalidClassException(desc.getName(), "not allowed in prefs backup")` for any name outside `PREFS_ALLOWED_CLASSES`, and calls `super.resolveClass` otherwise. `readPrefs` uses it. In `SyncthingService.importConfigSharedPrefs`, the existing `catch (IOException | ClassNotFoundException e)` already turns this into `failSuccess = false`. Check that the read happens before `editor.clear()`; if it doesn't, move it there.
- [ ] Run `./gradlew :app:testDebugUnitTest :app:lintDebug`. Expected: `BUILD SUCCESSFUL`.
- [ ] Manual check on the emulator:
  - export, change one setting, import, and the setting comes back;
  - a password-protected export and import with the same password;
  - import a backup made by the current release build (from `main`), which must still succeed.
- [ ] Commit: `fix(importexport): allow-list classes when reading the prefs backup`.

### Task 8: Run unit tests in CI

**Files:** change `.github/workflows/common-build.yaml`
**Depends on:** Task 1. Land it last so CI starts with the whole suite.

- [ ] On line 190, add `test${{ inputs.buildType }}UnitTest` to the Gradle command: `./gradlew --no-daemon buildNative lint${{ inputs.buildType }} test${{ inputs.buildType }}UnitTest assemble${{ inputs.buildType }} ${{ inputs.gradleOptions }}`.
- [ ] After that step, add a step using `actions/upload-artifact@cf430e030ddbb5b0abf93d22962f4752f3646cd9 # v7.0.2` (the same pin as lines 255 and 264) with `if: failure()`, uploading `app/build/reports/tests/` as `unit-test-report-${{ inputs.buildType }}`.
- [ ] Run `./gradlew --no-daemon :app:testReleaseUnitTest` locally. Expected: `BUILD SUCCESSFUL`, so the release variant also compiles and passes the tests.
- [ ] If `actionlint` is installed, run `actionlint .github/workflows/common-build.yaml`. Expected: no output.
- [ ] Commit: `ci: run unit tests in common build`. Pushing and opening a PR is the owner's call.

## Proof for the whole plan

- `./gradlew :app:testDebugUnitTest :app:testReleaseUnitTest :app:lintDebug` ends in `BUILD SUCCESSFUL`, with no Go toolchain installed. On Windows the POSIX-only tests show as skipped; the first CI run on `ubuntu-latest` shows them passing.
- `RunConditionMonitor.java` no longer contains `decideShouldRun`, any `checkConditionSyncOn*` method, or `parseInt`. `SyncthingService.java` no longer contains `ObjectInputStream`.
- The emulator checks in Tasks 3 and 7 match a build of `main`, and a backup made by the current release still imports.
- The first CI run on the branch shows the unit-test task running in `common-build`.

## Baseline

2026-11-22, on `main` at `71fca13c`: `./gradlew --no-daemon :app:testDebugUnitTest` ended in `BUILD SUCCESSFUL`, with `testDebugUnitTest NO-SOURCE` and 26 tasks run. `:syncthing:buildNative` did not run. The first run took about 25 minutes because Gradle installed NDK 30.0.16248370; later runs are much faster.

## Progress

- Run started 2026-11-22 from `71fca13c` on branch `test/unit-test-foundation`, inline mode.
- User decided: skip the emulator checks in Tasks 3 and 7 for now (Go isn't installed, so no APK can be built locally); verify later with a CI-built APK. POSIX-only tests are skipped locally and run in CI.
- Task 1: done — commit 2881f061 — `./gradlew :app:testDebugUnitTest` → BUILD SUCCESSFUL, 2 tests, 0 failures; Robolectric runs at SDK 37 (no fallback needed); dry run shows 0 buildNative tasks. First run took 15 min (Robolectric android-all download).
- Task 2: done — commit d9a33262 — `--tests '*RunConditionEvaluatorTest'` → 22 tests, 0 failures. Deviation: `Decision.replacesExplanation` dropped, because the explanation is always empty when force/time-frame/power reasons are added, so appending equals replacing. Evaluator has no verbose logging; the monitor logs the decision instead (Task 3).
- Task 3: done — commit c0b09a1f — `:app:testDebugUnitTest :app:lintDebug` → BUILD SUCCESSFUL, 28 tests, 0 failures. Emulator check skipped (user decision). Note: `readDeviceConditions` now reads `WifiInfo` on every decision, not only when the whitelist is on (null-guarded; SSID is never logged).
- Task 4: done — commit db602413 — `:app:testDebugUnitTest` → BUILD SUCCESSFUL, 33 tests, 0 failures. Also fixed a 4th unguarded parse in `QuickSettingsTileSchedule.java:150` (not in the plan, same bug). The one remaining `parseInt` is inside `minutesPref` itself.
- Task 5: done — commit f3051f7d — `:app:testDebugUnitTest :app:lintDebug` → BUILD SUCCESSFUL; ConfigXmlSaveTest 2 tests, 2 skipped (Windows host). Red/green not observed locally: WSL Ubuntu has a JDK but no Android SDK; the POSIX check is left to the first CI run.
- Task 6: done — commit 0c2fd21e — `:app:testDebugUnitTest` → BUILD SUCCESSFUL, 48 tests, 7 skipped (POSIX-only, Windows host), 0 failures. Added two tests the plan didn't list, which run on Windows: `loadConfig_setsIgnorePermsAndHashersOnEveryFolder` (in memory) and `unicodeLabel_parsedExactly`. The bind-port test uses port 9999 so it can tell a real port from the default.
- Task 7 part A: done — commit 928bdbf2 — `:app:testDebugUnitTest :app:lintDebug` → BUILD SUCCESSFUL, ConfigArchiveTest 13 tests (one extra, `extractAll_restoresFiles`), 0 failures. Two edge cases now skip a pref instead of crashing the import: a non-String key (was a ClassCastException) and a Set holding non-String items (was a ClassCastException in the old `asSet`, now removed).
- Task 7 part B: done — commit 36c1ad98 — `:app:testDebugUnitTest :app:lintDebug` → BUILD SUCCESSFUL, ConfigArchiveTest 17 tests, 0 failures (red first: 3 failed with nothing rejected, and the crafted file cleared current prefs). Also: a non-map top level now fails the import (it used to log and return success); dynamic proxy classes are refused too. Emulator checks skipped (user decision).
- Task 8: done — commit 1f3a6140 — `:app:testDebugUnitTest` → BUILD SUCCESSFUL, 65 tests, 7 skipped, 0 failures; YAML parses. Plan change: CI runs `testDebugUnitTest` in both jobs, because AGP 9 creates no `testReleaseUnitTest` task (`enableUnitTest = false` for release). The read-only-temp test now also skips when the host user can write read-only files (root). Found, not caused by this branch: `:app:tasks --all` fails on `main` too (Compose Preview task creation).
- Correction to Task 7 part A: non-String keys are **not** skipped; `readPrefs` turns them into strings (`String.valueOf`), so the key check in `importablePrefs` never fires on the real path (review finding M1, left open).
- Whole-branch review (fresh reviewer, 71fca13c..1f3a6140): 0 Critical, 0 High, 1 Medium, 7 Low. Run-condition and zip extractions confirmed faithful; allow-list rejects nothing a real `getAll()` export holds; no remaining path for `saveChanges()` to lose `config.xml`.
  - Open, Medium: M1 above (fix: drop non-String keys in `readPrefs` + a test).
  - Open, Low: L1 a Set with a null element now drops the whole pref; L2 check order not truly pinned (each test fails one condition only); L3 explanation tests cover 6 of 23 reasons; L4 "untouched prefs" test mirrors the call order instead of calling `importConfigSharedPrefs`; L5 two duplicate round-trip tests; L6 a test failure in CI now skips the native-library cache save (no `if: always()`); L7 Robolectric's android-all jar may not be cached in CI.
  - Pre-existing, cannot tell: `ConfigXml.saveChanges()` isn't synchronized and all callers share one temp file.
- Final: commit cf6e486d — `:app:testDebugUnitTest` → BUILD SUCCESSFUL, 65 tests, 7 skipped, 0 failures; `:app:lintDebug` passed at 1f3a6140. Duplicate-code checker: 5 blocking clones, all on lines older than this branch.
- Review fixes (user asked, 2026-11-22): M1 fixed in bd117c16 (non-String keys dropped in `readPrefs`, test `readPrefs_dropsNonStringKeys` red then green); L6 fixed in 80f19c6c (native build in its own step, cache saved before lint/tests). `:app:testDebugUnitTest` → BUILD SUCCESSFUL, 66 tests, 7 skipped, 0 failures. L1–L5 and L7 still open.
- Pushed rebased branch (on 1d569d8c) as PR haziaferi/syncthing-fork-fork#2, 2026-11-22. Rebase-run: `:app:testDebugUnitTest` → BUILD SUCCESSFUL, 66 tests, 7 skipped, 0 failures.
- 2026-11-23, PR #2 CI (`079de977`): reports artifact shows 66 tests, 0 failures, 0 skipped on Linux, so the 7 POSIX-only save tests passed there.
- 2026-11-23 device check (user approved; both local emulators crash on start): the CI debug build (`com.github.catfriend1.syncthingfork.debug`, separate from the release app) was installed on the OnePlus 6T (Android 11, offline). Results:
  - Run-condition explanations are correct for the default offline state, force start, force stop, and with mobile data + flight mode allowed (3 reasons, running).
  - Export/import works with no password and with a password. Encrypted zips are AES-encrypted; an imported setting is restored; cache prefs are dropped; the allow-list accepted the real on-device export.
  - A wrong-password import fails at the precheck (`CANNOT_OPEN`), and the settings are unchanged.
  - The debug app was uninstalled and its test backup folder removed. The release app (2.1.6.0, last updated 2026-10-14) was untouched throughout.
  - Not checked: importing a backup made by the release app, which would duplicate the device ID.
- 2026-11-23: PR #2 squash-merged into main as c58699b9 (all sign-offs kept). Plan complete.
