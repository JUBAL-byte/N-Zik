# The signing key

`jubal-key.jks` signs every Jubal build. Its password is `jubal-2026-key`,
and both the file and the password are in this repository on purpose.

## Why it is here

Android identifies an app by who signed it. As long as the signature stays
the same, a new build installs straight over the old one and keeps its
playlists and downloads; the moment it changes, Android refuses the update
and the app has to be uninstalled first, losing everything with it.

Keeping the key here means builds simply work — nothing to configure, no
secrets to set up, nothing to remember. That is the right trade for an app
one person builds for their own phone.

## What it costs

The key is public. Anyone reading this repository can build an APK that
Android will accept as an update to this app, because it carries the same
signature. They would still have to persuade you to install it, and you
install only from this repository's own Releases page — so in practice the
risk is small. It is not zero.

## When to replace it

Before this app goes to anyone else. Sharing it, publishing it, putting it
anywhere other people install from — at that point the key has to be one
nobody else holds.

Replacing it is a clean operation:

    keytool -genkeypair -v -keystore jubal-key.jks -alias jubal \
      -keyalg RSA -keysize 2048 -validity 10000

Delete the old `jubal-key.jks`, run that, move the password out of
`.github/workflows/jubal-build.yml` into a repository secret, and read it
there instead. Note that a new key breaks the update path once: the app has
to be uninstalled and installed again that one time.
