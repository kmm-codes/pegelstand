package app.pegelstand.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [IntakeEntity::class, DayGoalEntity::class], version = 1, exportSchema = true)
abstract class WaterDatabase : RoomDatabase() {
    abstract fun waterDao(): WaterDao

    companion object {
        fun create(context: Context): WaterDatabase =
            Room.databaseBuilder(context.applicationContext, WaterDatabase::class.java, "pegelstand.db").build()
    }
}
