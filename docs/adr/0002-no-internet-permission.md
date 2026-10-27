# No INTERNET permission, so payments are parsed with a regex

The APK declares no `INTERNET` permission. The manifest strips the one that libraries merge in with `tools:node="remove"`, and `aapt2 dump permissions` checks this on every release. Smart Reply uses the bundled ML Kit model, so it needs no download. ML Kit Entity Extraction, which the one-pager names for payment requests, downloads its model at runtime, so v0.1 finds amounts with a regex behind a `MoneyParser` seam instead.

**Considered options:** bundled Entity Extraction (no bundled variant exists); allowing network for the model download only (this breaks the "nothing leaves your phone" promise and can't be enforced per call).
