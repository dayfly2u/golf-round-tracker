# Galaxy Watch5 컴패니언 앱 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 갤럭시 워치5(SM-R900, Wear OS)에서 그린까지 타수/숏게임 타수 증감과 홀 이동을 할 수 있게 만들어서, 폰이 백그라운드/화면 꺼짐 상태여도 라운드 기록이 계속되게 한다.

**Architecture:** 폰에 포그라운드 서비스(`RoundRecordingService`)가 라운드 시작부터 완료/취소까지 떠 있으면서 워치의 `MessageClient` 명령(타수 증감 4종 + 홀 이동 2종)을 받아 기존 `ShotRepository`/`RoundRepository`/`LocationCapture`로 처리하고, `DataClient`로 최신 상태를 워치에 되돌려준다. 워치는 별도 Wear OS 앱(`:wear` 모듈)으로, 상태를 받아 보여주고 버튼 입력만 명령으로 보내는 얇은 클라이언트다. GPS는 항상 폰이 그 순간 조회한다.

**Tech Stack:** Kotlin, Jetpack Compose, Room, `com.google.android.gms:play-services-wearable` (Data Layer: `MessageClient`/`DataClient`/`NodeClient`), `androidx.wear.compose` (워치 UI), 기존 수동 DI(`AppContainer`).

## Global Constraints

- DB 마이그레이션은 항상 명시적 `Migration` 객체 + 추가형(additive) SQL만 사용한다 (`fallbackToDestructiveMigration` 금지) — `golf-round-tracker/CLAUDE.md`의 필수 정책, 실기기에 실제 라운딩 데이터가 있음.
- 마이그레이션 작성 후에는 반드시 adb로 실기기 DB를 직접 pull해서 스키마 버전과 값이 기대대로인지 확인한다 — 앱 재설치로 우회 검증하지 않는다.
- 커밋마다 `app/build.gradle.kts`의 `versionName` PATCH를 1 올리고 커밋 제목 끝에 `(vX.Y.Z)`를 붙인다 (문서 전용 커밋은 제외). `versionCode`는 태그 찍을 때만 올린다.
- 화면 단위 동작 버튼은 `TopAppBar.actions`(주요 액션) 또는 `bottomBar`(보조/저빈도 액션, 작은 스타일)에만 둔다. 삭제 버튼은 확인 다이얼로그를 반드시 거친다. (`C:\github\CLAUDE.md` 공통 규칙 — 이번 플랜은 새 화면/버튼이 거의 없어 직접 해당하는 곳은 적지만, 워치 쪽 화면 하나는 이 규칙 대상이 아님— 워치는 폰과 다른 UI 컨벤션을 쓰는 별도 플랫폼이라 이 규칙을 적용하지 않는다.)
- 시각적 확인(워치 화면 레이아웃, 지도 등)이 필요한 부분은 Claude가 스스로 조작해서 스크린샷으로 판단하지 않고, 사용자에게 "무엇을 눌러서 무엇을 확인해달라"고 요청한다. adb는 APK 설치/실행/로그 확인/DB 직접 조회 용도로만 쓴다.

---

## 파일 구조 개요

**폰 쪽 (`:app` 모듈) 새 파일:**
- `app/src/main/kotlin/com/golfrecorder/domain/model/StrokeCalculator.kt` — 순수 함수, 샷·벌타 테이블에서 현재 타수를 계산.
- `app/src/main/kotlin/com/golfrecorder/wearsync/WearSync.kt` — 워치와 주고받는 메시지 경로/키 상수 (워치 모듈에도 동일한 내용 복제).
- `app/src/main/kotlin/com/golfrecorder/service/RoundRecordingService.kt` — 포그라운드 서비스, 워치 명령 처리 + 상태 전송.

**폰 쪽 수정 파일:**
- `RoundEntity.kt`, `Migrations.kt`, `AppDatabase.kt`, `RoundDao.kt`, `RoundRepository.kt` — `currentHoleNumber` 컬럼.
- `RoundPlayViewModel.kt`(`RoundPlayScreen.kt` 안) — 홀 진입 시 타수 시드를 `StrokeCalculator`로 계산, 홀 이동 시 DB에 write-through.
- `di/AppContainer.kt` — 변경 없음(서비스가 직접 `AppContainer.getInstance`를 씀).
- `MainActivity.kt` — 라운드 생성/완료/취소 시 서비스 시작·종료 호출.
- `app/src/main/AndroidManifest.xml` — 서비스 등록, 포그라운드 서비스 권한.
- `app/build.gradle.kts`, `gradle/libs.versions.toml` — `play-services-wearable`, `kotlinx-coroutines-play-services` 의존성 추가.
- `settings.gradle.kts` — `:wear` 모듈 include.

**새 `:wear` 모듈:**
- `wear/build.gradle.kts`, `wear/src/main/AndroidManifest.xml`
- `wear/src/main/kotlin/com/golfrecorder/wearsync/WearSync.kt` (폰 쪽과 내용 동일, 반드시 같이 수정)
- `wear/src/main/kotlin/com/golfrecorder/wear/RoundStateViewModel.kt`
- `wear/src/main/kotlin/com/golfrecorder/wear/MainActivity.kt`

**검증 방식**: 이 프로젝트는 Compose UI/Room 마이그레이션/포그라운드 서비스처럼 Android 프레임워크에 강하게 묶인 부분을 로컬 유닛 테스트로 검증하는 인프라(Robolectric, Room `MigrationTestHelper` 등)가 없다 — 지금까지 전부 "빌드 성공 → adb 설치 → 실기기에서 사용자가 확인" 방식으로 검증해왔다(이번 세션의 다른 모든 작업과 동일). 이 플랜도 그 컨벤션을 그대로 따른다: 순수 Kotlin 로직(`StrokeCalculator`)만 진짜 유닛 테스트로 TDD하고, 나머지는 빌드+설치+실기기 확인으로 검증한다.

---

### Task 1: `rounds` 테이블에 `currentHoleNumber` 컬럼 추가

