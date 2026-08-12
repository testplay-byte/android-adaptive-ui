@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.adaptiveui.animeapp.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adaptiveui.animeapp.core.datastore.SettingsDataStore
import com.adaptiveui.animeapp.data.repository.AnimeRepository
import com.adaptiveui.animeapp.data.repository.EpisodeRepository
import com.adaptiveui.animeapp.data.repository.LibraryRepository
import com.adaptiveui.animeapp.domain.model.AiSettings
import com.adaptiveui.animeapp.domain.model.AnimeCard
import com.adaptiveui.animeapp.domain.model.AnimeDetail
import com.adaptiveui.animeapp.domain.model.Category
import com.adaptiveui.animeapp.domain.model.Episode
import com.adaptiveui.animeapp.domain.model.HomeData
import com.adaptiveui.animeapp.domain.model.LibraryEntry
import com.adaptiveui.animeapp.domain.model.SearchFilters
import com.adaptiveui.animeapp.domain.model.SearchResult
import com.adaptiveui.animeapp.interpreter.ColorExtractor
import com.adaptiveui.animeapp.interpreter.ScreenSpec
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── Home ────────────────────────────────────────────────────────────────────

sealed interface HomeState {
    data object Loading : HomeState
    data class Ready(val data: HomeData) : HomeState
    data class Error(val message: String) : HomeState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val animeRepository: AnimeRepository,
    private val dataStore: SettingsDataStore
) : ViewModel() {

    private val _state = MutableStateFlow<HomeState>(HomeState.Loading)
    val state: StateFlow<HomeState> = _state.asStateFlow()

    val spec: StateFlow<ScreenSpec?> = dataStore.specForScreen("home")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init { load() }

    fun load(forceRefresh: Boolean = false) {
        _state.value = HomeState.Loading
        viewModelScope.launch {
            runCatching { animeRepository.getHomePage(forceRefresh = forceRefresh) }
                .onSuccess { home -> _state.value = HomeState.Ready(home) }
                .onFailure { t -> _state.value = HomeState.Error(t.message ?: "Failed to load home") }
        }
    }

    fun retry() = load(forceRefresh = true)
}

