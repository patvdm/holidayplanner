package com.pat.app.holidayplanner.data.remote

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutedImageStoreTest {

    private class FakeStore(val prefix: String, override val isConfigured: Boolean = true) : ImageStore {
        val uploaded = mutableListOf<String>()
        val removed = mutableListOf<String>()

        override suspend fun upload(bytes: ByteArray, mimeType: String, name: String): Result<StoredImage> {
            uploaded += name
            return Result.success(StoredImage("$prefix$name", name))
        }

        override suspend fun remove(url: String): Result<Unit> {
            removed += url
            return Result.success(Unit)
        }

        override fun owns(url: String): Boolean = url.startsWith(prefix)
    }

    @Test
    fun `new photos go to the primary store when it is set up`() = runTest {
        val cloud = FakeStore("https://worker/photos/")
        val drive = FakeStore("https://lh3.googleusercontent.com/d/")

        val stored = RoutedImageStore(cloud, drive).upload(byteArrayOf(1), "image/jpeg", "a.jpg").getOrThrow()

        assertEquals("https://worker/photos/a.jpg", stored.url)
        assertEquals(emptyList<String>(), drive.uploaded)
    }

    @Test
    fun `without the primary store new photos fall back`() = runTest {
        val cloud = FakeStore("https://worker/photos/", isConfigured = false)
        val drive = FakeStore("https://lh3.googleusercontent.com/d/")

        RoutedImageStore(cloud, drive).upload(byteArrayOf(1), "image/jpeg", "a.jpg")

        assertEquals(listOf("a.jpg"), drive.uploaded)
        assertEquals(emptyList<String>(), cloud.uploaded)
    }

    @Test
    fun `a photo is removed from whichever store holds it`() = runTest {
        val cloud = FakeStore("https://worker/photos/")
        val drive = FakeStore("https://lh3.googleusercontent.com/d/")
        val routed = RoutedImageStore(cloud, drive)

        routed.remove("https://lh3.googleusercontent.com/d/old")
        routed.remove("https://worker/photos/new.jpg")
        routed.remove("https://example.com/not-ours.jpg")

        assertEquals(listOf("https://lh3.googleusercontent.com/d/old"), drive.removed)
        assertEquals(listOf("https://worker/photos/new.jpg"), cloud.removed)
    }
}
