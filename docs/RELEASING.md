# Releasing a new version

```sh
# 1. describe the change
$EDITOR CHANGELOG.md        # add a "## [0.2.0] - YYYY-MM-DD" section at the top
git commit -am "Release 0.2.0"
# 2. tag and push. CI builds, signs and publishes the release
git tag v0.2.0
git push origin main v0.2.0
```

Within about 20 minutes the release appears under *Releases*. The phone's app shows *Update available* next time it's
opened (or right away via *Check for updates*).

- Tags must be `vMAJOR.MINOR.PATCH`. `versionCode` is derived from it, so always go **up**.
- Builds from branches and PRs are version `0.0.0`. They're downloadable from the Actions run for testing,
  but the in-app updater never offers them.

## Signing key (one-time setup)

Updates only install over the existing app when every release is signed with the **same key**.
That key is committed **encrypted** in [`signing/`](../signing). The only secret is its password:

*Repo → Settings → Secrets and variables → Actions → New repository secret*
- Name: `AZEROID_SIGNING_PASSWORD`
- Value: the password (keep a private copy, e.g. in your password manager)

If the password is lost, future versions can't update the installed app. You'd have to uninstall,
which deletes the downloaded game.

## Bumping the engine

Change `ENGINE_COMMIT` in `engine.properties` (and the hashes if the imagefs changed). Push to a branch, let
CI build it, and test the artifact APK on the phone before tagging.
