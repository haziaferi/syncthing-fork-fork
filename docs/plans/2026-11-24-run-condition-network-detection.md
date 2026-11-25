# Run conditions: network detection, implementation plan

**Goal:** the run conditions see the network the phone is really on: callbacks report every change, a VPN no longer hides the Wi-Fi beneath it, metered state comes from the Wi-Fi itself, a Wi-Fi without internet never lets "Wi-Fi only" use mobile data, and a brief drop no longer stops Syncthing or pauses folders.
**Approach:** a pure `NetworkSnapshot` turns a list of per-network states into the network fields of `DeviceConditions`; `RunConditionEvaluator` judges those fields; a pure `StopDelay` holds a "met" result for 10 seconds; a small `NetworkTracker` is the only class that talks to `ConnectivityManager`. `RunConditionMonitor` wires them together and drops `CONNECTIVITY_ACTION`.
**Stack:** Java (minSdk 23, no core-library desugaring), JUnit 4, Robolectric 4.18.1 (`sdk = 37`), Truth.
**Spec:** `docs/specs/run-conditions/SPEC.md` (R01 to R13).
**Carry out with:** `dev-engineering-suite:plan-runner`; tick each step as it is done.

## Applies to every task

- Work on the branch `feat/run-condition-network`, cut from `main` at `6923787c`.
- Run Gradle as `ANDROID_HOME="$HOME/AppData/Local/Android/Sdk" ./gradlew --no-daemon ...` from the repository root. Send long output to `build-logs/`, never to the parent folder.
- Commits follow `CONTRIBUTING.md`: conventional commits in lower case, scope `runconditions`, `git commit -s`, ending with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- No `java.time`, no `java.util.function`. Time inside the monitor is `SystemClock.elapsedRealtime()`; the pure classes take the time as a `long` argument.
- Production code goes in `app/src/main/java/com/nutomic/syncthingandroid/service/`, tests in `app/src/test/java/com/nutomic/syncthingandroid/service/`, under `@RunWith(RobolectricTestRunner.class)` and `@Config(application = android.app.Application.class, sdk = 37)`.
- Name each test that proves a spec rule `<what>_<expected>` and put `// SPEC:Rnn` on the line above it.
- `RunConditionEvaluator` keeps its order of checks: force start/stop, time schedule, power source, power saving, auto-sync, mobile data, Wi-Fi, flight mode (R02). Charging and power-saving code is not touched (R11).
- Never log a Wi-Fi name (the existing rule at `RunConditionMonitor.java:503`).

## Already failing before this plan

No tests were failing before this plan. `:app:testDebugUnitTest` on `main` (`6923787c`), 2026-11-24: `BUILD SUCCESSFUL`, 143 tests, 7 skipped (the symbolic-link tests where the host cannot create links), 0 failures. The log is `build-logs/rc-baseline-tests.log`.

## Cases to watch

- Two Wi-Fi networks at once (API 31+ can join a second one): the snapshot uses the validated one, else the first (test in Task 1).
- Wi-Fi and Ethernet both connected: Ethernet counts, so the result is unmetered even if the Wi-Fi is metered (test in Task 2).
- A VPN is the default network and no physical network is marked default: the snapshot picks Ethernet, then validated Wi-Fi, then mobile, then Wi-Fi without internet, then other (test in Task 1).
- A Wi-Fi without internet, mobile data allowed only for one folder: that folder counts the Wi-Fi, the whole app does not (test in Task 2).
- A folder that was already paused when Syncthing starts is not resumed for 10 seconds by the stop delay: the delay only holds a result that was "met" before (test in Task 3).
- The network drops twice within 10 seconds: the delay counts from the first drop, not the second (test in Task 3).
- `shutdown()` while a 10-second hold or the 15-minute re-check is pending: nothing runs afterwards (test in Task 5).
- The Wi-Fi name is hidden (`<unknown ssid>`) on API 31+ when location is off: the existing location reason, not "not on whitelisted Wi-Fi" (test in Task 2).

