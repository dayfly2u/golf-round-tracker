package com.golfrecorder.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * courses에 sortOrder 컬럼만 추가하는 순수 추가형(additive) 마이그레이션 — 코스 관리
 * 화면의 드래그 정렬용. 기존 행은 지금까지 정렬 기준이었던 "이름순, 그다음 id순"을
 * 그대로 유지하도록 sortOrder를 채워서, 마이그레이션 직후 목록 순서가 갑자기 바뀌지
 * 않게 한다(자기보다 이름이 앞서는 행의 개수를 세는 방식 — SQLite에 윈도우 함수 없이도
 * "몇 번째인지"를 구할 수 있다).
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE courses ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            """
            UPDATE courses SET sortOrder = (
                SELECT COUNT(*) FROM courses c2
                WHERE c2.name < courses.name
                   OR (c2.name = courses.name AND c2.id < courses.id)
            )
            """.trimIndent()
        )
    }
}
