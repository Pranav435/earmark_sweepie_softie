package com.sweepiesoftie.earmark.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GistTest {

    @Test
    fun `a short message is quoted whole`() {
        assertEquals("free at 5?", Gist.of("free at 5?"))
        assertEquals("are you free at 5? need to talk about the trip", Gist.of("are you free at 5? need to talk about the trip"))
        assertEquals("kahan hai tu? sab wait kar rahe hain", Gist.of("kahan hai tu?  sab wait kar rahe hain"))
    }

    @Test
    fun `a long message is cut to the part that asks, marked as picked from the middle`() {
        assertEquals(
            "…can you pick up the cake by 6?",
            Gist.of("hey so I was thinking about the weekend plan and everything. can you pick up the cake by 6?"),
        )
        assertEquals(
            "…tu aa raha hai kya shaam ko?",
            Gist.of("bhai kal ka plan cancel ho gaya hai sab log busy hain. tu aa raha hai kya shaam ko?"),
        )
        assertEquals("…तुम कब आओगे?", Gist.of("मैं घर पहुँच गया हूँ और खाना भी खा लिया है और अब आराम कर रहा हूँ। तुम कब आओगे?"))
    }

    @Test
    fun `a real question wins over a sentence that only asks by its words`() {
        assertEquals(
            "are you free at 5?",
            Gist.of("are you free at 5? need to talk about the trip tomorrow before we book the tickets"),
        )
    }

    @Test
    fun `something urgent wins over a question`() {
        assertEquals(
            "…call me asap, dad is in the hospital.",
            Gist.of("I know you're busy with work today and all that. call me asap, dad is in the hospital. can you bring the insurance papers?"),
        )
    }

    @Test
    fun `with nothing asked, the start is trimmed at a word to about twelve words`() {
        assertEquals(
            "had such a great time at the concert last night, the band…",
            Gist.of("had such a great time at the concert last night, the band was amazing and the crowd was wild"),
        )
    }

    @Test
    fun `long words are cut by length too, and links are left out`() {
        val gist = Gist.of("Supercalifragilisticexpialidocious extraordinarily incomprehensibilities notwithstanding everything happened")
        assertTrue(gist, gist.endsWith("…"))
        assertTrue(gist, gist.codePointCount(0, gist.length) <= Gist.MAX_CHARS + 1)
        assertEquals("what do you think of this?", Gist.of("what do you think of this? https://youtu.be/dQw4w9WgXcQ"))
    }

    @Test
    fun `a message that is only a link is still quoted`() {
        assertEquals("https://youtu.be/dQw4w9WgXcQ", Gist.of("https://youtu.be/dQw4w9WgXcQ"))
    }
}
