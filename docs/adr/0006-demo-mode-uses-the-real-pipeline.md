# Demo mode posts real notifications into the real pipeline

Demo mode does not inject fake events into the engine. Earmark posts real `MessagingStyle` notifications (Rahul: "free at 5?"; Aisha requested $12.00) with a real reply action, and the listener reads them like any other app's notifications. The Classifier normally skips Earmark's own notifications and accepts them only while demo mode is on. The way back opens a small built-in demo chat. The drift timer drops from 90 s to 10 s, and the alarm history for the Bedtime story is seeded.

**Why:** the demo exercises the listener, the classifier, `RemoteInput` and loop closing exactly as real use does, so a working demo is evidence that the product works.

**Addendum (Phase 2):** demo mode also drops the 22:30 bedtime window for the alarm routine, so the Bedtime story can run at any hour. The weeknight check and the "no alarm set for tomorrow" check still apply.

**Addendum (Phase 3):** demo mode also drops the 10-minute cooldown between pills, so a tester can run the stories one after another. The one-pill-per-loop limit still applies.