**Files:**
- Modify: `app/src/main/kotlin/com/golfrecorder/data/local/entity/RoundEntity.kt`
- Modify: `app/src/main/kotlin/com/golfrecorder/data/local/Migrations.kt`
- Modify: `app/src/main/kotlin/com/golfrecorder/data/local/AppDatabase.kt`
- Modify: `app/src/main/kotlin/com/golfrecorder/data/local/dao/RoundDao.kt`
- Modify: `app/src/main/kotlin/com/golfrecorder/data/repository/RoundRepository.kt`
- Modify: `.gitignore` (검증 단계에서 DB를 pull할 임시 폴더 `.tmp/` 제외)

**Interfaces:**
- Produces: `RoundRepository.getCurrentHoleNumber(roundId: Long): Int?`, `RoundRepository.updateCurrentHoleNumber(roundId: Long, holeNumber: Int)` — Task 3(ViewModel)과 Task 5(서비스)가 둘 다 이걸 쓴다.

- [ ] **Step 1: `RoundEntity`에 필드 추가**

`app/src/main/kotlin/com/golfrecorder/data/local/entity/RoundEntity.kt`의 마지막 필드(`review`) 다음에 추가:

```kotlin
    val review: String? = null,
    /** 워치 연동/화면 재진입 시 "지금 몇 홀인지" 복원용. 라운드 생성 시 1로 시작. */
    val currentHoleNumber: Int = 1,
```

- [ ] **Step 2: 마이그레이션 작성**

`app/src/main/kotlin/com/golfrecorder/data/local/Migrations.kt` 맨 끝에 추가:

```kotlin
/** 워치 연동을 위해 "지금 몇 홀인지"를 라운드 행에 영구 저장한다 — 순수 컬럼 추가라
 * 기존 데이터에는 영향이 없다(기존 행은 전부 기본값 1로 채워짐). */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE rounds ADD COLUMN currentHoleNumber INTEGER NOT NULL DEFAULT 1")
    }
}
```

- [ ] **Step 3: `AppDatabase` 버전 올리고 마이그레이션 등록**

`app/src/main/kotlin/com/golfrecorder/data/local/AppDatabase.kt`:

```kotlin
    version = 15,
```

```kotlin
                instance ?: Room.databaseBuilder(context, AppDatabase::class.java, "golf_recorder.db")
                    .addMigrations(MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
                    .build()
                    .also { instance = it }
```

- [ ] **Step 4: `RoundDao`에 조회/갱신 쿼리 추가**

`app/src/main/kotlin/com/golfrecorder/data/local/dao/RoundDao.kt`의 `countRoundsForCourse` 다음에 추가:

```kotlin
    @Query("SELECT currentHoleNumber FROM rounds WHERE id = :roundId")
    suspend fun getCurrentHoleNumber(roundId: Long): Int?

    @Query("UPDATE rounds SET currentHoleNumber = :holeNumber WHERE id = :roundId")
    suspend fun updateCurrentHoleNumber(roundId: Long, holeNumber: Int)
```

- [ ] **Step 5: `RoundRepository`에 패스스루 추가**

`app/src/main/kotlin/com/golfrecorder/data/repository/RoundRepository.kt`의 `countRoundsForCourse` 다음에 추가:

```kotlin
    suspend fun getCurrentHoleNumber(roundId: Long): Int? = roundDao.getCurrentHoleNumber(roundId)

    suspend fun updateCurrentHoleNumber(roundId: Long, holeNumber: Int) =
        roundDao.updateCurrentHoleNumber(roundId, holeNumber)
```

- [ ] **Step 6: `.gitignore`에 검증용 임시 폴더 추가**

`.gitignore`의 `app/schemas/` 다음 줄에 추가:

```
wear/build/
/.tmp/
```

(`wear/build/`는 아직 `:wear` 모듈이 없어서 지금 당장 의미는 없지만, Task 7에서 모듈이 생기기 전에 미리 추가해도 무해하다.)

- [ ] **Step 7: 빌드**

