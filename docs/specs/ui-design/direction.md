# Design direction: the owner's rulings

The app-wide design pass reads this file first and never re-raises a note it records. The purpose profile is in `purpose-map.md`, beside it. The rules built on these rulings are in `SPEC.md`.

Pass count: 2 (started 2026-11-25; the backup screen was critiqued by three independent passes plus a confirmation pass the same day, with working files in `build-logs/ui-critique-2026-11-25/`).

## Rulings, 2026-11-25

| # | Topic | Ruling |
|---|---|---|
| 1 | Use pattern | Glance first. The notification and tiles are checked several times a day. The app itself is opened "weekly or bi-monthly". |
| 2 | Stop by the user's rules | Looks calm: a neutral colour, a pause icon and the reason in plain words. Real faults use the error role and offer an action. |
| 3 | Android TV | Every app-drawn screen works by D-pad. The widget and shortcuts are phone-only. |
| 4 | Axes | The 12 axes in `purpose-map.md`. No harvest from other projects. |
| 5 | Colour source | The fitted brand palette (below) is the default on every Android version, light or dark following the system. On Android 12 and later, a "Use wallpaper colours" switch in User interface settings turns on Material You, built by the app at contrast level 0.5, or 1.0 when the system asks for high contrast. |
| 6 | Palette review | Every palette is fitted by visual-craft, then checked by color-optimization-core. Done for the fallback palette on 2026-11-25 (below). |
| 7 | Emoji | Dropped. Status is carried by an icon, its role colour and a plain word. |
| 8 | Wording | Sentence case everywhere and no ALL CAPS buttons, with one term list (SPEC.md). British spelling, as the strings already use ("Behaviour"). |
| 9 | XML screens | One token set for both stacks. The XML screens get a Material 3 bridge theme now and move to Compose when they are next reworked. Nothing is rewritten for looks alone. |
| 10 | Main navigation | A Material 3 navigation bar with Status, Folders and Devices, Status first. The drawer goes. On TV the bar becomes a navigation rail. |
| 11 | Shape | Material 3 defaults: pill buttons, 12 dp cards, 28 dp dialogs. |
| 12 | Typeface | The system font, with Material 3's type scale. |
| 13 | Settings home of backup | Import & Export is renamed "Backup and restore" and holds the automatic-backup controls. Backup spec R15 and the tests that cite it change with it, in the implementation plan. |
| 14 | Backup on Status | The Status destination shows a backup card only while the last automatic backup failed. |
| 15 | Drawer items | Device ID (QR icon) and Settings (gear) appear as top-bar icons on every destination. Recent changes goes in the Folders top bar. Web GUI, Restart and Exit go in the overflow menu. |
| 16 | After manual export or import | The screen stays open and shows a snackbar. A failure names its fix, the same way the automatic-backup failure does. |
| 17 | Backup screen, after the critique | All the critique's fixes apply: a separate error row that names its fix, snackbar actions named after the fix, Import moved into Restore as "Restore from export file", an empty restore state, a device-ID warning, no eye icon on the row, named path roots, and heading spacing. |
| 18 | A fix starts a backup | Returning from a fix (access granted, folder changed, password set) schedules a backup at once (a backup spec R13 change, made with its plan). |
| 19 | Backup status wording | The status tells "Up to date" apart from "Change waiting" (a backup spec R17 change, made with its plan). |
| 20 | Restore in onboarding | Not now. Restore stays in Settings. Recorded as a later idea. |
| 21 | Density | "Standard" follows the requirements' density rule, which is tighter than Material 3's one-line 56 dp. An optional "Compact" mode lives in User interface settings. |
| 22 | Run-state words | Running, Syncing, Paused by your rules, Running anyway, Paused by you, Starting, and Stopped for faults only. These words are the same on the notification, the tile, the widget and Status. The owner also asked for a glossary or a small manual for beginners. |
| 23 | Widget button | One button whose action follows the state: Running → Pause, Paused by your rules → Run anyway, and either forced state → Follow rules. The Quick Settings Force tile adopts the same logic. |
| 24 | Widget size | 4 × 1, resizable down to 2 × 1. |
| 25 | Shortcuts | Run anyway, Pause and Follow my rules, shown according to the state, plus Show device ID and Add device (scan QR). |
| 26 | Top bars | Small bars on the main destinations and editors, large collapsing bars in Settings, and no filled blue bars (SPEC R13). |
| 27 | Settings order | By how often each entry is opened, Backup and restore second, each entry with a summary (SPEC R15). |
| 28 | Reasons | The reason table in SPEC R21 is accepted. It also finalises run-conditions R10's wording. |
| 29 | Beginner help | A glossary screen with info icons in the editors, plus a "Where to begin" manual of four steps (SPEC R22). |
| 30 | Status layout | State card first, then fault cards, then details (SPEC R23). |
| 31 | Notification | The state word and a reason, the state button plus Exit, and the word only on the lock screen (SPEC R24). |
| 32 | Upstream sync | The fork's wording and theme live under new keys in fork-owned files. Upstream's resource files stay unchanged (SPEC R41). |
| 33 | Screen allocation | Every screen's elements are examined and allocated with visual-craft, the critique and other relevant skills before its plan. This includes the list actions and the Exit button (SPEC R42). |
| 34 | Override frequency | Run anyway, Pause and Follow rules are used a few times a week. |
| 35 | Live effect | The Run conditions screens show a live state line above the rules (SPEC R43). |

