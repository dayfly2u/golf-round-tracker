# 골프 라운드 기록 (golf-round-tracker)

라운딩 중 홀마다 "그린 근처 도달까지 타수"와 "그린~홀아웃(숏게임+퍼팅 합산) 타수"를 기록하는 안드로이드 앱.
최종 목표는 단순 스코어 계산이 아니라 **GPS로 매 타수 위치를 기록해서 실제 코스 지도 위에 라운딩 경로를 시각화**하는 것.

## 설치 방법 (APK 직접 설치)

Play 스토어에 올리는 앱이 아니라서, [Releases 페이지](https://github.com/dayfly2u/golf-round-tracker/releases)에서 APK를 직접 받아 설치합니다.

1. 폰의 브라우저(또는 이 저장소를 볼 수 있는 앱)로 [최신 릴리즈](https://github.com/dayfly2u/golf-round-tracker/releases/latest) 페이지를 연다.
2. "Assets" 목록에서 `app-debug.apk` 파일을 탭해서 다운로드한다.
3. 다운로드가 끝나면 알림을 눌러 파일을 연다.
   - 이때 "출처를 알 수 없는 앱 설치를 허용하시겠습니까?" 같은 안내가 뜨면 허용해야 설치가 진행된다 (앱마다/OS 버전마다 문구가 다를 수 있음. 보통 "설치" 버튼 옆에 "허용" 버튼이 같이 뜬다).
4. "설치" 버튼을 눌러 설치를 마친다.
5. 앱을 처음 열면 위치 권한을 물어본다 — GPS로 샷 위치를 기록하는 핵심 기능이라 "앱 사용 중에만 허용"을 선택해야 정상 동작한다.

**참고**
- Android 8.0(API 26) 이상 기기에서만 설치 가능하다.
- 이 APK는 개발용(debug) 서명 키로 빌드되어 있다. 개인적으로 여러 기기에 설치해서 쓰는 데는 문제없지만, Play 스토어에 올리는 정식 배포용은 아니다.
- 기존에 이 앱을 설치해 둔 상태에서 새 버전을 덮어 설치하면 라운드/코스 기록이 그대로 유지된다. 다만 스키마가 바뀌는 업데이트는 기기 내 데이터를 초기화할 수 있다 — 해당 릴리즈 노트에 안내가 있으면 확인할 것.

## 사용 설명서

처음 써보는 분은 [사용 설명서](docs/user-guide.md)를 참고하세요 — 코스 등록부터 라운드 결과 확인까지 화면 캡처와 함께 안내합니다.

## 지금까지 구현된 것 (1단계: 스코어 기록)

### 아키텍처
kstock-digest의 `android/` 앱과 동일한 구성으로 만듦:
- AGP 9.0.1 + Kotlin 2.2.10 + KSP + Compose BOM 2026.03.01, `minSdk 26` / `compileSdk·targetSdk 36`
- Hilt 없이 수동 `AppContainer` DI (`di/AppContainer.kt`)
- Navigation 라이브러리 없이 `sealed interface Screen` + `MainActivity`의 수동 백스택
- Room DB, 화면 단위로 `ViewModel`+`ViewModelFactory`+`@Composable`을 한 파일에 같이 둠
- 소스는 `app/src/main/kotlin/...` (java 폴더 아님)

### 데이터 모델 (Room)
- `CourseEntity` — 코스(골프장) 이름
- `HoleEntity` — 코스별 홀 번호 + 파 (코스당 한 번만 등록하면 재사용)
- `RoundEntity` — 특정 날짜에 특정 코스를 친 라운드
- `HoleRecordEntity` — 라운드의 홀별 결과: `strokesToGreen`(그린까지 타수), `strokesGreenToHoleOut`(숏게임+퍼팅 합산 타수). `totalStrokes`/`scoreToPar`/`isGreenInRegulation`은 저장하지 않고 `domain/model/HoleResult.kt`에서 항상 계산.

### 화면 흐름
```
Home(라운드 기록 목록)
 ├─ FAB "새 라운드" → CourseSelect
 │    ├─ 코스 탭 → Round 생성 → RoundPlay(1홀)
 │    └─ FAB "+" / 코스 없을 때 → CourseEdit(courseId=null)
 ├─ 코스별 "수정" → CourseEdit(courseId)
 └─ 라운드 탭 → RoundSummary
      └─ 홀 행 탭 → RoundPlay(그 홀 번호로 재진입, 수정 가능)
```
- `RoundPlay`: 이전/다음 홀 이동 시마다 현재 홀 저장. 마지막 홀에서 "완료" → `RoundSummary`
- `RoundSummary`: 18홀 표 + 총타수 + GIR% + "홈으로"

### 검증 상태
- `gradlew testDebugUnitTest` — `HoleResultTest` 통과 (총타수/스코어/GIR 경계값 케이스)
- `gradlew assembleDebug` — 빌드 성공
- 실기기(갤럭시)에 설치해서 전체 흐름(코스 추가 → 라운드 시작 → 18홀 입력 → 완료 → 결과 확인 → 홈에서 재확인) 수동 테스트 완료
- 테스트 중 발견한 버그: `RoundSummaryScreen`에서 `LazyColumn(fillMaxSize())`가 남은 공간을 다 차지해서 "홈으로" 버튼이 화면 밖(제스처 내비게이션 바 뒤)으로 밀리는 문제 → `Scaffold`의 `bottomBar`로 옮기고 `LazyColumn`을 `Modifier.weight(1f)`로 수정해서 해결

## 2단계: GPS 매 타수 기록 + 코스 지도 (구현 완료, 실기기 검증 완료)

### 구현 내용
- **`Shot` 테이블** (`data/local/entity/ShotEntity.kt`): 키는 `(roundId, holeNumber, phase, shotIndex)`. `holeRecordId`가 아니라 `roundId`+`holeNumber`를 쓰는 이유는 `HoleRecordEntity` 행이 홀을 벗어날 때만 저장되는데, 실시간 GPS 캡처는 홀에 들어온 직후부터 필요해서 처음부터 안정적인 키가 필요했기 때문.
- **그린 좌표** (`HoleEntity.greenLat/greenLng`): 코스당 한 번만 지정하면 이후 라운드에서 재사용. `RoundPlayScreen`에서 그린 좌표가 없는 홀에 들어가면 지도가 자동으로 "탭해서 그린 위치 지정" 모드가 됨.
- **스트로크 스테퍼 ↔ GPS 연동** (`ui/round/RoundPlayScreen.kt`): 새 UI를 만들지 않고 기존 `StrokeStepper` +/- 에 그대로 연결. "+"를 누르면 카운터가 즉시 올라가고 백그라운드에서 위치를 잡아 그 인덱스의 Shot을 저장, "−"는 해당 인덱스의 Shot을 삭제. `Composable` 안의 `Mutex`로 두 작업을 직렬화해서 빠르게 연속으로 +/-를 누를 때 GPS 응답이 늦게 와서 방금 지운 샷이 유령처럼 다시 생기는 경합을 막음.
- **오프라인/권한 폴백**: 위치 권한 없으면 "위치 권한이 필요합니다", 온라인이면 카카오맵 위성뷰(마커+이동경로), 오프라인이면 그린 좌표가 있을 때 하버사인 거리(`util/GeoUtil.kt`)만 텍스트로, 없으면 "그린 위치 미설정" 안내.
- **DB 마이그레이션은 파괴적으로 처리** (`fallbackToDestructiveMigration()`, version 1→2). 아직 출시 전 개인 테스트 단계라 별도 Migration 코드는 작성하지 않음 — 이 변경으로 기존 테스트 데이터(TestCC 라운드)는 재설치 시 초기화됨.
- **카카오 키**: `local.properties`의 `kakao.native.app.key`를 `app/build.gradle.kts`에서 읽어 `BuildConfig.KAKAO_NATIVE_APP_KEY`로 노출, `GolfRecorderApp.onCreate()`에서 `KakaoMapSdk.init()` 호출.
- **구글 키**: 같은 방식으로 `local.properties`의 `google.maps.api.key`를 `app/build.gradle.kts`가 읽어 매니페스트 플레이스홀더(`googleMapsApiKey`)로 노출. Google Cloud Console에서 키를 발급받는 전체 절차는 여기 문서 대신 `docs/superpowers/specs/2026-09-14-google-maps-support-design.md`를 참고.

### 실기기 검증 결과
실기기(갤럭시)에서 전체 플로우를 확인함: 위치 권한 프롬프트 → 그린 좌표 없는 홀에서 지도 탭-지정 모드 → 그린 마커 표시 → 스테퍼 "+"로 GPS 샷 기록(DB에 좌표까지 직접 대조 확인) → "−"로 해당 Shot 삭제 확인 → 그린 좌표가 코스 자산으로 다음 라운드에도 재사용됨 확인 → 비행기 모드에서 "오프라인 상태입니다" 폴백 확인.

과정에서 실제로 걸렸던 두 가지 이슈와 해결:
1. **Kakao Developers 콘솔 설정 미비 (401 → 403 → 정상)**: 처음엔 Android 플랫폼에 키 해시가 등록 안 돼 있어 `401 Unauthorized`. Logcat `k3f` 필터에 찍힌 요청 헤더의 `origin/...` 값이 정확히 등록해야 할 키 해시였음. 키 해시 등록 후에도 `403 Forbidden`이 났는데, 이건 [제품 설정] → [카카오맵] 자체가 앱에서 활성화되어 있지 않아서였음 — 활성화 후 정상 인증됨.
2. **벡터 드로어블 마커가 안 보이는 문제**: `LabelStyle.from(R.drawable.xxx)`에 vector drawable(`<vector>` XML)을 넣었더니 Kakao 네이티브 렌더러가 `unsupported image format` / `Add Image Failed`로 조용히 실패함 (크래시 없이 그냥 안 그려짐). Kakao Maps SDK의 마커 이미지 로더는 raster 비트맵만 지원하는 것으로 보임 — PowerShell의 `System.Drawing`으로 실제 PNG(`ic_green_pin.png`, `ic_dot_red.png`, `ic_dot_blue.png`)를 생성해서 교체하니 정상 렌더링됨.

Kakao Maps SDK v2의 일부 API(라벨 스타일, Polyline/MapPoints 시그니처 등)는 공식 문서에 전체 예제가 없어 최선으로 추정 후 실제 컴파일 에러 메시지로 정확한 오버로드를 확인해가며 맞췄음 (`ui/map/CourseMapView.kt` 참고).

`RoundSummaryScreen`에는 아직 지도/경로 시각화를 추가하지 않음 (범위 밖, 후속 작업).

### 조사해서 확정한 것
- **GPS 매 타수 기록**: 기술적으로 어렵지 않음. `FusedLocationProviderClient`로 스트로크 버튼을 누르는 시점마다 위치를 한 번씩 찍어서 저장. `ACCESS_FINE_LOCATION` 런타임 권한 필요.
- **코스 지도 데이터**: 카카오맵/네이버맵 둘 다 일반 지도 SDK일 뿐, 골프 코스 전용 데이터(그린/페어웨이/벙커 경계)는 제공하지 않음. 실질적으로 가능한 유일한 방법은 **위성뷰를 베이스맵으로 띄우고, 처음 그 코스를 칠 때 사용자가 위성사진 위에서 그린 위치를 직접 한 번 찍어서 저장(크라우드소싱)**.
- **카카오맵 vs 네이버맵 비교**:
  | | 카카오맵 SDK v2 (Android) | 네이버맵 SDK (Android) |
  |---|---|---|
  | 위성뷰 | 지원 (`MapType.SKYVIEW`) | 지원 (`Satellite`/`Hybrid`) |
  | 커스텀 오버레이(마커/폴리곤/경로) | 지원 (`Label`, `Polyline`) | 지원 |
  | 무료 사용량 | 개발자 계정당 처음 활성화한 앱 1개는 무료 (2026-07-21 정책 변경 후 기준). 초과분 건당 10원(2026년 말까지 80% 할인) | 2025-05-28부로 무료 사용량 완전 종료, 처음부터 유료 |
  | 오프라인 캐싱 | **명시적으로 금지** (운영정책 제5조 20항: UX 개선 목적 외 캐시·최신화 안 되는 캐시 금지) + 데브톡에 "오프라인 불가" 답변 다수 + 타일 캡처 CORS로 기술 차단 | 명확한 허용 조항 없음, 카카오와 사실상 동일한 제약 |

  → **비용 면에서 카카오맵 SDK 채택**. 단, 오프라인 캐싱이 막혀있는 게 실제 문제(골프장은 산속이라 신호 약한 곳 많음).

- **오프라인 전략 (사용자 확정)**: 신호 있을 때는 카카오맵 위성뷰를 그대로 쓰고, 신호 없을 때는 지도 배경 없이 GPS 좌표/거리 숫자만 보여주는 폴백 UI로 전환. 그린 위치(좌표)는 지도 SDK가 아니라 우리 앱 자체 DB에 영원히 저장되는 데이터라 오프라인이어도 거리 계산 자체는 문제없음.

### 카카오맵 SDK 키 발급 절차
1. [Kakao Developers](https://developers.kakao.com)에 카카오 계정으로 로그인
2. 내 애플리케이션 → 애플리케이션 추가하기 (앱 이름 예: "골프 라운드 기록")
3. 생성된 앱의 "앱 키" 탭에서 **네이티브 앱 키** 복사 — 이게 코드에 넣을 값
4. "플랫폼" 탭 → Android 플랫폼 등록
   - 패키지명: `com.golfrecorder`
   - 키 해시: 일단 비워두고 앱을 한 번 실행 → Logcat에서 `k3f` 필터로 인증 실패 로그를 보면 정확한 키 해시 값이 그대로 출력됨 → 그 값을 복사해서 등록 (지금은 디버그 키스토어 기준. 나중에 정식 배포용 키스토어를 만들면 그때 해시를 하나 더 등록)
5. 네이티브 앱 키가 발급되면 `local.properties`의 `kakao.native.app.key`에 넣기 (이미 완료됨)
6. **[제품 설정] → [카카오맵]에서 사용 설정 활성화** — 이걸 빼먹으면 키/해시가 다 맞아도 `403 Forbidden`이 남 (이미 완료됨)

## 프로젝트 구조
```
app/src/main/kotlin/com/golfrecorder/
  data/local/entity/      CourseEntity, HoleEntity(+greenLat/greenLng), RoundEntity, HoleRecordEntity, ShotEntity
  data/local/relation/     CourseWithHoles, RoundWithHoleRecords
  data/local/dto/          RoundSummary
  data/local/dao/          CourseDao, RoundDao, ShotDao
  data/local/AppDatabase.kt
  data/repository/         CourseRepository, RoundRepository, ShotRepository
  domain/model/HoleResult.kt, ShotPhase.kt
  location/LocationCapture.kt
  util/ConnectivityUtil.kt, GeoUtil.kt
  di/AppContainer.kt
  ui/navigation/Screen.kt
  ui/common/StrokeStepper.kt
  ui/map/CourseMapView.kt
  ui/history/RoundHistoryScreen.kt
  ui/course/CourseSelectScreen.kt, CourseEditScreen.kt
  ui/round/RoundPlayScreen.kt, RoundSummaryScreen.kt
  MainActivity.kt, GolfRecorderApp.kt
app/src/main/res/drawable/  ic_green_pin.png, ic_dot_red.png, ic_dot_blue.png (지도 마커, raster PNG — vector는 Kakao SDK가 못 읽음)
app/src/test/kotlin/com/golfrecorder/domain/model/HoleResultTest.kt
```

## 빌드/실행
```
./gradlew.bat testDebugUnitTest   # 유닛 테스트
./gradlew.bat assembleDebug       # 디버그 APK 빌드 (app/build/outputs/apk/debug/app-debug.apk)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
