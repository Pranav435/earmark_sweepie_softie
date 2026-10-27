# Accuracy benchmark

Measures how often Earmark's rules make the right call on labelled cases. The report is [`docs/benchmark.md`](../docs/benchmark.md), with a PDF beside it that `make-pdf.py` builds.

- `LABELS.md` says how the cases are written and what the right answers are.
- `data/` holds the cases: `notifications.json` (the classifier), `messages.json` (urgent, asks, acknowledgement, and the reply strip), `money.json` (amounts), `guesses.json` (the chip at unlock), and `reply-judgments.json` (whether each suggested reply makes sense).
- `results/` is written by the run: `results.json` holds every number, `misses.md` lists every case the rules got wrong, and `smart-reply.json` holds Smart Reply's answers from a phone.

The cases were written and labelled from `LABELS.md` alone, without reading Earmark's code, so that they test the rules rather than echo them.

## Run it

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
./gradlew :core:benchmark
```

That covers everything that runs on a laptop. Smart Reply runs only on Android, so its answers come from the `:bench` app, a debug-only app that installs beside Earmark without touching it. With the emulator running:

```bash
./gradlew :bench:assembleDebug
adb -s emulator-5554 install -r bench/build/outputs/apk/debug/bench-debug.apk
adb -s emulator-5554 shell am instrument -w com.sweepiesoftie.earmark.bench/.SmartReplyBench
adb -s emulator-5554 shell run-as com.sweepiesoftie.earmark.bench cat files/smart-reply.json > benchmark/results/smart-reply.json
./gradlew :core:benchmark
```

After a change to the messages or to the reply rules, the run writes `results/replies-to-judge.json`: every reply it would offer. Each new reply needs a judgement in `data/reply-judgments.json` before the reply numbers come back.

The benchmark isn't part of the ship gate, because some misses are expected. Run it after changing a rule, and compare `results.json` with the committed one.
