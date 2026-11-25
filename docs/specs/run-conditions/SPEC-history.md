# Run-condition network detection spec history

Not a source of current rules: `SPEC.md` is. One line per change, newest at the bottom. Retired wording is in git.

- 2026-11-24 · R01 · added · jobs and how often · builder
- 2026-11-24 · R02 · added · network detection only · owner
- 2026-11-24 · R03 · added · network callbacks replace CONNECTIVITY_ACTION · builder
- 2026-11-24 · R04 · added · re-check on screen-on and every 15 minutes · owner
- 2026-11-24 · R05 · added · snapshot of physical networks, VPN left out · owner
- 2026-11-24 · R06 · added · physical networks visible under a VPN, to confirm on devices · builder
- 2026-11-24 · R07 · added · which network counts, no-internet Wi-Fi rule · owner
- 2026-11-24 · R08 · added · metered, roaming and name from the counting network · builder
- 2026-11-24 · R09 · added · 10-second stop delay, force-stop at once · owner
- 2026-11-24 · R10 · added · reason for a no-internet Wi-Fi that does not count · builder
- 2026-11-24 · R13 · added · unit, emulator and phone levels · owner
- 2026-11-24 · R11 · added · charging detection out of scope · owner
- 2026-11-24 · R12 · added · sharing a hotspot out of scope · builder
- 2026-11-25 · R07 · changed · roaming setting applies to mobile data allowed for a no-internet Wi-Fi · owner
- 2026-11-25 · R09 · changed · time-window end stops at once; object waits end at a stop · owner
- 2026-11-25 · R06 · kind · A → D, confirmed on Android 11 with Tor VPN over Wi-Fi · builder
- 2026-11-25 · R04 · changed · 15 minutes of awake time, deep sleep not counted · owner
- 2026-11-25 · R13 · changed · phone runs replace emulators; API 24 and 30 unit tests · owner
