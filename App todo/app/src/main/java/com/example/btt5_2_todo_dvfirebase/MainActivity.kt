package com.example.btt5_2_todo_dvfirebase

import android.app.Activity
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.btt5_2_todo_dvfirebase.ui.theme.Btt5_2_todo_dvfirebaseTheme
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseException
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d("MainActivity", "Quyen thong bao: $isGranted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        NotificationManagerHelper.init(applicationContext)
        TaskAlarmScheduler.rescheduleAll(applicationContext)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        handleNotificationIntent(intent)

        setContent {
            val isDark = when (globalSettings.themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            Btt5_2_todo_dvfirebaseTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = if (isDark) Color(0xFF121212) else Color(0xFFF9FAFB)
                ) {
                    TodoApp()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val title = intent?.getStringExtra("fcm_title")
            ?: intent?.getStringExtra("title")
            ?: intent?.getStringExtra("gcm.notification.title")
        val body = intent?.getStringExtra("fcm_body")
            ?: intent?.getStringExtra("body")
            ?: intent?.getStringExtra("gcm.notification.body")
            ?: intent?.getStringExtra("message")
        val openRoute = intent?.getStringExtra("open_route")
        val taskId = intent?.getStringExtra("task_id")

        if (!title.isNullOrBlank() || !body.isNullOrBlank()) {
            val notifType = if (title?.contains("Nhắc") == true || body?.contains("hạn") == true) "reminder" else "fcm"
            NotificationManagerHelper.addNotification(
                title = title ?: "Thông báo từ máy chủ",
                message = body ?: "",
                type = notifType
            )
        }

        if (taskId != null) {
            NotificationManagerHelper.pendingRoute = "task_detail/$taskId"
        } else if (openRoute != null || !title.isNullOrBlank() || !body.isNullOrBlank()) {
            NotificationManagerHelper.pendingRoute = openRoute ?: "notifications"
        }
    }
}

// Thông báo đẩy cloud messaging
class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "Thông báo từ UTH Todo"

        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: remoteMessage.data["message"]
            ?: remoteMessage.data["content"]
            ?: ""

        Log.d("FCM", "Đã nhận thông báo từ Server: Title='$title', Body='$body'")

        NotificationManagerHelper.addNotification(
            title = title,
            message = body,
            type = "fcm"
        )

        NotificationManagerHelper.showSystemNotification(applicationContext, title, body)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "FCM Token mới: $token")
        NotificationManagerHelper.currentFcmToken = token
    }
}

// Báo thức,ời nhắc công việc (AlarmManager & BroadcastReceiver)
object TaskAlarmScheduler {
    private const val TAG = "TaskAlarmScheduler"
    private const val PREFS_NAME = "task_reminders_prefs"
    private const val KEY_SCHEDULED_TASKS = "scheduled_tasks"

    data class ScheduledTaskInfo(
        val id: String,
        val title: String,
        val categoryName: String,
        val date: String,
        val time: String,
        val offsetMinutes: Int,
        val triggerTimeMs: Long,
        val deadlineTimeMs: Long
    )

    fun scheduleTaskReminder(
        context: Context,
        taskId: String,
        title: String,
        categoryName: String = "",
        date: String,
        time: String,
        reminder: Boolean,
        reminderTime: String = "Trước 15 phút",
        isCompleted: Boolean = false
    ) {
        if (!reminder || isCompleted) {
            cancelTaskReminder(context, taskId)
            return
        }

        val offsetMinutes = parseOffsetMinutes(reminderTime)
        val (triggerTimeMs, deadlineTimeMs) = calculateTriggerTime(date, time, offsetMinutes)
        val now = System.currentTimeMillis()

        if (deadlineTimeMs <= now) {
            Log.d(TAG, "Task $title ($taskId) đã quá hạn ($deadlineTimeMs <= $now), bỏ qua hẹn giờ.")
            cancelTaskReminder(context, taskId)
            return
        }

        val effectiveTriggerMs = if (triggerTimeMs <= now) {
            now + 2000L
        } else {
            triggerTimeMs
        }

        setAlarm(
            context = context,
            taskId = taskId,
            title = title,
            categoryName = categoryName,
            date = date,
            time = time,
            offsetMinutes = offsetMinutes,
            triggerTimeMs = effectiveTriggerMs,
            deadlineTimeMs = deadlineTimeMs
        )
    }

    private fun setAlarm(
        context: Context,
        taskId: String,
        title: String,
        categoryName: String,
        date: String,
        time: String,
        offsetMinutes: Int,
        triggerTimeMs: Long,
        deadlineTimeMs: Long,
        persist: Boolean = true
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            action = TaskReminderReceiver.ACTION_TASK_REMINDER
            putExtra("task_id", taskId)
            putExtra("task_title", title)
            putExtra("task_category", categoryName)
            putExtra("task_date", date)
            putExtra("task_time", time)
            putExtra("reminder_offset_minutes", offsetMinutes)
            putExtra("deadline_ms", deadlineTimeMs)
        }

        val requestCode = (taskId.hashCode() and 0x7FFFFFFF)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
            }
            if (persist) {
                saveScheduledTask(
                    context = context,
                    taskId = taskId,
                    title = title,
                    categoryName = categoryName,
                    date = date,
                    time = time,
                    offsetMinutes = offsetMinutes,
                    triggerTimeMs = triggerTimeMs,
                    deadlineTimeMs = deadlineTimeMs
                )
            }
            Log.d(TAG, "Đã lên lịch nhắc nhở task '$title' vào lúc ${Date(triggerTimeMs)} (trước hạn chót $offsetMinutes phút)")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException khi setExactAndAllowWhileIdle, chuyển sang set fallback: ${e.message}")
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                if (persist) {
                    saveScheduledTask(
                        context = context,
                        taskId = taskId,
                        title = title,
                        categoryName = categoryName,
                        date = date,
                        time = time,
                        offsetMinutes = offsetMinutes,
                        triggerTimeMs = triggerTimeMs,
                        deadlineTimeMs = deadlineTimeMs
                    )
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Lỗi khi đặt báo thức dự phòng: ${ex.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi đặt báo thức cho task $taskId: ${e.message}")
        }
    }

    fun cancelTaskReminder(context: Context, taskId: String) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, TaskReminderReceiver::class.java).apply {
                action = TaskReminderReceiver.ACTION_TASK_REMINDER
            }
            val requestCode = (taskId.hashCode() and 0x7FFFFFFF)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                Log.d(TAG, "Đã hủy báo thức cho task: $taskId")
            }
            removeScheduledTask(context, taskId)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi hủy báo thức cho task $taskId: ${e.message}")
        }
    }

    fun rescheduleAll(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val tasks = getScheduledTasks(context)
            val now = System.currentTimeMillis()
            Log.d(TAG, "Khôi phục báo thức cho ${tasks.size} công việc đã lưu...")
            for ((id, itemTitle, catName, itemDate, itemTime, offsetMins, trigMs, dlineMs) in tasks) {
                if (dlineMs > now) {
                    val effectiveTrigger = if (trigMs <= now) now + 2000L else trigMs
                    setAlarm(
                        context = context,
                        taskId = id,
                        title = itemTitle,
                        categoryName = catName,
                        date = itemDate,
                        time = itemTime,
                        offsetMinutes = offsetMins,
                        triggerTimeMs = effectiveTrigger,
                        deadlineTimeMs = dlineMs,
                        persist = false
                    )
                } else {
                    removeScheduledTask(context, id)
                }
            }
        }
    }

    fun parseOffsetMinutes(reminderTime: String): Int {
        val lower = reminderTime.lowercase(Locale.getDefault())
        return when {
            lower.contains("30") -> 30
            lower.contains("5") -> 5
            lower.contains("10") -> 10
            lower.contains("1 giờ") || lower.contains("60") -> 60
            lower.contains("1 ngày") || lower.contains("24") -> 24 * 60
            lower.contains("đúng giờ") || lower.contains("0") -> 0
            else -> 15 // Mặc định trước 15 phút
        }
    }

    fun calculateTriggerTime(dateStr: String, timeStr: String, offsetMinutes: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance()

        val timeRegex = Regex("""(\d{1,2})[:hH](\d{2})""")
        val timeMatch = timeRegex.find(timeStr.trim())
        if (timeMatch != null) {
            val hour = timeMatch.groupValues[1].toInt()
            val min = timeMatch.groupValues[2].toInt()
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, min)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
        } else {
            cal.set(Calendar.HOUR_OF_DAY, 9)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
        }

        val lowerDate = dateStr.lowercase(Locale.getDefault()).trim()
        when {
            lowerDate.contains("hôm nay") || lowerDate.contains("hom nay") || lowerDate.contains("today") -> {
            }
            lowerDate.contains("ngày mai") || lowerDate.contains("ngay mai") || lowerDate.contains("tomorrow") -> {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            lowerDate.contains("tuần này") || lowerDate.contains("tuan nay") || lowerDate.contains("week") -> {
                cal.add(Calendar.DAY_OF_YEAR, 5)
            }
            else -> {
                val isoDateRegex = Regex("""(\d{4})[/\-](\d{1,2})[/\-](\d{1,2})""")
                val isoMatch = isoDateRegex.find(lowerDate)
                if (isoMatch != null) {
                    val y = isoMatch.groupValues[1].toInt()
                    val m = isoMatch.groupValues[2].toInt() - 1
                    val d = isoMatch.groupValues[3].toInt()
                    cal.set(Calendar.YEAR, y)
                    cal.set(Calendar.MONTH, m)
                    cal.set(Calendar.DAY_OF_MONTH, d)
                } else {
                    val dmyRegex = Regex("""(\d{1,2})[/\-](\d{1,2})(?:[/\-](\d{2,4}))?""")
                    val dmyMatch = dmyRegex.find(lowerDate)
                    if (dmyMatch != null) {
                        val d = dmyMatch.groupValues[1].toInt()
                        val m = dmyMatch.groupValues[2].toInt() - 1
                        val yStr = dmyMatch.groupValues[3]
                        val y = if (yStr.isNotEmpty()) {
                            if (yStr.length == 2) 2000 + yStr.toInt() else yStr.toInt()
                        } else {
                            cal.get(Calendar.YEAR)
                        }
                        cal.set(Calendar.YEAR, y)
                        cal.set(Calendar.MONTH, m)
                        cal.set(Calendar.DAY_OF_MONTH, d)
                    }
                }
            }
        }

        val deadlineTimeMs = cal.timeInMillis
        val triggerTimeMs = deadlineTimeMs - (offsetMinutes * 60 * 1000L)
        return Pair(triggerTimeMs, deadlineTimeMs)
    }

    private fun saveScheduledTask(
        context: Context,
        taskId: String,
        title: String,
        categoryName: String,
        date: String,
        time: String,
        offsetMinutes: Int,
        triggerTimeMs: Long,
        deadlineTimeMs: Long
    ) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentList = getScheduledTasks(context).toMutableList()
            currentList.removeAll { it.id == taskId }
            currentList.add(
                ScheduledTaskInfo(
                    id = taskId,
                    title = title,
                    categoryName = categoryName,
                    date = date,
                    time = time,
                    offsetMinutes = offsetMinutes,
                    triggerTimeMs = triggerTimeMs,
                    deadlineTimeMs = deadlineTimeMs
                )
            )

            val jsonArray = JSONArray()
            for ((id, title, categoryName, date, time, offsetMinutes, triggerTimeMs, deadlineTimeMs) in currentList) {
                val obj = JSONObject().apply {
                    put("id", id)
                    put("title", title)
                    put("categoryName", categoryName)
                    put("date", date)
                    put("time", time)
                    put("offsetMinutes", offsetMinutes)
                    put("triggerTimeMs", triggerTimeMs)
                    put("deadlineTimeMs", deadlineTimeMs)
                }
                jsonArray.put(obj)
            }
            prefs.edit { putString(KEY_SCHEDULED_TASKS, jsonArray.toString()) }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi lưu ScheduledTask vào prefs: ${e.message}")
        }
    }

    fun removeScheduledTask(context: Context, taskId: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentList = getScheduledTasks(context).toMutableList()
            val removed = currentList.removeAll { it.id == taskId }
            if (removed) {
                val jsonArray = JSONArray()
                for ((id, title, categoryName, date, time, offsetMinutes, triggerTimeMs, deadlineTimeMs) in currentList) {
                    val obj = JSONObject().apply {
                        put("id", id)
                        put("title", title)
                        put("categoryName", categoryName)
                        put("date", date)
                        put("time", time)
                        put("offsetMinutes", offsetMinutes)
                        put("triggerTimeMs", triggerTimeMs)
                        put("deadlineTimeMs", deadlineTimeMs)
                    }
                    jsonArray.put(obj)
                }
                prefs.edit { putString(KEY_SCHEDULED_TASKS, jsonArray.toString()) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi xóa ScheduledTask khỏi prefs: ${e.message}")
        }
    }

    fun getScheduledTasks(context: Context): List<ScheduledTaskInfo> {
        val result = mutableListOf<ScheduledTaskInfo>()
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_SCHEDULED_TASKS, null) ?: return result
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                result.add(
                    ScheduledTaskInfo(
                        id = obj.getString("id"),
                        title = obj.optString("title", ""),
                        categoryName = obj.optString("categoryName", ""),
                        date = obj.optString("date", ""),
                        time = obj.optString("time", ""),
                        offsetMinutes = obj.optInt("offsetMinutes", 15),
                        triggerTimeMs = obj.optLong("triggerTimeMs", 0L),
                        deadlineTimeMs = obj.optLong("deadlineTimeMs", 0L)
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi đọc danh sách ScheduledTask: ${e.message}")
        }
        return result
    }
}

// Báo thức, lời nhắc (BroadcastReceiver)
class TaskReminderReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TASK_REMINDER = "com.example.btt5_2_todo_dvfirebase.ACTION_TASK_REMINDER"
        const val CHANNEL_ID = "uth_task_reminder_channel"
        const val CHANNEL_NAME = "Nhắc nhở hạn chót công việc"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra("task_id") ?: ""
        val taskTitle = intent.getStringExtra("task_title") ?: "Công việc"
        val taskTime = intent.getStringExtra("task_time") ?: ""
        val taskDate = intent.getStringExtra("task_date") ?: ""
        var deadlineMs = intent.getLongExtra("deadline_ms", 0L)
        val offsetMinutes = intent.getIntExtra("reminder_offset_minutes", 15)

        if (deadlineMs <= 0L) {
            val calc = TaskAlarmScheduler.calculateTriggerTime(taskDate, taskTime, offsetMinutes)
            deadlineMs = calc.second
        }

        val now = System.currentTimeMillis()
        val diffMs = deadlineMs - now
        val diffMinutes = if (diffMs > 0) {
            ((diffMs + 59999) / 60000).toInt()
        } else {
            0
        }

        val remainingText = when {
            diffMinutes > 60 -> {
                val h = diffMinutes / 60
                val m = diffMinutes % 60
                if (m > 0) "còn ${h}h ${m}p" else "còn ${h}h"
            }
            diffMinutes > 0 -> "còn $diffMinutes phút"
            diffMinutes == 0 && diffMs >= -60000L -> "đến hạn ngay bây giờ"
            else -> "đã đến hạn chót"
        }

        Log.d("TaskReminderReceiver", "Nhận broadcast báo thức cho task '$taskTitle' ($remainingText, time=$taskTime)")

        showReminderNotification(
            context = context,
            taskId = taskId,
            taskTitle = taskTitle,
            taskTime = taskTime,
            taskDate = taskDate,
            remainingText = remainingText
        )

        val displayTimeText = if (taskTime.isNotBlank()) " lúc $taskTime" else ""
        val displayDateText = if (taskDate.isNotBlank() && taskDate != "Hôm nay") " ngày $taskDate" else ""
        val notifTitle = "⏰ Nhắc nhở hạn chót: $taskTitle"
        val notifMessage = "Công việc '$taskTitle' sẽ đến hạn$displayTimeText$displayDateText ($remainingText). Hãy chuẩn bị hoàn thành nhé!"

        NotificationManagerHelper.addNotification(
            title = notifTitle,
            message = notifMessage,
            type = "reminder",
            context = context,
            deadlineMs = deadlineMs,
            taskTime = taskTime
        )

        TaskAlarmScheduler.removeScheduledTask(context, taskId)
    }

    private fun showReminderNotification(
        context: Context,
        taskId: String,
        taskTitle: String,
        taskTime: String,
        taskDate: String,
        remainingText: String
    ) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Kênh thông báo nhắc nhở trước hạn chót của công việc (mặc định 15 phút)"
                    enableLights(true)
                    lightColor = android.graphics.Color.BLUE
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 200, 500)
                    val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    setSound(soundUri, null)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val tapIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("task_id", taskId)
                putExtra("open_route", "task_detail")
                putExtra("title", "⏰ Nhắc việc: $taskTitle")
                putExtra("message", "Công việc sắp đến hạn lúc $taskTime ($remainingText)")
            }

            val requestCode = (taskId.hashCode() and 0x7FFFFFFF)
            val tapPendingIntent = PendingIntent.getActivity(
                context,
                requestCode,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val displayTime = if (taskTime.isNotBlank()) " lúc $taskTime" else ""
            val displayDate = if (taskDate.isNotBlank() && taskDate != "Hôm nay") " ngày $taskDate" else ""
            val bigText = "Công việc '$taskTitle' sẽ đến hạn$displayTime$displayDate. Bạn $remainingText để hoàn thành đúng hạn!"

            val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("⏰ Nhắc nhở hạn chót: $taskTitle")
                .setContentText("Hạn chót$displayTime ($remainingText)!")
                .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(tapPendingIntent)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())

            notificationManager.notify(requestCode, notificationBuilder.build())
        } catch (e: Exception) {
            Log.e("TaskReminderReceiver", "Lỗi hiển thị thông báo nhắc nhở: ${e.message}")
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("BootReceiver", "Nhận broadcast hệ thống khởi động: $action")
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            TaskAlarmScheduler.rescheduleAll(context)
        }
    }
}

