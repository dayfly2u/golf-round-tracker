# 해외 라운딩용 구글맵 지원 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 라운드 시작 시 "해외 코스 (구글맵 사용)" 스위치로 지도 프로바이더(카카오맵/구글맵)를 선택할 수 있게 하고, 구글맵으로도 카카오맵과 동일한 기능(위성뷰, 샷 마커, 구간 선, 거리 라벨, 자동 줌)이 동작하게 만든다.

**Architecture:** 앱 루트에 카카오/구글 지도 뷰를 각각 하나씩 상시 마운트해두고(카카오맵의 "반복 생성/파괴 금지" 제약을 구글 쪽에도 동일 적용해 버그를 원천 회피), `MapRequest.provider`에 맞는 쪽만 화면에 보이고 반대쪽은 화면 밖에 숨긴다. `RoundEntity.mapProvider` 컬럼에 라운드 시작 시점의 선택을 고정 저장해서, 리뷰할 때도 그 라운드가 기록될 때 쓴 지도가 그대로 다시 보인다.

**Tech Stack:** Kotlin, Jetpack Compose, Room, `com.google.android.gms:play-services-maps` (Google Maps SDK for Android, classic View 기반 `MapView`).

**설계 문서:** `docs/superpowers/specs/2026-09-14-google-maps-support-design.md` — 이 계획은 그 설계를 그대로 구현한다. Google Maps API 키 발급 안내도 그 문서 맨 아래에 있다.

## Global Constraints

- **DB 마이그레이션은 절대 `fallbackToDestructiveMigration`을 쓰지 않는다** — 반드시 명시적 `Migration(old, new)` 객체를 작성하고, 실기기(기존 데이터가 있는 폰)에 `adb install -r`로 설치해서 마이그레이션 경로가 실제로 동작하는지, 기존 데이터가 보존되는지 확인한다 (`C:\github\golf-round-tracker\CLAUDE.md` 규칙).
- 앱 코드가 바뀌는 커밋마다 `app/build.gradle.kts`의 `versionName` PATCH를 1 올리고, 커밋 제목 끝에 `(vX.Y.Z)`를 붙인다. `versionCode`는 건드리지 않는다 (태그 찍을 때만 올림). 각 태스크는 최소 1개 이상의 커밋으로 마무리하고, 그 커밋들 각각에 이 규칙을 적용한다.
- 카카오맵 관련 기존 동작(국내 라운드)은 이번 작업으로 전혀 회귀하면 안 된다 — `PersistentMap.kt`/`CourseMapView.kt`를 건드리는 태스크는 특히 실기기에서 기존 카카오 지도가 그대로 동작하는지 확인한다.
- 커밋 메시지 끝에는 다음 두 줄을 반드시 포함한다:
  ```
  Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01X7KasCSYPtL7EZstLv3KgP
  ```
- 실기기 adb 확인용 정보: 연결된 폰 시리얼은 `R3CW30CW70Z`. platform-tools 경로가 PATH에 없으면 `export PATH="$PATH:/c/Users/dayfl/AppData/Local/Android/Sdk/platform-tools"`로 추가한다. 패키지명은 `com.golfrecorder`. `run-as`로 DB 파일을 직접 볼 때는 절대경로 앞에 `//`를 붙여야 Windows/Git-Bash에서 경로가 망가지지 않는다 (예: `adb shell run-as com.golfrecorder cat //data/data/com.golfrecorder/databases/golf_recorder.db`). 바이너리 파일을 로컬로 가져올 땐 `adb shell`이 아니라 `adb exec-out`을 써야 CRLF로 깨지지 않는다.

---

## File Structure

- `app/src/main/kotlin/com/golfrecorder/domain/model/MapProvider.kt` (신규) — `MapProvider` enum.
- `app/src/main/kotlin/com/golfrecorder/ui/map/PersistentMap.kt` (수정) — `MapRequest`에서 `tapToSetGreen`/`onGreenTap` 제거, `provider` 필드 추가. `PersistentCourseMap`을 `PersistentKakaoMap` + `PersistentGoogleMap`을 마운트하는 디스패처로 변경. 기존 카카오 로직은 `PersistentKakaoMap`으로 이름만 바뀌고 provider 필터링 조건만 추가.
- `app/src/main/kotlin/com/golfrecorder/ui/map/CourseMapView.kt` (수정) — `tapToSetGreen` 관련 코드 없음(원래도 없었음, 그대로 유지). 변경 없음.
- `app/src/main/kotlin/com/golfrecorder/ui/map/GooglePersistentMap.kt` (신규) — `PersistentGoogleMap` composable.
- `app/src/main/kotlin/com/golfrecorder/ui/map/GoogleCourseMapView.kt` (신규) — `drawOverlaysGoogle` 함수.
- `app/src/main/kotlin/com/golfrecorder/data/local/entity/RoundEntity.kt` (수정) — `mapProvider` 컬럼 추가.
- `app/src/main/kotlin/com/golfrecorder/data/local/Migrations.kt` (수정) — `MIGRATION_17_18` 추가.
- `app/src/main/kotlin/com/golfrecorder/data/local/AppDatabase.kt` (수정) — 버전 17→18, 마이그레이션 등록.
- `app/src/main/kotlin/com/golfrecorder/data/local/dao/RoundDao.kt` (수정) — `getMapProvider` 추가.
- `app/src/main/kotlin/com/golfrecorder/data/repository/RoundRepository.kt` (수정) — `startRound`에 `mapProvider` 파라미터, `getMapProvider` passthrough.
- `gradle/libs.versions.toml` (수정) — `play-services-maps` 의존성 추가.
- `app/build.gradle.kts` (수정) — 의존성 + manifest placeholder.
- `app/src/main/AndroidManifest.xml` (수정) — 구글맵 API 키 메타데이터.
- `local.properties` (수정) — `google.maps.api.key=` 항목 추가.
- `app/src/main/kotlin/com/golfrecorder/ui/course/CourseSelectScreen.kt` (수정) — "해외 코스 (구글맵 사용)" 스위치 추가.
- `app/src/main/kotlin/com/golfrecorder/ui/round/RoundPlayScreen.kt` (수정) — `RoundPlayViewModel`에 `mapProvider` 상태 추가, `CourseMapSlot` 호출에 `provider` 전달, `tapToSetGreen=false`/`onGreenTap={}` 인자 제거.

---

## Task 1: MapProvider enum 추가 + 죽은 코드(tapToSetGreen/onGreenTap) 제거

**Files:**
- Create: `app/src/main/kotlin/com/golfrecorder/domain/model/MapProvider.kt`
- Modify: `app/src/main/kotlin/com/golfrecorder/ui/map/PersistentMap.kt`
- Modify: `app/src/main/kotlin/com/golfrecorder/ui/round/RoundPlayScreen.kt`

