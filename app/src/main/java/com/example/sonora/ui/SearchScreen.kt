package com.example.sonora.ui

import androidx.compose.foundation.lazy.items
import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sonora.SonoraApp
import com.example.sonora.data.Genres
import com.example.sonora.data.Track
import com.example.sonora.util.Load
import com.example.sonora.util.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class SearchViewModel(app: Application) : AndroidViewModel(app) {
    private val api = (app as SonoraApp).container.api
    private val prefs = app.getSharedPreferences("search", android.content.Context.MODE_PRIVATE)

    private val _history = MutableStateFlow(
        prefs.getString("history", "").orEmpty().split('\n').filter { it.isNotBlank() }
    )
    val history: StateFlow<List<String>> = _history.asStateFlow()

    private fun remember(q: String) {
        val list = (listOf(q) + _history.value.filterNot { it.equals(q, ignoreCase = true) }).take(10)
        _history.value = list
        prefs.edit().putString("history", list.joinToString("\n")).apply()
    }

    fun clearHistory() {
        _history.value = emptyList()
        prefs.edit().remove("history").apply()
    }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** null — пользователь ещё ничего не искал. */
    private val _results = MutableStateFlow<Load<List<Track>>?>(null)
    val results: StateFlow<Load<List<Track>>?> = _results.asStateFlow()

    init {
        viewModelScope.launch {
            _query.debounce(400).distinctUntilChanged().collectLatest { q -> run(q.trim()) }
        }
    }

    fun onQuery(q: String) {
        _query.value = q
    }

    fun retry() {
        viewModelScope.launch { run(_query.value.trim()) }
    }

    private suspend fun run(q: String) {
        if (q.isEmpty()) {
            _results.value = null
            return
        }
        _results.value = Load.Loading
        _results.value = try {
            Load.Ok(api.search(q, limit = 40)).also { if (it.value.isNotEmpty()) remember(q) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Load.Fail(e.userMessage())
        }
    }
}

@Composable
fun SearchScreen(vm: SearchViewModel = viewModel()) {
    val c = LocalContainer.current
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val history by vm.history.collectAsState()
    val ui = rememberTrackUi()
    val focus = LocalFocusManager.current

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ScreenTitle("Поиск") }
        item { SearchField(query, vm::onQuery, focus) }

        when (val r = results) {
            null -> {
                if (history.isNotEmpty()) {
                    item { SectionTitle("Недавние запросы") }
                    item {
                        androidx.compose.foundation.lazy.LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(history.size) { i ->
                                androidx.compose.material3.AssistChip(
                                    onClick = { vm.onQuery(history[i]) },
                                    label = { Text(history[i]) },
                                )
                            }
                            item {
                                androidx.compose.material3.TextButton(onClick = vm::clearHistory) { Text("Очистить") }
                            }
                        }
                    }
                }
                item { SectionTitle("Выберите жанр") }
                Genres.featured.chunked(2).forEach { pair ->
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            pair.forEach { g ->
                                GenreTile(g, Modifier.weight(1f)) { vm.onQuery(g.api) }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            is Load.Loading -> item { TrackSkeleton(6) }
            is Load.Fail -> item { ErrorBox(r.message, onRetry = vm::retry) }
            is Load.Ok -> {
                if (r.value.isEmpty()) {
                    item { EmptyState("Ничего не найдено", "Попробуйте другое название, исполнителя или жанр") }
                } else {
                    trackItems(r.value, ui, onPlay = { c.player.play(r.value, it) })
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, focus: FocusManager) {
    TextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        placeholder = { Text("Треки, исполнители, жанры") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Очистить")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
    )
}
