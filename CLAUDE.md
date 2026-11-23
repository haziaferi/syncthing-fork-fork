# Notes for this fork (haziaferi/syncthing-fork-fork)

This file is fork-specific. Upstream (`researchxxl/syncthing-android`) has no CLAUDE.md
today, so upstream syncs don't touch it. `CONTRIBUTING.md` belongs to upstream: leave it
unchanged, so upstream syncs stay clean.

## How changes land

- `main` is protected by the `main` ruleset:
  - every change goes through a pull request;
  - force pushes and deleting `main` are blocked;
  - a merge is blocked while CodeQL reports an error-level or high/critical-severity alert.
- The build and the unit tests are **not** required checks, so GitHub allows a merge
  while they are red or still running. Wait for every check on the PR to pass before
  merging.
- Work on a branch named `feat/…`, `fix/…`, `test/…` or `docs/…`, and open the PR against
  `haziaferi/syncthing-fork-fork`. No default repository is set for `gh` here, and it
  resolves to the `upstream` remote, so always pass `-R haziaferi/syncthing-fork-fork`.
- Commits follow `CONTRIBUTING.md`: conventional commits in lower case, an optional scope,
  and a `Signed-off-by` line (DCO).
- The owner approves each merge. Once every check is green, merge with
  `gh pr merge <n> -R haziaferi/syncthing-fork-fork --<method> --match-head-commit <sha> --delete-branch`.
  The repository does not delete merged branches by itself.
- No version tags or releases unless the owner asks.

### Which merge method

- **Squash (the default).** Use it for a feature or fix PR whose commits are work in
  progress or review fixes. Upstream squash-merges too (CONTRIBUTING.md, "Commits").
  The repository builds the squash commit's body from the PR's commit messages, which
  keeps every `Signed-off-by` line. Keep that setting: a body built from the PR
  description would drop the sign-offs unless the description carries one.
- **Rebase-merge.** Use it when every commit in the PR stands alone: it builds, it passes
  the tests, and it fixes or adds one thing. Each commit then stays revertible and
  bisectable on `main`. Say in the PR description that it should be rebase-merged.
- **Merge commit (only for upstream syncs).** To bring in `upstream/main`, merge it on a
  `sync/upstream-YYYY-MM-DD` branch, open a PR, and merge that PR with a merge commit.
  Never squash or rebase a sync: git would lose track of which upstream commits the fork
  already has, and every later sync would conflict again on the same changes.

### Automated pull requests

Upstream's Dependabot config and its scheduled bump workflows (`update-go-version`,
`update-syncthing-submodule`) have not run in this fork; GitHub keeps them off in forks
by default. Go, Syncthing and dependency bumps arrive through upstream syncs. If they are
ever turned on here, their PRs land like any other PR: squash, after the checks pass.

## Logs

Redirect command output to `build-logs/` in this checkout, never to the parent folder.
It is ignored locally through `.git/info/exclude`.
