# Purpose map (2026-11-25)

The purpose map says why each part of the app is opened, how it is used, and which design requirements lead there. The design spec in this folder builds on it. The owner confirmed every value on 2026-11-25. Section 3 records the proposals that came out of the map; the spec holds the decisions.

## 1. Axes

The first six axes are the standard purpose profile. Axes 7 to 12 are specific to this app: what a background sync app with system surfaces needs that the standard profile does not cover.

| # | Axis | Scale | Why this app needs it |
|---|---|---|---|
| 1 | Purpose | why someone opens it, in a few words | anchors every other value |
| 2 | Tone | 0 calm … 1 urgent | "not running" is usually the user's own rule working, not a fault |
| 3 | Comparison referent | none · self-history · fixed-target · social-peer | sync progress compares against "up to date"; the run decision compares against nothing |
| 4 | Frequency | several a day · daily · weekly · per setup · rare | decides what sits closest to hand |
| 5 | Stakes | what a mistake costs | spans from nothing to lost files |
| 6 | Data exposure | what the surface reveals | device names, folder names and Wi-Fi names appear on the lock and home screens |
| 7 | Attention budget | glance (under 3 s) · check (under 30 s) · task (minutes) · investigate | sets text length, density and how much is shown |
| 8 | Surface owner | app-drawn · system template (notification, tile, widget, shortcut) | system templates fix the type, the shape and often the colour |
| 9 | Explains the decision | none · state only · state + one reason · all reasons | the run-condition rewrite (run-conditions D-R10) needs a place to show "why" |
| 10 | Control consequence | none · reversible · costs data or battery · destroys or overwrites | sets confirmations, distance between controls, and where warnings sit |
| 11 | Expertise | everyone · Syncthing-literate · expert mode | many terms (introducer, versioning, ignore patterns) mean nothing to a newcomer |
| 12 | Input | touch · D-pad (Android TV) | the app is a TV launcher activity, and Settings and Recent changes already branch on TV |

## 2. Modules

The modules are grouped by purpose, not by screen. One screen can serve two modules: the Status tab is both Glance and Run control.

| Module | Purpose | Surfaces today (+ planned) | Tone · referent | Frequency | Attention | Stakes | Explains | Consequence | Expertise | Input |
|---|---|---|---|---|---|---|---|---|---|---|
| M1 Glance status | is it syncing, and if not, why | persistent notification, toolbar dot and ring, Status tab, drawer header (+ widget) | 0.25 · none; triggered 0.7 · fixed-target on a fault | several a day | glance | low: a wrong belief that it synced | state + one reason | none | everyone | touch; TV via the app |
| M2 Run control | run now, stop now, or follow my rules | Status tab segmented button, QS Force and Schedule tiles (+ widget toggle, shortcuts) | 0.4 · none | a few a week | glance | data cost when forced on mobile data; battery | state + one reason, and what a tap will do | costs data or battery; always one tap back to "follow rules" | everyone | touch |
| M3 Folders and devices | what is in sync with whom; unstick a folder | Folders tab, Devices tab, Recent changes | 0.5 · fixed-target (up to date); triggered 0.75 on a folder error | weekly or bi-monthly (the app itself) | check | missed or conflicting files | state per row | reversible (rescan, pause) | Syncthing-literate | touch, D-pad |
| M4 Setup and pairing | connect a device, share a folder | Folder and Device editors, QR scanner, device ID dialog, folder picker, folder type, versioning and pull order dialogs, share-request notifications | exempt (task flow) | per setup | task | **files lost**: folder type, delete, ignore patterns, accepting a stranger | none | destroys or overwrites | Syncthing-literate | touch, D-pad |
| M5 Rules and settings | decide when and how it runs | Settings root and subscreens, Run conditions, per-folder and per-device conditions | exempt (reference) | rare, then tuning | task | data cost if a rule is wrong | what the rules decide right now (SPEC R43) | costs data or battery | everyone; experts in Syncthing options | touch, D-pad |
| M6 Safety net | never lose my setup | Import and Export (+ automatic backup, restore, 7-day notification) | 0.15 · self-history (last good backup); triggered 0.6 after 7 days without a good backup | set once; restore once per phone | check | **setup lost**, or overwritten by an import | last good backup or the failure | overwrites the configuration (import, restore) | everyone | touch, D-pad |
| M7 Diagnostics | find out what went wrong | Log, Troubleshooting, Web GUI, About, Custom certificate, Experimental | exempt (reference) | rare | investigate | database reset, certificate swap | n/a | destroys (reset database, reset indexes) | expert | touch, D-pad |
| M8 First run | get it working and trusted | Onboarding (7 pages), key generation | 0.2 · none | once | task | sync broken if a permission is denied | why each permission is needed | reversible | everyone | touch, D-pad |
| M9 Capture and share | put a file into a synced folder | Share target "Save to Syncthing", camera (QS tile) | exempt (task flow) | occasional | task | file in the wrong folder | none | reversible | everyone | touch |

**Owner answers, 2026-11-25:**
- Use is glance-first: the notification and tiles several times a day, and the app itself opened "weekly or bi-monthly". The glance surfaces lead the design. The main screen serves a check and a fix, not daily browsing.
- A stop caused by the user's own run conditions looks calm, and real faults look different (§3, item 1).
- Every app-drawn screen keeps working by D-pad on Android TV.
- The 12 axes are enough. No harvest from other projects.

**Purpose-integrity check:** the calm tone for "not running" serves the user. It reports their own rule working and keeps real faults visible as faults. It does not hide anything to keep them engaged. Pass.

## 3. What the map implies (proposals, not decisions)

1. **Two states that look different.** A stop caused by the user's own rules (sleeping on mobile data, outside the schedule) uses the calm register: a neutral colour, the reason in plain words, no warning icon. A real fault (crash, folder error, storage access lost, backup failing for 7 days) uses the triggered register: the error role and an action. The Status tab, toolbar dot, notification and widget all follow the same split.
2. **The reason travels with the state.** Every glance surface (M1) that shows "not running" also shows one reason, the first one the evaluator reports, and the Status tab shows all of them. Today only the Status tab shows a reason (audit item 5).
3. **The system surfaces lead the visual system.** The notification, tiles, widget and shortcuts sit inside Android's own templates. The app's tokens must survive dynamic colour and the monochrome icon, so brand recognition comes from the node logo and the wording, not from a fixed blue.
4. **Destructive controls are spaced from everyday ones.** Glance surfaces carry no destructive control (M1, M2). Setup, backup and diagnostics (M4, M6, M7) put the warning above the action and ask for confirmation.
5. **Expert vocabulary sits behind Expert mode.** Experts see Syncthing terms. Everyone else sees plain names with the term in the summary.
6. **TV parity.** Every app-drawn screen works by D-pad. The widget and shortcuts are phone-only, because Android TV has neither.

## 4. MEASURED

```
MEASURED
first screen   not measured: no emulator on this host, and screens were not rendered.
               [estimate] Status tab: segmented control 100 dp + ~4 list rows at 360x800 before the fold (fragment_status.xml, StatusFragment.java:212-256)
pane fill      not measured
occlusion      [estimate] Exit FAB (56 dp) over the last Folders/Devices row when autostart is off (activity_main.xml:32-44)
checks         ck layout did not run: the screens are Android views and Compose, not HTML
```
