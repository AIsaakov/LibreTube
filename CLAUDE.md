# Claude instructions for this fork

This checkout is the user's fork, [AIsaakov/LibreTube](https://github.com/AIsaakov/LibreTube), of [libre-tube/LibreTube](https://github.com/libre-tube/LibreTube). `FORK.md` explains the branch workflow and its commands in full.

## Branches

- `master` mirrors `upstream/master`. Never commit to it.
- `fork-base` is `master` plus one commit holding this file, `FORK.md` and `fork/`. Every other branch starts from `fork-base`. To change that commit, amend it without changing its subject line, then run `fork/rebase-topics.sh`.
- Each change gets its own topic branch: `fix/...`, `feat/...`, `chore/...` or `debug/...`. The issue's `dest:` label records whether the change goes upstream, not the branch name. Never start a branch name with `upstream/`, which clashes with the remote.
- `fork-main` is `fork-base` plus a merge of each branch listed in `fork/branches.txt` on the `fork-config` branch. It is the build installed on the user's phone. Never commit to it directly: rebuild it with `fork/rebuild-fork-main.sh`.
- `fork-config` is an orphan branch that holds `fork/branches.txt` and `fork/merge-conflicts.md`, the conflicts known to come up when rebuilding `fork-main` and how to resolve them. Adding a branch to the app, renaming one or removing one is an ordinary commit on `fork-config`, followed by a rebuild of `fork-main`. It doesn't touch `fork-base` or the topic branches. Keep particular topic branch names out of `fork-base`.
- Before an upstream pull request, remove the `fork-base` commit: `git rebase --onto upstream/master fork-base <branch>`, on a copy of the branch.
- In issues, comments and notes, refer to a change by its branch name, plus the commit subject if needed. Never use commit hashes there: amending or updating `fork-base` gives every commit on the other branches a new hash.
- Put tests in separate `test(...)` commits, so they can be left out of an upstream pull request.
- Commit, push, force-push or delete branches only when the user approves. Force-push with `--force-with-lease`. Unattended agent work on an issue has two exceptions: being asked to do the work approves creating the issue's branch from the issue, and the branch's commits are made locally without asking, then approved together as a series before anything is pushed.
- Don't add entries to `.git/info/exclude` without asking.

## Upstream

- LibreTube's `CONTRIBUTING.md` does not accept AI-written contributions. Anything sent upstream (issues, comments, pull requests) is written by the user. Never comment on upstream issues.
- Keep upstream issue numbers and @mentions out of commit messages pushed to the fork.

## Issues in the fork

- Each issue gets one destination label (`dest: upstream`, `dest: fork-general`, `dest: fork-personal`), one type label (`type: bug`, `type: feature`, `type: chore`), optionally `reported-upstream`, and a priority (`priority: high`, `medium`, `low`) that the user chooses.
- `dest: fork-general` means kept in the fork but useful to other users. `dest: fork-personal` means specific to the user.
- Keep descriptions high level, a few lines. Propose the text before creating or editing an issue. Use this layout:

  ```
  **Description:**
  <what's wrong or what should change, and why>

  **Intended Destination:**
  <where the change goes and why>

  **References:**
  - Upstream issue: <full URL>
  ```

  Include References only when there is something to link. Links to upstream issues are fine.
- While a fix's commits may still move between branches, don't name commits or the fix in its issue.
- Branches are linked to issues in the issue's Development sidebar. Create a new issue's branch from the issue, with `gh issue develop <number> --name <branch> --base fork-base`, so it's linked from the start. GitHub's API can't link an existing branch, so the user links those on the website.

## Testing

Every change is tested at these levels before it's done:

1. Its branch builds and passes `gradlew testDebugUnitTest` on its own, without the other branches.
2. If it changes app behaviour, reproduce the problem on an emulator with an unmodified build, then show it's gone with the fixed build, in the same steps. Changes to tooling, docs or tests only skip this.
3. Before `fork-main` is installed on the user's phone, smoke-test it on an emulator: playback, background playback with the screen off, and the forced theme.

On emulators and devices:

- On emulators, play neutral public videos, such as Blender's Sintel (`eRsGyueVLvQ`). Never use video IDs from the user's watch history, phone logs or notes. Before capture runs, clear LibreTube's database (`run-as com.github.libretube.debug rm databases/LibreTubeDatabase*`, with the app stopped). Look at every screenshot before it goes into anything shared.
- Change Android settings only on emulators, never on the user's phone. The user types adb pairing codes.
- For builds to compare against, use throwaway worktrees in sibling folders (`git worktree add ../LibreTube-<name> <ref>`). Copy `local.properties` into each, and remove them when done.
