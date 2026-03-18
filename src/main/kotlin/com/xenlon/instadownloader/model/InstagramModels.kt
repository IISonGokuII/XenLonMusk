package com.xenlon.instadownloader.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Represents the type of media content from Instagram.
 */
enum class MediaType {
    IMAGE,
    VIDEO
}

/**
 * Quality preference for downloads.
 */
enum class DownloadQuality(val label: String) {
    HD("HD - Beste Qualität"),
    SD("SD - Kleinere Dateien")
}

enum class RequestHealthLevel {
    IDLE,
    ACTIVE,
    COOLDOWN,
    WARNING
}

data class RequestHealthState(
    val level: RequestHealthLevel = RequestHealthLevel.IDLE,
    val message: String = "",
    val recentRequestCount: Int = 0,
    val cooldownUntilMillis: Long = 0L,
    val lastUpdatedMillis: Long = System.currentTimeMillis()
)

/**
 * Represents a downloadable Instagram media item.
 */
data class MediaItem(
    val id: String,
    val url: String,
    val thumbnailUrl: String = url,
    val type: MediaType = MediaType.IMAGE,
    val timestamp: Long = System.currentTimeMillis(),
    val caption: String = ""
)

/**
 * Represents an Instagram user profile.
 */
data class UserProfile(
    val username: String,
    val fullName: String = "",
    val biography: String = "",
    val profilePicUrl: String = "",
    val profilePicUrlHD: String = "",
    val isPrivate: Boolean = false,
    val followerCount: Long = 0,
    val followingCount: Long = 0,
    val postCount: Long = 0,
    val userId: String = ""
)

/**
 * Represents a story item.
 */
data class StoryItem(
    val id: String,
    val mediaUrl: String,
    val thumbnailUrl: String = "",
    val type: MediaType = MediaType.IMAGE,
    val timestamp: Long = 0,
    val expiringAt: Long = 0
)

/**
 * Represents a highlight reel.
 */
data class HighlightReel(
    val id: String,
    val title: String,
    val coverImageUrl: String = "",
    val items: List<StoryItem> = emptyList()
)

/**
 * Represents a feed post (image/video/carousel posted to the profile grid).
 */
data class FeedPost(
    val id: String,
    val shortcode: String = "",
    val mediaUrls: List<String> = emptyList(),
    val thumbnailUrl: String = "",
    val type: MediaType = MediaType.IMAGE,
    val isCarousel: Boolean = false,
    val caption: String = "",
    val timestamp: Long = 0,
    val likeCount: Long = 0,
    val commentCount: Long = 0
)

/**
 * Represents a downloaded file in the in-app gallery.
 */
data class GalleryItem(
    val file: File,
    val name: String,
    val isVideo: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val sourceTimestamp: Long = 0L,
    val username: String = "",
    val category: String = "", // stories, highlights, posts, archive, profile
    val caption: String = "",
    val shortcode: String = "",
    val sourceId: String = "",
    val highlightTitle: String = "",
) 

@Serializable
data class DownloadedMediaMetadata(
    val username: String = "",
    val category: String = "",
    val sourceTimestamp: Long = 0L,
    val caption: String = "",
    val shortcode: String = "",
    val sourceId: String = "",
    val highlightTitle: String = "",
    val mediaIndex: Int = 0,
    val isVideo: Boolean = false,
)