Run: `./gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: 실기기에 설치하고 마이그레이션 실제 동작 확인 (adb로 DB 직접 조회, 화면 안 봄)**

adb 실행 파일 경로가 PATH에 없을 수 있으니 먼저 찾는다 (Windows 기준):

```bash
ADB="$(where adb 2>/dev/null | head -1)"
if [ -z "$ADB" ]; then ADB="/c/Users/dayfl/AppData/Local/Android/Sdk/platform-tools/adb.exe"; fi
```

설치하고 앱을 한 번 실행해서 마이그레이션이 실제로 돌게 한 뒤:

```bash
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" shell am start -n com.golfrecorder/.MainActivity
sleep 3
```

DB 파일을 프로젝트 안의 임시 폴더(Windows 네이티브 python이 바로 읽을 수 있는 경로)로 pull한다 — Git Bash의 `/tmp` 경로는 Windows python이 못 읽으므로 쓰지 않는다:

```bash
mkdir -p .tmp
MSYS_NO_PATHCONV=1 "$ADB" exec-out run-as com.golfrecorder cat /data/data/com.golfrecorder/databases/golf_recorder.db > .tmp/golf.db
MSYS_NO_PATHCONV=1 "$ADB" exec-out run-as com.golfrecorder cat /data/data/com.golfrecorder/databases/golf_recorder.db-wal > .tmp/golf.db-wal
python -c "
import sqlite3
con = sqlite3.connect('.tmp/golf.db')
print('user_version:', con.execute('PRAGMA user_version').fetchone())
print(con.execute('SELECT id, currentHoleNumber FROM rounds').fetchall())
"
```

Expected: `user_version`이 `(15,)`이고, 기존에 있던 모든 라운드 행의 `currentHoleNumber`가 `1`.

- [ ] **Step 9: 커밋**

```bash
git add app/src/main/kotlin/com/golfrecorder/data/local/entity/RoundEntity.kt app/src/main/kotlin/com/golfrecorder/data/local/Migrations.kt app/src/main/kotlin/com/golfrecorder/data/local/AppDatabase.kt app/src/main/kotlin/com/golfrecorder/data/local/dao/RoundDao.kt app/src/main/kotlin/com/golfrecorder/data/repository/RoundRepository.kt app/build.gradle.kts .gitignore
git commit -m "Add currentHoleNumber column to rounds for watch sync (vX.Y.Z)"
```

(버전 번호는 실제 작업 시점의 `app/build.gradle.kts` 현재 값 기준으로 PATCH+1 — 워크스페이스 규칙대로 다음 커밋까지 이어지는 uncommitted 배치면 같은 번호를 재사용해도 됨.)

---

### Task 2: 타수 계산 순수 함수 (`StrokeCalculator`) — TDD

**Files:**
- Create: `app/src/main/kotlin/com/golfrecorder/domain/model/StrokeCalculator.kt`
- Test: `app/src/test/kotlin/com/golfrecorder/domain/model/StrokeCalculatorTest.kt`

**Interfaces:**
- Consumes: `ShotEntity(phase: String, shotIndex: Int, ...)`, `PenaltyEntity(phase: String, penaltyIndex: Int, ...)` (이미 존재하는 엔티티, 필드명 그대로).
- Produces: `StrokeCalculator.currentTotal(shots: List<ShotEntity>, penalties: List<PenaltyEntity>, phase: ShotPhase): Int` — Task 3, Task 5가 둘 다 이 함수로 "지금 이 홀의 타수"를 계산한다.

**배경**: `ShotEntity.shotIndex`와 `PenaltyEntity.penaltyIndex`는 둘 다 "그 이벤트가 일어난 시점의 그 구간(phase) 누적 타수"를 그대로 저장한 값이다(`RoundPlayViewModel.addPenalty`가 `penaltyIndex` 자리에 갱신된 총 타수를 넘김 — `RoundPlayScreen.kt:194-206` 참고). 그래서 OB/해저드처럼 `shots` 테이블에 행이 안 생기는 이벤트도 `penaltyIndex`에 그 시점 총 타수가 남는다. 즉 특정 홀·구간의 "지금 타수"는 두 테이블의 index 중 더 큰 값이다.

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
package com.golfrecorder.domain.model

import com.golfrecorder.data.local.entity.PenaltyEntity
import com.golfrecorder.data.local.entity.ShotEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class StrokeCalculatorTest {

    private fun shot(phase: ShotPhase, shotIndex: Int) = ShotEntity(
        roundId = 1, holeNumber = 1, phase = phase.name, shotIndex = shotIndex,
        lat = 0.0, lng = 0.0, capturedAt = 0L,
    )

    private fun penalty(phase: ShotPhase, type: PenaltyType, penaltyIndex: Int, strokeCount: Int) = PenaltyEntity(
        roundId = 1, holeNumber = 1, phase = phase.name, type = type.name,
        penaltyIndex = penaltyIndex, strokeCount = strokeCount, lat = 0.0, lng = 0.0, capturedAt = 0L,
    )

    @Test
    fun `no shots or penalties means zero strokes`() {
        val total = StrokeCalculator.currentTotal(emptyList(), emptyList(), ShotPhase.TO_GREEN)
        assertEquals(0, total)
    }

    @Test
    fun `total is the highest shot index for that phase`() {
        val shots = listOf(shot(ShotPhase.TO_GREEN, 1), shot(ShotPhase.TO_GREEN, 2))
        val total = StrokeCalculator.currentTotal(shots, emptyList(), ShotPhase.TO_GREEN)
        assertEquals(2, total)
    }

    @Test
    fun `penalty with no matching shot still counts toward the total`() {
        // OB 벌타만 있고 실제 샷은 아직 없는 경우 (shots 테이블엔 행이 없음)
        val penalties = listOf(penalty(ShotPhase.TO_GREEN, PenaltyType.OB, penaltyIndex = 2, strokeCount = 2))
        val total = StrokeCalculator.currentTotal(emptyList(), penalties, ShotPhase.TO_GREEN)
        assertEquals(2, total)
    }

    @Test
    fun `total is the max of shot and penalty indices, not their sum`() {
        // 1타 치고(shotIndex=1) OB(penaltyIndex=2)를 선언한 뒤 이어친 상황 등
        val shots = listOf(shot(ShotPhase.TO_GREEN, 1))
        val penalties = listOf(penalty(ShotPhase.TO_GREEN, PenaltyType.OB, penaltyIndex = 2, strokeCount = 1))
        val total = StrokeCalculator.currentTotal(shots, penalties, ShotPhase.TO_GREEN)
        assertEquals(2, total)
    }

    @Test
    fun `phases are independent`() {
        val shots = listOf(shot(ShotPhase.TO_GREEN, 3), shot(ShotPhase.SHORT_GAME, 1))
        val toGreen = StrokeCalculator.currentTotal(shots, emptyList(), ShotPhase.TO_GREEN)
        val shortGame = StrokeCalculator.currentTotal(shots, emptyList(), ShotPhase.SHORT_GAME)
        assertEquals(3, toGreen)
        assertEquals(1, shortGame)
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `./gradlew.bat testDebugUnitTest --tests "com.golfrecorder.domain.model.StrokeCalculatorTest"`
Expected: FAIL (컴파일 에러 — `StrokeCalculator`가 아직 없음)

- [ ] **Step 3: 최소 구현 작성**

```kotlin
package com.golfrecorder.domain.model

import com.golfrecorder.data.local.entity.PenaltyEntity
import com.golfrecorder.data.local.entity.ShotEntity

object StrokeCalculator {

    /** [shots]와 [penalties]만 보고 특정 홀·구간(phase)의 "지금 타수"를 복원한다.
     * shotIndex/penaltyIndex 둘 다 "그 시점의 누적 타수"를 담고 있으므로, 둘 중
     * 최댓값이 곧 현재 타수다. */
    fun currentTotal(shots: List<ShotEntity>, penalties: List<PenaltyEntity>, phase: ShotPhase): Int {
        val maxShotIndex = shots.filter { it.phase == phase.name }.maxOfOrNull { it.shotIndex } ?: 0
        val maxPenaltyIndex = penalties.filter { it.phase == phase.name }.maxOfOrNull { it.penaltyIndex } ?: 0
        return maxOf(maxShotIndex, maxPenaltyIndex)
    }
}
```

- [ ] **Step 4: 테스트 실행해서 통과 확인**

Run: `./gradlew.bat testDebugUnitTest --tests "com.golfrecorder.domain.model.StrokeCalculatorTest"`
Expected: PASS (5개 테스트 모두)

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/kotlin/com/golfrecorder/domain/model/StrokeCalculator.kt app/src/test/kotlin/com/golfrecorder/domain/model/StrokeCalculatorTest.kt
git commit -m "Add StrokeCalculator to derive live stroke totals from shots/penalties (vX.Y.Z)"
```

