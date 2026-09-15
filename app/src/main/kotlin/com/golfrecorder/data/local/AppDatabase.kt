package com.golfrecorder.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.golfrecorder.data.local.dao.CourseDao
import com.golfrecorder.data.local.dao.CourseYoutubeLinkDao
import com.golfrecorder.data.local.dao.PenaltyDao
import com.golfrecorder.data.local.dao.RoundDao
import com.golfrecorder.data.local.dao.ShotDao
import com.golfrecorder.data.local.entity.CourseEntity
import com.golfrecorder.data.local.entity.CourseYoutubeLinkEntity
import com.golfrecorder.data.local.entity.HoleEntity
import com.golfrecorder.data.local.entity.HoleRecordEntity
import com.golfrecorder.data.local.entity.PenaltyEntity
import com.golfrecorder.data.local.entity.RoundEntity
import com.golfrecorder.data.local.entity.ShotEntity

@Database(
    entities = [
        CourseEntity::class,
        HoleEntity::class,
        RoundEntity::class,
        HoleRecordEntity::class,
        ShotEntity::class,
        PenaltyEntity::class,
        CourseYoutubeLinkEntity::class,
    ],
    version = 19,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun courseDao(): CourseDao
    abstract fun roundDao(): RoundDao
    abstract fun shotDao(): ShotDao
    abstract fun penaltyDao(): PenaltyDao
    abstract fun courseYoutubeLinkDao(): CourseYoutubeLinkDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context, AppDatabase::class.java, "golf_recorder.db")
                    .addMigrations(
                        MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15,
                        MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19,
                    )
                    .build()
                    .also { instance = it }
            }

        /** 백업/복원 시 DB 파일을 직접 다루기 전에 열려 있는 연결을 닫는다 —
         * 다음 [getInstance] 호출에서 새로 열린다. */
        fun closeInstance() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }
    }
}
