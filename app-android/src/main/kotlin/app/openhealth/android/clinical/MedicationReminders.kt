package app.openhealth.android.clinical

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.openhealth.R
import app.openhealth.android.MainActivity
import app.openhealth.android.OpenHealthApp
import app.openhealth.core.clinical.Medication
import app.openhealth.core.clinical.MedicationDose
import app.openhealth.core.ids.SourceId
import app.openhealth.core.observation.Provenance
import app.openhealth.core.time.TimeProvenance
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.time.Instant
import kotlinx.datetime.toLocalDateTime

/**
 * Local medication reminders (`pr.md` §6.9; `BUILD-ROADMAP.md` §B12). No push server, no
 * network: one `AlarmManager` alarm per medicine and time, a notification that offers
 * Taken and Skipped, and a boot receiver that puts the schedule back. A reminder is a
 * convenience, never a medical alert, and nothing is assumed about a dose that is not
 * logged.
 */
@Suppress("TooManyFunctions", "MagicNumber")
object MedicationReminders {
    const val CHANNEL_ID = "medication_reminders"
    const val ACTION_REMIND = "app.openhealth.MEDICATION_REMIND"
    const val ACTION_TAKEN = "app.openhealth.MEDICATION_TAKEN"
    const val ACTION_SKIPPED = "app.openhealth.MEDICATION_SKIPPED"
    const val EXTRA_MEDICATION_ID = "medication_id"
    const val EXTRA_SCHEDULED_FOR = "scheduled_for"

    /** Puts every active medicine's schedule back on the alarm clock. */
    fun rescheduleAll(context: Context) {
        val app = context.applicationContext as? OpenHealthApp ?: return
        val pending = runCatching { app.container.store.clinical.activeMedications() }
            .getOrDefault(emptyList())
        if (!remindersAllowed(context)) {
            cancelAll(context, pending)
            return
        }
        pending.forEach { medication -> schedule(context, medication) }
    }

    /** Schedules the next occurrence of every time on one medicine's schedule. */
    fun schedule(context: Context, medication: Medication) {
        if (!remindersAllowed(context)) {
            medication.schedule.times.forEach { time -> cancel(context, medication.id, time) }
            return
        }
        if (!medication.active || !medication.schedule.reminds) return
        medication.schedule.times.forEach { time ->
            scheduleNext(context, medication.id, time, medication.schedule.daysOfWeek)
        }
    }

