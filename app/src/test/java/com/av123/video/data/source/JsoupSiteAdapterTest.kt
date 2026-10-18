package com.av123.video.data.source

import org.junit.Assert.assertEquals
import org.junit.Test

class JsoupSiteAdapterTest {

    @Test
    fun configVersion_combinesSiteIdAndConfigVersion() {
        val config = JsoupSiteConfig(id = "test-site", version = 7)
        val adapter = JsoupSiteAdapter(JsoupVideoDataSource(config), config)

        assertEquals("test-site@7", adapter.configVersion)
    }
}
