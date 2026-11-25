# App-wide UI design spec

Scope: the look, structure and wording of every user-facing surface of the haziaferi/syncthing-fork-fork build of Syncthing-Fork: app screens, dialogs, the notification, Quick Settings tiles, the home-screen widget and launcher shortcuts.

History: `SPEC-history.md`, one line per change. It is never a source of current rules.

Kinds: O owner rule · D decided · I inherited · A assumed · Q open.

## Rule index

| ID | Name | Section |
|---|---|---|
| D-R01 | Jobs and how often | 1. Purpose and jobs |
| D-R02 | Scope of the design pass | 1. Purpose and jobs |
| D-R03 | Purpose profile and registers | 1. Purpose and jobs |
| D-R04 | Colour source | 2. Tokens and theme |
| D-R05 | Brand palette | 2. Tokens and theme |
| D-R06 | Status shown by more than colour | 2. Tokens and theme |
| D-R07 | Typography | 2. Tokens and theme |
| D-R08 | Shape | 2. Tokens and theme |
| D-R09 | Density | 2. Tokens and theme |
| D-R10 | One token set for both stacks | 2. Tokens and theme |
| D-R11 | Icons | 2. Tokens and theme |
| D-R12 | Main navigation | 3. Navigation and structure |
| D-R13 | Top bars | 3. Navigation and structure |
| D-R14 | Dialogs | 3. Navigation and structure |
| D-R15 | Settings root order | 3. Navigation and structure |
| D-R16 | Per-folder and per-device run conditions | 3. Navigation and structure |
| D-R42 | Screen-by-screen allocation | 3. Navigation and structure |
| D-R41 | Fork-owned strings and themes | 4. Wording |
| D-R17 | Wording style | 4. Wording |
| D-R18 | Terms | 4. Wording |
| D-R19 | Paths and long values | 4. Wording |
| D-R20 | Run-state words | 4. Wording |
| D-R21 | Reasons | 4. Wording |
| D-R22 | Help for beginners | 4. Wording |
| D-R23 | Status destination | 5. Status and glance surfaces |
| D-R24 | Persistent notification | 5. Status and glance surfaces |
| D-R25 | State button | 5. Status and glance surfaces |
| D-R26 | Home-screen widget | 5. Status and glance surfaces |
| D-R27 | Launcher shortcuts | 5. Status and glance surfaces |
| D-R43 | Live state on the Run conditions screen | 5. Status and glance surfaces |
| D-R28 | Backup screen layout | 6. Backup and restore screen |
| D-R29 | Automatic backup status | 6. Backup and restore screen |
| D-R30 | Backup failure row | 6. Backup and restore screen |
| D-R31 | Restore list and dialogs | 6. Backup and restore screen |
| D-R32 | Manual export and import feedback | 6. Backup and restore screen |
| D-R33 | Backup password dialog | 6. Backup and restore screen |
| D-R34 | Backup on Status and in the notification | 6. Backup and restore screen |
| D-R35 | Changes to other specs | 6. Backup and restore screen |
| D-R36 | Floors and targets | 7. Accessibility and TV |
| D-R37 | TV parity | 7. Accessibility and TV |
| D-R38 | Test plan | 8. Test plan |
| D-R39 | Web GUI content | Out of scope |
| D-R40 | Restore during onboarding | Out of scope |

## Glossary

| Term | Meaning |
|---|---|
| glance surface | a surface read in seconds without opening the app: the persistent notification, the Quick Settings tiles, the widget and the launcher shortcuts |
| run state | what Syncthing is doing as the user sees it, one of the words in R20 |
| reason | one sentence from the run-decision explanation (run-conditions R10), in a short form for glance surfaces and a long form for Status |
| fault | a stop or failure the user did not choose and that needs an action, as opposed to a pause caused by the user's own run conditions |
| brand palette | the fitted colour roles in R05, the default scheme on every Android version |
| wallpaper colours | Material You colours built from the wallpaper's source colour (R04) |
| density | Standard or Compact row spacing (R09) |
| Status destination | the first item of the main navigation bar (R12), which shows the run state, its reasons and faults |
| backup screen | the Settings screen "Backup and restore" (R28), formerly "Import and Export" |
| direction file | `docs/specs/ui-design/direction.md`, the owner's design rulings and the palette record |
| purpose map | `docs/specs/ui-design/purpose-map.md`, the purpose profile and its axes |

## 1. Purpose and jobs

The app grew two visual systems: Compose screens on Material 3 with dynamic colour (or stock purple below Android 12), and older XML screens on AppCompat with a light blue and amber palette. Words, case and terms drift between screens. The run decision is explained only on one tab. This spec gives the whole app one direction, built from the owner's rulings in the direction file and the purpose map, and fixes the layout and wording of the automatic-backup controls and of the coming widget and shortcuts.

### D-R01 · Jobs and how often

**Rule:** The design serves the jobs below, weighted by how often each is done. The glance surfaces lead; the app itself serves a check and a fix.

**Values:**

| Job | Surface | How often |
|---|---|---|
| As a user, I see whether Syncthing is syncing, and if not, why | notification, tiles, widget | several times a day |
| As a user, I make it run at once, pause it, or hand it back to my rules | widget, tiles, shortcuts, Status | a few times a week |
| As a user, I check folders and devices and fix a stuck one | Status, Folders, Devices | weekly or bi-monthly |
| As a user, I connect a device or share a folder | Folder and Device editors, QR scanner | once per device or folder |
| As a user, I set when Syncthing runs | Settings, Run conditions | rarely, then tuning |
| As a user, I keep a backup of my setup and restore it on a new phone | backup screen | set once; restore once per phone |
| As a user, I find out what went wrong | Log, Troubleshooting, Web GUI | rarely |

**Evidence:** The owner's answers, 2026-11-25: glance several times a day, app opened "weekly or bi-monthly", overrides a few times a week. Modules and their axes: purpose map.

**Acceptance:** none: the jobs are checked through the rules that serve them.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R02 · Scope of the design pass

**Rule:** This spec settles direction, structure and wording for every user-facing surface. Implementation plans build it screen by screen. No screen is rebuilt for looks alone: an XML screen takes the bridge theme (R10) at once and its full layout when it is next reworked.

**Why:** The owner asked for one app-wide design rather than single-screen placements, and for no rewrite that only changes looks.