## Questions for you

- **Fix the 5-second freeze when a charger is plugged in or out?** `RunConditionMonitor.java:226` calls `SystemClock.sleep(5000)` on the main thread. Planned: no, since R02 and R11 keep charging out of scope. Saying yes adds Task 6 below (post the re-check 5 seconds later with the monitor's handler instead of sleeping) and a one-row change to R11.
- **A VPN app on the emulators?** Neither emulator has one, and installing one (for example WireGuard from F-Droid) is a download. Planned, provisionally: the VPN rows of the device run happen on your phone only, so A-R06 is confirmed on one device. Saying yes adds the VPN rows to both emulators in Task 7.

---

### Task 1: Network fields and the snapshot

**Files:** change `DeviceConditions.java`; create `NetworkState.java`, `NetworkSnapshot.java`; test in `NetworkSnapshotTest.java`; update the builders in `RunConditionEvaluatorTest.java`
**Depends on:** none
**Provides:**
- `DeviceConditions` network fields, replacing `activeNetworkMetered` and `Transport.DISCONNECTED`: `transport` (kind of the default physical network, `NO_ACTIVE_NETWORK` when none), `wifiConnected`, `wifiValidated`, `wifiMetered`, `ethernetConnected`, `mobileConnected`, `roaming` (of the mobile network), plus the existing `wifiInfoAvailable`, `wifiSsid`. Builder setters of the same names. `isConnected()` is `transport != NO_ACTIVE_NETWORK`.
- `final class NetworkState { NetworkState(Transport kind, boolean isDefault, boolean validated, boolean metered, boolean roaming, boolean wifiInfoAvailable, @Nullable String wifiSsid) }` with public final fields of those names. Holds one physical network; VPNs never become a `NetworkState`.
- `final class NetworkSnapshot { static void fill(DeviceConditions.Builder b, List<NetworkState> physical) }`.

`fill` sets `transport` from the network with `isDefault`; when none is default (a VPN is), it picks by the order Ethernet, validated Wi-Fi, mobile, Wi-Fi without internet, other. The Wi-Fi fields come from the validated Wi-Fi, else the first Wi-Fi.

- [ ] Write `NetworkSnapshotTest` first:
  - `fill_noNetworks_flightMode`: empty list gives `transport == NO_ACTIVE_NETWORK`, `isConnected() == false`.
  - `// SPEC:run-conditions:R05` `fill_vpnDefaultOverWifi_usesWifi`: one validated Wi-Fi with `isDefault == false` gives `transport == WIFI`, `wifiConnected`, `wifiValidated`.
  - `fill_vpnDefault_picksByOrder`: validated Wi-Fi plus mobile, none default, gives `WIFI`; Wi-Fi without internet plus mobile gives `MOBILE`; Ethernet plus validated Wi-Fi gives `ETHERNET`.
  - `fill_defaultMobileWithUnvalidatedWifi_mobileDefault`: mobile default plus Wi-Fi with `validated == false` gives `transport == MOBILE`, `wifiConnected`, `!wifiValidated`, `mobileConnected`.
  - `// SPEC:run-conditions:R08` `fill_metersAndRoamingFromOwnNetwork`: unmetered Wi-Fi plus a metered, roaming mobile default gives `!wifiMetered`, `roaming`.
  - `fill_twoWifi_prefersValidated`: the SSID comes from the validated one.
- [ ] Run `./gradlew --no-daemon :app:testDebugUnitTest --tests "*NetworkSnapshotTest"`. Expected: compile failure, `cannot find symbol class NetworkState`.
- [ ] Write the code. Update `RunConditionEvaluatorTest`'s builders to the new fields (`wifi()` sets `wifiConnected(true).wifiValidated(true)`; the metered tests use `wifiMetered`; the test at line 289 that used `DISCONNECTED` uses `NO_ACTIVE_NETWORK`). In `RunConditionEvaluator`, read `wifiMetered` where it read `activeNetworkMetered`, so the evaluator compiles with today's behaviour; Task 2 changes it.
- [ ] Run `./gradlew --no-daemon :app:testDebugUnitTest`. Expected: `BUILD SUCCESSFUL`, 0 failures.
- [ ] Commit: `refactor(runconditions): describe each connected network in device conditions`.

### Task 2: Which network counts

**Files:** change `RunConditionEvaluator.java`, `app/src/main/res/values/strings.xml`; test in `RunConditionEvaluatorTest.java`
**Depends on:** Task 1
**Provides:** `Reason.WIFI_WITHOUT_INTERNET(R.string.reason_wifi_without_internet, true)`; string `reason_wifi_without_internet` = "Wi-Fi has no internet and mobile data is not allowed."

The Wi-Fi check takes the mobile-data preference of the conditions it checks (`PREF_RUN_ON_MOBILE_DATA` for the app, `DYN_PREF_OBJECT_SYNC_ON_MOBILE_DATA(id)` for an object) and decides by R07's table: Ethernet counts; a validated Wi-Fi counts; a Wi-Fi without internet counts if that mobile preference is on or `!mobileConnected`, else the reason is `WIFI_WITHOUT_INTERNET`; no Wi-Fi gives `NOT_ON_WIFI`. Mobile data counts when `transport == MOBILE`. Metered is false when Ethernet counts, else `wifiMetered`. Roaming reads `roaming`.

- [ ] Write the tests first, each `// SPEC:run-conditions:R07` unless noted:
  - `validatedWifi_runsUnderWifiOnly`.
  - `unvalidatedWifi_mobileAllowed_runs`, and `unvalidatedWifi_noMobileNetwork_runs`.
  - `unvalidatedWifi_mobileOnButDisallowed_doesNotRun`: the reasons contain `WIFI_WITHOUT_INTERNET` (also `// SPEC:run-conditions:R10`).
  - `ethernet_countsAsUnmeteredWifi`: Ethernet plus a metered Wi-Fi runs under "unmetered Wi-Fi only".
  - `mobileDefault_countsAsMobile`.
  - `objectConditions_unvalidatedWifi_followObjectMobilePref`: the app's mobile pref off, the folder's on, gives the folder met and the app not.
  - `// SPEC:run-conditions:R08` `unmeteredWifi_meteredDefault_runsUnderUnmeteredOnly`, `meteredWifi_doesNotRun`, `whitelistedSsid_runs`, `hiddenSsid_givesLocationReason`.
- [ ] Run `./gradlew --no-daemon :app:testDebugUnitTest --tests "*RunConditionEvaluatorTest"`. Expected: the new tests fail (`cannot find symbol WIFI_WITHOUT_INTERNET`).
- [ ] Write the code.
- [ ] Run the same command. Expected: all `RunConditionEvaluatorTest` tests pass; then `:app:testDebugUnitTest` ends in `BUILD SUCCESSFUL`.
- [ ] Commit: `fix(runconditions): judge wi-fi, mobile and metered from each network`.

### Task 3: Stop delay

**Files:** create `StopDelay.java`; test in `StopDelayTest.java`
**Depends on:** none
**Provides:** `final class StopDelay { static final long DELAY_MS = 10_000L; boolean effective(String key, boolean met, long nowMs); long nextCheckAt(); void clear(); }`

`effective` returns `met` when it is true, or when the last result for `key` was not true (no hold on a first or repeated "unmet"). When `met` is false after a true result, it returns true until `DELAY_MS` has passed since the first unmet call, then false. `nextCheckAt()` returns the earliest time a hold ends, or `-1` when none is held. `clear()` forgets every key.

- [ ] Write the tests first, each `// SPEC:run-conditions:R09`:
  - `shortDrop_staysMet`: met at 0, unmet at 1 000 and 5 000, met at 6 000: every result true.
  - `longDrop_stopsAfterTenSeconds`: met at 0, unmet at 1 000 gives true, unmet at 11 000 gives false; `nextCheckAt()` after the 1 000 call is 11 000.
  - `firstResultUnmet_noHold`: a first call with false gives false.
  - `twoDrops_countFromFirst`: unmet at 1 000, met at 2 000, unmet at 3 000, unmet at 13 000 gives false (the hold restarted at 3 000).
  - `keysIndependent`: a hold on `"folder:a"` does not hold `""`.
- [ ] Run `./gradlew --no-daemon :app:testDebugUnitTest --tests "*StopDelayTest"`. Expected: compile failure, `cannot find symbol class StopDelay`.
- [ ] Write the code.
- [ ] Run the same command. Expected: 5 tests pass.
- [ ] Commit: `feat(runconditions): hold a met result for ten seconds`.

### Task 4: Network tracker

**Files:** create `NetworkTracker.java`; test in `NetworkTrackerTest.java`
**Depends on:** Task 1 (`NetworkState`)
**Provides:**
- `final class NetworkTracker { interface Listener { void onNetworksChanged(); } NetworkTracker(Context context, Listener listener); void start(); void stop(); List<NetworkState> snapshot(); static Transport kindOf(NetworkCapabilities caps); }`

`start()` registers one `NetworkCallback` with `new NetworkRequest.Builder().build()` (that request already excludes VPNs). On API 31+ the callback is built with `NetworkCallback.FLAG_INCLUDE_LOCATION_INFO`. `onCapabilitiesChanged` stores the capabilities per `Network`; `onLost` removes them; each posts `listener.onNetworksChanged()` to the main looper. `snapshot()` turns the stored entries into `NetworkState`s: kind from `kindOf` (R05's table), `isDefault` when the network equals `ConnectivityManager.getActiveNetwork()`, `validated` from `NET_CAPABILITY_VALIDATED`, `metered` as `!NET_CAPABILITY_NOT_METERED`, `roaming` as `!NET_CAPABILITY_NOT_ROAMING` on API 28+ and `getNetworkInfo(network).isRoaming()` below. The Wi-Fi name comes from `caps.getTransportInfo()` as a `WifiInfo` on API 31+, and from `WifiManager.getConnectionInfo()` below. `stop()` unregisters and clears the map; calls to `stop()` without `start()` do nothing.

- [ ] Write the tests first:
  - `// SPEC:run-conditions:R05` `kindOf_mapsTransports`: Wi-Fi, Ethernet, cellular, Bluetooth and an unknown transport give `WIFI`, `ETHERNET`, `MOBILE`, `MOBILE`, `OTHER` (build caps with `ShadowNetworkCapabilities.newInstance()` and `shadowOf(caps).addTransportType(...)`).
  - `// SPEC:run-conditions:R03` `start_registersOneCallback_stopUnregisters`: `shadowOf(connectivityManager).getNetworkCallbacks()` has size 1 after `start()`, 0 after `stop()`.
  - `// SPEC:run-conditions:R03` `capabilitiesChanged_addsNetworkAndNotifies`: calling the registered callback's `onCapabilitiesChanged(ShadowNetwork.newInstance(1), wifiCaps)` and idling the main looper gives one listener call and a snapshot with one validated, unmetered Wi-Fi.
  - `lost_removesNetwork`: `onLost` of that network empties the snapshot and calls the listener again.
- [ ] Run `./gradlew --no-daemon :app:testDebugUnitTest --tests "*NetworkTrackerTest"`. Expected: compile failure, `cannot find symbol class NetworkTracker`.
- [ ] Write the code.
- [ ] Run the same command. Expected: 4 tests pass.
- [ ] Commit: `feat(runconditions): track networks through connectivity callbacks`.

### Task 5: Wire it into the monitor

**Files:** change `RunConditionMonitor.java`; test in `RunConditionMonitorTest.java`
**Depends on:** Tasks 1 to 4
**Provides:** a package-private constructor `RunConditionMonitor(Context, SharedPreferences, OnShouldRunChangedListener, OnSyncPreconditionChangedListener)` that the public one calls after injection, so tests need no `SyncthingApp`; `static final long RECHECK_MS = 15 * 60 * 1000L`.

Changes:
1. Remove `NetworkReceiver` and its `CONNECTIVITY_ACTION` registration. The constructor creates a `NetworkTracker` whose listener calls `updateShouldRunDecision()`, and starts it before the first decision; `shutdown()` stops it.
2. `readDeviceConditions()` fills the network fields with `NetworkSnapshot.fill(builder, tracker.snapshot())` and no longer calls `getActiveNetworkInfo()`, `isActiveNetworkMetered()` or `WifiManager`.
3. Register an `Intent.ACTION_SCREEN_ON` receiver through `ReceiverManager` that calls `updateShouldRunDecision()`. At the end of each `updateShouldRunDecision()`, remove and re-post a runnable on a main-looper `Handler` that calls it again after `RECHECK_MS`. `shutdown()` removes all of the handler's callbacks.
4. In `updateShouldRunDecision()`, the decision passed on is `StopDelay.effective("", raw, now)`, except that a decision whose reasons contain `FORCE_STOP` is passed on at once and clears the delay. `checkObjectSyncConditions(id)` returns `StopDelay.effective(id, raw, now)`. When `nextCheckAt()` is not `-1`, post `updateShouldRunDecision()` for that time on the same handler.

- [ ] Write `RunConditionMonitorTest` first. Fake the networks by calling the tracker's registered callback, found through `shadowOf(connectivityManager).getNetworkCallbacks()`; record the listener's calls in a list.
  - `// SPEC:run-conditions:R03` `wifiAppears_decisionTurnsTrue`: with default prefs and no network, the first decision is false; a validated Wi-Fi through `onCapabilitiesChanged`, then idling the main looper, gives `onShouldRunDecisionChanged(true)`.
  - `// SPEC:run-conditions:R03` `shutdown_unregistersCallback`: `getNetworkCallbacks()` is empty after `shutdown()`, and idling the looper for 20 minutes adds no listener call.
  - `// SPEC:run-conditions:R04` `recheck_after15Minutes`: with Wi-Fi up and running, set `PREF_BTNSTATE_FORCE_START_STOP` to `BTNSTATE_FORCE_STOP` with `commit()` and no other notice; idling 14 minutes changes nothing, idling to 15 minutes gives `false`.
  - `// SPEC:run-conditions:R04` `screenOn_rechecks`: the same silent pref change, then `context.sendBroadcast(new Intent(Intent.ACTION_SCREEN_ON))` and an idle, gives `false`.
  - `// SPEC:run-conditions:R09` `wifiDrop5s_keepsRunning`: `onLost` of the Wi-Fi, idle 5 s, Wi-Fi back: no `false` call.
  - `// SPEC:run-conditions:R09` `wifiDrop11s_stops`: `onLost`, idle 11 s: one `false` call, and none before 10 s.
  - `// SPEC:run-conditions:R09` `forceStop_actsAtOnce`: the force-stop pref plus an `updateShouldRunDecision()` call gives `false` with no idle.
- [ ] Run `./gradlew --no-daemon :app:testDebugUnitTest --tests "*RunConditionMonitorTest"`. Expected: compile failure on the package-private constructor.
- [ ] Write the code.
- [ ] Run `./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug > build-logs/rc-task5.log 2>&1`. Expected: `BUILD SUCCESSFUL`, 0 failures, no new lint errors.
- [ ] Commit: `feat(runconditions): detect network changes with callbacks and a stop delay`.

### Task 6: Charger freeze (only if you say yes under "Questions for you")

**Files:** change `RunConditionMonitor.java`, `docs/specs/run-conditions/SPEC.md` (R11), `SPEC-history.md`; test in `RunConditionMonitorTest.java`
**Depends on:** Task 5

- [ ] Write `powerConnected_doesNotBlockMainThread`: sending `ACTION_POWER_CONNECTED` returns within 100 ms of wall time, and idling 5 s triggers a decision.
- [ ] Run it. Expected: fails, the broadcast takes about 5 000 ms.
- [ ] Replace `SystemClock.sleep(5000)` with posting `updateShouldRunDecision()` 5 s later on the monitor's handler. Reword R11 to "charging detection keeps its receivers; it re-checks 5 seconds after a plug event without blocking", with a history row.
- [ ] Run `:app:testDebugUnitTest`. Expected: `BUILD SUCCESSFUL`.
- [ ] Commit: `fix(runconditions): stop blocking the main thread on charger events`.

### Task 7: Device runs

**Files:** none changed; results go to this plan's `## Progress`
**Depends on:** Task 5 (and Task 6 if done)

Build with `./gradlew --no-daemon :app:assembleDebug`. The debug build is `com.github.catfriend1.syncthingfork.debug`, so it installs beside the release. Never install anything over `com.github.catfriend1.syncthingfork`, and never uninstall it. Turn on verbose logging in the debug app, and watch `adb logcat -s RunConditionMonitor SyncthingService`; a decision shows as `shouldRun decision changed to`.

- [ ] Emulators `Api32_Alarm` and `Medium_Phone` (reuse them, create none): "Run on Wi-Fi" only. `adb shell svc wifi disable` gives a stop after about 10 s; `adb shell svc wifi enable` gives a start within seconds. Mark the emulator's Wi-Fi as metered in Settings with "unmetered Wi-Fi only" on: Syncthing stops; unmetered again: it starts.
- [ ] Your phone, debug build: Wi-Fi to mobile handover (walk out of range, or turn Wi-Fi off) under "Wi-Fi only" stops; turning on your VPN over Wi-Fi keeps Syncthing running and the log shows the Wi-Fi in the snapshot (confirms A-R06); a Wi-Fi without internet (a laptop hotspot with no uplink) with mobile data on and disallowed stops with the new reason, and with mobile data off it runs; toggling Wi-Fi off and on within 5 s does not stop Syncthing.
- [ ] If A-R06 holds, change it to `D-R06` with the device evidence and a history row (`kind · A → D`); if it fails on a device, stop and bring the log to the owner.
- [ ] Log each run in `## Progress` with the device, Android version and result.

## Proof for the whole plan

- `ANDROID_HOME="$HOME/AppData/Local/Android/Sdk" ./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug` ends in `BUILD SUCCESSFUL`, 0 failures, 7 skipped as before.
- `speccheck docs/specs/run-conditions/SPEC.md` reports 0 BLOCK and 0 REPORT, and every rule with a unit-test acceptance has a test tagged `SPEC:Rnn`.
- The device runs of Task 7 are logged in `## Progress`, with A-R06 confirmed or reported.

## Progress

- Start: branch `feat/run-condition-network` from `6923787c`; spec committed as `c2d42b92`. Inline mode, whole-branch review at the end.
- Owner said "Run it" without answering the two questions, so the plan's defaults hold: Task 6 is skipped, and the VPN rows of Task 7 run on the phone only.
- Task 1: done — commit 3df797e8 — :app:testDebugUnitTest → BUILD SUCCESSFUL, 149 tests, 7 skipped, 0 failed. Until Task 5, the monitor maps a not-connected active network to NO_ACTIVE_NETWORK (DISCONNECTED is gone).
- Task 3: done — commit 5489dd00 — StopDelayTest → 6 tests, 0 failures (plan named 5; added clear_dropsHolds).
- Task 2: done — commit f16119cd — :app:testDebugUnitTest → BUILD SUCCESSFUL, 166 tests, 7 skipped, 0 failed. RunConditionExplanationTest's reason list gained reason_wifi_without_internet.
- Task 4: done — commit 6317a815 — NetworkTrackerTest → 5 tests, 0 failures (added stop_dropsNetworks). The tracker also handles onAvailable, since onCapabilitiesChanged is only guaranteed to follow it from API 26.
- Task 5: decisions — the package-private constructor takes `@Nullable SharedPreferences` and the public one passes null to inject; `NetworkTracker.start()` now seeds the networks already connected (`getAllNetworks()`, VPNs skipped), so the first decision does not read flight mode before the callback reports (test `start_seedsConnectedNetworks_skipsVpn`); `ContentResolver.addStatusChangeListener` is wrapped in try/catch, because Robolectric 4.18.1 has no shadow for it on SDK 37 and the 15-minute re-check covers a missed auto-sync toggle; force-stop is read from the decision's reasons; per-object holds post a re-evaluation for when they end. Added `objectConditions_heldDuringShortDrop`.
- Task 5: note — a 10-minute shell timeout ended one run early; a test JVM from another project was spinning the CPU at the time (left alone). A JUnit `Timeout` rule does not work with Robolectric's paused looper and was removed.
- Task 5: bug found by `objectConditions_heldDuringShortDrop` — a folder hold that ended while Syncthing was stopped kept `nextCheckAt()` due, so the monitor re-posted itself with no delay forever (main thread spinning). Fixed: `StopDelay.nextCheckAt(long nowMs)` returns only ends after `nowMs`; test `expiredHoldNotChecked_notReportedAgain`.
- Task 5: done — commit 74013838 — :app:testDebugUnitTest :app:lintDebug → BUILD SUCCESSFUL, 181 tests, 7 skipped, 0 failed; lint 0 errors (fixed one new WifiManagerPotentialLeak warning after the run).
- Task 7: blocked — `:app:assembleDebug` fails in `:syncthing:buildNative` ("go is not available on the PATH"; no prebuilt native libraries for syncthing commit d6d0181f). The APK must come from CI (needs the branch pushed and a PR) or a local Go install; asking the owner. Two OnePlus phones are connected (LE2123 API 34, A6013 API 30), each with the release app; untouched.
- Review (fresh subagent, 6923787c..74013838): 3 High, 1 Medium, 5 Low. Fixed in 68a03626, each with a test that failed first: H1 folder hold carried over a stop (`objectHold_notCarriedOverAStop`, `clearExcept_keepsOnlyThatKey`); H2 re-check read a callback-only map (`screenOn_catchesMissedWifiLoss`, `snapshot_dropsNetworkAfterMissedLost`, `snapshot_takesMeteredFromAndroidNow`); H3 roaming regression (`unvalidatedWifi_roamingDisallowed_doesNotRun`; R07 row wording updated, owner to confirm). M4 (seeded network lost before registration) is fixed by H2's change. :app:testDebugUnitTest :app:lintDebug → BUILD SUCCESSFUL, 186 tests, 7 skipped, 0 failed, lint 0 errors.
- Left open (Low): L5 a time-schedule stop is held 10 s, which can delay the next window up to the sync duration; L6 the 15-minute re-check counts awake time only (Handler, not AlarmManager); L7 Ethernet without internet counts while mobile carries traffic; L8 the explanation shows unmet reasons during a hold; L9 no per-object test for every R07 row, no VPN-through-callback test, API 23-30 paths untested in Robolectric.
- speccheck --base with the new spec reports 13 BLOCKs that all come from backup tests: SPEC:Rnn tags carry no spec name, so the backup spec's R11/R12 collide with this spec's. Without --base: clean.
- Owner, 2026-11-25: confirmed the R07 roaming change; chose push + draft PR so CI builds the debug APK; asked to fix L5 (time-schedule stop acts at once).
- L5 fixed in 28b96f50 (`timeWindowEnd_stopsAtOnce`; R09 reworded, owner-approved). :app:testDebugUnitTest :app:lintDebug → BUILD SUCCESSFUL, 187 tests, 7 skipped, 0 failed; lint 0 errors; speccheck clean.
- Pushed `feat/run-condition-network` (28b96f50) and opened draft PR https://github.com/haziaferi/syncthing-fork-fork/pull/8; its Build Debug run produces the debug APK for Task 7.
- Task 7: emulators not usable — `Api32_Alarm` exits right after "WHPX ... operational" (tried normal, `-read-only`, `-memory 1536 -gpu swiftshader_indirect`; a stale `hardware-qemu.ini.lock` from 2026-11-23 sits in the AVD folder; 3.7 GB RAM free). Owner approved the phones instead: OnePlus LE2123 (API 34), A6013 (API 30), CPH2621 (API 36), each with the release app; the debug build installs as `com.github.catfriend1.syncthingfork.debug` beside it (CI artifact from run 45508990767).
- Task 7, LE2123 (API 34), APK 28b96f5: Wi-Fi on → started +3.0 s (unvalidated Wi-Fi, no mobile network: counts, R07 row 2). Wi-Fi off → did NOT stop: onLost arrived at +0.5 s while getAllNetworks() still listed the Wi-Fi being torn down, and no later callback followed (bug from the review fix that reads Android's list). Fixed: the tracker keeps a set of networks reported lost and skips them until Android drops them (test `lostBeforeAndroidDropsIt_snapshotSkipsIt`, failed first).
- Task 7, APK 078f772 (CI run 45513655022), Wi-Fi only + unmetered only (defaults), verbose log on; logs in `build-logs/rc-device-<serial>/`:

  | Check | LE2123 (API 34) | A6013 (API 30) |
  |---|---|---|
  | Wi-Fi off | loss seen +0.6 s, stopped +10.6 s | loss seen +0.6 s, stopped +10.7 s |
  | Wi-Fi on | started +2.9 s | started +4.9 s |
  | Wi-Fi off 4 s, then on (about 5.5 s without network) | kept running | kept running |
  | Wi-Fi marked metered | stopped 10 s after the reconnect showed it metered | not run (`cmd wifi status` format differs on API 30) |
  | Metered setting back to automatic | started +0.6 s, from the capability change alone | not run |

- Not run on the phones: both have no SIM, so no mobile handover or Wi-Fi-without-internet-plus-mobile check (R07 rows 3 and 5 rest on unit tests). VPN (A-R06) not checked: the only VPN app (Tor VPN on the A6013) has never been set up, and its onboarding and Android's VPN consent are the owner's to accept. CPH2621 (API 36) disconnected before its turn. A-R06 stays assumed.
- Left on the phones: the debug app `com.github.catfriend1.syncthingfork.debug` (verbose log on) on LE2123 and A6013; release apps untouched; the LE2123 Wi-Fi metered setting restored to automatic.
- Task 7, VPN (A6013, Tor VPN set up by the owner): default network was the VPN (`WIFI|VPN`); snapshot held the validated, unmetered Wi-Fi; Syncthing kept running; Wi-Fi off under the VPN stopped it at +10.7 s and Wi-Fi on restarted it at +4.8 s. A-R06 → D-R06 in af7487b3. Task 7: done, with the gaps listed above (no SIM, emulators, API 23–29/31+ VPN).
- Pre-merge gap fixes (owner: "Fix recommended set", 4 & 5 accepted), 10949ae9: R07 per-object rows test, R05 VPN-to-decision monitor test, tracker tests at API 24 and 30 (Robolectric 4.18.1 has no API 23; owner approved downloading the API 24 image), R04 reworded to awake time, R13 lists the phone runs. :app:testDebugUnitTest :app:lintDebug → BUILD SUCCESSFUL, 194 tests, 7 skipped, 0 failed; lint 0 errors; speccheck clean.
- Merged: PR #8 squash-merged as edca0259 on 2026-11-25 after all checks passed; branch deleted.
- Cleanup 2026-11-25: debug app uninstalled from LE2123 and A6013 at the owner's request; release apps still installed on both.
