package com.sweepiesoftie.earmark.store

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sweepiesoftie.earmark.core.AppStat
import com.sweepiesoftie.earmark.DebugLog
import com.sweepiesoftie.earmark.core.ContactStat
import com.sweepiesoftie.earmark.core.KindStat
import com.sweepiesoftie.earmark.core.LoopEntry
import com.sweepiesoftie.earmark.core.LoopKind
import com.sweepiesoftie.earmark.core.Outcome
import com.sweepiesoftie.earmark.core.Stats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** One recap row. No message text: the label is the task ("Pay Aisha back $12") and is cleared after 24 h (ADR 0004). */
@Entity(tableName = "loop_log")
data class LoopRow(
    @PrimaryKey val loopId: String,
    val kind: String,
    val label: String?,
    val openedAt: Long,
    val guessedAt: Long?,
    val closedAt: Long?,
    val outcome: String?,
    val broughtBack: Boolean,
    /** On today's list (ADR 0025): decided by the engine, so a swiped chip for a flow stays off it. */
    @ColumnInfo(defaultValue = "0") val listed: Boolean,
)

/** Keyed by a salted hash of the conversation, never a name or number. Opens and swipes are counts of taps on its notifications (ADR 0028). */
@Entity(tableName = "contact_stats")
data class ContactRow(
    @PrimaryKey val conversation: String,
    val replies: Int,
    val medianReplyMinutes: Double,
    @ColumnInfo(defaultValue = "0") val opened: Int,
    @ColumnInfo(defaultValue = "0") val swiped: Int,
)

@Entity(tableName = "kind_stats")
data class KindRow(@PrimaryKey val kind: String, val holds: Int, val ignores: Int, val ignoreStreak: Int)

@Entity(tableName = "alarm_seen")
data class AlarmRow(@PrimaryKey val date: String, val minuteOfDay: Int)

/**
 * Per app: your usual visit to a feed app, for the smart drift timer (ADR 0023), and how many of its messages you
 * dealt with or left and how soon (ADR 0027). A package name and numbers, nothing else.
 */
@Entity(tableName = "app_stats")
data class AppRow(
    @PrimaryKey val packageName: String,
    val visits: Int,
    val medianVisitSeconds: Double,
    @ColumnInfo(defaultValue = "0") val done: Int,
    @ColumnInfo(defaultValue = "0") val left: Int,
    @ColumnInfo(defaultValue = "0") val medianDoneMinutes: Double,
)

@Dao
interface EarmarkDao {
    @Upsert suspend fun upsertLoop(row: LoopRow)

    @Query("SELECT * FROM loop_log WHERE listed = 1 AND (guessedAt >= :since OR openedAt >= :since) ORDER BY COALESCE(guessedAt, openedAt) DESC")
    fun things(since: Long): Flow<List<LoopRow>>

    @Query("UPDATE loop_log SET label = NULL WHERE openedAt < :before")
    suspend fun trimLabels(before: Long)

    @Query("DELETE FROM loop_log WHERE openedAt < :before")
    suspend fun deleteLoopsBefore(before: Long)

    @Upsert suspend fun upsertContact(row: ContactRow)

    @Upsert suspend fun upsertKind(row: KindRow)

    @Upsert suspend fun upsertAlarm(row: AlarmRow)

    @Upsert suspend fun upsertApp(row: AppRow)

    @Query("SELECT * FROM contact_stats") suspend fun contacts(): List<ContactRow>

    @Query("SELECT * FROM kind_stats") suspend fun kinds(): List<KindRow>

    @Query("SELECT * FROM kind_stats") fun kindsFlow(): Flow<List<KindRow>>

    @Query("SELECT * FROM alarm_seen ORDER BY date DESC LIMIT 10") suspend fun recentAlarms(): List<AlarmRow>

    @Query("SELECT * FROM app_stats") suspend fun apps(): List<AppRow>
}

@Database(entities = [LoopRow::class, ContactRow::class, KindRow::class, AlarmRow::class, AppRow::class], version = 4, exportSchema = false)
abstract class EarmarkDatabase : RoomDatabase() {
    abstract fun dao(): EarmarkDao
}

/** Room behind a small interface. Every call is wrapped here so a database failure is logged and skipped, never thrown. */
class Store(context: Context, private val log: DebugLog) {
    private val dao = Room.databaseBuilder(context, EarmarkDatabase::class.java, "earmark.db").addMigrations(ADD_APP_STATS, LIST_PICKED_UP, LEARN_OPENS_AND_APPS).build().dao()

    fun things(since: Long): Flow<List<LoopRow>> = dao.things(since).catch { log.error("Store.things", it); emit(emptyList()) }

    fun mutedKinds(): Flow<List<LoopKind>> = dao.kindsFlow()
        .map { rows -> rows.filter { it.ignoreStreak >= KindStat.MUTE_AFTER_IGNORES }.mapNotNull { kindOf(it.kind) } }
        .catch { log.error("Store.mutedKinds", it); emit(emptyList()) }

