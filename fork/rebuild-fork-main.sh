#!/usr/bin/env bash
# Rebuilds fork-main as fork-base plus a merge of each branch listed in fork/branches.txt on fork-config.
# braces make bash read the whole script before running it, since checking out or rebasing
# branches can replace this file while it runs
{
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
    echo "Commit or stash your changes first." >&2
    exit 1
fi

# replay conflict resolutions recorded in earlier rebuilds
git config rerere.enabled true
git config rerere.autoupdate true

if ! git rev-parse -q --verify refs/heads/fork-config >/dev/null; then
    echo "There is no local fork-config branch. Create it with: git branch fork-config origin/fork-config" >&2
    exit 1
fi

# read the list from fork-config, so the result doesn't depend on the checked-out branch
mapfile -t branches < <(git show fork-config:fork/branches.txt | sed 's/#.*//; s/[[:space:]]*$//' | grep -v '^$')

git checkout -q -B fork-main fork-base
for branch in "${branches[@]}"; do
    if git merge --no-ff --no-edit -q "$branch" >/dev/null; then
        echo "merged $branch"
    elif ! git rev-parse -q --verify MERGE_HEAD >/dev/null; then
        # the merge failed without starting, e.g. because a file was locked or would be overwritten
        echo "Merging $branch failed without a conflict (see the error above)." >&2
        echo "Fix the cause, check git status for files the failed merge left behind, then run this script again." >&2
        exit 1
    elif [ -z "$(git diff --name-only --diff-filter=U)" ]; then
        git commit -q --no-edit
        echo "merged $branch (conflicts resolved by rerere)"
    else
        echo "New conflict while merging $branch. Resolve it, commit, then run this script again." >&2
        exit 1
    fi
done
exit
}
