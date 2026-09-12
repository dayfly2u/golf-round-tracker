package com.golfrecorder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.golfrecorder.MainActivity
import com.golfrecorder.di.AppContainer
import com.golfrecorder.domain.model.ShotPhase
import com.golfrecorder.domain.model.StrokeCalculator
import com.golfrecorder.location.LocationCapture
import com.golfrecorder.location.LocationTracker
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class RoundRecordingService : Service() {

    private lateinit var container: AppContainer
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var roundId: Long = -1
    private var courseId: Long = -1
    // 워치에서 누른 버튼이 GPS를 못 잡아 기록되지 않았을 때, 그 시각을 남겨뒀다가
    // pushState()에서 워치로 실어 보낸다(워치는 이 값이 바뀌면 진동으로 알려준다).
    private var lastFailedAt: Long = 0L

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
        Log.d(TAG, "onStartCommand action=${intent?.action} roundId=${intent?.getLongExtra(EXTRA_ROUND_ID, -1)} courseId=${intent?.getLongExtra(EXTRA_COURSE_ID, -1)}")
        // 서비스가 처음 뜰 때는 아직 위치 권한이 없을 수 있다(RoundPlayScreen이 권한
        // 허용을 기다리지 않고 서비스를 먼저 시작한다). ACTION_REFRESH는 폰에서 타수/홀이
        // 바뀔 때마다 오므로 여기서 매번 재시도해두면 권한이 늦게 허용돼도 구독이 살아난다.
        // 이미 구독 중이면 start()가 즉시 리턴하므로 반복 호출해도 안전하다.
        LocationTracker.start(applicationContext)
        if (intent?.action == ACTION_REFRESH) {
            serviceScope.launch { pushState() }
            // START_STICKY를 쓰면 시스템이 메모리 부족으로 프로세스를 죽였다가 나중에
            // intent 없이 재시작시키는데, 그 경우 roundId/courseId를 알 방법이 없어
            // -1로 남는다 — 그러면 handleAction/pushState가 전부 조용히 no-op되면서도
            // GPS는 계속 폴링하는 "좀비" 서비스가 된다(불필요한 배터리 소모). 라운드
            // 화면을 다시 열면 RoundPlayScreen이 유효한 값으로 다시 start()를 호출해
            // 정상 복구되므로, 자동 재시작에 의존하지 않는 게 더 안전하다.
            return START_NOT_STICKY
        }
        roundId = intent?.getLongExtra(EXTRA_ROUND_ID, -1) ?: -1
        courseId = intent?.getLongExtra(EXTRA_COURSE_ID, -1) ?: -1
        serviceScope.launch { pushState() }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Wearable.getMessageClient(this).removeListener(messageListener)
        LocationTracker.stop(applicationContext)
        // 스코프를 취소하기 전에 "라운드 종료" 상태가 실제로 전송 완료(혹은 타임아웃)되도록
        // 기다린다 — serviceScope.launch { ... }로 던지고 바로 cancel()하면 코루틴이
        // 실행되기 전에 취소돼 워치에 마지막 상태가 전달되지 않는 경우가 있었다.
        runBlocking {
            withTimeoutOrNull(2000) {
                pushState(roundActive = false)
            }
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
            WearSync.ACTION_INCREMENT_PUTT -> incrementStroke(holeNumber, ShotPhase.PUTT)
            WearSync.ACTION_DECREMENT_PUTT -> decrementStroke(holeNumber, ShotPhase.PUTT)
            WearSync.ACTION_NEXT_HOLE -> changeHole(holeNumber + 1)
            WearSync.ACTION_PREV_HOLE -> changeHole(holeNumber - 1)
        }
        pushState()
    }

    private suspend fun incrementStroke(holeNumber: Int, phase: ShotPhase) {
        val shots = container.shotRepository.getShots(roundId, holeNumber).first()
        val penalties = container.penaltyRepository.getPenalties(roundId, holeNumber).first()
        val newValue = StrokeCalculator.currentTotal(shots, penalties, phase) + 1
        val loc = if (LocationCapture.hasPermission(this)) LocationTracker.latestLocation(this) else null
        if (loc != null) {
            container.shotRepository.recordShot(roundId, holeNumber, phase, newValue, loc.lat, loc.lng)
        } else {
            // 폰 쪽(RoundPlayScreen)은 이 경우 Toast로 바로 알려주지만, 워치는 텍스트를
            // 띄우기 부담스러운 화면이라 대신 이 시각을 다음 pushState()에 실어 보내
            // 진동으로 알려준다.
            lastFailedAt = System.currentTimeMillis()
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
            dataMap.putLong(WearSync.KEY_UPDATED_AT, System.currentTimeMillis())
            dataMap.putLong(WearSync.KEY_LAST_FAILED_AT, lastFailedAt)
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
                    WearSync.KEY_STROKES_PUTT,
                    StrokeCalculator.currentTotal(shots, penalties, ShotPhase.PUTT),
                )
            }
        }.setUrgent()
        try {
            val result = Wearable.getDataClient(this).putDataItem(dataMapRequest.asPutDataRequest()).await()
            Log.d(TAG, "pushState success uri=${result.uri} roundActive=$roundActive")
        } catch (e: Exception) {
            Log.e(TAG, "pushState FAILED roundActive=$roundActive", e)
        }
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
        private const val TAG = "RoundRecordingService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "round_recording"
        private const val EXTRA_ROUND_ID = "extra_round_id"
        private const val EXTRA_COURSE_ID = "extra_course_id"
        private const val ACTION_REFRESH = "com.golfrecorder.service.ACTION_REFRESH"

        fun start(context: Context, roundId: Long, courseId: Long) {
            val intent = Intent(context, RoundRecordingService::class.java)
                .putExtra(EXTRA_ROUND_ID, roundId)
                .putExtra(EXTRA_COURSE_ID, courseId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RoundRecordingService::class.java))
        }

        fun refreshState(context: Context) {
            val intent = Intent(context, RoundRecordingService::class.java).setAction(ACTION_REFRESH)
            context.startService(intent)
        }
    }
}
