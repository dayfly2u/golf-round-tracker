# golf-round-tracker 프로젝트 규칙

(워크스페이스 공통 규칙은 `C:\github\CLAUDE.md` 참고. 이 파일은 이 프로젝트에만 해당하는 규칙.)

## DB 마이그레이션 (2026-08-14 이후 필수)

2026-08-14부터 실기기에 실제 필드 라운딩 기록이 쌓이기 시작했다. **이 시점부터 저장된 라운딩 기록(rounds, hole_records, shots, penalties 등)은 절대 유실되면 안 되는 실데이터다.**

지금까지는 `AppDatabase.kt`에서 스키마를 바꿀 때마다 `fallbackToDestructiveMigration(dropAllTables = true)`로 기존 데이터를 통째로 날리고 새로 시작하는 방식이었다 (버전 8→11까지 전부 이 방식, 프리릴리즈 개인 테스트 단계라 허용됐던 컨벤션).

**앞으로는 이 방식을 쓰면 안 된다.** 스키마를 바꿔야 할 일이 생기면:

1. `fallbackToDestructiveMigration`으로 넘어가지 말고, 반드시 명시적인 `Migration(oldVersion, newVersion) { ... }` 객체를 작성해서 `.addMigrations(...)`로 등록한다.
2. 컬럼 추가처럼 단순한 변경은 `ALTER TABLE ... ADD COLUMN ...` SQL로 처리 가능. 테이블 구조가 크게 바뀌는 경우(컬럼 삭제/타입 변경/테이블 분리 등)는 임시 테이블 생성 → 데이터 복사 → 기존 테이블 삭제 → rename 패턴으로 기존 행을 보존한다.
3. 마이그레이션 작성 후에는 실기기(또는 이전 버전 DB 파일)를 대상으로 업그레이드 경로가 실제로 동작하는지, 기존 라운딩 기록이 그대로 남아있는지 확인한다 — 앱 재설치로 우회 검증하지 않는다(재설치는 DB를 초기화시키므로 마이그레이션 검증이 안 됨).
4. `exportSchema = true`이므로 `app/schemas/`에 버전별 스키마 JSON이 쌓인다 — 마이그레이션 작성 시 이전 버전 스키마 JSON을 참고해서 정확한 컬럼/타입을 맞춘다.

**절대 하면 안 되는 것**: "테스트 데이터 몇 개 정도야 괜찮겠지"라는 판단으로 `fallbackToDestructiveMigration`을 그대로 두고 버전만 올리는 것. 데이터 보존이 필요 없다고 확신이 서는 예외적 상황이라도, 사용자에게 먼저 명시적으로 확인 받은 뒤에만 destructive migration을 고려한다.
