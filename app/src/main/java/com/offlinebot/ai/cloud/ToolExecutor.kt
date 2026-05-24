package com.offlinebot.ai.cloud

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import android.provider.Telephony
import android.util.Log
import com.offlinebot.recorder.service.AlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolExecutor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** Execute a tool call JSON and return the result */
    fun execute(toolCallJson: String): String {
        return try {
            val json = JSONObject(toolCallJson.trim())
            val name = json.optString("name", json.optString("function", ""))
            val args = json.optJSONObject("parameters")
                ?: json.optJSONObject("arguments")
                ?: JSONObject()

            when (name) {
                "call_contact" -> callContact(args)
                "send_message" -> sendMessage(args)
                "show_notification" -> showNotification(args)
                "set_alarm" -> setAlarm(args)
                "get_call_log" -> getCallLog(args)
                "get_contacts" -> getContacts()
                else -> "Unknown tool: $name"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Tool execution failed", e)
            "Tool error: ${e.message}"
        }
    }

    private fun callContact(args: JSONObject): String {
        val name = anyString(args, "contact_name", "name", "contact")
        val number = anyString(args, "phone_number", "number", "phone")
        if (number.isBlank()) return "Need a phone number to call $name"
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$number")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "Dialer opened for $name ($number)"
    }

    private fun sendMessage(args: JSONObject): String {
        val name = anyString(args, "contact_name", "name", "contact", "to")
        val content = anyString(args, "message_content", "content", "message", "text", "body")
        if (content.isBlank()) return "Need message text"
        val number = anyString(args, "phone_number", "number", "phone")
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$number")
            putExtra("sms_body", content)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "Opening SMS to $name with message: ${content.take(50)}..."
    }

    private fun anyString(json: JSONObject, vararg keys: String): String {
        for (k in keys) { val v = json.optString(k, ""); if (v.isNotBlank()) return v }
        return ""
    }

    private fun showNotification(args: JSONObject): String {
        val title = anyString(args, "title", "label", "name", "header")
        val content = anyString(args, "content", "text", "message", "body", "description")
        if (title.isBlank() && content.isBlank()) return "Need title or content for notification"

        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        val channel = android.app.NotificationChannel("kbot_tools", "KBot Tools", android.app.NotificationManager.IMPORTANCE_HIGH)
        manager.createNotificationChannel(channel)

        val tapIntent = android.content.Intent(context, com.offlinebot.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pending = android.app.PendingIntent.getActivity(context, System.currentTimeMillis().toInt(), tapIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = androidx.core.app.NotificationCompat.Builder(context, "kbot_tools")
            .setSmallIcon(com.offlinebot.R.drawable.ic_launcher_foreground)
            .setContentTitle(title.ifBlank { "KBot" })
            .setContentText(content.ifBlank { "Reminder" })
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
        return "Notification shown: ${title.ifBlank { content.take(30) }}"
    }

    private fun setAlarm(args: JSONObject): String {
        val time = anyString(args, "time", "datetime", "alarm_time")
        var label = anyString(args, "label", "title", "name", "description", "reminder")
        val dateStr = args.optString("date", "") // yyyy-MM-dd format

        if (time.isBlank()) return "No time specified for alarm"

        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return "Invalid time: $time"
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (dateStr.isNotBlank()) {
                val dp = dateStr.split("-")
                if (dp.size == 3) {
                    set(Calendar.YEAR, dp[0].toInt())
                    set(Calendar.MONTH, dp[1].toInt() - 1)
                    set(Calendar.DAY_OF_MONTH, dp[2].toInt())
                }
            }
            if (before(Calendar.getInstance())) add(Calendar.DAY_OF_MONTH, 1)
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("label", label)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        val timeStr = "%02d:%02d".format(hour, minute)
        return "Alarm set for $timeStr - '$label'"
    }

    private fun getCallLog(args: JSONObject): String {
        val limit = args.optInt("limit", if (args.has("count")) args.optInt("count", 10) else 10)
        val calls = mutableListOf<String>()
        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI, null, null, null,
                "${CallLog.Calls.DATE} DESC LIMIT $limit"
            )?.use { cursor ->
                val numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
                val durationIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)
                while (cursor.moveToNext()) {
                    val num = cursor.getString(numberIdx) ?: "Unknown"
                    val type = when (cursor.getInt(typeIdx)) {
                        CallLog.Calls.INCOMING_TYPE -> "Incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                        CallLog.Calls.MISSED_TYPE -> "Missed"
                        else -> "Unknown"
                    }
                    val dur = cursor.getLong(durationIdx)
                    val date = java.text.SimpleDateFormat("MMM d HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(cursor.getLong(dateIdx)))
                    calls.add("$type: $num (${dur}s) at $date")
                }
            }
        } catch (e: SecurityException) {
            return "Call log access denied. Grant READ_CALL_LOG permission."
        }
        return if (calls.isEmpty()) "No call log entries found."
        else "Recent calls:\n${calls.joinToString("\n")}"
    }

    private fun getContacts(): String {
        val contacts = mutableListOf<String>()
        try {
            context.contentResolver.query(
                android.provider.ContactsContract.Contacts.CONTENT_URI,
                null, null, null, "display_name ASC LIMIT 20"
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(android.provider.ContactsContract.Contacts.DISPLAY_NAME)
                val idIdx = cursor.getColumnIndex(android.provider.ContactsContract.Contacts._ID)
                val hasPhoneIdx = cursor.getColumnIndex(android.provider.ContactsContract.Contacts.HAS_PHONE_NUMBER)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx) ?: continue
                    val hasPhone = cursor.getInt(hasPhoneIdx) > 0
                    if (hasPhone) {
                        val contactId = cursor.getString(idIdx)
                        context.contentResolver.query(
                            android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            "${android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                            arrayOf(contactId), null
                        )?.use { phoneCursor ->
                            val numIdx = phoneCursor.getColumnIndex(
                                android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                            while (phoneCursor.moveToNext()) {
                                contacts.add("$name: ${phoneCursor.getString(numIdx)}")
                            }
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            return "Contacts access denied."
        }
        return if (contacts.isEmpty()) "No contacts found."
        else "Contacts:\n${contacts.joinToString("\n")}"
    }

    companion object {
        private const val TAG = "ToolExecutor"
    }
}
