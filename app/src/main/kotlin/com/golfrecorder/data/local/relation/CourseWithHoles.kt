package com.golfrecorder.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.golfrecorder.data.local.entity.CourseEntity
import com.golfrecorder.data.local.entity.HoleEntity

data class CourseWithHoles(
    @Embedded val course: CourseEntity,
    @Relation(parentColumn = "id", entityColumn = "courseId")
    val holes: List<HoleEntity>
)
