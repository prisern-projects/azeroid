# Releasing a new version

```sh
echo 0.2.0 > VERSION
$EDITOR CHANGELOG.md        # add a "## [0.2.0] - YYYY-MM-DD" section at the top
git commit -am "Release 0.2.0"
git push origin main
```

CI sees a `VERSION` with no release yet, builds and signs the APK, then creates the tag `v0.2.0` and the
GitHub Release with that changelog section as notes. In about 10 minutes the phone shows *Update available* next time
Azeroid is opened (or right away via *Check for updates*).

- `VERSION` must be `MAJOR.MINOR.PATCH`. `versionCode` is derived from it, so always go **up**.
- Pushes that don't change `VERSION`, and all PR builds, produce test APKs (version `0.0.0`) in the Actions run.
  The in-app updater never offers them.

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