**Acceptance:** none: a scope and working-practice decision.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R03 · Purpose profile and registers

**Rule:** Each surface takes the tone of its module in the purpose map. A pause caused by the user's own run conditions uses the calm register. A fault uses the triggered register: the error role, an icon and an action.

**Values:**

| Module | Tone (0 calm, 1 urgent) | Triggered state |
|---|---|---|
| Glance status | 0.25 | 0.7 on a fault |
| Run control | 0.4 | none |
| Folders and devices | 0.5 | 0.75 on a folder error |
| Safety net (backup) | 0.15 | 0.6 after 7 days without a good backup |
| First run | 0.2 | none |
| Setup, settings, diagnostics, capture | exempt | none |

**Evidence:** Purpose map, owner answers of 2026-11-25. The purpose-integrity check passed: the calm register reports the user's own rule and hides no fault.

**Acceptance:** On every glance surface and on Status, a pause by the user's rules shows no error or warning colour, and a fault shows the error role with an icon and an action.

**Since:** 2026-11-25 · owner · agreed rewording

## 2. Tokens and theme

### D-R04 · Colour source

**Rule:** The brand palette (R05), light or dark following the system theme, is the default on every Android version. On Android 12 and later, a "Use wallpaper colours" switch in User interface settings replaces it with wallpaper colours. The app builds that scheme itself from the wallpaper's source colour with Material Color Utilities at contrast level 0.5, or 1.0 when the system asks for high contrast.

**Why:** Wallpaper schemes at the standard level give muted text and primary only 4.5:1; at 0.5 they reach 7:1, the floor the brand palette meets.

**Values:**

| Role | Standard level (0) | Level 0.5 | Level 1.0 |
|---|---|---|---|
| onSurface | 7:1 | 11:1 | 21:1 |
| onSurfaceVariant | 4.5:1 | 7:1 | 11:1 |
| primary | 4.5:1 | 7:1 | 7:1 |
| error | 4.5:1 | 7:1 | 7:1 |
| outline | 3:1 | 4.5:1 | 7:1 |

**Evidence:** Contrast curves from `ColorSpec2021.java` and `ContrastCurve.java` in material-foundation/material-color-utilities (main), read 2026-11-25. `SchemeTonalSpot(Hct, boolean, double contrastLevel)` builds a scheme at a chosen level. Not checked: the 2021 `SpecVersion` constant name, and which artifact gives the app Material Color Utilities (no dependency provides it today; the copy inside Material Components is internal).

**Acceptance:** A unit test builds the wallpaper scheme from three seeds at level 0.5 and finds onSurfaceVariant, primary and error at 7:1 or more against surface; with the switch off, every screen uses R05's values.

**Not:** Android's own dynamic scheme as default: it gives 4.5:1 for muted text. Stock purple below Android 12, as today (`ApplicationTheme.kt:25-28`).

**Since:** 2026-11-25 · owner · agreed rewording

### D-R05 · Brand palette

**Rule:** The brand palette's roles hold the values below. The full Material 3 scheme (containers, outline, surface steps) is generated from seed `#0f8ecc` at contrast level 0.5, with these roles pinned over it.

**Values:**

| Role | Light | Ratio | Dark | Ratio | Use |
|---|---|---|---|---|---|
| surface | `#f6fafd` | — | `#0f1416` | — | ground |
| text | `#0b2230` | 15.6:1 | `#dfe8ee` | 14.9:1 | body text |
| primary | `#005b86` | 7.0:1 | `#39a8e7` | 7.0:1 | buttons, links, the running state; its label 7.0:1 |
| muted | `#485861` | 7.0:1 | `#8ea2ae` | 7.0:1 | secondary text, the paused state |
| warning | `#b78a00` | 3.0:1 | `#ffc107` | 11.4:1 | icons only in light mode |
| error | `#b00014` | 7.0:1 | `#ff746a` | 7.0:1 | fault icons and fault text |
| success | `#247a3c` | 5.1:1 | `#2e7d32` | 3.6:1 | icons only |
| brand mark | `#0f8ecc` | 3.6:1 | `#0f8ecc` | 4.9:1 | the logo only |

**Evidence:** Source: `res/mipmap-xxxhdpi/ic_launcher.png` through `dk extract`, fitted with `dk fit --text-min 7`, checked with `dk fitness` and `dk palette` (fills) on 2026-11-25. Status roles apart under colour-vision simulation: worst CIEDE2000 10.3 light (error and success, deuteranopia), 14.3 dark; floor 10. Harmony: split-complementary, fit 0.80. Record: direction file.

**Acceptance:** A unit test reads the theme's colour tokens and finds every ratio in the table within 0.05 of the stated value against the surface of its mode.

**Not:** Today's `#03A9F4` primary with amber accent (`colors.xml:3-5`): white text on it is 2.7:1.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R06 · Status shown by more than colour

**Rule:** Every state is shown by an icon, its word and a role colour together, and text in a state's colour is used only where that colour clears 7:1. Warning and success colour icons only.

**Values:**

| State | Icon (Material Symbols, outlined) | Colour role |
|---|---|---|
| running, up to date | `check_circle` | primary |
| syncing | `sync` | primary |
| paused by your rules | `pause_circle` | muted |
| running anyway, paused by you | `back_hand` | primary |
| needs attention (an action the user can take, no fault) | `info` | warning |
| fault | `error` | error |

**Evidence:** WCAG 2.2 SC 1.4.1. Today the toolbar dot carries state by colour alone, with no content description (`widget_toolbar.xml:20-50`).

**Acceptance:** On each glance surface and Status, every state carries its icon and word, and TalkBack reads the word.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R07 · Typography

**Rule:** All text uses the system font and Material 3's type scale, in sp. No screen sets its own text size.

**Values:**

| Use | Type style | Size / line |
|---|---|---|
| screen title, large top bar | headlineMedium | 28 / 36 |
| screen title, small top bar | titleLarge | 22 / 28 |
| dialog title | headlineSmall | 24 / 32 |
| row title | bodyLarge | 16 / 24 |
| row summary, description | bodyMedium | 14 / 20 |
| section heading | labelLarge, primary colour | 14 / 20 |
| widget state word | titleSmall | 14 / 20 |
| widget reason | bodySmall | 12 / 16 |