**Interfaces:**
- Produces: `enum class MapProvider { KAKAO, GOOGLE }` (패키지 `com.golfrecorder.domain.model`) — 이후 모든 태스크가 이 enum을 쓴다.
- Produces: `MapRequest`/`CourseMapSlot`에서 `tapToSetGreen: Boolean`, `onGreenTap: (AppLatLng) -> Unit` 파라미터가 사라진 시그니처.

- [ ] **Step 1: MapProvider enum 생성**

```kotlin
package com.golfrecorder.domain.model

enum class MapProvider {
    KAKAO,
    GOOGLE,
}
```

- [ ] **Step 2: PersistentMap.kt에서 tapToSetGreen/onGreenTap 제거**

`MapRequest` 클래스 정의(현재 58~74줄)에서 `tapToSetGreen: Boolean`과 `onGreenTap: (AppLatLng) -> Unit` 두 필드를 제거한다. 나머지 필드는 그대로 둔다.

`CourseMapSlot` 함수 시그니처(현재 108~121줄)에서 `tapToSetGreen: Boolean`, `onGreenTap: (AppLatLng) -> Unit` 파라미터를 제거한다.

`CourseMapSlot` 본문에서:
- `val latestOnGreenTap by rememberUpdatedState(onGreenTap)`와 `val stableOnGreenTap = remember { { tapped: AppLatLng -> latestOnGreenTap(tapped) } }` 두 줄을 삭제한다.
- `LaunchedEffect`의 키 목록에서 `tapToSetGreen`을 제거한다.
- `MapRequest(...)` 생성 호출에서 `tapToSetGreen = tapToSetGreen,`과 `onGreenTap = stableOnGreenTap,` 두 줄을 제거한다.

`PersistentCourseMap` 함수 본문에서, 지도 클릭 리스너 안의 이 블록:
```kotlin
map.setOnMapClickListener { clickedMap, position, screenPoint, _ ->
    // 리스너는 한 번만 등록되므로, 캡처한 값이 아니라 지금 화면의
    // 요청을 그때그때 읽어야 한다.
    val active = state.request
    if (active != null && active.tapToSetGreen) {
        // position은 팬(pan) 이후 최신 카메라 상태를 반영하지 못하는
        // 경우가 있어, 실제 스크린 픽셀 좌표(screenPoint)로 직접
        // 재변환한 좌표를 우선 사용한다.
        val resolved = clickedMap.fromScreenPoint(
            screenPoint.x.toInt(),
            screenPoint.y.toInt(),
        ) ?: position
        active.onGreenTap(AppLatLng(resolved.latitude, resolved.longitude))
    }
}
```
전체를 삭제하고, `map.setOnMapClickListener { ... }` 호출 자체를 지운다(리스너를 아예 등록하지 않는다). `kakaoMapState.value = map`는 그대로 남긴다.

- [ ] **Step 3: RoundPlayScreen.kt 호출부 수정**

`CourseMapSlot(...)` 호출(현재 446~457줄 부근)에서 `tapToSetGreen = false,`와 `onGreenTap = {},` 두 줄을 제거한다.

- [ ] **Step 4: 빌드 확인**

```bash
cd "C:/github/golf-round-tracker"
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --console=plain
```
Expected: `BUILD SUCCESSFUL`, 기존 유닛 테스트 전부 통과.

- [ ] **Step 5: 실기기 회귀 확인**

```bash
export PATH="$PATH:/c/Users/dayfl/AppData/Local/Android/Sdk/platform-tools"
./gradlew :app:assembleDebug --console=plain
adb -s R3CW30CW70Z shell am force-stop com.golfrecorder
adb -s R3CW30CW70Z install -r app/build/outputs/apk/debug/app-debug.apk
```
앱을 열어 기존 라운드(또는 새 라운드)에서 지도가 평소처럼 뜨고 샷 마커/선이 그려지는지 확인한다. `tapToSetGreen`은 원래도 항상 `false`였으므로 지도를 탭해도 아무 일이 없었던 것과 동일하게, 지금도 탭해도 아무 일이 없어야 한다(회귀 아님).

- [ ] **Step 6: 버전 올리고 커밋**

`app/build.gradle.kts`의 `versionName`을 현재 값에서 PATCH +1로 올린다(현재 값은 커밋 직전에 `grep versionName app/build.gradle.kts`로 확인 — 이 문서 작성 시점 기준 `0.6.0`이었으나 그 사이 다른 커밋이 있었을 수 있다).

```bash
git add app/src/main/kotlin/com/golfrecorder/domain/model/MapProvider.kt \
        app/src/main/kotlin/com/golfrecorder/ui/map/PersistentMap.kt \
        app/src/main/kotlin/com/golfrecorder/ui/round/RoundPlayScreen.kt \
        app/build.gradle.kts
git commit -m "$(cat <<'EOF'
MapProvider enum 추가, 안 쓰는 핀위치 탭 코드 정리 (vX.Y.Z)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X7KasCSYPtL7EZstLv3KgP
EOF
)"
```

---

## Task 2: 빌드 설정 — 구글맵 SDK 의존성 + 매니페스트 + local.properties

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `local.properties`

**Interfaces:**
- Consumes: 없음 (독립적, Task 1과 병렬 가능하지만 순서상 Task 1 다음에 진행).
- Produces: `libs.play.services.maps` 의존성 카탈로그 항목, `BuildConfig`가 아니라 매니페스트 플레이스홀더 `${googleMapsApiKey}`를 통해 구글 Maps SDK가 API 키를 읽을 수 있는 상태. 이후 태스크(5)가 `com.google.android.gms.maps.MapView` 등을 바로 쓸 수 있다.

- [ ] **Step 1: 버전 카탈로그에 추가**

`gradle/libs.versions.toml`의 `[versions]` 섹션, `playServicesWearable = "18.2.0"` 다음 줄에 추가:
```toml
playServicesMaps = "19.0.0"
```

`[libraries]` 섹션, `play-services-wearable = { ... }` 다음 줄에 추가:
```toml
play-services-maps = { group = "com.google.android.gms", name = "play-services-maps", version.ref = "playServicesMaps" }
```

(주: 19.0.0보다 더 최신 안정 버전이 있으면 그쪽을 써도 된다 — `https://mvnrepository.com/artifact/com.google.android.gms/play-services-maps`에서 확인.)

- [ ] **Step 2: app/build.gradle.kts 수정**

`dependencies { ... }` 블록 안, `implementation(libs.play.services.wearable)` 다음 줄에 추가:
```kotlin
    implementation(libs.play.services.maps)
```

`defaultConfig { ... }` 블록 안, 기존 `buildConfigField("String", "YOUTUBE_API_KEY", ...)` 다음에 추가:
```kotlin
        manifestPlaceholders["googleMapsApiKey"] =
            localProperties.getProperty("google.maps.api.key", "")
```

- [ ] **Step 3: AndroidManifest.xml에 메타데이터 추가**

