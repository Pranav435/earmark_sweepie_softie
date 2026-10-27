package com.sweepiesoftie.earmark.core

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickRepliesTest {
    @Test
    fun `a question with a time gets a yes, a no and a maybe`() {
        assertEquals(listOf("Works for me", "Can't, sorry", "Let me check"), QuickReplies.forMessage("free at 5?"))
        assertEquals(listOf("Works for me", "Can't, sorry", "Let me check"), QuickReplies.forMessage("kal milte hain? tomorrow"))
    }

    @Test
    fun `a question is a question mark or a question word at the start`() {
        val yesNo = listOf("Yes", "No", "Let me check")
        assertEquals(yesNo, QuickReplies.forMessage("kal milte hain?"))
        assertEquals(yesNo, QuickReplies.forMessage("are you coming"))
        assertEquals(yesNo, QuickReplies.forMessage("Did you see it"))
        assertEquals(yesNo, QuickReplies.forMessage("آتے ہو؟"))
    }

    @Test
    fun `a greeting gets one back`() {
        val hello = listOf("Hey!", "Hi! What's up?", "Give me a minute")
        listOf("hi", "Hiii", "hey!!", "Hello 👋", "hey Pranav", "good morning", "gm").forEach {
            assertEquals(it, hello, QuickReplies.forMessage(it))
        }
    }

    @Test
    fun `thanks gets a welcome`() {
        assertEquals(listOf("You're welcome!", "Anytime", "Of course"), QuickReplies.forMessage("thanks a lot"))
        assertEquals(listOf("You're welcome!", "Anytime", "Of course"), QuickReplies.forMessage("ty"))
    }

    @Test
    fun `anything else gets replies that suit any message, including bad news`() {
        val neutral = listOf("Got it", "Will reply soon", "Can I call you?")
        assertEquals(neutral, QuickReplies.forMessage("ok"))
        assertEquals(neutral, QuickReplies.forMessage("lol"))
        assertEquals(neutral, QuickReplies.forMessage("this year has been really hard"))
        // Words that only start with a greeting aren't one.
        assertEquals(neutral, QuickReplies.forMessage("history notes attached"))
    }
}
