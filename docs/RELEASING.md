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
The key lives only in GitHub Actions secrets, never in the repo:

| Secret | Content |
|---|---|
| `AZEROID_KEYSTORE_BASE64` | `base64 -w0 azeroid-release.jks` |
| `AZEROID_KEYSTORE_PASSWORD` | keystore password |
| `AZEROID_KEY_ALIAS` | `azeroid` |
| `AZEROID_KEY_PASSWORD` | key password |

Create one with:

```sh
keytool -genkeypair -v -keystore azeroid-release.jks -alias azeroid \
  -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=Azeroid"
```

**Back the keystore up somewhere private.** If it's lost, future versions can't update the installed app. You'd
have to uninstall, which deletes the downloaded game.

## Bumping the engine

Change `ENGINE_COMMIT` in `engine.properties` (and the hashes if the imagefs changed). Push to a branch, let
CI build it, and test the artifact APK on the phone before tagging.
