package com.sweepiesoftie.earmark.core

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickRepliesTest {
    @Test
    fun `a question with a time gets a yes, a no and a maybe`() {
        assertEquals(listOf("Works for me", "Can't, sorry", "Let me check"), QuickReplies.forMessage("free at 5?"))
        assertEquals(listOf("Works for me", "Can't, sorry", "Let me check"), QuickReplies.forMessage("lunch tomorrow?"))
        assertEquals(listOf("Haan, chalega", "Nahi ho payega", "Ek min, check karna padega"), QuickReplies.forMessage("kal milte hain? tomorrow"))
    }

    @Test
    fun `a question is a question mark or a question word at the start`() {
        val yesNo = listOf("Yes", "No", "Let me check")
        assertEquals(yesNo, QuickReplies.forMessage("coming to the party?"))
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

    @Test
    fun `where, how and what questions get replies that answer them`() {
        assertEquals(listOf("On my way", "5 min away", "Will call you"), QuickReplies.forMessage("where are u?? everyone's here"))
        assertEquals(listOf("All good, you?", "Doing well!", "Busy, talk later?"), QuickReplies.forMessage("hey how are you"))
        assertEquals(listOf("Went well!", "Not great", "Tell you later"), QuickReplies.forMessage("how was the interview?"))
        assertEquals(listOf("Let me check", "Not sure", "Will tell you soon"), QuickReplies.forMessage("which bus goes to the station"))
        assertEquals(listOf("Calling you now", "Can't talk, text me?", "Give me 5 min"), QuickReplies.forMessage("call me asap"))
        assertEquals(listOf("Okay, will do", "Done", "Can't right now"), QuickReplies.forMessage("bring the charger tmrw"))
        assertEquals(listOf("Thank you!", "Same to you!", "Thanks a lot!"), QuickReplies.forMessage("Happy Diwali to you and family 🪔"))
    }

    @Test
    fun `Hinglish and Hindi messages get replies in kind, with no gendered verbs`() {
        assertEquals(listOf("Raste mein hoon", "Bas 5 min", "Thodi der mein"), QuickReplies.forMessage("kidhar hai tu? sab wait kar rahe"))
        assertEquals(listOf("Haan", "Nahi", "Ek min, check karna padega"), QuickReplies.forMessage("notes ho gaye kya"))
        assertEquals(listOf("Ek min, check karna padega", "Pata nahi", "Thodi der mein bolte hain"), QuickReplies.forMessage("bhai fees kitni hai"))
        assertEquals(listOf("Badhiya, tum batao?", "Sab theek!", "Baad mein baat karte hain"), QuickReplies.forMessage("kaisa hai bhai?"))
        assertEquals(listOf("अच्छा गया!", "ठीक-ठाक", "बाद में बात करते हैं"), QuickReplies.forMessage("पेपर कैसा गया?"))
        assertEquals(listOf("रास्ते में हूँ", "बस 5 मिनट", "थोड़ी देर में"), QuickReplies.forMessage("कहाँ हो? सब इंतज़ार कर रहे हैं"))
        assertEquals(listOf("धन्यवाद!", "आपको भी!", "बहुत-बहुत धन्यवाद"), QuickReplies.forMessage("दिवाली की हार्दिक शुभकामनाएँ"))
    }
}
