# Keep frames coming while an overlay settles

On a Samsung Galaxy A34 (One UI 8, Android 16), no pill ever showed outside demo mode. The engine decided correctly, and the log said "pill at drift". The pill's window was attached, but it stayed 42 px tall, which is just the padding around the empty card, and it drew nothing. It drew as soon as Earmark was opened, minutes late.

System traces showed why. The window's first composition marks the card visible, and Compose asks Choreographer for a frame by posting a frame callback. The frames that followed ran the window's layout and draw but skipped the frame-callback phase completely. With another app in front, nothing else asked for a frame, so Compose's callback waited in the queue, and the recomposer waited with work pending. A frame requested afresh from a handler message did run the queued callbacks: one `Choreographer.postFrameCallback` posted 1.5 s later drew the pill within 35 ms. The exact rule in Compose 1.12 or One UI's Choreographer wasn't found. The emulator never showed it. Demo mode looked fine because its stories start from Earmark's own screen, which draws frames of its own.

v0.1.6 kept a frame callback re-posting itself for a second after each attach. That fixed the case where Earmark's own screen had never been opened in the process. It failed once the screen had been opened and left, which is the usual case: the frames skipped the callback phase, so the callback never ran to post itself again. Its first frame callback was stuck alongside Compose's.

From v0.1.7, every overlay change (a chip, pill, dot or reply strip shown, or any of them hidden) starts a handler that posts a fresh no-op frame callback every 50 ms for one second. Each post asks for a new frame if none is scheduled, and that frame runs whatever callbacks are waiting. Covering every change, not just an attach, also handles a chip turning into a pill in a window that's already showing.

On the A34 with Earmark's screen opened and left, 5 of 5 background pills in demo mode drew within about 0.6 s, as did one at 30 s outside demo mode. v0.1.6 failed every one of those.

To spot it again, look at the window in `adb shell dumpsys window windows`. A card stuck at `Requested w=1080 h=42` while the engine's log says it was shown is this bug. Test with Earmark's screen opened and then left, not only right after an install.

This was probably part of what ADR 0018 saw ("chips and pills appeared only when the tester opened Earmark"). A chip shown at unlock on the A34 also never logged "chip faded" after its 8 seconds, which fits a window that never recomposed. The foreground service stays, because One UI's freezer is real.

Rejected:

- **A dedicated recomposer for the overlays:** it would wait for frames from the same Choreographer, and it would lose the system animation-scale handling that the window recomposer provides.
- **A frame callback that re-posts itself (v0.1.6):** it gets stuck in the same skipped phase as Compose's.