`app/src/main/AndroidManifest.xml`의 `<application ...>` 태그 안, `<service android:name=".service.RoundRecordingService" .../>` 앞에 추가:
```xml
        <meta-data
            android:name="com.google.android.geo.API_KEY"
            android:value="${googleMapsApiKey}" />
```

- [ ] **Step 4: local.properties에 키 항목 추가**

`local.properties`가 `.gitignore`에 포함돼 있는지 먼저 확인한다(`git check-ignore -q local.properties`, 종료 코드 0이면 무시 대상). 무시 대상이 맞으면 `kakao.native.app.key=...` 다음 줄에 빈 값으로 추가:
```properties
google.maps.api.key=
```
(사용자가 나중에 직접 발급받은 키로 채워 넣는다 — 이 태스크에서는 빈 값이어도 된다. 빌드는 키가 비어 있어도 실패하지 않는다.)

- [ ] **Step 5: 빌드 확인**

```bash
cd "C:/github/golf-round-tracker"
./gradlew :app:assembleDebug --console=plain
```
Expected: `BUILD SUCCESSFUL`. (키가 비어 있어도 컴파일/패키징은 성공해야 한다 — 실패하면 매니페스트 플레이스홀더 문법이나 `google.maps.api.key` 프로퍼티 이름을 다시 확인.)

- [ ] **Step 6: 실기기 설치 확인 (크래시 없는지만)**

```bash
export PATH="$PATH:/c/Users/dayfl/AppData/Local/Android/Sdk/platform-tools"
adb -s R3CW30CW70Z shell am force-stop com.golfrecorder
adb -s R3CW30CW70Z install -r app/build/outputs/apk/debug/app-debug.apk
adb -s R3CW30CW70Z shell am start -n com.golfrecorder/.MainActivity
```
앱이 정상적으로 뜨는지(크래시 안 하는지) 확인한다. 구글맵을 실제로 쓰는 화면이 아직 없으므로 이 시점에는 지도 관련 변화가 없는 게 정상이다.

- [ ] **Step 7: 버전 올리고 커밋**

`local.properties`는 gitignore 대상이라 커밋에 포함되지 않는다 — 나머지 3개 파일만 스테이징.

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml
git commit -m "$(cat <<'EOF'
구글맵 SDK 의존성 및 API 키 매니페스트 설정 추가 (vX.Y.Z)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X7KasCSYPtL7EZstLv3KgP
EOF
)"
```

---

## Task 3: 데이터 모델 — RoundEntity.mapProvider, Room 마이그레이션, DAO/Repository

**Files:**
- Modify: `app/src/main/kotlin/com/golfrecorder/data/local/entity/RoundEntity.kt`
- Modify: `app/src/main/kotlin/com/golfrecorder/data/local/Migrations.kt`
- Modify: `app/src/main/kotlin/com/golfrecorder/data/local/AppDatabase.kt`
- Modify: `app/src/main/kotlin/com/golfrecorder/data/local/dao/RoundDao.kt`
- Modify: `app/src/main/kotlin/com/golfrecorder/data/repository/RoundRepository.kt`

**Interfaces:**
- Consumes: `MapProvider` enum (Task 1) — 저장은 `MapProvider.KAKAO.name`/`MapProvider.GOOGLE.name` 문자열로 한다(기존 `locationSource` 패턴과 동일).
- Produces: `RoundRepository.startRound(courseId, courseName, playedAt, locationSource, mapProvider)`, `RoundRepository.getMapProvider(roundId): String?` — Task 6이 이 두 함수를 쓴다.

- [ ] **Step 1: RoundEntity에 컬럼 추가**

`app/src/main/kotlin/com/golfrecorder/data/local/entity/RoundEntity.kt`의 마지막 필드(`locationSource`) 다음에 추가:
```kotlin
    /** MapProvider.name ("KAKAO"/"GOOGLE") — 이 라운드 지도를 어느 프로바이더로
     * 그릴지. 코스 선택 화면에서 라운드 시작 시 한 번 정하면 고정된다(리뷰할 때도
     * 같은 값을 다시 읽어서 그 프로바이더로 그린다). */
    val mapProvider: String = "KAKAO",
```

- [ ] **Step 2: 마이그레이션 작성**

`app/src/main/kotlin/com/golfrecorder/data/local/Migrations.kt` 파일 맨 끝(`MIGRATION_16_17` 다음)에 추가:
```kotlin

/** 라운드마다 지도를 카카오맵/구글맵 중 무엇으로 그릴지 고정해서 저장한다
 * (해외 라운딩은 카카오맵이 커버를 못 해서 구글맵이 필요하다). 순수 컬럼 추가라
 * 기존 데이터에는 영향이 없다 — 기존 라운드는 전부 국내 기록이므로 지금까지와
 * 동일한 'KAKAO' 기준으로 채워진다. */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE rounds ADD COLUMN mapProvider TEXT NOT NULL DEFAULT 'KAKAO'")
    }
}
```

- [ ] **Step 3: AppDatabase 버전/마이그레이션 등록**

`app/src/main/kotlin/com/golfrecorder/data/local/AppDatabase.kt`에서 `version = 17`을 `version = 18`로 바꾸고, `.addMigrations(...)` 목록 끝에 `MIGRATION_17_18`을 추가:
```kotlin
                    .addMigrations(
                        MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15,
                        MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18,
                    )
```

- [ ] **Step 4: RoundDao에 조회 쿼리 추가**

`app/src/main/kotlin/com/golfrecorder/data/local/dao/RoundDao.kt`의 `getLocationSource` 함수 다음에 추가:
```kotlin

    @Query("SELECT mapProvider FROM rounds WHERE id = :roundId")
    suspend fun getMapProvider(roundId: Long): String?
```

- [ ] **Step 5: RoundRepository 수정**

`app/src/main/kotlin/com/golfrecorder/data/repository/RoundRepository.kt`에서 `startRound`를 다음으로 교체:
```kotlin
    suspend fun startRound(
        courseId: Long,
        courseName: String,
        playedAt: Long,
        locationSource: String,
        mapProvider: String,
    ): Long =
        roundDao.insertRound(
            RoundEntity(
                courseId = courseId,
                courseName = courseName,
                playedAt = playedAt,
                locationSource = locationSource,
                mapProvider = mapProvider,
            )
        )
```
그리고 `getLocationSource` passthrough 함수 다음에 추가:
```kotlin

    suspend fun getMapProvider(roundId: Long): String? = roundDao.getMapProvider(roundId)
