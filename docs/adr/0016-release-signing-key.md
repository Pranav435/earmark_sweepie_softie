# Release builds are signed with the Sweepie Softie release key

Supersedes ADR 0010. Phones block or warn on APKs signed with the shared Android debug key, and every developer's debug key is different. So a debug-signed APK also can't update an Earmark installed from another machine. Release builds are now signed with a dedicated RSA 4096 key (`CN=Sweepie Softie, OU=Earmark, O=Sweepie Softie`, alias `earmark`, valid 30 years), using signature schemes v1, v2 and v3.

The keystore and its passwords live outside git. `keystore.properties` at the repo root names the keystore file and holds the passwords, and `.gitignore` excludes it along with `*.jks` and `*.keystore`. Without that file the release build still works, but it is signed with the debug key and Gradle warns.

**Consequences:** the key can't change once testers install a build signed with it, because Android refuses an update signed with a different key. Lose the key and every tester must uninstall and reinstall. Back it up, and share it with the other maintainers only through a password manager. Anyone who installed an earlier debug-signed v0.1 has to uninstall it once. A Play Store release would use Play App Signing with this key as the upload key.
