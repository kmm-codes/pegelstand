package app.pegelstand.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class WaterDao {

    @Insert
    protected abstract suspend fun insertIntake(intake: IntakeEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertGoalIfAbsent(goal: DayGoalEntity)

    @Upsert
    abstract suspend fun upsertGoal(goal: DayGoalEntity)

    @Query("SELECT * FROM intake WHERE day = :day ORDER BY timestampMs DESC, id DESC LIMIT 1")
    protected abstract suspend fun latestIntakeOfDay(day: Long): IntakeEntity?

    @Query("DELETE FROM intake WHERE id = :id")
    protected abstract suspend fun deleteIntake(id: Long): Int

    /** Writes the intake and, if the day has no stored goal yet, the goal that applies now. */
    @Transaction
    open suspend fun addIntake(intake: IntakeEntity, currentGoalMl: Int): Long {
        val id = insertIntake(intake)
        insertGoalIfAbsent(DayGoalEntity(day = intake.day, goalMl = currentGoalMl))
        return id
    }

    /** Deletes the most recent intake of [day]; returns false if the day has none. */
    @Transaction
    open suspend fun deleteLatestOfDay(day: Long): Boolean {
        val latest = latestIntakeOfDay(day) ?: return false
        return deleteIntake(latest.id) > 0
    }

    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM intake WHERE day = :day")
    abstract fun observeTotalOfDay(day: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM intake WHERE day = :day")
    abstract suspend fun totalOfDay(day: Long): Int

    @Query("SELECT goalMl FROM day_goal WHERE day = :day")
    abstract fun observeGoalOfDay(day: Long): Flow<Int?>

    @Query("SELECT goalMl FROM day_goal WHERE day = :day")
    abstract suspend fun goalOfDay(day: Long): Int?

    @Query("SELECT MAX(timestampMs) FROM intake")
    abstract suspend fun lastIntakeTimestampMs(): Long?

    /** Per-day totals (only days with at least one intake) in [fromDay]..[toDay], ascending. */
    @Query(
        """
        SELECT i.day AS day, SUM(i.amountMl) AS totalMl, g.goalMl AS goalMl
        FROM intake i LEFT JOIN day_goal g ON g.day = i.day
        WHERE i.day BETWEEN :fromDay AND :toDay
        GROUP BY i.day
        ORDER BY i.day
        """
    )
    abstract fun observeDayTotals(fromDay: Long, toDay: Long): Flow<List<DayTotalRow>>

    /** Per-day totals of the whole history, ascending. */
    @Query(
        """
        SELECT i.day AS day, SUM(i.amountMl) AS totalMl, g.goalMl AS goalMl
        FROM intake i LEFT JOIN day_goal g ON g.day = i.day
        GROUP BY i.day
        ORDER BY i.day
        """
    )
    abstract fun observeAllDayTotals(): Flow<List<DayTotalRow>>
}
