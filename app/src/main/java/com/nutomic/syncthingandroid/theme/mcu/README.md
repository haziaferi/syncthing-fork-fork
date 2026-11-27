# Vendored: Material Color Utilities

Java sources of [material-color-utilities](https://github.com/material-foundation/material-color-utilities),
copied from the `java/` folder at commit `5519f65a77a56bc24de922504efa8e3dfa59d992` (committed 2026-11-19 PST).
Licence: Apache-2.0, see `LICENSE` beside this file (copied unchanged).

Why vendored: the copy inside Material Components is `@RestrictTo`, which fails the lint run in CI.
This fork builds a Material You colour scheme at a chosen contrast level from these classes.

## Folders

Taken: `blend`, `contrast`, `dislike`, `dynamiccolor`, `hct`, `palettes`, `scheme`, `temperature`, `utils`
(37 `.java` files). Left out: `quantize` and `score`, which build seed colours from images.

## Changes to the upstream sources

Exactly two, applied by script to every file:

1. `package <folder>;` became `package com.nutomic.syncthingandroid.theme.mcu.<folder>;`, and every
   `import` / `import static` of one of those folders was changed to match.
2. Each of the 37 top-level classes, interfaces and enums gained `@androidx.annotation.RequiresApi(31)`
   on the line above its declaration, because the sources use `java.util.Optional`, `java.util.function`
   and streams (API 24) and are meant to run on Android 12+ only (the app's minSdk is 23).

The errorprone annotations (`CanIgnoreReturnValue`, `Var`) stay; `error_prone_annotations` is already
on the compile classpath.

## Updating

Re-copy from a newer upstream commit, re-apply the two changes above, and change the commit here.
Do not edit these files by hand.
