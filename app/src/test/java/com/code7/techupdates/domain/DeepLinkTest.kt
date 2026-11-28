package com.code7.techupdates.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkTest {
    @Test
    fun storyId_parsesWebReaderLink() {
        assertEquals("42", DeepLink.storyId("https://agent7jacob-7177.github.io/code7/#story/42"))
    }

    @Test
    fun storyId_acceptsPathVariantsAndCase() {
        assertEquals("1", DeepLink.storyId("https://agent7jacob-7177.github.io/code7#story/1"))
        assertEquals("2", DeepLink.storyId("https://agent7jacob-7177.github.io/code7/index.html#story/2"))
        assertEquals("3", DeepLink.storyId("HTTPS://Agent7Jacob-7177.GitHub.io/code7/#story/3"))
        assertEquals("4", DeepLink.storyId("  https://agent7jacob-7177.github.io/code7/?utm=x#story/4 "))
    }

    @Test
    fun storyId_rejectsOtherFragments() {
        assertNull(DeepLink.storyId("https://agent7jacob-7177.github.io/code7/"))
        assertNull(DeepLink.storyId("https://agent7jacob-7177.github.io/code7/#"))
        assertNull(DeepLink.storyId("https://agent7jacob-7177.github.io/code7/#story/"))
        assertNull(DeepLink.storyId("https://agent7jacob-7177.github.io/code7/#story/abc"))
        assertNull(DeepLink.storyId("https://agent7jacob-7177.github.io/code7/#story/12/extra"))
        assertNull(DeepLink.storyId("https://agent7jacob-7177.github.io/code7/#comments/12"))
    }

    @Test
    fun storyId_rejectsOtherSitesAndSchemes() {
        assertNull(DeepLink.storyId("http://agent7jacob-7177.github.io/code7/#story/1"))
        assertNull(DeepLink.storyId("https://example.com/code7/#story/1"))
        assertNull(DeepLink.storyId("https://agent7jacob-7177.github.io/other/#story/1"))
        assertNull(DeepLink.storyId("https://agent7jacob-7177.github.io/code7/privacy.html#story/1"))
    }

    @Test
    fun storyId_handlesNullBlankAndMalformed() {
        assertNull(DeepLink.storyId(null))
        assertNull(DeepLink.storyId(""))
        assertNull(DeepLink.storyId("not a uri at all"))
        assertNull(DeepLink.storyId("https://agent7jacob-7177.github.io/code7/#story/1#2"))
    }

    @Test
    fun storyUrl_roundTrips() {
        assertEquals("https://agent7jacob-7177.github.io/code7/#story/99", DeepLink.storyUrl("99"))
        assertEquals("99", DeepLink.storyId(DeepLink.storyUrl("99")))
    }
}