**Evidence:** Today onboarding overrides `labelLarge` to 18 and 20 sp (`OnboardingScaffold.kt:474`, `:540`), and XML layouts use raw 11 to 18 sp.

**Acceptance:** A lint or grep in CI finds no `textSize` literal in layouts and no `fontSize =` override in Compose outside the theme.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R08 · Shape

**Rule:** Shapes follow Material 3's defaults: pill buttons, 12 dp cards, 28 dp dialogs, and the system widget radius for the widget.

**Acceptance:** No component overrides its Material 3 shape outside the theme.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R09 · Density

**Rule:** Lists and settings rows use the Standard density by default. A "Compact" option in User interface settings tightens them. Row height is the text plus the padding below, shared equally above and below.

**Values:**

| Element | Standard | Compact |
|---|---|---|
| row, 1 line of text | text + 24 dp (48 dp) | text + 12 dp (36 dp) |
| row, 2 lines | text + 26 dp (70 dp) | text + 12 dp (56 dp) |
| row, 3 lines | text + 28 dp | text + 14 dp |
| row, 6 lines or more | text + 32 dp | text + 16 dp |
| section heading, space above its text | 32 dp | 18 dp |
| section heading, space below its text | 16 dp | 8 dp |
| horizontal margin | 16 dp | 16 dp |

**Evidence:** The density rule of the design requirements (text + 24 / 28 / 32 dp, compact 12 / 14 / 16 / 18 dp). Measured on the backup screen mockups, 2026-11-25 (`build-logs/ui-critique-2026-11-25/critique/layout-v2.txt`): heading gaps pass in both densities.

**Acceptance:** A Compose test measures a one-line and a two-line settings row in each density and finds the heights in the table within 1 dp.

**Not:** Material 3's 56 dp one-line item as Standard: the owner asked that Standard not be airy.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R10 · One token set for both stacks

**Rule:** Compose and XML screens read the same colour, type and shape tokens. The XML theme extends `Theme.Material3.DayNight` and maps R05's roles, in a fork-owned file (R41); hardcoded colours are replaced by roles.

**Values:**

| Hardcoded today | Replaced by |
|---|---|
| status segmented button gradients (`fragment_status.xml:20-27`) | R23's state button |
| toolbar dots `#76ff03`, `#fff103`, `#ffae03` (`widget_toolbar.xml:29-49`) | R06's icons and roles |
| folder and device state text colours (`FoldersAdapter.java:166-232`, `DevicesAdapter.java:132-206`) | R06's roles |
| certificate check colours (`SettingsCustomCertificateScreen.kt:315-316`) | success and error roles |
| `@color/primary_dark` backgrounds on XML roots | surface |

**Evidence:** UI audit of 2026-11-25 (shared notes, `ui-audit-2026-11-25.md`).

**Acceptance:** `dk audit` over `app/src/main` reports no colour literal outside the theme files.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R11 · Icons

**Rule:** Icons are Material Symbols, outlined, as vectors. Emoji and symbol characters never carry meaning in the UI. PNG icons are replaced by vectors when their screen is reworked; the notification icon becomes a vector.

**Evidence:** 148 PNGs beside 56 vectors under `res/drawable*`; emoji in `backup_password_*` strings (`strings.xml:638-642`).

**Acceptance:** A CI check finds no emoji code point in the fork-owned string files (R41), and no screen uses an upstream string that holds one.

**Since:** 2026-11-25 · owner · agreed rewording

## 3. Navigation and structure

### D-R12 · Main navigation

**Rule:** The main screen has a Material 3 navigation bar with Status, Folders and Devices, in that order, Status first; on Android TV it is a navigation rail. The drawer is removed and its items move as the table says.

**Values:**

| Item | New place |
|---|---|
| Show device ID | QR icon in the top bar of every destination |
| Settings | gear icon in the top bar of every destination |
| Recent changes | icon in the Folders top bar |
| Web GUI, Restart, Exit | the top bar's overflow menu |
| Import and Export | Settings, "Backup and restore" (R28) |

**Evidence:** Today: drawer over tabs Folders, Devices, Status (`activity_main.xml`, `DrawerFragment.kt:163-232`).

**Acceptance:** On a phone the bar shows three items with Status selected at launch; on TV the rail does, and every item is reachable by D-pad.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R13 · Top bars

**Rule:** Each kind of screen uses the top bar the table gives; no top bar is filled with the primary colour. The back control is labelled "Navigate up".

**Why:** Today three top-bar styles coexist (audit item 3), and the XML back arrow is labelled "Main Menu" (`SyncthingActivity.java:46`).

**Values:**

| Screen | Top bar |
|---|---|
| main destinations (Status, Folders, Devices) | small, title and icons (R12) |
| Settings and its subscreens, Licenses, Recent changes | large, collapsing, title and description |
| editors and full-screen dialogs (Folder, Device, Restore a backup) | small, close icon, and a "Save" text button where something is saved |
| Web GUI host | small, back and title |

**Acceptance:** Every screen's bar matches its row; the XML `widget_toolbar` and its hidden status views are gone.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R14 · Dialogs

**Rule:** Confirmations and short inputs are Material 3 dialogs, with any warning above the action it warns about. The confirm button names the action ("Restore", "Delete folder"), with "Cancel" before it. Dialogs implemented as activities (folder type, pull order, versioning) become dialogs or full-screen dialogs when their screen is reworked.

**Evidence:** Three dialog idioms today: activities with `Theme.Syncthing.Dialog` (Manifest:227-250), AppCompat and Compose dialogs.

**Acceptance:** No new dialog activity is added; every confirm button in `strings.xml` is a verb that names its action.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R15 · Settings root order

**Rule:** The Settings root lists Run conditions, Backup and restore, User interface, Behaviour, Syncthing options, Troubleshooting, Experimental and About, in that order, each with a summary line.

**Why:** Ordered by how often each is opened. Today only Run conditions and Syncthing options have summaries (`SettingsRootScreen.kt:20-85`).

**Acceptance:** The root shows the eight entries in this order, each with a summary.

**Not:** Today's order, with Backup and restore fifth.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R16 · Per-folder and per-device run conditions

**Rule:** The per-folder and per-device run conditions screen names what it edits in its title ("Run conditions: Photos") and lists its items in the order of the app-wide Run conditions screen.

