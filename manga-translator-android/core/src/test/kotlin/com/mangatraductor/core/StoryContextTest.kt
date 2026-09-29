package com.mangatraductor.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StoryContextTest {

    @Test
    fun remembersPagesNamesAndSummaryAndSurvivesSaving() {
        val story = StoryContext()
        assertTrue(story.isEmpty)
        assertEquals("", story.describe())

        story.remember(listOf("おはよう", "待って、ハル！"), listOf("Good morning", "Wait, Haru!"))
        story.update("Aki runs after Haru.", mapOf("ハル" to "Haru"))
        story.update("", mapOf("ハル" to "Haru-chan")) // un resumen vacío no borra el anterior

        val text = story.describe()
        assertTrue("Story so far: Aki runs after Haru." in text)
        assertTrue("ハル = Haru-chan" in text)
        assertTrue("待って、ハル！ -> Wait, Haru!" in text)

        val copy = StoryContext.fromJson(story.toJson())
        assertEquals(story.describe(), copy.describe())
        assertEquals(1, copy.pages)
    }

    @Test
    fun namesCanBeCorrectedByHand() {
        val story = StoryContext()
        story.update("", mapOf("ハル" to "Haru", "アキ" to "Aki"))
        val typed = "ハル = Haruka\n• アキ → Akira\nlínea sin sentido\n斉藤: Saito"
        story.replaceGlossary(story.parseGlossary(typed))
        assertEquals(mapOf("ハル" to "Haruka", "アキ" to "Akira", "斉藤" to "Saito"), story.glossary)
    }

    @Test
    fun keepsOnlyTheLastLines() {
        val story = StoryContext()
        repeat(StoryContext.MAX_RECENT + 10) { i -> story.remember(listOf("線$i"), listOf("line $i")) }
        assertEquals(StoryContext.MAX_RECENT, story.recent.size)
        assertEquals("line ${StoryContext.MAX_RECENT + 9}", story.recent.last().second)
        // Versión corta (para Gemini Nano): sólo las últimas frases.
        assertEquals(2, story.describe(maxLines = 2).lines().count { it.startsWith("- 線") })
    }
}
