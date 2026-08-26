# Galaxy Watch5 컴패니언 앱 설계

## 배경 / 목표

지금 라운드 진행 화면(`RoundPlayScreen`)은 그린까지 타수/숏게임+퍼팅 타수를 +/- 버튼으로 기록한다. 매 샷마다 폰을 꺼내서 이 버튼을 누르는 게 번거로워서, 손목의 갤럭시 워치5(SM-R900, Wear OS)로 타수 +/- 와 홀 이동만 할 수 있게 만든다.

**목표**: 폰을 주머니/골프백에 넣어둔 채로 워치만으로 "그린까지 타수", "숏게임+퍼팅 타수" 증감과 홀 이동이 가능해야 한다. 폰 화면이 꺼져있거나 앱이 백그라운드여도 동작해야 한다.

**비목표 (1차 버전에서 제외)**:
- OB/해저드 등 벌타 기록은 계속 폰에서만 한다. (버튼 종류가 많고, 벌타 위치는 "직전 샷 위치"를 쓰는 로직이라 워치에서 다루기엔 복잡함)
- 가격/동반자/리뷰 입력, 코스 관리, 백업/복원 등 워치와 무관한 기능은 그대로 폰 전용.
- 워치 자체 GPS는 쓰지 않는다 (아래 "GPS 위치 결정" 참고).
- 워치에서 라운드 시작/완료는 하지 않는다 — 라운드는 항상 폰에서 시작하고, 워치는 이미 시작된 라운드에 대해서만 동작한다.

## 전체 아키텍처

**Foreground Service가 Data Layer 리스너를 들고 있는 방식(채택안)을 쓴다.** 라운드가 시작되는 순간 폰에 `RoundRecordingService`(포그라운드 서비스)가 뜨고, "OO CC 라운딩 기록 중 (워치 연동)" 같은 낮은 우선순위 알림을 계속 띄운 채로 라운드 끝까지 살아있는다. 이 서비스가 `MessageClient` 리스너를 직접 들고 있다가 워치에서 오는 버튼 이벤트를 받아 처리한다.

**왜 포그라운드 서비스인가**: 매니페스트에 등록만 해두는 `WearableListenerService`(포그라운드 아님)도 이론적으로는 가능하지만, 삼성 폰은 백그라운드 프로세스를 배터리 최적화 명목으로 공격적으로 죽이는 것으로 알려져 있다. 4시간짜리 라운드 동안 샷 사이 간격이 길 때(걷는 시간 등) 이런 프로세스가 조용히 죽어서 샷이 누락되는 위험을 감수하고 싶지 않다. 포그라운드 서비스 + 상시 알림으로 프로세스 생존을 보장한다.

**배터리 영향은 낮다**: 서비스는 계속 켜져 있지만 GPS를 상시로 폴링하지 않는다. 지금 폰 UI도 버튼을 누른 순간에만 `LocationCapture.getCurrentLocation(context)`로 단발성 GPS fix를 받아오는 방식이라, 서비스도 동일하게 "워치에서 메시지가 올 때만" 단발성으로 위치를 조회한다. 평상시엔 메시지 리스너만 대기 상태.

## 서비스 생명주기

- **시작**: 라운드가 생성되는 순간(`CourseSelectViewModel.startRound` 직후) 바로 시작한다 — 폰에서 `RoundPlayScreen`을 한 번도 열지 않아도 워치만으로 기록 가능해야 하기 때문.
- **종료**: 그 라운드가 "완료"되거나 취소되면 서비스도 함께 종료한다.
- **재부팅 등 극단적 예외**: 폰이 라운드 도중 재부팅되는 경우처럼 아주 드문 케이스는 1차 버전에서 처리하지 않는다.

## GPS 위치는 어디서 결정하는가

**폰 GPS를 그대로 쓴다.** 워치 버튼은 "지금 샷을 쳤다"는 타이밍 신호만 보내고, 실제 좌표는 그 신호를 받은 폰이 그 순간 `LocationCapture.getCurrentLocation(context)`로 조회한 값을 쓴다 — 지금 폰 UI가 하는 것과 완전히 동일한 방식이고, 새 코드가 필요 없다.