**Evidence:** Today it never shows the name it reads (`SyncConditionsActivity.java:109`, `:122`), and orders Wi-Fi, selected networks, metered, where the app-wide screen orders Wi-Fi, metered, selected networks.

**Acceptance:** Opening it from a folder named "Photos" shows "Run conditions: Photos", with items in the app-wide order.

**Since:** 2026-11-25 · builder · own words

### D-R42 · Screen-by-screen allocation

**Rule:** Before a screen's implementation plan, the screen is examined with visual-craft and design-critique, and every element on it is allocated or re-allocated against this spec. The result is written back here as rules. The allocations below are still open.

**Why:** The owner wants every screen's elements decided with the design skills, not placed one screen at a time without them.

**Values:**

| Screen or element | Open allocation |
|---|---|
| Folders and Devices destinations | where "Add folder", "Add device" and "Rescan all" go |
| Exit floating button (`activity_main.xml:32-44`) | removed or re-placed (Exit is in the overflow menu, R12) |
| Folder and Device editors, with their dialogs | layout, order of fields, destructive actions |
| onboarding pages | layout and button sizes inside R07 |
| Log, Share, QR scanner, folder picker, camera | layout and top bar inside R13 |

**Acceptance:** Each implementation plan cites the rules written for its screens, and no row above is still open when its screen is built.

**Since:** 2026-11-25 · owner · agreed rewording

## 4. Wording

### D-R41 · Fork-owned strings and themes

**Rule:** The fork's wording lives under new keys in fork-owned files (`values/strings_fork.xml`, with the theme in a fork-owned themes file); upstream's resource files stay unchanged. A screen switches to a fork key when it is built to this spec, and languages without a translation of a fork key show the English.

**Why:** Rewording upstream strings would conflict on every upstream sync, or leave the 43 translations saying the old thing.

**Acceptance:** The implementation plans' diffs leave `res/values*/strings.xml` and the upstream themes files unchanged, except for removals upstream also made.

**Not:** Editing upstream strings in place: each sync may conflict on `strings.xml`. Proposing the wording upstream first: slower, and upstream may decline.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R17 · Wording style

**Rule:** All UI text is in sentence case, British spelling, with no ALL CAPS, no emoji, no "please", and no exclamation marks. An error says what happened and offers the fix as an action; it never describes a menu path. Text never says "tap" or "click": an action is named by what it does, so it reads right on TV.

**Values:**

| Today | Becomes |
|---|---|
| "Export Configuration" | "Export configuration" |
| "FOLLOW RUN CONDITIONS" | "Follow rules" |
| "…failed. Go to Settings-Troubleshooting-Open log to see details." | "Export failed: no storage access", action "Allow storage access" |
| "Click for logs" | "Open log" |
| a warning emoji, then "Password not set." | "Not set · needed for automatic backup" |

**Evidence:** Owner rulings 7 and 8, 2026-11-25. Audit: Title Case, sentence case and ALL CAPS side by side; 6 emoji lines; menu paths at `strings.xml:689`, `:696`.

**Acceptance:** A CI check flags, in the fork-owned string files (R41), strings in ALL CAPS, strings with emoji, and strings containing "Click", "Tap" or "Go to Settings".

**Since:** 2026-11-25 · owner · agreed rewording

### D-R18 · Terms

**Rule:** The UI uses one term for each thing, as the table says.

**Values:**

| Use | Not |
|---|---|
| Wi-Fi | WiFi |
| Web GUI | web GUI, WebUI, Web UI |
| configuration (in titles), setup (in explanations) | config |
| selected Wi-Fi networks | whitelisted, specified, selected WiFi networks |
| run conditions | sync conditions |
| phone | device, when the user's own phone is meant |
| device | remote device, when another Syncthing device is meant |
| Syncthing-Fork (the app), Syncthing (the sync engine) | Syncthing for Android, Syncthing Camera, Sync. Camera |
| Internal storage/… (a path shown to the user) | a path without its root |

**Evidence:** Audit counts, 2026-11-25: "WiFi" 26 against "Wi-Fi" 14; four spellings of the web interface; "run condition" 14 against "sync condition" 3.

**Acceptance:** A CI check finds none of the Not column in the fork-owned string files (R41).

**Since:** 2026-11-25 · owner · agreed rewording

### D-R19 · Paths and long values

**Rule:** A path shown to the user names its root ("Internal storage/") and wraps after a "/" rather than mid-word. A value that cannot wrap (a device ID) is shown in full in its own screen, never cut on a summary line.

**Evidence:** Critique of the backup screen, 2026-11-25: a path broke mid-word at 320 dp and 200% text.

**Acceptance:** At 320 dp and 200% text, the backup screen's paths wrap only at "/" or spaces.

**Since:** 2026-11-25 · builder · own words

### D-R20 · Run-state words

**Rule:** The run state is named by one of the words below, the same on the notification, the tiles, the widget and Status.

**Values:**

| State | Word | Replaces |
|---|---|---|
| running, nothing to sync | "Running" | "Running: N devices online and up to date" |
| transferring | "Syncing" | "Syncing: X% complete" |
| paused by the user's run conditions | "Paused by your rules" (glance: "Paused") | "Sleeping", "Syncthing is not running." |
| forced to run | "Running anyway" | "Forced to run", "FORCE START" |
| forced to stop | "Paused by you" | "Forced to stop", "FORCE STOP" |
| starting | "Starting" | "Syncthing is starting." |
| stopped by a fault | "Stopped" | "Terminated", "Crashed" |

**Evidence:** Owner ruling 22, 2026-11-25. Today's words: `strings.xml:943-958`, `:236-240`, `:1124-1126`.

**Acceptance:** A unit test maps each run state to its word, and the notification, tile and widget code take the word from that one mapping.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R21 · Reasons

**Rule:** Each reason in the run-decision explanation has a short form for glance surfaces, at most 48 characters, and a long form for Status, in the words below. They replace every `reason_*` string in `strings.xml:893-930`, and finalise run-conditions R10's reason.

**Why:** Today's reasons repeat "Syncthing is …", use "WiFi" and "whitelisted", and one names a setting that does not exist ("Run on selected WiFi networks").

**Values:**