// Database realtime
data class AppNotification(
    val id: String = System.currentTimeMillis().toString(),
    val title: String = "",
    val message: String = "",
    val time: String = "",
    val isRead: Boolean = false,
    val type: String = "fcm",
    val deadlineMs: Long = 0L,
    val taskTime: String = ""
)

object NotificationManagerHelper {
    val notifications = mutableStateListOf<AppNotification>()
    var currentFcmToken by mutableStateOf("")
    var pendingRoute by mutableStateOf<String?>(null)
    var latestInAppReminder by mutableStateOf<AppNotification?>(null)
    private var appContext: Context? = null

    var activeUserId: String = "guest_user"

    fun getEffectiveUserId(): String {
        val authId = FirebaseServiceHelper.getCurrentUserId()
        if (authId.isNotBlank() && authId != "guest_user") {
            activeUserId = authId
            return authId
        }
        return activeUserId.ifBlank { "guest_user" }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        activeUserId = getEffectiveUserId()
        loadFromPrefs(context, activeUserId)
    }

    fun switchUser(userId: String, context: Context? = null) {
        activeUserId = userId
        notifications.clear()
        loadFromPrefs(context ?: appContext, userId)
    }

    fun clearMemory() {
        notifications.clear()
        latestInAppReminder = null
    }

    private fun getPrefsName(userId: String = getEffectiveUserId()): String {
        val effective = if (userId.isNotBlank() && userId != "guest_user") userId else getEffectiveUserId()
        return if (effective.isNotBlank() && effective != "guest_user") {
            "app_notifications_prefs_$effective"
        } else {
            "app_notifications_prefs_guest"
        }
    }

    fun loadFromPrefs(context: Context?, userId: String = getEffectiveUserId()) {
        val ctx = context ?: appContext ?: return
        val effectiveUid = if (userId.isNotBlank() && userId != "guest_user") userId else getEffectiveUserId()
        try {
            val prefs = ctx.getSharedPreferences(getPrefsName(effectiveUid), Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("notifications_list", null) ?: return
            val jsonArray = JSONArray(jsonStr)
            val loadedList = mutableListOf<AppNotification>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                loadedList.add(
                    AppNotification(
                        id = obj.optString("id", System.currentTimeMillis().toString()),
                        title = obj.optString("title", ""),
                        message = obj.optString("message", ""),
                        time = obj.optString("time", ""),
                        isRead = obj.optBoolean("isRead", false),
                        type = obj.optString("type", "fcm"),
                        deadlineMs = obj.optLong("deadlineMs", 0L),
                        taskTime = obj.optString("taskTime", "")
                    )
                )
            }
            if (loadedList.isNotEmpty() && notifications.isEmpty()) {
                notifications.addAll(loadedList)
            }
        } catch (e: Exception) {
            Log.e("NotificationHelper", "Lỗi tải notifications từ prefs: ${e.message}")
        }
    }

    fun saveToPrefs(context: Context? = appContext, userId: String = getEffectiveUserId()) {
        val ctx = context ?: appContext ?: return
        val effectiveUid = if (userId.isNotBlank() && userId != "guest_user") userId else getEffectiveUserId()
        try {
            val prefs = ctx.getSharedPreferences(getPrefsName(effectiveUid), Context.MODE_PRIVATE)
            val jsonArray = JSONArray()
            for ((id, title, message, time, isRead, type, deadlineMs, taskTime) in notifications.take(50)) {
                val obj = JSONObject().apply {
                    put("id", id)
                    put("title", title)
                    put("message", message)
                    put("time", time)
                    put("isRead", isRead)
                    put("type", type)
                    put("deadlineMs", deadlineMs)
                    put("taskTime", taskTime)
                }
                jsonArray.put(obj)
            }
            prefs.edit { putString("notifications_list", jsonArray.toString()) }
        } catch (e: Exception) {
            Log.e("NotificationHelper", "Lỗi lưu notifications vào prefs: ${e.message}")
        }
    }

    fun addNotification(
        title: String,
        message: String,
        type: String = "fcm",
        context: Context? = null,
        deadlineMs: Long = 0L,
        taskTime: String = ""
    ) {
        val uid = getEffectiveUserId()
        val sdf = SimpleDateFormat("HH:mm - dd/MM", Locale.getDefault())
        val currentTime = sdf.format(Date())
        val item = AppNotification(
            id = System.currentTimeMillis().toString(),
            title = title.ifBlank { "Thông báo từ UTH Todo" },
            message = message,
            time = currentTime,
            isRead = false,
            type = type,
            deadlineMs = deadlineMs,
            taskTime = taskTime
        )
        notifications.add(0, item)
        if (type == "reminder") {
            latestInAppReminder = item
        }
        saveToPrefs(context, uid)
        FirebaseServiceHelper.saveNotificationToFirebase(uid, item)
    }

    fun markAllAsRead(context: Context? = null) {
        val uid = getEffectiveUserId()
        for (i in notifications.indices) {
            notifications[i] = notifications[i].copy(isRead = true)
        }
        saveToPrefs(context, uid)
        FirebaseServiceHelper.markAllNotificationsAsReadInFirebase(uid, notifications.map { it.id })
    }

    fun markAsRead(id: String, context: Context? = null) {
        val uid = getEffectiveUserId()
        val index = notifications.indexOfFirst { it.id == id }
        if (index != -1) {
            notifications[index] = notifications[index].copy(isRead = true)
        }
        saveToPrefs(context, uid)
        FirebaseServiceHelper.markNotificationAsReadInFirebase(uid, id)
    }

    fun removeNotification(id: String, context: Context? = null) {
        val uid = getEffectiveUserId()
        notifications.removeAll { it.id == id }
        saveToPrefs(context, uid)
        FirebaseServiceHelper.deleteNotificationInFirebase(uid, id)
    }

    fun clearAll(context: Context? = null) {
        val uid = getEffectiveUserId()
        val idsToDelete = notifications.map { it.id }
        notifications.clear()
        saveToPrefs(context, uid)
        FirebaseServiceHelper.clearAllNotificationsInFirebase(uid, idsToDelete)
    }

    fun unreadCount(): Int = notifications.count { !it.isRead }

    fun showSystemNotification(context: Context, title: String, message: String) {
        try {
            val channelId = "uth_todo_fcm_channel"
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Thông báo UTH Todo",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Kênh nhận thông báo máy chủ và lời nhắc công việc"
                    enableLights(true)
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("fcm_title", title)
                putExtra("fcm_body", message)
                putExtra("open_route", "notifications")
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                (System.currentTimeMillis() % 100000).toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify((System.currentTimeMillis() % 100000).toInt(), notification)
        } catch (e: Exception) {
            Log.e("NotificationHelper", "Lỗi hiển thị system notification: ${e.message}")
        }
    }
}

data class StartScreenData(
    val route: String,
    val nextRoute: String,
    val logoRes: Int,
    val appName: String,
    val slogan: String,
    val version: String
)

data class Category(
    val tagName: String,
    val textColor: Color,
    val bgColor: Color
)

data class Onboarding(
    val route: String,
    val nextRoute: String,
    val stepIndex: Int,
    val logoRes: Int,
    val appName: String,
    val skipText: String,
    val title: String,
    val description: String,
    val buttonText: String,
    val checklistStates: List<Boolean> = emptyList(),
    val categories: List<Category> = emptyList(),
    val reminderText: String = ""
)

data class LoginScreenData(
    val route: String,
    val nextRoute: String,
    val logoRes: Int,
    val title: String,
    val description: String,
    val usernamePlaceholder: String,
    val passwordPlaceholder: String,
    val loginButtonText: String,
    val googleButtonText: String,
    val guestButtonText: String,
    val termsText: String
)

data class SubTaskItem(
    val id: String = "",
    val title: String = "",
    val isCompleted: Boolean = false
)

data class FirebaseTaskModel(
    val id: String = "",
    val title: String = "",
    val categoryName: String = "Học tập",
    val time: String = "08:00",
    val date: String = "Hôm nay",
    val note: String = "",
    val priority: String = "Vừa",
    val reminder: Boolean = false,
    val reminderTime: String = "Trước 15 phút",
    val completed: Boolean = false,
    val subTasks: List<SubTaskItem> = emptyList()
)

class TodoTask(
    val id: String,
    title: String,
    category: Category,
    time: String,
    date: String = "Hôm nay",
    note: String = "",
    priority: String = "Vừa",
    reminder: Boolean = false,
    reminderTime: String = "Trước 15 phút",
    initialCompleted: Boolean = false,
    subTasks: List<SubTaskItem> = emptyList()
) {
    var title by mutableStateOf(title)
    var category by mutableStateOf(category)
    var time by mutableStateOf(time)
    var date by mutableStateOf(date)
    var note by mutableStateOf(note)
    var priority by mutableStateOf(priority)
    var reminder by mutableStateOf(reminder)
    var reminderTime by mutableStateOf(reminderTime)
    var isCompleted by mutableStateOf(initialCompleted)
    val subTasks = mutableStateListOf<SubTaskItem>().apply { addAll(subTasks) }
}

data class DailyStat(
    val dayLabel: String,
    val heightRatio: Float,
    val isHighlighted: Boolean = false
)

data class UserProfile(
    val initials: String,
    val fullName: String,
    val email: String,
    val accountBadge: String,
    val completedTasks: Int,
    val pendingTasks: Int,
    val completionRate: String,
    val weeklyStats: List<DailyStat>
)

data class HomeScreenData(
    val route: String,
    val dateText: String,
    val progressTitle: String,
    val progressEncourage: String,
    val todaySectionTitle: String,
    val seeAllText: String
)

class AppSettings {
    var themeMode by mutableStateOf("light") // "light", "dark", "auto"
    var accentColor by mutableStateOf(Color(0xFF254EDB))
    var fontSizeScale by mutableFloatStateOf(1.0f)
    var reminderEnabled by mutableStateOf(true)
    var soundEnabled by mutableStateOf(true)
    var dailySummaryEnabled by mutableStateOf(false)
    @Suppress("unused")
    var language by mutableStateOf("Tiếng Việt")
}
val globalSettings = AppSettings()

class TodoAppRepository {
    val studyCategory = Category("Học tập", Color(0xFF254EDB), Color(0xFFDCE6FF))
    val lifeCategory = Category("Cuộc sống", Color(0xFFD97706), Color(0xFFFEF3C7))

    fun getCategoryByName(name: String): Category {
        return if (name == lifeCategory.tagName) lifeCategory else studyCategory
    }

    val startData = StartScreenData(
        route = "start",
        nextRoute = "step_1",
        logoRes = R.drawable.logo_app,
        appName = "UTH Todo",
        slogan = "Việc học, việc nhà – gọn gàng mỗi ngày",
        version = "Phiên bản 2.1.0"
    )

    val onboardingList = listOf(
        Onboarding(
            route = "step_1",
            nextRoute = "step_2",
            logoRes = R.drawable.logo_nho,
            stepIndex = 0,
            appName = "UTH Todo",
            skipText = "Bỏ qua",
            title = "Mọi việc, gọn trong một chỗ",
            description = "Ghi lại bài tập, việc nhà, lịch hẹn. Không còn quên việc nào.",
            buttonText = "Tiếp tục",
            checklistStates = listOf(true, false, false)
        ),
        Onboarding(
            route = "step_2",
            nextRoute = "step_3",
            logoRes = R.drawable.logo_nho,
            stepIndex = 1,
            appName = "UTH Todo",
            skipText = "Bỏ qua",
            title = "Chia theo Học tập và Cuộc sống",
            description = "Mỗi việc có nhóm riêng, nhìn là biết hôm nay cần lo gì.",
            buttonText = "Tiếp tục",
            categories = listOf(studyCategory, lifeCategory)
        ),
        Onboarding(
            route = "step_3",
            nextRoute = "login",
            stepIndex = 2,
            logoRes = R.drawable.logo_nho,
            appName = "UTH Todo",
            skipText = "Bỏ qua",
            title = "Nhắc đúng lúc",
            description = "Bật thông báo để UTH Todo nhắc bạn trước hạn chót.",
            buttonText = "Bắt đầu ngay",
            reminderText = "Ôn bài Toán lúc 19:00"
        )
    )

    val loginData = LoginScreenData(
        route = "login",
        nextRoute = "home",
        logoRes = R.drawable.logo_nho,
        title = "Chào mừng đến với\nUTH Todo",
        description = "Đăng nhập để lưu công việc và đồng bộ\ntrên mọi thiết bị của bạn",
        usernamePlaceholder = "Tên đăng nhập hoặc Email",
        passwordPlaceholder = "Mật khẩu",
        loginButtonText = "Đăng nhập",
        googleButtonText = "Tiếp tục với Google",
        guestButtonText = "Dùng thử không cần tài khoản",
        termsText = "Khi tiếp tục, bạn đồng ý với Điều khoản sử dụng và Chính sách quyền riêng tư của UTH Todo"
    )

    val homeData = HomeScreenData(
        route = "home",
        dateText = "Thứ Ba, 20 tháng 10",
        progressTitle = "Tiến độ hôm nay",
        progressEncourage = "Bắt đầu từ việc nhỏ nhất nhé.",
        todaySectionTitle = "Việc hôm nay",
        seeAllText = "Xem tất cả"
    )

    val tasks = mutableStateListOf<TodoTask>()

    val defaultStats = listOf(
        DailyStat("T2", 0.55f, false),
        DailyStat("T3", 0.85f, true),
        DailyStat("T4", 0.25f, false),
        DailyStat("T5", 0.65f, false),
        DailyStat("T6", 0.45f, false),
        DailyStat("T7", 0.15f, false),
        DailyStat("CN", 0.20f, false)
    )

    @Suppress("unused")
    val googleUserProfile = UserProfile(
        initials = "MA",
        fullName = "Minh Anh",
        email = "minhanh@gmail.com",
        accountBadge = "✓ Đã liên kết tài khoản Google",
        completedTasks = 128,
        pendingTasks = 4,
        completionRate = "86%",
        weeklyStats = defaultStats
    )

    val guestUserProfile = UserProfile(
        initials = "KH",
        fullName = "Khách",
        email = "",
        accountBadge = "Tài khoản dùng thử (Khách)",
        completedTasks = 2,
        pendingTasks = 4,
        completionRate = "33%",
        weeklyStats = defaultStats
    )
}

val appData = TodoAppRepository()

fun buildUserProfile(firebaseUser: FirebaseUser?): UserProfile {
    if (firebaseUser == null) {
        return appData.guestUserProfile
    }

    val email = firebaseUser.email?.trim().orEmpty()
    val rawName = firebaseUser.displayName?.trim().orEmpty()
    val fullName = when {
        rawName.isNotBlank() -> rawName
        email.isNotBlank() -> email.substringBefore("@")
        !firebaseUser.phoneNumber.isNullOrBlank() -> firebaseUser.phoneNumber!!
        else -> "Người dùng"
    }
    val initials = when {
        rawName.isNotBlank() -> rawName.take(2).uppercase()
        email.isNotBlank() -> email.take(2).uppercase()
        !firebaseUser.phoneNumber.isNullOrBlank() -> firebaseUser.phoneNumber!!.takeLast(2)
        else -> "ND"
    }
    val badge = when {
        firebaseUser.providerData.any { it.providerId == "google.com" } -> "✓ Đã liên kết tài khoản Google"
        email.isNotBlank() -> "✓ Đã đăng nhập bằng Email"
        !firebaseUser.phoneNumber.isNullOrBlank() -> "✓ Đã đăng nhập bằng SĐT"
        firebaseUser.isAnonymous -> "Tài khoản dùng thử (Khách)"
        else -> "✓ Đã đăng nhập"
    }

    return UserProfile(
        initials = initials,
        fullName = fullName,
        email = email,
        accountBadge = badge,
        completedTasks = 0,
        pendingTasks = 0,
        completionRate = "0%",
        weeklyStats = appData.defaultStats
    )
}

