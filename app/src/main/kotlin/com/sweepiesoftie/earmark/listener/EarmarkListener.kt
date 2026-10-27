package com.sweepiesoftie.earmark.listener

import android.service.notification.NotificationListenerService

/** The one long-lived component (ADR 0001). Adapters hang off it as they are built. */
class EarmarkListener : NotificationListenerService()
