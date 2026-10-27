# Keep frames coming while a new overlay settles

On a Samsung Galaxy A34 (One UI 8, Android 16), no pill ever showed outside demo mode. The engine decided correctly, and the log said "pill at drift". The pill's window was attached, but it stayed 42 px tall, which is just the padding around the empty card, and it drew nothing. It drew as soon as Earmark was opened, minutes late.

A system trace showed why. The window's first composition marks the card visible, and Compose asks Choreographer for a frame. The next vsync ran only the window's layout and draw. Compose's frame callback did not run, and with Instagram in front nothing else in Earmark's process asked for another frame. The recomposer waited with work pending until something drew. Any frame unstuck it: one `Choreographer.postFrameCallback` 1.5 s later drew the pill within 35 ms. Later requests, such as hiding the pill, arrived normally. It happened in 5 of 5 background trials on the A34 and never on the emulator. Demo mode looked fine because its stories start from Earmark's own screen, which draws frames of its own. The exact cause, in Compose 1.12 or in One UI's Choreographer, wasn't found.

Each overlay window now asks Choreographer for every frame during the first second after it attaches. The callback does nothing except keep vsync coming, so the frame Compose asked for is delivered. It stops early if the window detaches. With this change, 4 of 4 background pills in demo mode, and one at 90 s outside demo mode, drew on time over Instagram.

To spot it again, look at the window in `adb shell dumpsys window windows`. A card stuck at `Requested w=1080 h=42` with the engine's log saying it was shown is this bug.

This was probably part of what ADR 0018 saw ("chips and pills appeared only when the tester opened Earmark"). A chip shown at unlock on the A34 also never logged "chip faded" after its 8 seconds, which fits a window that never recomposed. The foreground service stays, because One UI's freezer is real.

Rejected:

- **A dedicated recomposer for the overlays:** it would wait for frames from the same Choreographer, and it would lose the system animation-scale handling that the window recomposer provides.
- **Asking for frames whenever the recomposer has pending work:** this needs a recomposer that only exists once the window is attached, for a stall that was only seen on the first frame.
