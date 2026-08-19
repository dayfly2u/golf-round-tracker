package com.golfrecorder.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.golfrecorder.data.local.entity.CourseEntity
import com.golfrecorder.data.local.entity.HoleEntity
import com.golfrecorder.data.local.relation.CourseWithHoles
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {

    @Insert
    suspend fun insertCourse(course: CourseEntity): Long

    @Insert
    suspend fun insertHoles(holes: List<HoleEntity>)

    @Transaction
    suspend fun insertCourseWithHoles(course: CourseEntity, holePars: List<Int>): Long {
        val courseId = insertCourse(course)
        val holes = holePars.mapIndexed { index, par ->
            HoleEntity(courseId = courseId, holeNumber = index + 1, par = par)
        }
        insertHoles(holes)
        return courseId
    }

    @Query("UPDATE holes SET par = :par WHERE id = :holeId")
    suspend fun updateHolePar(holeId: Long, par: Int)

    @Query("UPDATE courses SET name = :name WHERE id = :courseId")
    suspend fun updateCourseName(courseId: Long, name: String)

    @Query(
        """
        UPDATE courses SET
            rating = :rating,
            difficulty = :difficulty,
            region = :region,
            distance = :distance,
            travelTime = :travelTime,
            oneLineReview = :oneLineReview,
            transportInfo = :transportInfo,
            clubhouseInfo = :clubhouseInfo,
            courseInfo = :courseInfo
        WHERE id = :courseId
        """
    )
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
    )

    @Query("UPDATE holes SET greenLat = :lat, greenLng = :lng WHERE id = :holeId")
    suspend fun updateGreenLocation(holeId: Long, lat: Double, lng: Double)

    @Query("UPDATE holes SET greenLat = NULL, greenLng = NULL WHERE id = :holeId")
    suspend fun clearGreenLocation(holeId: Long)

    @Query("SELECT * FROM courses ORDER BY sortOrder ASC, id ASC")
    fun getCourses(): Flow<List<CourseEntity>>

    @Update
    suspend fun updateAll(courses: List<CourseEntity>)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM courses")
    suspend fun getMaxSortOrder(): Int

    @Transaction
    @Query("SELECT * FROM courses WHERE id = :courseId")
    fun getCourseWithHoles(courseId: Long): Flow<CourseWithHoles?>

    @Query("DELETE FROM courses WHERE id = :courseId")
    suspend fun deleteCourse(courseId: Long)
}
