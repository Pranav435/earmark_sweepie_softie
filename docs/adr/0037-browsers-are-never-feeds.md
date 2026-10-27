# Browsers are never feeds

Amends the default feed apps from the v0.1 grill: the curated feeds, plus any app the Play Store files under video, social or game, minus messaging apps. Pranav tried the "Earmark this" tile in Brave on the A34, and it was greyed out. Android had Brave's category overridden to social, so Earmark treated Brave as a feed: the tile won't hold a feed (ADR 0035), and browsing in Brave counted as drift, so pills could show there. On the same phone, Tor Browser was filed as social too, and so were two chat apps missing from the messaging list, Arattai and Jio Messages. For those, opening the chat to answer one of their own messages counted as drifting into a feed.

**Browsers.** An app that opens any web link (`ACTION_VIEW` on an https URL, `BROWSABLE`, `MATCH_ALL`) is a browser, and a browser is never a feed by its category. On the A34 that's Chrome, Brave, Tor Browser and Arattai's in-app browser; no curated feed claims every link. A curated feed stays a feed whatever it handles, and you can still switch any app on in Settings › Feed apps.

**Messaging.** Arattai, Jio Messages, Google Chat, WeChat, KakaoTalk, imo, Element and Messenger Lite join `CuratedApps.messaging`.

**The tile says why.** `TileState.feed` names a feed app in front, and the tile reads "Instagram is a feed app" rather than "Open an app first" when that's why it's greyed out.

Rejected:

- **Drop categories and use only the curated list:** games and video apps would stop counting, which the grill chose on purpose.
- **List browsers by package:** the intent finds every browser, including ones no list would know.
- **Let the tile hold a feed app:** being in a feed is what the pill interrupts, so a pill would bring you back into a feed.