//Mock api
object MockTeachApiService {
    const val BASE_URL = "https://share.tryapps.store/mock/teach"
    const val TOKEN = "TuanTruong-uth-student"
    private const val TAG = "MockTeachApi"

    fun formatDisplayDate(dateStr: String): String {
        return when (dateStr.trim()) {
            "2026-10-20" -> "Hôm nay"
            "2026-10-21" -> "Ngày mai"
            "2026-10-25" -> "Tuần này"
            else -> dateStr.ifBlank { "Hôm nay" }
        }
    }

    fun parseJsonToTask(obj: JSONObject): TodoTask {
        val id = obj.optString("id", System.currentTimeMillis().toString())
        val title = obj.optString("title", "Công việc")
        val note = obj.optString("note", "")
        val catStr = obj.optString("category", "study")
        val category = if (catStr.equals("life", ignoreCase = true) || catStr.contains("cuộc sống", ignoreCase = true)) {
            appData.lifeCategory
        } else {
            appData.studyCategory
        }
        val rawDate = obj.optString("date", "2026-10-20")
        val date = formatDisplayDate(rawDate)
        val time = obj.optString("time", "09:00")
        val priorityRaw = obj.optString("priority", "medium").lowercase(Locale.getDefault())
        val priority = when {
            priorityRaw.contains("high") || priorityRaw.contains("cao") -> "Cao"
            priorityRaw.contains("low") || priorityRaw.contains("thấp") -> "Thấp"
            else -> "Vừa"
        }
        val remind = obj.optBoolean("remind", true)
        val done = obj.optBoolean("done", false)
        val subTasksList = mutableListOf<SubTaskItem>()
        val subtasksArray = obj.optJSONArray("subtasks")
        if (subtasksArray != null) {
            for (i in 0 until subtasksArray.length()) {
                val subObj = subtasksArray.optJSONObject(i)
                if (subObj != null) {
                    subTasksList.add(
                        SubTaskItem(
                            id = "${id}_sub_$i",
                            title = subObj.optString("title", "Việc nhỏ"),
                            isCompleted = subObj.optBoolean("done", false)
                        )
                    )
                }
            }
        }
        return TodoTask(
            id = id,
            title = title,
            category = category,
            time = time,
            date = date,
            note = note,
            priority = priority,
            reminder = remind,
            reminderTime = "Trước 15 phút",
            initialCompleted = done,
            subTasks = subTasksList
        )
    }

    private fun createConnection(endpoint: String, method: String): HttpURLConnection {
        val url = URL("$BASE_URL$endpoint")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
        } catch (_: Exception) {
            try {
                val methodField = HttpURLConnection::class.java.getDeclaredField("method")
                methodField.isAccessible = true
                methodField.set(conn, method)
            } catch (_: Exception) {}
        }
        conn.setRequestProperty("Authorization", "Bearer $TOKEN")
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        return conn
    }

    //Get
    fun fetchTasks(
        onSuccess: (List<TodoTask>) -> Unit,
        onError: ((String) -> Unit)? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val conn = createConnection("/tasks", "GET")
                val code = conn.responseCode
                if (code in 200..299) {
                    val stream = conn.inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = JSONArray(stream)
                    val result = mutableListOf<TodoTask>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        result.add(parseJsonToTask(obj))
                    }
                    Log.d(TAG, "Tải thành công ${result.size} công việc từ Mock API")
                    withContext(Dispatchers.Main) {
                        onSuccess(result)
                    }
                } else {
                    val err = "HTTP Error $code"
                    Log.e(TAG, err)
                    withContext(Dispatchers.Main) {
                        onError?.invoke(err)
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi fetchTasks: ${e.message}")
                withContext(Dispatchers.Main) {
                    onError?.invoke(e.message ?: "Network error")
                }
            }
        }
    }

    //Get (bấm vào công việc)
    fun fetchTaskDetail(
        taskId: String,
        onSuccess: (TodoTask) -> Unit,
        onError: ((String) -> Unit)? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val conn = createConnection("/tasks/$taskId", "GET")
                val code = conn.responseCode
                if (code in 200..299) {
                    val stream = conn.inputStream.bufferedReader().use { it.readText() }
                    val obj = JSONObject(stream)
                    val task = parseJsonToTask(obj)
                    Log.d(TAG, "Tải chi tiết task $taskId thành công")
                    withContext(Dispatchers.Main) {
                        onSuccess(task)
                    }
                } else {
                    val err = "HTTP Error $code"
                    Log.e(TAG, err)
                    withContext(Dispatchers.Main) {
                        onError?.invoke(err)
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi fetchTaskDetail: ${e.message}")
                withContext(Dispatchers.Main) {
                    onError?.invoke(e.message ?: "Network error")
                }
            }
        }
    }

    //Patch(done)
    fun patchTaskStatus(
        taskId: String,
        done: Boolean,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val conn = createConnection("/tasks/$taskId", "PATCH")
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.doOutput = true
                val body = JSONObject().apply { put("done", done) }.toString()
                conn.outputStream.bufferedWriter().use { it.write(body) }
                val code = conn.responseCode
                if (code in 200..299) {
                    Log.d(TAG, "PATCH status task $taskId thành công: done=$done")
                    withContext(Dispatchers.Main) {
                        onSuccess?.invoke()
                    }
                } else {
                    val err = "HTTP Error $code"
                    Log.e(TAG, "PATCH status task $taskId thất bại: $err")
                    withContext(Dispatchers.Main) {
                        onError?.invoke(err)
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi patchTaskStatus: ${e.message}")
                withContext(Dispatchers.Main) {
                    onError?.invoke(e.message ?: "Network error")
                }
            }
        }
    }

    //Patch(ghi chú)
    fun patchTaskFull(
        taskId: String,
        task: TodoTask,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val conn = createConnection("/tasks/$taskId", "PATCH")
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.doOutput = true
                val subArray = JSONArray()
                for ((_, st) in task.subTasks.withIndex()) {
                    subArray.put(JSONObject().apply {
                        put("title", st.title)
                        put("done", st.isCompleted)
                    })
                }
                val body = JSONObject().apply {
                    put("title", task.title)
                    put("note", task.note)
                    put("done", task.isCompleted)
                    put("subtasks", subArray)
                }.toString()
                conn.outputStream.bufferedWriter().use { it.write(body) }
                val code = conn.responseCode
                if (code in 200..299) {
                    Log.d(TAG, "PATCH full task $taskId thành công")
                    withContext(Dispatchers.Main) {
                        onSuccess?.invoke()
                    }
                } else {
                    val err = "HTTP Error $code"
                    Log.e(TAG, "PATCH full task $taskId thất bại: $err")
                    withContext(Dispatchers.Main) {
                        onError?.invoke(err)
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi patchTaskFull: ${e.message}")
                withContext(Dispatchers.Main) {
                    onError?.invoke(e.message ?: "Network error")
                }
            }
        }
    }

    //Delete
    fun deleteTask(
        taskId: String,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val conn = createConnection("/tasks/$taskId", "DELETE")
                val code = conn.responseCode
                if (code in 200..299) {
                    Log.d(TAG, "DELETE task $taskId thành công")
                    withContext(Dispatchers.Main) {
                        onSuccess?.invoke()
                    }
                } else {
                    val err = "HTTP Error $code"
                    Log.e(TAG, "DELETE task $taskId thất bại: $err")
                    withContext(Dispatchers.Main) {
                        onError?.invoke(err)
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi deleteTask: ${e.message}")
                withContext(Dispatchers.Main) {
                    onError?.invoke(e.message ?: "Network error")
                }
            }
        }
    }

    //Post
    fun createTask(
        task: TodoTask,
        onSuccess: ((TodoTask) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val conn = createConnection("/tasks", "POST")
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.doOutput = true
                val body = JSONObject().apply {
                    put("title", task.title)
                    put("category", if (task.category.tagName == "Cuộc sống") "life" else "study")
                    put("date", task.date)
                    put("time", task.time)
                    put("note", task.note)
                    put("priority", when (task.priority) {
                        "Cao" -> "high"
                        "Thấp" -> "low"
                        else -> "medium"
                    })
                    put("remind", task.reminder)
                    put("done", task.isCompleted)
                }.toString()
                conn.outputStream.bufferedWriter().use { it.write(body) }
                val code = conn.responseCode
                if (code in 200..299) {
                    val stream = conn.inputStream.bufferedReader().use { it.readText() }
                    val obj = JSONObject(stream)
                    val created = parseJsonToTask(obj)
                    Log.d(TAG, "POST task thành công: id=${created.id}")
                    withContext(Dispatchers.Main) {
                        onSuccess?.invoke(created)
                    }
                } else {
                    val err = "HTTP Error $code"
                    Log.e(TAG, "POST task thất bại: $err")
                    withContext(Dispatchers.Main) {
                        onError?.invoke(err)
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi createTask: ${e.message}")
                withContext(Dispatchers.Main) {
                    onError?.invoke(e.message ?: "Network error")
                }
            }
        }
    }
}

//Authentication, Realtime Database, Remote Config, FCM, Analytics, Crashlytics
object FirebaseServiceHelper {
    private const val RTDB_URL = "https://fir-login-app-25e44-default-rtdb.asia-southeast1.firebasedatabase.app"
    private fun getDb(): FirebaseDatabase = FirebaseDatabase.getInstance(RTDB_URL)

    // Xác thực
    fun getCurrentUserId(): String {
        val authUser = FirebaseAuth.getInstance().currentUser
        return when {
            authUser != null && authUser.uid.isNotBlank() -> authUser.uid
            else -> "guest_user"
        }
    }
    private var currentTasksListener: ValueEventListener? = null
    private var currentTasksRef: DatabaseReference? = null

    // Cơ sở dữ liệu thời gian thực
    fun stopListeningToTasks() {
        currentTasksListener?.let { listener ->
            currentTasksRef?.removeEventListener(listener)
        }
        currentTasksListener = null
        currentTasksRef = null
    }

