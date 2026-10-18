package com.anfhly.streaming.data.model

import java.io.Serializable as JavaSerializable
import java.util.Date
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Serializer for java.util.Date
 */
object DateSerializer : KSerializer<Date> {
    override val descriptor = PrimitiveSerialDescriptor("Date", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: Date) = encoder.encodeLong(value.time)
    override fun deserialize(decoder: Decoder): Date = Date(decoder.decodeLong())
}

/**
 * User data model representing a Firebase Auth user
 */
data class User(
    val uid: String = "",
    val email: String = "",
    val displayName: String? = null,
    val photoUrl: String? = null,
    val emailVerified: Boolean = false,
    val createdAt: Date? = null,
    val lastLoginAt: Date? = null,
    val role: String = "user",
    val isPremium: Boolean = false,
    val canUpload: Boolean = false
)

/**
 * User profile for multi-profile support
 */
@Serializable
 data class UserProfile(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val avatar: String = "adult1",
    val customImageUrl: String? = null,
    val isKids: Boolean = false,
    val isLocked: Boolean = false,
    val pin: String? = null,
    val profileColor: String = "#3b82f6",
    @Serializable(with = DateSerializer::class)
    val createdAt: Date? = null,
    @Serializable(with = DateSerializer::class)
    val updatedAt: Date? = null
) {
    companion object {
        const val AVATAR_ADULT_1 = "adult1"
        const val AVATAR_ADULT_2 = "adult2"
        const val AVATAR_ADULT_3 = "adult3"
        const val AVATAR_ADULT_4 = "adult4"
        const val AVATAR_KIDS_1 = "kids1"
        const val AVATAR_KIDS_2 = "kids2"
        
        val AVATAR_OPTIONS = listOf(
            AVATAR_ADULT_1, AVATAR_ADULT_2, AVATAR_ADULT_3, AVATAR_ADULT_4,
            AVATAR_KIDS_1, AVATAR_KIDS_2
        )
    }
}

/**
 * Miembro del reparto (Firestore: array `cast` con objetos TMDB o strings).
 */
data class CastMember(
    val name: String,
    val character: String? = null,
    val profileUrl: String? = null
) : JavaSerializable

/**
 * Streaming content (Movies and Series)
 */
sealed class Content : JavaSerializable {
    abstract val id: String
    abstract val title: String
    abstract val overview: String
    abstract val posterUrl: String?
    abstract val backdropUrl: String?
    abstract val backdropPublicityUrl: String?
    abstract val voteAverage: Double
    abstract val releaseDate: String?
    abstract val genres: List<String>
    abstract val isPremium: Boolean
    abstract val logoUrl: String?
    abstract val customPosterUrl: String?
    abstract val customBackdropUrl: String?
    abstract val customLogoUrl: String?
    abstract val customBackdropUrlBluray: String?
    abstract val customBackdropUrlImax: String?
    abstract val customBackdropUrlOpenmatte: String?
    abstract val customBackdropUrl43: String?
    open val platform: String? = null
    
    data class Movie(
        override val id: String = "",
        override val title: String = "",
        override val overview: String = "",
        override val posterUrl: String? = null,
        override val backdropUrl: String? = null,
        override val backdropPublicityUrl: String? = null,
        override val voteAverage: Double = 0.0,
        override val releaseDate: String? = null,
        override val genres: List<String> = emptyList(),
        override val isPremium: Boolean = false,
        override val logoUrl: String? = null,
        override val customPosterUrl: String? = null,
        override val customBackdropUrl: String? = null,
        override val customLogoUrl: String? = null,
        override val customBackdropUrlBluray: String? = null,
        override val customBackdropUrlImax: String? = null,
        override val customBackdropUrlOpenmatte: String? = null,
        override val customBackdropUrl43: String? = null,
        override val platform: String? = null,
        val runtime: Int? = null,
        val videoUrl: String? = null,
        val fileUrl: String? = null,
        val fileUrlImax: String? = null,
        val fileUrlOpenmatte: String? = null,
        val fileUrl43: String? = null,
        val filemoonUrl: String? = null,
        val easyvidplayerUrl: String? = null,
        val luluvdoUrl: String? = null,
        val tmdbId: Int? = null,
        val trailerUrl: String? = null,
        val isCustomPoster: Boolean = false,
        val addedAt: Date? = null,
        val repairedAt: Date? = null,
        val updatedAt: Date? = null,
        val certification: String? = null,
        val cast: List<String> = emptyList(),
        val castMembers: List<CastMember> = emptyList(),
        val hasDolbyAtmos: Boolean = false
    ) : Content()

    data class Series(
        override val id: String = "",
        override val title: String = "",
        override val overview: String = "",
        override val posterUrl: String? = null,
        override val backdropUrl: String? = null,
        override val backdropPublicityUrl: String? = null,
        override val voteAverage: Double = 0.0,
        override val releaseDate: String? = null,
        override val genres: List<String> = emptyList(),
        override val isPremium: Boolean = false,
        override val logoUrl: String? = null,
        override val customPosterUrl: String? = null,
        override val customBackdropUrl: String? = null,
        override val customLogoUrl: String? = null,
        override val customBackdropUrlBluray: String? = null,
        override val customBackdropUrlImax: String? = null,
        override val customBackdropUrlOpenmatte: String? = null,
        override val customBackdropUrl43: String? = null,
        override val platform: String? = null,
        val seasons: List<Season> = emptyList(),
        val seasonCount: Int = 0,
        val episodeCount: Int = 0,
        val tmdbId: Int? = null,
        val trailerUrl: String? = null,
        val isCustomPoster: Boolean = false,
        val addedAt: Date? = null,
        val repairedAt: Date? = null,
        val updatedAt: Date? = null,
        val certification: String? = null,
        val cast: List<String> = emptyList(),
        val castMembers: List<CastMember> = emptyList(),
        val hasDolbyAtmos: Boolean = false
    ) : Content()
}

