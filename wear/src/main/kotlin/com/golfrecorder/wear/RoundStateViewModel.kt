package com.golfrecorder.wear

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.golfrecorder.wearsync.WearSync
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
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
    val strokesGreenToHoleOut: Int = 0,
    val holeCount: Int = 18,
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
            try {
                val items = dataClient.dataItems.await()
                Log.d(TAG, "initial dataItems count=${items.count}")
                for (item in items) {
                    Log.d(TAG, "initial dataItem uri=${item.uri}")
                    if (item.uri.path == WearSync.STATE_PATH) {
                        applyDataMap(DataMapItem.fromDataItem(item).dataMap)
                    }
                }
                items.release()
            } catch (e: Exception) {
                Log.e(TAG, "initial dataItems FAILED", e)
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
            strokesGreenToHoleOut = map.getInt(WearSync.KEY_STROKES_SHORT_GAME, 0),
            holeCount = map.getInt(WearSync.KEY_HOLE_COUNT, 18),
        )
    }

    fun sendAction(action: String) {
        viewModelScope.launch {
            try {
                val nodes = nodeClient.connectedNodes.await()
                Log.d(TAG, "sendAction($action) connectedNodes=${nodes.size}: ${nodes.map { it.displayName + "/" + it.id }}")
                for (node in nodes) {
                    messageClient.sendMessage(node.id, WearSync.ACTION_PATH, action.toByteArray(Charsets.UTF_8)).await()
                    Log.d(TAG, "sendAction($action) sent to ${node.id}")
                }
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
