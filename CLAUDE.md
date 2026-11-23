# Notes for this fork (haziaferi/syncthing-fork-fork)

This file is fork-specific. Upstream (`researchxxl/syncthing-android`) has no CLAUDE.md,
so this file never conflicts when upstream is merged in. `CONTRIBUTING.md` belongs to
upstream: leave it unchanged, so upstream syncs stay clean.

## How changes land

- `main` is protected by a ruleset: every change goes through a pull request, force
  pushes are blocked, and CodeQL must pass before a merge. Never push to `main` directly.
- Work on a branch named `feat/…`, `fix/…`, `test/…` or `docs/…`, and open the PR against
  `haziaferi/syncthing-fork-fork`. `gh` defaults to the upstream repository here, so
  always pass `-R haziaferi/syncthing-fork-fork`.
- Commits follow `CONTRIBUTING.md`: conventional commits in lower case, an optional scope,
  and a `Signed-off-by` line (DCO).
- The owner approves each merge. Wait until CI is green, then merge with
  `gh pr merge <n> -R haziaferi/syncthing-fork-fork --<method> --match-head-commit <sha>`,
  and delete the branch afterwards.
- No version tags or releases unless the owner asks.

### Which merge method

- **Squash (the default).** Use it for a feature or fix PR whose commits are work in
  progress or review fixes. This matches upstream's own policy (CONTRIBUTING.md,
  "Commits"), so a squashed change can later be offered upstream as one commit.
- **Rebase-merge.** Use it when every commit in the PR stands alone: it builds, it passes
  the tests, and it fixes or adds one thing. Each commit then stays revertible and
  bisectable on `main`. Say in the PR description that it should be rebase-merged.
- **Merge commit (only for upstream syncs).** To bring in `upstream/main`, merge it on a
  `sync/upstream-YYYY-MM-DD` branch, open a PR, and merge that PR with a merge commit.
  Never squash or rebase a sync: git would lose track of which upstream commits the fork
  already has, and every later sync would conflict again on the same changes.

## Logs

Redirect command output to `build-logs/` in this checkout, never to the parent folder.
It is ignored locally through `.git/info/exclude`.
