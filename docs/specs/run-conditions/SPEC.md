# Run-condition network detection spec

Scope: how the app detects the network it is on, and when it starts, stops, pauses or resumes Syncthing because of it, in the haziaferi/syncthing-fork-fork build of Syncthing-Fork.

History: `SPEC-history.md`, one line per change. It is never a source of current rules.

Kinds: O owner rule · D decided · I inherited · A assumed · Q open.

## Rule index

| ID | Name | Section |
|---|---|---|
| D-R01 | Jobs and how often | 1. Purpose and jobs |
| D-R02 | Scope | 1. Purpose and jobs |
| D-R03 | Network callbacks | 2. Detecting changes |
| D-R04 | Re-check without a callback | 2. Detecting changes |
| D-R05 | Network snapshot | 3. Reading the network |
| D-R06 | Physical networks stay visible under a VPN | 3. Reading the network |
| D-R07 | Which network counts | 3. Reading the network |
| D-R08 | Metered, roaming and Wi-Fi name | 3. Reading the network |
| D-R09 | Stop delay | 4. Acting on the decision |
| D-R10 | Explanation of the decision | 4. Acting on the decision |
| D-R13 | Test plan | 5. Test plan |
| D-R11 | Charging detection | Out of scope |
| D-R12 | Sharing a hotspot | Out of scope |

## Glossary

| Term | Meaning |
|---|---|
| run conditions | the user's settings that decide when Syncthing runs, in Settings and per folder or device, evaluated by `RunConditionEvaluator` |
| decision | the result of evaluating the run conditions: run or not, with the reasons shown in the app |
| per-object conditions | a folder's or device's own run conditions, `RunConditionEvaluator.checkObjectConditions()` |
| network snapshot | what the app knows about the connected networks at one moment, passed to the evaluator as `DeviceConditions` |
| default network | the network Android sends internet traffic through, `ConnectivityManager.getActiveNetwork()` |
| physical network | a connected network that is not a VPN |
| validated | a network Android has confirmed reaches the internet, `NET_CAPABILITY_VALIDATED` |
| stop delay | the time conditions must stay unmet before Syncthing stops or an object pauses (R09) |

## 1. Purpose and jobs