(트레이드오프로 논의했던 "워치 자체 GPS를 써서 몸에 붙어있는 워치 위치를 기록"하는 방식은 워치 쪽에 위치 권한/조회 로직을 새로 만들어야 해서 1차 버전에서는 채택하지 않기로 함 — 나중에 필요해지면 재검토.)

## 데이터 흐름 / 통신 방식

요청-응답이 아니라 단방향 채널 두 개로 구성한다 (지금 폰 UI가 이미 "낙관적 갱신 후 백그라운드 저장" 방식이라 이 구조와 자연스럽게 맞는다):

- **워치 → 폰 (명령)**: `MessageClient`, path `/stroke/action`, payload는 다음 중 하나 — `INCREMENT_TO_GREEN`, `DECREMENT_TO_GREEN`, `INCREMENT_SHORT_GAME`, `DECREMENT_SHORT_GAME`, `NEXT_HOLE`, `PREV_HOLE`. Fire-and-forget이고, 처리 후 폰이 항상 최신 상태를 다시 보내주기 때문에 메시지 하나가 유실돼도 다음 상태 갱신 때 워치 화면이 스스로 맞춰진다.
- **폰 → 워치 (상태)**: `MessageClient`가 아니라 `DataClient`(path `/round-state`)를 쓴다. payload: `{ holeNumber, par, strokesToGreen, strokesGreenToHoleOut, holeCount, roundActive }`. `DataClient`는 마지막 값을 계속 들고 있다가 구독자에게 전달하는 방식이라, 워치 앱이 늦게 켜져도(라이브 업데이트를 놓쳤어도) 그 시점의 최신 상태를 바로 받을 수 있다 — 별도 폴링이 필요 없다.

## DB 스키마 변경

`rounds` 테이블에 `currentHoleNumber INTEGER NOT NULL DEFAULT 1` 컬럼을 추가한다 (순수 추가형 마이그레이션, 기존 데이터 보존 정책 그대로 — `MIGRATION_x_y` 객체로 작성).

지금은 "지금 몇 홀인지"가 `RoundPlayViewModel`의 메모리(`mutableStateOf`)에만 있어서 화면을 벗어나면 사라진다. 이 값을 DB 컬럼으로 옮겨서 폰 화면과 워치 서비스가 동시에 참조하는 단일 소스로 만든다 — 그래야 워치로 홀을 넘긴 뒤 폰을 켜도 정확한 홀이 뜨고, 반대로 폰에서 홀을 넘긴 것도 워치에 반영된다.

타수 자체는 별도 카운터 컬럼이 필요 없다 — 단, 구현 계획 작성 중 코드를 읽어보니 "행 개수를 센다"는 정확한 표현이 아니어서 바로잡는다. `ShotEntity.shotIndex`와 `PenaltyEntity.penaltyIndex`는 둘 다 "그 이벤트가 일어난 시점의 그 구간 누적 타수"를 그대로 저장한 값이다(`RoundPlayViewModel.addPenalty`가 `penaltyRepository.addPenalty(...)`를 호출할 때 `penaltyIndex` 자리에 갱신된 총 타수 `newValue`를 넘김). 즉 OB/해저드처럼 `shots` 테이블에 행이 안 생기는 이벤트도 `penaltyIndex`에 그 시점 총 타수가 남기 때문에, 특정 홀·구간(phase)의 현재 총 타수는:

```
currentTotal(phase) = max(
    shots.filter { it.phase == phase.name }.maxOfOrNull { it.shotIndex } ?: 0,
    penalties.filter { it.phase == phase.name }.maxOfOrNull { it.penaltyIndex } ?: 0,
)
```

