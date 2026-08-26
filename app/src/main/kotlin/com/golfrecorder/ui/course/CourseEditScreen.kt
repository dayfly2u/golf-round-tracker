package com.golfrecorder.ui.course

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.golfrecorder.data.local.entity.CourseYoutubeLinkEntity
import com.golfrecorder.data.repository.CourseRepository
import com.golfrecorder.data.repository.CourseYoutubeLinkRepository
import com.golfrecorder.data.repository.RoundRepository
import com.golfrecorder.data.repository.isValidYoutubeUrl
import com.golfrecorder.domain.model.YoutubeCategory
import com.golfrecorder.domain.model.YoutubeSearchResult
import com.golfrecorder.ui.common.SectionHeader
import com.golfrecorder.ui.common.VideoThumbnail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val DEFAULT_HOLE_COUNT = 18
private const val DEFAULT_PAR = 4
private val PAR_OPTIONS = listOf(3, 4, 5)

fun difficultyLabel(level: Int): String = when (level) {
    1 -> "하"
    2 -> "중하"
    3 -> "중"
    4 -> "중상"
    5 -> "상"
    else -> ""
}

class CourseEditViewModel(
    private val courseRepository: CourseRepository,
    private val courseYoutubeLinkRepository: CourseYoutubeLinkRepository,
    private val roundRepository: RoundRepository,
    val existingCourseId: Long?,
) : ViewModel() {
    /**
     * 이 코스로 기록된 라운드가 있으면 삭제를 막는다 — 나중에 지도에서 예전 샷
     * 위치를 다시 보려면 코스의 홀/그린 정보가 남아있어야 하기 때문이다.
     * [onResult]에 남은 라운드 개수를 넘긴다(0이면 바로 삭제해도 된다는 뜻).
     */
    fun checkDeletable(onResult: (roundCount: Int) -> Unit) {
        val courseId = existingCourseId ?: return
        viewModelScope.launch {
            onResult(roundRepository.countRoundsForCourse(courseId))
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val courseId = existingCourseId ?: return
        viewModelScope.launch {
            courseRepository.deleteCourse(courseId)
            onDeleted()
        }
    }

    val youtubeLinks: StateFlow<List<CourseYoutubeLinkEntity>> = if (existingCourseId != null) {
        courseYoutubeLinkRepository.getLinks(existingCourseId)
    } else {
        flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _youtubeSearchResults = MutableStateFlow<List<YoutubeSearchResult>>(emptyList())
    val youtubeSearchResults: StateFlow<List<YoutubeSearchResult>> = _youtubeSearchResults

    private val _isSearchingYoutube = MutableStateFlow(false)
    val isSearchingYoutube: StateFlow<Boolean> = _isSearchingYoutube

    fun searchYoutube(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _isSearchingYoutube.value = true
            _youtubeSearchResults.value = courseYoutubeLinkRepository.search(query)
            _isSearchingYoutube.value = false
        }
    }

    fun clearYoutubeSearch() {
        _youtubeSearchResults.value = emptyList()
    }

    fun addYoutubeLink(url: String, category: YoutubeCategory, selectedResult: YoutubeSearchResult?) {
        val courseId = existingCourseId ?: return
        viewModelScope.launch {
            courseYoutubeLinkRepository.addLink(
                courseId = courseId,
                url = url,
                category = category,
                title = selectedResult?.title,
                thumbnailUrl = selectedResult?.thumbnailUrl,
                channelTitle = selectedResult?.channelTitle,
            )
        }
    }

    fun deleteYoutubeLink(linkId: Long) {
        viewModelScope.launch { courseYoutubeLinkRepository.deleteLink(linkId) }
    }

    var name by mutableStateOf("")
    var pars by mutableStateOf(List(DEFAULT_HOLE_COUNT) { DEFAULT_PAR })
        private set
    var nameError by mutableStateOf(false)
        private set

    /** 0f는 "아직 평점 없음"을 의미한다 — 저장 시 null로 변환한다. */
    var rating by mutableStateOf(0f)

    /** 0f는 "아직 난이도 없음", 1~5는 하~상 5단계 — 저장 시 반올림한 Int 또는 null로 변환한다. */
    var difficulty by mutableStateOf(0f)
    var region by mutableStateOf("")
    var distance by mutableStateOf("")
    var travelTime by mutableStateOf("")
    var oneLineReview by mutableStateOf("")
    var transportInfo by mutableStateOf("")
    var clubhouseInfo by mutableStateOf("")
    var courseInfo by mutableStateOf("")

    private var existingHoleIds: List<Long> = emptyList()

    init {
        existingCourseId?.let { id ->
            viewModelScope.launch {
                courseRepository.getCourseWithHoles(id).first()?.let { courseWithHoles ->
                    name = courseWithHoles.course.name
                    val sortedHoles = courseWithHoles.holes.sortedBy { it.holeNumber }
                    pars = sortedHoles.map { it.par }
                    existingHoleIds = sortedHoles.map { it.id }
                    rating = courseWithHoles.course.rating?.toFloat() ?: 0f
                    difficulty = courseWithHoles.course.difficulty?.toFloat() ?: 0f
                    region = courseWithHoles.course.region.orEmpty()
                    distance = courseWithHoles.course.distance.orEmpty()
                    travelTime = courseWithHoles.course.travelTime.orEmpty()
                    oneLineReview = courseWithHoles.course.oneLineReview.orEmpty()
                    transportInfo = courseWithHoles.course.transportInfo.orEmpty()
                    clubhouseInfo = courseWithHoles.course.clubhouseInfo.orEmpty()
                    courseInfo = courseWithHoles.course.courseInfo.orEmpty()
                }
            }
        }
    }

    fun setPar(holeIndex: Int, par: Int) {
        pars = pars.toMutableList().also { it[holeIndex] = par }
    }

    fun save(onDone: () -> Unit) {
        if (name.isBlank()) {
            nameError = true
            return
        }
        viewModelScope.launch {
            val id = existingCourseId
            val courseId = if (id == null) {
                courseRepository.createCourse(name.trim(), pars)
            } else {
                courseRepository.updateCourseName(id, name.trim())
                existingHoleIds.forEachIndexed { index, holeId ->
                    courseRepository.updateHolePar(holeId, pars[index])
                }
                id
            }
            courseRepository.updateCourseReview(
                courseId = courseId,
                // Slider가 내놓는 Float는 0.5 단위 스텝이어도 부동소수점 오차로
                // 3.4999998 같은 값이 나올 수 있어, Double로 그냥 바꾸면 그 오차가
                // 소수점 자리수로 그대로 남는다 — 가장 가까운 0.5 단위로 반올림한다.
                rating = if (rating <= 0f) null else Math.round(rating * 2) / 2.0,
                difficulty = if (difficulty <= 0f) null else difficulty.roundToInt(),
                region = region.trim().ifBlank { null },
                distance = distance.trim().ifBlank { null },
                travelTime = travelTime.trim().ifBlank { null },
                oneLineReview = oneLineReview.trim().ifBlank { null },
                transportInfo = transportInfo.trim().ifBlank { null },
                clubhouseInfo = clubhouseInfo.trim().ifBlank { null },
                courseInfo = courseInfo.trim().ifBlank { null },
            )
            onDone()
        }
    }
}

class CourseEditViewModelFactory(
    private val courseRepository: CourseRepository,
    private val courseYoutubeLinkRepository: CourseYoutubeLinkRepository,
    private val roundRepository: RoundRepository,
    private val existingCourseId: Long?,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        CourseEditViewModel(courseRepository, courseYoutubeLinkRepository, roundRepository, existingCourseId) as T
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseEditScreen(
    viewModel: CourseEditViewModel,
    isNew: Boolean,
    onBack: () -> Unit,
) {
    val youtubeLinks by viewModel.youtubeLinks.collectAsStateWithLifecycle()
    var showAddLinkDialog by remember { mutableStateOf(false) }
    var pendingDeleteLink by remember { mutableStateOf<CourseYoutubeLinkEntity?>(null) }
    var pendingDeleteCourse by remember { mutableStateOf(false) }
    var courseBlockedFromDelete by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current

    if (pendingDeleteCourse) {
        AlertDialog(
            onDismissRequest = { pendingDeleteCourse = false },
            title = { Text("코스를 삭제할까요?") },
            text = { Text("\"${viewModel.name}\"을(를) 삭제하면 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteCourse = false
                    viewModel.delete(onBack)
                }) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteCourse = false }) { Text("취소") }
            },
        )
    }

    courseBlockedFromDelete?.let { roundCount ->
        AlertDialog(
            onDismissRequest = { courseBlockedFromDelete = null },
            title = { Text("삭제할 수 없습니다") },
            text = {
                Text(
                    "\"${viewModel.name}\"으로 기록된 라운드가 ${roundCount}개 있어 삭제할 수 없습니다. " +
                        "홈 화면에서 그 라운드들을 먼저 삭제해주세요."
                )
            },
            confirmButton = {
                TextButton(onClick = { courseBlockedFromDelete = null }) { Text("확인") }
            },
        )
    }

    pendingDeleteLink?.let { link ->
        AlertDialog(
            onDismissRequest = { pendingDeleteLink = null },
            title = { Text("링크를 삭제할까요?") },
            text = { Text("\"${link.title ?: link.url}\"를 삭제하면 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteYoutubeLink(link.id); pendingDeleteLink = null }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteLink = null }) { Text("취소") } },
        )
    }

    if (showAddLinkDialog) {
        val searchResults by viewModel.youtubeSearchResults.collectAsStateWithLifecycle()
        val isSearching by viewModel.isSearchingYoutube.collectAsStateWithLifecycle()
        AddYoutubeLinkDialog(
            initialQuery = viewModel.name,
            searchResults = searchResults,
            isSearching = isSearching,
            onSearch = viewModel::searchYoutube,
            onDismiss = {
                showAddLinkDialog = false
                viewModel.clearYoutubeSearch()
            },
            onConfirm = { url, category, selectedResult ->
                viewModel.addYoutubeLink(url, category, selectedResult)
                showAddLinkDialog = false
                viewModel.clearYoutubeSearch()
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "코스 추가" else "코스 수정") },
                navigationIcon = { TextButton(onClick = onBack) { Text("< 뒤로") } },
                actions = {
                    TextButton(onClick = { viewModel.save(onBack) }) { Text("저장") }
                    if (!isNew) {
                        TextButton(onClick = {
                            viewModel.checkDeletable { roundCount ->
                                if (roundCount > 0) {
                                    courseBlockedFromDelete = roundCount
                                } else {
                                    pendingDeleteCourse = true
                                }
                            }
                        }) {
                            Text("삭제", color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp)) {
            item {
                OutlinedTextField(
                    value = viewModel.name,
                    onValueChange = { viewModel.name = it },
                    label = { Text("코스 이름") },
                    isError = viewModel.nameError && viewModel.name.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (viewModel.nameError && viewModel.name.isBlank()) {
                    Text(
                        "코스 이름을 입력하세요.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
            items(viewModel.pars.size) { index ->
                if (index == 0) {
                    Text(
                        "전반 (1~9)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                } else if (index == 9) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "후반 (10~18)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${index + 1}홀", modifier = Modifier.padding(end = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PAR_OPTIONS.forEach { par ->
                            FilterChip(
                                selected = viewModel.pars[index] == par,
                                onClick = { viewModel.setPar(index, par) },
                                label = { Text("파$par") },
                            )
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                SectionHeader("리뷰 (선택 입력)")
                Spacer(Modifier.height(8.dp))
                Text(
                    if (viewModel.rating <= 0f) "총평점: 없음" else "총평점: ${"%.1f".format(viewModel.rating)}",
                )
                Slider(
                    value = viewModel.rating,
                    onValueChange = { viewModel.rating = it },
                    valueRange = 0f..5f,
                    steps = 9, // 0, 0.5, 1.0, ..., 5.0 (0.5 단위)
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (viewModel.difficulty <= 0f) {
                        "난이도: 없음"
                    } else {
                        val level = viewModel.difficulty.roundToInt()
                        "난이도: ${difficultyLabel(level)} ($level)"
                    },
                )
                Slider(
                    value = viewModel.difficulty,
                    onValueChange = { viewModel.difficulty = it },
                    valueRange = 0f..5f,
                    steps = 4, // 0(없음), 1(하), 2(중하), 3(중), 4(중상), 5(상)
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = viewModel.region,
                        onValueChange = { viewModel.region = it },
                        label = { Text("지역") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = viewModel.distance,
                        onValueChange = { viewModel.distance = it },
                        label = { Text("거리") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = viewModel.travelTime,
                        onValueChange = { viewModel.travelTime = it },
                        label = { Text("시간") },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = viewModel.oneLineReview,
                    onValueChange = { viewModel.oneLineReview = it },
                    label = { Text("한줄평") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = viewModel.transportInfo,
                    onValueChange = { viewModel.transportInfo = it },
                    label = { Text("교통 (거리, 경로 등)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = viewModel.clubhouseInfo,
                    onValueChange = { viewModel.clubhouseInfo = it },
                    label = { Text("클럽하우스 (외관/내관, 소품 등)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = viewModel.courseInfo,
                    onValueChange = { viewModel.courseInfo = it },
                    label = { Text("코스 (티샷, 코스, 그린 등)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                if (viewModel.existingCourseId == null) {
                    SectionHeader("유튜브 링크")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "코스를 저장한 후에 유튜브 링크를 추가할 수 있어요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionHeader("유튜브 링크")
                        TextButton(onClick = { showAddLinkDialog = true }) { Text("링크 추가") }
                    }
                    if (youtubeLinks.isEmpty()) {
                        Text(
                            "아직 추가한 링크가 없어요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    YoutubeCategory.entries.forEach { category ->
                        val links = youtubeLinks.filter { it.category == category.name }
                        if (links.isNotEmpty()) {
                            Text(category.displayLabel, style = MaterialTheme.typography.labelMedium)
                            links.forEach { link ->
                                Box(
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)))
                                        }
                                        .padding(vertical = 6.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(end = 56.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        VideoThumbnail(link.thumbnailUrl)
                                        Column {
                                            Text(
                                                link.title ?: link.url,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            link.channelTitle?.let {
                                                Text(
                                                    it,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.outline,
                                                )
                                            }
                                        }
                                    }
                                    TextButton(
                                        onClick = { pendingDeleteLink = link },
                                        modifier = Modifier.align(Alignment.CenterEnd),
                                    ) { Text("삭제") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddYoutubeLinkDialog(
    initialQuery: String,
    searchResults: List<YoutubeSearchResult>,
    isSearching: Boolean,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String, YoutubeCategory, YoutubeSearchResult?) -> Unit,
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf(initialQuery) }
    var url by remember { mutableStateOf("") }
    var selectedResult by remember { mutableStateOf<YoutubeSearchResult?>(null) }
    var category by remember { mutableStateOf(YoutubeCategory.ROUND) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("유튜브 링크 추가") },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("영상 검색") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    YoutubeCategory.entries.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.displayLabel) })
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = {
                            val augmentedQuery = listOf(searchQuery.trim(), category.displayLabel)
                                .filter { it.isNotBlank() }
                                .joinToString(" ")
                            onSearch(augmentedQuery)
                        },
                        enabled = searchQuery.isNotBlank(),
                    ) { Text("검색") }
                }
                if (isSearching) {
                    Text("검색 중...", style = MaterialTheme.typography.bodySmall)
                }
                searchResults.forEach { result ->
                    val resultUrl = "https://www.youtube.com/watch?v=${result.videoId}"
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable {
                                url = resultUrl
                                selectedResult = result
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(resultUrl)))
                            }
                            .background(
                                if (url == resultUrl) MaterialTheme.colorScheme.primaryContainer else Color.Unspecified,
                            )
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        VideoThumbnail(result.thumbnailUrl)
                        Column {
                            Text(
                                result.title,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                listOfNotNull(result.channelTitle, result.publishedYear).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
                if (searchResults.isNotEmpty()) {
                    Text(
                        "위 목록에서 선택하거나, 아래에 URL을 직접 입력할 수 있어요.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        if (selectedResult != null && it != "https://www.youtube.com/watch?v=${selectedResult?.videoId}") {
                            selectedResult = null
                        }
                    },
                    label = { Text("유튜브 URL") },
                    isError = url.isNotBlank() && !isValidYoutubeUrl(url),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(url.trim(), category, selectedResult) },
                enabled = isValidYoutubeUrl(url),
            ) { Text("추가") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
