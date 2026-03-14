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
    ): String {
        val carouselSuffix = if (isCarousel) "_${mediaIndex + 1}" else ""
        return "${prefix}_${timestamp}_${shortcode}${carouselSuffix}.$extension"
    }

    fun buildProfilePictureFileName(username: String, extension: String): String {
        return "${username}_profile_pic.$extension"
    }

    fun sanitizePathSegment(value: String): String {
        return value.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    }
}