    fun listenToTasks(userId: String = getCurrentUserId(), onTasksLoaded: (List<TodoTask>) -> Unit) {
        stopListeningToTasks()
        try {
            val userRef = getDb().getReference("users").child(userId)
            val tasksRef = userRef.child("tasks")
            currentTasksRef = tasksRef
            userRef.child("initialized").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(initSnapshot: DataSnapshot) {
                    val isInitialized = initSnapshot.getValue(Boolean::class.java) == true
                    if (!isInitialized) {
                        userRef.child("initialized").setValue(true)
                        val sample1 = FirebaseTaskModel(
                            id = "sample_1",
                            title = "Chào mừng bạn đến với UTH Todo! 🎉",
                            categoryName = "Học tập",
                            time = "09:00",
                            date = "Hôm nay",
                            note = "Đây là công việc mẫu được lưu trực tiếp trên Firebase Realtime Database. Bạn có thể nhấn để hoàn thành hoặc vuốt để chỉnh sửa.",
                            priority = "Cao",
                            reminder = true,
                            reminderTime = "Trước 15 phút",
                            completed = false
                        )
                        val sample2 = FirebaseTaskModel(
                            id = "sample_2",
                            title = "Khám phá các tính năng của ứng dụng",
                            categoryName = "Cuộc sống",
                            time = "14:30",
                            date = "Hôm nay",
                            note = "Thử nhấn nút (+) ở góc dưới màn hình để tạo thêm công việc mới của riêng bạn.",
                            priority = "Vừa",
                            reminder = false,
                            reminderTime = "Trước 15 phút",
                            completed = false
                        )
                        tasksRef.child(sample1.id).setValue(sample1)
                        tasksRef.child(sample2.id).setValue(sample2)
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("FirebaseDB", "Lỗi kiểm tra initialized: ${error.message}")
                }
            })
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val loadedList = mutableListOf<TodoTask>()
                    if (snapshot.exists()) {
                        for (child in snapshot.children) {
                            try {
                                if (child.hasChildren()) {
                                    val model = child.getValue(FirebaseTaskModel::class.java)
                                    if (model != null) {
                                        val fallbackId = child.key ?: java.util.UUID.randomUUID().toString()
                                        val resolvedId = if (model.id.isNotBlank()) model.id else fallbackId
                                        val resolvedTitle = if (model.title.isNotBlank()) model.title else "Công việc"
                                        loadedList.add(
                                            TodoTask(
                                                id = resolvedId,
                                                title = resolvedTitle,
                                                category = appData.getCategoryByName(model.categoryName),
                                                time = model.time,
                                                date = model.date,
                                                note = model.note,
                                                priority = model.priority,
                                                reminder = model.reminder,
                                                reminderTime = model.reminderTime,
                                                initialCompleted = model.completed,
                                                subTasks = model.subTasks
                                            )
                                        )
                                    }
                                } else {
                                    // Child không phải object task (ví dụ node "completed: true" bị tạo nhầm trước đây) -> Tự động dọn dẹp
                                    Log.w("FirebaseDB", "Dọn dẹp node rác không hợp lệ: key=${child.key}, value=${child.value}")
                                    child.ref.removeValue()
                                }
                            } catch (e: Exception) {
                                Log.e("FirebaseDB", "Bỏ qua child lỗi ${child.key}: ${e.message}")
                            }
                        }
                    }
                    onTasksLoaded(loadedList)
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("FirebaseDB", "Lỗi đọc dữ liệu người dùng: ${error.message}")
                }
            }
            currentTasksListener = listener
            tasksRef.addValueEventListener(listener)
        } catch (e: Exception) {
            Log.e("FirebaseDB", "Chưa cấu hình Realtime Database: ${e.message}")
        }
    }

    fun toggleTaskStatus(context: Context, task: TodoTask, explicitStatus: Boolean? = null) {
        val newStatus = explicitStatus ?: !task.isCompleted
        task.isCompleted = newStatus
        val validTaskId = task.id.ifBlank { System.currentTimeMillis().toString() }
        try {
            val userId = getCurrentUserId()
            val updates = hashMapOf<String, Any>(
                "id" to validTaskId,
                "title" to task.title,
                "categoryName" to task.category.tagName,
                "time" to task.time,
                "date" to task.date,
                "note" to task.note,
                "priority" to task.priority,
                "reminder" to task.reminder,
                "reminderTime" to task.reminderTime,
                "completed" to newStatus
            )
            getDb().getReference("users")
                .child(userId)
                .child("tasks")
                .child(validTaskId)
                .updateChildren(updates)
            logEvent(context, "toggle_task", "task_title", task.title)

            if (newStatus) {
                TaskAlarmScheduler.cancelTaskReminder(context, validTaskId)
            } else if (task.reminder) {
                TaskAlarmScheduler.scheduleTaskReminder(
                    context = context,
                    taskId = validTaskId,
                    title = task.title,
                    categoryName = task.category.tagName,
                    date = task.date,
                    time = task.time,
                    reminder = task.reminder,
                    reminderTime = task.reminderTime,
                    isCompleted = false
                )
            }
        } catch (e: Exception) {
            Log.e("FirebaseDB", "Lỗi cập nhật task: ${e.message}")
        }
    }
 
    fun addNewTask(
        context: Context,
        title: String,
        categoryName: String,
        time: String,
        date: String = "Hôm nay",
        note: String = "",
        priority: String = "Vừa",
        reminder: Boolean = false,
        reminderTime: String = "Trước 15 phút"
    ) {
        val newId = System.currentTimeMillis().toString()
        val userId = getCurrentUserId()
        try {
            val model = FirebaseTaskModel(
                id = newId,
                title = title,
                categoryName = categoryName,
                time = time,
                date = date,
                note = note,
                priority = priority,
                reminder = reminder,
                reminderTime = reminderTime,
                completed = false
            )
            getDb().getReference("users")
                .child(userId)
                .child("tasks")
                .child(newId)
                .setValue(model)
            logEvent(context, "add_new_task", "task_title", title)

            if (reminder) {
                TaskAlarmScheduler.scheduleTaskReminder(
                    context = context,
                    taskId = newId,
                    title = title,
                    categoryName = categoryName,
                    date = date,
                    time = time,
                    reminder = true,
                    reminderTime = reminderTime,
                    isCompleted = false
                )
            }
        } catch (e: Exception) {
            Log.e("FirebaseDB", "Lỗi thêm task: ${e.message}")
        }
    }

    fun updateTask(context: Context, task: TodoTask) {
        try {
            val userId = getCurrentUserId()
            val model = FirebaseTaskModel(
                id = task.id,
                title = task.title,
                categoryName = task.category.tagName,
                time = task.time,
                date = task.date,
                note = task.note,
                priority = task.priority,
                reminder = task.reminder,
                reminderTime = task.reminderTime,
                completed = task.isCompleted,
                subTasks = task.subTasks.toList()
            )
            getDb().getReference("users")
                .child(userId)
                .child("tasks")
                .child(task.id)
                .setValue(model)
            logEvent(context, "update_task", "task_id", task.id)

            if (task.reminder && !task.isCompleted) {
                TaskAlarmScheduler.scheduleTaskReminder(
                    context = context,
                    taskId = task.id,
                    title = task.title,
                    categoryName = task.category.tagName,
                    date = task.date,
                    time = task.time,
                    reminder = task.reminder,
                    reminderTime = task.reminderTime,
                    isCompleted = task.isCompleted
                )
            } else {
                TaskAlarmScheduler.cancelTaskReminder(context, task.id)
            }
        } catch (e: Exception) {
            Log.e("FirebaseDB", "Lỗi cập nhật task: ${e.message}")
        }
    }

    fun deleteTask(context: Context, taskId: String) {
        if (taskId.isBlank()) return
        try {
            val userId = getCurrentUserId()
            getDb().getReference("users")
                .child(userId)
                .child("tasks")
                .child(taskId)
                .removeValue()
            logEvent(context, "delete_task", "task_id", taskId)

            TaskAlarmScheduler.cancelTaskReminder(context, taskId)
        } catch (e: Exception) {
            Log.e("FirebaseDB", "Lỗi xóa task: ${e.message}")
        }
    }

    // Cấu hình từ xa Remote Config
    fun fetchRemoteConfig(onConfigFetched: (encourageText: String, sectionTitle: String) -> Unit) {
        try {
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            val configSettings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(0)
                .build()
            remoteConfig.setConfigSettingsAsync(configSettings)
            val defaults = mapOf(
                "progress_encourage" to appData.homeData.progressEncourage,
                "today_section_title" to appData.homeData.todaySectionTitle
            )
            remoteConfig.setDefaultsAsync(defaults)
            remoteConfig.fetchAndActivate().addOnCompleteListener {
                val encourage = remoteConfig.getString("progress_encourage").ifBlank {
                    appData.homeData.progressEncourage
                }
                val sectionTitle = remoteConfig.getString("today_section_title").ifBlank {
                    appData.homeData.todaySectionTitle
                }
                onConfigFetched(encourage, sectionTitle)
            }
        } catch (e: Exception) {
            Log.e("RemoteConfig", "Lỗi Remote Config: ${e.message}")
        }
    }

    private var userNotifListener: ValueEventListener? = null
    private var userNotifRef: DatabaseReference? = null
    private var globalNotifListener: ValueEventListener? = null
    private var globalNotifRef: Query? = null

    // Cơ sở dữ liệu thời gian thực
    fun stopListeningToNotifications() {
        userNotifListener?.let { userNotifRef?.removeEventListener(it) }
        userNotifListener = null
        userNotifRef = null

        globalNotifListener?.let { globalNotifRef?.removeEventListener(it) }
        globalNotifListener = null
        globalNotifRef = null
    }

    fun saveNotificationToFirebase(userId: String = getCurrentUserId(), item: AppNotification) {
        try {
            getDb().getReference("users").child(userId).child("notifications").child(item.id).setValue(item)
        } catch (e: Exception) {
            Log.e("FirebaseDB", "Lỗi lưu notification vào Firebase: ${e.message}")
        }
    }

    fun markNotificationAsReadInFirebase(userId: String = getCurrentUserId(), notificationId: String) {
        try {
            val userRef = getDb().getReference("users").child(userId)
            userRef.child("notifications").child(notificationId).child("isRead").setValue(true)
            userRef.child("read_notifications").child(notificationId).setValue(true)
        } catch (e: Exception) {
            Log.e("FirebaseDB", "Lỗi cập nhật đã đọc trên Firebase: ${e.message}")
        }
    }

    fun markAllNotificationsAsReadInFirebase(userId: String = getCurrentUserId(), ids: List<String>) {
        try {
            val userRef = getDb().getReference("users").child(userId)
            val updates = mutableMapOf<String, Any>()
            for (id in ids) {
                updates["notifications/$id/isRead"] = true
                updates["read_notifications/$id"] = true
            }
            if (updates.isNotEmpty()) {
                userRef.updateChildren(updates)
            }
        } catch (e: Exception) {
            Log.e("FirebaseDB", "Lỗi đánh dấu đọc hết trên Firebase: ${e.message}")
        }
    }

    fun deleteNotificationInFirebase(userId: String = getCurrentUserId(), notificationId: String) {
        try {
            val userRef = getDb().getReference("users").child(userId)
            userRef.child("notifications").child(notificationId).removeValue()

            userRef.child("deleted_notifications").child(notificationId).setValue(true)
        } catch (e: Exception) {
            Log.e("FirebaseDB", "Lỗi xóa thông báo trên Firebase: ${e.message}")
        }
    }

    fun clearAllNotificationsInFirebase(userId: String = getCurrentUserId(), ids: List<String>) {
        try {
            val userRef = getDb().getReference("users").child(userId)
            userRef.child("notifications").removeValue()
           
            userRef.child("cleared_all_timestamp").setValue(System.currentTimeMillis())
           
            val updates = mutableMapOf<String, Any>()
            for (id in ids) {
                updates["deleted_notifications/$id"] = true
            }
            if (updates.isNotEmpty()) {
                userRef.updateChildren(updates)
            }
        } catch (e: Exception) {
            Log.e("FirebaseDB", "Lỗi xóa tất cả thông báo trên Firebase: ${e.message}")
        }
    }

    @Suppress("unused")
    fun listenToNotifications(context: Context? = null) {
        listenToNotificationsForUser(getCurrentUserId(), context)
    }

    fun listenToNotificationsForUser(userId: String = getCurrentUserId(), context: Context? = null) {
        stopListeningToNotifications()
        NotificationManagerHelper.switchUser(userId, context)

        try {
            val userRef = getDb().getReference("users").child(userId)
            val notifRef = userRef.child("notifications")
            userNotifRef = notifRef

            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(userSnap: DataSnapshot) {
                            val clearedAllTimestamp = userSnap.child("cleared_all_timestamp").getValue(Long::class.java) ?: 0L
                            val deletedIds = mutableSetOf<String>()
                            for (d in userSnap.child("deleted_notifications").children) {
                                if (d.getValue(Boolean::class.java) == true) {
                                    deletedIds.add(d.key ?: "")
                                }
                            }
                            val readIds = mutableSetOf<String>()
                            for (r in userSnap.child("read_notifications").children) {
                                if (r.getValue(Boolean::class.java) == true) {
                                    readIds.add(r.key ?: "")
                                }
                            }

                            val list = mutableListOf<AppNotification>()
                            if (snapshot.exists()) {
                                for (child in snapshot.children) {
                                    val id = child.key ?: ""
            
                                    if (deletedIds.contains(id)) continue

                                    val idTimestamp = id.toLongOrNull() ?: 0L
                                    if (clearedAllTimestamp > 0L && idTimestamp > 0L && idTimestamp < clearedAllTimestamp) continue

                                    val rawItem = child.getValue(AppNotification::class.java)
                                    if (rawItem != null && (rawItem.title.isNotBlank() || rawItem.message.isNotBlank())) {
                                        val isRead = readIds.contains(id) || rawItem.isRead
                                        list.add(rawItem.copy(id = id, isRead = isRead))
                                    }
                                }
                            }
                            list.sortByDescending { it.id.toLongOrNull() ?: 0L }
                            if (NotificationManagerHelper.notifications != list) {
                                NotificationManagerHelper.notifications.clear()
                                NotificationManagerHelper.notifications.addAll(list)
                                NotificationManagerHelper.saveToPrefs(context, userId)
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.e("FirebaseDB", "Lỗi đọc deleted/read notifications: ${error.message}")
                        }
                    })
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("FirebaseDB", "Lỗi đọc notifications của user: ${error.message}")
                }
            }
            userNotifListener = listener
            notifRef.addValueEventListener(listener)

            //thông báo chung notifications
            listenToGlobalServerNotifications(userId, context)
        } catch (e: Exception) {
            Log.e("FirebaseDB", "Lỗi listenToNotificationsForUser: ${e.message}")
        }
    }

    // Cơ sở dữ liệu thời gian thực
    private fun listenToGlobalServerNotifications(userId: String, context: Context? = null) {
        try {
            var isInitial = true
            val userRef = getDb().getReference("users").child(userId)
            val globalRef = getDb().getReference("notifications").limitToLast(30)
            globalNotifRef = globalRef

            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(userSnap: DataSnapshot) {
                                val clearedAllTimestamp = userSnap.child("cleared_all_timestamp").getValue(Long::class.java) ?: 0L
                                val deletedIds = mutableSetOf<String>()
                                for (d in userSnap.child("deleted_notifications").children) {
                                    if (d.getValue(Boolean::class.java) == true) {
                                        deletedIds.add(d.key ?: "")
                                    }
                                }
                                val readIds = mutableSetOf<String>()
                                for (r in userSnap.child("read_notifications").children) {
                                    if (r.getValue(Boolean::class.java) == true) {
                                        readIds.add(r.key ?: "")
                                    }
                                }

                                val updates = mutableMapOf<String, Any>()
                                val newItemsToShow = mutableListOf<AppNotification>()
                                val existingIds = NotificationManagerHelper.notifications.map { it.id }.toSet()

                                for (child in snapshot.children) {
                                    val id = child.key ?: ""
                                   
                                    if (deletedIds.contains(id)) continue

                                    val idTimestamp = id.toLongOrNull() ?: 0L
                                  
                                    if (clearedAllTimestamp > 0L && idTimestamp > 0L && idTimestamp < clearedAllTimestamp) continue

                                    val title = child.child("title").getValue(String::class.java)
                                        ?: child.child("subject").getValue(String::class.java)
                                        ?: "Thông báo từ máy chủ"
                                    val body = child.child("message").getValue(String::class.java)
                                        ?: child.child("body").getValue(String::class.java)
                                        ?: child.child("content").getValue(String::class.java)
                                        ?: child.child("text").getValue(String::class.java)
                                        ?: (child.value as? String ?: "")
                                    val time = child.child("time").getValue(String::class.java)
                                        ?: child.child("timestamp").getValue(String::class.java)
                                        ?: child.child("createdAt").getValue(String::class.java)
                                        ?: ""

                                    val isRead = readIds.contains(id)

                                    if ((title.isNotBlank() || body.isNotBlank()) && !existingIds.contains(id)) {
                                        val item = AppNotification(
                                            id = id,
                                            title = title.ifBlank { "Thông báo từ máy chủ" },
                                            message = body,
                                            time = time.ifBlank {
                                                SimpleDateFormat("HH:mm - dd/MM", Locale.getDefault()).format(Date())
                                            },
                                            isRead = isRead,
                                            type = "server_rtdb"
                                        )
                                        NotificationManagerHelper.notifications.add(0, item)
                                        updates["notifications/$id"] = item

                                        if (!isInitial && context != null && !isRead) {
                                            newItemsToShow.add(item)
                                        }
                                    } else if (isRead) {
                                        val existingIndex = NotificationManagerHelper.notifications.indexOfFirst { it.id == id }
                                        if (existingIndex != -1 && !NotificationManagerHelper.notifications[existingIndex].isRead) {
                                            NotificationManagerHelper.notifications[existingIndex] =
                                                NotificationManagerHelper.notifications[existingIndex].copy(isRead = true)
                                        }
                                    }
                                }

                                if (updates.isNotEmpty()) {
                                    NotificationManagerHelper.saveToPrefs(context, userId)
                                    userRef.updateChildren(updates)
                                }

                                for ((_, title, message) in newItemsToShow) {
                                    if (context != null) {
                                        NotificationManagerHelper.showSystemNotification(context, title, message)
                                    }
                                }
                            }

                            override fun onCancelled(error: DatabaseError) {
                                Log.e("FirebaseDB", "Lỗi đọc dữ liệu user: ${error.message}")
                            }
                        })
                    }
                    isInitial = false
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("FirebaseDB", "Lỗi listenToGlobalServerNotifications: ${error.message}")
                }
            }
            globalNotifListener = listener
            globalRef.addValueEventListener(listener)
        } catch (e: Exception) {
            Log.e("FirebaseDB", "Lỗi listenToGlobalServerNotifications: ${e.message}")
        }
    }

    // Cơ sở dữ liệu thời gian thực
    fun pushServerNotification(title: String, message: String, onComplete: ((Boolean) -> Unit)? = null) {
        try {
            val dbRef = getDb().getReference("notifications").push()
            val sdf = SimpleDateFormat("HH:mm - dd/MM", Locale.getDefault())
            val currentTime = sdf.format(Date())
            val data = mapOf(
                "title" to title,
                "message" to message,
                "time" to currentTime,
                "type" to "server_rtdb"
            )
            dbRef.setValue(data).addOnCompleteListener { task ->
                onComplete?.invoke(task.isSuccessful)
            }
        } catch (_: Exception) {
            onComplete?.invoke(false)
        }
    }

    // Thông báo đẩy cloud messaging (FCM)
    fun initCloudMessaging(onTokenReceived: (String) -> Unit) {
        try {
            FirebaseMessaging.getInstance().subscribeToTopic("uth_todo")
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result ?: "Không lấy được Token"
                    NotificationManagerHelper.currentFcmToken = token
                    onTokenReceived(token)
                } else {
                    onTokenReceived("Lỗi lấy FCM Token: ${task.exception?.message}")
                }
            }
        } catch (e: Exception) {
            onTokenReceived("Chưa khởi tạo FCM: ${e.message}")
        }
    }

    // Phân tích dữ liệu Analytics
    fun logEvent(context: Context, eventName: String, paramKey: String, paramValue: String) {
        try {
            val bundle = Bundle().apply { putString(paramKey, paramValue) }
            FirebaseAnalytics.getInstance(context).logEvent(eventName, bundle)
        } catch (e: Exception) {
            Log.e("Analytics", "Lỗi ghi Analytics: ${e.message}")
        }
    }

    // Báo cáo sự cố Crashlytics
    fun recordNonFatalError(context: Context) {
        try {
            FirebaseCrashlytics.getInstance().log("Người dùng bấm kiểm tra lỗi trên UTH Todo")
            FirebaseCrashlytics.getInstance().recordException(
                RuntimeException("Test Non-Fatal Error từ UTH Todo App")
            )
            Toast.makeText(context, "Đã gửi báo cáo lỗi lên Firebase Crashlytics!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Lỗi Crashlytics: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
@Composable
fun AppBottomNavigation(
    currentRoute: String,
    accentColor: Color,
    isDark: Boolean,
    onNavigate: (String) -> Unit,
    onOpenAdd: () -> Unit,
    onOpenNotifications: () -> Unit
) {
    val bgColor = if (isDark) Color(0xFF1E1E1E) else Color.White
    val borderColor = if (isDark) Color(0xFF2E2E2E) else Color(0xFFE5E7EB)
    val inactiveColor = if (isDark) Color(0xFF9CA3AF) else Color.Gray

    Column {
        HorizontalDivider(color = borderColor, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(bgColor),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isHome = currentRoute == "home"
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onNavigate("home") }
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Trang chủ",
                    tint = if (isHome) accentColor else inactiveColor
                )
                Text(
                    text = "Trang chủ",
                    fontSize = 10.sp,
                    color = if (isHome) accentColor else inactiveColor,
                    fontWeight = if (isHome) FontWeight.Bold else FontWeight.Normal
                )
            }

            val isList = currentRoute == "task_list"
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onNavigate("task_list") }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = "Danh sách",
                    tint = if (isList) accentColor else inactiveColor
                )
                Text(
                    text = "Danh sách",
                    fontSize = 10.sp,
                    color = if (isList) accentColor else inactiveColor,
                    fontWeight = if (isList) FontWeight.Bold else FontWeight.Normal
                )
            }

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(accentColor)
                    .clickable { onOpenAdd() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add,
                    contentDescription = "Add",
                    tint = Color.White)
            }

            val isNotifications = currentRoute == "notifications"
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onOpenNotifications() }
            ) {
                BadgedBox(
                    badge = {
                        val unread = NotificationManagerHelper.unreadCount()
                        if (unread > 0) {
                            Badge(containerColor = Color(0xFFEF4444)) {
                                Text("$unread",
                                    color = Color.White,
                                    fontSize = 9.sp)
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (isNotifications) Icons.Default.Notifications else Icons.Outlined.Notifications,
                        contentDescription = "Thông báo",
                        tint = if (isNotifications) accentColor else inactiveColor
                    )
                }
                Text(
                    text = "Thông báo",
                    fontSize = 10.sp,
                    color = if (isNotifications) accentColor else inactiveColor,
                    fontWeight = if (isNotifications) FontWeight.Bold else FontWeight.Normal
                )
            }

            val isSettings = currentRoute == "settings"
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onNavigate("settings") }
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Cài đặt",
                    tint = if (isSettings) accentColor else inactiveColor
                )
                Text(
                    text = "Cài đặt",
                    fontSize = 10.sp,
                    color = if (isSettings) accentColor else inactiveColor,
                    fontWeight = if (isSettings) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun TodoApp() {
    val context = LocalContext.current
    val navControl = rememberNavController()
    val startScreen = appData.startData
    val pages = appData.onboardingList
    var currentUser by remember {
        val authUser = FirebaseAuth.getInstance().currentUser
        mutableStateOf(if (authUser != null) buildUserProfile(authUser) else appData.guestUserProfile)
    }

    var remoteEncourageText by remember { mutableStateOf(appData.homeData.progressEncourage) }
    var remoteTodayTitle by remember { mutableStateOf(appData.homeData.todaySectionTitle) }

    // Khởi tạo dịch vụ và tải dữ liệu từ Mock API khi mở ứng dụng
    LaunchedEffect(Unit) {
        // Tuần 04: Gọi GET /mock/teach/tasks để tải mọi việc khi mở màn hình
        MockTeachApiService.fetchTasks(
            onSuccess = { apiTasks ->
                if (apiTasks.isNotEmpty()) {
                    appData.tasks.clear()
                    appData.tasks.addAll(apiTasks)
                    for (task in apiTasks) {
                        if (task.reminder && !task.isCompleted) {
                            TaskAlarmScheduler.scheduleTaskReminder(
                                context = context,
                                taskId = task.id,
                                title = task.title,
                                categoryName = task.category.tagName,
                                date = task.date,
                                time = task.time,
                                reminder = task.reminder,
                                reminderTime = task.reminderTime,
                                isCompleted = task.isCompleted
                            )
                        }
                    }
                }
            },
            onError = { err ->
                Log.e("TodoApp", "Lỗi tải Mock API: $err")
            }
        )

        val authUser = FirebaseAuth.getInstance().currentUser
        val uid = authUser?.uid ?: FirebaseServiceHelper.getCurrentUserId()
        if (authUser != null) {
            currentUser = buildUserProfile(authUser)
            FirebaseServiceHelper.listenToTasks(authUser.uid) { loadedTasks ->
                if (loadedTasks.isNotEmpty()) {
                    for (firebaseTask in loadedTasks) {
                        val existing = appData.tasks.find { it.id == firebaseTask.id }
                        if (existing != null) {
                            existing.title = firebaseTask.title
                            existing.category = firebaseTask.category
                            existing.time = firebaseTask.time
                            existing.date = firebaseTask.date
                            existing.note = firebaseTask.note
                            existing.priority = firebaseTask.priority
                            existing.reminder = firebaseTask.reminder
                            existing.reminderTime = firebaseTask.reminderTime
                            existing.isCompleted = firebaseTask.isCompleted
                        } else {
                            appData.tasks.add(firebaseTask)
                        }
                    }
                    for (task in loadedTasks) {
                        if (task.reminder && !task.isCompleted) {
                            TaskAlarmScheduler.scheduleTaskReminder(
                                context = context,
                                taskId = task.id,
                                title = task.title,
                                categoryName = task.category.tagName,
                                date = task.date,
                                time = task.time,
                                reminder = task.reminder,
                                reminderTime = task.reminderTime,
                                isCompleted = task.isCompleted
                            )
                        } else {
                            TaskAlarmScheduler.cancelTaskReminder(context, task.id)
                        }
                    }
                }
            }
        }
        FirebaseServiceHelper.listenToNotificationsForUser(uid, context)
        FirebaseServiceHelper.fetchRemoteConfig { encourage, sectionTitle ->
            remoteEncourageText = encourage
            remoteTodayTitle = sectionTitle
        }
        FirebaseServiceHelper.initCloudMessaging { }
    }

    LaunchedEffect(NotificationManagerHelper.pendingRoute) {
        val route = NotificationManagerHelper.pendingRoute
        if (route != null) {
            navControl.navigate(route)
            NotificationManagerHelper.pendingRoute = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navControl, startDestination = startScreen.route) {
        composable(startScreen.route) {
            StartScreen(
                data = startScreen,
                onNavigate = { route -> navControl.navigate(route) }
            )
        }
        composable(pages[0].route) {
            OnboardingScreen(
                data = pages[0],
                totalSteps = pages.size,
                onNavigate = { route -> navControl.navigate(route) }
            )
        }
        composable(pages[1].route) {
            OnboardingScreen(
                data = pages[1],
                totalSteps = pages.size,
                onNavigate = { route -> navControl.navigate(route) }
            )
        }
        composable(pages[2].route) {
            OnboardingScreen(
                data = pages[2],
                totalSteps = pages.size,
                onNavigate = { route -> navControl.navigate(route) }
            )
        }
        // Xác thực
        composable(appData.loginData.route) {
            LoginScreen(
                data = appData.loginData,
                onLoginSuccess = { profile ->
                    currentUser = profile
                    val uid = FirebaseServiceHelper.getCurrentUserId()
                    FirebaseServiceHelper.listenToTasks(uid) { loadedTasks ->
                        for (firebaseTask in loadedTasks) {
                            val existing = appData.tasks.find { it.id == firebaseTask.id }
                            if (existing != null) {
                                existing.title = firebaseTask.title
                                existing.category = firebaseTask.category
                                existing.time = firebaseTask.time
                                existing.date = firebaseTask.date
                                existing.note = firebaseTask.note
                                existing.priority = firebaseTask.priority
                                existing.reminder = firebaseTask.reminder
                                existing.reminderTime = firebaseTask.reminderTime
                                existing.isCompleted = firebaseTask.isCompleted
                            } else {
                                appData.tasks.add(firebaseTask)
                            }
                        }
                        for (task in loadedTasks) {
                            if (task.reminder && !task.isCompleted) {
                                TaskAlarmScheduler.scheduleTaskReminder(
                                    context = context,
                                    taskId = task.id,
                                    title = task.title,
                                    categoryName = task.category.tagName,
                                    date = task.date,
                                    time = task.time,
                                    reminder = task.reminder,
                                    reminderTime = task.reminderTime,
                                    isCompleted = task.isCompleted
                                )
                            } else {
                                TaskAlarmScheduler.cancelTaskReminder(context, task.id)
                            }
                        }
                    }
                    FirebaseServiceHelper.listenToNotificationsForUser(uid, context)
                    FirebaseServiceHelper.logEvent(context, "login_success", "user_email", profile.email)
                    navControl.navigate(appData.homeData.route) {
                        popUpTo(appData.loginData.route) { inclusive = true }
                    }
                }
            )
        }
        composable(appData.homeData.route) {
            HomeScreen(
                data = appData.homeData.copy(
                    progressEncourage = remoteEncourageText,
                    todaySectionTitle = remoteTodayTitle
                ),
                user = currentUser,
                tasks = appData.tasks,
                studyCategory = appData.studyCategory,
                lifeCategory = appData.lifeCategory,
                onRefreshRemoteConfig = {
                    FirebaseServiceHelper.fetchRemoteConfig { encourage, sectionTitle ->
                        remoteEncourageText = encourage
                        remoteTodayTitle = sectionTitle
                        Toast.makeText(context, "Đã đồng bộ Remote Config!", Toast.LENGTH_SHORT).show()
                    }
                },
                onNavigate = { route -> navControl.navigate(route) },
                onOpenNotifications = { navControl.navigate("notifications") }
            )
        }
        composable("task_list") {
            TaskListScreen(
                tasks = appData.tasks,
                studyCategory = appData.studyCategory,
                lifeCategory = appData.lifeCategory,
                onNavigate = { route -> navControl.navigate(route) },
                onOpenNotifications = { navControl.navigate("notifications") }
            )
        }
        composable("add_task") {
            AddTaskScreen(
                studyCategory = appData.studyCategory,
                lifeCategory = appData.lifeCategory,
                onCancel = { navControl.popBackStack() },
                onTaskCreated = {
                    navControl.popBackStack()
                }
            )
        }
        composable(
            route = "task_detail/{taskId}",
            arguments = listOf(navArgument("taskId") { type = NavType.StringType })
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getString("taskId") ?: ""
            val task = appData.tasks.find { it.id == taskId }
            if (task != null) {
                TaskDetailScreen(
                    task = task,
                    onBack = { navControl.popBackStack() },
                    onTaskDeleted = { navControl.popBackStack() }
                )
            } else {
                LaunchedEffect(Unit) { navControl.popBackStack() }
            }
        }
        composable("notifications") {
            NotificationsScreen(
                onNavigate = { route -> navControl.navigate(route) },
                onBack = { navControl.popBackStack() }
            )
        }
        composable("settings") {
            SettingsScreen(
                user = currentUser,
                onNavigate = { route -> navControl.navigate(route) },
                onOpenNotifications = { navControl.navigate("notifications") }
            )
        }
        composable("profile") {
            ProfileScreen(
                user = currentUser,
                tasks = appData.tasks,
                onBack = { navControl.popBackStack() },
                onLogout = {
                    // Xác thực
                    FirebaseAuth.getInstance().signOut()
                    FirebaseServiceHelper.stopListeningToTasks()
                    FirebaseServiceHelper.stopListeningToNotifications()
                    NotificationManagerHelper.clearMemory()
                    appData.tasks.clear()
                    currentUser = appData.guestUserProfile
                    navControl.navigate(appData.loginData.route) {
                        popUpTo(appData.homeData.route) { inclusive = true }
                    }
                }
            )
        }
    }

    NotificationManagerHelper.latestInAppReminder?.let { reminderItem ->
        val isDark = when (globalSettings.themeMode) {
            "dark" -> true
            "light" -> false
            else -> isSystemInDarkTheme()
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .statusBarsPadding()
                .clickable {
                    NotificationManagerHelper.latestInAppReminder = null
                    navControl.navigate("notifications")
                },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF1E1E2E) else Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            border = BorderStroke(1.5.dp, Color(0xFF8B5CF6))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF8B5CF6).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = Color(0xFF8B5CF6),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = reminderItem.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color(0xFFF3F4F6) else Color(0xFF111827)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val bannerMsg = if (reminderItem.deadlineMs > 0L) {
                        val diffMs = reminderItem.deadlineMs - System.currentTimeMillis()
                        val diffMinutes = if (diffMs > 0) ((diffMs + 59999) / 60000).toInt() else 0
                        val remainingText = if (diffMinutes > 0) "còn $diffMinutes phút" else "đến hạn ngay bây giờ"
                        val timeInfo = if (reminderItem.taskTime.isNotBlank()) " lúc ${reminderItem.taskTime}" else ""
                        "Hạn chót$timeInfo ($remainingText). Nhấn để xem!"
                    } else {
                        reminderItem.message
                    }
                    Text(
                        text = bannerMsg,
                        fontSize = 12.sp,
                        color = if (isDark) Color(0xFF9CA3AF) else Color.Gray,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { NotificationManagerHelper.latestInAppReminder = null }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Đóng",
                        tint = if (isDark) Color(0xFF9CA3AF) else Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
}

@Composable
fun StartScreen(
    data: StartScreenData,
    onNavigate: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF254EDB))
            .clickable { onNavigate(data.nextRoute) }
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Image(
            painter = painterResource(id = data.logoRes),
            contentDescription = "App Logo",
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(22.dp))
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = data.appName,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = data.slogan,
            fontSize = 14.sp,
            color = Color(0xFFDCE6FF),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier
                .width(64.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color.White)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFF5C7CFA))
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = data.version,
            fontSize = 11.sp,
            color = Color(0xFFB8C9FF)
        )
    }
}