---

### Task 3: `RoundPlayViewModel`이 홀 번호를 DB에 write-through, 타수 시드를 `StrokeCalculator`로 계산

**Files:**
- Modify: `app/src/main/kotlin/com/golfrecorder/ui/round/RoundPlayScreen.kt` (파일 안의 `RoundPlayViewModel` 클래스만 수정, Composable은 이 태스크에서 안 건드림)

**Interfaces:**
- Consumes: Task 1의 `roundRepository.updateCurrentHoleNumber`, Task 2의 `StrokeCalculator.currentTotal`.
- Produces: 없음(외부에서 쓰는 public API는 그대로 — `currentHoleNumber`, `strokesToGreen`, `strokesGreenToHoleOut`, `goToHole` 시그니처 불변이라 `RoundPlayScreen` Composable은 이 태스크에서 수정할 필요 없음).

**배경**: 지금은 홀에 들어올 때 `hole_records`(홀을 나갈 때만 저장되는 스냅샷)에서 타수를 읽어와서, 워치가 그 홀의 타수를 바꾼 뒤 폰 화면을 새로 열면 옛날 값이 보이는 문제가 있다. `shots`/`penalties`는 매 이벤트마다 즉시 저장되므로, 그 두 테이블에서 `StrokeCalculator`로 다시 계산하면 항상 최신값이다. `goToHole`은 지금 로컬에서만 홀 번호를 바꾸는데, DB에도 같이 써야 워치가 그 값을 볼 수 있다.

- [ ] **Step 1: `loadHole`의 타수 시드 로직 교체**

`RoundPlayScreen.kt`에서 `loadHole` 함수를 찾아 아래처럼 바꾼다 (기존 `hole_records` 기반 타수 조회 블록을 `shots`/`penalties` 기반으로 교체):

```kotlin
    private fun loadHole(holeNumber: Int) {
        currentHoleNumber = holeNumber
        shotsCollectJob?.cancel()
        shotsCollectJob = viewModelScope.launch {
            shotRepository.getShots(roundId, holeNumber).collect { shotsFlow.value = it }
        }
        penaltiesCollectJob?.cancel()
        penaltiesCollectJob = viewModelScope.launch {
            penaltyRepository.getPenalties(roundId, holeNumber).collect { penaltiesFlow.value = it }
        }
        viewModelScope.launch {
            val shots = shotRepository.getShots(roundId, holeNumber).first()
            val penalties = penaltyRepository.getPenalties(roundId, holeNumber).first()
            strokesToGreen = StrokeCalculator.currentTotal(shots, penalties, ShotPhase.TO_GREEN)
            strokesGreenToHoleOut = StrokeCalculator.currentTotal(shots, penalties, ShotPhase.SHORT_GAME)
        }
    }
```

(참고: 이 변경으로 이 함수 안에서 더 이상 `roundRepository.getRoundWithHoleRecords(roundId)`를 안 쓰게 되지만, 클래스의 다른 곳(`exitRound`)에서 여전히 쓰므로 관련 import는 그대로 둔다.)

- [ ] **Step 2: `goToHole`이 DB에도 홀 번호를 쓰도록 수정**

```kotlin
    fun goToHole(holeNumber: Int) {
        saveCurrentHole {
            viewModelScope.launch { roundRepository.updateCurrentHoleNumber(roundId, holeNumber) }
            loadHole(holeNumber)
        }
    }
```

- [ ] **Step 3: `init` 블록이 진입 시점 홀 번호도 DB에 반영하도록 수정**

(리뷰 모드가 아닐 때만 — 리뷰 중엔 그 라운드가 이미 끝나서 서비스도 안 떠 있으므로 의미 없음)

```kotlin
    init {
        if (!isReview) {
            viewModelScope.launch { roundRepository.updateCurrentHoleNumber(roundId, initialHoleNumber) }
        }
        loadHole(initialHoleNumber)
    }
```

- [ ] **Step 4: 빌드**

Run: `./gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 실기기에서 기존 플레이 흐름이 그대로 동작하는지 확인**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

사용자에게 확인 요청: "코스를 하나 골라 라운드를 시작하고, 1홀에서 그린까지 타수를 몇 번 +/- 해보고, 다음 홀로 넘어갔다가 다시 이전 홀로 돌아왔을 때 타수가 그대로 남아있는지 확인해주세요. OB/해저드도 하나 추가해보고 타수가 정상적으로 올라가는지 확인해주세요."

- [ ] **Step 6: 커밋**

```bash
git add app/src/main/kotlin/com/golfrecorder/ui/round/RoundPlayScreen.kt
git commit -m "Seed live hole state from shots/penalties and persist current hole (vX.Y.Z)"
```

---

### Task 4: Wearable 의존성 + 공유 상수(`WearSync`)

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/kotlin/com/golfrecorder/wearsync/WearSync.kt`

**Interfaces:**
- Produces: `WearSync` object의 모든 상수 — Task 5(서비스)와 Task 8(워치 앱)이 그대로 가져다 쓴다. **이 파일은 워치 모듈(Task 7)에도 내용을 그대로 복제해야 한다 — 두 파일이 항상 같은 값을 가지도록 유지.**

- [ ] **Step 1: 버전 카탈로그에 의존성 추가**

`gradle/libs.versions.toml`의 `[versions]`에 추가:

```toml
playServicesWearable = "18.2.0"
coroutinesPlayServices = "1.10.2"
```

`[libraries]`에 추가:

```toml
play-services-wearable = { group = "com.google.android.gms", name = "play-services-wearable", version.ref = "playServicesWearable" }
kotlinx-coroutines-play-services = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-play-services", version.ref = "coroutinesPlayServices" }
```

- [ ] **Step 2: `:app`에 의존성 반영**

`app/build.gradle.kts`의 `dependencies` 블록, `implementation(libs.play.services.location)` 다음 줄에 추가:

```kotlin
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
```

- [ ] **Step 3: 공유 상수 파일 작성**