```

- [ ] **Step 6: startRound 호출부 임시 수정**

이 시점에는 `CourseSelectViewModel.startRound`가 아직 `mapProvider`를 안 넘긴다(Task 6에서 UI와 함께 처리). 컴파일이 깨지지 않도록, `app/src/main/kotlin/com/golfrecorder/ui/course/CourseSelectScreen.kt`의 `roundRepository.startRound(courseId, courseName, System.currentTimeMillis(), locationSource.name)` 호출에 `, "KAKAO"`를 임시로 추가해서 `roundRepository.startRound(courseId, courseName, System.currentTimeMillis(), locationSource.name, "KAKAO")`로 바꾼다. (Task 6에서 이 임시값을 실제 스위치 상태로 교체한다.)

- [ ] **Step 7: 빌드 확인**

```bash
cd "C:/github/golf-round-tracker"
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --console=plain
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: 실기기 마이그레이션 검증 (필수 — CLAUDE.md 규칙)**

기존 데이터가 있는 폰에 `adb install -r`로 설치해서 업그레이드 경로가 실제로 동작하는지 확인한다. 재설치(`uninstall` 후 `install`)로 우회 검증하지 않는다 — 그러면 DB가 초기화돼 마이그레이션이 전혀 검증되지 않는다.

```bash
export PATH="$PATH:/c/Users/dayfl/AppData/Local/Android/Sdk/platform-tools"
cd "C:/github/golf-round-tracker"
./gradlew :app:assembleDebug --console=plain
adb -s R3CW30CW70Z shell am force-stop com.golfrecorder
adb -s R3CW30CW70Z install -r app/build/outputs/apk/debug/app-debug.apk
adb -s R3CW30CW70Z shell am start -n com.golfrecorder/.MainActivity
```

앱이 크래시 없이 뜨는지 확인한 뒤, DB를 직접 열어 검증한다:
```bash
mkdir -p /tmp/gmap-migration-check
adb -s R3CW30CW70Z exec-out run-as com.golfrecorder cat //data/data/com.golfrecorder/databases/golf_recorder.db > /tmp/gmap-migration-check/golf_recorder.db
adb -s R3CW30CW70Z exec-out run-as com.golfrecorder cat //data/data/com.golfrecorder/databases/golf_recorder.db-wal > /tmp/gmap-migration-check/golf_recorder.db-wal
adb -s R3CW30CW70Z exec-out run-as com.golfrecorder cat //data/data/com.golfrecorder/databases/golf_recorder.db-shm > /tmp/gmap-migration-check/golf_recorder.db-shm
python - <<'EOF'
import sqlite3
con = sqlite3.connect("/tmp/gmap-migration-check/golf_recorder.db")
cur = con.cursor()
cur.execute("PRAGMA user_version")
print("user_version:", cur.fetchone())
cur.execute("SELECT COUNT(*), SUM(CASE WHEN mapProvider = 'KAKAO' THEN 1 ELSE 0 END) FROM rounds")
print("total rounds, KAKAO count (should match):", cur.fetchone())
cur.execute("PRAGMA integrity_check")
print("integrity_check:", cur.fetchone())
EOF
```
Expected: `user_version`이 18(Room이 내부적으로 version 그대로 기록), 전체 라운드 수와 KAKAO 개수가 정확히 같음(기존 행 전부 기본값으로 채워짐, 데이터 유실 없음), `integrity_check`가 `ok`.

주: `adb exec-out`을 안 쓰고 `adb shell cat ...`으로 가져오면 Windows에서 바이너리가 CRLF로 깨진다 — 반드시 `exec-out`을 쓴다. 경로 앞 `//`는 Git-Bash의 절대경로 변환을 피하기 위함이다.

- [ ] **Step 9: 버전 올리고 커밋**

```bash
git add app/src/main/kotlin/com/golfrecorder/data/local/entity/RoundEntity.kt \
        app/src/main/kotlin/com/golfrecorder/data/local/Migrations.kt \
        app/src/main/kotlin/com/golfrecorder/data/local/AppDatabase.kt \
        app/src/main/kotlin/com/golfrecorder/data/local/dao/RoundDao.kt \
        app/src/main/kotlin/com/golfrecorder/data/repository/RoundRepository.kt \
        app/src/main/kotlin/com/golfrecorder/ui/course/CourseSelectScreen.kt \
        app/build.gradle.kts
git commit -m "$(cat <<'EOF'
라운드별 지도 프로바이더(카카오/구글) 선택값 저장 (vX.Y.Z)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X7KasCSYPtL7EZstLv3KgP
EOF
)"
```

---

## Task 4: PersistentCourseMap을 카카오/구글 디스패처로 리팩터링

**Files:**
- Modify: `app/src/main/kotlin/com/golfrecorder/ui/map/PersistentMap.kt`

**Interfaces:**
- Consumes: `MapProvider` enum (Task 1).
- Produces: `MapRequest.provider: MapProvider` 필드. `PersistentKakaoMap(state: MapSlotState)` composable(기존 `PersistentCourseMap`의 카카오 로직을 그대로 옮긴 것 + provider 필터). `PersistentCourseMap(state: MapSlotState)`가 `PersistentKakaoMap`+`PersistentGoogleMap`을 함께 마운트하는 디스패처. Task 5가 `PersistentGoogleMap`을 이 파일이 아니라 `GooglePersistentMap.kt`에 구현하고, 이 디스패처가 그걸 호출한다.

- [ ] **Step 1: MapRequest에 provider 필드 추가**

`MapRequest` 클래스(Task 1에서 tapToSetGreen/onGreenTap 제거된 이후 상태)에 필드 추가 — `cameraKey: String,` 바로 다음 줄에:
```kotlin
    val provider: MapProvider,
```
파일 상단 import에 추가:
```kotlin
import com.golfrecorder.domain.model.MapProvider
```

- [ ] **Step 2: CourseMapSlot 시그니처/본문에 provider 스레딩**

`CourseMapSlot` 함수 파라미터에 추가 (`cameraKey: String,` 다음):
```kotlin
    provider: MapProvider,
```
`LaunchedEffect`의 키 목록에 `provider`를 추가하고, `MapRequest(...)` 생성 호출에 `provider = provider,`를 추가한다 (`cameraKey = cameraKey,` 다음 줄).

- [ ] **Step 3: 기존 PersistentCourseMap을 PersistentKakaoMap으로 개명 + provider 필터**

