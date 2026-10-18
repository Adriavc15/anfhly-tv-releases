package com.anfhly.streaming.provider

import android.app.SearchManager
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.BaseColumns
import com.anfhly.streaming.data.model.Content
import com.anfhly.streaming.data.model.Result
import com.anfhly.streaming.data.repository.ContentRepository
import kotlinx.coroutines.runBlocking

class SearchSuggestionProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val query = uri.lastPathSegment?.lowercase() ?: ""
        if (query.isEmpty() || query == "search_suggest_query") {
            return null
        }

        val results = runBlocking {
            try {
                val repository = ContentRepository()
                when (val searchResult = repository.searchContent(query)) {
                    is Result.Success -> searchResult.data
                    else -> emptyList()
                }
            } catch (e: Exception) {
                android.util.Log.e("SearchSuggestionProvider", "Error searching content", e)
                emptyList()
            }
        }

        val columns = arrayOf(
            BaseColumns._ID,
            SearchManager.SUGGEST_COLUMN_TEXT_1,
            SearchManager.SUGGEST_COLUMN_TEXT_2,
            SearchManager.SUGGEST_COLUMN_RESULT_CARD_IMAGE,
            SearchManager.SUGGEST_COLUMN_INTENT_DATA,
            SearchManager.SUGGEST_COLUMN_CONTENT_TYPE,
            SearchManager.SUGGEST_COLUMN_PRODUCTION_YEAR
        )

        val cursor = MatrixCursor(columns)
        results.forEachIndexed { index, content ->
            val id = index.toLong()
            val title = content.title
            val overview = content.overview
            val posterUrl = content.posterUrl

            val releaseDate = when (content) {
                is Content.Movie -> content.releaseDate
                is Content.Series -> content.releaseDate
            }
            val year = releaseDate?.take(4)?.toIntOrNull()

            val contentType = when (content) {
                is Content.Movie -> "video/mp4"
                is Content.Series -> "video/series"
            }

            val intentData = when (content) {
                is Content.Movie -> "anfhly://content/movie/${content.id}"
                is Content.Series -> "anfhly://content/series/${content.id}"
            }

            cursor.addRow(arrayOf(
                id,
                title,
                overview,
                posterUrl,
                intentData,
                contentType,
                year
            ))
        }
        return cursor
    }

    override fun getType(uri: Uri): String? {
        return SearchManager.SUGGEST_MIME_TYPE
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        return 0
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        return 0
    }
}
