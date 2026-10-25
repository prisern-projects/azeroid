# Release signing key

`azeroid-release.jks.enc` is the release keystore, encrypted with AES-256 (PBKDF2, 600k iterations) using a
random 192-bit password. Without the password the file is useless, so it's safe in a public repo.

The password is the only secret: `AZEROID_SIGNING_PASSWORD` in *Settings → Secrets and variables → Actions*.
It also serves as the keystore and key password (alias `azeroid`). Keep a private copy of it.
