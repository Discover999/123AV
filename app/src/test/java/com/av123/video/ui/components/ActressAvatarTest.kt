package com.av123.video.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActressAvatarTest {

    @Test
    fun dataUriIsTreatedAsMissing() {
        assertFalse(
            hasRemoteAvatar("data:image/svg+xml,%3csvg%20xmlns='http://www.w3.org/2000/svg'%3e")
        )
    }

    @Test
    fun httpsAvatarIsUsable() {
        assertTrue(hasRemoteAvatar("https://cdn.example.com/a/1.jpg"))
    }

    @Test
    fun blankAvatarIsMissing() {
        assertFalse(hasRemoteAvatar(""))
        assertFalse(hasRemoteAvatar("   "))
    }
}
