package com.sweepiesoftie.earmark.core

/**
 * One-tap replies for the reply strip when Smart Reply can't answer (ADR 0026). Smart Reply only answers in English,
 * and calls short messages such as "hi" or "ok" not English. These come from the message's words alone: what kind of
 * question or request it is, and whether it's written in Hindi or Hinglish, so "kahan hai tu??" gets "Raste mein hoon"
 * rather than "Yes / No" (ADR 0031). The Hindi and Hinglish replies avoid verbs that say whether you're a man or a woman.
 * Anything that isn't plainly a question, a request, a greeting, thanks or a wish gets replies that suit any message,
 * bad news included.
 */
public object QuickReplies {
    private enum class Kind { WhereAreYou, HowAreYou, HowWasIt, Wishes, CallMe, WhenFree, Info, YesNo, Request, Greeting, Thanks, Other }

    private enum class Language { English, Hinglish, Hindi }

    private val replies: Map<Kind, Map<Language, List<String>>> = mapOf(
        Kind.WhereAreYou to set(listOf("On my way", "5 min away", "Will call you"), listOf("Raste mein hoon", "Bas 5 min", "Thodi der mein"), listOf("रास्ते में हूँ", "बस 5 मिनट", "थोड़ी देर में")),
        Kind.HowAreYou to set(listOf("All good, you?", "Doing well!", "Busy, talk later?"), listOf("Badhiya, tum batao?", "Sab theek!", "Baad mein baat karte hain"), listOf("बढ़िया, आप बताओ?", "सब ठीक है!", "बाद में बात करते हैं")),
        Kind.HowWasIt to set(listOf("Went well!", "Not great", "Tell you later"), listOf("Accha gaya!", "Theek-thaak", "Baad mein baat karte hain"), listOf("अच्छा गया!", "ठीक-ठाक", "बाद में बात करते हैं")),
        Kind.Wishes to set(listOf("Thank you!", "Same to you!", "Thanks a lot!"), listOf("Thank you!", "Aapko bhi!", "Thanks yaar!"), listOf("धन्यवाद!", "आपको भी!", "बहुत-बहुत धन्यवाद")),
        Kind.CallMe to set(listOf("Calling you now", "Can't talk, text me?", "Give me 5 min"), listOf("Calling now", "Abhi baat nahi ho sakti, text karo", "5 min do"), listOf("अभी कॉल करते हैं", "अभी बात नहीं हो सकती", "5 मिनट दो")),
        Kind.WhenFree to set(listOf("Works for me", "Can't, sorry", "Let me check"), listOf("Haan, chalega", "Nahi ho payega", "Ek min, check karna padega"), listOf("हाँ, चलेगा", "नहीं हो पाएगा", "एक मिनट, देखना पड़ेगा")),
        Kind.Info to set(listOf("Let me check", "Not sure", "Will tell you soon"), listOf("Ek min, check karna padega", "Pata nahi", "Thodi der mein bolte hain"), listOf("एक मिनट, देखना पड़ेगा", "पता नहीं", "थोड़ी देर में बताते हैं")),
        Kind.YesNo to set(listOf("Yes", "No", "Let me check"), listOf("Haan", "Nahi", "Ek min, check karna padega"), listOf("हाँ", "नहीं", "एक मिनट, देखना पड़ेगा")),
        Kind.Request to set(listOf("Okay, will do", "Done", "Can't right now"), listOf("Theek hai, ho jayega", "Done", "Abhi nahi ho payega"), listOf("ठीक है, हो जाएगा", "हो गया", "अभी नहीं हो पाएगा")),
        Kind.Greeting to set(listOf("Hey!", "Hi! What's up?", "Give me a minute"), listOf("Hey!", "Haan bolo", "Ek min"), listOf("नमस्ते!", "हाँ, बोलो", "एक मिनट")),
        Kind.Thanks to set(listOf("You're welcome!", "Anytime", "Of course"), listOf("Welcome!", "Koi baat nahi", "Anytime"), listOf("कोई बात नहीं!", "हमेशा", "Welcome!")),
        Kind.Other to set(listOf("Got it", "Will reply soon", "Can I call you?"), listOf("Accha", "Baad mein baat karte hain", "Call karoon?"), listOf("अच्छा", "बाद में बात करते हैं", "कॉल करूँ?")),
    )

    private fun set(english: List<String>, hinglish: List<String>, hindi: List<String>) =
        mapOf(Language.English to english, Language.Hinglish to hinglish, Language.Hindi to hindi)