@Composable
fun OnboardingScreen(
    data: Onboarding,
    totalSteps: Int,
    onNavigate: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = data.logoRes),
                    contentDescription = "Small Logo",
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = data.appName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF254EDB)
                )
            }
            Text(
                text = data.skipText,
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.clickable { onNavigate(appData.loginData.route) }
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFFEEF2FF))
        ) {
            if (data.checklistStates.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
                ) {
                    for (isChecked in data.checklistStates) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isChecked) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF254EDB)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .border(1.5.dp, Color(0xFFBDBDBD), CircleShape)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFE5E7EB))
                            )
                        }
                    }
                }
            } else if (data.categories.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
                ) {
                    for ((tagName, textColor, bgColor) in data.categories) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .padding(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(bgColor)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = tagName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Notifications,
                            contentDescription = null,
                            tint = Color(0xFF254EDB),
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White)
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = data.reminderText.ifBlank { "Ôn bài Toán lúc 19:00" },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1F2937)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = data.title,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF111827),
            lineHeight = 30.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = data.description,
            fontSize = 14.sp,
            color = Color(0xFF6B7280),
            lineHeight = 22.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (index in 0 until totalSteps) {
                val isSelected = index == data.stepIndex
                Box(
                    modifier = Modifier
                        .width(if (isSelected) 20.dp else 6.dp)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) Color(0xFF254EDB) else Color(0xFFD1D5DB))
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = { onNavigate(data.nextRoute) },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF254EDB))
        ) {
            Text(
                text = data.buttonText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
// Xác thực
@Composable
fun LoginScreen(
    data: LoginScreenData,
    onLoginSuccess: (UserProfile) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var showPhoneDialog by remember { mutableStateOf(false) }
    var showOtpDialog by remember { mutableStateOf(false) }
    var phoneNumber by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var storedVerificationId by remember { mutableStateOf("") }
    
    val webClientId = stringResource(id = R.string.default_web_client_id)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Image(
            painter = painterResource(id = data.logoRes),
            contentDescription = "Logo",
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = data.title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF111827),
            lineHeight = 32.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = data.description,
            fontSize = 13.sp,
            color = Color(0xFF6B7280),
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(28.dp))
        OutlinedTextField(
            value = username,
            onValueChange = {
                username = it
                errorMessage = ""
            },
            placeholder = { Text(data.usernamePlaceholder,
                fontSize = 14.sp,
                color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Person,
                contentDescription = null,
                tint = Color.Gray) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                errorMessage = ""
            },
            placeholder = { Text(data.passwordPlaceholder,
                fontSize = 14.sp,
                color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Lock,
                contentDescription = null,
                tint = Color.Gray) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        if (errorMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                color = Color(0xFFD32F2F),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        // Xác thực đăng nhập bằng email và mật khẩu 
        Button(
            onClick = {
                if (username.isBlank() || password.isBlank()) {
                    errorMessage = "Đăng nhập lỗi: Vui lòng nhập đầy đủ email và mật khẩu!"
                    Toast.makeText(context, "Đăng nhập thất bại!", Toast.LENGTH_SHORT).show()
                } else {
                    FirebaseAuth.getInstance().signInWithEmailAndPassword(username, password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Toast.makeText(context, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                                val user = FirebaseAuth.getInstance().currentUser
                                onLoginSuccess(buildUserProfile(user))
                            } else {
                                errorMessage = "Đăng nhập thất bại: ${task.exception?.message}"
                            }
                        }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF254EDB))
        ) {
            Text(data.loginButtonText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White)
        }
        Spacer(modifier = Modifier.height(24.dp))
        // Xác thực đăng nhập bằng gg
        OutlinedButton(
            onClick = {
                val credentialManager = CredentialManager.create(context)
                val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(webClientId)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(signInWithGoogleOption)
                    .build()

                coroutineScope.launch {
                    try {
                        var activityContext: Context = context
                        while (activityContext is ContextWrapper) {
                            if (activityContext is Activity) break
                            activityContext = activityContext.baseContext
                        }
                        val activity = activityContext as? Activity

                        val result = credentialManager.getCredential(
                            request = request,
                            context = activity ?: context,
                        )
                        val credential = result.credential

                        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                            FirebaseAuth.getInstance().signInWithCredential(firebaseCredential)
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        val user = FirebaseAuth.getInstance().currentUser
                                        Toast.makeText(context, "Đăng nhập Google thành công!", Toast.LENGTH_SHORT).show()
                                        onLoginSuccess(buildUserProfile(user))
                                    } else {
                                        val errorMsg = task.exception?.localizedMessage ?: task.exception?.message ?: "Lỗi xác thực"
                                        Log.e("GoogleSignIn", "Lỗi Firebase Auth: $errorMsg", task.exception)
                                        Toast.makeText(context, "Đăng nhập Firebase thất bại: $errorMsg", Toast.LENGTH_LONG).show()
                                    }
                                }
                        } else {
                            Log.e("GoogleSignIn", "Loại credential không được hỗ trợ: ${credential::class.java.name}")
                        }
                    } catch (e: GetCredentialCancellationException) {
                        Log.d("GoogleSignIn", "Người dùng đóng hoặc bị hủy: ${e.message}")
                        Toast.makeText(context, "Đăng nhập bị hủy hoặc chưa hoàn tất cấp quyền Google.", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e("GoogleSignIn", "Đăng nhập Google thất bại", e)
                        Toast.makeText(context, "Đăng nhập Google thất bại: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF3F4F6)),
                contentAlignment = Alignment.Center
            ) {
                Text("G", fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    color = Color(0xFF254EDB))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = data.googleButtonText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1F2937)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        // Xác thực đăng nhập sdt
        OutlinedButton(
            onClick = {
                showPhoneDialog = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF28A745)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Phone,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Tiếp tục với Số điện thoại",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1F2937)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "hoặc",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = data.guestButtonText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF254EDB),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    FirebaseAuth.getInstance().signInAnonymously()
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Toast.makeText(context, "Đang dùng thử với tư cách Khách!", Toast.LENGTH_SHORT).show()
                                val user = FirebaseAuth.getInstance().currentUser
                                onLoginSuccess(buildUserProfile(user))
                            } else {
                                Toast.makeText(context, "Lỗi đăng nhập khách: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                }
                .padding(vertical = 6.dp)
        )
        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = data.termsText,
            fontSize = 11.sp,
            color = Color(0xFF9CA3AF),
            textAlign = TextAlign.Center,
            lineHeight = 16.sp,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (showPhoneDialog) {
        AlertDialog(
            onDismissRequest = { showPhoneDialog = false },
            title = { Text("Đăng nhập bằng Số điện thoại") },
            text = {
                Column {
                    Text("Nhập số điện thoại của bạn:", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone
                        ),
                        placeholder = { Text("VD:0987654321") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                // Xác thực OTP sdt
                Button(
                    onClick = {
                        var activityContext: Context = context
                        while (activityContext is ContextWrapper) {
                            if (activityContext is Activity) break
                            activityContext = activityContext.baseContext
                        }
                        val activity = activityContext as? Activity

                        val formattedNumber = phoneNumber.trim().let {
                            when {
                                it.startsWith("+") -> it
                                it.startsWith("0") -> "+84${it.drop(1)}"
                                it.startsWith("84") -> "+$it"
                                else -> "+84$it"
                            }
                        }

                        if (activity != null && formattedNumber.isNotBlank()) {
                            val options = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
                                .setPhoneNumber(formattedNumber)
                                .setTimeout(60L, TimeUnit.SECONDS)
                                .setActivity(activity)
                                .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                                    override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                                        FirebaseAuth.getInstance().signInWithCredential(credential)
                                            .addOnCompleteListener { task ->
                                                if (task.isSuccessful) {
                                                    val user = FirebaseAuth.getInstance().currentUser
                                                    showPhoneDialog = false
                                                    showOtpDialog = false
                                                    Toast.makeText(context, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                                                    onLoginSuccess(buildUserProfile(user))
                                                }
                                            }
                                    }
                                    override fun onVerificationFailed(e: FirebaseException) {
                                        Log.e("PhoneAuth", "Lỗi xác thực SĐT", e)
                                        Toast.makeText(context, "Lỗi xác thực SĐT: ${e.localizedMessage ?: e.message}", Toast.LENGTH_LONG).show()
                                    }
                                    override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                                        storedVerificationId = verificationId
                                        showPhoneDialog = false
                                        showOtpDialog = true
                                        Toast.makeText(context, "Đã gửi mã xác nhận!", Toast.LENGTH_SHORT).show()
                                    }
                                }).build()
                            PhoneAuthProvider.verifyPhoneNumber(options)
                        } else {
                            Toast.makeText(context, "Vui lòng nhập số điện thoại hợp lệ", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) { Text("Gửi mã") }
            },
            dismissButton = {
                TextButton(onClick = { showPhoneDialog = false }) { Text("Hủy") }
            }
        )
    }

    if (showOtpDialog) {
        AlertDialog(
            onDismissRequest = { showOtpDialog = false },
            title = { Text("Xác thực OTP") },
            text = {
                Column {
                    Text("Nhập mã OTP gồm 6 chữ số:", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = { otpCode = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (otpCode.isNotBlank() && storedVerificationId.isNotBlank()) {
                            val credential = PhoneAuthProvider.getCredential(storedVerificationId, otpCode)
                            FirebaseAuth.getInstance().signInWithCredential(credential)
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        val user = FirebaseAuth.getInstance().currentUser
                                        showOtpDialog = false
                                        Toast.makeText(context, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                                        onLoginSuccess(buildUserProfile(user))
                                    } else {
                                        Toast.makeText(context, "Sai mã OTP!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        }
                    }
                ) { Text("Xác nhận") }
            },
            dismissButton = {
                TextButton(onClick = { showOtpDialog = false }) { Text("Hủy") }
            }
        )
    }
}

@Composable
fun HomeScreen(
    data: HomeScreenData,
    user: UserProfile,
    tasks: List<TodoTask>,
    studyCategory: Category,
    lifeCategory: Category,
    onRefreshRemoteConfig: () -> Unit,
    onNavigate: (String) -> Unit,
    onOpenNotifications: () -> Unit
) {
    val context = LocalContext.current
    val isDark = when (globalSettings.themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val accentColor = globalSettings.accentColor
    val bgColor = if (isDark) Color(0xFF121212) else Color(0xFFF9FAFB)
    val cardBgColor = if (isDark) Color(0xFF1E1E1E) else Color.White
    val textPrimary = if (isDark) Color(0xFFF3F4F6) else Color(0xFF111827)
    val textSecondary = if (isDark) Color(0xFF9CA3AF) else Color.Gray

    val todayTasks = tasks.filter { it.date == "Hôm nay" || it.date == "2026-10-20" || it.date.isBlank() }
    val completedCount = todayTasks.count { it.isCompleted }
    val totalCount = todayTasks.size
    val progressPercent = if (totalCount > 0) (completedCount * 100) / totalCount else 0
    val studyRemaining = tasks.count { it.category.tagName == studyCategory.tagName && !it.isCompleted }
    val lifeRemaining = tasks.count { it.category.tagName == lifeCategory.tagName && !it.isCompleted }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onNavigate("profile") }
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFDCE6FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = user.initials,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF254EDB)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = data.dateText, fontSize = 12.sp, color = textSecondary)
                        Text(
                            text = "Chào ${user.fullName}!",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                        if (user.email.isNotBlank() && user.email.contains("@")) {
                            Text(
                                text = user.email,
                                fontSize = 12.sp,
                                color = textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        MockTeachApiService.fetchTasks(
                            onSuccess = { apiTasks ->
                                if (apiTasks.isNotEmpty()) {
                                    appData.tasks.clear()
                                    appData.tasks.addAll(apiTasks)
                                    Toast.makeText(context, "Đã cập nhật từ Mock API!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onError = {
                                Toast.makeText(context, "Không thể tải từ Mock API", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Tải lại từ Mock API",
                            tint = if (isDark) Color(0xFFE5E7EB) else Color(0xFF374151)
                        )
                    }
                    IconButton(onClick = { onOpenNotifications() }) {
                        val unreadBellCount = NotificationManagerHelper.unreadCount()
                        Box {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = "Notifications",
                                tint = if (isDark) Color(0xFFE5E7EB) else Color(0xFF374151)
                            )
                            if (unreadBellCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEF4444))
                                        .align(Alignment.TopEnd)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRefreshRemoteConfig() },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = accentColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = data.progressTitle,
                            fontSize = 12.sp,
                            color = Color(0xFFDCE6FF)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$completedCount/$totalCount việc",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = data.progressEncourage,
                            fontSize = 12.sp,
                            color = Color(0xFFDCE6FF)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .border(4.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$progressPercent%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBgColor)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(studyCategory.bgColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.School,
                                contentDescription = null,
                                tint = studyCategory.textColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(studyCategory.tagName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = textPrimary)
                        Text("$studyRemaining việc còn lại",
                            fontSize = 11.sp,
                            color = textSecondary)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBgColor)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(lifeCategory.bgColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Home,
                                contentDescription = null,
                                tint = lifeCategory.textColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(lifeCategory.tagName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = textPrimary)
                        Text("$lifeRemaining việc còn lại",
                            fontSize = 11.sp,
                            color = textSecondary)
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = data.todaySectionTitle,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
                Text(
                    text = data.seeAllText,
                    fontSize = 13.sp,
                    color = accentColor,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { onNavigate("task_list") }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            if (todayTasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.AssignmentLate,
                            contentDescription = null,
                            tint = textSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Chưa có công việc nào hôm nay",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = textSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Nhấn nút (+) bên dưới để thêm công việc mới",
                            fontSize = 12.sp,
                            color = textSecondary.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                items(todayTasks, key = { "today_${it.id}" }) { task ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigate("task_detail/${task.id}") },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBgColor)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable {
                                        val newDone = !task.isCompleted
                                        task.isCompleted = newDone
                                        MockTeachApiService.patchTaskStatus(task.id, newDone)
                                        FirebaseServiceHelper.toggleTaskStatus(context, task, explicitStatus = newDone)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (task.isCompleted) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(accentColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .border(1.5.dp, Color(0xFFD1D5DB), CircleShape)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = task.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (task.isCompleted) textSecondary else textPrimary,
                                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(task.category.bgColor)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = task.category.tagName,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = task.category.textColor
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = task.time,
                                        fontSize = 11.sp,
                                        color = textSecondary
                                    )
                                    if (task.priority == "Cao") {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFFFEE2E2))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "Gấp",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFEF4444)
                                            )
                                        }
                                    }
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = Color(0xFFD1D5DB),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
            }
        }
        AppBottomNavigation(
            currentRoute = "home",
            accentColor = accentColor,
            isDark = isDark,
            onNavigate = onNavigate,
            onOpenAdd = { onNavigate("add_task") },
            onOpenNotifications = onOpenNotifications
        )
    }
}
@Composable
fun TaskListScreen(
    tasks: List<TodoTask>,
    studyCategory: Category,
    lifeCategory: Category,
    onNavigate: (String) -> Unit,
    onOpenNotifications: () -> Unit
) {
    val context = LocalContext.current
    val isDark = when (globalSettings.themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val accentColor = globalSettings.accentColor
    val bgColor = if (isDark) Color(0xFF121212) else Color(0xFFF9FAFB)
    val cardBgColor = if (isDark) Color(0xFF1E1E1E) else Color.White
    val textPrimary = if (isDark) Color(0xFFF3F4F6) else Color(0xFF111827)
    val textSecondary = if (isDark) Color(0xFF9CA3AF) else Color.Gray

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("Tất cả") }

    val filteredTasks = tasks.filter { task ->
        val matchesSearch = task.title.contains(searchQuery, ignoreCase = true) ||
                task.note.contains(searchQuery, ignoreCase = true)
        val matchesCategory = when (selectedFilter) {
            "Học tập" -> task.category.tagName == studyCategory.tagName
            "Cuộc sống" -> task.category.tagName == lifeCategory.tagName
            else -> true
        }
        matchesSearch && matchesCategory
    }
    val pendingTasks = filteredTasks.filter { !it.isCompleted }
    val completedTasks = filteredTasks.filter { it.isCompleted }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "Danh sách",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
                Text(
                    text = "${tasks.count { !it.isCompleted }} việc chưa xong",
                    fontSize = 13.sp,
                    color = textSecondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Tìm công việc",
                    fontSize = 14.sp,
                    color = textSecondary) },
                leadingIcon = { Icon(Icons.Default.Search,
                    contentDescription = "Search", tint = textSecondary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = textSecondary)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = cardBgColor,
                    unfocusedContainerColor = cardBgColor,
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = if (isDark) Color(0xFF2E2E2E) else Color(0xFFE5E7EB)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val filters = listOf("Tất cả", "Học tập", "Cuộc sống")
                for (f in filters) {
                    val isSelected = selectedFilter == f
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = f },
                        label = {
                            Text(
                                text = f,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else textPrimary
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (isDark) accentColor else Color(0xFF1E293B),
                            containerColor = cardBgColor
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (pendingTasks.isNotEmpty()) {
                    item {
                        Text(
                            text = "CẦN LÀM",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = textSecondary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(pendingTasks, key = { "pending_${it.id}" }) { task ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigate("task_detail/${task.id}") },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBgColor)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .border(1.5.dp, Color(0xFFD1D5DB), CircleShape)
                                        .clickable {
                                            val newDone = !task.isCompleted
                                            task.isCompleted = newDone
                                            MockTeachApiService.patchTaskStatus(task.id, newDone)
                                            FirebaseServiceHelper.toggleTaskStatus(context, task, explicitStatus = newDone)
                                        }
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = task.title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = textPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(task.category.bgColor)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = task.category.tagName,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = task.category.textColor
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "${task.date}, ${task.time}",
                                            fontSize = 11.sp,
                                            color = textSecondary
                                        )
                                        if (task.priority == "Cao" || task.priority.lowercase() == "high") {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0xFFFEE2E2))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "Gấp",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFEF4444)
                                                )
                                            }
                                        }
                                    }
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = Color(0xFFD1D5DB),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
                if (completedTasks.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "ĐÃ XONG",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = textSecondary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(completedTasks, key = { "completed_${it.id}" }) { task ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigate("task_detail/${task.id}") },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBgColor)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(accentColor)
                                        .clickable {
                                            val newDone = !task.isCompleted
                                            task.isCompleted = newDone
                                            MockTeachApiService.patchTaskStatus(task.id, newDone)
                                            FirebaseServiceHelper.toggleTaskStatus(context, task, explicitStatus = newDone)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = task.title,
                                    fontSize = 14.sp,
                                    color = textSecondary,
                                    textDecoration = TextDecoration.LineThrough,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = Color(0xFFD1D5DB),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                if (pendingTasks.isEmpty() && completedTasks.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.AssignmentLate,
                                contentDescription = null,
                                tint = textSecondary,
                                modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Không tìm thấy công việc nào",
                                color = textSecondary,
                                fontSize = 14.sp)
                        }
                    }
                }
            }
        }
        AppBottomNavigation(
            currentRoute = "task_list",
            accentColor = accentColor,
            isDark = isDark,
            onNavigate = onNavigate,
            onOpenAdd = { onNavigate("add_task") },
            onOpenNotifications = onOpenNotifications
        )
    }
}
// Cơ sở dữ liệu thời gian thực
@Composable
fun AddTaskScreen(
    studyCategory: Category,
    lifeCategory: Category,
    onCancel: () -> Unit,
    onTaskCreated: () -> Unit
) {
    val context = LocalContext.current
    val isDark = when (globalSettings.themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val accentColor = globalSettings.accentColor
    val bgColor = if (isDark) Color(0xFF121212) else Color(0xFFF9FAFB)
    val cardBgColor = if (isDark) Color(0xFF1E1E1E) else Color.White
    val textPrimary = if (isDark) Color(0xFFF3F4F6) else Color(0xFF111827)
    val textSecondary = if (isDark) Color(0xFF9CA3AF) else Color.Gray

    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(studyCategory.tagName) }
    val todayCal = Calendar.getInstance()
    val defaultDateStr = String.format(Locale.getDefault(), "%02d/%02d/%04d",
        todayCal.get(Calendar.DAY_OF_MONTH),
        todayCal.get(Calendar.MONTH) + 1,
        todayCal.get(Calendar.YEAR))
    val defaultTimeStr = String.format(Locale.getDefault(), "%02d:%02d",
        (todayCal.get(Calendar.HOUR_OF_DAY) + 1) % 24,
        0)
    var taskDate by remember { mutableStateOf(defaultDateStr) }
    var taskTime by remember { mutableStateOf(defaultTimeStr) }
    var reminderEnabled by remember { mutableStateOf(true) }
    var reminderTimeOption by remember { mutableStateOf("Trước 15 phút") }
    var showReminderMenu by remember { mutableStateOf(false) }
    var selectedPriority by remember { mutableStateOf("Vừa") }

    val showDatePicker = {
        val c = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                taskDate = String.format(Locale.getDefault(), "%02d/%02d/%04d", dayOfMonth, month + 1, year)
            },
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH),
            c.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    val showTimePicker = {
        val c = Calendar.getInstance()
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                taskTime = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
            },
            c.get(Calendar.HOUR_OF_DAY),
            c.get(Calendar.MINUTE),
            true
        ).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .systemBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Hủy",
                color = accentColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { onCancel() }
            )
            Text(
                text = "Việc mới",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
            Text(
                text = "Lưu",
                color = accentColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    if (title.isNotBlank()) {
                        FirebaseServiceHelper.addNewTask(
                            context = context,
                            title = title.trim(),
                            categoryName = selectedCategory,
                            time = taskTime.trim(),
                            date = taskDate.trim(),
                            note = note.trim(),
                            priority = selectedPriority,
                            reminder = reminderEnabled,
                            reminderTime = reminderTimeOption
                        )
                        Toast.makeText(context, "Đã thêm công việc thành công!", Toast.LENGTH_SHORT).show()
                        onTaskCreated()
                    } else {
                        Toast.makeText(context, "Vui lòng nhập tên công việc", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
        HorizontalDivider(color = if (isDark) Color(0xFF2E2E2E) else Color(0xFFE5E7EB))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Tên công việc", fontSize = 12.sp, color = textSecondary)
                    TextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text("Ôn chương 4 – Giải thuật",
                            color = Color.Gray,
                            fontSize = 15.sp) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    HorizontalDivider(color = if (isDark) Color(0xFF2E2E2E) else Color(0xFFE5E7EB))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Ghi chú", fontSize = 12.sp, color = textSecondary)
                    TextField(
                        value = note,
                        onValueChange = { note = it },
                        placeholder = { Text("Thêm ghi chú (không bắt buộc)",
                            color = Color.Gray,
                            fontSize = 14.sp) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Column {
                Text("Nhóm", fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val isStudy = selectedCategory == studyCategory.tagName
                    OutlinedButton(
                        onClick = { selectedCategory = studyCategory.tagName },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            width = if (isStudy) 2.dp else 1.dp,
                            color = if (isStudy) accentColor else if (isDark) Color(0xFF333333) else Color(0xFFD1D5DB)
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isStudy) accentColor.copy(alpha = 0.08f) else cardBgColor
                        )
                    ) {
                        Text(
                            studyCategory.tagName,
                            fontWeight = if (isStudy) FontWeight.Bold else FontWeight.Normal,
                            color = if (isStudy) accentColor else textPrimary
                        )
                    }

                    val isLife = selectedCategory == lifeCategory.tagName
                    OutlinedButton(
                        onClick = { selectedCategory = lifeCategory.tagName },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            width = if (isLife) 2.dp else 1.dp,
                            color = if (isLife) Color(0xFFD97706) else if (isDark) Color(0xFF333333) else Color(0xFFD1D5DB)
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isLife) Color(0xFFFEF3C7) else cardBgColor
                        )
                    ) {
                        Text(
                            lifeCategory.tagName,
                            fontWeight = if (isLife) FontWeight.Bold else FontWeight.Normal,
                            color = if (isLife) Color(0xFFD97706) else textPrimary
                        )
                    }
                }
            }
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { showDatePicker() }
                        ) {
                            Icon(Icons.Default.DateRange,
                                contentDescription = "Chọn ngày",
                                tint = accentColor,
                                modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Ngày", fontSize = 14.sp, color = textPrimary)
                        }
                        OutlinedTextField(
                            value = taskDate,
                            onValueChange = { taskDate = it },
                            singleLine = true,
                            modifier = Modifier.width(150.dp)
                        )
                    }
                    HorizontalDivider(color = if (isDark) Color(0xFF2E2E2E) else Color(0xFFE5E7EB))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { showTimePicker() }
                        ) {
                            Icon(Icons.Default.Schedule,
                                contentDescription = "Chọn giờ",
                                tint = accentColor,
                                modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Giờ", fontSize = 14.sp, color = textPrimary)
                        }
                        OutlinedTextField(
                            value = taskTime,
                            onValueChange = { taskTime = it },
                            singleLine = true,
                            modifier = Modifier.width(120.dp)
                        )
                    }
                }
            }
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Notifications,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Nhắc tôi trước hạn chót", fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = textPrimary)
                                Text("Báo thức cả khi tắt app",
                                    fontSize = 11.sp,
                                    color = textSecondary)
                            }
                        }
                        Switch(
                            checked = reminderEnabled,
                            onCheckedChange = { reminderEnabled = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor)
                        )
                    }

                    if (reminderEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = if (isDark) Color(0xFF2E2E2E) else Color(0xFFE5E7EB))
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Thời gian nhắc nhở:", fontSize = 13.sp, color = textSecondary)
                            Box {
                                OutlinedButton(
                                    onClick = { showReminderMenu = true },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(reminderTimeOption, fontSize = 12.sp, color = accentColor, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                                }
                                DropdownMenu(
                                    expanded = showReminderMenu,
                                    onDismissRequest = { showReminderMenu = false }
                                ) {
                                    val reminderOptions = listOf(
                                        "Trước 15 phút",
                                        "Trước 30 phút",
                                        "Trước 5 phút",
                                        "Trước 1 giờ",
                                        "Đúng giờ"
                                    )
                                    for (opt in reminderOptions) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = if (opt == "Trước 15 phút") "$opt (Mặc định)" else opt,
                                                    fontWeight = if (opt == reminderTimeOption) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (opt == reminderTimeOption) accentColor else textPrimary
                                                )
                                            },
                                            onClick = {
                                                reminderTimeOption = opt
                                                showReminderMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Column {
                Text("Mức ưu tiên", fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(cardBgColor)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    val priorities = listOf("Thấp", "Vừa", "Cao")
                    for (p in priorities) {
                        val isPSelected = selectedPriority == p
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isPSelected) accentColor else Color.Transparent)
                                .clickable { selectedPriority = p }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = p,
                                fontSize = 13.sp,
                                fontWeight = if (isPSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isPSelected) Color.White else textPrimary
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val newTask = TodoTask(
                            id = System.currentTimeMillis().toString(),
                            title = title.trim(),
                            category = appData.getCategoryByName(selectedCategory),
                            time = taskTime.trim(),
                            date = taskDate.trim(),
                            note = note.trim(),
                            priority = selectedPriority,
                            reminder = reminderEnabled,
                            reminderTime = reminderTimeOption,
                            initialCompleted = false
                        )
                        MockTeachApiService.createTask(newTask)
                        FirebaseServiceHelper.addNewTask(
                            context = context,
                            title = title.trim(),
                            categoryName = selectedCategory,
                            time = taskTime.trim(),
                            date = taskDate.trim(),
                            note = note.trim(),
                            priority = selectedPriority,
                            reminder = reminderEnabled,
                            reminderTime = reminderTimeOption
                        )
                        appData.tasks.add(newTask)
                        Toast.makeText(context, "Đã lưu công việc!", Toast.LENGTH_SHORT).show()
                        onTaskCreated()
                    } else {
                        Toast.makeText(context, "Vui lòng nhập tên công việc!", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Text("Lưu công việc", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
fun TaskDetailScreen(
    task: TodoTask,
    onBack: () -> Unit,
    onTaskDeleted: () -> Unit
) {
    val context = LocalContext.current
    val isDark = when (globalSettings.themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val accentColor = globalSettings.accentColor
    val bgColor = if (isDark) Color(0xFF121212) else Color(0xFFF9FAFB)
    val cardBgColor = if (isDark) Color(0xFF1E1E1E) else Color.White
    val textPrimary = if (isDark) Color(0xFFF3F4F6) else Color(0xFF111827)
    val textSecondary = if (isDark) Color(0xFF9CA3AF) else Color.Gray

    var isEditing by remember { mutableStateOf(false) }
    var editedTitle by remember { mutableStateOf(task.title) }
    var editedNote by remember { mutableStateOf(task.note) }
    var editedDate by remember { mutableStateOf(task.date) }
    var editedTime by remember { mutableStateOf(task.time) }
    var editedReminder by remember { mutableStateOf(task.reminder) }
    var editedReminderTime by remember { mutableStateOf(task.reminderTime) }
    var showEditReminderMenu by remember { mutableStateOf(false) }
    var newSubTaskTitle by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val showDatePicker = {
        val c = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                editedDate = String.format(Locale.getDefault(), "%02d/%02d/%04d", dayOfMonth, month + 1, year)
            },
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH),
            c.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    val showTimePicker = {
        val c = Calendar.getInstance()
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                editedTime = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
            },
            c.get(Calendar.HOUR_OF_DAY),
            c.get(Calendar.MINUTE),
            true
        ).show()
    }

    val completedSubtasks = task.subTasks.count { it.isCompleted }
    val totalSubtasks = task.subTasks.size
    val subtaskRatio = if (totalSubtasks > 0) completedSubtasks.toFloat() / totalSubtasks else 0f

    // Tuần 04: Gọi GET /mock/teach/tasks/{id} để mở khi bấm một việc
    LaunchedEffect(task.id) {
        MockTeachApiService.fetchTaskDetail(
            taskId = task.id,
            onSuccess = { apiTask ->
                task.title = apiTask.title
                task.note = apiTask.note
                task.date = apiTask.date
                task.time = apiTask.time
                task.priority = apiTask.priority
                task.reminder = apiTask.reminder
                task.isCompleted = apiTask.isCompleted
                editedTitle = apiTask.title
                editedNote = apiTask.note
                editedDate = apiTask.date
                editedTime = apiTask.time
                if (apiTask.subTasks.isNotEmpty()) {
                    task.subTasks.clear()
                    task.subTasks.addAll(apiTask.subTasks)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .systemBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onBack() }
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Back",
                    tint = accentColor)
                Text("Danh sách", fontSize = 15.sp,
                    color = accentColor,
                    fontWeight = FontWeight.Medium)
            }
            Text(
                text = if (isEditing) "Xong" else "Sửa",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                modifier = Modifier.clickable {
                    if (isEditing) {
                        task.title = editedTitle
                        task.note = editedNote
                        task.date = editedDate
                        task.time = editedTime
                        task.reminder = editedReminder
                        task.reminderTime = editedReminderTime
                        MockTeachApiService.patchTaskFull(task.id, task)
                        FirebaseServiceHelper.updateTask(context, task)
                        Toast.makeText(context, "Đã cập nhật công việc!", Toast.LENGTH_SHORT).show()
                    }
                    isEditing = !isEditing
                }
            )
        }
        HorizontalDivider(color = if (isDark) Color(0xFF2E2E2E) else Color(0xFFE5E7EB))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(task.category.bgColor)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = task.category.tagName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = task.category.textColor
                    )
                }
                val priorityColor = when (task.priority) {
                    "Cao" -> Color(0xFFEF4444)
                    "Thấp" -> Color(0xFF10B981)
                    else -> Color(0xFFF59E0B)
                }
                val priorityBg = when (task.priority) {
                    "Cao" -> Color(0xFFFEE2E2)
                    "Thấp" -> Color(0xFFD1FAE5)
                    else -> Color(0xFFFEF3C7)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(priorityBg)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Ưu tiên ${task.priority.lowercase()}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = priorityColor
                    )
                }
            }

            if (isEditing) {
                OutlinedTextField(
                    value = editedTitle,
                    onValueChange = { editedTitle = it },
                    label = { Text("Tên công việc") },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = task.title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    lineHeight = 28.sp
                )
            }
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    if (isEditing) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { showDatePicker() }
                            ) {
                                Icon(Icons.Default.DateRange,
                                    contentDescription = "Chọn ngày",
                                    tint = accentColor,
                                    modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Ngày:", fontSize = 14.sp, color = textPrimary)
                            }
                            OutlinedTextField(
                                value = editedDate,
                                onValueChange = { editedDate = it },
                                singleLine = true,
                                modifier = Modifier.width(150.dp)
                            )
                        }
                        HorizontalDivider(color = if (isDark) Color(0xFF2E2E2E) else Color(0xFFE5E7EB))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { showTimePicker() }
                            ) {
                                Icon(Icons.Default.Schedule,
                                    contentDescription = "Chọn giờ",
                                    tint = accentColor,
                                    modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Giờ:", fontSize = 14.sp, color = textPrimary)
                            }
                            OutlinedTextField(
                                value = editedTime,
                                onValueChange = { editedTime = it },
                                singleLine = true,
                                modifier = Modifier.width(120.dp)
                            )
                        }
                        HorizontalDivider(color = if (isDark) Color(0xFF2E2E2E) else Color(0xFFE5E7EB))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Notifications,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Nhắc nhở:", fontSize = 14.sp, color = textPrimary)
                            }
                            Switch(
                                checked = editedReminder,
                                onCheckedChange = { editedReminder = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor)
                            )
                        }
                        if (editedReminder) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Thời điểm nhắc:", fontSize = 13.sp, color = textSecondary)
                                Box {
                                    OutlinedButton(
                                        onClick = { showEditReminderMenu = true },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(editedReminderTime, fontSize = 12.sp, color = accentColor)
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                                    }
                                    DropdownMenu(
                                        expanded = showEditReminderMenu,
                                        onDismissRequest = { showEditReminderMenu = false }
                                    ) {
                                        val options = listOf(
                                            "Trước 15 phút",
                                            "Trước 30 phút",
                                            "Trước 5 phút",
                                            "Trước 1 giờ",
                                            "Đúng giờ"
                                        )
                                        for (opt in options) {
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = if (opt == "Trước 15 phút") "$opt (Mặc định)" else opt,
                                                        fontWeight = if (opt == editedReminderTime) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (opt == editedReminderTime) accentColor else textPrimary
                                                    )
                                                },
                                                onClick = {
                                                    editedReminderTime = opt
                                                    showEditReminderMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DateRange,
                                    contentDescription = null,
                                    tint = textSecondary,
                                    modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Hạn chót", fontSize = 14.sp, color = textSecondary)
                            }
                            Text("${task.date}, ${task.time}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = textPrimary)
                        }
                        HorizontalDivider(color = if (isDark) Color(0xFF2E2E2E) else Color(0xFFE5E7EB))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Notifications,
                                    contentDescription = null,
                                    tint = textSecondary,
                                    modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Nhắc nhở",
                                    fontSize = 14.sp,
                                    color = textSecondary)
                            }
                            Text(if (task.reminder) task.reminderTime else "Không",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = textPrimary)
                        }
                    }
                    HorizontalDivider(color = if (isDark) Color(0xFF2E2E2E) else Color(0xFFE5E7EB))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Repeat,
                                contentDescription = null,
                                tint = textSecondary,
                                modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Lặp lại",
                                fontSize = 14.sp,
                                color = textSecondary)
                        }
                        Text("Không", fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = textPrimary)
                    }
                }
            }
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Ghi chú", fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = textSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    if (isEditing) {
                        OutlinedTextField(
                            value = editedNote,
                            onValueChange = { editedNote = it },
                            placeholder = { Text("Thêm ghi chú") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = task.note.ifBlank { "Không có ghi chú" },
                            fontSize = 14.sp,
                            color = textPrimary,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Việc nhỏ",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary)
                        Text("$completedSubtasks/$totalSubtasks",
                            fontSize = 12.sp,
                            color = textSecondary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { subtaskRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = accentColor,
                        trackColor = if (isDark) Color(0xFF333333) else Color(0xFFE5E7EB)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    for ((index, st) in task.subTasks.withIndex()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(if (st.isCompleted) accentColor else Color.Transparent)
                                    .border(1.5.dp, if (st.isCompleted) accentColor else Color(0xFFD1D5DB), CircleShape)
                                    .clickable {
                                        task.subTasks[index] = st.copy(isCompleted = !st.isCompleted)
                                        MockTeachApiService.patchTaskFull(task.id, task)
                                        FirebaseServiceHelper.updateTask(context, task)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (st.isCompleted) {
                                    Icon(Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = st.title,
                                fontSize = 13.sp,
                                color = if (st.isCompleted) textSecondary else textPrimary,
                                textDecoration = if (st.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    task.subTasks.remove(st)
                                    MockTeachApiService.patchTaskFull(task.id, task)
                                    FirebaseServiceHelper.updateTask(context, task)
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline,
                                    contentDescription = "Delete",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newSubTaskTitle,
                            onValueChange = { newSubTaskTitle = it },
                            placeholder = { Text("Thêm việc nhỏ...", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newSubTaskTitle.isNotBlank()) {
                                    task.subTasks.add(
                                        SubTaskItem(
                                            id = "${task.id}_sub_${System.currentTimeMillis()}",
                                            title = newSubTaskTitle.trim(),
                                            isCompleted = false
                                        )
                                    )
                                    MockTeachApiService.patchTaskFull(task.id, task)
                                    FirebaseServiceHelper.updateTask(context, task)
                                    newSubTaskTitle = ""
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                        ) {
                            Text("Thêm", fontSize = 12.sp)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = {
                    val newDone = !task.isCompleted
                    task.isCompleted = newDone
                    MockTeachApiService.patchTaskStatus(task.id, newDone)
                    FirebaseServiceHelper.toggleTaskStatus(context, task, explicitStatus = newDone)
                    Toast.makeText(context, if (newDone) "Đã hoàn thành công việc!" else "Đã chuyển về chưa xong", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (task.isCompleted) Color(0xFF10B981) else accentColor
                )
            ) {
                Icon(
                    imageVector = if (task.isCompleted) Icons.Default.CheckCircle else Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (task.isCompleted) "Đã hoàn thành (Bấm để mở lại)" else "Đánh dấu hoàn thành",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            TextButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Xóa công việc",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEF4444)
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Xác nhận xóa") },
            text = { Text("Bạn có chắc chắn muốn xóa công việc '${task.title}' không?") },
            confirmButton = {
                Button(
                    onClick = {
                        MockTeachApiService.deleteTask(
                            taskId = task.id,
                            onSuccess = {
                                appData.tasks.removeAll { it.id == task.id }
                                FirebaseServiceHelper.deleteTask(context, task.id)
                                showDeleteConfirm = false
                                Toast.makeText(context, "Đã xóa công việc trên Mock API!", Toast.LENGTH_SHORT).show()
                                onTaskDeleted()
                            },
                            onError = {
                                appData.tasks.removeAll { it.id == task.id }
                                FirebaseServiceHelper.deleteTask(context, task.id)
                                showDeleteConfirm = false
                                Toast.makeText(context, "Đã xóa công việc!", Toast.LENGTH_SHORT).show()
                                onTaskDeleted()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) { Text("Xóa") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Hủy") }
            }
        )
    }
}
@Composable
fun SettingsScreen(
    user: UserProfile,
    onNavigate: (String) -> Unit,
    onOpenNotifications: () -> Unit
) {
    val context = LocalContext.current
    val isDark = when (globalSettings.themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val accentColor = globalSettings.accentColor
    val bgColor = if (isDark) Color(0xFF121212) else Color(0xFFF9FAFB)
    val cardBgColor = if (isDark) Color(0xFF1E1E1E) else Color.White
    val textPrimary = if (isDark) Color(0xFFF3F4F6) else Color(0xFF111827)
    val textSecondary = if (isDark) Color(0xFF9CA3AF) else Color.Gray

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cài đặt",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFDCE6FF))
                        .clickable { onNavigate("profile") },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user.initials,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF254EDB)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text("GIAO DIỆN",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = textSecondary)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val themeOptions = listOf(
                    Triple("Sáng", "light", Color(0xFFFFFFFF)),
                    Triple("Tối", "dark", Color(0xFF1E1E1E)),
                    Triple("Tự động", "auto", Color(0xFF6B7280))
                )
                for ((label, mode, color) in themeOptions) {
                    val isSelected = globalSettings.themeMode == mode
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { globalSettings.themeMode = mode },
                        shape = RoundedCornerShape(12.dp),
                        border = if (isSelected) BorderStroke(2.dp, accentColor) else null,
                        colors = CardDefaults.cardColors(containerColor = cardBgColor)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(color)
                                    .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) accentColor else textPrimary
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Màu chủ đạo",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = textPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val colors = listOf(
                            Color(0xFF254EDB),
                            Color(0xFF10B981),
                            Color(0xFFEA580C),
                            Color(0xFF7C3AED)
                        )
                        for (c in colors) {
                            val isColorSelected = globalSettings.accentColor == c
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(
                                        width = if (isColorSelected) 3.dp else 0.dp,
                                        color = if (isColorSelected) (if (isDark) Color.White else Color(0xFF1F2937)) else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { globalSettings.accentColor = c }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Cỡ chữ", fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = textPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("A", fontSize = 12.sp, color = textSecondary)
                        Slider(
                            value = globalSettings.fontSizeScale,
                            onValueChange = { globalSettings.fontSizeScale = it },
                            valueRange = 0.85f..1.15f,
                            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = accentColor,
                                activeTrackColor = accentColor
                            )
                        )
                        Text("A", fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text("THÔNG BÁO",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = textSecondary)
            Spacer(modifier = Modifier.height(10.dp))

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Nhắc việc",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = textPrimary)
                            Text("Báo trước hạn chót 15 phút",
                                fontSize = 11.sp,
                                color = textSecondary)
                        }
                        Switch(
                            checked = globalSettings.reminderEnabled,
                            onCheckedChange = { globalSettings.reminderEnabled = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor)
                        )
                    }
                    HorizontalDivider(color = if (isDark) Color(0xFF2E2E2E) else Color(0xFFE5E7EB))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Âm thanh", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                            Text("Phát âm khi có thông báo", fontSize = 11.sp, color = textSecondary)
                        }
                        Switch(
                            checked = globalSettings.soundEnabled,
                            onCheckedChange = { globalSettings.soundEnabled = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor)
                        )
                    }
                    HorizontalDivider(color = if (isDark) Color(0xFF2E2E2E) else Color(0xFFE5E7EB))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Tổng kết cuối ngày", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                            Text("Gửi lúc 21:00 mỗi tối", fontSize = 11.sp, color = textSecondary)
                        }
                        Switch(
                            checked = globalSettings.dailySummaryEnabled,
                            onCheckedChange = { globalSettings.dailySummaryEnabled = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text("CHUNG",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = textSecondary)
            Spacer(modifier = Modifier.height(10.dp))

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigate("profile") }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Hồ sơ cá nhân", fontSize = 14.sp, color = textPrimary)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = textSecondary)
                    }
                    HorizontalDivider(color = if (isDark) Color(0xFF2E2E2E) else Color(0xFFE5E7EB))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Ngôn ngữ", fontSize = 14.sp, color = textPrimary)
                        Text("Tiếng Việt >", fontSize = 13.sp, color = textSecondary)
                    }
                    HorizontalDivider(color = if (isDark) Color(0xFF2E2E2E) else Color(0xFFE5E7EB))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { FirebaseServiceHelper.recordNonFatalError(context) }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Gửi báo cáo lỗi (Crashlytics)", fontSize = 14.sp, color = accentColor)
                        Icon(Icons.Default.BugReport,
                            contentDescription = null,
                            tint = accentColor)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
        AppBottomNavigation(
            currentRoute = "settings",
            accentColor = accentColor,
            isDark = isDark,
            onNavigate = onNavigate,
            onOpenAdd = { onNavigate("add_task") },
            onOpenNotifications = onOpenNotifications
        )
    }
}

// Xác thực
@Composable
fun ProfileScreen(
    user: UserProfile,
    tasks: List<TodoTask>,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val isDark = when (globalSettings.themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val accentColor = globalSettings.accentColor
    val bgColor = if (isDark) Color(0xFF121212) else Color(0xFFF9FAFB)
    val cardBgColor = if (isDark) Color(0xFF1E1E1E) else Color.White
    val textPrimary = if (isDark) Color(0xFFF3F4F6) else Color(0xFF111827)
    val textSecondary = if (isDark) Color(0xFF9CA3AF) else Color.Gray

    val completedCount = tasks.count { it.isCompleted }
    val totalCount = tasks.size
    val rate = if (totalCount > 0) "${(completedCount * 100) / totalCount}%" else user.completionRate

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clickable { onBack() }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Back",
                    tint = accentColor
                )
                Text(
                    text = "Cài đặt",
                    fontSize = 14.sp,
                    color = accentColor,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = "Hồ sơ",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary,
                modifier = Modifier.align(Alignment.Center)
            )
            Text(
                text = "Sửa",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clickable { Toast.makeText(context, "Chức năng sửa thông tin", Toast.LENGTH_SHORT).show() }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFDCE6FF)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.initials,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF254EDB)
                )
            }
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(accentColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = "Edit photo",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = user.fullName,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = textPrimary
        )
        if (user.email.isNotBlank() && user.email.contains("@")) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = user.email, fontSize = 13.sp, color = textSecondary)
        }
        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFDCFCE7))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = user.accountBadge,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF15803D)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("${user.completedTasks}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary)
                    Text("Việc đã xong",
                        fontSize = 11.sp,
                        color = textSecondary)
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("12 ngày",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary)
                    Text("Chuỗi liên tiếp",
                        fontSize = 11.sp,
                        color = textSecondary)
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(rate,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary)
                    Text("Đúng hạn",
                        fontSize = 11.sp,
                        color = textSecondary)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBgColor)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Tuần này",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = textPrimary)
                    Text("18/22 việc",
                        fontSize = 12.sp,
                        color = textSecondary)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.Bottom
                ) {
                    for ((dayLabel, heightRatio, isHighlighted) in user.weeklyStats) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(16.dp)
                                    .fillMaxHeight(heightRatio)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (isHighlighted) accentColor
                                        else if (heightRatio > 0.2f) accentColor.copy(alpha = 0.4f)
                                        else if (isDark) Color(0xFF333333) else Color(0xFFE5E7EB)
                                    )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = dayLabel,
                                fontSize = 10.sp,
                                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                                color = if (isHighlighted) accentColor else textSecondary
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = cardBgColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Sync,
                            contentDescription = null,
                            tint = textSecondary,
                            modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Đồng bộ dữ liệu", fontSize = 14.sp, color = textPrimary)
                    }
                    Text("Vừa xong", fontSize = 12.sp, color = textSecondary)
                }
                HorizontalDivider(color = if (isDark) Color(0xFF2E2E2E) else Color(0xFFE5E7EB))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock,
                            contentDescription = null,
                            tint = textSecondary,
                            modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Quyền riêng tư", fontSize = 14.sp, color = textPrimary)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = textSecondary)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                Toast.makeText(context, "Đã đăng xuất!", Toast.LENGTH_SHORT).show()
                onLogout()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = cardBgColor)
        ) {
            Text(
                text = "[→ Đăng xuất",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFDC2626)
            )
        }
    }
}