/**
 * Season model for series
 */
data class Season(
    val id: String = "",
    val seriesId: String = "",
    val seasonNumber: Int = 0,
    val name: String = "",
    val overview: String = "",
    val posterUrl: String? = null,
    val episodeCount: Int = 0,
    val episodes: List<Episode> = emptyList(),
    val airDate: String? = null
) : JavaSerializable

/**
 * Episode model
 */
data class Episode(
    val id: String = "",
    val seriesId: String = "",
    val seasonId: String = "",
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val name: String = "",
    val overview: String = "",
    val stillUrl: String? = null,
    val videoUrl: String? = null,
    val filemoonUrl: String? = null,
    val easyvidplayerUrl: String? = null,
    val luluvdoUrl: String? = null,
    val runtime: Int? = null,
    val airDate: String? = null,
    val voteAverage: Double = 0.0
) : JavaSerializable

/**
 * Content category for home screen rows
 */
data class ContentCategory(
    val title: String,
    val items: List<Content> = emptyList()
)

/**
 * Watchlist item
 */
data class WatchlistItem(
    val id: String = "", // Document ID in Firebase
    val tmdbId: Int = 0, // TMDB ID
    val firebaseId: String = "", // Firebase ID for navigation
    val title: String? = null,
    val name: String? = null,
    val type: String = "", // "movie" or "series"
    val posterPath: String = "",
    val posterUrl: String = "",
    val backdropPath: String? = null,
    val backdropUrl: String? = null,
    val logoUrl: String? = null,
    val backdropPublicityUrl: String? = null,
    val overview: String? = null,
    val vote_average: Double? = null,
    val release_date: String? = null,
    val first_air_date: String? = null,
    val addedAt: Date? = null,
    val profileId: String = ""
) {
    // Helper property to get the display name (compatibilidad con código existente)
    val displayName: String
        get() = when {
            !title.isNullOrEmpty() -> title
            !name.isNullOrEmpty() -> name
            else -> "Sin título"
        }
        
    // Helper property to get the release year
    val year: String?
        get() = when {
            !release_date.isNullOrEmpty() -> release_date.take(4)
            !first_air_date.isNullOrEmpty() -> first_air_date.take(4)
            else -> null
        }
        
    // Helper property to get the rating
    val rating: String?
        get() = vote_average?.toString()
        
    // Helper properties para compatibilidad con código existente
    val contentType: String
        get() = type
        
    val contentId: String
        get() = id
}

/**
 * User settings
 */
data class UserSettings(
    val defaultAudioLanguage: String = "es",
    val audioEnabled: Boolean = true,
    val defaultSubtitleLanguage: String = "es",
    val subtitlesEnabled: Boolean = true,
    val defaultVideoQuality: String = "1080p",
    val autoQuality: Boolean = true,
    val autoplay: Boolean = true,
    val notifications: Boolean = true,
    val parentalControl: Boolean = false,
    val preferredLanguage: String = "es"
)

/**
 * Navigation state for TV remote control
 */
data class NavigationState(
    val section: NavigationSection = NavigationSection.HEADER,
    val headerIndex: Int = 0,
    val carouselIndex: Int = 0,
    val contentCategoryIndex: Int = 0,
    val contentItemIndex: Int = 0,
    val searchResultIndex: Int = 0
)

enum class NavigationSection {
    HEADER,
    CAROUSEL,
    CONTENT,
    SEARCH,
    SEARCH_RESULTS
}

enum class InterfaceMode {
    HOME,
    MOVIES,
    SERIES,
    CATEGORIES,
    MY_LIST,
    SHOWS,
    LIVE,
    SEARCH,
    SETTINGS
}

/**
 * Video model for extractors (from streamflix-main)
 */
data class Video(
    val source: String,
    val subtitles: List<Subtitle> = listOf(),
    val headers: Map<String, String>? = null,
    val type: String? = null,
    val extraBuffering: Boolean = false,
    val useServerSubtitleSetting: Boolean = false,
    val qualities: List<Quality> = emptyList(),
    val audios: List<AudioTrack> = emptyList(),
    val expiresAt: Long? = null // Timestamp when URL expires (for expiring URLs like Ok.ru)
) : JavaSerializable {

    data class Quality(
        val label: String,
        val url: String,
        val selected: Boolean = false
    ) : JavaSerializable

    data class AudioTrack(
        val label: String,
        val language: String,
        val file: String,
        val default: Boolean = false
    ) : JavaSerializable

    sealed class Type : JavaSerializable {
        data class Movie(val id: String) : Type()
        data class Episode(val tvShow: TvShow, val season: Season, val number: Int) : Type() {
            data class TvShow(val id: String, val title: String? = null, val poster: String? = null, val banner: String? = null, val releaseDate: String? = null)
            data class Season(val number: Int, val title: String? = null)
        }
    }

    data class Subtitle(
        val label: String,
        val file: String,
        var default: Boolean = false,
        val initialDefault: Boolean = false
    ) : JavaSerializable

    data class Server(
        val id: String,
        val name: String,
        val src: String = "",
    ) : JavaSerializable {
        var video: Video? = null
    }
}