로 두 테이블만 보고 정확히 복원된다 — 새 카운터 컬럼이 필요 없다는 원래 결론은 맞고, 공식만 이렇게 바로잡는다. 이 공식은 워치 서비스뿐 아니라 `RoundPlayViewModel` 자신도 써야 한다: 지금은 `strokesToGreen`/`strokesGreenToHoleOut`이 그 화면(ViewModel) 인스턴스가 메모리에서 직접 증감시키는 값이라, 워치가 같은 홀의 타수를 바꿔도 이미 열려 있는 폰 화면에는 반영되지 않는 문제가 있다. 이걸 `shots`/`penalties` Flow에서 위 공식으로 매번 다시 계산하는 파생값으로 바꾸면, 워치가 쓰든 폰이 쓰든 같은 테이블을 보고 항상 같은 숫자가 나온다.

## 폰 쪽 코드 변경

1. **마이그레이션**: 위 `currentHoleNumber` 컬럼 추가.
2. **`RoundPlayViewModel` 리팩터링**:
   - `currentHoleNumber`를 로컬 `mutableStateOf`가 아니라 라운드 행의 `currentHoleNumber`를 관찰하는 값으로 바꾸고, `goToHole`은 DB에 write-through 한다.
   - `strokesToGreen`/`strokesGreenToHoleOut`을 로컬에서 직접 증감시키는 대신, 위 공식으로 `shots`/`penalties` Flow에서 매번 다시 계산하는 파생값으로 바꾼다.
3. **`RoundRecordingService`(신규)**: 포그라운드 서비스. `MessageClient` 리스너 등록, 기존 `ShotRepository`/`RoundRepository`/`LocationCapture`를 그대로 재사용해서 증감·홀이동 처리(같은 공식으로 현재 타수를 계산), 처리 후 `DataClient`로 최신 상태 전송, 상시 알림 표시(탭하면 해당 라운드 화면으로 이동).
4. **서비스 시작/종료 트리거**: `CourseSelectViewModel.startRound` 직후 시작, 라운드 완료/취소 시 종료.

## 워치 앱 (신규 `:wear` Gradle 모듈)

플레이스토어에 올리지 않고 sideload로 설치하는 독립 Wear OS 앱(별도 APK, `com.golfrecorder.wear`)으로 만든다.

화면 하나로 구성:
```
[◀ 3홀 ▶]
[그린까지: 3   −   +]
[숏게임: 1   −   +]
```
- 홀 이동 화살표는 1홀/코스 마지막 홀에서 비활성화 (폰 화면과 동일한 규칙).
- 활성 라운드 상태를 아직 못 받았거나 폰이 "라운드 없음"을 보낸 경우: "라운드가 시작되지 않았습니다. 폰에서 라운드를 시작해주세요." 안내와 함께 모든 버튼 비활성화.
- 버튼을 누르면 `/stroke/action` 메시지를 보내고, 낙관적으로 화면 숫자를 먼저 바꾼 뒤 폰이 보내주는 `/round-state`로 다시 맞춘다.

## 설치 / 테스트 방법

플레이스토어 배포 없이 개발 중에는 폰에 이미 연결된 adb를 경유하는 **블루투스 디버그 브리지** 방식을 쓰기로 함(사용자 선택):
- 워치 개발자 옵션의 "블루투스를 통한 디버그"를 켜고, `adb forward tcp:4444 localabstract:/adb-hub` → `adb connect localhost:4444`로 PC의 adb가 워치에 붙는다.
- 이후 `adb -s localhost:4444 install -r wear-app-debug.apk`로 반복 설치.
- 실제 설정 화면(개발자 옵션 위치 등)은 워치를 보면서 사용자가 직접 확인해야 하는 부분 — 구현 단계에서 같이 진행.

## 알려진 한계 (1차 버전)

- 워치→폰 메시지는 fire-and-forget이라 아주 드물게 유실될 수 있음 — 다음 상태 동기화 때 자동으로 맞춰지지만, 그 사이 워치 화면이 잠깐 실제와 다를 수 있음.
- 폰이 라운드 도중 재부팅되면 서비스가 자동으로 다시 시작되지 않음 — 사용자가 앱을 다시 열어야 함 (이 경우도 앱 재실행 시 미완료 라운드가 있으면 서비스를 다시 띄우는 정도는 구현에서 고려 가능).
- OB/해저드/가격/리뷰 등은 계속 폰에서만 가능.
