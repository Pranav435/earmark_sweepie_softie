package com.sweepiesoftie.earmark.store

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
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
)

/** Keyed by a salted hash of the conversation, never a name or number. */
@Entity(tableName = "contact_stats")
data class ContactRow(@PrimaryKey val conversation: String, val replies: Int, val medianReplyMinutes: Double)

@Entity(tableName = "kind_stats")
data class KindRow(@PrimaryKey val kind: String, val holds: Int, val ignores: Int, val ignoreStreak: Int)

@Entity(tableName = "alarm_seen")
data class AlarmRow(@PrimaryKey val date: String, val minuteOfDay: Int)

@Dao
interface EarmarkDao {
    @Upsert suspend fun upsertLoop(row: LoopRow)

    @Query("SELECT * FROM loop_log WHERE guessedAt IS NOT NULL AND guessedAt >= :since ORDER BY guessedAt DESC")
    fun things(since: Long): Flow<List<LoopRow>>

    @Query("UPDATE loop_log SET label = NULL WHERE openedAt < :before")
    suspend fun trimLabels(before: Long)

    @Query("DELETE FROM loop_log WHERE openedAt < :before")
    suspend fun deleteLoopsBefore(before: Long)

    @Upsert suspend fun upsertContact(row: ContactRow)

    @Upsert suspend fun upsertKind(row: KindRow)

    @Upsert suspend fun upsertAlarm(row: AlarmRow)

    @Query("SELECT * FROM contact_stats") suspend fun contacts(): List<ContactRow>

    @Query("SELECT * FROM kind_stats") suspend fun kinds(): List<KindRow>

    @Query("SELECT * FROM kind_stats") fun kindsFlow(): Flow<List<KindRow>>

    @Query("SELECT * FROM alarm_seen ORDER BY date DESC LIMIT 10") suspend fun recentAlarms(): List<AlarmRow>
}

@Database(entities = [LoopRow::class, ContactRow::class, KindRow::class, AlarmRow::class], version = 1, exportSchema = false)
abstract class EarmarkDatabase : RoomDatabase() {
    abstract fun dao(): EarmarkDao
}

/** Room behind a small interface. Every call is wrapped here so a database failure is logged and skipped, never thrown. */
class Store(context: Context, private val log: DebugLog) {
    private val dao = Room.databaseBuilder(context, EarmarkDatabase::class.java, "earmark.db").build().dao()

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
            ),
        )
    }

    suspend fun record(kind: LoopKind, stat: KindStat): Unit? = guard("record kind") {
        dao.upsertKind(KindRow(kind.name, stat.holds, stat.ignores, stat.ignoreStreak))
    }

    suspend fun record(conversation: String, stat: ContactStat): Unit? = guard("record contact") {
        dao.upsertContact(ContactRow(conversation, stat.replies, stat.medianReplyMinutes))
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
            contacts = dao.contacts().associate { it.conversation to ContactStat(it.replies, it.medianReplyMinutes) },
            weeknightAlarms = dao.recentAlarms().map { LocalTime.of(it.minuteOfDay / 60, it.minuteOfDay % 60) },
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

        fun hashId(id: String): String =
            MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)

        fun kindOf(name: String): LoopKind? = LoopKind.entries.firstOrNull { it.name == name }

        fun outcomeOf(name: String?): Outcome? = Outcome.entries.firstOrNull { it.name == name }

    }
}
