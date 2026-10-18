package com.anfhly.streaming.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anfhly.streaming.data.model.Content
import com.anfhly.streaming.data.model.Episode
import com.anfhly.streaming.data.model.Result
import com.anfhly.streaming.data.model.Season
import com.anfhly.streaming.data.repository.ContentRepository
import com.anfhly.streaming.data.repository.TMDBRepository
import com.anfhly.streaming.data.repository.WatchProgressRepository
import com.anfhly.streaming.data.repository.WatchProgress
import com.anfhly.streaming.data.repository.WatchlistRepository
import com.anfhly.streaming.data.model.WatchlistItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ContentDetailViewModel : ViewModel() {
    
    private val contentRepository = ContentRepository()
    private val tmdbRepository = TMDBRepository()
    private val watchlistRepository = WatchlistRepository()
    private var watchProgressRepository: WatchProgressRepository? = null
    private val TAG = "ContentDetailViewModel"
    
    private val _contentDetail = MutableStateFlow<Content?>(null)
    val contentDetail: StateFlow<Content?> = _contentDetail
    
    private val _seasons = MutableStateFlow<List<Season>>(emptyList())
    val seasons: StateFlow<List<Season>> = _seasons
    
    private val _selectedSeason = MutableStateFlow<Season?>(null)
    val selectedSeason: StateFlow<Season?> = _selectedSeason
    
    private val _episodes = MutableStateFlow<List<Episode>>(emptyList())
    val episodes: StateFlow<List<Episode>> = _episodes
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    // Watch progress state
    private val _hasWatchProgress = MutableStateFlow(false)
    val hasWatchProgress: StateFlow<Boolean> = _hasWatchProgress
    
    private val _watchProgress = MutableStateFlow<WatchProgress?>(null)
    val watchProgress: StateFlow<WatchProgress?> = _watchProgress
    
    private val _isInWatchlist = MutableStateFlow(false)
    val isInWatchlist: StateFlow<Boolean> = _isInWatchlist
    
    fun initWatchProgressRepository(context: android.content.Context) {
        watchProgressRepository = WatchProgressRepository(context)
    }
    
    fun loadContentDetails(content: Content) {
        viewModelScope.launch {
            _isLoading.value = true
            
            try {
                when (content) {
                    is Content.Movie -> {
                        // Load movie details from Firestore
                        val result = contentRepository.getMovieById(content.id)
                        if (result is Result.Success) {
                            _contentDetail.value = result.data
                        } else {
                            _contentDetail.value = content
                        }
                        // Check for saved progress
                        checkMovieProgress(content.id)
                    }
                    is Content.Series -> {
                        // Load series details with seasons from Firestore
                        val result = contentRepository.getSeriesById(content.id)
                        val series = if (result is Result.Success) result.data else content
                        _contentDetail.value = series
                        _seasons.value = series.seasons
                        
                        // Check for last watched episode first to select correct season
                        var seasonToSelect: Season? = null
                        val progressRepo = watchProgressRepository
                        if (progressRepo != null) {
                            val lastWatched = progressRepo.getLastWatchedEpisode(series.id)
                            if (lastWatched != null && lastWatched.positionMs > 10000 && lastWatched.positionMs < lastWatched.durationMs - 10000) {
                                _watchProgress.value = lastWatched
                                _hasWatchProgress.value = true
                                seasonToSelect = series.seasons.find { it.seasonNumber == lastWatched.seasonNumber }
                                Log.d(TAG, "Series progress found: S${lastWatched.seasonNumber}E${lastWatched.episodeNumber} at ${lastWatched.positionMs}ms. Auto-selecting Season S${lastWatched.seasonNumber}")
                            } else {
                                _hasWatchProgress.value = false
                                _watchProgress.value = null
                            }
                        }
                        
                        if (seasonToSelect == null) {
                            seasonToSelect = series.seasons.firstOrNull()
                        }
                        
                        if (seasonToSelect != null) {
                            selectSeason(seasonToSelect)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading content details", e)
                _contentDetail.value = content
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    private suspend fun checkMovieProgress(movieId: String) {
        watchProgressRepository?.let { repo ->
            val progress = repo.getMovieProgress(movieId)
            if (progress != null && progress.positionMs > 10000 && progress.positionMs < progress.durationMs - 10000) {
                _watchProgress.value = progress
                _hasWatchProgress.value = true
                Log.d(TAG, "Movie progress found: ${progress.positionMs}ms / ${progress.durationMs}ms")
            } else {
                _hasWatchProgress.value = false
                _watchProgress.value = null
            }
        }
    }
    
    private suspend fun checkSeriesProgress(seriesId: String) {
        watchProgressRepository?.let { repo ->
            val lastWatched = repo.getLastWatchedEpisode(seriesId)
            if (lastWatched != null && lastWatched.positionMs > 10000 && lastWatched.positionMs < lastWatched.durationMs - 10000) {
                _watchProgress.value = lastWatched
                _hasWatchProgress.value = true
                Log.d(TAG, "Series progress found: S${lastWatched.seasonNumber}E${lastWatched.episodeNumber} at ${lastWatched.positionMs}ms")
            } else {
                _hasWatchProgress.value = false
                _watchProgress.value = null
            }
        }
    }
    
    fun checkWatchlist(userId: String, profileId: String, contentId: String) {
        if (userId.isEmpty()) return
        viewModelScope.launch {
            try {
                val result = watchlistRepository.isInWatchlist(userId, profileId, contentId)
                if (result is Result.Success) {
                    _isInWatchlist.value = result.data
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking watchlist", e)
            }
        }
    }
    
    fun toggleWatchlist(userId: String, profileId: String, content: Content) {
        if (userId.isEmpty()) return
        viewModelScope.launch {
            try {
                val currentlyInWatchlist = _isInWatchlist.value
                
                if (currentlyInWatchlist) {
                    // Remove
                    val result = watchlistRepository.removeFromWatchlist(userId, profileId, content.id)
                    if (result is Result.Success) {
                        _isInWatchlist.value = false
                    }
                } else {
                    // Add
                    val watchlistItem = WatchlistItem(
                        id = content.id,
                        tmdbId = if (content is Content.Movie) content.tmdbId ?: 0 else if (content is Content.Series) content.tmdbId ?: 0 else 0,
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
                    
                    val result = watchlistRepository.addToWatchlist(userId, profileId, watchlistItem)
                    if (result is Result.Success) {
                        _isInWatchlist.value = true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling watchlist", e)
            }
        }
    }
    
    fun formatWatchTime(): String {
        val progress = _watchProgress.value ?: return ""
        val remainingMs = progress.durationMs - progress.positionMs
        val minutes = remainingMs / 60000
        val seconds = (remainingMs % 60000) / 1000
        return if (minutes > 0) {
            "${minutes}m ${seconds}s restantes"
        } else {
            "${seconds}s restantes"
        }
    }
    
    fun formatWatchTimeFromStart(): String {
        val progress = _watchProgress.value ?: return ""
        val minutes = progress.positionMs / 60000
        val seconds = (progress.positionMs % 60000) / 1000
        return "${minutes}:${seconds.toString().padStart(2, '0')}"
    }
    
    fun selectSeason(season: Season) {
        _selectedSeason.value = season
        
        // Load episodes for this season
        viewModelScope.launch {
            val content = _contentDetail.value
            if (content is Content.Series) {
                Log.d(TAG, "Loading episodes for series ${content.id}, season ${season.id} (seasonNumber: ${season.seasonNumber})")
                
                val result = contentRepository.getEpisodesForSeason(
                    seriesId = content.id,
                    seasonId = season.id,
                    seasonNumber = season.seasonNumber
                )
                
                if (result is Result.Success) {
                    var episodes = result.data
                    
                    // Enrich episodes with TMDB data (images and proper names)
                    episodes = tmdbRepository.enrichEpisodesWithTMDB(
                        tmdbId = content.tmdbId,
                        seasonNumber = season.seasonNumber,
                        episodes = episodes
                    )
                    
                    _episodes.value = episodes
                    Log.d(TAG, "Loaded ${episodes.size} episodes for season ${season.seasonNumber}")
                    episodes.forEach { episode ->
                        Log.d(TAG, "  Episode ${episode.episodeNumber}: ${episode.name}, stillUrl: ${episode.stillUrl}")
                    }
                } else {
                    Log.e(TAG, "Failed to load episodes for season ${season.id}")
                    _episodes.value = season.episodes
                }
            } else {
                Log.e(TAG, "Content is not a Series or is null")
            }
        }
    }
    
    fun refreshContent() {
        _contentDetail.value?.let { content ->
            loadContentDetails(content)
        }
    }
    
    fun clearContentState() {
        Log.d(TAG, "Clearing content state")
        _contentDetail.value = null
        _seasons.value = emptyList()
        _selectedSeason.value = null
        _episodes.value = emptyList()
        _isLoading.value = false
        _hasWatchProgress.value = false
        _watchProgress.value = null
    }
}
