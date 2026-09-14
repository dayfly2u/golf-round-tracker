package com.golfrecorder.wear

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.golfrecorder.wearsync.WearSync
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItemBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
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
    val strokesPutt: Int = 0,
    val holeCount: Int = 18,
    /** 최근에 GPS를 못 잡아 버튼 입력이 기록되지 않은 시각(epoch ms), 없으면 0. */
    val lastFailedAt: Long = 0L,
    /** 이 라운드가 샷 위치를 워치 GPS 기준으로 기록하는지 — true면 워치가 자체
     * GPS를 계속 추적하고 타수 증가 액션에 좌표를 실어 보낸다. */
    val useWatchLocation: Boolean = false,
)

class RoundStateViewModel(application: Application) :
    AndroidViewModel(application),
    DataClient.OnDataChangedListener {

    private val _state = MutableStateFlow(RoundUiState())
    val state: StateFlow<RoundUiState> = _state

    private val dataClient = Wearable.getDataClient(application)

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
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d(TAG, "onDataChanged count=${dataEvents.count}")
        for (event in dataEvents) {
            Log.d(TAG, "onDataChanged event type=${event.type} uri=${event.dataItem.uri}")
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
            strokesPutt = map.getInt(WearSync.KEY_STROKES_PUTT, 0),
            holeCount = map.getInt(WearSync.KEY_HOLE_COUNT, 18),
            lastFailedAt = map.getLong(WearSync.KEY_LAST_FAILED_AT, 0L),
            useWatchLocation = map.getBoolean(WearSync.KEY_USE_WATCH_LOCATION, false),
        )
    }

    /** 좌표를 함께 보내야 하는 액션인지 — 타수를 실제로 "추가"하는 두 액션만 위치가
     * 필요하다(감소/홀 이동은 기록할 좌표가 없다). */
    private fun needsLocation(action: String): Boolean =
        action == WearSync.ACTION_INCREMENT_TO_GREEN || action == WearSync.ACTION_INCREMENT_PUTT

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
        viewModelScope.launch {
            try {
                val request = PutDataMapRequest.create(
                    "${WearSync.ACTION_QUEUE_PATH_PREFIX}/${System.currentTimeMillis()}",
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
            }
        }
    }

    override fun onCleared() {
        dataClient.removeListener(this)
        super.onCleared()
    }

    companion object {
        private const val TAG = "RoundStateViewModel"
    }
}
