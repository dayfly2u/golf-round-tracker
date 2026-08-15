package com.golfrecorder.backup

import android.content.Context
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

    /** [source] 파일로 현재 DB를 통째로 덮어쓴다. 진행 중이던 연결을 닫고, 남아있는
     * -wal/-shm 저널을 지워 새 메인 파일과 안 맞는 상태가 남지 않게 한다. 복사 후에는
     * 이미 열려 있던 리포지토리/DAO가 예전 연결을 그대로 들고 있으므로, 호출자가
     * 반드시 앱 프로세스를 재시작해야 한다. */
    suspend fun restore(context: Context, source: Uri) = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath(DB_NAME)
        AppDatabase.closeInstance()
        context.contentResolver.openInputStream(source)?.use { input ->
            dbFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("복원할 파일을 열 수 없습니다.")
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
    }
}
