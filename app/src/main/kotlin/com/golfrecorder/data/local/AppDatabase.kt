package com.golfrecorder.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.golfrecorder.data.local.dao.CourseDao
import com.golfrecorder.data.local.dao.RoundDao
import com.golfrecorder.data.local.dao.ShotDao
import com.golfrecorder.data.local.entity.CourseEntity
import com.golfrecorder.data.local.entity.HoleEntity
import com.golfrecorder.data.local.entity.HoleRecordEntity
import com.golfrecorder.data.local.entity.RoundEntity
import com.golfrecorder.data.local.entity.ShotEntity

@Database(
    entities = [
        CourseEntity::class,
        HoleEntity::class,
        RoundEntity::class,
        HoleRecordEntity::class,
        ShotEntity::class,
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun courseDao(): CourseDao
    abstract fun roundDao(): RoundDao
    abstract fun shotDao(): ShotDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context, AppDatabase::class.java, "golf_recorder.db")
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
