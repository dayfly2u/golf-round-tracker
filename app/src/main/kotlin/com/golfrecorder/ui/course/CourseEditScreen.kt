package com.golfrecorder.ui.course

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.golfrecorder.data.repository.CourseRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val DEFAULT_HOLE_COUNT = 18
private const val DEFAULT_PAR = 4
private val PAR_OPTIONS = listOf(3, 4, 5)

class CourseEditViewModel(
    private val courseRepository: CourseRepository,
    private val existingCourseId: Long?,
) : ViewModel() {
    var name by mutableStateOf("")
    var pars by mutableStateOf(List(DEFAULT_HOLE_COUNT) { DEFAULT_PAR })
        private set

    private var existingHoleIds: List<Long> = emptyList()

    init {
        existingCourseId?.let { id ->
            viewModelScope.launch {
                courseRepository.getCourseWithHoles(id).first()?.let { courseWithHoles ->
                    name = courseWithHoles.course.name
                    val sortedHoles = courseWithHoles.holes.sortedBy { it.holeNumber }
                    pars = sortedHoles.map { it.par }
                    existingHoleIds = sortedHoles.map { it.id }
                }
            }
        }
    }

    fun setPar(holeIndex: Int, par: Int) {
        pars = pars.toMutableList().also { it[holeIndex] = par }
    }

    fun save(onDone: () -> Unit) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = existingCourseId
            if (id == null) {
                courseRepository.createCourse(name.trim(), pars)
            } else {
                courseRepository.updateCourseName(id, name.trim())
                existingHoleIds.forEachIndexed { index, holeId ->
                    courseRepository.updateHolePar(holeId, pars[index])
                }
            }
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = existingCourseId ?: return
        viewModelScope.launch {
            courseRepository.deleteCourse(id)
            onDone()
        }
    }
}

class CourseEditViewModelFactory(
    private val courseRepository: CourseRepository,
    private val existingCourseId: Long?,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        CourseEditViewModel(courseRepository, existingCourseId) as T
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseEditScreen(
    viewModel: CourseEditViewModel,
    isNew: Boolean,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "코스 추가" else "코스 수정") },
                navigationIcon = { TextButton(onClick = onBack) { Text("< 뒤로") } },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp)) {
            item {
                OutlinedTextField(
                    value = viewModel.name,
                    onValueChange = { viewModel.name = it },
                    label = { Text("코스 이름") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
            }
            items(viewModel.pars.size) { index ->
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
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.save(onBack) }) { Text("저장") }
                    if (!isNew) {
                        TextButton(onClick = { viewModel.delete(onBack) }) {
                            Text("삭제", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
