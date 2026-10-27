# Accuracy benchmark

Measures how often Earmark's rules make the right call on labelled cases. The reports are [`docs/benchmark.md`](../docs/benchmark.md) for 0.1.14 and [`docs/benchmark-v2.md`](../docs/benchmark-v2.md) for 0.1.15, each with a PDF beside it that `make-pdf.py` builds.

- `LABELS.md` says how the cases are written and what the right answers are.
- `data/` holds the cases: `notifications.json` (the classifier), `messages.json` (urgent, asks, acknowledgement, and the reply strip), `money.json` (amounts), `guesses.json` (the chip at unlock), and `reply-judgments.json` (whether each suggested reply makes sense).
- `results/` is written by the run: `results.json` holds every number, `misses.md` lists every case the rules got wrong, and `smart-reply.json` holds Smart Reply's answers from a phone. `results/rules-only/` is the same run without the message model.
- `holdout/` is a second set of cases of the same kinds, written for 0.1.15 by agents who saw only `LABELS.md`, and run once, after the rules and the model were frozen, to check that the changes made for `data/` hold up on cases nobody tuned against. It has been run now, so it no longer tests that: the next benchmark needs a new one.

The cases were written and labelled from `LABELS.md` alone, without reading Earmark's code, so that they test the rules rather than echo them.

## Run it

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
./gradlew :core:benchmark
```

That covers everything that runs on a laptop. `-PrulesOnly` leaves the message model out (ADR 0031), and `-PbenchmarkRoot=benchmark/holdout` runs the held-out cases instead, writing to `holdout/results/`. Smart Reply runs only on Android, so its answers come from the `:bench` app, a debug-only app that installs beside Earmark without touching it. With the emulator running:

```bash
./gradlew :bench:assembleDebug
adb -s emulator-5554 install -r bench/build/outputs/apk/debug/bench-debug.apk
adb -s emulator-5554 shell am instrument -w com.sweepiesoftie.earmark.bench/.SmartReplyBench
adb -s emulator-5554 shell run-as com.sweepiesoftie.earmark.bench cat files/smart-reply.json > benchmark/results/smart-reply.json
./gradlew :core:benchmark
```

Add `-PbenchmarkRoot=benchmark/holdout` to the first and last commands, and write to `benchmark/holdout/results/`, for the held-out messages.

The same app times the engine on a phone: every notification, and every message as a WhatsApp chat, through `Engine.handle`.

```bash
adb -s <serial> shell am instrument -w com.sweepiesoftie.earmark.bench/.EngineTiming
adb -s <serial> shell run-as com.sweepiesoftie.earmark.bench cat files/engine-timing.json
```

After a change to the messages or to the reply rules, the run writes `results/replies-to-judge.json`: every reply it would offer. Each new reply needs a judgement in `data/reply-judgments.json` before the reply numbers come back.

The benchmark isn't part of the ship gate, because some misses are expected. Run it after changing a rule, and compare `results.json` with the committed one.
