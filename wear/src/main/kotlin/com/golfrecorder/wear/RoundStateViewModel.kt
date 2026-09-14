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
    private val messageClient = Wearable.getMessageClient(application)
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
        // 이 라운드가 워치 기준이면 워치가 캐시해둔 최신 좌표를 액션에 붙여 보낸다
        // ("ACTION|위도|경도", WearSync.ACTION_PATH 참고). 아직 첫 fix를 못 받았으면
        // 좌표 없이 보내고 — 폰 쪽이 그걸 실패로 기록해 진동으로 알려준다(카트에 둔
        // 폰 위치로 조용히 대체하는 것보다 낫다).
        val payload = if (needsLocation(action) && _state.value.useWatchLocation) {
            val loc = WatchLocationService.latestLocation()
            if (loc != null) "$action|${loc.first}|${loc.second}" else action
        } else {
            action
        }
        viewModelScope.launch {
            try {
                val nodes = nodeClient.connectedNodes.await()
                Log.d(TAG, "sendAction($payload) connectedNodes=${nodes.size}: ${nodes.map { it.displayName + "/" + it.id }}")
                for (node in nodes) {
                    messageClient.sendMessage(node.id, WearSync.ACTION_PATH, payload.toByteArray(Charsets.UTF_8)).await()
                    Log.d(TAG, "sendAction($payload) sent to ${node.id}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "sendAction($payload) FAILED", e)
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