    private val questionStart = Regex(
        """(?i)^\s*(what|when|where|who|why|how|which|are|is|can|could|will|would|should|do|does|did|have|has|wanna|r u)\b""",
    )
    private val greeting = Regex("""(?i)^\s*(hi+|hey+|hel+o+|yo+|sup|hola|namaste|namaskar|नमस्ते|good (morning|afternoon|evening|night)|gm|gn)\b""")
    private val thanks = Regex("""(?i)\b(thanks|thank you|thx|ty|tysm|shukriya|dhanyavad)\b|धन्यवाद|शुक्रिया""")
    private val wishes = Regex(
        """(?i)\b(happy (birthday|bday|diwali|holi|new year|anniversary|eid|navratri|dussehra|ganesh chaturthi|christmas|independence day)|hbd|eid mubarak|merry christmas|""" +
            """subh|shubh)\b|शुभकामना|मुबारक|बधाई|हार्दिक""",
    )
    private val whereAreYou = Regex(
        """(?i)\bwhere (are|r) (you|u)\b(?! (going|staying|from|planning|headed|heading|thinking))|\b(kaha|kahan|kahaan|kidhar)( pe)? (hai|ho|h|he|pahuch\w*|pahunch\w*)\b|""" +
            """(कहाँ|कहां|किधर) (हो|है)|कुठे आहेस""",
    )
    private val howAreYou = Regex("""(?i)\bhow (are|r) (you|u)\b|\bhow('?s| is) (it going|everything|life)\b|\bhow have you been\b|\b(kaise ho|kaisa hai|kaisi hai|kya haal)\b|कैसे हो|कैसी हो|कैसा है|क्या हाल""")
    private val howWasIt = Regex("""(?i)\bhow (was|did)\b|\b(kaisa|kaisi) (gaya|gayi|raha|rahi|tha|thi)\b|(कैसा|कैसी) (गया|गई|गयी|रहा|रही)""")
    private val callMe = Regex("""(?i)\b(call me|call (kar|karo|kr)|phone (kar|karo|utha\w*)|pick up)\b|(फ़?ोन|कॉल) (करो|कर|उठा\S*)""")
    /** "What", "kab", "kitna"; and "kya" or "क्या" mid-sentence, which asks what, where at either end it only makes a yes-or-no question. */
    private val infoWord = Regex(
        """(?i)\b(what|whats|what's|which|when|who|why|how much|how many|kab|kaun|kitna|kitne|kitni|konsa|kaunsa|kyun|kyu)\b|(?<=\S\s)(kya|क्या)(?=\s+\S)|कब|कौन|कितना|कितने|कौनसा|क्यों""",
    )
    private val yesNoEnd = Regex("""(?i)\b(kya|na)\s*[?!.]*\s*$|क्या\s*[?।]*\s*$""")

    private val hinglishWords = setOf(
        "hai", "kya", "kab", "kahan", "kaha", "kidhar", "kaisa", "kaise", "kaisi", "tu", "tum", "aap", "bhai", "yaar", "nahi", "nhi", "haan",
        "kar", "karo", "dena", "aaja", "raha", "rahi", "gaya", "gayi", "hoon", "hu", "mein", "ka", "ki", "ke", "ko", "se", "bhi", "toh", "kuch",
        "abhi", "kal", "aaj", "accha", "acha", "theek", "chal", "sab", "bata", "bhej", "ghar", "tera", "mera", "apna", "bol", "aa",
        "ho", "gaye", "gye", "hain", "tha", "thi", "hoga", "liya", "diya", "wala", "wali", "kitna", "kitni", "kaun", "bhaiya", "kyu", "kyun",
    )
    private val wordsIn = Regex("""[\p{L}']+""")

    public fun forMessage(text: String): List<String> = replies.getValue(kind(text)).getValue(language(text))

    private fun kind(text: String): Kind {
        val question = isQuestion(text)
        return when {
            whereAreYou.containsMatchIn(text) -> Kind.WhereAreYou
            howAreYou.containsMatchIn(text) -> Kind.HowAreYou
            question && howWasIt.containsMatchIn(text) -> Kind.HowWasIt
            wishes.containsMatchIn(text) -> Kind.Wishes
            callMe.containsMatchIn(text) && (Urgency.isUrgent(text) || !question) -> Kind.CallMe
            question && Classifier.hasTimeWord(text) -> Kind.WhenFree
            question && infoWord.containsMatchIn(text) && !yesNoEnd.containsMatchIn(text) -> Kind.Info
            question -> Kind.YesNo
            greeting.containsMatchIn(text) -> Kind.Greeting
            thanks.containsMatchIn(text) -> Kind.Thanks
            Urgency.asks(text) -> Kind.Request
            else -> Kind.Other
        }
    }

    private fun isQuestion(text: String): Boolean =
        Classifier.hasQuestionMark(text) || questionStart.containsMatchIn(text) || yesNoEnd.containsMatchIn(text) ||
            (infoWord.containsMatchIn(text) && Urgency.asks(text) && !Urgency.acknowledges(text))

    /** Devanagari is Hindi; Latin letters with two or more everyday Hindi words are Hinglish. */
    private fun language(text: String): Language = when {
        text.any { it in 'ऀ'..'ॿ' } -> Language.Hindi
        wordsIn.findAll(text.lowercase()).map { it.value }.filter { it in hinglishWords }.distinct().count() >= 2 -> Language.Hinglish
        else -> Language.English
    }
}
