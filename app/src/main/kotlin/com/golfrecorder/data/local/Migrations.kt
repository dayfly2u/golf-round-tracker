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

/** 워치 연동을 위해 "지금 몇 홀인지"를 라운드 행에 영구 저장한다 — 순수 컬럼 추가라
 * 기존 데이터에는 영향이 없다(기존 행은 전부 기본값 1로 채워짐). */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE rounds ADD COLUMN currentHoleNumber INTEGER NOT NULL DEFAULT 1")
    }
}

/** 숏어프로치(칩)와 퍼팅을 분리 입력하기 위해 ShotPhase에 PUTT을 새로 추가하면서,
 * 기존에 구분 없이 하나로 합쳐 기록하던(phase='SHORT_GAME') 샷/벌타를 전부 PUTT으로
 * 재분류한다 — "숏게임+퍼팅"으로 뭉뚱그려 기록하던 시절엔 실제로는 대부분 퍼팅이었고
 * (칩은 훨씬 드물다), 남은 shots.phase 원본 데이터로 나중에 화면(리뷰 화면, 지도 점
 * 색)이 그대로 다시 계산하므로 hole_records 컬럼만 바꿔서는 이 재분류가 반영되지
 * 않는다 — 실제로 값을 갖는 원본 테이블(shots/penalties)을 고쳐야 한다.
 * hole_records.strokesPutt은 그 위에서 파생되는 값이라 원래 strokesGreenToHoleOut과
 * 그대로 맞춰서 채운다(숏어프로치=0, 퍼팅=기존 합산값). 새로 분리 입력을 쓰기
 * 시작하는 시점부터는 실제 phase가 정확히 나뉘어 기록되므로 이 재분류는 이
 * 마이그레이션 시점의 기존 행에만 적용된다. */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE hole_records ADD COLUMN strokesPutt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE hole_records SET strokesPutt = strokesGreenToHoleOut")
        db.execSQL("UPDATE shots SET phase = 'PUTT' WHERE phase = 'SHORT_GAME'")
        db.execSQL("UPDATE penalties SET phase = 'PUTT' WHERE phase = 'SHORT_GAME'")
    }
}

/** 라운드마다 샷 GPS를 폰 기준/워치 기준 중 무엇으로 기록할지 고정해서 저장한다
 * (카트를 타면 폰이 카트에 남는 경우가 많아 그럴 땐 워치 GPS가 더 정확하다).
 * 순수 컬럼 추가라 기존 데이터에는 영향이 없다 — 기존 라운드는 전부 지금까지와
 * 동일한 'PHONE' 기준으로 채워진다. */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE rounds ADD COLUMN locationSource TEXT NOT NULL DEFAULT 'PHONE'")
    }
}
