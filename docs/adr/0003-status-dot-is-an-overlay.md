# The held-loop marker is an overlay dot, not a notification

When a chip is held, Earmark draws a small non-touchable marigold dot at alpha 0.8 just below the status bar, reusing the overlay window. A silent notification would put Earmark in the shade and among other apps' notifications, which goes against the product's quiet tone. Alpha 0.8 is the highest value at which Android 12+ still lets touches pass through another app's overlay.
