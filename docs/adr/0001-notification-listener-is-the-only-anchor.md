# The notification listener is the only long-lived component

Earmark runs everything inside `EarmarkListener`, a `NotificationListenerService`. While notification access is on, the system keeps it bound, so it can register the runtime-only broadcasts (`USER_PRESENT`, `SCREEN_ON/OFF`, `NEXT_ALARM_CLOCK_CHANGED`) and own the overlay windows. We chose this over a foreground service, which needs a persistent notification that contradicts "never counts a minute", and over an AccessibilityService, which reads the screen and is a Play policy and trust risk.

**Consequences:** if the user turns off notification access, the whole app goes idle. The listener calls `requestRebind` when it is disconnected. Every other adapter is created and torn down by the listener.

**Amended by ADR 0038:** an accessibility service, off until you switch it on, lets custom triggers read an app's screen. The listener still hosts the engine; the service only hands it what it reads.
