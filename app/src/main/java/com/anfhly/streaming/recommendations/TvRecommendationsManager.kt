package com.anfhly.streaming.recommendations

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import com.anfhly.streaming.data.model.Content
import com.anfhly.streaming.data.model.Result
import com.anfhly.streaming.data.repository.ContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TvRecommendationsManager {
    private const val TAG = "TvRecommendations"
    private const val PREFS_NAME = "anfhly_tv_recommendations"
    private const val KEY_CHANNEL_ID = "channel_id"

    suspend fun updateRecommendations(context: Context) = withContext(Dispatchers.IO) {
        try {
            val helper = PreviewChannelHelper(context)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            var channelId = prefs.getLong(KEY_CHANNEL_ID, -1L)

            // Verify if the channel still exists in system
            if (channelId != -1L) {
                try {
                    val channel = helper.getPreviewChannel(channelId)
                    if (channel == null) {
                        channelId = -1L
                    }
                } catch (e: Exception) {
                    channelId = -1L
                }
            }

            // Create channel if it doesn't exist
            if (channelId == -1L) {
                val channel = PreviewChannel.Builder()
                    .setDisplayName("Estrenos en ANFHLYTV+")
                    .setDescription("Las últimas películas y series de estreno")
                    .setAppLinkIntentUri(Uri.parse("anfhly://home"))
                    .build()

                channelId = helper.publishChannel(channel)
                prefs.edit().putLong(KEY_CHANNEL_ID, channelId).apply()
                Log.d(TAG, "Created new recommendation channel: $channelId")
                
                // Make the channel visible by default on Android 8.0+
                TvContractCompat.requestChannelBrowsable(context, channelId)
            }

            // Fetch movies and series
            val repository = ContentRepository()
            val contentList = mutableListOf<Content>()
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)

            val moviesResult = repository.getAllMovies()
            val seriesResult = repository.getAllSeries()
            
            // Filter movies for current year (Estrenos)
            if (moviesResult is Result.Success) {
                val estrenosMovies = moviesResult.data.filter { movie ->
                    val releaseYear = movie.releaseDate?.take(4)?.toIntOrNull() ?: 0
                    releaseYear == currentYear
                }
                contentList.addAll(estrenosMovies)
            }
            
            // Filter series for current year (Estrenos)
            if (seriesResult is Result.Success) {
                val estrenosSeries = seriesResult.data.filter { series ->
                    val releaseYear = series.releaseDate?.take(4)?.toIntOrNull() ?: 0
                    releaseYear == currentYear
                }
                contentList.addAll(estrenosSeries)
            }

            // Fallback: If Firestore doesn't have current-year estrenos, load the latest added content
            if (contentList.size < 5) {
                if (moviesResult is Result.Success) {
                    contentList.addAll(moviesResult.data.sortedByDescending { it.releaseDate ?: "" }.take(10))
                }
                if (seriesResult is Result.Success) {
                    contentList.addAll(seriesResult.data.sortedByDescending { it.releaseDate ?: "" }.take(10))
                }
            }

            if (contentList.isEmpty()) {
                Log.d(TAG, "No content found to recommend")
                return@withContext
            }

            // Distinct list by ID, sorted by releaseDate, take top 15
            val recommendations = contentList
                .distinctBy { it.id }
                .sortedByDescending { content ->
                    when (content) {
                        is Content.Movie -> content.releaseDate
                        is Content.Series -> content.releaseDate
                    } ?: ""
                }
                .take(15)

            // Clear existing programs in the channel
            try {
                context.contentResolver.delete(
                    TvContractCompat.PreviewPrograms.CONTENT_URI,
                    "${TvContractCompat.PreviewPrograms.COLUMN_CHANNEL_ID} = ?",
                    arrayOf(channelId.toString())
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing old recommendations", e)
            }

            // Add new programs
            recommendations.forEach { content ->
                val title = content.title
                val overview = content.overview
                val posterUrl = content.posterUrl ?: ""
                
                val intentUri = when (content) {
                    is Content.Movie -> "anfhly://content/movie/${content.id}"
                    is Content.Series -> "anfhly://content/series/${content.id}"
                }

                val builder = PreviewProgram.Builder()
                    .setChannelId(channelId)
                    .setTitle(title)
                    .setDescription(overview)
                    .setIntentUri(Uri.parse(intentUri))
                    .setPosterArtUri(Uri.parse(posterUrl))
                    .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3)
                    
                if (content is Content.Movie) {
                    builder.setType(TvContractCompat.PreviewPrograms.TYPE_MOVIE)
                } else {
                    builder.setType(TvContractCompat.PreviewPrograms.TYPE_TV_SERIES)
                }

                val program = builder.build()
                helper.publishPreviewProgram(program)
            }

            Log.d(TAG, "Successfully updated recommendation channel with ${recommendations.size} items")

        } catch (e: Exception) {
            Log.e(TAG, "Error updating recommendations channel", e)
        }
    }
}
