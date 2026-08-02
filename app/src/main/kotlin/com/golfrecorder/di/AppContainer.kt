package com.golfrecorder.di

import android.content.Context
import com.golfrecorder.data.local.AppDatabase
import com.golfrecorder.data.repository.CourseRepository
import com.golfrecorder.data.repository.PenaltyRepository
import com.golfrecorder.data.repository.RoundRepository
import com.golfrecorder.data.repository.ShotRepository

/**
 * 수동으로 짜는 최소 DI 컨테이너. 의존성이 DB와 리포지토리 2개뿐이라
 * Hilt/KSP 조합을 하나 더 얹을 이유가 없다 (kstock-digest/android와 동일한 판단).
 */
class AppContainer(context: Context) {

    val database: AppDatabase = AppDatabase.getInstance(context)

    val courseRepository: CourseRepository = CourseRepository(database.courseDao())

    val roundRepository: RoundRepository = RoundRepository(database.roundDao())

    val shotRepository: ShotRepository = ShotRepository(database.shotDao())

    val penaltyRepository: PenaltyRepository = PenaltyRepository(database.penaltyDao())

    companion object {
        @Volatile private var instance: AppContainer? = null

        fun getInstance(context: Context): AppContainer =
            instance ?: synchronized(this) {
                instance ?: AppContainer(context.applicationContext).also { instance = it }
            }
    }
}