```kotlin
package com.golfrecorder.wearsync

/**
 * 폰 앱(:app)과 워치 앱(:wear)이 Data Layer로 주고받는 경로/키 상수.
 * 두 모듈은 별도 Gradle 모듈이라 소스를 공유하지 않으므로, 이 파일은
 * `wear/src/main/kotlin/com/golfrecorder/wearsync/WearSync.kt`에 내용을
 * 그대로 복제해서 둔다 — 한쪽만 고치면 통신이 깨지니 항상 같이 수정할 것.
 */
object WearSync {
    /** 워치 → 폰: 버튼 입력 명령. MessageClient, payload는 아래 ACTION_* 문자열 UTF-8 바이트. */
    const val ACTION_PATH = "/stroke/action"

    /** 폰 → 워치: 최신 라운드 상태. DataClient DataMap. */
    const val STATE_PATH = "/round-state"

    const val ACTION_INCREMENT_TO_GREEN = "INCREMENT_TO_GREEN"
    const val ACTION_DECREMENT_TO_GREEN = "DECREMENT_TO_GREEN"
    const val ACTION_INCREMENT_SHORT_GAME = "INCREMENT_SHORT_GAME"
    const val ACTION_DECREMENT_SHORT_GAME = "DECREMENT_SHORT_GAME"
    const val ACTION_NEXT_HOLE = "NEXT_HOLE"
    const val ACTION_PREV_HOLE = "PREV_HOLE"

    const val KEY_ROUND_ACTIVE = "roundActive"
    const val KEY_HOLE_NUMBER = "holeNumber"
    const val KEY_PAR = "par"
    const val KEY_STROKES_TO_GREEN = "strokesToGreen"
    const val KEY_STROKES_SHORT_GAME = "strokesGreenToHoleOut"
    const val KEY_HOLE_COUNT = "holeCount"
}
```

- [ ] **Step 4: 빌드**

Run: `./gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL` (새 의존성이 정상적으로 받아지는지만 확인 — 아직 아무도 안 씀)

- [ ] **Step 5: 커밋**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/kotlin/com/golfrecorder/wearsync/WearSync.kt
git commit -m "Add Wearable Data Layer dependency and shared message constants (vX.Y.Z)"
```

---

### Task 5: `RoundRecordingService` (포그라운드 서비스)

**Files:**
- Create: `app/src/main/kotlin/com/golfrecorder/service/RoundRecordingService.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `WearSync`(Task 4), `StrokeCalculator.currentTotal`(Task 2), `RoundRepository.getCurrentHoleNumber`/`updateCurrentHoleNumber`(Task 1), 기존 `ShotRepository.recordShot`/`removeShot`, `PenaltyRepository`(읽기만), `CourseRepository.getCourseWithHoles`, `LocationCapture.getCurrentLocation`, `AppContainer.getInstance(context)`.
- Produces: `RoundRecordingService.start(context: Context, roundId: Long, courseId: Long)`, `RoundRecordingService.stop(context: Context)` — Task 6(MainActivity)가 이 두 함수를 호출한다.

**알려진 한계 (여기서 의도적으로 처리 안 함)**: 워치의 "−" 버튼은 그 구간의 마지막 이벤트가 실제 샷일 때만 정상 동작한다. 마지막 이벤트가 OB/해저드 벌타였다면(즉 `shots` 테이블엔 해당 인덱스가 없고 `penalties`에만 있다면) 워치에서 "−"를 눌러도 지울 샷이 없어서 아무 효과가 없다 — 벌타 취소는 계속 폰에서만 가능하다(OB/해저드가 애초에 워치 범위 밖이므로 받아들이는 트레이드오프).

- [ ] **Step 1: 서비스 클래스 작성**

