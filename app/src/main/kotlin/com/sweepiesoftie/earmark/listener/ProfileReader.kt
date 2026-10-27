package com.sweepiesoftie.earmark.listener

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.DisplayNameSources
import com.sweepiesoftie.earmark.DebugLog

/**
 * Optional: your name, first name and nicknames from your own contact card ("Me"), so a group message that
 * says your name counts as for you (ADR 0028). Names typed in Settings are added to these. Only the profile is queried, never your other contacts. The
 * names stay in memory and are never stored or logged. Without READ_CONTACTS it stays idle.
 */
class ProfileReader(private val context: Context, private val log: DebugLog) {
    private var loggedIdle = false

    fun names(): List<String> {
        if (!Permissions.contacts(context)) {
            if (!loggedIdle) log.add("ProfileReader idle: no contacts permission, so only @mentions count in groups")
            loggedIdle = true
            return emptyList()
        }
        loggedIdle = false
        return try {
            val names = LinkedHashSet<String>()
            val profileColumns = arrayOf(ContactsContract.Profile.DISPLAY_NAME_PRIMARY, ContactsContract.Profile.DISPLAY_NAME_SOURCE)
            context.contentResolver.query(ContactsContract.Profile.CONTENT_URI, profileColumns, null, null, null)?.use { c ->
                // An empty card still has a display name: a placeholder, an email or a number. Only a real name counts.
                while (c.moveToNext()) if (c.getInt(1) >= DisplayNameSources.NICKNAME) c.getString(0)?.let { names += it }
            }
            val data = Uri.withAppendedPath(ContactsContract.Profile.CONTENT_URI, ContactsContract.Contacts.Data.CONTENT_DIRECTORY)
            val columns = arrayOf(ContactsContract.Data.MIMETYPE, StructuredName.GIVEN_NAME, Nickname.NAME)
            context.contentResolver.query(
                data,
                columns,
                "${ContactsContract.Data.MIMETYPE} IN (?, ?)",
                arrayOf(StructuredName.CONTENT_ITEM_TYPE, Nickname.CONTENT_ITEM_TYPE),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    // GIVEN_NAME is data2 and a nickname's NAME is data1, so read the column that fits the row.
                    val name = if (c.getString(0) == StructuredName.CONTENT_ITEM_TYPE) c.getString(1) else c.getString(2)
                    name?.takeIf { it.isNotBlank() }?.let { names += it.trim() }
                }
            }
            names.toList()
        } catch (e: RuntimeException) {
            log.error("profile query", e)
            emptyList()
        }
    }
}