//Thông báo, máy chủ
@Composable
fun NotificationsScreen(
    onNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isDark = when (globalSettings.themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val accentColor = globalSettings.accentColor
    val bgColor = if (isDark) Color(0xFF121212) else Color(0xFFF9FAFB)
    val cardBgColor = if (isDark) Color(0xFF1E1E1E) else Color.White
    val textPrimary = if (isDark) Color(0xFFF3F4F6) else Color(0xFF111827)
    val textSecondary = if (isDark) Color(0xFF9CA3AF) else Color.Gray
    val borderColor = if (isDark) Color(0xFF2E2E2E) else Color(0xFFE5E7EB)

    val notifications = NotificationManagerHelper.notifications
    val unreadCount = NotificationManagerHelper.unreadCount()
    var isSendingTest by remember { mutableStateOf(false) }

    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(15.seconds)
            currentTimeMillis = System.currentTimeMillis()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Quay lại",
                            tint = textPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Thông báo",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                    if (unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFEF4444).copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "$unreadCount mới",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444)
                            )
                        }
                    }
                }

                Row {
                    if (notifications.isNotEmpty()) {
                        TextButton(onClick = { NotificationManagerHelper.markAllAsRead(context) }) {
                            Text("Đọc hết",
                                fontSize = 12.sp,
                                color = accentColor)
                        }
                        TextButton(onClick = { NotificationManagerHelper.clearAll(context) }) {
                            Text("Xóa hết",
                                fontSize = 12.sp,
                                color = Color(0xFFEF4444))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (!isSendingTest) {
                            isSendingTest = true
                            FirebaseServiceHelper.pushServerNotification(
                                title = "Thông báo máy chủ Realtime",
                                message = "Server vừa đồng bộ dữ liệu vào lúc ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}."
                            ) { success ->
                                isSendingTest = false
                                if (success) {
                                    Toast.makeText(context, "Đã gửi thông báo lên Firebase Realtime Database!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Lỗi gửi lên Firebase!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = accentColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isSendingTest) "Đang gửi..." else "Gửi thử Realtime DB",
                        fontSize = 11.sp,
                        color = accentColor,
                        maxLines = 1
                    )
                }
                OutlinedButton(
                    onClick = {
                        val title = "Lời nhắc hoàn thành bài tập"
                        val msg = "Hệ thống nhắc: Bạn còn công việc chưa hoàn thành hôm nay!"
                        NotificationManagerHelper.addNotification(title, msg, type = "fcm")
                        NotificationManagerHelper.showSystemNotification(context, title, msg)
                        Toast.makeText(context, "Đã phát thông báo hệ thống!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = accentColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Bắn thử thông báo",
                        fontSize = 11.sp,
                        color = accentColor,
                        maxLines = 1
                    )
                }
            }
            OutlinedButton(
                onClick = {
                    val testTitle = "Ôn bài Giải thuật"
                    val nowCal = Calendar.getInstance()
                    nowCal.add(Calendar.MINUTE, 15)
                    nowCal.add(Calendar.SECOND, 5)
                    val timeStr = String.format(Locale.getDefault(), "%02d:%02d", nowCal.get(Calendar.HOUR_OF_DAY), nowCal.get(Calendar.MINUTE))
                    TaskAlarmScheduler.scheduleTaskReminder(
                        context = context,
                        taskId = "test_alarm_${System.currentTimeMillis()}",
                        title = testTitle,
                        date = "Hôm nay",
                        time = timeStr,
                        reminder = true,
                        reminderTime = "Trước 15 phút"
                    )
                    Toast.makeText(context, "Đã hẹn nhắc nhở trước 15p (báo sau 5s)! Bạn có thể khóa máy hoặc đóng app để kiểm tra.", Toast.LENGTH_LONG).show()
                },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color(0xFF8B5CF6)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "⏰ Thử nhắc nhở trước 15p (báo sau 5 giây)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF8B5CF6),
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            if (notifications.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Chưa có thông báo nào",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Khi server gửi thông báo qua Firebase Cloud Messaging (FCM) hoặc ghi vào Realtime Database (/notifications), nội dung sẽ xuất hiện ngay tại đây.",
                            fontSize = 13.sp,
                            color = textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(notifications, key = { it.id }) { notif ->
                        NotificationCard(
                            notification = notif,
                            currentTimeMillis = currentTimeMillis,
                            cardBgColor = cardBgColor,
                            borderColor = borderColor,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            accentColor = accentColor,
                            onItemClick = { NotificationManagerHelper.markAsRead(notif.id, context) },
                            onDelete = { NotificationManagerHelper.removeNotification(notif.id, context) }
                        )
                    }
                }
            }
        }
        AppBottomNavigation(
            currentRoute = "notifications",
            accentColor = accentColor,
            isDark = isDark,
            onNavigate = onNavigate,
            onOpenAdd = { onNavigate("add_task") },
            onOpenNotifications = { }
        )
    }
}

@Composable
fun NotificationCard(
    notification: AppNotification,
    currentTimeMillis: Long = System.currentTimeMillis(),
    cardBgColor: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentColor: Color,
    onItemClick: () -> Unit,
    onDelete: () -> Unit
) {
    val isReminderWithDeadline = notification.type == "reminder" && notification.deadlineMs > 0L
    val diffMs = if (isReminderWithDeadline) notification.deadlineMs - currentTimeMillis else 0L
    val diffMinutes = if (diffMs > 0) ((diffMs + 59999) / 60000).toInt() else (diffMs / 60000).toInt()

    val remainingBadge = when {
        diffMinutes > 60 -> {
            val h = diffMinutes / 60
            val m = diffMinutes % 60
            if (m > 0) "Còn ${h}h ${m}p" else "Còn ${h}h"
        }
        diffMinutes > 0 -> "Còn $diffMinutes phút"
        diffMinutes == 0 && diffMs >= -60000L -> "Đến hạn ngay bây giờ"
        else -> "Quá hạn ${abs(diffMinutes)} phút"
    }

    val typeBadge = when {
        isReminderWithDeadline -> "⏰ $remainingBadge"
        notification.type == "server_rtdb" -> "Realtime DB"
        notification.type == "fcm" -> "Máy chủ FCM"
        notification.type == "reminder" -> "Nhắc việc"
        else -> "Hệ thống"
    }

    val badgeColor = when {
        isReminderWithDeadline -> {
            when {
                diffMinutes <= 0 -> Color(0xFFEF4444)
                diffMinutes <= 5 -> Color(0xFFEF4444)
                diffMinutes <= 15 -> Color(0xFFF59E0B)
                else -> Color(0xFF8B5CF6)
            }
        }
        notification.type == "server_rtdb" -> Color(0xFF0284C7)
        notification.type == "fcm" -> Color(0xFFD97706)
        notification.type == "reminder" -> Color(0xFF8B5CF6)
        else -> accentColor
    }

    val displayMessage = if (isReminderWithDeadline) {
        val timeInfo = if (notification.taskTime.isNotBlank()) " lúc ${notification.taskTime}" else ""
        val remainingDesc = when {
            diffMinutes > 60 -> "còn ${diffMinutes / 60}h ${diffMinutes % 60}p"
            diffMinutes > 0 -> "còn $diffMinutes phút"
            diffMinutes == 0 && diffMs >= -60000L -> "đến hạn ngay bây giờ"
            else -> "đã quá hạn ${abs(diffMinutes)} phút"
        }
        "Công việc sẽ đến hạn$timeInfo ($remainingDesc). Hãy chuẩn bị hoàn thành đúng hạn nhé!"
    } else {
        notification.message
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = BorderStroke(
            1.dp,
            if (!notification.isRead) {
                if (isReminderWithDeadline && diffMinutes in 0..5) Color(0xFFEF4444) else accentColor.copy(alpha = 0.5f)
            } else borderColor
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (!notification.isRead) badgeColor.copy(alpha = 0.15f)
                        else Color.Gray.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = null,
                    tint = if (!notification.isRead) badgeColor else textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = notification.title,
                        fontSize = 14.sp,
                        fontWeight = if (!notification.isRead) FontWeight.Bold else FontWeight.SemiBold,
                        color = textPrimary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!notification.isRead) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444))
                        )
                    }
                }

                if (displayMessage.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = displayMessage,
                        fontSize = 13.sp,
                        color = textSecondary,
                        lineHeight = 18.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(badgeColor.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = typeBadge,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = badgeColor
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = notification.time,
                            fontSize = 11.sp,
                            color = textSecondary.copy(alpha = 0.8f)
                        )
                    }
                    Text(
                        text = "Xóa",
                        fontSize = 11.sp,
                        color = textSecondary,
                        modifier = Modifier
                            .clickable { onDelete() }
                            .padding(4.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    Btt5_2_todo_dvfirebaseTheme {
        TodoApp()
    }
}