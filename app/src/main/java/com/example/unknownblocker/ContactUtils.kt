package com.example.unknownblocker

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

object ContactUtils {
    fun isNumberInContacts(context: Context, phoneNumber: String): Boolean {
        if (phoneNumber.isBlank()) return false
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                cursor.count > 0
            } ?: false
        } catch (_: SecurityException) {
            // Missing READ_CONTACTS — treat as unknown so we don't silently allow spam
            false
        }
    }
}