```kotlin
package com.golfrecorder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.golfrecorder.MainActivity
import com.golfrecorder.di.AppContainer
import com.golfrecorder.domain.model.ShotPhase
import com.golfrecorder.domain.model.StrokeCalculator
import com.golfrecorder.location.LocationCapture
import com.golfrecorder.wearsync.WearSync
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class RoundRecordingService : Service() {

    private lateinit var container: AppContainer
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var roundId: Long = -1
    private var courseId: Long = -1

    private val messageListener = MessageClient.OnMessageReceivedListener { event ->
        if (event.path != WearSync.ACTION_PATH) return@OnMessageReceivedListener
        val action = String(event.data, Charsets.UTF_8)
        serviceScope.launch { handleAction(action) }
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer.getInstance(applicationContext)
        Wearable.getMessageClient(this).addListener(messageListener)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        roundId = intent?.getLongExtra(EXTRA_ROUND_ID, -1) ?: -1
        courseId = intent?.getLongExtra(EXTRA_COURSE_ID, -1) ?: -1
        serviceScope.launch { pushState() }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Wearable.getMessageClient(this).removeListener(messageListener)
        serviceScope.launch {
            pushState(roundActive = false)
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun handleAction(action: String) {
        if (roundId <= 0 || courseId <= 0) return
        val holeNumber = container.roundRepository.getCurrentHoleNumber(roundId) ?: return
        when (action) {
            WearSync.ACTION_INCREMENT_TO_GREEN -> incrementStroke(holeNumber, ShotPhase.TO_GREEN)
            WearSync.ACTION_DECREMENT_TO_GREEN -> decrementStroke(holeNumber, ShotPhase.TO_GREEN)
            WearSync.ACTION_INCREMENT_SHORT_GAME -> incrementStroke(holeNumber, ShotPhase.SHORT_GAME)
            WearSync.ACTION_DECREMENT_SHORT_GAME -> decrementStroke(holeNumber, ShotPhase.SHORT_GAME)
            WearSync.ACTION_NEXT_HOLE -> changeHole(holeNumber + 1)
            WearSync.ACTION_PREV_HOLE -> changeHole(holeNumber - 1)
        }
        pushState()
    }

    private suspend fun incrementStroke(holeNumber: Int, phase: ShotPhase) {
        val shots = container.shotRepository.getShots(roundId, holeNumber).first()
        val penalties = container.penaltyRepository.getPenalties(roundId, holeNumber).first()
        val newValue = StrokeCalculator.currentTotal(shots, penalties, phase) + 1
        val loc = if (LocationCapture.hasPermission(this)) LocationCapture.getCurrentLocation(this) else null
        if (loc != null) {
            container.shotRepository.recordShot(roundId, holeNumber, phase, newValue, loc.lat, loc.lng)
        }
    }

    private suspend fun decrementStroke(holeNumber: Int, phase: ShotPhase) {
        val shots = container.shotRepository.getShots(roundId, holeNumber).first()
        val penalties = container.penaltyRepository.getPenalties(roundId, holeNumber).first()
        val currentTotal = StrokeCalculator.currentTotal(shots, penalties, phase)
        if (currentTotal <= 0) return
        // 마지막 이벤트가 실제 샷일 때만 지울 게 있다 — 벌타였다면(shots에 없음) 조용히 무시한다.
        val hasMatchingShot = shots.any { it.phase == phase.name && it.shotIndex == currentTotal }
        if (hasMatchingShot) {
            container.shotRepository.removeShot(roundId, holeNumber, phase, currentTotal)
        }
    }

    private suspend fun changeHole(newHoleNumber: Int) {
        val course = container.courseRepository.getCourseWithHoles(courseId).first() ?: return
        val holeCount = course.holes.size
        if (newHoleNumber < 1 || (holeCount > 0 && newHoleNumber > holeCount)) return
        container.roundRepository.updateCurrentHoleNumber(roundId, newHoleNumber)
    }

    private suspend fun pushState(roundActive: Boolean = true) {
        if (roundId <= 0) return
        val dataMapRequest = PutDataMapRequest.create(WearSync.STATE_PATH).apply {
            dataMap.putBoolean(WearSync.KEY_ROUND_ACTIVE, roundActive)
            if (roundActive && courseId > 0) {
                val holeNumber = container.roundRepository.getCurrentHoleNumber(roundId) ?: 1
                val course = container.courseRepository.getCourseWithHoles(courseId).first()
                val hole = course?.holes?.find { it.holeNumber == holeNumber }
                val shots = container.shotRepository.getShots(roundId, holeNumber).first()
                val penalties = container.penaltyRepository.getPenalties(roundId, holeNumber).first()
                dataMap.putInt(WearSync.KEY_HOLE_NUMBER, holeNumber)
                dataMap.putInt(WearSync.KEY_PAR, hole?.par ?: 4)
                dataMap.putInt(WearSync.KEY_HOLE_COUNT, course?.holes?.size ?: 18)
                dataMap.putInt(
                    WearSync.KEY_STROKES_TO_GREEN,
                    StrokeCalculator.currentTotal(shots, penalties, ShotPhase.TO_GREEN),
                )
                dataMap.putInt(
                    WearSync.KEY_STROKES_SHORT_GAME,
                    StrokeCalculator.currentTotal(shots, penalties, ShotPhase.SHORT_GAME),
                )
            }
        }.setUrgent()
        Wearable.getDataClient(this).putDataItem(dataMapRequest.asPutDataRequest()).await()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "라운드 워치 연동",
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("라운딩 기록 중 (워치 연동)")
            .setContentText("워치에서 타수/홀 이동을 조작할 수 있습니다")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "round_recording"
        private const val EXTRA_ROUND_ID = "extra_round_id"
        private const val EXTRA_COURSE_ID = "extra_course_id"

        fun start(context: Context, roundId: Long, courseId: Long) {
            val intent = Intent(context, RoundRecordingService::class.java)
                .putExtra(EXTRA_ROUND_ID, roundId)
                .putExtra(EXTRA_COURSE_ID, courseId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RoundRecordingService::class.java))
        }
    }
}
```

- [ ] **Step 2: 매니페스트에 서비스 등록 + 권한 추가**

`app/src/main/AndroidManifest.xml`의 `<uses-permission>` 목록 끝에 추가:

```xml
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

`<application>` 안, `<activity>` 태그 앞에 추가:

```xml
        <service
            android:name=".service.RoundRecordingService"
            android:exported="false"
            android:foregroundServiceType="location|connectedDevice" />
```

- [ ] **Step 3: 빌드**

Run: `./gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`

(이 태스크에서는 아직 서비스를 시작하는 코드가 없어서 실기기 동작 확인은 Task 6에서 같이 한다.)

- [ ] **Step 4: 커밋**

```bash
git add app/src/main/kotlin/com/golfrecorder/service/RoundRecordingService.kt app/src/main/AndroidManifest.xml
git commit -m "Add RoundRecordingService for watch-driven stroke/hole updates (vX.Y.Z)"
```

---

### Task 6: 서비스 시작/종료를 라운드 생명주기에 연결

**Files:**
- Modify: `app/src/main/kotlin/com/golfrecorder/MainActivity.kt`

**Interfaces:**
- Consumes: `RoundRecordingService.start`/`stop` (Task 5).

- [ ] **Step 1: import 추가**

`MainActivity.kt` 파일 상단 import 블록에 추가:

```kotlin
import androidx.compose.ui.platform.LocalContext
import com.golfrecorder.service.RoundRecordingService
```

- [ ] **Step 2: `AppRoot`에 `LocalContext` 추가**

`AppRoot` Composable 맨 위, `val current = backStack.last()` 다음 줄에 추가:

```kotlin
    val context = LocalContext.current
```

- [ ] **Step 3: `CourseSelect` 화면에서 라운드 생성 직후 서비스 시작**

`is Screen.CourseSelect ->` 블록의 `onCourseSelected`를 수정:

```kotlin
            CourseSelectScreen(
                viewModel = vm,
                onCourseSelected = { courseId, roundId ->
                    RoundRecordingService.start(context, roundId, courseId)
                    push(Screen.RoundPlay(roundId, courseId, 1))
                },
                onBack = { pop() },
            )
```

- [ ] **Step 4: `RoundPlay` 화면의 완료/취소 시 서비스 종료**

`is Screen.RoundPlay ->` 블록의 `onFinished`/`onCancelled`를 수정 (`onShowSummary`는 그대로 — 아직 라운드가 안 끝났으므로 서비스는 계속 살아있어야 함):

```kotlin
            RoundPlayScreen(
                viewModel = vm,
                mapSlotState = mapSlotState,
                onFinished = {
                    RoundRecordingService.stop(context)
                    push(Screen.RoundSummary(screen.roundId, screen.courseId))
                },
                onCancelled = {
                    RoundRecordingService.stop(context)
                    pop()
                },
                onShowSummary = {
                    goHome()
                    push(Screen.RoundSummary(screen.roundId, screen.courseId))
                },
            )