함수 이름을 `PersistentCourseMap`에서 `PersistentKakaoMap`으로 바꾼다. 함수 본문 맨 앞부분, `val request = state.request` 다음 줄에 추가:
```kotlin
    val kakaoRequest = request?.takeIf { it.provider == MapProvider.KAKAO }
```
그리고 이 함수 안에서 지금까지 `request`를 읽던 자리(`request?.size`, `request?.cameraKey`, `request?.greenLocation` 등 — 카메라 이동/오버레이 그리기에 쓰이는 모든 참조, `centeredCameraKey` 비교 포함) 전부를 `kakaoRequest`로 바꾼다. 정확히는 다음 줄들이다:
- `val size = request?.size?.takeIf { it.width > 0 } ?: state.lastSize...` → `kakaoRequest?.size`
- `val offset = if (request != null && request.cameraKey == centeredCameraKey) { request.offset } else {...}` → `kakaoRequest`
- `val cameraKey = request?.cameraKey` → `kakaoRequest?.cameraKey`
- `val center = if (request?.preferCurrentLocation == true) { request.currentLocation ?: request.greenLocation } else { request?.greenLocation ?: request?.shots?.firstOrNull()?...}` → `kakaoRequest`
- `val fitPoints: List<AppLatLng> = if (request != null && request.shots.isNotEmpty()) {...}` → `kakaoRequest`
- `val recenterSignal = request?.recenterSignal ?: 0` → `kakaoRequest?.recenterSignal ?: 0`
- `val target = request?.currentLocation` → `kakaoRequest?.currentLocation`
- `val key = request?.cameraKey` (recenter 이펙트 안) → `kakaoRequest?.cameraKey`
- 마지막 `LaunchedEffect(kakaoMapState.value, request?.greenLocation, request?.shots, request?.penalties) { ... val active = request ?: return@LaunchedEffect; drawOverlays(map, active.greenLocation, active.shots, active.penalties) }` → 키와 `request` 참조를 전부 `kakaoRequest`로.

이렇게 하면 `request.provider == GOOGLE`일 때 `kakaoRequest`가 `null`이 되어, 이 카카오 뷰는 기존에 "요청이 없을 때" 하던 것과 똑같이(화면 밖에 숨겨진 채 유지) 동작한다 — 즉 다른 프로바이더가 선택된 라운드에서는 카카오 뷰가 조용히 비활성 상태를 유지한다.

- [ ] **Step 4: 새 디스패처 PersistentCourseMap 작성**

파일 끝(또는 원래 `PersistentCourseMap`이 있던 자리)에 추가:
```kotlin
/**
 * 앱에 하나뿐인 지도 슬롯의 진입점. 카카오/구글 지도 뷰를 둘 다 마운트해두고,
 * 각자 [MapRequest.provider]가 자신과 다르면 스스로 화면 밖에 숨는다 — 매 라운드
 * 전환마다 지도 엔진을 새로 만들지 않기 위해서다(카카오 SDK가 MapView를 반복
 * 생성/파괴하면 GL 컨텍스트가 깨지는 버그가 있어 이 프로젝트는 지도를 앱 내내
 * 하나만 유지하는 패턴을 쓴다 — 구글 쪽도 프로바이더 전환 시 같은 문제를 피하려고
 * 동일한 패턴을 그대로 따른다).
 */
@Composable
fun PersistentCourseMap(state: MapSlotState) {
    PersistentKakaoMap(state)
    PersistentGoogleMap(state)
}
```

- [ ] **Step 5: RoundPlayScreen.kt 호출부에 provider 전달**

`app/src/main/kotlin/com/golfrecorder/ui/round/RoundPlayScreen.kt`의 `CourseMapSlot(...)` 호출에 `provider = viewModel.mapProvider,`를 추가한다 (`cameraKey = "round-${viewModel.roundId}-hole-${viewModel.currentHoleNumber}",` 다음 줄). `viewModel.mapProvider`는 Task 6에서 `RoundPlayViewModel`에 추가하므로, 이 태스크 시점에는 아직 없다 — 컴파일이 깨지지 않도록 임시로 `provider = com.golfrecorder.domain.model.MapProvider.KAKAO,`를 대신 넣는다 (Task 6에서 `viewModel.mapProvider`로 교체).

- [ ] **Step 6: 빌드 + 실기기 회귀 확인**

```bash
cd "C:/github/golf-round-tracker"
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --console=plain
```
`PersistentGoogleMap`이 아직 Task 5에서 만들어지지 않았으므로, 이 시점에는 **컴파일이 실패한다(정상)** — `unresolved reference: PersistentGoogleMap`. 이 태스크는 여기서 멈추지 않고, Task 5 착수 전 임시로 최소 스텁을 하나 만들어서 빌드를 통과시킨다:

`app/src/main/kotlin/com/golfrecorder/ui/map/GooglePersistentMap.kt` (임시 스텁, Task 5가 실제 구현으로 완전히 교체):
```kotlin
package com.golfrecorder.ui.map

import androidx.compose.runtime.Composable

@Composable
fun PersistentGoogleMap(state: MapSlotState) {
    // Task 5에서 실제 구현으로 교체된다.
}
```

이 스텁 파일도 이 태스크의 커밋에 포함시킨다.

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --console=plain
./gradlew :app:assembleDebug --console=plain
export PATH="$PATH:/c/Users/dayfl/AppData/Local/Android/Sdk/platform-tools"
adb -s R3CW30CW70Z shell am force-stop com.golfrecorder
adb -s R3CW30CW70Z install -r app/build/outputs/apk/debug/app-debug.apk
```
Expected: `BUILD SUCCESSFUL`, 테스트 통과. 앱을 열어 기존 카카오 지도가 (모든 라운드가 아직 `mapProvider = KAKAO` 기본값이므로) 평소처럼 완전히 정상 동작하는지 반드시 확인한다 — 이 태스크는 기존 지도 코드를 옮기고 조건을 추가하는 리팩터링이라, 여기서 회귀가 생기면 전체 사용자에게 영향을 준다.

- [ ] **Step 7: 버전 올리고 커밋**

```bash
git add app/src/main/kotlin/com/golfrecorder/ui/map/PersistentMap.kt \
        app/src/main/kotlin/com/golfrecorder/ui/map/GooglePersistentMap.kt \
        app/src/main/kotlin/com/golfrecorder/ui/round/RoundPlayScreen.kt \
        app/build.gradle.kts
git commit -m "$(cat <<'EOF'
PersistentCourseMap을 카카오/구글 디스패처로 분리 (vX.Y.Z)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X7KasCSYPtL7EZstLv3KgP
EOF
)"
```

---

## Task 5: 구글맵 뷰 구현 (PersistentGoogleMap + GoogleCourseMapView)

**Files:**
- Modify: `app/src/main/kotlin/com/golfrecorder/ui/map/GooglePersistentMap.kt` (Task 4의 스텁을 실제 구현으로 완전히 교체)
- Create: `app/src/main/kotlin/com/golfrecorder/ui/map/GoogleCourseMapView.kt`

**Interfaces:**
- Consumes: `MapSlotState`/`MapRequest`(Task 1, 4), `ShotPoint`/`PenaltyPoint`(기존, `CourseMapView.kt`에 정의돼 있고 프로바이더 무관 — 그대로 재사용), `circlePoints()`/`haversineMeters()`(기존 `com.golfrecorder.util`, 프로바이더 무관 유틸 — 그대로 재사용), 기존 드로어블(`ic_dot_red`/`ic_dot_blue`/`ic_dot_yellow`/`ic_penalty_ob`/`ic_penalty_hazard`).
- Produces: `PersistentGoogleMap(state: MapSlotState)` composable, `drawOverlaysGoogle(map: GoogleMap, greenLocation: AppLatLng?, shots: List<ShotPoint>, penalties: List<PenaltyPoint>)` 함수 — Task 4의 디스패처가 이미 `PersistentGoogleMap`을 호출하도록 돼 있다(재교체만 하면 됨).

- [ ] **Step 1: GoogleCourseMapView.kt — 오버레이 그리기**

```kotlin
package com.golfrecorder.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.golfrecorder.domain.model.PenaltyType
import com.golfrecorder.domain.model.ShotPhase
import com.golfrecorder.location.LatLng as AppLatLng
import com.golfrecorder.util.circlePoints
import com.golfrecorder.util.haversineMeters
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolygonOptions
import com.google.android.gms.maps.model.PolylineOptions
import kotlin.math.roundToInt

