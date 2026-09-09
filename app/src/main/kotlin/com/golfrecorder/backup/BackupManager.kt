package com.golfrecorder.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.golfrecorder.data.local.AppDatabase
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Room DB 파일을 그대로 복사해서 백업/복원한다. 코스/라운드/홀/샷/벌타 전부 한 파일에
 * 들어있으니, 테이블마다 JSON으로 직렬화하는 코드를 따로 만들고 스키마가 바뀔 때마다
 * 같이 유지보수하는 대신 파일 복사 하나로 항상 실제 스키마와 100% 일치하게 유지한다.
 */
object BackupManager {

    private const val DB_NAME = "golf_recorder.db"
    private val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA)
    // SQLite 파일의 첫 16바이트는 "SQLite format 3" 뒤에 널 종료 바이트 하나가 붙은
    // 고정 헤더다. 문자열 리터럴에 널 바이트를 직접 넣으면(이스케이프든 raw byte든)
    // 소스 파일이 바이너리로 인식돼 git diff가 깨지는 문제가 있어, 바이트 배열을
    // 조립해서 비교한다.
    private val SQLITE_HEADER_PREFIX = "SQLite format 3".toByteArray(Charsets.US_ASCII)

    fun backupFileName(): String = "golf_recorder_backup_${fileNameFormat.format(Date())}.db"

    /** [destination]에 현재 DB 스냅샷을 복사한다. DB 연결은 계속 열어둔 채, WAL을
     * 메인 파일로 체크포인트만 해서 그 파일 하나만으로 완전한 백업이 되게 한다. */
    suspend fun backup(context: Context, destination: Uri) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        // PRAGMA는 커서를 실제로 한 번 읽어야(moveToFirst) 실행된다 — 그냥 열고 닫기만
        // 하면 체크포인트가 조용히 실행 안 되고, 최근에 저장된 코스/라운드 리뷰처럼
        // 아직 WAL에만 있는 변경분이 메인 DB 파일에 반영 안 된 채로 백업되는 버그가 있었다.
        // TRUNCATE는 FULL과 달리 WAL 파일 자체를 비워서 메인 파일 하나로 완결됨을 보장한다.
        db.query("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
        val dbFile = context.getDatabasePath(DB_NAME)
        context.contentResolver.openOutputStream(destination)?.use { output ->
            dbFile.inputStream().use { input -> input.copyTo(output) }
        } ?: error("백업 파일을 열 수 없습니다.")
    }

    private fun isValidSqliteFile(file: File): Boolean {
        if (!file.exists() || file.length() < 16) return false
        val header = ByteArray(16)
        file.inputStream().use { it.read(header) }
        val prefix = SQLITE_HEADER_PREFIX
        return prefix.indices.all { i -> header[i] == prefix[i] } && header[prefix.size] == 0.toByte()
    }

    /** [source] 파일로 현재 DB를 통째로 덮어쓴다. 검증 없이 곧바로 라이브 DB 파일에
     * 스트림으로 쓰면, 도중에 예외가 나거나(디스크 공간 부족, 연결 끊김) 사용자가
     * 엉뚱한 파일을 골랐을 때 실제 사용 중인 DB가 반쯤 덮어써진 채로 남을 수 있다.
     * 그래서 캐시 디렉토리의 임시 파일에 먼저 받고, SQLite 헤더와 무결성을 검증한
     * 뒤에야 라이브 파일을 원자적으로 교체한다. 교체 직전 현재 DB는 따로 남겨서
     * 복원이 잘못돼도 되돌릴 수 있게 한다. 복사 후에는 이미 열려 있던
     * 리포지토리/DAO가 예전 연결을 그대로 들고 있으므로, 호출자가 반드시 앱
     * 프로세스를 재시작해야 한다. */
    suspend fun restore(context: Context, source: Uri) = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath(DB_NAME)
        val staging = File(context.cacheDir, "restore_staging.db")

        context.contentResolver.openInputStream(source)?.use { input ->
            staging.outputStream().use { output -> input.copyTo(output) }
        } ?: error("복원할 파일을 열 수 없습니다.")

        if (!isValidSqliteFile(staging)) {
            staging.delete()
            error("올바른 백업 파일이 아닙니다.")
        }
        // SQLiteDatabase.isDatabaseIntegrityOk가 내부적으로 PRAGMA integrity_check를
        // 돌린다 — 헤더만 맞고 내용물이 잘린/손상된 파일까지 여기서 걸러진다.
        val ok = SQLiteDatabase.openDatabase(staging.path, null, SQLiteDatabase.OPEN_READONLY)
            .use { it.isDatabaseIntegrityOk }
        if (!ok) {
            staging.delete()
            error("백업 파일이 손상되어 있습니다.")
        }

        AppDatabase.closeInstance()
        dbFile.copyTo(File(dbFile.path + ".before_restore"), overwrite = true)
        staging.copyTo(dbFile, overwrite = true)
        staging.delete()
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
    }
}
