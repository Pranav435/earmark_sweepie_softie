# Chip and pill copy lives in the core, not in strings.xml

The words on a chip or pill ("Here to reply to Rahul?", "Pay now") are built by `Copy` in `:core`, so the scenario tests can hold the engine to the exact demo copy. Screen chrome (onboarding, settings, recap, TalkBack labels) stays in `strings.xml`. The cost is that loop copy can't be translated through Android resources. v0.1 is English-only anyway, because ML Kit Smart Reply only supports English. Localising later means giving `Copy` a locale-keyed table.
