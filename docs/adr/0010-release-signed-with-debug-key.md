# The v0.1 release APK is signed with the debug key

v0.1 is sideloaded onto test phones only, so the R8-minified release build is signed with `~/.android/debug.keystore`. This gives testers a release-speed APK without creating and guarding an upload key yet. Before any store release, the app needs a real signing key. Because Android refuses an update signed with a different key, testers will have to uninstall v0.1 at that point.
