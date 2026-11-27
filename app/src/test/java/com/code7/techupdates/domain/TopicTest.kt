package com.code7.techupdates.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TopicTest {
    @Test
    fun topics_matchJsOrderAndFields() {
        assertEquals(
            listOf("", "AI", "ML", "Security", "Programming", "Hardware", "Space", "Startups"),
            Topic.entries.map { it.id },
        )
        assertEquals(
            listOf(null, "AI", "machine learning", "security", "programming", "chip", "space", "startup"),
            Topic.entries.map { it.q },
        )
        assertEquals("Machine Learning", Topic.ML.label)
        assertEquals("ML", Topic.ML.tag)
        assertEquals("Security", Topic.SECURITY.tag)
        assertNull(Topic.ALL.match)
    }

    @Test
    fun byId_findsTopicOrNull() {
        assertEquals(Topic.ALL, Topic.byId(""))
        assertEquals(Topic.SPACE, Topic.byId("Space"))
        assertNull(Topic.byId("space"))
        assertNull(Topic.byId(null))
    }

    @Test
    fun topicOf_eachTopic() {
        assertEquals("AI", topicOf("Anthropic releases new Claude model"))
        assertEquals("AI", topicOf("Running LLMs locally"))
        assertEquals("ML", topicOf("Fine-tuning PyTorch models on a budget"))
        assertEquals("ML", topicOf("Neural networks from scratch"))
        assertEquals("Security", topicOf("New CVE in OpenSSH"))
        assertEquals("Programming", topicOf("Why I switched from Python to Rust"))
        assertEquals("Programming", topicOf("Writing Go in 2026"))
        assertEquals("Hardware", topicOf("Nvidia's next GPU"))
        assertEquals("Space", topicOf("SpaceX launches another rocket"))
        assertEquals("Startups", topicOf("Acme raises Series B"))
    }

    @Test
    fun topicOf_noMatch_returnsEmpty() {
        assertEquals("", topicOf("A history of the bicycle"))
        assertEquals("", topicOf(""))
    }

    @Test
    fun topicOf_firstMatchWins() {
        // Matches AI and Security; AI comes first in TOPICS.
        assertEquals("AI", topicOf("OpenAI security breach"))
    }

    @Test
    fun topicOf_usesJsWordBoundaries() {
        // "vulnerab\b" only matches the bare stem, exactly like the web app.
        assertEquals("", topicOf("Vulnerable systems"))
        assertEquals("Security", topicOf("vulnerab"))
        assertEquals("", topicOf("Said the Aide"))
        assertEquals("", topicOf("Paint drying"))
        // "go " needs a word character after the space.
        assertEquals("", topicOf("Let it go "))
        assertEquals("Programming", topicOf("go generics"))
        // Non-ASCII letters are not word characters in JS \b.
        assertEquals("AI", topicOf("éAIé"))
        assertEquals("AI", topicOf("ai"))
        assertEquals("", topicOf("ai_x"))
    }
}