```

- [ ] **Step 5: 빌드**

Run: `./gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: 실기기에서 서비스가 실제로 뜨고 사라지는지 확인**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

사용자에게 확인 요청: "새 라운드를 시작하면 알림창에 '라운딩 기록 중 (워치 연동)' 알림이 뜨는지, 그 라운드를 완료하거나(완료 버튼) 코스만 고르고 바로 뒤로가기로 취소했을 때 그 알림이 사라지는지 확인해주세요."

로그로 크래시 여부는 Claude가 직접 확인 가능:

```bash
adb logcat -d | grep -i "golfrecorder\|RoundRecordingService" | tail -50
```

Expected: `AndroidRuntime` 크래시 스택트레이스가 없어야 함.

- [ ] **Step 7: 커밋**

```bash
git add app/src/main/kotlin/com/golfrecorder/MainActivity.kt
git commit -m "Start/stop RoundRecordingService with the round lifecycle (vX.Y.Z)"
```

---

### Task 7: `:wear` Gradle 모듈 스캐폴딩

**Files:**
- Modify: `settings.gradle.kts`
- Create: `wear/build.gradle.kts`
- Create: `wear/src/main/AndroidManifest.xml`
- Create: `wear/src/main/res/values/strings.xml`
- Create: `wear/src/main/kotlin/com/golfrecorder/wearsync/WearSync.kt` (Task 4 파일과 내용 동일)

**Interfaces:**
- Produces: 빌드 가능한 빈 Wear OS 앱 스켈레톤 — Task 8이 여기에 실제 화면을 채운다.

- [ ] **Step 1: 버전 카탈로그에 Wear Compose 버전 추가**

`gradle/libs.versions.toml`의 `[versions]`에 추가:

```toml
wearCompose = "1.4.1"
```

`[libraries]`에 추가:

```toml
wear-compose-material = { group = "androidx.wear.compose", name = "compose-material", version.ref = "wearCompose" }
wear-compose-foundation = { group = "androidx.wear.compose", name = "compose-foundation", version.ref = "wearCompose" }
```

- [ ] **Step 2: `settings.gradle.kts`에 모듈 추가**

`include(":app")` 다음 줄에 추가:

```kotlin
include(":wear")
```

- [ ] **Step 3: `wear/build.gradle.kts` 작성**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.golfrecorder.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.golfrecorder.wear"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
```

- [ ] **Step 4: `wear/src/main/AndroidManifest.xml` 작성**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-feature android:name="android.hardware.type.watch" />

    <application
        android:allowBackup="true"
        android:icon="@android:drawable/ic_dialog_info"
        android:label="@string/app_name"
        android:theme="@android:style/Theme.DeviceDefault">

        <meta-data
            android:name="com.google.android.wearable.standalone"
            android:value="true" />

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@android:style/Theme.DeviceDefault">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

(아이콘은 시스템 기본 드로어블로 임시 처리 — K 모노그램 아이콘 적용은 워치 화면 레이아웃이 실기기에서 확정된 뒤 별도로 진행한다.)

- [ ] **Step 5: `strings.xml` 작성**

```xml
<resources>
    <string name="app_name">K-Golf Watch</string>
</resources>
```

- [ ] **Step 6: 공유 상수 파일 복제**

Task 4에서 작성한 `app/src/main/kotlin/com/golfrecorder/wearsync/WearSync.kt`와 **완전히 동일한 내용**으로 `wear/src/main/kotlin/com/golfrecorder/wearsync/WearSync.kt`를 만든다.

- [ ] **Step 7: 임시 `MainActivity` (빌드 확인용)**

```kotlin
package com.golfrecorder.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Text("K-Golf Watch")
            }
        }
    }
}
```

- [ ] **Step 8: 빌드**

Run: `./gradlew.bat :wear:assembleDebug`
Expected: `BUILD SUCCESSFUL`, `wear/build/outputs/apk/debug/wear-debug.apk` 생성됨.

- [ ] **Step 9: 커밋**

```bash
git add settings.gradle.kts gradle/libs.versions.toml wear/
git commit -m "Scaffold standalone :wear Gradle module (vX.Y.Z)"
```

---

### Task 8: 워치 화면 — 상태 수신 + 명령 전송 + UI

**Files:**
- Create: `wear/src/main/kotlin/com/golfrecorder/wear/RoundStateViewModel.kt`
- Modify: `wear/src/main/kotlin/com/golfrecorder/wear/MainActivity.kt`

**Interfaces:**
- Consumes: `WearSync`(Task 4/7에서 복제된 상수).
- Produces: 없음(최종 사용자 화면).

- [ ] **Step 1: `RoundStateViewModel` 작성**

```kotlin
package com.golfrecorder.wear

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.golfrecorder.wearsync.WearSync
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class RoundUiState(
    val roundActive: Boolean = false,
    val holeNumber: Int = 1,
    val par: Int = 4,
    val strokesToGreen: Int = 0,
    val strokesGreenToHoleOut: Int = 0,
    val holeCount: Int = 18,
)

class RoundStateViewModel(application: Application) :
    AndroidViewModel(application),
    DataClient.OnDataChangedListener {

    private val _state = MutableStateFlow(RoundUiState())
    val state: StateFlow<RoundUiState> = _state

    private val dataClient = Wearable.getDataClient(application)
    private val messageClient = Wearable.getMessageClient(application)
    private val nodeClient = Wearable.getNodeClient(application)

    init {
        dataClient.addListener(this)
        viewModelScope.launch {
            val items = dataClient.dataItems.await()
            for (item in items) {
                if (item.uri.path == WearSync.STATE_PATH) {
                    applyDataMap(DataMapItem.fromDataItem(item).dataMap)
                }
            }
            items.release()
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.dataItem.uri.path == WearSync.STATE_PATH) {
                applyDataMap(DataMapItem.fromDataItem(event.dataItem).dataMap)
            }
        }
        dataEvents.release()
    }

    private fun applyDataMap(map: DataMap) {
        _state.value = RoundUiState(
            roundActive = map.getBoolean(WearSync.KEY_ROUND_ACTIVE, false),
            holeNumber = map.getInt(WearSync.KEY_HOLE_NUMBER, 1),
            par = map.getInt(WearSync.KEY_PAR, 4),
            strokesToGreen = map.getInt(WearSync.KEY_STROKES_TO_GREEN, 0),
            strokesGreenToHoleOut = map.getInt(WearSync.KEY_STROKES_SHORT_GAME, 0),
            holeCount = map.getInt(WearSync.KEY_HOLE_COUNT, 18),
        )
    }

    fun sendAction(action: String) {
        viewModelScope.launch {
            val nodes = nodeClient.connectedNodes.await()
            for (node in nodes) {
                messageClient.sendMessage(node.id, WearSync.ACTION_PATH, action.toByteArray(Charsets.UTF_8)).await()
            }
        }
    }

    override fun onCleared() {
        dataClient.removeListener(this)
        super.onCleared()
    }
}
```

- [ ] **Step 2: `MainActivity`를 실제 화면으로 교체**

```kotlin
package com.golfrecorder.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.golfrecorder.wearsync.WearSync