// CourseMapView.kt와 동일한 색/반경 상수를 쓴다 — 두 SDK의 타입이 달라 그리는 코드
// 자체는 공유하지 않지만, 시각적으로는 카카오 쪽과 똑같이 보여야 하므로 값만 맞춘다.
private const val SHOT_RED = "#E53935"
private const val SHOT_BLUE = "#1E88E5"
private const val SHOT_YELLOW = "#FFEB3B"
private const val GREEN_RADIUS_METERS = 2.0
private const val GREEN_FILL_COLOR = "#CCFFEB3B"
private const val DISTANCE_LABEL_TEXT_SIZE_PX = 36f
private const val DISTANCE_LABEL_STROKE_WIDTH_PX = 6f

private fun ShotPhase.order(): Int = when (this) {
    ShotPhase.TO_GREEN -> 0
    ShotPhase.SHORT_GAME -> 1
    ShotPhase.PUTT -> 2
}

private fun dotIconFor(phase: ShotPhase): Int = when (phase) {
    ShotPhase.TO_GREEN -> com.golfrecorder.R.drawable.ic_dot_red
    ShotPhase.SHORT_GAME -> com.golfrecorder.R.drawable.ic_dot_blue
    ShotPhase.PUTT -> com.golfrecorder.R.drawable.ic_dot_yellow
}

/** 구글맵엔 "지도 좌표에 붙는 텍스트 라벨"이 없어서, 캔버스로 흰 글씨+검은 외곽선
 * 비트맵을 직접 그려 마커 아이콘으로 쓴다 — 카카오 쪽 거리 라벨과 같은 스타일. */
private fun distanceLabelBitmap(text: String): Bitmap {
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = DISTANCE_LABEL_TEXT_SIZE_PX
        textAlign = Paint.Align.CENTER
    }
    val strokePaint = Paint(fillPaint).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = DISTANCE_LABEL_STROKE_WIDTH_PX
    }
    val textWidth = fillPaint.measureText(text)
    val width = (textWidth + DISTANCE_LABEL_STROKE_WIDTH_PX * 2).roundToInt().coerceAtLeast(1)
    val height = (DISTANCE_LABEL_TEXT_SIZE_PX * 1.4f).roundToInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val baselineY = height * 0.75f
    val centerX = width / 2f
    canvas.drawText(text, centerX, baselineY, strokePaint)
    canvas.drawText(text, centerX, baselineY, fillPaint)
    return bitmap
}

internal fun drawOverlaysGoogle(
    map: GoogleMap,
    greenLocation: AppLatLng?,
    shots: List<ShotPoint>,
    penalties: List<PenaltyPoint>,
) {
    map.clear()

    if (greenLocation != null) {
        val circleLatLngs = circlePoints(greenLocation.lat, greenLocation.lng, GREEN_RADIUS_METERS)
            .map { (lat, lng) -> LatLng(lat, lng) }
        map.addPolygon(
            PolygonOptions()
                .addAll(circleLatLngs)
                .fillColor(Color.parseColor(GREEN_FILL_COLOR))
                .strokeWidth(0f)
        )
    }

    val segments = (0 until shots.size - 1)
        .map { i -> shots[i] to shots[i + 1] }
        .filterNot { (from, to) -> to.phase.order() < from.phase.order() }

    segments.forEach { (from, to) ->
        val color = when (from.phase) {
            ShotPhase.TO_GREEN -> Color.parseColor(SHOT_RED)
            ShotPhase.SHORT_GAME -> Color.parseColor(SHOT_BLUE)
            ShotPhase.PUTT -> Color.parseColor(SHOT_YELLOW)
        }
        map.addPolyline(
            PolylineOptions()
                .add(LatLng(from.lat, from.lng), LatLng(to.lat, to.lng))
                .color(color)
                .width(5f)
        )
    }

    val dotDescriptors = mutableMapOf<Int, BitmapDescriptor>()
    fun descriptorFor(drawableRes: Int): BitmapDescriptor =
        dotDescriptors.getOrPut(drawableRes) { BitmapDescriptorFactory.fromResource(drawableRes) }

    shots.forEach { shot ->
        map.addMarker(
            MarkerOptions()
                .position(LatLng(shot.lat, shot.lng))
                .icon(descriptorFor(dotIconFor(shot.phase)))
                .anchor(0.5f, 0.5f)
        )
    }

    segments.forEach { (from, to) ->
        val distanceMeters = haversineMeters(from.lat, from.lng, to.lat, to.lng)
        val midLat = (from.lat + to.lat) / 2
        val midLng = (from.lng + to.lng) / 2
        val bitmap = distanceLabelBitmap("${distanceMeters.roundToInt()}m")
        map.addMarker(
            MarkerOptions()
                .position(LatLng(midLat, midLng))
                .icon(BitmapDescriptorFactory.fromBitmap(bitmap))
                .anchor(0.5f, 0.5f)
        )
    }

    penalties.forEach { penalty ->
        val drawableRes = if (penalty.type == PenaltyType.OB) {
            com.golfrecorder.R.drawable.ic_penalty_ob
        } else {
            com.golfrecorder.R.drawable.ic_penalty_hazard
        }
        map.addMarker(
            MarkerOptions()
                .position(LatLng(penalty.lat, penalty.lng))
                .icon(descriptorFor(drawableRes))
                .anchor(0.5f, 0.5f)
        )
    }
}
```

- [ ] **Step 2: GooglePersistentMap.kt — 지도 뷰 (Task 4의 스텁을 완전히 교체)**

```kotlin
package com.golfrecorder.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.golfrecorder.domain.model.MapProvider
import com.golfrecorder.location.LatLng as AppLatLng
import com.golfrecorder.util.haversineMeters
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds

// 구글 표준 웹 메르카토르 줌 기준 추정치 — 카카오 MAP_ZOOM_LEVEL과 같은 이유로
// 실측이 아닌 추정값이다. 17~18이면 골프 홀 하나(약 300~600m 폭)가 화면에 들어오는
// 범위라 17로 시작하고, 필드 테스트에서 다르면 조정한다.
private const val GOOGLE_MAP_ZOOM_LEVEL = 17f