enum class DownloadQueueStatus {
    WAITING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

data class DownloadQueueItem(
    val workId: String,
    val label: String,
    val outputPath: String,
    val sourceUrl: String,
    val status: DownloadQueueStatus,
    val metadataJson: String = "",
)

/**
 * Represents a search history entry for quick profile access.
 */
data class SearchHistoryEntry(
    val username: String,
    val fullName: String = "",
    val profilePicUrl: String = "",
    val isFavorite: Boolean = false,
    val lastSearched: Long = System.currentTimeMillis()
)

/**
 * Data for a pending 2FA verification.
 */
data class TwoFactorInfo(
    val identifier: String,
    val username: String,
    val obfuscatedPhone: String = "",
    val totpEnabled: Boolean = false,
    val smsEnabled: Boolean = false
)

/**
 * Result wrapper for API operations.
 */
sealed class DownloadResult<out T> {
    data class Success<T>(val data: T) : DownloadResult<T>()
    data class Error(val message: String, val code: Int = -1) : DownloadResult<Nothing>()
    data object Loading : DownloadResult<Nothing>()
    data class TwoFactorRequired(val twoFactorInfo: TwoFactorInfo) : DownloadResult<Nothing>()
}

/**
 * A profile on the watchlist for automatic new-content monitoring.
 */
@Serializable
data class WatchlistEntry(
    val username: String,
    val userId: String = "",
    val profilePicUrl: String = "",
    val fullName: String = "",
    val enabled: Boolean = true,
    val autoDownload: Boolean = false,
    val checkStories: Boolean = true,
    val checkPosts: Boolean = true,
    val checkReels: Boolean = false,
    val lastCheckedTimestamp: Long = 0L,
    val lastKnownPostCount: Int = 0,
    val lastKnownStoryCount: Int = 0,
    val addedTimestamp: Long = System.currentTimeMillis(),
)

/**
 * Result of a watchlist check for a single profile.
 */
data class WatchlistCheckResult(
    val username: String,
    val newPostCount: Int = 0,
    val newStoryCount: Int = 0,
    val newReelCount: Int = 0,
)

/**
 * Download statistics snapshot.
 */
@Serializable
data class DownloadStats(
    val totalDownloads: Long = 0,
    val totalSizeBytes: Long = 0,
    val imageCount: Long = 0,
    val videoCount: Long = 0,
    val perUserStats: Map<String, UserDownloadStats> = emptyMap(),
    val dailyDownloads: Map<String, Int> = emptyMap(),
)

@Serializable
data class UserDownloadStats(
    val username: String,
    val downloadCount: Int = 0,
    val totalSizeBytes: Long = 0,
    val categories: Map<String, Int> = emptyMap(),
)

/**
 * A single download history entry for the timeline.
 */
@Serializable
data class DownloadHistoryEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val username: String = "",
    val category: String = "",
    val itemCount: Int = 1,
    val label: String = "",
)

/**
 * A user-created album/collection for organizing downloads.
 */
@Serializable
data class CustomAlbum(
    val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val filePaths: List<String> = emptyList(),
)

// --- JSON response models for Instagram's public API ---

@Serializable
data class GraphqlResponse(
    val graphql: GraphqlData? = null,
    val data: DataWrapper? = null
)

@Serializable
data class DataWrapper(
    val user: GraphqlUser? = null
)

@Serializable
data class GraphqlData(
    val user: GraphqlUser? = null
)

@Serializable
data class GraphqlUser(
    val id: String? = null,
    val username: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    val biography: String? = null,
    @SerialName("profile_pic_url") val profilePicUrl: String? = null,
    @SerialName("profile_pic_url_hd") val profilePicUrlHd: String? = null,
    @SerialName("is_private") val isPrivate: Boolean? = null,
    @SerialName("edge_followed_by") val edgeFollowedBy: CountNode? = null,
    @SerialName("edge_follow") val edgeFollow: CountNode? = null,
    @SerialName("edge_owner_to_timeline_media") val edgeMedia: CountNode? = null,
    @SerialName("edge_highlight_reels") val edgeHighlightReels: HighlightReelsEdge? = null
)

@Serializable
data class CountNode(
    val count: Long? = null
)

@Serializable
data class HighlightReelsEdge(
    val edges: List<HighlightEdge>? = null
)

@Serializable
data class HighlightEdge(
    val node: HighlightNode? = null
)

@Serializable
data class HighlightNode(
    val id: String? = null,
    val title: String? = null,
    @SerialName("cover_media_cropped_thumbnail") val coverMedia: CoverMedia? = null
)

@Serializable
data class CoverMedia(
    val url: String? = null
)

@Serializable
data class ReelsMediaResponse(
    val reels_media: List<ReelsMedia>? = null,
    val reels: Map<String, ReelsMedia>? = null
)

@Serializable
data class ReelsMedia(
    val id: String? = null,
    val items: List<ReelsItem>? = null,
    val user: ReelsUser? = null
)

@Serializable
data class ReelsUser(
    val username: String? = null,
    @SerialName("profile_pic_url") val profilePicUrl: String? = null
)

@Serializable
data class ReelsItem(
    val id: String? = null,
    @SerialName("media_type") val mediaType: Int? = null,
    @SerialName("image_versions2") val imageVersions2: ImageVersions? = null,
    @SerialName("video_versions") val videoVersions: List<VideoVersion>? = null,
    @SerialName("taken_at") val takenAt: Long? = null,
    @SerialName("expiring_at") val expiringAt: Long? = null
)

@Serializable
data class ImageVersions(
    val candidates: List<ImageCandidate>? = null
)

@Serializable
data class ImageCandidate(
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null
)

@Serializable
data class VideoVersion(
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val type: Int? = null
)