| Reason | Short | Long |
|---|---|---|
| forced start | "You chose Run anyway" | "Running because you chose Run anyway. Your rules apply again when you choose Follow rules." |
| forced stop | "You chose Pause" | "Paused because you chose Pause. Your rules apply again when you choose Follow rules." |
| outside the time schedule | "Waiting for the next scheduled run" | "You run Syncthing on a schedule. The last run was %s ago." |
| not charging | "Not charging" | "You run Syncthing only while the phone is charging." |
| not on battery power | "Charging" | "You run Syncthing only on battery power." |
| battery saver | "Battery saver is on" | "You pause Syncthing while battery saver is on." |
| auto-sync off | "Android auto-sync is off" | "You follow Android's auto-sync setting, and it is off." |
| on mobile data, not allowed | "On mobile data" | "Mobile data is connected, and you haven't allowed it." |
| mobile data allowed, not connected | "No mobile data" | "You allowed mobile data, but none is connected." |
| roaming, not allowed | "Roaming" | "The phone is roaming, and you allowed mobile data only when not roaming." |
| Wi-Fi not allowed | "Wi-Fi not allowed" | "You haven't allowed Wi-Fi or Ethernet." |
| no Wi-Fi | "No Wi-Fi" | "You allowed Wi-Fi, but none is connected." |
| Wi-Fi without internet (run-conditions R07, R10) | "Wi-Fi has no internet; mobile data not allowed" | "This Wi-Fi has no internet, and you haven't allowed mobile data, which Android would use instead." |
| metered Wi-Fi | "Wi-Fi is metered" | "This Wi-Fi is metered, and you allowed unmetered Wi-Fi only." |
| Wi-Fi not selected | "Not one of your Wi-Fi networks" | "This Wi-Fi isn't one of your selected Wi-Fi networks." |
| location off (needs attention, action "Turn on location") | "Can't read the Wi-Fi name" | "You run Syncthing on selected Wi-Fi networks only, and Android hides the Wi-Fi name while location is off." |
| flight mode, allowed | "Flight mode" | "Running in flight mode, which you allowed." |
| monitor not loaded (fault, action "Restart app") | "Run conditions not loaded" | "Run conditions didn't load. Restart the app; if this comes back, report it." |

**Acceptance:** A unit test finds every short form at 48 characters or fewer, and the run decision for each case returns its row's words.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R22 · Help for beginners

**Rule:** Settings › About holds two help screens: "Where to begin", four short steps to a first setup, and "Words in this app", a glossary. An info icon beside the first use of a glossary term in the Folder and Device editors opens its entry, and onboarding offers "Where to begin" once at its end.

**Why:** Many Syncthing terms mean nothing to a newcomer (purpose map, axis 11), and a first setup takes steps on two devices.

**Values:**

| Screen | Content |
|---|---|
| "Where to begin" | 1 "Share your device ID" · 2 "Add the other device" · 3 "Share a folder" · 4 "Choose when it runs"; each step opens the screen it describes |
| "Words in this app" | one entry each: device ID, introducer, folder types (send and receive, send only, receive only), file versioning, ignore patterns, run conditions |

**Acceptance:** Each step of "Where to begin" opens its screen; each info icon opens its glossary entry; both screens work by D-pad.

**Not:** An online manual: it needs a hosted page and leaves the app for a short answer.

**Since:** 2026-11-25 · owner · agreed rewording

## 5. Status and glance surfaces

### D-R23 · Status destination

**Rule:** Status shows, from the top, a run-state card, one card per open fault, and the details. The gradient segmented button and the "Reason:\n- …" list (`StatusFragment.java:158-234`) go.

**Values:**

| Part | Content |
|---|---|
| run-state card | R06 icon, R20 word, every reason in long form (R21) as plain sentences, R25's button |
| fault cards | one per open fault: a backup failure (R34), a folder error; each with its action |
| details | uptime, memory, download and upload rates, announce server, as label and value rows |

**Acceptance:** With Syncthing paused by a rule, Status shows the card with the word, the long reason and "Run anyway" above the details.

**Not:** Details first: the visit comes to learn whether it runs, and why.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R24 · Persistent notification

**Rule:** The persistent notification's title is the R20 word, with progress while syncing ("Syncing · 42%"), and its text is the short reason when paused or "Up to date · 3 devices" when running. Its actions are R25's button, then "Exit". On the lock screen it shows the word only, with no device or folder names.

**Evidence:** Today it shows a title only, with "Exit" as its one action (`NotificationHandler.java:146-219`); `:74` sets the lock-screen visibility on the wrong channel.

**Acceptance:** In each R20 state the notification shows its word, text and actions as the rule says; the locked screen shows the word only.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R25 · State button

**Rule:** Every surface that changes the run state shows one button whose action follows the state, as the table says. The Quick Settings Force tile uses the same logic: its label is the R20 word and a tap does the button's action.

**Values:**

| State | Button | Result |
|---|---|---|
| Running or Syncing | "Pause" | Paused by you |
| Paused by your rules | "Run anyway" | Running anyway |
| Running anyway or Paused by you | "Follow rules" | the run conditions decide again |
| Starting | none | — |
| Stopped | "Open" | opens Status |

**Evidence:** Owner ruling 23, 2026-11-25. Today the tile cycles Follow, Force start, Force stop (`QuickSettingsTileForce.java:54-102`).

**Acceptance:** From each state, one tap on the widget, the tile or Status reaches the result in the table, and one more tap on "Follow rules" returns control to the run conditions.

**Not:** A three-way cycle: a forced state is two taps away from the rules.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R26 · Home-screen widget

**Rule:** The widget shows the run state, one reason and the state button, at 4 × 1 resizable down to 2 × 1; selecting its text opens Status. It follows the app's colour choice (R04) and never shows a device, folder or Wi-Fi name.

**Values:**

| Size | Content |
|---|---|
| 4 × 1 | R06 icon, R20 word, short reason (or "Up to date · N devices", or "42% · N devices"), button with its label |
| 2 × 1 | icon and word on one line, short reason cut with an ellipsis, icon-only button (label read by TalkBack) |

| Measure | Value |
|---|---|
| corner radius | the system widget radius |
| button hit area | 48 dp |
| state change shown | within 2 s of the run decision |

**Evidence:** Owner rulings 22 to 24, 2026-11-25. No widget exists today (`res/xml` holds none).