Rule notes given: none yet.

## Brand palette (fitted 2026-11-25)

Source: the launcher icon (`res/mipmap-xxxhdpi/ic_launcher.png`), blues around `#0f8ecc`, plus the app's existing blue-grey `#5e7c8b` and amber `#ffc107`. Only lightness moved, except muted text and light-mode success, whose saturation and green were adjusted so the status colours stay apart for every colour-vision type. Every ratio is measured against the surface of the same mode.

| Role | Light | Ratio | Dark | Ratio | Use |
|---|---|---|---|---|---|
| surface | `#f6fafd` | — | `#0f1416` | — | the ground |
| text | `#0b2230` | 15.6:1 | `#dfe8ee` | 14.9:1 | all body text |
| primary | `#005b86` | 7.0:1 | `#39a8e7` | 7.0:1 | buttons, links, running state; white label on it 7.0:1 (light), surface label 7.0:1 (dark); moved on 2026-11-26 to `#004872` / `#68ccff` for 7:1 on dialog backgrounds (SPEC R05) |
| muted | `#485861` | 7.0:1 | `#8ea2ae` | 7.0:1 | secondary text, the paused-by-rules state |
| warning | `#b78a00` | 3.0:1 | `#ffc107` | 11.4:1 | icons only in light mode; never text there |
| error | `#b00014` | 7.0:1 | `#ff746a` | 7.0:1 | fault icon and fault text |
| success | `#247a3c` | 5.1:1 | `#2e7d32` | 3.6:1 | icons only |
| brand mark | `#0f8ecc` | 3.6:1 | `#0f8ecc` | 4.9:1 | the logo only |

Checks:
- Colour-vision separation between the status roles (info/primary, muted, warning, error, success), worst pair CIEDE2000 after simulation: light 10.3 (error/success, deuteranopia), dark 14.3 (error/success, protanopia). The floor is 10. Tritanopia is not held to the floor (light info/success 7.4); the icon shape separates those states.
- Harmony: split-complementary, template fit 0.80 (heuristic).
- Screen fitness: all convention checks pass. The Sci-Fin project's outdoor and vivid-headroom rules are reported for information only. Under them, warning and secondary sit under 7:1 outdoors, and primary and accent sit near the gamut edge.
- The full Material 3 scheme (containers, outline, surface steps) is generated from seed `#0f8ecc` with Material Color Utilities at contrast level 0.5, and the roles above are pinned over it. The generated containers are checked again when the scheme is built (implementation plan).
