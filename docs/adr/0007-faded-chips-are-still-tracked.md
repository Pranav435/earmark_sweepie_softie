# A faded chip is still tracked; only a swipe stops tracking

If a chip fades without a tap, its loop stays the guess, and drift is still watched for it. A swipe means "not this": it stops tracking that loop until the next unlock and counts as an ignore toward muting the kind. A fade does not count as an ignore. We chose this because a TalkBack user, or anyone who glances away, often misses a timed chip, and losing the hand-back for that reason would punish inattention, which the product exists to help with.