@Composable
fun PersistentGoogleMap(state: MapSlotState) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val mapViewState = remember { mutableStateOf<MapView?>(null) }
    val googleMapState = remember { mutableStateOf<GoogleMap?>(null) }

    val request = state.request
    val googleRequest = request?.takeIf { it.provider == MapProvider.GOOGLE }

    val configuration = LocalConfiguration.current
    val defaultSize = with(density) {
        IntSize(configuration.screenWidthDp.dp.roundToPx(), MAP_HEIGHT.roundToPx())
    }
    val size = googleRequest?.size?.takeIf { it.width > 0 }
        ?: state.lastSize.takeIf { it.width > 0 }
        ?: defaultSize

    var centeredCameraKey by remember { mutableStateOf<String?>(null) }
    val offset = if (googleRequest != null && googleRequest.cameraKey == centeredCameraKey) {
        googleRequest.offset
    } else {
        IntOffset(0, HIDDEN_OFFSET_Y)
    }

    AndroidView(
        modifier = Modifier
            .offset { offset }
            .size(
                width = with(density) { size.width.toDp() },
                height = with(density) { size.height.toDp() },
            ),
        factory = { context ->
            MapView(context).also { mapView ->
                mapViewState.value = mapView
                mapView.onCreate(null)
                mapView.getMapAsync { map ->
                    map.mapType = GoogleMap.MAP_TYPE_SATELLITE
                    googleMapState.value = map
                }
            }
        },
    )

    val cameraKey = googleRequest?.cameraKey
    val center = if (googleRequest?.preferCurrentLocation == true) {
        googleRequest.currentLocation ?: googleRequest.greenLocation
    } else {
        googleRequest?.greenLocation
            ?: googleRequest?.shots?.firstOrNull()?.let { AppLatLng(it.lat, it.lng) }
    }
    val fitPoints: List<AppLatLng> = if (googleRequest != null && googleRequest.shots.isNotEmpty()) {
        buildList {
            googleRequest.greenLocation?.let { add(it) }
            googleRequest.shots.forEach { add(AppLatLng(it.lat, it.lng)) }
        }
    } else {
        emptyList()
    }
    LaunchedEffect(googleMapState.value, cameraKey, center, fitPoints) {
        val map = googleMapState.value ?: return@LaunchedEffect
        if (cameraKey == null) return@LaunchedEffect
        val lats = fitPoints.map { it.lat }
        val lngs = fitPoints.map { it.lng }
        val spanMeters = if (fitPoints.size >= 2) {
            haversineMeters(lats.min(), lngs.min(), lats.max(), lngs.max())
        } else {
            0.0
        }
        if (fitPoints.size >= 2 && spanMeters >= MIN_FIT_SPAN_METERS) {
            val bounds = LatLngBounds.Builder().apply {
                fitPoints.forEach { include(LatLng(it.lat, it.lng)) }
            }.build()
            val paddingPx = with(density) { MAP_FIT_PADDING.roundToPx() }
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, paddingPx))
            centeredCameraKey = cameraKey
        } else if (center != null) {
            map.moveCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(center.lat, center.lng), GOOGLE_MAP_ZOOM_LEVEL)
            )
            centeredCameraKey = cameraKey
        }
    }

    val recenterSignal = googleRequest?.recenterSignal ?: 0
    LaunchedEffect(googleMapState.value, recenterSignal) {
        val map = googleMapState.value ?: return@LaunchedEffect
        val target = googleRequest?.currentLocation
        val key = googleRequest?.cameraKey
        if (recenterSignal > 0 && target != null) {
            map.moveCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(target.lat, target.lng), GOOGLE_MAP_ZOOM_LEVEL)
            )
            if (key != null) centeredCameraKey = key
        }
    }

    LaunchedEffect(googleMapState.value, googleRequest?.greenLocation, googleRequest?.shots, googleRequest?.penalties) {
        val map = googleMapState.value ?: return@LaunchedEffect
        val active = googleRequest ?: return@LaunchedEffect
        drawOverlaysGoogle(map, active.greenLocation, active.shots, active.penalties)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mapViewState.value?.onStart()
                    mapViewState.value?.onResume()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    mapViewState.value?.onPause()
                    mapViewState.value?.onStop()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
```

이 코드는 `PersistentMap.kt`의 `MAP_HEIGHT`, `HIDDEN_OFFSET_Y`, `MAP_FIT_PADDING`, `MIN_FIT_SPAN_METERS`를 그대로 참조한다 — 전부 `internal`/`val` 최상위 선언이라 같은 패키지(`com.golfrecorder.ui.map`)에서 import 없이 바로 쓸 수 있다.

- [ ] **Step 3: 빌드 확인**

```bash
cd "C:/github/golf-round-tracker"
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --console=plain
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: 실기기 확인 — 구글맵으로 지도가 실제로 뜨는지**

`local.properties`의 `google.maps.api.key`가 비어 있으면 이 단계에서 실제 위성 타일은 안 뜨고 회색 지도나 워터마크만 보일 수 있다 — 그래도 크래시 없이 `PersistentGoogleMap`이 마운트/그려지는지, 마커/선 오버레이 로직이 예외 없이 도는지는 확인 가능하다. 키가 있으면 실제 위성 이미지까지 확인한다.

```bash
export PATH="$PATH:/c/Users/dayfl/AppData/Local/Android/Sdk/platform-tools"
./gradlew :app:assembleDebug --console=plain
adb -s R3CW30CW70Z shell am force-stop com.golfrecorder
adb -s R3CW30CW70Z install -r app/build/outputs/apk/debug/app-debug.apk
```
이 시점에는 아직 UI에서 구글맵을 선택할 방법이 없으므로(Task 6에서 추가), 실기기 확인은 "크래시 없이 기존 카카오 지도가 정상 동작하는지"(회귀 확인)로 충분하다. 구글맵이 실제로 화면에 뜨는지는 Task 6에서 종단간으로 확인한다.

- [ ] **Step 5: 버전 올리고 커밋**

```bash
git add app/src/main/kotlin/com/golfrecorder/ui/map/GooglePersistentMap.kt \
        app/src/main/kotlin/com/golfrecorder/ui/map/GoogleCourseMapView.kt \
        app/build.gradle.kts
git commit -m "$(cat <<'EOF'
구글맵 지도 뷰 구현 (샷 마커/구간 선/거리 라벨/자동 줌) (vX.Y.Z)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X7KasCSYPtL7EZstLv3KgP
EOF
)"
```

---

## Task 6: UI 연결 — 코스 선택 스위치 + RoundPlayScreen provider 전달 (엔드투엔드 완성)

**Files:**
- Modify: `app/src/main/kotlin/com/golfrecorder/ui/course/CourseSelectScreen.kt`
- Modify: `app/src/main/kotlin/com/golfrecorder/ui/round/RoundPlayScreen.kt`

**Interfaces:**
- Consumes: `RoundRepository.startRound(..., mapProvider)`/`getMapProvider(roundId)` (Task 3), `MapProvider` enum (Task 1), `CourseMapSlot`의 `provider` 파라미터(Task 4).
- Produces: 없음 — 이 태스크로 기능이 종단간 완성된다.

- [ ] **Step 1: CourseSelectScreen — 스위치 추가**

`app/src/main/kotlin/com/golfrecorder/ui/course/CourseSelectScreen.kt` 상단에 추가:
```kotlin
import com.golfrecorder.domain.model.MapProvider
```
`PREFS_NAME`/`KEY_LAST_USE_WATCH_LOCATION` 상수 다음에 추가:
```kotlin
private const val KEY_LAST_USE_GOOGLE_MAP = "lastUseGoogleMap"
```

`CourseSelectViewModel.startRound`를 다음으로 교체:
```kotlin
    fun startRound(
        courseId: Long,
        courseName: String,
        useWatchLocation: Boolean,
        useGoogleMap: Boolean,
        onStarted: (roundId: Long) -> Unit,
    ) {
        viewModelScope.launch {
            val locationSource = if (useWatchLocation) LocationSource.WATCH else LocationSource.PHONE
            val mapProvider = if (useGoogleMap) MapProvider.GOOGLE else MapProvider.KAKAO
            val roundId = roundRepository.startRound(
                courseId, courseName, System.currentTimeMillis(),
                locationSource.name, mapProvider.name,
            )
            onStarted(roundId)
        }
    }
```
(이 함수는 Task 3의 Step 6에서 `"KAKAO"`로 임시 하드코딩됐던 것을 여기서 실제 스위치 값으로 교체하는 것이다.)

`CourseSelectScreen` composable에서, `useWatchLocation` 상태/`setUseWatchLocation` 함수와 같은 패턴으로 추가:
```kotlin
    var useGoogleMap by remember {
        mutableStateOf(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_LAST_USE_GOOGLE_MAP, false)
        )
    }
    fun setUseGoogleMap(value: Boolean) {
        useGoogleMap = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_LAST_USE_GOOGLE_MAP, value) }
    }
```

"와치 GPS 켜기" `Row`(현재 126~140줄) 바로 다음, `HorizontalDivider()` 앞에 같은 구조로 추가:
```kotlin
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("해외 코스 (구글맵 사용)", fontWeight = FontWeight.Bold)
                    Text(
                        " (꺼져있으면 카카오맵 사용)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Switch(checked = useGoogleMap, onCheckedChange = { setUseGoogleMap(it) })
            }
```

코스 탭 클릭 핸들러의 `viewModel.startRound(course.id, course.name, useWatchLocation) { roundId -> ... }` 호출을 `viewModel.startRound(course.id, course.name, useWatchLocation, useGoogleMap) { roundId -> ... }`로 바꾼다.

- [ ] **Step 2: RoundPlayViewModel — mapProvider 상태 추가**

`app/src/main/kotlin/com/golfrecorder/ui/round/RoundPlayScreen.kt` 상단 import에 추가:
```kotlin
import com.golfrecorder.domain.model.MapProvider
```
`RoundPlayViewModel` 클래스 안, `strokesPutt` 프로퍼티 다음에 추가:
```kotlin
    var mapProvider by mutableStateOf(MapProvider.KAKAO)
        private set
```
`init { ... }` 블록 맨 앞(`loadHole(initialHoleNumber)` 앞 또는 뒤, 순서 무관)에 추가:
```kotlin
        viewModelScope.launch {
            val stored = roundRepository.getMapProvider(roundId)
            mapProvider = runCatching { MapProvider.valueOf(stored ?: "KAKAO") }.getOrDefault(MapProvider.KAKAO)
        }
```

- [ ] **Step 3: CourseMapSlot 호출부 — 임시값을 실제 상태로 교체**

Task 4의 Step 5에서 임시로 넣었던:
```kotlin
                    provider = com.golfrecorder.domain.model.MapProvider.KAKAO,
```
를:
```kotlin
                    provider = viewModel.mapProvider,
```
로 교체한다.

- [ ] **Step 4: 빌드 확인**

```bash
cd "C:/github/golf-round-tracker"
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --console=plain
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: 실기기 종단간 확인**

```bash
export PATH="$PATH:/c/Users/dayfl/AppData/Local/Android/Sdk/platform-tools"
./gradlew :app:assembleDebug --console=plain
adb -s R3CW30CW70Z shell am force-stop com.golfrecorder
adb -s R3CW30CW70Z install -r app/build/outputs/apk/debug/app-debug.apk
```

확인 순서:
1. 코스 선택 화면에서 "해외 코스 (구글맵 사용)" 스위치를 켜고 아무 코스나 골라 라운드 시작 → 구글 위성 지도가 뜨는지 확인(`google.maps.api.key`가 비어 있으면 회색/워터마크 지도가 정상이다 — 이 경우 사용자에게 키 발급이 필요하다고 안내). 샷을 몇 개 입력해 마커/선이 그려지는지 확인.
2. 그 라운드를 라운드 결과 화면에서 다시 열어(리뷰 모드) 여전히 구글맵으로 보이는지 확인.
3. 스위치를 끄고 새 라운드를 시작 → 기존 카카오맵이 평소처럼 뜨는지(회귀 없는지) 확인.
4. 기존에 있던(이번 작업 전에 기록된) 라운드를 리뷰 → 카카오맵으로 정상적으로 보이는지 확인(마이그레이션 기본값이 맞게 적용됐는지의 최종 확인).

- [ ] **Step 6: 버전 올리고 커밋**

```bash
git add app/src/main/kotlin/com/golfrecorder/ui/course/CourseSelectScreen.kt \
        app/src/main/kotlin/com/golfrecorder/ui/round/RoundPlayScreen.kt \
        app/build.gradle.kts
git commit -m "$(cat <<'EOF'
코스 선택 화면에 "해외 코스 (구글맵 사용)" 스위치 연결 (vX.Y.Z)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X7KasCSYPtL7EZstLv3KgP
EOF
)"
```

---

## 참고: Google Maps API 키

이 계획의 구현 자체는 API 키 없이도 끝까지 진행/커밋 가능하다(빌드 실패하지 않음, 지도가 회색으로 뜨거나 워터마크만 보일 뿐). 실제 위성 이미지를 확인하려면 설계 문서
(`docs/superpowers/specs/2026-09-14-google-maps-support-design.md`) 맨 아래 "Google Maps API 키 발급 안내"를 따라 키를 발급받아 `local.properties`의 `google.maps.api.key=`에 채워 넣어야 한다 — 이건 사용자가 직접 하는 절차라 이 계획의 태스크에 포함하지 않는다.
