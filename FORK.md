# How this fork's branches work

Written for anyone maintaining this fork who knows everyday Git (commit, merge, rebase, push) but not this fork's setup. It explains the branch layout and gives the commands for each routine task.

## The layout

The fork uses topic branches with a throwaway integration branch. Each change lives on its own topic branch, and one branch combines them for the installed app.

```
master      = upstream/master, unchanged
fork-base   = master + 1 commit: CLAUDE.md, FORK.md, fork/
 ├─ fix/<name>
 ├─ feat/<name>
 └─ ...           one topic branch per change

fork-config = fork/branches.txt and fork/merge-conflicts.md,
              no shared history

fork-main   = fork-base + a merge of each branch
              listed on fork-config
```

- **`master`** only ever follows upstream. Nothing is committed to it.
- **`fork-base`** carries the fork's own tooling, so that every branch has `CLAUDE.md` for Claude sessions. Keep it to a single commit with the subject `chore(fork): add the fork tooling and Claude instructions`. The scripts find it by that subject.
- **Topic branches** each hold one change and start from `fork-base`. None depends on another.
- **`fork-config`** holds `fork/branches.txt`, the list of branches that go into `fork-main`, and `fork/merge-conflicts.md`, the conflicts known to come up when merging them. Everything that names particular topic branches lives there, so adding, renaming or dropping a branch never changes `fork-base`. It is an orphan branch: its first commit has no parent, so it shares no history with the other branches and never needs rebasing. Changing the list is an ordinary commit on it.
- **`fork-main`** is what gets built and installed on the phone. It is rebuilt from scratch whenever something changes, so it has no history worth keeping.

## Adding a change

```bash
git checkout -b fix/<name> fork-base
# commit the change
```

If it belongs in the app, add it to the list and rebuild `fork-main`:

```bash
git checkout fork-config
# add the branch name to the end of fork/branches.txt
git commit -am "chore(fork): merge fix/<name> into fork-main"
git checkout fork-main
fork/rebuild-fork-main.sh
git push origin fork-config
```

Switch back before running the script: `fork-config` contains only the list and `fork/merge-conflicts.md`, not the scripts. Adding branches at the end of the list keeps the earlier merges, and their recorded conflict resolutions, unchanged.

## Rebuilding fork-main

```bash
fork/rebuild-fork-main.sh
```

The script resets `fork-main` to `fork-base` and merges each branch listed in `fork/branches.txt` on `fork-config`, in order. It reads the list from your local `fork-config` branch. If the list was changed on the other computer, update your copy first with `git fetch origin fork-config:fork-config`, which refuses to overwrite local commits that aren't on GitHub. Git's `rerere` setting ("reuse recorded resolution") replays conflict resolutions it has seen before, and the script turns it on. If a merge hits a conflict, the script stops. Check `fork/merge-conflicts.md` on `fork-config` (`git show fork-config:fork/merge-conflicts.md`) for how to resolve it, resolve it, commit, and run the script again: it starts over and reuses the resolution. A conflict not described there yet gets an entry, committed on `fork-config`.

Then build and test:

```bash
./gradlew assembleDebug testDebugUnitTest
```

## Updating from upstream

```bash
git fetch upstream
git checkout master
git merge --ff-only upstream/master
git rebase master fork-base        # moves the single fork-base commit
fork/rebase-topics.sh              # moves every topic branch onto the new fork-base
fork/rebuild-fork-main.sh
./gradlew assembleDebug testDebugUnitTest
```

Then push, force-pushing the branches that moved:

```bash
git push origin master
git push --force-with-lease origin fork-base fork-main <each topic branch>
```

`--force-with-lease` refuses to overwrite a branch on GitHub that changed since your last fetch, for example from the other computer. `fork-config` doesn't move during an update, since it shares no history with the other branches, so it is never force-pushed.

## Changing the fork tooling

To edit `CLAUDE.md`, `FORK.md` or anything in `fork/`:

```bash
git checkout fork-base
# edit the files
git commit --amend --no-edit       # keep the subject line unchanged
fork/rebase-topics.sh
fork/rebuild-fork-main.sh
```

`fork/rebase-topics.sh` finds the old `fork-base` commit in each local branch by its subject and moves the branch's own commits onto the current `fork-base`.

## Dropping a branch from the app

Remove it from the list on `fork-config`, along with its entries in `fork/merge-conflicts.md`, and rebuild `fork-main`, as in "Adding a change". Nothing else depends on it, so nothing needs reverting. The branch itself can stay or be deleted.

## Submitting a change upstream

An upstream pull request must not contain the `fork-base` commit. Make a copy of the topic branch without it:

```bash
git checkout -b pr/<name> fix/<name>
git rebase --onto upstream/master fork-base pr/<name>
```

Check that the pull request's file list has no `CLAUDE.md`, `FORK.md` or `fork/`. Leave out `test(...)` commits written by Claude: LibreTube's `CONTRIBUTING.md` does not accept AI-written contributions, so everything sent upstream must be written by the fork's owner.

## Setting up another computer

```bash
git clone https://github.com/AIsaakov/LibreTube.git
cd LibreTube
git remote add upstream https://github.com/libre-tube/LibreTube.git
git fetch upstream
git config rerere.enabled true
git branch fork-config origin/fork-config
for b in $(git show fork-config:fork/branches.txt | grep -v '^#'); do git branch "$b" "origin/$b"; done
git checkout fork-main
```

The scripts work with local branches only. The `git branch` lines create local copies of `fork-config` and of every branch on its list. Create a local copy of any other topic branch you want to work on in the same way.
