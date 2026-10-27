# A foreground service keeps the phone from freezing Earmark

Supersedes the "no foreground service" line in architecture.md. On a Samsung Galaxy A34, chips and pills appeared only when the tester opened Earmark, never while Instagram or another app was in front. On stock Android, the process that hosts a bound notification listener sits at a perceptible priority (adj 250), below the cached-app freezer's cutoff, which is why the emulator never showed the problem. One UI runs its own freezer, which also freezes background apps that have no foreground service. While Earmark is frozen, notifications, `SCREEN_ON`, `USER_PRESENT` and the foreground poll all queue up. When the app is opened they arrive at once, and in the wrong order. `adb shell am freeze --sticky com.sweepiesoftie.earmark` reproduces this on the emulator.

`KeepAliveService` is a foreground service of type `specialUse` with a silent, minimum-importance "Earmark is on" notification. It does no work. It only holds the shared process at foreground-service priority (adj 200), which both freezers leave alone. It is started:

- when the listener connects;
- whenever Earmark is opened;
- after a reboot (`BOOT_COMPLETED`);
- after an update (`MY_PACKAGE_REPLACED`).

Android only lets an app start a foreground service from the background in a few cases: after a boot or an update, while one of its overlays is showing, or when battery optimisation is off. A start it refuses is logged, and the next time Earmark is opened starts it.

Onboarding also asks for battery optimisation to be turned off ("Unrestricted" on Samsung) with Android's own prompt. While it is on, the home screen shows a banner. Earmark is sideloaded, so Play's policy on that permission doesn't apply.

**Consequences:** Earmark now posts a notification and shows a small icon in the status bar, which ADR 0006 had limited to demo mode. Battery cost should be small because the service does nothing. Unlock polling and the foreground poll still run only while the screen is on. This could only be tested on the emulator; One UI's freezer policy isn't public.
