package com.golfrecorder.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.golfrecorder.data.local.dto.RoundSummary
import com.golfrecorder.data.repository.RoundRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RoundHistoryViewModel(roundRepository: RoundRepository) : ViewModel() {
    val rounds: StateFlow<List<RoundSummary>> = roundRepository.getRoundSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

class RoundHistoryViewModelFactory(
    private val roundRepository: RoundRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        RoundHistoryViewModel(roundRepository) as T
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoundHistoryScreen(
    viewModel: RoundHistoryViewModel,
    onStartRound: () -> Unit,
    onRoundClick: (RoundSummary) -> Unit,
) {
    val rounds by viewModel.rounds.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("골프 라운드 기록") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onStartRound) { Text("+", style = MaterialTheme.typography.headlineSmall) }
        },
    ) { padding ->
        if (rounds.isEmpty()) {
            Text(
                "아직 기록된 라운드가 없습니다. + 버튼으로 새 라운드를 시작하세요.",
                modifier = Modifier.padding(padding).padding(16.dp),
            )
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(rounds, key = { it.roundId }) { round ->
                Column(
                    modifier = Modifier.fillMaxWidth().clickable { onRoundClick(round) }.padding(16.dp),
                ) {
                    Text(round.courseName, fontWeight = FontWeight.Bold)
                    Text(
                        "${dateFormat.format(Date(round.playedAt))} · 총 ${round.totalStrokes}타",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}