The app learns about network changes from the `CONNECTIVITY_ACTION` broadcast and reads one network, the active one, through `getActiveNetworkInfo()` (`RunConditionMonitor.java:127`, `:470`). Both are deprecated since Android 9 and 10. Upstream users report Wi-Fi changes that go unnoticed until battery saver is toggled (researchxxl/syncthing-android#405), Wi-Fi wrongly seen as metered (#350, #345), and a VPN hiding the Wi-Fi beneath it. This spec replaces how the network is detected. The decision logic (`RunConditionEvaluator`) keeps its order and its other inputs.

### D-R01 · Jobs and how often

**Rule:** The rewrite serves the jobs below.

**Values:**

| Job | Who | How often | Done when |
|---|---|---|---|
| As a user who restricts Syncthing to Wi-Fi, unmetered Wi-Fi or non-roaming data, I never pay for data I disallowed | users with metered or limited mobile data | on every network change | Syncthing never runs over a network the conditions disallow, beyond the stop delay |
| As a user, Syncthing starts by itself as soon as I reach an allowed network | every user with network conditions | several times a day | Syncthing starts within seconds of the allowed network connecting |
| As a user with a VPN, the conditions see the network my VPN runs over | users of an always-on or app VPN | continuously | a VPN over Wi-Fi counts as that Wi-Fi |

**Evidence:** researchxxl/syncthing-android#405, #350, #345, read 2026-11-24.

**Acceptance:** none: the jobs are checked through R03 to R13.

**Since:** 2026-11-24 · builder · own words

### D-R02 · Scope

**Rule:** The rewrite changes network detection only: Wi-Fi, mobile data, Ethernet, VPN, metered, roaming and the Wi-Fi name. Charging, power saving, the time schedule, Android's auto-sync toggle, and the order in which the evaluator checks conditions stay as they are.

**Why:** The reported faults are in network detection. The only charging report (#58, Xiaomi) was Android itself returning a stale battery state, fixed by a reboot, and no OnePlus charging fault is on record.

**Acceptance:** none: a scope decision, visible in the pull request's diff.

**Not:** Reworking charging detection: it would not help when the OS reports the wrong state, as in #58.

**Since:** 2026-11-24 · owner · agreed rewording

## 2. Detecting changes

### D-R03 · Network callbacks

**Rule:** The run-condition monitor learns about network changes from a `ConnectivityManager.NetworkCallback` registered for every network, from the moment it is created until `shutdown()`; nothing listens for `CONNECTIVITY_ACTION`. A callback rebuilds the network snapshot and re-evaluates the decision on the main thread.

**Why:** `CONNECTIVITY_ACTION` is deprecated and, per #405, not always delivered to a running app; callbacks report each network's own changes, including its metered state.

**Evidence:** `NetworkCallback` and `registerNetworkCallback(NetworkRequest, NetworkCallback)` exist from API 21, below the app's `minSdk` 23 (`gradle/libs.versions.toml:5`).

**Acceptance:** A unit test creates the monitor, makes a Wi-Fi network appear through the callback, and sees the decision change; after `shutdown()` the callback is unregistered.

**Since:** 2026-11-24 · builder · own words

### D-R04 · Re-check without a callback

**Rule:** While the monitor exists, it also re-evaluates the decision when the screen turns on, and after 15 minutes of awake time since the last evaluation. Time the device spends in deep sleep does not count toward the 15 minutes.

**Why:** A vendor that drops callbacks would otherwise leave the decision wrong until something else changes (#405). The screen-on check covers a wake the user causes; the timed check covers a phone left awake.

**Acceptance:** A unit test advances the clock 15 minutes after an evaluation and sees a new evaluation, and sees one when a screen-on broadcast arrives.

**Not:** Callbacks only: a missed callback leaves the state wrong with no limit. A wake-up alarm every 15 minutes: it would wake a sleeping phone only to re-check, at a cost in battery.

**Since:** 2026-11-25 · owner · agreed rewording

## 3. Reading the network

### D-R05 · Network snapshot

**Rule:** The snapshot holds every connected physical network with its kind and its capabilities, and which of them is the default network. VPN networks are left out: the conditions judge the physical networks a VPN runs over. A network's kind follows the table.

**Values:**

| Transport of the network | Kind |
|---|---|
| `TRANSPORT_WIFI` | Wi-Fi |
| `TRANSPORT_ETHERNET` | Ethernet |
| `TRANSPORT_CELLULAR`, `TRANSPORT_BLUETOOTH` | mobile |
| `TRANSPORT_VPN` | left out |
| anything else | other |

**Evidence:** Today a VPN is the active network and reads as `TYPE_VPN`, which falls through to `Transport.OTHER` (`RunConditionMonitor.java:491`), so "Run on Wi-Fi" sees no Wi-Fi.

**Acceptance:** A unit test with a VPN and a Wi-Fi network connected builds a snapshot with only the Wi-Fi, and the decision under "Run on Wi-Fi" is to run.

**Not:** A VPN as its own kind: Syncthing would run over a VPN only when no network kind is restricted.

**Since:** 2026-11-24 · owner · agreed rewording

### D-R06 · Physical networks stay visible under a VPN

**Rule:** With a VPN active, Android still reports the physical networks beneath it with their own transports and capabilities, so the snapshot holds them. The VPN's own network carries `TRANSPORT_VPN`, often together with the transport it runs over, and is left out by that flag.

**Evidence:** OnePlus A6013, Android 11 (API 30), Tor VPN over Wi-Fi, 2026-11-25: the default network was the VPN (`WIFI|VPN`). The snapshot held the Wi-Fi, validated and unmetered, and Syncthing kept running under "Wi-Fi only". Wi-Fi off stopped it after 10.7 s, and Wi-Fi on restarted it after 4.8 s, with the VPN still up (`build-logs/rc-device-8ad59cbb/t6`–`t8`). Not checked: API 23–29 and 31+ with a VPN.

**Acceptance:** In the device run, a VPN over Wi-Fi leaves the Wi-Fi network in the snapshot with its metered state.

**Since:** 2026-11-25 · builder · own words

### D-R07 · Which network counts

**Rule:** The conditions see Wi-Fi, mobile data or Ethernet as the table says. Flight mode means no physical network is connected.

**Why:** On a Wi-Fi without internet, Android sends internet traffic over mobile data, so counting that Wi-Fi under "Wi-Fi only" would let relays and global discovery use mobile data. Counting it when mobile data is allowed or off keeps LAN sync on a local-only Wi-Fi or a hotspot (#286).

**Values:**

| Situation | Counts as |
|---|---|
| a validated Wi-Fi is connected | Wi-Fi |
| a Wi-Fi without internet is connected, and mobile data is allowed by the conditions being checked (with the app's roaming setting permitting it while roaming) or no mobile network is connected | Wi-Fi |
| a Wi-Fi without internet is connected, mobile data is not allowed (or only while not roaming, and it is roaming), and a mobile network is connected | not Wi-Fi, with its own reason in the explanation |
| Ethernet is connected | Wi-Fi, unmetered, as today (`RunConditionEvaluator.java:318`) |
| a mobile network is the default network | mobile data |
| no physical network | flight mode |

**Acceptance:** Unit tests for each row, for the whole-app decision and for per-object conditions.

**Not:** Any connected Wi-Fi counts: under "Wi-Fi only", relays would use mobile data. Only the default network: a local-only Wi-Fi with mobile data off would still count, but with mobile data on and allowed it would read as mobile.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R08 · Metered, roaming and Wi-Fi name

**Rule:** Metered, roaming and the Wi-Fi name are read from the network that counts (R07), as the table says. A missing location permission still hides the name and gives the existing "location unavailable" reason.

**Why:** `isActiveNetworkMetered()` describes the default network, which is not the Wi-Fi when a VPN or mobile data carries the traffic (#350, #345).

**Values:**

| Input | Read from |
|---|---|
| metered | that Wi-Fi network's own `NET_CAPABILITY_NOT_METERED` |
| roaming | the mobile network that counts |
| Wi-Fi name, API 31 and later | that Wi-Fi network's `NetworkCapabilities` transport info |
| Wi-Fi name, API 23 to 30 | `WifiManager.getConnectionInfo()` |

**Acceptance:** Unit tests: an unmetered Wi-Fi with a metered default network runs under "unmetered Wi-Fi only"; a metered Wi-Fi does not; a whitelisted name runs, and a hidden name gives the location reason.

**Since:** 2026-11-24 · builder · own words

## 4. Acting on the decision

### D-R09 · Stop delay

**Rule:** Starting Syncthing and resuming a folder or device happen as soon as the conditions are met. Stopping Syncthing and pausing a folder or device happen only once the conditions have stayed unmet for 10 seconds; if they are met again within that time, nothing stops. The user's force-stop button and the end of a time-schedule window act at once, and a folder's or device's wait ends when Syncthing stops.

**Why:** Wi-Fi roaming and mobile handovers drop a network for a moment, and each drop would restart Syncthing or pause folders. The time schedule reschedules its next window from an immediate stop, so holding it would start the next window late.

**Acceptance:** Unit tests: the network drops for 5 seconds and returns, and Syncthing keeps running with folders unpaused; it drops for 11 seconds, and Syncthing stops; force-stop and a time-window end stop at once; a folder's wait is not carried over a stop.

**Not:** Stopping at once: each brief drop restarts Syncthing.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R10 · Explanation of the decision

**Rule:** The explanation keeps its existing reasons and adds one for a Wi-Fi without internet that does not count (R07): "Wi-Fi has no internet and mobile data is not allowed". Its wording is final after the app-wide design pass.

**Acceptance:** A unit test for R07's third row finds the new reason in the decision.

**Since:** 2026-11-24 · builder · own words

## 5. Test plan

### D-R13 · Test plan

**Rule:** Each level below passes before the pull request is merged.

**Values:**

| Level | Covers | Rules | Runs |
|---|---|---|---|
| Unit (Robolectric), at API 37 and, for the tracker's older paths, API 24 and 30 | callbacks, re-check, snapshot, which network counts, metered, roaming, name, stop delay, explanation | R03 R04 R05 R07 R08 R09 R10 | every commit, in CI's `testDebugUnitTest` |
| Owner's phones without SIM (OnePlus LE2123 Android 14, A6013 Android 11), `.debug` build from CI beside the release | Wi-Fi off and on, a short drop, metered toggle, VPN over Wi-Fi | R03 R06 R08 R09 | once before merge |

**Acceptance:** CI's Build / Build Debug check passes on the pull request, and the phone runs are logged in the plan's progress section.

**Not:** Emulator runs: the emulators do not start on the build host (they exit after the hypervisor starts). Mobile data checks on a device: the phones have no SIM, so the handover and Wi-Fi-without-internet-plus-mobile rows rest on unit tests.

**Since:** 2026-11-25 · owner · agreed rewording

## Acceptance list

- R03 · Network callbacks: A unit test creates the monitor, makes a Wi-Fi network appear through the callback, and sees the decision change; after `shutdown()` the callback is unregistered.
- R04 · Re-check without a callback: A unit test advances the clock 15 minutes after an evaluation and sees a new evaluation, and sees one when a screen-on broadcast arrives.
- R05 · Network snapshot: A unit test with a VPN and a Wi-Fi network connected builds a snapshot with only the Wi-Fi, and the decision under "Run on Wi-Fi" is to run.
- R06 · Physical networks stay visible under a VPN: In the device run, a VPN over Wi-Fi leaves the Wi-Fi network in the snapshot with its metered state.
- R07 · Which network counts: Unit tests for each row, for the whole-app decision and for per-object conditions.
- R08 · Metered, roaming and Wi-Fi name: Unit tests: an unmetered Wi-Fi with a metered default network runs under "unmetered Wi-Fi only"; a metered Wi-Fi does not; a whitelisted name runs, and a hidden name gives the location reason.
- R09 · Stop delay: Unit tests: the network drops for 5 seconds and returns, and Syncthing keeps running with folders unpaused; it drops for 11 seconds, and Syncthing stops; force-stop and a time-window end stop at once; a folder's wait is not carried over a stop.
- R10 · Explanation of the decision: A unit test for R07's third row finds the new reason in the decision.
- R13 · Test plan: CI's Build / Build Debug check passes on the pull request, and the phone runs are logged in the plan's progress section.

## Out of scope

### D-R11 · Charging detection

**Rule:** Charging detection keeps its current code and receivers.

**Why:** The only report (#58) was Android returning a stale battery state; the app read it correctly.

**Acceptance:** none: a scope boundary, held by R02.

**Since:** 2026-11-24 · owner · agreed rewording

### D-R12 · Sharing a hotspot

**Rule:** The phone sharing its own hotspot is not a Wi-Fi connection for the run conditions; only networks the phone joins count.

**Why:** A shared hotspot is not a network the phone connects to, and Android reports no Wi-Fi network for it.

**Acceptance:** none: a scope boundary.

**Since:** 2026-11-24 · builder · own words
