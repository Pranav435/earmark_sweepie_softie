package com.sweepiesoftie.earmark.core

import com.sweepiesoftie.earmark.core.Fixtures.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrgencyTest {
    private val now = at(16, 12)

    private fun read(vararg texts: String, inGroup: Boolean = false): Reading =
        Urgency.read(texts.mapIndexed { i, text -> Message("Rahul", text, now.minusSeconds(10L * (texts.size - 1 - i))) }, inGroup)

    @Test
    fun `a message to you is half due, and asking, a time and writing again each add a quarter`() {
        assertEquals(0.5, read("hey").due, 1e-9)
        assertEquals(0.75, read("free?").due, 1e-9)
        assertEquals(0.75, read("can you send the deck").due, 1e-9)
        assertEquals(0.75, read("back home tonight").due, 1e-9)
        assertEquals(1.0, read("free at 5?").due, 1e-9)
        assertEquals(0.75, read("hey", "you there", "hello").due, 1e-9)
        assertEquals(listOf("keeps writing"), read("hey", "you there", "hello").reasons)
    }

    @Test
    fun `urgent words make it fully due in English, Hinglish and Hindi`() {
        listOf(
            "call me asap", "it's urgent", "EMERGENCY", "dad's in the hospital", "pick up your phone",
            "where are you", "are you ok", "jaldi aa", "kahan ho", "phone utha", "तुरंत आओ", "ज़रूरी है",
        ).forEach { text ->
            val reading = read(text)
            assertTrue(text, reading.urgent)
            assertEquals(text, 1.0, reading.due, 1e-9)
        }
        listOf("hospitality was great", "call me later", "not important", "i'll help with the move").forEach { assertFalse(it, read(it).urgent) }
    }

    @Test
    fun `an urgent message stays urgent when more messages follow it`() {
        val reading = read("call me, urgent", "hello??")
        assertTrue(reading.urgent)
        assertEquals("urgent", reading.reasons.first())
    }

    @Test
    fun `acknowledgements, reactions and emoji owe nothing, a greeting or a question still does`() {
        listOf("ok", "Okay!", "haha nice", "lol", "thank you", "👍", "😂😂", "ok 👍", "got it", "theek hai", "Reacted 👍 to: “see you”", "Liked a message", "Sticker")
            .forEach { assertTrue(it, Urgency.acknowledges(it)) }
        listOf("hey", "hi", "ok?", "free at 5?", "📷 Photo", "ok but where are you", "haha can you call").forEach { assertFalse(it, Urgency.acknowledges(it)) }
    }

    @Test
    fun `urgency said in other ways and other languages`() {
        listOf(
            "CALL ME BACK", "need u here rn, car broke down", "come home now pls", "everyone's waiting for u at the gate",
            "dad collapsed at work", "kahan hai tu", "sab wait kar rahe hain", "phone kyu nahi utha raha",
            "अभी के अभी नीचे आओ", "लवकर ये", "જલ્દી આવ", "உடனே வா", "তাড়াতাড়ি আয়", "عاجل اتصل بي", "es urgente, llámame",
            "komm sofort", "c'est urgent", "срочно перезвони",
        ).forEach { assertTrue(it, Urgency.isUrgent(it)) }
    }

    @Test
    fun `urgent words don't count when they're denied, over, joking or quoted`() {
        listOf(
            "not urgent but can u send the pdf", "no rush, whenever you can", "koi jaldi nahi, aaram se bhejna", "urgent nahi hai",
            "कोई जल्दी नहीं है", "घाई नाही", "sin prisa", "that was urgent lol", "the emergency yesterday is all sorted now",
            "kal emergency thi, ab sab theek hai", "urgent!!! 😂 look at this dog", "Emphasized “call me asap”",
            "where are you going for diwali", "she was like \"where are u\" lmao",
        ).forEach { assertFalse(it, Urgency.isUrgent(it)) }
    }

    @Test
    fun `a question or a request without a question mark still asks`() {
        listOf(
            "what time is dinner", "did u eat", "r u at home", "ok wait which platform", "where r u", "kya kar raha hai",
            "notes ho gaye kya", "kab aayega", "bhai charger kidhar rakha", "ghar aate waqt bread le aana", "jaldi neeche aa",
            "phone kar", "kal yaad se laptop lana", "don't forget the tickets", "bring ur id tmrw", "pay me back for lunch",
            "शाम को कॉल करना", "दवाई ले आना", "किताब भेज दो", "कब आओगे", "लवकर फोन कर", "ફોન કર", "plsss reply",
            "sun na, ek baat puchni thi", "your turn to pick",
        ).forEach { assertTrue(it, Urgency.asks(it)) }
    }

    @Test
    fun `quoted questions, reactions, offers and withdrawn questions don't ask`() {
        listOf(
            "he literally texted \"are you up?\" at 3am lol", "Liked “are you coming?”", "Loved \"what time?\"",
            "lmk if you need anything", "did u see the news? anyway nvm, tell u later", "what a day", "khana bahut accha tha",
        ).forEach { assertFalse(it, Urgency.asks(it)) }
    }

    @Test
    fun `acknowledgements in many words and languages`() {
        listOf(
            "theek hai bhai 👍", "ठीक है बेटा", "haha sahi hai", "thank you so much yaar 🙏", "Danke dir!", "jajaja", "kkkk",
            "nvm mil gaye", "okkk", "k thx bye", "ok see u then", "accha theek hai, milte hain", "ho gaya 👍", "धन्यवाद", "अच्छा",
            "સારું", "சரி", "ঠিক আছে", "شكرا حبيبي", "merci beaucoup", "obrigado!", "Liked an image", "Loved \"pahunch gaya\"",
            "Reacted 😂 to \"where r u?\"", "ठीक आहे", "badhiya", "chalo done", "hahaha pagal",
        ).forEach { assertTrue(it, Urgency.acknowledges(it)) }
        listOf("ok come now", "thanks, can you send it too", "ho gaya?", "ठीक है, कब आओगे", "danke, wann kommst du").forEach {
            assertFalse(it, Urgency.acknowledges(it))
        }
    }

    @Test
    fun `a burst only counts when it is recent and in a direct chat`() {
        val spread = listOf(Message("Rahul", "hey", now.minusSeconds(3600)), Message("Rahul", "you there", now.minusSeconds(60)), Message("Rahul", "hello", now))
        assertEquals(0.5, Urgency.read(spread, inGroup = false).due, 1e-9)
        assertEquals(0.5, read("hey", "you there", "hello", inGroup = true).due, 1e-9)
    }

    @Test
    fun `your name or an @mention counts, the word you does not`() {
        val names = listOf("Pranav Savla", "Pranav")
        assertTrue(Urgency.mentions("Pranav can you get the cake?", names, selfName = "You"))
        assertTrue(Urgency.mentions("ask @pranav", names, selfName = null))
        assertTrue(Urgency.mentions("thanks @You", emptyList(), selfName = "You"))
        assertFalse(Urgency.mentions("can you get the cake?", emptyList(), selfName = "You"))
        assertFalse(Urgency.mentions("pranavi's party", names, selfName = null))
        assertFalse("a one-letter name never matches", Urgency.mentions("a b c", listOf("A"), selfName = null))
    }
}
