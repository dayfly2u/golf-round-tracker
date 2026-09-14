package com.golfrecorder.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rounds",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            // 코스를 지워도 이미 기록된 라운드(스코어, 샷 GPS)는 남긴다 — 코스는
            // "다음 라운드를 시작할 때 고르는 목록"일 뿐이고, 지난 기록은 코스
            // 존재 여부와 무관하게 값어치가 있는 데이터라서다.
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["courseId"])]
)
data class RoundEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long?,
    // 라운드 시작 시점의 코스 이름을 스냅샷으로 저장한다. 코스가 삭제돼도
    // (courseId가 null이 돼도) "어느 코스였는지"는 계속 보여줄 수 있게.
    val courseName: String,
    val playedAt: Long,
    val finishedAt: Long? = null,
    // 라운딩 리뷰 — 전부 선택 입력.
    val price: Int? = null,
    val companions: String? = null,
    val review: String? = null,
    /** 워치 연동/화면 재진입 시 "지금 몇 홀인지" 복원용. 라운드 생성 시 1로 시작. */
    val currentHoleNumber: Int = 1,
    /** LocationSource.name ("PHONE"/"WATCH") — 이 라운드의 샷 GPS를 어느 기기
     * 기준으로 기록할지. 코스 선택 화면에서 라운드 시작 시 한 번 정하면 고정된다. */
    val locationSource: String = "PHONE",
    /** MapProvider.name ("KAKAO"/"GOOGLE") — 이 라운드 지도를 어느 프로바이더로
     * 그릴지. 코스 선택 화면에서 라운드 시작 시 한 번 정하면 고정된다(리뷰할 때도
     * 같은 값을 다시 읽어서 그 프로바이더로 그린다). */
    val mapProvider: String = "KAKAO",
)
