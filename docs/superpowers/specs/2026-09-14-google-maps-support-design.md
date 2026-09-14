# 해외 라운딩용 구글맵 지원 설계

## 배경

golf-round-tracker는 지도 렌더링(위성뷰, 샷 마커, 구간 선, 거리 라벨, 자동 줌)에
카카오맵 SDK만 쓰고 있다. 카카오맵은 한국 지역만 커버해서, 해외에서 라운딩할 때는
지도가 아예 안 나오거나 부정확하다. 이 문서는 해외 라운딩 시 구글맵으로 전환해서
같은 기능(위성뷰, 마커, 선, 자동 줌)을 그대로 쓸 수 있게 하는 설계를 정리한다.

카카오맵은 코스 검색/주소 geocoding에는 쓰이지 않고, 순수하게 지도 "렌더링"에만
쓰인다 — 그래서 이번 작업의 범위는 지도 표시 레이어로 한정된다.

## 기존 구조

- `MapRequest`/`MapSlotState`/`CourseMapSlot` (`PersistentMap.kt`): 화면이 지도에
  "이 위치/크기에 이 데이터를 그려달라"고 요청하는 순수 Compose 상태. 카카오 타입을
  전혀 참조하지 않는 **프로바이더 무관 코드**다.
- `PersistentCourseMap` (`PersistentMap.kt`): 앱 루트(`MainActivity`)에 단 하나만
  마운트되는 실제 지도 뷰. 카카오맵 SDK는 `MapView`를 반복 생성/파괴하면
  `libEGL: call to OpenGL ES API with no current context` 오류로 렌더링이 깨지는
  버그가 있어서, 화면을 오갈 때 지도를 새로 만들지 않고 위치만 옮기는 방식으로
  회피하고 있다.
- `drawOverlays` (`CourseMapView.kt`): 카카오 `Label`/`Shape` API로 샷 마커, 구간
  선, 그린 원, 거리 라벨, OB/해저드 마커를 그린다.
- 호출부는 두 곳뿐이다: `MainActivity`가 `PersistentCourseMap`을 마운트하고,
  `RoundPlayScreen`(라이브 플레이와 리뷰 모두 `viewModel.isReview`로 분기)이
  `CourseMapSlot`으로 요청을 보낸다.

## 프로바이더 선택 방식

라운드 시작 시 수동 선택 — 기존 "와치 GPS 켜기" 토글과 같은 패턴이다.

- `CourseSelectScreen`에 "해외 코스 (구글맵 사용)" 스위치를 추가한다 (기본값 꺼짐 =
  카카오맵). 마지막 선택값은 `SharedPreferences`(`golf_prefs`)에 기억해뒀다가 다음
  라운드 시작 시 기본값으로 제안한다 — `lastUseWatchLocation`과 동일한 패턴.
- `RoundEntity`에 `mapProvider: String`(기본값 `"KAKAO"`) 컬럼을 추가해서, 그
  라운드가 시작될 때 고른 프로바이더를 영구 저장한다.
- **리뷰할 때도 새로 고르지 않는다.** `RoundPlayScreen`은 라이브 플레이와 리뷰를
  같은 화면에서 처리하므로, 화면 진입 시 `RoundRepository.getMapProvider(roundId)`로
  그 라운드가 시작될 때 저장된 값을 읽어와 그대로 쓴다. 한 라운드는 항상 같은
  지도로 기록되고 리뷰된다.

## 아키텍처: 두 지도 뷰를 동시에 마운트

카카오맵의 "반복 생성/파괴 금지" 제약을 프로바이더 전환에도 그대로 적용한다 —
**앱 시작 시 카카오 지도 뷰와 구글 지도 뷰를 각각 하나씩 만들어서 계속 유지**하고,
현재 라운드의 `mapProvider`에 맞는 쪽만 화면 위치로 옮기고 반대쪽은 화면 밖
(`HIDDEN_OFFSET_Y`)에 둔다. 프로바이더를 오갈 때마다(예: 국내 라운드 리뷰 ↔ 해외
라운드 리뷰를 번갈아 볼 때) 지도 엔진을 새로 만들지 않아서, 카카오 쪽에서 이미 겪은
버그 클래스를 구글 쪽에서도 원천적으로 피한다.