// ─── Library ─────────────────────────────────────────────────────────────────

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val dataStore: SettingsDataStore
) : ViewModel() {

    val categories: StateFlow<List<Category>> = libraryRepository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    val selectedCategoryId: StateFlow<Long?> = _selectedCategoryId.asStateFlow()

    @OptIn(FlowPreview::class)
    val entries: StateFlow<List<LibraryEntry>> = _selectedCategoryId
        .flatMapLatest { id ->
            if (id == null) libraryRepository.observeAllEntries()
            else libraryRepository.observeEntriesForCategory(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val spec: StateFlow<ScreenSpec?> = dataStore.specForScreen("library")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectCategory(id: Long?) { _selectedCategoryId.value = id }

    fun addCategory(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { libraryRepository.addCategory(name.trim()) }
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch { libraryRepository.deleteCategory(id) }
    }

    fun renameCategory(id: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { libraryRepository.renameCategory(id, name.trim()) }
    }

    fun removeEntry(animeId: Int) {
        viewModelScope.launch { libraryRepository.removeAnime(animeId) }
    }
}

// ─── Search ──────────────────────────────────────────────────────────────────

data class SearchState(
    val query: String = "",
    val results: List<AnimeCard> = emptyList(),
    val isLoading: Boolean = false,
    val isInitial: Boolean = true,
    val error: String? = null,
    val filters: SearchFilters = SearchFilters(),
    val currentPage: Int = 1,
    val hasNextPage: Boolean = false,
    val isLoadingMore: Boolean = false
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val animeRepository: AnimeRepository,
    private val dataStore: SettingsDataStore
) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    val spec: StateFlow<ScreenSpec?> = dataStore.specForScreen("search")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Debounced query flow — fires 500ms after the user stops typing. The initial value is
     * dropped so we don't re-trigger a search immediately on startup; the explicit `init` block
     * below performs the first default search instead.
     */
    @OptIn(FlowPreview::class)
    private val queryFlow = _state
        .map { it.query }
        .distinctUntilChanged()
        .drop(1)
        .debounce(500L)

    init {
        // Kick off the initial default search (popular anime) immediately.
        loadDefault()
        // Watch the debounced query stream for user-driven searches.
        viewModelScope.launch {
            queryFlow.collect { q ->
                if (q.isBlank()) loadDefault() else performSearch(reset = true)
            }
        }
    }

    fun updateQuery(q: String) {
        _state.value = _state.value.copy(query = q)
    }

    fun search() = performSearch(reset = true)

    fun loadDefault() {
        _state.value = _state.value.copy(isLoading = true, error = null, isInitial = false)
        viewModelScope.launch {
            runCatching { animeRepository.getDefaultSearch() }
                .onSuccess { res ->
                    _state.value = _state.value.copy(
                        results = res.items,
                        isLoading = false,
                        currentPage = 1,
                        hasNextPage = res.hasNextPage,
                        error = null
                    )
                }
                .onFailure { t ->
                    _state.value = _state.value.copy(isLoading = false, error = t.message ?: "Search failed")
                }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.isLoading || s.isLoadingMore || !s.hasNextPage) return
        _state.value = s.copy(isLoadingMore = true)
        viewModelScope.launch {
            val nextPage = s.currentPage + 1
            runCatching { animeRepository.search(s.query.ifBlank { null }, page = nextPage, filters = s.filters) }
                .onSuccess { res: SearchResult ->
                    _state.value = _state.value.copy(
                        results = _state.value.results + res.items,
                        currentPage = nextPage,
                        hasNextPage = res.hasNextPage,
                        isLoadingMore = false
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(isLoadingMore = false)
                }
        }
    }

    fun updateFilters(filters: SearchFilters) {
        _state.value = _state.value.copy(filters = filters)
        performSearch(reset = true)
    }

    private fun performSearch(reset: Boolean) {
        val s = _state.value
        _state.value = s.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val page = if (reset) 1 else s.currentPage
            runCatching {
                animeRepository.search(s.query.ifBlank { null }, page = page, filters = s.filters)
            }.onSuccess { res ->
                _state.value = _state.value.copy(
                    results = if (reset) res.items else s.results + res.items,
                    isLoading = false,
                    currentPage = page,
                    hasNextPage = res.hasNextPage,
                    error = null
                )
            }.onFailure { t ->
                _state.value = _state.value.copy(isLoading = false, error = t.message ?: "Search failed")
            }
        }
    }
}

// ─── Details ─────────────────────────────────────────────────────────────────

data class DetailsState(
    val detail: AnimeDetail? = null,
    val episodes: List<Episode> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class DetailsViewModel @Inject constructor(
    private val animeRepository: AnimeRepository,
    private val episodeRepository: EpisodeRepository,
    private val libraryRepository: LibraryRepository,
    savedStateHandle: SavedStateHandle,
    private val dataStore: SettingsDataStore
) : ViewModel() {

    val animeId: Int = savedStateHandle.get<Int>("animeId")
        ?: savedStateHandle.get<String>("animeId")?.toIntOrNull()
        ?: 0

    private val _state = MutableStateFlow(DetailsState())
    val state: StateFlow<DetailsState> = _state.asStateFlow()

    val isSaved: StateFlow<Boolean> = libraryRepository.observeIsSaved(animeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val savedCategoryIds: StateFlow<List<Long>> = libraryRepository.observeCategoryIdsForAnime(animeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<Category>> = libraryRepository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val spec: StateFlow<ScreenSpec?> = dataStore.specForScreen("details")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init { load(forceRefresh = false) }

    fun refresh() = load(forceRefresh = true)

    private fun load(forceRefresh: Boolean) {
        _state.value = _state.value.copy(
            isLoading = !forceRefresh && _state.value.detail == null,
            isRefreshing = forceRefresh,
            error = null
        )
        viewModelScope.launch {
            val detail = runCatching { animeRepository.getAnimeDetail(animeId, forceRefresh = forceRefresh) }
                .getOrNull()
            if (detail == null) {
                _state.value = _state.value.copy(isLoading = false, isRefreshing = false, error = "Couldn't load details")
                return@launch
            }
            val episodes = runCatching {
                episodeRepository.getEpisodes(
                    animeId = animeId,
                    idMal = detail.idMal,
                    episodeCount = detail.episodes,
                    forceRefresh = forceRefresh,
                    titleForKitsuSearch = detail.displayTitle
                )
            }.getOrDefault(emptyList())
            _state.value = DetailsState(
                detail = detail,
                episodes = episodes,
                isLoading = false,
                isRefreshing = false,
                error = null
            )
        }
    }

    fun saveToDefault() {
        val detail = _state.value.detail ?: return
        viewModelScope.launch {
            libraryRepository.saveAnime(detail, categoryIds = listOf(Category.DEFAULT_ID))
        }
    }

    fun saveToCategories(ids: List<Long>) {
        val detail = _state.value.detail ?: return
        viewModelScope.launch {
            libraryRepository.saveAnime(detail, categoryIds = ids)
        }
    }

    fun unsave() {
        viewModelScope.launch { libraryRepository.removeAnime(animeId) }
    }

    fun addCategory(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { libraryRepository.addCategory(name.trim()) }
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch { libraryRepository.deleteCategory(id) }
    }
}

// ─── Settings ────────────────────────────────────────────────────────────────

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: SettingsDataStore
) : ViewModel() {

    val aiSettings: StateFlow<AiSettings> = dataStore.aiSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiSettings())

    val quickEditEnabled: StateFlow<Boolean> = dataStore.quickEditEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val themeMode: StateFlow<String> = dataStore.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "AUTO")

    val screenSpecs: StateFlow<Map<String, ScreenSpec>> = dataStore.screenSpecs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun saveAiSettings(settings: AiSettings) {
        viewModelScope.launch { dataStore.saveAiSettings(settings) }
    }

    fun setQuickEditEnabled(enabled: Boolean) {
        viewModelScope.launch { dataStore.setQuickEditEnabled(enabled) }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { dataStore.setThemeMode(mode) }
    }

    fun clearSpec(screenName: String) {
        viewModelScope.launch { dataStore.clearSpec(screenName) }
    }

    fun clearAllSpecs() {
        viewModelScope.launch { dataStore.clearAllSpecs() }
    }
}

// ─── ColorExtractor provider ─────────────────────────────────────────────────

/**
 * Tiny HiltViewModel whose only job is to expose the singleton [ColorExtractor] to composables
 * (Hilt can't inject directly into @Composable functions). Screens fetch it via `hiltViewModel()`
 * and pass the instance down to [com.adaptiveui.animeapp.interpreter.UiSpecInterpreter].
 */
@HiltViewModel
class ColorExtractorProvider @Inject constructor(
    val colorExtractor: ColorExtractor
) : ViewModel()
