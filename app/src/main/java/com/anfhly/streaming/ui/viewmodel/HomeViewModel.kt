package com.anfhly.streaming.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anfhly.streaming.data.model.Content
import com.anfhly.streaming.data.model.ContentCategory
import com.anfhly.streaming.data.model.InterfaceMode
import com.anfhly.streaming.data.model.NavigationSection
import com.anfhly.streaming.data.model.NavigationState
import com.anfhly.streaming.data.model.Result
import com.anfhly.streaming.data.model.WatchlistItem
import com.anfhly.streaming.data.repository.ContentRepository
import com.anfhly.streaming.data.repository.WatchlistRepository
import com.anfhly.streaming.utils.MemoryUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.async

class HomeViewModel(
    private val contentRepository: ContentRepository,
    private val watchlistRepository: WatchlistRepository = WatchlistRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _categories = MutableStateFlow<List<ContentCategory>>(emptyList())
    val categories: StateFlow<List<ContentCategory>> = _categories.asStateFlow()

    private val _featuredSeries = MutableStateFlow<List<Content.Series>>(emptyList())
    val featuredSeries: StateFlow<List<Content.Series>> = _featuredSeries.asStateFlow()

    private val _featuredMovies = MutableStateFlow<List<Content.Movie>>(emptyList())
    val featuredMovies: StateFlow<List<Content.Movie>> = _featuredMovies.asStateFlow()

    private val _featuredContent = MutableStateFlow<List<Content>>(emptyList())
    val featuredContent: StateFlow<List<Content>> = _featuredContent.asStateFlow()

    private val _currentFeaturedIndex = MutableStateFlow(0)
    val currentFeaturedIndex: StateFlow<Int> = _currentFeaturedIndex.asStateFlow()

    private val _navigationState = MutableStateFlow(NavigationState())
    val navigationState: StateFlow<NavigationState> = _navigationState.asStateFlow()

    private val _activeInterface = MutableStateFlow(InterfaceMode.HOME)
    val activeInterface: StateFlow<InterfaceMode> = _activeInterface.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Content>>(emptyList())
    val searchResults: StateFlow<List<Content>> = _searchResults.asStateFlow()

    private val _movies = MutableStateFlow<List<Content.Movie>>(emptyList())
    val movies: StateFlow<List<Content.Movie>> = _movies.asStateFlow()

    private val _series = MutableStateFlow<List<Content.Series>>(emptyList())
    val series: StateFlow<List<Content.Series>> = _series.asStateFlow()

    private val _seriesCategories = MutableStateFlow<List<ContentCategory>>(emptyList())
    val seriesCategories: StateFlow<List<ContentCategory>> = _seriesCategories.asStateFlow()

    private val _moviesCategories = MutableStateFlow<List<ContentCategory>>(emptyList())
    val moviesCategories: StateFlow<List<ContentCategory>> = _moviesCategories.asStateFlow()

    private val _watchlist = MutableStateFlow<List<WatchlistItem>>(emptyList())
    val watchlist: StateFlow<List<WatchlistItem>> = _watchlist.asStateFlow()

    private val _channels = MutableStateFlow<List<Content.Movie>>(emptyList())
    val channels: StateFlow<List<Content.Movie>> = _channels.asStateFlow()

    private val _channelsCategories = MutableStateFlow<List<ContentCategory>>(emptyList())
    val channelsCategories: StateFlow<List<ContentCategory>> = _channelsCategories.asStateFlow()


    init {
        // loadContent() is now called explicitly to avoid lagging the splash animation
    }

    fun loadContent() {
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.value = HomeUiState.Loading
            android.util.Log.d("HomeViewModel", "Starting content load...")
            
            // Log memory usage before loading
            android.util.Log.d("HomeViewModel", "Memory before load: ${MemoryUtils.getProcessMemoryUsage()}")

            try {
                // Load all content in parallel para mejor rendimiento
                val moviesDeferred = async(Dispatchers.Default) {
                    try {
                        contentRepository.getAllMovies()
                    } catch (e: Exception) {
                        android.util.Log.e("HomeViewModel", "Movies loading failed", e)
                        Result.Error(e)
                    }
                }
                
                val seriesDeferred = async(Dispatchers.Default) {
                    try {
                        contentRepository.getAllSeries()
                    } catch (e: Exception) {
                        android.util.Log.e("HomeViewModel", "Series loading failed", e)
                        Result.Error(e)
                    }
                }
                
                val categoriesDeferred = async(Dispatchers.Default) {
                    try {
                        contentRepository.getContentCategories()
                    } catch (e: Exception) {
                        android.util.Log.e("HomeViewModel", "Categories loading failed", e)
                        Result.Error(e)
                    }
                }
                
                val moviesByGenresDeferred = async(Dispatchers.Default) {
                    try {
                        contentRepository.getMoviesByGenres()
                    } catch (e: Exception) {
                        android.util.Log.e("HomeViewModel", "Movies genres loading failed", e)
                        Result.Error(e)
                    }
                }
                
                val seriesByGenresDeferred = async(Dispatchers.Default) {
                    try {
                        contentRepository.getSeriesByGenres()
                    } catch (e: Exception) {
                        android.util.Log.e("HomeViewModel", "Series genres loading failed", e)
                        Result.Error(e)
                    }
                }
                
                val channelsDeferred = async(Dispatchers.Default) {
                    try {
                        contentRepository.getAllChannels()
                    } catch (e: Exception) {
                        android.util.Log.e("HomeViewModel", "Channels loading failed", e)
                        Result.Error(e)
                    }
                }
                
                // Esperar todos los resultados en paralelo
                val moviesResult = moviesDeferred.await()
                val seriesResult = seriesDeferred.await()
                val categoriesResult = categoriesDeferred.await()
                val moviesByGenresResult = moviesByGenresDeferred.await()
                val seriesByGenresResult = seriesByGenresDeferred.await()
                val channelsResult = channelsDeferred.await()


                // Store movies and series
                when (moviesResult) {
                    is Result.Success -> {
                        _movies.value = moviesResult.data
                        android.util.Log.d("HomeViewModel", "Loaded ${moviesResult.data.size} movies")
                    }
                    is Result.Error -> {
                        android.util.Log.e("HomeViewModel", "Movies error: ${moviesResult.message}")
                    }
                    else -> {}
                }

                when (seriesResult) {
                    is Result.Success -> {
                        _series.value = seriesResult.data
                        android.util.Log.d("HomeViewModel", "Loaded ${seriesResult.data.size} series")
                    }
                    is Result.Error -> {
                        android.util.Log.e("HomeViewModel", "Series error: ${seriesResult.message}")
                    }
                    else -> {}
                }

                // Set featured series and movies sorted by upload date (recently added first)
                val moviesSortedByAdded = _movies.value.sortedWith(
                    compareByDescending<Content.Movie> { it.addedAt?.time ?: 0L }
                        .thenByDescending { it.releaseDate.orEmpty() }
                )
                _featuredMovies.value = moviesSortedByAdded

                val seriesSortedByAdded = _series.value.sortedWith(
                    compareByDescending<Content.Series> { it.addedAt?.time ?: 0L }
                        .thenByDescending { it.releaseDate.orEmpty() }
                )
                _featuredSeries.value = seriesSortedByAdded

                // Set featured content for home (hero) - only premiere movies from the current year, sorted from most recent to oldest
                val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    .format(java.util.Calendar.getInstance().time)
                val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)

                val homeFeaturedMovies = _movies.value.filter { movie ->
                    val releaseDate = movie.releaseDate.orEmpty()
                    val releaseYear = releaseDate.take(4).toIntOrNull() ?: 0
                    releaseYear == currentYear && releaseDate <= todayStr
                }.sortedByDescending { it.releaseDate.orEmpty() }

                _featuredContent.value = if (homeFeaturedMovies.isNotEmpty()) {
                    homeFeaturedMovies
                } else {
                    // Fallback to all released movies sorted by release date
                    _movies.value.filter { it.releaseDate.orEmpty() <= todayStr }
                        .sortedByDescending { it.releaseDate.orEmpty() }
                }

                // Set categories
                when (categoriesResult) {
                    is Result.Success -> {
                        _categories.value = categoriesResult.data
                        android.util.Log.d("HomeViewModel", "Loaded ${categoriesResult.data.size} categories")
                    }
                    is Result.Error -> {
                        android.util.Log.e("HomeViewModel", "Categories error: ${categoriesResult.message}")
                    }
                    else -> {}
                }

                // Set movies and series categories from parallel results
                when (moviesByGenresResult) {
                    is Result.Success -> {
                        _moviesCategories.value = moviesByGenresResult.data
                        android.util.Log.d("HomeViewModel", "Loaded ${moviesByGenresResult.data.size} movie categories")
                    }
                    is Result.Error -> {
                        android.util.Log.e("HomeViewModel", "Movies genres error: ${moviesByGenresResult.message}")
                    }
                    else -> {}
                }

                when (seriesByGenresResult) {
                    is Result.Success -> {
                        _seriesCategories.value = seriesByGenresResult.data
                        android.util.Log.d("HomeViewModel", "Loaded ${seriesByGenresResult.data.size} series categories")
                    }
                    is Result.Error -> {
                        android.util.Log.e("HomeViewModel", "Series genres error: ${seriesByGenresResult.message}")
                    }
                    else -> {}
                }

                when (channelsResult) {
                    is Result.Success -> {
                        _channels.value = channelsResult.data
                        android.util.Log.d("HomeViewModel", "Loaded ${channelsResult.data.size} channels")
                        val grouped = channelsResult.data.groupBy { it.genres.firstOrNull() ?: "Canales" }
                        _channelsCategories.value = grouped.map { ContentCategory(it.key, it.value) }
                    }
                    is Result.Error -> {
                        android.util.Log.e("HomeViewModel", "Channels error: ${channelsResult.message}")
                    }
                    else -> {}
                }

                // Determine overall state
                val hasError = moviesResult is Result.Error || seriesResult is Result.Error || channelsResult is Result.Error
                val hasData = _movies.value.isNotEmpty() || _series.value.isNotEmpty() || _channels.value.isNotEmpty()

                android.util.Log.d("HomeViewModel", "State check - hasError: $hasError, hasData: $hasData")
                android.util.Log.d("HomeViewModel", "Movies: ${_movies.value.size}, Series: ${_series.value.size}, Channels: ${_channels.value.size}")

                _uiState.value = when {
                    hasError && !hasData -> HomeUiState.Error("Failed to load content")
                    hasData -> {
                        _searchResults.value = _movies.value + _series.value + _channels.value
                        HomeUiState.Success
                    }
                    else -> HomeUiState.Empty
                }

                android.util.Log.d("HomeViewModel", "Final UI state: ${_uiState.value}")
                
                // Log memory usage after loading
                android.util.Log.d("HomeViewModel", "Memory after load: ${MemoryUtils.getProcessMemoryUsage()}")
                
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Exception loading content", e)
                android.util.Log.d("HomeViewModel", "Memory on error: ${MemoryUtils.getProcessMemoryUsage()}")
                _uiState.value = HomeUiState.Error("Error: ${e.message}")
            }
        }
    }

    private var searchJob: kotlinx.coroutines.Job? = null

    fun search(query: String) {
        _searchQuery.value = query
        
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(500) // Debounce para evitar lag al escribir
            
            if (query.isBlank()) {
                _searchResults.value = _movies.value + _series.value + _channels.value
                return@launch
            }

            when (val result = contentRepository.searchContent(query)) {
                is Result.Success -> {
                    _searchResults.value = result.data
                }
                is Result.Error -> {
                    _searchResults.value = emptyList()
                }
                is Result.Loading -> {
                    // Keep previous results while loading
                }
            }
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = _movies.value + _series.value + _channels.value
    }


    fun loadWatchlist(userId: String, profileId: String) {
        viewModelScope.launch {
            try {
                when (val result = watchlistRepository.getWatchlist(userId, profileId)) {
                    is Result.Success -> {
                        _watchlist.value = result.data
                    }
                    is Result.Error -> {
                        _watchlist.value = emptyList()
                    }
                    is Result.Loading -> {}
                }
            } catch (e: Exception) {
                _watchlist.value = emptyList()
            }
        }
    }

    fun toggleWatchlist(userId: String, profileId: String, content: Content) {
        if (userId.isEmpty() || profileId.isEmpty()) return
        viewModelScope.launch {
            try {
                val tmdbId = (content as? Content.Movie)?.tmdbId ?: (content as? Content.Series)?.tmdbId ?: 0
                val inWatchlist = _watchlist.value.any { it.id == content.id || (it.tmdbId != 0 && it.tmdbId == tmdbId) }
                
                if (inWatchlist) {
                    watchlistRepository.removeFromWatchlist(userId, profileId, content.id)
                } else {
                    val watchlistItem = WatchlistItem(
                        id = content.id,
                        tmdbId = tmdbId,
                        title = content.title,
                        name = content.title,
                        type = if (content is Content.Movie) "movie" else "tv",
                        posterPath = "",
                        posterUrl = content.posterUrl ?: "",
                        backdropPath = "",
                        backdropUrl = content.backdropUrl ?: "",
                        logoUrl = content.logoUrl,
                        backdropPublicityUrl = content.backdropPublicityUrl,
                        overview = content.overview,
                        vote_average = content.voteAverage,
                        release_date = if (content is Content.Movie) content.releaseDate else "",
                        first_air_date = if (content is Content.Series) content.releaseDate else "",
                        profileId = profileId
                    )
                    watchlistRepository.addToWatchlist(userId, profileId, watchlistItem)
                }
                // Reload watchlist after toggling
                loadWatchlist(userId, profileId)
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Error toggling watchlist", e)
            }
        }
    }

    fun setActiveInterface(mode: InterfaceMode) {
        _activeInterface.value = mode
        
        // Reset navigation when switching interfaces
        _navigationState.value = NavigationState(
            section = if (mode == InterfaceMode.SEARCH) NavigationSection.SEARCH else NavigationSection.HEADER
        )
    }

    // Navigation functions for TV remote
    fun navigateUp() {
        val current = _navigationState.value
        _navigationState.value = when (current.section) {
            NavigationSection.CONTENT -> {
                if (current.contentCategoryIndex > 0) {
                    current.copy(contentCategoryIndex = current.contentCategoryIndex - 1)
                } else {
                    current.copy(section = NavigationSection.CAROUSEL)
                }
            }
            NavigationSection.SEARCH_RESULTS -> {
                if (current.searchResultIndex >= 6) {
                    current.copy(searchResultIndex = current.searchResultIndex - 6)
                } else {
                    current.copy(section = NavigationSection.SEARCH)
                }
            }
            else -> current
        }
    }

    fun navigateDown() {
        val current = _navigationState.value
        _navigationState.value = when (current.section) {
            NavigationSection.HEADER -> {
                when (_activeInterface.value) {
                    InterfaceMode.HOME -> current.copy(section = NavigationSection.CAROUSEL)
                    InterfaceMode.SEARCH -> current.copy(section = NavigationSection.SEARCH)
                    else -> current.copy(section = NavigationSection.CONTENT, contentCategoryIndex = 0)
                }
            }
            NavigationSection.CAROUSEL -> {
                current.copy(section = NavigationSection.CONTENT, contentCategoryIndex = 0)
            }
            NavigationSection.CONTENT -> {
                if (current.contentCategoryIndex < _categories.value.size - 1) {
                    current.copy(contentCategoryIndex = current.contentCategoryIndex + 1)
                } else {
                    current
                }
            }
            NavigationSection.SEARCH -> {
                if (_searchResults.value.isNotEmpty()) {
                    current.copy(section = NavigationSection.SEARCH_RESULTS, searchResultIndex = 0)
                } else {
                    current
                }
            }
            else -> current
        }
    }

    fun navigateLeft() {
        val current = _navigationState.value
        _navigationState.value = when (current.section) {
            NavigationSection.HEADER -> {
                val newIndex = if (current.headerIndex > 0) current.headerIndex - 1 else 3
                current.copy(headerIndex = newIndex)
            }
            NavigationSection.CAROUSEL -> {
                val newIndex = if (current.carouselIndex > 0) current.carouselIndex - 1 
                    else _featuredContent.value.size - 1
                current.copy(carouselIndex = newIndex)
            }
            NavigationSection.CONTENT -> {
                if (current.contentItemIndex > 0) {
                    current.copy(contentItemIndex = current.contentItemIndex - 1)
                } else {
                    val category = _categories.value.getOrNull(current.contentCategoryIndex)
                    if (category != null) {
                        current.copy(contentItemIndex = category.items.size - 1)
                    } else {
                        current
                    }
                }
            }
            NavigationSection.SEARCH_RESULTS -> {
                if (current.searchResultIndex > 0) {
                    current.copy(searchResultIndex = current.searchResultIndex - 1)
                } else {
                    current.copy(searchResultIndex = _searchResults.value.size - 1)
                }
            }
            else -> current
        }
    }

    fun navigateRight() {
        val current = _navigationState.value
        _navigationState.value = when (current.section) {
            NavigationSection.HEADER -> {
                val newIndex = if (current.headerIndex < 3) current.headerIndex + 1 else 0
                current.copy(headerIndex = newIndex)
            }
            NavigationSection.CAROUSEL -> {
                val newIndex = if (current.carouselIndex < _featuredContent.value.size - 1) 
                    current.carouselIndex + 1 else 0
                current.copy(carouselIndex = newIndex)
            }
            NavigationSection.CONTENT -> {
                val category = _categories.value.getOrNull(current.contentCategoryIndex)
                if (category != null && current.contentItemIndex < category.items.size - 1) {
                    current.copy(contentItemIndex = current.contentItemIndex + 1)
                } else {
                    current.copy(contentItemIndex = 0)
                }
            }
            NavigationSection.SEARCH_RESULTS -> {
                if (current.searchResultIndex < _searchResults.value.size - 1) {
                    current.copy(searchResultIndex = current.searchResultIndex + 1)
                } else {
                    current.copy(searchResultIndex = 0)
                }
            }
            else -> current
        }
    }

    fun goToNextFeatured() {
        val content = when (_activeInterface.value) {
            InterfaceMode.SERIES -> _featuredSeries.value
            InterfaceMode.MOVIES -> _featuredMovies.value
            InterfaceMode.LIVE -> _channels.value
            InterfaceMode.MY_LIST -> _watchlist.value
            else -> _featuredContent.value
        }
        val nextIndex = (_currentFeaturedIndex.value + 1) % content.size.coerceAtLeast(1)
        _currentFeaturedIndex.value = nextIndex
        _navigationState.value = _navigationState.value.copy(carouselIndex = nextIndex)
    }

    fun goToPreviousFeatured() {
        val content = when (_activeInterface.value) {
            InterfaceMode.SERIES -> _featuredSeries.value
            InterfaceMode.MOVIES -> _featuredMovies.value
            InterfaceMode.LIVE -> _channels.value
            InterfaceMode.MY_LIST -> _watchlist.value
            else -> _featuredContent.value
        }
        val prevIndex = if (_currentFeaturedIndex.value > 0) {
            _currentFeaturedIndex.value - 1
        } else {
            content.size - 1
        }
        _currentFeaturedIndex.value = prevIndex
        _navigationState.value = _navigationState.value.copy(carouselIndex = prevIndex)
    }

    fun setHeaderIndex(index: Int) {
        _navigationState.value = _navigationState.value.copy(
            section = NavigationSection.HEADER,
            headerIndex = index
        )
    }

    fun getSelectedContent(): Content? {
        val nav = _navigationState.value
        return when (nav.section) {
            NavigationSection.CAROUSEL -> _featuredContent.value.getOrNull(nav.carouselIndex)
            NavigationSection.CONTENT -> {
                val category = _categories.value.getOrNull(nav.contentCategoryIndex)
                category?.items?.getOrNull(nav.contentItemIndex)
            }
            NavigationSection.SEARCH_RESULTS -> _searchResults.value.getOrNull(nav.searchResultIndex)
            else -> null
        }
    }

    sealed class HomeUiState {
        data object Loading : HomeUiState()
        data object Success : HomeUiState()
        data object Empty : HomeUiState()
        data class Error(val message: String) : HomeUiState()
    }
}
