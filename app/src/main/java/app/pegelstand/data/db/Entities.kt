package app.pegelstand.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One tap. [day] is the logical day (epoch day) computed when the intake was written. */
@Entity(tableName = "intake", indices = [Index("day")])
data class IntakeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val day: Long,
    val amountMl: Int,
)

/** The goal that applied on [day] (epoch day). */
@Entity(tableName = "day_goal")
data class DayGoalEntity(
    @PrimaryKey val day: Long,
    val goalMl: Int,
)

/** Result row of the day-totals query. [goalMl] is null if no goal was stored for that day. */
data class DayTotalRow(
    @ColumnInfo(name = "day") val day: Long,
    @ColumnInfo(name = "totalMl") val totalMl: Int,
    @ColumnInfo(name = "goalMl") val goalMl: Int?,
)