`MapRequest`에 `provider: MapProvider` 필드를 추가한다:

```kotlin
enum class MapProvider { KAKAO, GOOGLE }
```

`PersistentCourseMap`은 두 구현을 함께 마운트하는 얇은 디스패처가 된다:

```kotlin
@Composable
fun PersistentCourseMap(state: MapSlotState) {
    PersistentKakaoMap(state)
    PersistentGoogleMap(state)
}
```

각 구현(`PersistentKakaoMap`/`PersistentGoogleMap`)은 `state.request?.provider`가
자신의 프로바이더와 다르면 "요청 없음"과 동일하게 취급해 화면 밖에 숨긴 채로
둔다 — 기존 `centeredCameraKey != request.cameraKey`일 때 숨기는 로직과 같은 자리에
프로바이더 조건만 추가하면 된다.

## 파일 구성

- `app/src/main/kotlin/com/golfrecorder/domain/model/MapProvider.kt` (신규):
  `MapProvider` enum.
- `app/src/main/kotlin/com/golfrecorder/ui/map/PersistentMap.kt` (기존, 수정):
  - `PersistentCourseMap`을 `PersistentKakaoMap` + `PersistentGoogleMap`을 마운트하는
    디스패처로 변경.
  - 기존 카카오 관련 로직은 `PersistentKakaoMap`으로 이름만 바뀐다(로직은 거의
    동일, 프로바이더 필터링 조건만 추가).
  - `MapRequest`에 `provider: MapProvider` 필드 추가.
  - `MapRequest`의 `tapToSetGreen`/`onGreenTap` 필드 제거 (아래 "정리" 항목).
- `app/src/main/kotlin/com/golfrecorder/ui/map/GooglePersistentMap.kt` (신규):
  `PersistentGoogleMap` composable. `PersistentKakaoMap`과 같은 책임(단일 인스턴스
  유지, 화면 밖 숨김, 카메라 이동, 오버레이 다시 그리기, 생명주기 전달)을 구글맵
  API로 구현한다.
- `app/src/main/kotlin/com/golfrecorder/ui/map/CourseMapView.kt` (기존, 수정):
  `tapToSetGreen` 관련 코드 제거 외 변경 없음 — 카카오 전용으로 그대로 유지.
- `app/src/main/kotlin/com/golfrecorder/ui/map/GoogleCourseMapView.kt` (신규):
  `drawOverlaysGoogle` 함수 — `CourseMapView.kt`의 `drawOverlays`와 같은 입력
  (`ShotPoint`/`PenaltyPoint` 리스트, 그린 위치)을 받아 구글맵 API로 그린다. 색상
  상수(`SHOT_RED`/`SHOT_BLUE`/`SHOT_YELLOW` 등)와 `ShotPhase.order()` 필터링 로직은
  두 파일에서 각자 정의한다 — 두 SDK의 타입 시스템이 근본적으로 달라 공유 인터페이스를
  만드는 이득보다 비용이 크다(추상화 레이어가 오히려 두 SDK 각각의 특수성을 감추는
  용도로만 쓰이게 됨).
- `app/src/main/kotlin/com/golfrecorder/GolfRecorderApp.kt`: 변경 없음 (구글맵은
  카카오의 `KakaoMapSdk.init()`과 달리 별도 초기화 호출이 필요 없고, API 키를
  매니페스트 메타데이터로만 읽는다).

## 구글 API 매핑

