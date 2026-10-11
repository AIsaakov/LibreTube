#!/usr/bin/env bash
# Moves every local branch built on an older fork-base commit onto the current fork-base.
# braces make bash read the whole script before running it, since checking out or rebasing
# branches can replace this file while it runs
{
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

marker='chore(fork): add the fork tooling and Claude instructions'
base=$(git rev-parse fork-base)

for branch in $(git for-each-ref --format='%(refname:short)' refs/heads); do
    # fork-config holds only the fork's configuration files and shares no history with fork-base
    case "$branch" in master|fork-base|fork-main|fork-config) continue ;; esac
    # the fork-base commit this branch was built on, found by its subject
    old=$(git log -1 --format=%H --fixed-strings --grep="$marker" "$branch")
    if [ -z "$old" ]; then
        echo "$branch: not built on fork-base, skipped"
    elif [ "$old" = "$base" ]; then
        echo "$branch: already on the current fork-base"
    else
        git rebase -q --onto fork-base "$old" "$branch"
        echo "$branch: moved onto the current fork-base"
    fi
done
exit
}
