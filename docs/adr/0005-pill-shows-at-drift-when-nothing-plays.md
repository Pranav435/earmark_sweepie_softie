# With no media playing, the pill shows when drift starts

The pill normally waits for the first pause after drift starts. If no media session is playing at that moment (scrolling a text feed, for example), there is no pause to wait for, and the user might never see the pill. In that case the moment drift starts counts as the pause. Media that is playing still holds the pill back until it pauses, the track changes or the user switches apps.
