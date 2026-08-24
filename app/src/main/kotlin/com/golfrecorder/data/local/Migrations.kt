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

/** 코스별 유튜브 링크(라운딩 영상 등) 저장용 테이블 신설 — 새 테이블 추가만 하는
 * 순수 추가형(additive) 마이그레이션이라 기존 데이터에는 영향이 없다. */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `course_youtube_links` (
                `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                `courseId` INTEGER NOT NULL,
                `url` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `title` TEXT,
                `thumbnailUrl` TEXT,
                `channelTitle` TEXT,
                `addedAt` INTEGER NOT NULL,
                FOREIGN KEY(`courseId`) REFERENCES `courses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_course_youtube_links_courseId` ON `course_youtube_links` (`courseId`)"
        )
    }
}

/** 유튜브 링크 카테고리를 "라운딩"/"코스" 2개로 단순화하면서 기존에 저장된 값 중
 * 없어지는 카테고리(COURSE_INTRO, LESSON, OTHER)를 남겨두면 화면 필터에서 아예
 * 안 보이게 되므로, 새 카테고리로 다시 매핑해서 기존 링크가 계속 보이게 한다. */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE course_youtube_links SET category = 'COURSE' WHERE category = 'COURSE_INTRO'")
        db.execSQL("UPDATE course_youtube_links SET category = 'ROUND' WHERE category IN ('LESSON', 'OTHER')")
    }
}
