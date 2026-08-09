package com.golfrecorder.data.repository

import com.golfrecorder.data.local.dao.CourseDao
import com.golfrecorder.data.local.entity.CourseEntity
import com.golfrecorder.data.local.relation.CourseWithHoles
import kotlinx.coroutines.flow.Flow

class CourseRepository(private val courseDao: CourseDao) {

    fun getCourses(): Flow<List<CourseEntity>> = courseDao.getCourses()

    fun getCourseWithHoles(courseId: Long): Flow<CourseWithHoles?> =
        courseDao.getCourseWithHoles(courseId)

    suspend fun createCourse(name: String, holePars: List<Int>): Long =
        courseDao.insertCourseWithHoles(CourseEntity(name = name, holeCount = holePars.size), holePars)

    suspend fun updateHolePar(holeId: Long, par: Int) =
        courseDao.updateHolePar(holeId, par)

    suspend fun updateCourseName(courseId: Long, name: String) =
        courseDao.updateCourseName(courseId, name)

    suspend fun updateCourseReview(
        courseId: Long,
        rating: Double?,
        difficulty: Int?,
        region: String?,
        distance: String?,
        travelTime: String?,
        oneLineReview: String?,
        transportInfo: String?,
        clubhouseInfo: String?,
        courseInfo: String?,
    ) = courseDao.updateCourseReview(
        courseId, rating, difficulty, region, distance, travelTime, oneLineReview, transportInfo, clubhouseInfo, courseInfo,
    )

    suspend fun updateGreenLocation(holeId: Long, lat: Double, lng: Double) =
        courseDao.updateGreenLocation(holeId, lat, lng)

    suspend fun clearGreenLocation(holeId: Long) =
        courseDao.clearGreenLocation(holeId)

    suspend fun deleteCourse(courseId: Long) =
        courseDao.deleteCourse(courseId)
}