    fun cancel(context: Context, medicationId: String, time: String) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        manager.cancel(alarmIntent(context, medicationId, time))
    }

    fun cancelAll(context: Context, medications: List<Medication>) {
        medications.forEach { medication ->
            medication.schedule.times.forEach { time -> cancel(context, medication.id, time) }
        }
    }

    /**
     * The next wall-clock occurrence of [time] on the medicine's days. A timezone change
     * moves the alarm with the clock, which is what "08:00" means to the person.
     */
    @Suppress("ReturnCount")
    fun nextOccurrence(daysOfWeek: List<Int>, now: ZonedDateTime, time: String): ZonedDateTime? {
        val localTime = runCatching { LocalTime.parse(time) }.getOrNull() ?: return null
        var date: LocalDate = now.toLocalDate()
        repeat(DAYS_IN_WEEK + 1) {
            val matchesDay = daysOfWeek.isEmpty() || date.dayOfWeek.iso in daysOfWeek
            val candidate = ZonedDateTime.of(date, localTime, now.zone)
            if (matchesDay && candidate.isAfter(now)) return candidate
            date = date.plusDays(1)
        }
        return null
    }

    private fun scheduleNext(
        context: Context,
        medicationId: String,
        time: String,
        daysOfWeek: List<Int>,
    ) {
        val now = ZonedDateTime.now()
        val next = nextOccurrence(daysOfWeek, now, time) ?: return
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = alarmIntent(context, medicationId, time)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            manager.canScheduleExactAlarms()
        runCatching {
            if (canExact) {
                manager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    next.toInstant().toEpochMilli(),
                    intent,
                )
            } else {
                manager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    next.toInstant().toEpochMilli(),
                    intent,
                )
            }
        }
    }

    private fun alarmIntent(context: Context, medicationId: String, time: String): PendingIntent {
        val intent = Intent(context, MedicationReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            putExtra(EXTRA_MEDICATION_ID, medicationId)
            putExtra("time", time)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(medicationId, time),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun requestCode(medicationId: String, time: String): Int =
        ("$medicationId|$time").hashCode() and REQUEST_CODE_MASK

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.clinical_reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.clinical_reminder_channel_detail)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun notify(context: Context, medication: Medication) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val scheduledFor = System.currentTimeMillis()
        val taken = actionIntent(context, medication.id, ACTION_TAKEN, scheduledFor)
        val skipped = actionIntent(context, medication.id, ACTION_SKIPPED, scheduledFor)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_steps)
            .setContentTitle(context.getString(R.string.clinical_reminder_title))
            .setContentText(
                context.getString(
                    R.string.clinical_reminder_body,
                    medication.name,
                    trim(medication.doseAmount),
                    medication.doseUnit.ucum,
                ),
            )
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(0, context.getString(R.string.clinical_reminder_taken), taken)
            .addAction(0, context.getString(R.string.clinical_reminder_skipped), skipped)
            .build()
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(requestCode(medication.id, "now"), notification)
        }
    }

    private fun actionIntent(
        context: Context,
        medicationId: String,
        action: String,
        scheduledFor: Long,
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode(medicationId, action),
        Intent(context, MedicationReminderReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_MEDICATION_ID, medicationId)
            putExtra(EXTRA_SCHEDULED_FOR, scheduledFor)
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Off in Settings, or the whole reminder surface is off. */
    internal fun remindersAllowed(context: Context): Boolean {
        val app = context.applicationContext as? OpenHealthApp
        val notifications = app?.let {
            runCatching { it.container.preferences.snapshot().notifications }.getOrNull()
        }
        return notifications?.let { it.enabled && it.medication } ?: true
    }

    /** Logs a dose from the notification; the app never assumes the person took it. */
    fun logDose(context: Context, medicationId: String, skipped: Boolean, atMillis: Long) {
        val app = context.applicationContext as? OpenHealthApp ?: return
        val store = app.container.store
        val medication = store.clinical.medication(medicationId) ?: return
        val zone = kotlinx.datetime.TimeZone.currentSystemDefault()
        val at = Instant.fromEpochMilliseconds(atMillis)
        val local = at.toLocalDateTime(zone).date
        store.clinical.saveDose(
            MedicationDose(
                id = java.util.UUID.randomUUID().toString(),
                medicationId = medication.id,
                medicationName = medication.name,
                scheduledFor = at,
                takenAt = if (skipped) null else at,
                skipped = skipped,
                localDate = local,
                notes = null,
                provenance = Provenance(
                    sourceId = SourceId("manual"),
                    time = TimeProvenance.of(at, kotlin.time.Clock.System.now(), zone),
                    idempotencyKey = "dose-${java.util.UUID.randomUUID()}",
                    appVersion = app.container.versionName,
                ),
            ),
        )
        NotificationManagerCompat.from(context).cancel(requestCode(medicationId, "now"))
    }

    private const val DAYS_IN_WEEK = 7
    private const val REQUEST_CODE_MASK = 0x7FFFFFF

    private fun trim(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
}

/** Receives the alarm and the two notification actions. */
class MedicationReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val medicationId = intent.getStringExtra(MedicationReminders.EXTRA_MEDICATION_ID)
            ?: return
        val app = context.applicationContext as? OpenHealthApp ?: return
        when (intent.action) {
            MedicationReminders.ACTION_TAKEN,
            MedicationReminders.ACTION_SKIPPED,
            -> {
                val at = intent.getLongExtra(
                    MedicationReminders.EXTRA_SCHEDULED_FOR,
                    System.currentTimeMillis(),
                )
                val skipped = intent.action == MedicationReminders.ACTION_SKIPPED
                val pending = goAsync()
                Thread({
                    try {
                        runCatching {
                            MedicationReminders.logDose(context, medicationId, skipped, at)
                        }
                    } finally {
                        pending.finish()
                    }
                }, "medication-dose").start()
            }
            MedicationReminders.ACTION_REMIND -> {
                val time = intent.getStringExtra("time").orEmpty()
                val pending = goAsync()
                Thread({
                    try {
                        runCatching {
                            if (!MedicationReminders.remindersAllowed(context)) return@runCatching
                            val medication = app.container.store.clinical
                                .medication(medicationId)
                            if (medication == null || !medication.active) return@runCatching
                            // The alarm was set for a day; a delayed delivery still tells
                            // the person rather than being silently dropped at midnight.
                            MedicationReminders.notify(context, medication)
                            // Put the next occurrence of this time back on the clock.
                            if (time.isNotEmpty()) {
                                MedicationReminders.schedule(context, medication)
                            }
                        }
                    } finally {
                        pending.finish()
                    }
                }, "medication-remind").start()
            }
        }
    }
}

/** Restores the reminder schedule after a reboot or an app update. */
class ClinicalBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_TIME_CHANGED,
            )
        ) {
            return
        }
        val pending = goAsync()
        Thread({
            try {
                runCatching { MedicationReminders.rescheduleAll(context) }
            } finally {
                pending.finish()
            }
        }, "clinical-boot").start()
    }
}

/** `DayOfWeek.iso` is Monday 1 … Sunday 7, the schedule's numbering. */
private val DayOfWeek.iso: Int get() = value
