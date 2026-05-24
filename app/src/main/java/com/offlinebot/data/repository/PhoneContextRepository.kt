package com.offlinebot.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.offlinebot.data.database.dao.PhoneContextDao
import com.offlinebot.data.database.entities.ContactContextEntity
import com.offlinebot.data.database.entities.MessageContextEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PhoneContextImportResult(
    val contactsImported: Int,
    val messagesImported: Int,
    val missingPermissions: List<String>
)

@Singleton
class PhoneContextRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val phoneContextDao: PhoneContextDao
) {
    suspend fun importAvailableContext(limitMessages: Int = 300): PhoneContextImportResult =
        withContext(Dispatchers.IO) {
            val missing = mutableListOf<String>()
            val contacts = if (hasPermission(Manifest.permission.READ_CONTACTS)) {
                readContacts()
            } else {
                missing += Manifest.permission.READ_CONTACTS
                emptyList()
            }

            val messages = if (hasPermission(Manifest.permission.READ_SMS)) {
                readMessages(limitMessages)
            } else {
                missing += Manifest.permission.READ_SMS
                emptyList()
            }

            if (contacts.isNotEmpty()) phoneContextDao.upsertContacts(contacts)
            if (messages.isNotEmpty()) phoneContextDao.upsertMessages(messages)

            PhoneContextImportResult(
                contactsImported = contacts.size,
                messagesImported = messages.size,
                missingPermissions = missing
            )
        }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun readContacts(): List<ContactContextEntity> {
        val now = System.currentTimeMillis()
        val projection = arrayOf(
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
        )
        return context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            projection,
            null,
            null,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
        )?.use { cursor ->
            val lookupIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)
            val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            buildList {
                while (cursor.moveToNext()) {
                    val lookup = cursor.getString(lookupIndex) ?: continue
                    val name = cursor.getString(nameIndex) ?: continue
                    add(ContactContextEntity(lookupKey = lookup, displayName = name, updatedAt = now))
                }
            }
        } ?: emptyList()
    }

    private fun readMessages(limit: Int): List<MessageContextEntity> {
        val now = System.currentTimeMillis()
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )
        return context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC LIMIT $limit"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            buildList {
                while (cursor.moveToNext()) {
                    val body = cursor.getString(bodyIndex).orEmpty()
                    if (body.isBlank()) continue
                    add(
                        MessageContextEntity(
                            id = cursor.getLong(idIndex),
                            address = cursor.getString(addressIndex),
                            body = body.take(MAX_MESSAGE_CHARS),
                            sentAt = cursor.getLong(dateIndex),
                            importedAt = now
                        )
                    )
                }
            }
        } ?: emptyList()
    }

    private companion object {
        const val MAX_MESSAGE_CHARS = 1000
    }
}
