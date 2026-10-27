# Message text and intents live in memory only

Message text, `PendingIntent`s and Smart Reply input stay in the adapters' memory and are dropped when a loop closes. Room stores only kinds, times, outcomes, a SHA-256 hash of the conversation key with a per-install salt, and a short recap label that is reduced to kind and time after 24 h. `DebugLog` lines carry scores and reasons, never text.

**Consequences:** after a process restart or reboot, way backs are rebuilt from `getActiveNotifications()`. A loop whose notification is gone falls back to the app's launch intent.
