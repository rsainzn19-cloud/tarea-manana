package com.mangatraductor.core

import kotlin.test.Test
import kotlin.test.assertEquals

class NumberedLinesTest {

    @Test
    fun formatsOneLinePerBubble() {
        assertEquals("0: おはよう\n1: 待って 待って", NumberedLines.format(listOf("おはよう", "待って\n待って")))
    }

    @Test
    fun parsesTypicalSmallModelReplies() {
        val reply = """
            Here are the translations:
            0: "Good morning!"
            1. We're gonna be late
            for school!
            [2]: Wait a sec!
            9: (id que no existe)
        """.trimIndent()
        assertEquals(
            listOf("Good morning!", "We're gonna be late for school!", "Wait a sec!", ""),
            NumberedLines.parse(reply, 4),
        )
    }
}
