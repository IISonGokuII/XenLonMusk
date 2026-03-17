package com.xenlon.instadownloader.service

object DownloadFilePlanner {
    fun mediaExtension(url: String, treatAsVideo: Boolean): String {
        return when {
            treatAsVideo || url.contains(".mp4", ignoreCase = true) -> "mp4"
            url.contains(".webp", ignoreCase = true) -> "webp"
            url.contains(".png", ignoreCase = true) -> "png"
            else -> "jpg"
        }
    }

    fun buildPostFileName(
        prefix: String,
        timestamp: String,
        shortcode: String,
        mediaIndex: Int,
        isCarousel: Boolean,
        extension: String,
        totalMediaInPost: Int = 1,
    ): String {
        // Always add media index when there's more than one media item,
        // regardless of whether isCarousel flag is set.
        // This prevents multiple carousel images from overwriting each other
        // when media_type is not correctly parsed as 8 (carousel).
        val needsIndex = isCarousel || totalMediaInPost > 1 || mediaIndex > 0
        val indexSuffix = if (needsIndex) "_${mediaIndex + 1}" else ""
        return "${prefix}_${timestamp}_${shortcode}${indexSuffix}.$extension"
    }

    fun buildProfilePictureFileName(username: String, extension: String): String {
        return "${username}_profile_pic.$extension"
    }

    fun sanitizePathSegment(value: String): String {
        return value.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    }
}
