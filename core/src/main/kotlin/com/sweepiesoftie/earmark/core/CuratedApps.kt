package com.sweepiesoftie.earmark.core

/** Hand-picked package lists. Category matching for feeds happens in the adapter, which can see app categories. */
public object CuratedApps {
    public val feeds: Set<String> = setOf(
        "com.instagram.android",
        "com.google.android.youtube",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.twitter.android",
        "com.reddit.frontpage",
        "com.facebook.katana",
        "com.snapchat.android",
    )

    public val rides: Set<String> = setOf(
        "com.ubercab", "com.olacabs.customer", "me.lyft.android", "ee.mtakso.client", "com.rapido.passenger",
        "com.grabtaxi.passenger", "com.bolt.client",
    )

    public val food: Set<String> = setOf(
        "in.swiggy.android", "com.application.zomato", "com.dd.doordash", "com.ubercab.eats", "com.grubhub.android",
        "com.deliveroo.orderapp",
    )

    public val shopping: Set<String> = setOf(
        "com.amazon.mShop.android.shopping", "in.amazon.mShop.android.shopping", "com.flipkart.android",
        "com.myntra.android", "com.ebay.mobile", "com.etsy.android", "com.walmart.android", "com.zzkko",
    )

    /** Apps where leaving early can leave a ride, order or checkout unfinished. */
    public val flows: Set<String> = rides + food + shopping

    /** Reminder and to-do apps: what they notify about is something you told yourself to do, even without the reminder category. */
    public val reminders: Set<String> = setOf(
        "com.google.android.keep", "com.google.android.apps.tasks", "com.samsung.android.app.reminder", "com.microsoft.todos",
        "com.ticktick.task", "com.todoist", "com.anydo",
    )

    /** Messaging apps are never feeds, even when their Play category says social. */
    public val messaging: Set<String> = setOf(
        "com.whatsapp", "com.whatsapp.w4b", "com.google.android.apps.messaging", "org.telegram.messenger",
        "org.thoughtcrime.securesms", "com.facebook.orca", "com.discord", "com.Slack", "com.microsoft.teams",
        "com.samsung.android.messaging", "jp.naver.line.android", "com.viber.voip",
    )
}
