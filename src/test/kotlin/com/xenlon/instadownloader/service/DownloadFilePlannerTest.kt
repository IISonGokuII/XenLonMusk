package com.xenlon.instadownloader.service

import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadFilePlannerTest {

    @Test
    fun `mediaExtension prefers explicit video flag and known image suffixes`() {
        assertEquals("mp4", DownloadFilePlanner.mediaExtension("https://cdn.example.com/file.jpg", true))
        assertEquals("webp", DownloadFilePlanner.mediaExtension("https://cdn.example.com/file.webp", false))
        assertEquals("png", DownloadFilePlanner.mediaExtension("https://cdn.example.com/file.png", false))
        assertEquals("jpg", DownloadFilePlanner.mediaExtension("https://cdn.example.com/file", false))
    }

    @Test
    fun `buildPostFileName adds carousel suffix only when needed`() {
        assertEquals(
            "user_post_2026-03-14_ABC123.jpg",
            DownloadFilePlanner.buildPostFileName(
                prefix = "user_post",
                timestamp = "2026-03-14",
                shortcode = "ABC123",
                mediaIndex = 0,
                isCarousel = false,
                extension = "jpg",
            ),
        )
        assertEquals(
            "user_post_2026-03-14_ABC123_2.jpg",
            DownloadFilePlanner.buildPostFileName(
                prefix = "user_post",
                timestamp = "2026-03-14",
                shortcode = "ABC123",
                mediaIndex = 1,
                isCarousel = true,
                extension = "jpg",
            ),
        )
    }

    @Test
    fun `sanitizePathSegment removes unsupported characters`() {
        assertEquals(
            "Best_Of_2026_",
            DownloadFilePlanner.sanitizePathSegment("Best Of 2026!"),
        )
    }
}
