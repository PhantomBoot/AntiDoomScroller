# Signing key

`debug.keystore` is the standard Android debug key, committed on purpose.

It is **not a secret**. Every Android developer machine has an identical one, and anybody can
regenerate it:

```
keytool -genkeypair -keystore debug.keystore -storepass android -alias androiddebugkey \
  -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Android Debug,O=AntiDoomScroller,C=US"
```

It is here so that every build of this app is signed with the same key, which means a new build
installs over the old one as an update instead of making you uninstall first and lose your
settings.

Do not use it to publish to Google Play or anywhere else. A store release needs its own private
key, kept out of this repository.
