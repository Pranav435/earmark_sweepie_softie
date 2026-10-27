# The demo reply action's PendingIntent is mutable

Every PendingIntent Earmark creates is `FLAG_IMMUTABLE`, with one exception: the reply action on demo mode's Rahul notification. Android fills the typed reply into a `RemoteInput` action's intent, and that only works on a mutable PendingIntent. The intent is explicit (it targets `DemoReplyReceiver`, which isn't exported), so no other app can redirect it.