| 기능 | 카카오 | 구글 |
|---|---|---|
| 위성뷰 | `map.changeMapType(MapType.SKYVIEW)` | `googleMap.mapType = GoogleMap.MAP_TYPE_SATELLITE` |
| 샷 마커 | `LabelOptions.from(...).setStyles(LabelStyle.from(R.drawable.ic_dot_red))` | `googleMap.addMarker(MarkerOptions().position(latLng).icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_dot_red)))` — **드로어블 그대로 재사용** |
| OB/해저드 마커 | `ic_penalty_ob`/`ic_penalty_hazard` | 동일 드로어블 재사용 |
| 구간 선 | `shapeLayer.addPolyline(PolylineOptions.from(...))` | `googleMap.addPolyline(PolylineOptions().add(from, to).color(...).width(5f))` |
| 그린 원 | `shapeLayer.addPolygon(...)` (좌표는 `circlePoints()` 유틸로 계산) | `googleMap.addPolygon(PolygonOptions().addAll(circleLatLngs).fillColor(...))` — `circlePoints()`/`haversineMeters()`는 이미 프로바이더 무관 유틸이라 **그대로 재사용** |
| 거리 라벨("123m") | `LabelTextBuilder`로 텍스트 라벨 | 구글엔 지도 좌표에 붙는 텍스트 라벨 API가 없다 — `Canvas`로 흰 글씨+검은 외곽선을 그린 작은 `Bitmap`을 만들어 `MarkerOptions().icon(BitmapDescriptorFactory.fromBitmap(bitmap))`으로 표시한다. 기존 `DISTANCE_LABEL_TEXT_SIZE`/`DISTANCE_LABEL_STROKE_WIDTH` 상수와 같은 시각 스타일을 캔버스로 재현. |
| 전체 샷 화면에 맞추기 | `CameraUpdateFactory.fitMapPoints(bounds, paddingPx)` | `CameraUpdateFactory.newLatLngBounds(LatLngBounds, paddingPx)` |
| 중심+고정 줌 이동 | `CameraUpdateFactory.newCenterPosition(latLng, MAP_ZOOM_LEVEL)` | `CameraUpdateFactory.newLatLngZoom(latLng, GOOGLE_MAP_ZOOM_LEVEL)` |
| 지도 준비 콜백 | `KakaoMapReadyCallback`/`MapLifeCycleCallback` | `mapView.getMapAsync { googleMap -> ... }` |
| 생명주기 전달 | `mapView.start()`/`resume()`/`pause()` | `mapView.onCreate(bundle)`/`onStart()`/`onResume()`/`onPause()`/`onStop()`/`onDestroy()` — 콜백이 더 세분화돼 있어 `DisposableEffect`에서 좀 더 많이 전달해야 한다 |

`GOOGLE_MAP_ZOOM_LEVEL`은 카카오 쪽 `MAP_ZOOM_LEVEL = 17`과 같은 이유(문서상 배율이
명확하지 않아 실측 추정치)로 추정값을 쓴다. 구글의 표준 웹 메르카토르 줌 레벨
기준으로는 17~18이 골프 홀 하나(약 300~600m 폭)를 화면에 담기 적당한 범위라, 17을
초기값으로 쓰고 실제 필드 테스트에서 조정한다 — 카카오 상수의 코멘트와 동일한 성격의
주석을 남긴다.

## 정리: 죽은 코드 제거

`MapRequest.tapToSetGreen`/`onGreenTap`은 예전에 있었던 "핀위치 지정" 기능의
잔재로, `RoundPlayScreen`에서 항상 `false`/`{}`로만 호출되는 죽은 코드다. 구글 쪽
구현에 똑같이 복제하지 않도록 이번에 `MapRequest`/`CourseMapSlot`/
`PersistentKakaoMap`에서 제거한다.

## 데이터 모델 변경

- `RoundEntity`에 `val mapProvider: String = "KAKAO"` 컬럼 추가.
- Room 마이그레이션: `MIGRATION_17_18` — `ALTER TABLE rounds ADD COLUMN mapProvider TEXT NOT NULL DEFAULT 'KAKAO'`. 기존 행은 전부 국내(카카오) 기록이므로 기본값이 정확히 맞는다 — 별도 백필 로직 불필요.
- `RoundDao`: `getMapProvider(roundId): String?` 추가 (`getLocationSource`와 동일 패턴).
- `RoundRepository`: `startRound` 시그니처에 `mapProvider: String` 파라미터 추가, `getMapProvider` passthrough 추가.
- `CourseSelectViewModel.startRound`: `useGoogleMap: Boolean` 파라미터 추가, `MapProvider.GOOGLE`/`MapProvider.KAKAO`로 변환.

## 빌드 설정

- `gradle/libs.versions.toml`: `playServicesMaps = "19.0.0"` (구현 시점에 최신 안정
  버전이 더 높으면 그 버전을 써도 된다), `play-services-maps = { group =
  "com.google.android.gms", name = "play-services-maps", version.ref =
  "playServicesMaps" }` 추가.
