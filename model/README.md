# Message model

The small model that reads each chat message beside `Urgency`'s word lists and decides three things: is it urgent, does it ask something, and is it only an acknowledgement (ADR 0031). It lives in `core/src/main/kotlin/.../MessageModel.kt`; its weights are `core/src/main/resources/com/sweepiesoftie/earmark/core/message-model.bin`.

## Data

`data/train-*.json` holds 2,644 labelled messages, one `{text, language, urgent, asks, ack}` each. Three AI agents (Claude) wrote them from the definitions in `benchmark/LABELS.md`, without seeing the benchmark's cases, Earmark's code or the benchmark's results:

- `train-a.json`: English, and Spanish, French, German, Portuguese, Arabic, Italian, Indonesian, Turkish and Russian.
- `train-b.json`: Hinglish, Hindi in Devanagari, and Marathi, Gujarati, Tamil, Bengali, Telugu, Kannada, Punjabi and Urdu.
- `train-c.json`: group messages, older relatives, work and college, emotional or high-stakes messages, and messages of one to four words.

Each was asked for the cases word lists get wrong: questions without a question mark, requests said as instructions, "not urgent", urgency in the past or as a joke, quoted questions, reactions. Two kinds of label were changed by hand after writing, to match the labelling guide and ADR 0017: a bare greeting ("hey", "yo", "hello") doesn't ask, and an offer ("lmk if you need anything from home") doesn't ask. Six messages changed.

Never train on `benchmark/data` or `benchmark/holdout/data`. They are how the model is checked.

## Train

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
./gradlew :core:trainModel
```

It reads `data/`, scores the word lists and the model by five-fold cross-validation, picks each head's two thresholds from predictions on messages the model didn't train on, then trains on everything and writes the weights. It prints how the lists and the lists with the model do on the cross-validation and on `benchmark/data/messages.json`, and writes those numbers to `results.json`. Training is deterministic: the same data gives the same file.

Retrain after changing the data, or after changing which clues `Urgency.read` names, since the clue names are features. Then run `./gradlew :core:test :core:benchmark`.

## How it works

Three logistic regressions over hashed features: each word, each pair of words, runs of two to four letters inside each word, the first and last word, emoji and "?" or "!", the message's length, and the names of the word-list clues that fired. They're hashed into 32,768 slots, with one byte per weight per head: 96 KB. Each head has two thresholds: how sure the model must be to say yes when the lists say no, and to keep yes when they say yes. The lists that say what a message *isn't* have the last word: "not urgent", "it's over", a reaction, an offer, and for acknowledgements any question, request or greeting.

| Head | Lists alone | Lists and model |
|---|---:|---:|
| Urgent | 90.7% right, 59% caught | 93.2% right, 75% caught |
| Asks | 87.8% right, 82% caught | 91.0% right, 89% caught |
| Acknowledges | 92.9% right, 65% caught | 95.6% right, 80% caught, 98% right when it says yes |

That's five-fold cross-validation on `data/`, at the time of 0.1.15. `results.json` has the current numbers.
