package com.golfrecorder.wear

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.golfrecorder.wearsync.WearSync
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItemBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class RoundUiState(
    val roundActive: Boolean = false,
    val holeNumber: Int = 1,
    val par: Int = 4,
    val strokesToGreen: Int = 0,
    val strokesPutt: Int = 0,
    val holeCount: Int = 18,
    /** 최근에 GPS를 못 잡아 버튼 입력이 기록되지 않은 시각(epoch ms), 없으면 0. */
    val lastFailedAt: Long = 0L,
    /** 이 라운드가 샷 위치를 워치 GPS 기준으로 기록하는지 — true면 워치가 자체
     * GPS를 계속 추적하고 타수 증가 액션에 좌표를 실어 보낸다. */
    val useWatchLocation: Boolean = false,
)

private enum class Counter { TO_GREEN, PUTT }
private data class PendingDelta(val counter: Counter, val amount: Int)

class RoundStateViewModel(application: Application) :
    AndroidViewModel(application),
    DataClient.OnDataChangedListener {

    // 폰이 보내주는 원본(확정) 상태 — round-state DataItem을 받을 때만 갱신된다.
    private var serverState = RoundUiState()
    // 아직 폰의 처리 완료 확인(=큐 아이템 삭제)을 못 받은 액션들의 증감분. 블루투스가
    // 끊긴 동안 버튼을 눌러도 화면 숫자가 바로 움직이도록, 이 값을 serverState 위에
    // 얹어서 보여준다 — 나중에 폰이 그 액션을 처리하면(큐 아이템 삭제 이벤트) 더는
    // 겹쳐 더하지 않는다. 키는 sendAction에서 만든 큐 경로의 타임스탬프.
    private val pendingDeltas = mutableMapOf<Long, PendingDelta>()
    private val _state = MutableStateFlow(RoundUiState())
    val state: StateFlow<RoundUiState> = _state

    // 폰과 블루투스로 연결돼 있는지 — 몇 초 간격으로 연결된 노드 목록을 확인해서
    // 반영한다. 초기값을 true로 두는 이유는 "연결 끊김" 배너가 앱을 막 열자마자
    // 잠깐 잘못 뜨는 것보다, 첫 확인 전까지는 조용히 있는 게 낫기 때문이다.
    private val _isPhoneConnected = MutableStateFlow(true)
    val isPhoneConnected: StateFlow<Boolean> = _isPhoneConnected

    private val dataClient = Wearable.getDataClient(application)
    private val nodeClient = Wearable.getNodeClient(application)

    init {
        dataClient.addListener(this)
        viewModelScope.launch {
            var items: DataItemBuffer? = null
            try {
                items = dataClient.dataItems.await()
                Log.d(TAG, "initial dataItems count=${items.count}")
                for (item in items) {
                    Log.d(TAG, "initial dataItem uri=${item.uri}")
                    if (item.uri.path == WearSync.STATE_PATH) {
                        applyDataMap(DataMapItem.fromDataItem(item).dataMap)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "initial dataItems FAILED", e)
            } finally {
                items?.release()
            }
        }
        viewModelScope.launch {
            while (isActive) {
                val connected = try {
                    nodeClient.connectedNodes.await().isNotEmpty()
                } catch (e: Exception) {
                    Log.e(TAG, "connectedNodes check FAILED", e)
                    false
                }
                if (_isPhoneConnected.value != connected) {
                    Log.d(TAG, "phone connection changed: $connected")
                }
                _isPhoneConnected.value = connected
                delay(CONNECTION_POLL_INTERVAL_MS)
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d(TAG, "onDataChanged count=${dataEvents.count}")
        var pendingChanged = false
        for (event in dataEvents) {
            Log.d(TAG, "onDataChanged event type=${event.type} uri=${event.dataItem.uri}")
            val path = event.dataItem.uri.path.orEmpty()
            if (path == WearSync.STATE_PATH) {
                if (event.type == DataEvent.TYPE_CHANGED) {
                    applyDataMap(DataMapItem.fromDataItem(event.dataItem).dataMap)
                }
            } else if (path.startsWith(WearSync.ACTION_QUEUE_PATH_PREFIX)) {
                // 폰이 처리를 마치고 이 큐 아이템을 지우면(TYPE_DELETED) 그 액션이 확정된
                // 것 — round-state에 이미 반영됐을 것이므로 낙관적 증감분을 지운다.
                if (event.type == DataEvent.TYPE_DELETED) {
                    val seq = path.substringAfterLast('/').toLongOrNull()
                    if (seq != null && pendingDeltas.remove(seq) != null) {
                        pendingChanged = true
                    }
                }
            }
        }
        dataEvents.release()
        if (pendingChanged) {
            _state.value = displayState()
        }
    }

    private fun applyDataMap(map: DataMap) {
        val newServerState = RoundUiState(
            roundActive = map.getBoolean(WearSync.KEY_ROUND_ACTIVE, false),
            holeNumber = map.getInt(WearSync.KEY_HOLE_NUMBER, 1),
            par = map.getInt(WearSync.KEY_PAR, 4),
            strokesToGreen = map.getInt(WearSync.KEY_STROKES_TO_GREEN, 0),
            strokesPutt = map.getInt(WearSync.KEY_STROKES_PUTT, 0),
            holeCount = map.getInt(WearSync.KEY_HOLE_COUNT, 18),
            lastFailedAt = map.getLong(WearSync.KEY_LAST_FAILED_AT, 0L),
            useWatchLocation = map.getBoolean(WearSync.KEY_USE_WATCH_LOCATION, false),
        )
        // 홀이 바뀌었으면 이전 홀 기준으로 쌓아뒀던 낙관적 증감분은 더 이상 의미가 없다.
        if (newServerState.holeNumber != serverState.holeNumber) {
            pendingDeltas.clear()
        }
        serverState = newServerState
        _state.value = displayState()
    }

    private fun displayState(): RoundUiState {
        var toGreenDelta = 0
        var puttDelta = 0
        for (pending in pendingDeltas.values) {
            when (pending.counter) {
                Counter.TO_GREEN -> toGreenDelta += pending.amount
                Counter.PUTT -> puttDelta += pending.amount
            }
        }
        return serverState.copy(
            strokesToGreen = (serverState.strokesToGreen + toGreenDelta).coerceAtLeast(0),
            strokesPutt = (serverState.strokesPutt + puttDelta).coerceAtLeast(0),
        )
    }

    /** 좌표를 함께 보내야 하는 액션인지 — 타수를 실제로 "추가"하는 두 액션만 위치가
     * 필요하다(감소/홀 이동은 기록할 좌표가 없다). */
    private fun needsLocation(action: String): Boolean =
        action == WearSync.ACTION_INCREMENT_TO_GREEN || action == WearSync.ACTION_INCREMENT_PUTT

    private fun pendingDeltaFor(action: String): PendingDelta? = when (action) {
        WearSync.ACTION_INCREMENT_TO_GREEN -> PendingDelta(Counter.TO_GREEN, 1)
        WearSync.ACTION_DECREMENT_TO_GREEN -> PendingDelta(Counter.TO_GREEN, -1)
        WearSync.ACTION_INCREMENT_PUTT -> PendingDelta(Counter.PUTT, 1)
        WearSync.ACTION_DECREMENT_PUTT -> PendingDelta(Counter.PUTT, -1)
        else -> null
    }

    fun sendAction(action: String) {
        // DataItem 큐에 넣는다 — MessageClient(옛 방식)는 그 순간 폰과 블루투스가
        // 연결돼 있어야만 전달되고 끊겨 있으면 조용히 실패했지만, DataItem은 로컬에
        // 저장됐다가 재연결되면 자동으로 동기화된다. 경로에 타임스탬프를 붙여 액션마다
        // 고유하게 만드는 이유는, 같은 경로로 여러 번 보내면 나중 값이 이전 값을
        // 덮어써서 그 사이에 눌렀던 액션들이 통째로 사라지기 때문이다(WearSync 참고).
        val loc = if (needsLocation(action) && _state.value.useWatchLocation) {
            WatchLocationService.latestLocation()
        } else {
            null
        }
        val seq = System.currentTimeMillis()
        // 폰의 처리 확인(큐 삭제 이벤트)을 기다리지 않고 버튼을 누른 즉시 화면에
        // 반영한다 — 블루투스가 끊겨 있어도 누른 게 바로 보여야 하기 때문.
        pendingDeltaFor(action)?.let { pending ->
            pendingDeltas[seq] = pending
            _state.value = displayState()
        }
        viewModelScope.launch {
            try {
                val request = PutDataMapRequest.create(
                    "${WearSync.ACTION_QUEUE_PATH_PREFIX}/$seq",
                ).apply {
                    dataMap.putString(WearSync.KEY_QUEUED_ACTION, action)
                    if (loc != null) {
                        dataMap.putDouble(WearSync.KEY_QUEUED_LAT, loc.first)
                        dataMap.putDouble(WearSync.KEY_QUEUED_LNG, loc.second)
                    }
                }.asPutDataRequest().setUrgent()
                val result = dataClient.putDataItem(request).await()
                Log.d(TAG, "sendAction($action) queued uri=${result.uri}")
            } catch (e: Exception) {
                Log.e(TAG, "sendAction($action) FAILED", e)
                // 큐에 넣는 것조차 실패했으면(로컬 저장 실패 등) 낙관적 표시도 되돌린다.
                if (pendingDeltas.remove(seq) != null) {
                    _state.value = displayState()
                }
            }
        }
    }

    override fun onCleared() {
        dataClient.removeListener(this)
        super.onCleared()
    }

    companion object {
        private const val TAG = "RoundStateViewModel"
        private const val CONNECTION_POLL_INTERVAL_MS = 3000L
    }
}