    suspend fun record(entry: LoopEntry): Unit? = guard("record loop") {
        dao.upsertLoop(
            LoopRow(
                // Loop ids can carry a notification tag such as a phone number, so only a hash is stored.
                loopId = hashId(entry.loopId.value),
                kind = entry.kind.name,
                // A late update must not bring back a label the 24-hour trim already cleared.
                label = entry.label.takeIf { System.currentTimeMillis() - entry.openedAt.toEpochMilli() < DAY_MILLIS },
                openedAt = entry.openedAt.toEpochMilli(),
                guessedAt = entry.guessedAt?.toEpochMilli(),
                closedAt = entry.closedAt?.toEpochMilli(),
                outcome = entry.outcome?.name,
                broughtBack = entry.broughtBack,
                listed = entry.listed,
            ),
        )
    }

    suspend fun record(kind: LoopKind, stat: KindStat): Unit? = guard("record kind") {
        dao.upsertKind(KindRow(kind.name, stat.holds, stat.ignores, stat.ignoreStreak))
    }

    suspend fun record(conversation: String, stat: ContactStat): Unit? = guard("record contact") {
        dao.upsertContact(ContactRow(conversation, stat.replies, stat.medianReplyMinutes, stat.opened, stat.swiped))
    }

    suspend fun recordApp(packageName: String, stat: AppStat): Unit? = guard("record app") {
        dao.upsertApp(AppRow(packageName, stat.visits, stat.medianVisitSeconds, stat.done, stat.left, stat.medianDoneMinutes))
    }

    suspend fun recordAlarm(time: LocalTime, on: LocalDate): Unit? = guard("record alarm") {
        dao.upsertAlarm(AlarmRow(on.toString(), time.hour * 60 + time.minute))
    }

    /** Labels older than 24 h become kind + time; rows older than a week go. */
    suspend fun trim(nowMillis: Long): Unit? = guard("trim") {
        dao.trimLabels(nowMillis - DAY_MILLIS)
        dao.deleteLoopsBefore(nowMillis - 7 * DAY_MILLIS)
    }

    suspend fun stats(): Stats = guard("load stats") {
        Stats(
            kinds = dao.kinds().mapNotNull { row -> kindOf(row.kind)?.let { it to KindStat(row.holds, row.ignores, row.ignoreStreak) } }.toMap(),
            contacts = dao.contacts().associate { it.conversation to ContactStat(it.replies, it.medianReplyMinutes, it.opened, it.swiped) },
            weeknightAlarms = dao.recentAlarms().map { LocalTime.of(it.minuteOfDay / 60, it.minuteOfDay % 60) },
            apps = dao.apps().associate { it.packageName to AppStat(it.visits, it.medianVisitSeconds, it.done, it.left, it.medianDoneMinutes) },
        )
    } ?: Stats()

    /** Demo mode: five weeknights of a 6:30 alarm, so the Bedtime story has a routine to find. */
    suspend fun seedAlarmHistory(zone: ZoneId): Unit? = guard("seed alarms") {
        var day = LocalDate.now(zone)
        var seeded = 0
        while (seeded < 5) {
            day = day.minusDays(1)
            if (day.dayOfWeek.value <= 5) {
                dao.upsertAlarm(AlarmRow(day.toString(), 6 * 60 + 30))
                seeded++
            }
        }
    }

    private suspend fun <T> guard(what: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.error("Store.$what", e)
        null
    }

    companion object {
        const val DAY_MILLIS: Long = 24 * 60 * 60 * 1000L

        /** v0.1.8 adds the usual feed visits; everything already stored stays. */
        val ADD_APP_STATS: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `app_stats` (`packageName` TEXT NOT NULL, `visits` INTEGER NOT NULL, `medianVisitSeconds` REAL NOT NULL, PRIMARY KEY(`packageName`))")
            }
        }

        /** v0.1.10 lists picked-up messages as they arrive; everything already on the list stays there. */
        val LIST_PICKED_UP: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `loop_log` ADD COLUMN `listed` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `loop_log` SET `listed` = 1 WHERE `guessedAt` IS NOT NULL")
            }
        }

        /** v0.1.12 learns taps on each conversation's notifications and how you deal with each app's messages; counts start at zero. */
        val LEARN_OPENS_AND_APPS: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `contact_stats` ADD COLUMN `opened` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `contact_stats` ADD COLUMN `swiped` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `app_stats` ADD COLUMN `done` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `app_stats` ADD COLUMN `left` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `app_stats` ADD COLUMN `medianDoneMinutes` REAL NOT NULL DEFAULT 0")
            }
        }

        fun hashId(id: String): String =
            MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)

        fun kindOf(name: String): LoopKind? = LoopKind.entries.firstOrNull { it.name == name }

        fun outcomeOf(name: String?): Outcome? = Outcome.entries.firstOrNull { it.name == name }

    }
}