class MainActivity : ComponentActivity() {
    private val viewModel: RoundStateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                RoundControlScreen(viewModel)
            }
        }
    }
}

@Composable
fun RoundControlScreen(viewModel: RoundStateViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (!state.roundActive) {
            Text(
                "라운드가 시작되지 않았습니다.\n폰에서 라운드를 시작해주세요.",
                textAlign = TextAlign.Center,
            )
            return@Column
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { viewModel.sendAction(WearSync.ACTION_PREV_HOLE) },
                enabled = state.holeNumber > 1,
            ) { Text("◀") }
            Text(" ${state.holeNumber}홀 (파${state.par}) ")
            Button(
                onClick = { viewModel.sendAction(WearSync.ACTION_NEXT_HOLE) },
                enabled = state.holeNumber < state.holeCount,
            ) { Text("▶") }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("그린까지: ${state.strokesToGreen} ")
            Button(
                onClick = { viewModel.sendAction(WearSync.ACTION_DECREMENT_TO_GREEN) },
                enabled = state.strokesToGreen > 0,
            ) { Text("-") }
            Button(onClick = { viewModel.sendAction(WearSync.ACTION_INCREMENT_TO_GREEN) }) { Text("+") }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("숏게임: ${state.strokesGreenToHoleOut} ")
            Button(
                onClick = { viewModel.sendAction(WearSync.ACTION_DECREMENT_SHORT_GAME) },
                enabled = state.strokesGreenToHoleOut > 0,
            ) { Text("-") }
            Button(onClick = { viewModel.sendAction(WearSync.ACTION_INCREMENT_SHORT_GAME) }) { Text("+") }
        }
    }
}
```

- [ ] **Step 3: 빌드**

Run: `./gradlew.bat :wear:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 커밋**

```bash
git add wear/src/main/kotlin/com/golfrecorder/wear/RoundStateViewModel.kt wear/src/main/kotlin/com/golfrecorder/wear/MainActivity.kt
git commit -m "Add watch screen for hole nav and stroke steppers (vX.Y.Z)"
```

---

### Task 9: 엔드투엔드 실기기 검증

**Files:** 없음 (수동 검증 태스크)

이 태스크는 코드 변경이 아니라, 지금까지 만든 걸 실제 폰+워치 페어로 확인하는 절차다. 워치 설치는 사용자가 이미 "방법 2(폰의 adb를 경유하는 블루투스 디버그 브리지)"로 하겠다고 확정했다.

- [ ] **Step 1: 폰 앱 설치**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 2: 사용자에게 워치 개발자 옵션 활성화 요청**

사용자에게 안내: "워치에서 설정 → 정보 → 소프트웨어 버전을 5번 탭해서 개발자 모드를 켜고, 개발자 옵션에서 'ADB 디버깅'과 '블루투스를 통한 디버그'를 켜주세요. 켜지면 알려주세요."

- [ ] **Step 3: 블루투스 디버그 브리지 연결 (Claude가 직접 실행 가능한 adb 명령)**

```bash
adb forward tcp:4444 localabstract:/adb-hub
adb connect localhost:4444
adb devices
```

Expected: `localhost:4444`가 기기 목록에 추가로 표시됨.

- [ ] **Step 4: 워치 앱 설치**

```bash
adb -s localhost:4444 install -r wear/build/outputs/apk/debug/wear-debug.apk
```

- [ ] **Step 5: 사용자에게 실제 동작 확인 요청 (화면 판단은 전부 사용자가)**

사용자에게 순서대로 안내:
1. "폰에서 새 라운드를 시작해주세요. 워치에서 K-Golf Watch 앱을 열었을 때 '1홀 (파 N)', '그린까지: 0', '숏게임: 0'이 보이는지 확인해주세요."
2. "워치에서 '그린까지 +'를 두 번 눌러주세요. 워치 화면 숫자가 2로 바뀌는지, 폰 화면(그 홀)을 열었을 때도 그린까지 타수가 2로 보이는지 확인해주세요."
3. "폰 화면을 끄거나 앱을 완전히 백그라운드로 보낸 상태에서 워치로 '다음 홀' → '숏게임 +'를 눌러보고, 그 다음 폰을 다시 켰을 때 정확한 홀·타수가 보이는지 확인해주세요."
4. "라운드를 '완료'한 뒤 워치 앱을 다시 열면 '라운드가 시작되지 않았습니다' 안내가 뜨는지 확인해주세요."

- [ ] **Step 6: 로그로 문제 유무 확인 (Claude가 직접, 화면 조작 없이)**

```bash
adb logcat -d | grep -iE "golfrecorder|RoundRecordingService|FATAL" | tail -100
```

Expected: 크래시/미처리 예외 없음.

- [ ] **Step 7: 최종 커밋 (버전 태그는 사용자가 명시적으로 요청할 때만)**

사용자가 전체 흐름을 확인해준 뒤, 지금까지 태스크 1~8에서 이미 각각 커밋했으므로 별도 커밋은 필요 없다 — 문제가 발견되면 수정 후 그 수정만 새 커밋으로 추가한다.

---

## 실행 방식 안내

이 플랜은 위에서부터 순서대로 실행해야 한다 — Task 1(DB)이 Task 3(ViewModel)의 전제이고, Task 2(계산 함수)가 Task 3/5의 전제이며, Task 4(의존성/상수)가 Task 5/7/8의 전제다.