**Acceptance:** On the owner's phone (`.debug` build beside the release), the widget shows each R20 state as the table says, the button does R25's action, and turning Wi-Fi off shows "Paused" with "No Wi-Fi" within the stop delay plus 2 s.

**Not:** Device or folder names on the widget: it sits on a home screen others can see.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R27 · Launcher shortcuts

**Rule:** A long press on the app icon shows the shortcuts below. The run-state shortcuts follow R25: at most two show at once, those whose action differs from the current state.

**Values:**

| Shortcut | Icon | Shown when |
|---|---|---|
| "Run anyway" | `play_arrow` | not Running anyway |
| "Pause" | `pause` | not Paused by you |
| "Follow my rules" | `rule` | a forced state is on |
| "Show device ID" | `qr_code` | always |
| "Add device" | `qr_code_scanner` | always; opens the QR scanner |

**Evidence:** Owner ruling 25, 2026-11-25.

**Acceptance:** On the owner's phone the long press shows four shortcuts, and each does its action without opening another screen first, except Show device ID and Add device.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R43 · Live state on the Run conditions screen

**Rule:** The app-wide and per-object Run conditions screens show, above the rules, a one-line card with the rules' current decision: the R06 icon, the R20 word and the R21 short reason. It updates as a rule changes.

**Why:** The user sees a rule's effect at once, without leaving the screen.

**Acceptance:** With Syncthing running on mobile data, turning "Run on mobile data" off changes the card to "Paused by your rules · On mobile data" within the stop delay (run-conditions R09) plus 2 s.

**Since:** 2026-11-25 · owner · agreed rewording

## 6. Backup and restore screen

### D-R28 · Backup screen layout

**Rule:** "Import and Export" is renamed "Backup and restore", and holds the rows below in this order. The automatic backup's status is the first thing on the screen; overwriting actions sit together under Restore.

**Values:**

| Section | Row title | Summary |
|---|---|---|
| (description) | — | "Backups hold your device ID, folders, devices and app settings, never your synced files." |
| Automatic backup | "Back up automatically" (switch) | R29 |
| Automatic backup | failure row, only while failing | R30 |
| Automatic backup | "Backup folder" | "Internal storage/backups/syncthing/auto · keeps 5" |
| Password | "Backup password" | "Set · protects the device key in every backup", or "Not set · needed for automatic backup" |
| Restore | "Restore a backup" | "5 automatic backups, newest 20 Nov 2026", or "No automatic backups yet" |
| Restore | "Restore from export file" | "Internal storage/backups/syncthing/config.zip" |
| Manual export | "Export file" | the path |
| Manual export | "Export configuration" | "Last exported 18 Nov 2026, 20:31", "Not exported yet", or R32's failure |

**Evidence:** Owner rulings 13 and 17, 2026-11-25, after a critique by three independent passes and a confirmation pass (`build-logs/ui-critique-2026-11-25/`). Measured: heading spacing passes in both densities; rows sized by their text.

**Acceptance:** The screen shows the rows in this order with these words; a TalkBack walk reads each row's title and summary.

**Not:** A separate "Automatic backup" screen beside "Import and Export": restore and password would sit in two places. Import under its own section below Export: it sat one row from Export and away from Restore. An eye icon on the password row: it shows the password on a list screen.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R29 · Automatic backup status

**Rule:** The switch's summary states the backup's state in the words below, with dates as date and time in the phone's format.

**Values:**

| State | Summary |
|---|---|
| on, newest backup holds the current setup | "Up to date · last backup 25 Nov 2026, 14:02" |
| on, a change is waiting for its backup | "Change waiting · last backup 25 Nov 2026, 14:02" |
| on, no backup yet | "No backup yet · the first follows your next change" |
| on, failing | "Last backup 20 Nov 2026, 09:14" (the failure has its own row, R30) |
| off, backups exist | "Off · last backup 20 Nov 2026, 09:14" |
| off, none | "Off" |

**Evidence:** Owner ruling 19, 2026-11-25. Critique: a date alone cannot tell "nothing changed" from "a change is waiting" (confirmed against backup R13).

**Acceptance:** A unit test of the summary builder returns each row's text for its state.

**Not:** Relative times ("2 hours ago"): backup R17 rules them out.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R30 · Backup failure row

**Rule:** While the last automatic backup failed, a row under the switch shows the error icon, the reason in the error colour, the time of the last attempt ("Last tried 21 Nov 2026, 10:02", from `auto_backup_last_failure`), and the fix as its action. Selecting the row opens the fix; the switch row keeps its own behaviour.

**Values:**

| Reason (backup R13, R19) | Title | Action |
|---|---|---|
| no storage access | "Backup failed: no storage access" | "Allow storage access", opens the permission screen |
| folder not writable | "Backup failed: can't write to the backup folder" | "Change folder", opens the folder editor |
| out of space | "Backup failed: phone storage is full" | "Open storage settings" |
| backup failed its check | "Backup failed its check" | "Open log" |
| old backups could not be removed | "Old backups couldn't be removed" | "Open log" |
| password removed | "Automatic backup is off: no password" | "Set password", opens the password dialog |

**Evidence:** Critique, 2026-11-25: in the drafted single row, the row's tap changed meaning in the failure state, and the "on" switch outweighed the fault.

**Acceptance:** With each failure recorded, the row shows its title and action, and selecting it opens the listed target; selecting the switch row still toggles the switch.

**Not:** "Tap to fix": wrong on TV, and it does not say what the fix is.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R31 · Restore list and dialogs

**Rule:** "Restore a backup" opens a list of the backups, newest first, each row giving the date and time and, on the newest, "Newest". Choosing one opens a confirmation; a password field joins the confirmation when the set password does not open that backup.

**Values:**

| Part | Words |
|---|---|
| empty list | "No backups in Internal storage/backups/syncthing/auto. Copy your backup files there, or restore from an export file." with the action "Restore from export file" |
| confirmation title | "Restore this backup?" |
| confirmation body | "Your setup is replaced with the one from 24 Nov 2026, 21:40, and this phone takes over that setup's device ID. Turn Syncthing off on the old phone first. If the restore fails, nothing changes." |
| password field, when needed | label "This backup's password"; helper "It also becomes your backup password." only when none is set |
| buttons | "Cancel", "Restore" |
| result | snackbar "Backup restored" or "Restore failed: wrong password" |

