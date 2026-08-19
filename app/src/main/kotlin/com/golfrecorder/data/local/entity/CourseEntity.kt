package com.golfrecorder.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val holeCount: Int = 18,
    val createdAt: Long = System.currentTimeMillis(),
    /** 코스 관리 화면에서 드래그로 정한 표시 순서. */
    val sortOrder: Int = 0,
    // 코스 리뷰 — 전부 선택 입력. 교통/클럽하우스/코스는 사용자가 하위 항목
    // (거리·경로, 외관·소품, 티샷·코스·그린 등)을 자유롭게 줄바꿔 적는 자유 텍스트.
    val rating: Double? = null,
    // 1(하)~5(상) 5단계.
    val difficulty: Int? = null,
    val region: String? = null,
    val distance: String? = null,
    val travelTime: String? = null,
    val oneLineReview: String? = null,
    val transportInfo: String? = null,
    val clubhouseInfo: String? = null,
    val courseInfo: String? = null,
)
