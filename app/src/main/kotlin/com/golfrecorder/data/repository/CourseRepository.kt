package com.golfrecorder.data.repository

import com.golfrecorder.data.local.dao.CourseDao
import com.golfrecorder.data.local.entity.CourseEntity
import com.golfrecorder.data.local.relation.CourseWithHoles
import kotlinx.coroutines.flow.Flow

class CourseRepository(private val courseDao: CourseDao) {

    fun getCourses(): Flow<List<CourseEntity>> = courseDao.getCourses()

    fun getCourseWithHoles(courseId: Long): Flow<CourseWithHoles?> =
        courseDao.getCourseWithHoles(courseId)

    suspend fun createCourse(name: String, holePars: List<Int>): Long {
        val nextSortOrder = courseDao.getMaxSortOrder() + 1
        val course = CourseEntity(name = name, holeCount = holePars.size, sortOrder = nextSortOrder)
        return courseDao.insertCourseWithHoles(course, holePars)
    }

    /** [orderedCourses]가 화면에 보여줄 새 순서 — 각 항목의 sortOrder를 그 위치(index)로 다시 매겨서 저장한다. */
    suspend fun reorder(orderedCourses: List<CourseEntity>) =
        courseDao.updateAll(orderedCourses.mapIndexed { index, course -> course.copy(sortOrder = index) })

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