**Evidence:** Critique, 2026-11-25: the empty state was undesigned and the device-ID handover was not warned about (both confirmed).

**Acceptance:** On the device run, restoring the second-newest backup onto a cleared debug install shows the confirmation with the password field and ends with "Backup restored".

**Since:** 2026-11-25 · owner · agreed rewording

### D-R32 · Manual export and import feedback

**Rule:** After "Export configuration" or "Restore from export file" the screen stays open. The result shows as a snackbar and stays on the row: success as the row's last-export date, failure as the row's summary in the error colour with the fix of R30's table as the snackbar action. Restoring from the export file uses R31's confirmation.

**Values:**

| Result | Snackbar | Action |
|---|---|---|
| exported | "Configuration exported" | none |
| restored from export file | "Configuration restored" | none |
| failed, known reason | "Export failed: no storage access" (and the other R30 reasons) | the R30 action |
| export failed, other reason | "Export failed" | "Open log" |
| restore from export file failed, known reason | "Restore failed: no storage access" (and the other R30 reasons) | the R30 action |
| restore failed, wrong password | "Restore failed: wrong password" | none; the password field stays open |
| restore failed, other reason | "Restore failed. Nothing changed." | "Open log" |

**Evidence:** Today a success closes all of Settings and a failure opens the log after a toast with a menu path (`SettingsImportExportScreen.kt:237`, `:313`, `:239-245`; `SettingsActivity.kt:72-74`), confirmed 2026-11-25.

**Acceptance:** After an export the backup screen is still shown with the new date; after a failure the row shows the reason until the next success.

**Not:** A timed snackbar as the only trace of a failure: it is gone before a TV user can reach it.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R33 · Backup password dialog

**Rule:** The password dialog explains, then asks. The field is masked, with a show toggle inside the field.

**Values:**

| Part | Words |
|---|---|
| title | "Backup password" |
| explanation | "Encrypts the device key in every export and backup. You need it to restore. Older backups keep the password they were made with." |
| buttons | "Cancel", "Save" |
| when a password is set | a "Remove password" text button, with "Automatic backup turns off without a password." above it |
| when opened by the switch with no password set | title "Set a backup password", with "Automatic backup needs a password." before the explanation; "Save" turns the switch on |

**Evidence:** Owner approval, 2026-11-25. Today the field is plain text (`SettingsImportExportScreen.kt:167-200`).

**Acceptance:** The field masks its input until the toggle is selected; removing the password turns automatic backup off (backup R19).

**Since:** 2026-11-25 · owner · agreed rewording

### D-R34 · Backup on Status and in the notification

**Rule:** While the last automatic backup failed, Status shows a fault card with "Backup failed", the reason, the last good backup and the R30 action. The 7-day notification (backup R17) reads as the table says.

**Values:**

| Part | Words |
|---|---|
| Status card | "Backup failed" · "No storage access. Last good backup 20 Nov 2026, 09:14." · action "Allow storage access" |
| notification title | "No backup for 7 days" |
| notification text | the R30 title's reason, then the action as a notification action |

**Evidence:** Owner ruling 14, 2026-11-25.

**Acceptance:** With a failure recorded, Status shows the card; with the feature on and no good backup for 7 days with a change waiting, the notification shows these words.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R35 · Changes to other specs

**Rule:** The rulings below change rules in other specs. Each change is made in that spec, with its tests, by the implementation plan that builds it.

**Values:**

| Spec and rule | Change |
|---|---|
| auto-config-backup R13 | returning from a fix (access granted, folder changed, password set) schedules a backup at once |
| auto-config-backup R15 | the feature is turned on in "Backup and restore" |
| auto-config-backup R17 | the status shows "Up to date" or "Change waiting" (R29); "Restore from export file" restores the manual export with the same confirmation |
| run-conditions R10 | the reason's wording is R21's |

**Evidence:** Owner rulings 13, 18 and 19, 2026-11-25. Critique: after a fix, the failure stayed until an unrelated change (confirmed against backup R13).

**Acceptance:** none: each change is checked by the acceptance of the rule it changes, in its own spec.

**Since:** 2026-11-25 · owner · agreed rewording

## 7. Accessibility and TV

### D-R36 · Floors and targets

**Rule:** Every surface meets the floors below, in both densities and both colour sources.

**Values:**

