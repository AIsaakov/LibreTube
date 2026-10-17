# Known merge conflicts in fork-main

Written for whoever runs `fork/rebuild-fork-main.sh` and has it stop on a conflict. It lists the conflicts known to come up when merging the branches in `fork/branches.txt`, in that order, and how to resolve each. `FORK.md` on `fork-base` explains the rebuild itself.

`rerere` stores resolutions per computer, so a new clone meets each of these once. Keep the entries in step with `fork/branches.txt`: when a branch is renamed or dropped from the list, update or remove its entries in the same commit.

- **`SabrClient.kt`**, merging `fix/sabr-url-refresh` after `fix/sabr-video-download`. Both add a top-level function before `InitializedFormat` and a constant at the start of the `companion object`. Keep both functions and both constants.
- **`SabrDataSource.kt`**, merging `debug/diagnostics` after `fix/sabr-video-download`. Keep the `DebugDiagnostics.sabr(...)` call from `debug/diagnostics`, followed by the comment and `throw IOException(e)` from `fix/sabr-video-download`.
- **`app/build.gradle.kts`**, merging `chore/fork-app-name` after `debug/diagnostics`. In the `debug` build type, `debug/diagnostics` adds the `versionNameSuffix` lines that put the commit hash in the version name, just before `resValue("string", "app_name", ...)`, and `chore/fork-app-name` changes that line's name to "LibreTube+". Keep the `versionNameSuffix` lines from `debug/diagnostics`, followed by `resValue("string", "app_name", "LibreTube+")`.