- `app/build.gradle.kts`: `implementation(libs.play.services.maps)` 추가.
  `local.properties`에서 `google.maps.api.key`를 읽어 매니페스트 플레이스홀더로
  주입 (카카오는 `BuildConfig` 필드로 코드에서 읽지만, 구글 클래식 Maps SDK는 키를
  매니페스트 메타데이터로만 읽으므로 주입 방식이 다르다):
  ```kotlin
  android {
      defaultConfig {
          manifestPlaceholders["googleMapsApiKey"] =
              localProperties.getProperty("google.maps.api.key", "")
      }
  }
  ```
- `app/src/main/AndroidManifest.xml`: `<application>` 안에 추가:
  ```xml
  <meta-data
      android:name="com.google.android.geo.API_KEY"
      android:value="${googleMapsApiKey}" />
  ```
- `local.properties`에 `google.maps.api.key=` 항목 추가 (값은 비워두고, 사용자가
  발급받은 키를 직접 채워 넣는다 — `kakao.native.app.key`와 동일한 패턴).

## 에러 처리

- API 키가 비어있거나 잘못된 경우: 구글 Maps SDK는 앱을 크래시시키지 않고 회색
  타일이나 "for development purposes only" 워터마크가 있는 지도를 보여주거나 로그에
  에러만 남긴다 — 카카오 지도(정상 작동)와 별개로 숨겨져 있으므로 국내 라운드 사용에는
  전혀 영향 없다.
- 오프라인 상태: 기존 `RoundPlayScreen`의 `online` 분기(오프라인이면 지도 대신 텍스트
  거리 표시)가 프로바이더와 무관하게 그대로 적용된다 — 구글맵도 타일을 받으려면
  네트워크가 필요하므로 별도 처리 불필요.

## 테스트 방침

지도 렌더링 자체는 순수 로직이 거의 없어(대부분 SDK 호출 위임) 유닛 테스트 대상이
아니다. `circlePoints()`/`haversineMeters()`처럼 이미 유닛 테스트가 있는 유틸은
변경하지 않으므로 기존 테스트가 그대로 유효하다.

검증은 실기기 수동 테스트로 한다:
1. 코스 선택 화면에서 "해외 코스 (구글맵 사용)" 켜고 라운드 시작 → 구글 위성지도가
   뜨는지, 샷 입력 시 마커/선이 정상적으로 그려지는지 확인.
2. 같은 라운드를 리뷰 모드로 다시 열었을 때 여전히 구글맵으로 보이는지 확인.
3. 스위치를 끄고 국내 라운드를 새로 시작 → 기존 카카오맵이 그대로 정상 동작하는지
   (회귀 없는지) 확인.
4. 실제 해외 GPS 좌표가 없으므로, 국내에서도 위성 이미지 자체는 전 세계 어디든 뜨는
   구글맵의 특성상 임의 좌표(예: 해외 골프장 좌표를 검색해서)로 지도가 뜨는지 확인
   가능 — 정확한 GPS 동작(샷 기록 등)은 국내에서도 동일 로직이라 위치 자체보다 지도
   렌더링 정상 여부가 핵심 확인 대상.

## Google Maps API 키 발급 안내

1. https://console.cloud.google.com 접속, 로그인.
2. 새 프로젝트 생성 (또는 기존 프로젝트 선택).
3. "API 및 서비스" → "라이브러리"에서 "Maps SDK for Android" 검색 후 사용 설정.
4. "API 및 서비스" → "사용자 인증 정보" → "사용자 인증 정보 만들기" → "API 키".
5. 생성된 키를 클릭해 "애플리케이션 제한사항"을 "Android 앱"으로 설정하고, 이 앱의
   패키지 이름(`com.golfrecorder`)과 서명 인증서 SHA-1 지문을 등록 — 지문은
   `./gradlew signingReport`로 확인 가능 (디버그 빌드는 자동 생성된 디버그
   키스토어의 지문).
6. "API 제한사항"을 "키 제한"으로 설정하고 "Maps SDK for Android"만 선택 —
   키가 유출돼도 다른 API에 쓰이지 못하게 제한.
7. 발급받은 키를 `local.properties`의 `google.maps.api.key=`에 붙여넣기.

무료 크레딧 한도 안에서는 과금 없이 쓸 수 있지만, 프로젝트에 결제 계정을 연결해야
API가 활성화되는 경우가 있다 — Google Cloud Console 안내를 따라 진행.