| Floor | Value |
|---|---|
| text contrast | 7:1 for text below 24 sp, 4.5:1 above |
| icons and control boundaries | 3:1 |
| hit area | at least 24 dp; touch controls 48 dp (Compose's minimum interactive size), compact rows 36 dp |
| text size | content fits at 320 dp wide and 200% text |
| content descriptions | every icon-only control and every state icon |

**Evidence:** Design requirements floors; the 7:1 text floor is visual-craft's and the owner's (ruling 6).

**Acceptance:** Compose UI tests at 320 dp and font scale 2.0 find no clipped text on the backup screen and Status; `dk audit` passes on the theme.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R37 · TV parity

**Rule:** Every app-drawn screen works by D-pad, with a visible focus ring. A settings row has one focus stop; a row with two targets (a switch and an action) is split into two rows. The widget and shortcuts are phone-only.

**Evidence:** Owner ruling 3, 2026-11-25. The app is a TV launcher activity (Manifest:74-87).

**Acceptance:** On a TV build, every row of the backup screen and every navigation rail item is reached by D-pad up and down alone.

**Since:** 2026-11-25 · owner · agreed rewording

## 8. Test plan

### D-R38 · Test plan

**Rule:** Each implementation plan carries the levels below for the rules it builds. Tests and code cite this spec's rules as `SPEC:ui-design:Rnn`, or `SPEC_ui_design_Rnn` in an identifier.

**Values:**

| Level | Covers | Rules | Runs |
|---|---|---|---|
| Unit (Robolectric) | palette ratios, wallpaper scheme levels, state words, reason lengths, summaries, density heights | R04 R05 R09 R20 R21 R29 | every commit, in CI's `testDebugUnitTest` |
| Resource checks in CI | no emoji, no ALL CAPS, no banned terms, no text-size literals, no colour literals, upstream resource files unchanged | R07 R10 R11 R17 R18 R41 | every commit |
| Owner's phones (`.debug` build beside the release) | top bars, Settings order, help screens, Status, notification, widget, shortcuts, tile, live state line, backup screen, restore | R13 R15 R22 R23 R24 R25 R26 R27 R28 R30 R31 R32 R34 R43 | once per plan before merge |

**Evidence:** speccheck 1.22.0 (dev-engineering-suite, merged 2026-11-25) reads the named form and blocks a change only for the spec a citation names; the backup and run-conditions tests use it since 2026-11-25.

**Acceptance:** CI's Build / Build Debug check passes on each plan's pull request, and the phone runs are logged in the plan's progress section.

**Not:** Emulator runs: emulators do not start on the build host.

**Since:** 2026-11-25 · owner · agreed rewording

## Acceptance list

- R03 · Purpose profile and registers: On every glance surface and on Status, a pause by the user's rules shows no error or warning colour, and a fault shows the error role with an icon and an action.
- R04 · Colour source: A unit test builds the wallpaper scheme from three seeds at level 0.5 and finds onSurfaceVariant, primary and error at 7:1 or more against surface; with the switch off, every screen uses R05's values.
- R05 · Brand palette: A unit test reads the theme's colour tokens and finds every ratio in the table within 0.05 of the stated value against the surface of its mode.
- R06 · Status shown by more than colour: On each glance surface and Status, every state carries its icon and word, and TalkBack reads the word.
- R07 · Typography: A lint or grep in CI finds no `textSize` literal in layouts and no `fontSize =` override in Compose outside the theme.
- R08 · Shape: No component overrides its Material 3 shape outside the theme.
- R09 · Density: A Compose test measures a one-line and a two-line settings row in each density and finds the heights in the table within 1 dp.
- R10 · One token set for both stacks: `dk audit` over `app/src/main` reports no colour literal outside the theme files.
- R11 · Icons: A CI check finds no emoji code point in the fork-owned string files (R41), and no screen uses an upstream string that holds one.
- R12 · Main navigation: On a phone the bar shows three items with Status selected at launch; on TV the rail does, and every item is reachable by D-pad.
- R13 · Top bars: Every screen's bar matches its row; the XML `widget_toolbar` and its hidden status views are gone.
- R14 · Dialogs: No new dialog activity is added; every confirm button in `strings.xml` is a verb that names its action.
- R15 · Settings root order: The root shows the eight entries in this order, each with a summary.
- R16 · Per-folder and per-device run conditions: Opening it from a folder named "Photos" shows "Run conditions: Photos", with items in the app-wide order.
- R42 · Screen-by-screen allocation: Each implementation plan cites the rules written for its screens, and no row above is still open when its screen is built.
- R41 · Fork-owned strings and themes: The implementation plans' diffs leave `res/values*/strings.xml` and the upstream themes files unchanged, except for removals upstream also made.
- R17 · Wording style: A CI check flags, in the fork-owned string files (R41), strings in ALL CAPS, strings with emoji, and strings containing "Click", "Tap" or "Go to Settings".
- R18 · Terms: A CI check finds none of the Not column in the fork-owned string files (R41).
- R19 · Paths and long values: At 320 dp and 200% text, the backup screen's paths wrap only at "/" or spaces.
- R20 · Run-state words: A unit test maps each run state to its word, and the notification, tile and widget code take the word from that one mapping.
- R21 · Reasons: A unit test finds every short form at 48 characters or fewer, and the run decision for each case returns its row's words.
- R22 · Help for beginners: Each step of "Where to begin" opens its screen; each info icon opens its glossary entry; both screens work by D-pad.
- R23 · Status destination: With Syncthing paused by a rule, Status shows the card with the word, the long reason and "Run anyway" above the details.
- R24 · Persistent notification: In each R20 state the notification shows its word, text and actions as the rule says; the locked screen shows the word only.
- R25 · State button: From each state, one tap on the widget, the tile or Status reaches the result in the table, and one more tap on "Follow rules" returns control to the run conditions.
- R26 · Home-screen widget: On the owner's phone (`.debug` build beside the release), the widget shows each R20 state as the table says, the button does R25's action, and turning Wi-Fi off shows "Paused" with "No Wi-Fi" within the stop delay plus 2 s.
- R27 · Launcher shortcuts: On the owner's phone the long press shows four shortcuts, and each does its action without opening another screen first, except Show device ID and Add device.
- R43 · Live state on the Run conditions screen: With Syncthing running on mobile data, turning "Run on mobile data" off changes the card to "Paused by your rules · On mobile data" within the stop delay (run-conditions R09) plus 2 s.
- R28 · Backup screen layout: The screen shows the rows in this order with these words; a TalkBack walk reads each row's title and summary.
- R29 · Automatic backup status: A unit test of the summary builder returns each row's text for its state.
- R30 · Backup failure row: With each failure recorded, the row shows its title and action, and selecting it opens the listed target; selecting the switch row still toggles the switch.
- R31 · Restore list and dialogs: On the device run, restoring the second-newest backup onto a cleared debug install shows the confirmation with the password field and ends with "Backup restored".
- R32 · Manual export and import feedback: After an export the backup screen is still shown with the new date; after a failure the row shows the reason until the next success.
- R33 · Backup password dialog: The field masks its input until the toggle is selected; removing the password turns automatic backup off (backup R19).
- R34 · Backup on Status and in the notification: With a failure recorded, Status shows the card; with the feature on and no good backup for 7 days with a change waiting, the notification shows these words.
- R36 · Floors and targets: Compose UI tests at 320 dp and font scale 2.0 find no clipped text on the backup screen and Status; `dk audit` passes on the theme.
- R37 · TV parity: On a TV build, every row of the backup screen and every navigation rail item is reached by D-pad up and down alone.
- R38 · Test plan: CI's Build / Build Debug check passes on each plan's pull request, and the phone runs are logged in the plan's progress section.

## Out of scope

### D-R39 · Web GUI content

**Rule:** The design pass covers the screen that hosts the Web GUI, not the Web GUI itself.

**Why:** The Web GUI is Syncthing's own interface, served by the sync engine.

**Acceptance:** none: a scope boundary.

**Since:** 2026-11-25 · builder · own words

### D-R40 · Restore during onboarding

**Rule:** Onboarding does not offer a restore; restore stays in the backup screen.

**Why:** Restore needs Syncthing running, which onboarding reaches only at its end. Kept as a later idea.

**Acceptance:** none: a scope boundary.

**Since:** 2026-11-25 · owner · agreed rewording
