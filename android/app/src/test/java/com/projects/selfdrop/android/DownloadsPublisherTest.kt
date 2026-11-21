package com.projects.selfdrop.android

import com.projects.selfdrop.android.data.DownloadsPublisher
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadsPublisherTest {

    @Test
    fun keepsTheNameWhenItIsFree() {
        assertEquals("photo.jpg", DownloadsPublisher.uniqueName("photo.jpg", emptySet()))
    }

    @Test
    fun addsASuffixBeforeTheExtension() {
        assertEquals(
            "photo (2).jpg",
            DownloadsPublisher.uniqueName("photo.jpg", setOf("photo.jpg", "photo (1).jpg")),
        )
    }

    @Test
    fun handlesNamesWithoutAnExtension() {
        assertEquals(
            "notes (1)",
            DownloadsPublisher.uniqueName("notes", setOf("notes")),
        )
    }
}
