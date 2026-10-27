package com.sweepiesoftie.earmark.core

import java.text.Normalizer
import java.time.Duration

/**
 * What the word lists found in one message: their answers, and the names of the [fired] rules, which [MessageModel]
 * reads beside the words. [decide] lets the model overrule the lists when it is sure (ADR 0031).
 */
internal class Clues(val text: String, val fired: List<String>, val urgent: Boolean, val asks: Boolean, val acknowledges: Boolean) {
    private val probabilities: DoubleArray? by lazy { MessageModel.probabilities(this) }

    fun rule(head: MessageModel.Head): Boolean = when (head) {
        MessageModel.Head.Urgent -> urgent
        MessageModel.Head.Asks -> asks
        MessageModel.Head.Ack -> acknowledges
    }

    fun decide(head: MessageModel.Head): Boolean {
        val p = probabilities?.takeIf { MessageModel.enabled } ?: return rule(head)
        // The lists that say what a message isn't are exact, and a model of words can't tell what "not" applies to.
        if (fired.any { it in vetoes.getValue(head) }) return rule(head)
        return MessageModel.decide(head, rule(head), p[head.ordinal])
    }

    fun overruled(head: MessageModel.Head): Boolean = decide(head) != rule(head)

    companion object {
        val vetoes: Map<MessageModel.Head, Set<String>> = mapOf(
            MessageModel.Head.Urgent to setOf("reaction", "calm", "over"),
            MessageModel.Head.Asks to setOf("reaction", "offer"),
            // A message the lists read as asking is never only an acknowledgement.
            MessageModel.Head.Ack to setOf("reaction", "question mark", "request words", "question words", "question start", "instruction", "polite ending", "greeting"),
        )
    }
}

/** What the messages waiting for you ask of you. Only [reasons] reach DebugLog, never the words (ADR 0027). */
internal data class Reading(
    /** How soon a reply is owed, 0..1. */
    val due: Double,
    /** Why, for DebugLog: "urgent", "asks", "time", "keeps writing", and "model" when the message model overruled the lists. */
    val reasons: List<String>,
    val urgent: Boolean,
)

/**
 * Reads what a message asks of you from its words, on the phone (ADR 0027): word lists in the languages Earmark's
 * testers write in, then [MessageModel], which learned from labelled messages what the lists miss (ADR 0031).
 */
internal object Urgency {
    /** Three messages within this long of the latest: they keep writing. */
    private val burstWindow: Duration = Duration.ofMinutes(10)
    private const val BURST = 3

    private val urgentWords = words(
        """urgent(ly)?""", """emergency""", """asap""", """a\.s\.a\.p""", """immediately""", """right away""", """sos""",
        """(please|pls|plz|need|send) help""", """help!+""", """accident""", """hospital""", """ambulance""",
        """(pick(ing)?|answer(ing)?) up (the |your |ur )?phone""", """answer (the |your |ur )?phone""", """(pick|answer) up\W+(pick|answer) up""",
        """(not|aren't|isn't|why aren't (you|u)) (picking up|answering)""",
        """where (are|r) (you|u)(?! (going|staying|from|planning|headed|heading|thinking|travel\w*|working|studying|based|celebrating))""",
        """(are|r) (you|u) (ok|okay|alright|safe)""", """is everything (ok|okay|alright|fine)""", """call me( back)?\W+(right )?(now|rn|asap)""",
        """come\b[^.?!\n]{0,30}?\b(now|rn|right now|fast|quick(ly)?)""", """need (you|u|ur help|your help)\b[^.?!\n]{0,30}?\b(now|rn|right now|urgently)""",
        """hurry( up)?""", """(i'?m|im|i am) (locked out|stuck|stranded|outside|downstairs|at (the|ur|your) (door|gate))""",
        """(everyone|everybody|they)('s| is| are) waiting""", """waiting (for (you|u)|downstairs|outside)""",
        """(collapsed|fainted|unconscious|bleeding|heart attack|chest pain|can'?t breathe|stroke)""", """(fell|has fallen) (down|off|from|in the|on the)""",
        """lost my (wallet|phone|bag|passport|keys?)""", """(pls|please|plz) (reply|respond|answer)""", """reply (asap|fast|quick(ly)?|jaldi|now|rn)""",
        // Hinglish
        """jaldi+""", """jldi""", """turant""", """fau?ran""", """(zaroori|zaruri|jaruri|jaroori|jruri)""", """phone uth\w*""", """madad""",
        """(kaha|kahan|kahaan|kidhar)( pe)? (ho|hai|h|he|hain)( (tu|tum|aap|bhai|yaar|re|be))?(?=\s*[?!.]*\s*$)""",
        """(kaha|kahan|kahaan|kidhar)( pe)? (hai|h|ho) (tu|tum|aap)""", """(kaha|kahan|kahaan|kidhar) (pahuch|pahunch)\w*""",
        """abhi ke abhi""", """phone (kyu|kyun|kyon) (nahi|nhi|nai)""", """phone band aa""", """sab (wait|intezaar|intzaar)""",
        """tabiyat (bahut )?(kharab|kharaab)""", """chakkar""", """behosh""",
        // Hindi
        """तुरंत""", """जल्दी""", """ज़?रूरी""", """फ़?ोन उठा\S*""", """(कहाँ|कहां|किधर) (हो|है)""", """मदद""", """अभी के अभी""", """अस्पताल""",
        """एक्सीडेंट""", """बेहोश""", """सब इंतज़?ार""",
        // Marathi, Gujarati, Tamil, Bengali, Telugu, Kannada, Punjabi, Urdu
        """लवकर""", """तातडीने""", """महत्त?्वाचं""", """कुठे आहेस""", """अर्जंट""", """જલ્દી""", """તાત્કાલિક""", """ક્યાં છે""", """અરજન્ટ""",
        """அவசரம்""", """உடனே""", """எங்கே இருக்க\S*""", """জরুরি""", """তাড়াতাড়ি""", """এখনই""", """কোথায় (তুমি|আছ\S*)""",
        """అత్యవసరం""", """త్వరగా""", """ತುರ್ತು""", """ಬೇಗ""", """ਜਲਦੀ""", """ਜ਼?ਰੂਰੀ""", """جلدی""", """فوراً?""", """ضروری""",
        // Arabic, Spanish, French, German, Portuguese, Italian, Indonesian, Turkish, Russian
        """عاجل""", """بسرعة""", """ضروري""", """(وين|فين|أين|اين) (انت|أنت|انتي|إنت)""", """حالا""", """urgente""", """emergencia""",
        """ahora mismo""", """(dónde|donde) (estás|estas)""", """urgence""", """tout de suite""", """où es-tu""", """t'es où""", """au secours""",
        """dringend""", """sofort""", """notfall""", """wo bist du""", """emergência""", """socorro""", """cadê (você|vc)""", """subito""",
        """dove sei""", """darurat""", """segera""", """acil""", """hemen""", """neredesin""", """срочно""", """где ты""", """помоги\S*""",
    )

    /** "Not urgent", "no rush", "koi jaldi nahi": the urgent word is there to say it isn't. */
    private val calm = words(
        """(not|isn't|is not|nothing|no|non)[- ](urgent|an emergency|a big deal|important)""", """no (rush|hurry|pressure|stress|tension)""",
        """(take|taking) (your|ur) time""", """when(ever)? (you|u) (can|get (a |the )?(chance|time|sec|minute)|are free|r free|have time)""",
        """at (your|ur) (leisure|convenience)""",
        """(koi|koi bhi) (jaldi|jldi|urgency|tension) (nahi|nhi|nai|na)""", """(jaldi|jldi|urgent|zaroori|zaruri) (nahi|nhi|nai|na)""",
        """aa?raa?m se""", """jab (time|fursat|waqt) (mile|ho)""", """koi tension (nahi|nhi|nai)""",
        """(कोई )?जल्दी नहीं""", """(ज़?रूरी|अर्जेंट) नहीं""", """आराम से""", """जब (समय|टाइम|फ़ुर्सत|वक़्त) (मिले|हो)""",
        """घाई नाही""", """सावकाश""", """वेळ मिळाल्यावर""", """ઉતાવળ નથી""", """નિરાંતે""",
        """sin prisa""", """no (es|hay) (urgente|prisa)""", """cuando puedas""", """sem pressa""", """quando puder""", """não é urgente""",
        """pas (urgent|pressé)""", """rien de pressé""", """quand tu peux""", """nicht (dringend|eilig)""", """keine eile""", """nessuna fretta""", """con calma""",
    )

    /** It was urgent, and isn't any more. */
    private val over = words(
        """(was|were) (so |really |kinda |pretty |super )?(urgent|an emergency|important)""", """(urgent|emergency|zaroori|zaruri|jaruri) (tha|thi|the)""",
        """yesterday""", """earlier today""", """last (night|week)""", """(all )?(sorted|resolved|handled|fine|okay|ok|good|safe) now""", """no longer""",
        """(sab|ab sab|ab) (theek|thik|sorted|badhiya)""", """(ज़?रूरी|इमरजेंसी) (था|थी)""", """अब (सब )?ठीक""",
    )

    /** Laughter and celebration: "help!!! 😂", "it's a girl!! 🎉". */
    private val joking = Regex("""[😂🤣😆😹🎉🥳🎊]|(?<![\p{L}])(lol|lmao|haha\w*|hehe\w*|jk|just kidding|jaja\w*|mdr|kkkk\w*)(?![\p{L}])""", RegexOption.IGNORE_CASE)

    private val requestWords = words(
        """(can|could|would|will) (you|u)""", """(please|pls|plz|plss)""", """let me know""", """lmk""", """tell me""", """send me""",
        """(call|ring|text) me""", """call back""", """need (you|u) to""", """need (ur|your) help""", """pay me( back)?""", """your turn""", """ur turn""",
        """(don't|dont|do not) forget""", """remember to""", """make sure""",
        // Hinglish
        """bata(o|na|do|dena)?""", """bhej(o|na|do|dena|de)?""", """(kar|kr) (do|dena|de)""", """(kardo|krdo)""", """(call|phone|reply|msg|message) (kar|karo|kr|kro|karna)""",
        """(aaja|aa ja|aajao|aa jao|aao|aa na)""", """(neeche|niche|ghar|idhar|bahar|jaldi) aa""", """(le|leke|lekar) (aa|aana|ana|aao)""", """(bhar|bhej|bata|rakh|la|dila|de|nikal|dikha|likh|sun|bol|chhod|laga|daal|dal|bana|dekh|le|pakad|sambhal|mangwa|bhijwa|pahuncha|samjha|kar) (de|do|dena|dijiye|lena|le|lo|lijiye)""",
        """utha( na| le| lo)?""", """ek kaam kar""", """yaad se""", """(bhool|bhul) mat""", """mat (bhoolna|bhulna|nikalna|jana)""", """ruk ja""",
        """(puchni|poochni|puchna|poochna|batani|batana) (thi|tha|hai)""",
        // Hindi and Marathi
        """(बताओ|बताना|भेजो|भेज दो|कर दो|कॉल करो|फोन करो)""", """(करो|भेजो|बोलो|सुनो|चलो|निकलो|पहुँचो|पहुंचो|उठाओ|आओ|आ जाओ|लाओ|बताइए|कीजिए|दीजिए|भेजिए|आइए)""",
        """(दे दो|ले लो|कर दो|आ जाना|ले आना|भेज देना|बता देना|कर देना|ले लेना|दे देना)""", """(फ़?ोन|कॉल) कर\S*""", """पाठव\S*""", """सांग\S*""",
        // Gujarati, Tamil, Bengali, Arabic, Spanish, Portuguese, French, German, Italian, Indonesian, Turkish, Russian
        """(ફોન|કૉલ|કોલ) કર\S*""", """મોકલ\S*""", """આવ(જે|જો)""", """લાવ(જે|જો)""", """கால் பண்ணு""", """அனுப்பு""", """சொல்லு""",
        """ফোন কর\S*""", """পাঠাও""", """এসো""", """বলো""", """اتصل""", """كلمني""", """ارسل""", """تعال""",
        """llámame""", """mándame""", """envíame""", """avísame""", """dime""", """me liga""", """me manda""", """me avisa""", """me diz""",
        """appelle-moi""", """rappelle-moi""", """envoie-moi""", """dis-moi""", """ruf (mich )?an""", """schick mir""", """sag mir""", """melde dich""",
        """chiamami""", """mandami""", """dimmi""", """kabari""", """telepon aku""", """beni ara""", """haber ver""", """позвони""", """напиши""", """пришли""",
    )

    /** An offer rather than a request: "lmk if you need anything". */
    private val offer = words("""(lmk|let me know|tell me|call me|text me|ping me) if (you|u) (need|want)""")

    /**
     * A clause that opens with a question word or with "are you", "did we", after any "ok", "wait" or "bhai" in front:
     * "what time is the train", "ok wait did u eat". "What a day" and "who cares" are exclamations.
     */
    private val questionStart = Regex(
        """^(?:(?:ok|okay|wait|so|and|but|hey|yo|bro|bhai|oh|acha|accha|sun|suno|arre|btw|also|then|um|hm|dude|yaar|listen|pranav)\W+)*""" +
            """(?:(?:what|whats|what's|wat|when|where|who|whose|why|how|which)(?! (a|an|cares|knows)\b)|""" +
            """(?:can|could|will|would|should|shall|did|do|does|are|is|have|has|r|were)(?!')\s+(?:you|u|ya|we|i|he|she|they|it|someone|anyone|this|that|there)\b|""" +
            """u (?:there|free|up|home|around|coming|awake|done|ok|okay|sure|joining|in)\b|wanna\b|any (?:update|news|plans?)\b)""",
        RegexOption.IGNORE_CASE,
    )

    /** Hinglish, Hindi, Marathi, Gujarati, Bengali, Tamil and Arabic question words, which ask wherever they come. */
    private val questionWords = words(
        """kab""", """kahan""", """kahaan""", """kidhar""", """kaise""", """kaisa""", """kaisi""", """kyun""", """kyu""", """kyon""", """kaun""", """kitna""",
        """kitne""", """kitni""", """konsa""", """kaunsa""", """kisko""", """kiska""", """kiski""", """kya(?! (baat|pata|hi)\b)""",
        """क्या(?! (बात|पता))""", """कब""", """कहाँ""", """कहां""", """किधर""", """कैसे""", """कैसा""", """कैसी""", """क्यों""", """कौन""", """कितना""", """कितने""", """कितनी""",
        """काय""", """कधी""", """कुठे""", """किती""", """શું""", """ક્યારે""", """ક્યાં""", """કેમ""", """કોણ""", """કેટલા""", """કેટલું""",
        """কখন""", """কোথায়""", """কেমন""", """কেন""", """என்ன""", """எப்போ""", """எங்கே""", """எப்படி""", """ஏன்""", """متى""", """وين""", """فين""", """كيف""", """ليش""", """شو""",
    )

    /** A request said as an instruction: "bring the charger", "pick up milk", "call mom". Not "check out this", "have fun". */
    private val instruction = Regex(
        """^(?:(?:ok|okay|so|and|bro|bhai|hey|yo|pls|please|also|btw|pranav|dude|and then)\W+)*""" +
            """(?:come(?! on\b)|call|ring|text|bring|send|pick|get(?! well)|buy|tell(?! (?:you|u)\b)|reply|pay|share|sign|fill|submit|book|order|remind|help|meet|join|wake|grab|""" +
            """drop|take(?! care)|give|lend|forward|print|ping|message|msg|dm|email|mail|fix|transfer|return|save|open|answer|respond|confirm|ask|hurry)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** A Hinglish or Hindi verb that ends the clause in the polite imperative: "doodh le aana", "शाम को कॉल करना". */
    private val politeEnding = Regex(
        """(?<![\p{L}\p{M}])(dena|lena|aana|ana|karna|bhejna|batana|rakhna|dekhna|lana|देना|लेना|आना|करना|भेजना|बताना|रखना|लाना)(?![\p{L}\p{M}])\W*$""",
        RegexOption.IGNORE_CASE,
    )

    /** "hi", "hiii", "namaste": someone starting a conversation, which is never only an acknowledgement. */
    private val greeting = Regex("""^\W*(hi+|hey+|hel+o+|hola|namaste|namaskar|नमस्ते|oye|sup|wassup)(?![\p{L}])""", RegexOption.IGNORE_CASE)

    /** "nvm", "chhod": they took the question back. */
    private val withdrawn = words("""nvm""", """never ?mind""", """forget it""", """chhod""", """chhodo""", """jaane de""", """jane de""", """rehne de""")

    /** Words that answer or react without asking anything back. */
    private val acknowledgements = setOf(
        "ok", "okay", "okk", "okie", "okies", "oki", "ohk", "k", "kk", "lol", "lmao", "lmfao", "rofl", "xd", "alr", "aight", "bet",
        "thanks", "thanx", "thnx", "thx", "ty", "tysm", "tq", "tnx", "welcome", "cool", "nice", "great", "good", "sure", "np", "yes", "yeah", "yea",
        "yep", "yup", "ya", "yaa", "haan", "han", "ha", "haa", "no", "nope", "nah", "done", "noted", "alright", "fine", "sweet", "perfect",
        "awesome", "amazing", "lovely", "yay", "woohoo", "gn", "tc", "bye", "byee", "cya", "ttyl", "hmm", "hm", "mm", "oh", "ah", "wow", "true", "same",
        "ikr", "agreed", "exactly", "totally", "sticker", "gif", "nvm",
        // Hinglish and Hindi
        "accha", "acha", "achha", "achcha", "theek", "thik", "thk", "tik", "ji", "jee", "shukriya", "dhanyavad", "dhanyawad", "badhiya", "badiya",
        "sahi", "mast", "wah", "waah", "ठीक", "हाँ", "हां", "जी", "अच्छा", "धन्यवाद", "शुक्रिया", "बढ़िया", "ओके", "हम्म", "सही", "मस्त", "थैंक्स", "वाह",
        // Marathi, Gujarati, Tamil, Bengali, Telugu, Kannada, Punjabi, Urdu
        "बरं", "छान", "સારું", "આભાર", "હા", "ઓકે", "બરાબર", "சரி", "நன்றி", "ஓகே", "சூப்பர்", "ধন্যবাদ", "আচ্ছা", "হ্যাঁ", "ঠিক",
        "సరే", "ధన్యవాదాలు", "ಸರಿ", "ಧನ್ಯವಾದ", "ਠੀਕ", "ਧੰਨਵਾਦ", "ٹھیک", "شکریہ",
        // Arabic, Spanish, French, German, Portuguese, Italian, Indonesian, Turkish, Russian
        "شكرا", "شكراً", "تمام", "حاضر", "اوكي", "مشكور", "طيب", "gracias", "vale", "dale", "perfecto", "genial", "claro", "listo", "merci",
        "dac", "parfait", "super", "génial", "ouais", "oui", "top", "danke", "passt", "gut", "genau", "jo", "obrigado", "obrigada", "valeu",
        "beleza", "blz", "show", "perfeito", "grazie", "perfetto", "certo", "oke", "makasih", "siap", "sip", "iya", "tamam", "teşekkürler",
        "sağol", "eyvallah", "ок", "спасибо", "хорошо", "ладно", "понял", "поняла",
    )

    /** Words that ride along with an acknowledgement without asking anything: "thanks bhai", "ठीक है बेटा", "Danke dir". */
    private val fillers = setOf(
        "bhai", "bhaiya", "yaar", "yar", "bro", "dude", "man", "buddy", "beta", "dear", "re", "na", "toh", "to", "bas", "chal", "chalo", "arre", "are",
        "arey", "so", "very", "much", "a", "lot", "then", "again", "all", "u", "you", "too", "also", "sir", "mam", "maam", "boss", "pagal", "kal",
        "tomorrow", "tmrw", "tmr", "later", "soon", "now", "ab", "babe", "dost", "di", "didi", "is", "hai", "h", "he",
        "है", "हैं", "बेटा", "भाई", "यार", "बहुत", "तो", "कल", "फिर", "अब", "भी", "आहे", "છે", "બેટા", "আছে", "حبيبي", "حبيبتي", "يا",
        "dir", "schön", "vielen", "muchas", "mucho", "beaucoup", "muito", "molto", "banyak", "çok", "большое",
    )
    private val laughs = Regex("""^((a?ha)+h?|(he)+h?|(hi){2,}|lo+l|lmf?ao+|rofl|xd|(ja)+|(je)+|(ji){2,}|k+|(rs)+|mdr|ptdr|(wk)+|(ха)+|(ах)+х?|ه+|(هه)+|(हा)+|(हे)+)$""")

    /** Said in two or more words but meaning one acknowledgement. */
    private val phrases = listOf(
        "thank you" to "thanks", "thank u" to "thanks", "got it" to "ok", "sounds good" to "ok", "all good" to "ok", "no problem" to "np",
        "no worries" to "np", "no prob" to "np", "good night" to "gn", "you're welcome" to "welcome", "youre welcome" to "welcome",
        "see you" to "bye", "see u" to "bye", "see ya" to "bye", "talk later" to "bye", "talk soon" to "bye", "take care" to "tc", "will do" to "ok",
        "never mind" to "nvm", "my pleasure" to "welcome", "much appreciated" to "thanks", "appreciate it" to "thanks",
        "theek hai" to "ok", "thik hai" to "ok", "theek h" to "ok", "thik h" to "ok", "thk h" to "ok", "tik hai" to "ok", "sahi hai" to "ok",
        "ho gaya" to "done", "ho gya" to "done", "mil gaya" to "ok", "mil gaye" to "ok", "mil gya" to "ok", "milte hain" to "bye", "milte hai" to "bye",
        "ठीक है" to "ok", "ठीक हैं" to "ok", "मिलते हैं" to "bye", "हो गया" to "done", "ठीक आहे" to "ok", "बरं झालं" to "ok", "ઠીક છે" to "ok",
        "ધ્યાન રાખજે" to "tc", "ঠিক আছে" to "ok", "de nada" to "welcome", "nos vemos" to "bye", "hasta luego" to "bye", "buenas noches" to "gn",
        "d'accord" to "ok", "à plus" to "bye", "bonne nuit" to "gn", "alles klar" to "ok", "bis dann" to "bye", "bis später" to "bye",
        "gute nacht" to "gn", "tá bom" to "ok", "ta bom" to "ok", "boa noite" to "gn", "até logo" to "bye", "va bene" to "ok",
        "terima kasih" to "thanks", "sağ ol" to "thanks",
    ).toMap()
    private val phrase = Regex("""$BEFORE(${phrases.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }})$AFTER""")

    /** A reaction to one of your messages, written the way iMessage, Google Messages, WhatsApp and Instagram write them. */
    private val reactions = Regex(
        """^(reacted\s.*\bto\b|(liked|loved) (a|an|your) (message|image|photo|video|sticker|gif)|(loved|liked|laughed at|emphasized|disliked)\s*["“'])""",
    )

    /** Everything but letters, digits and apostrophes, including emoji and the selectors that colour them. */
    private val notWords = Regex("""[^\p{L}\p{M}\p{N}\s']|[︎️⃣]""")
    private val spaces = Regex("""\s+""")

    /** "plsssss" and "okkkk": a letter said three or more times is said once. */
    private val stretched = Regex("""(\p{L})\1{2,}""")

    /** "he asked "are you free?"": words in quotes are someone else's. */
    private val quoted = Regex("""["“”«»][^"“”«»]*["“”«»]|(?<![\p{L}])'[^']{2,}'(?![\p{L}])""")
    private val clauseBreak = Regex("""[,.;:!?\n।؟？]+""")

    fun read(messages: List<Message>, inGroup: Boolean): Reading {
        val urgent = messages.any { isUrgent(it.text) }
        val asks = messages.any { asks(it.text) }
        val time = messages.any { Classifier.hasTimeWord(normalize(it.text)) }
        val latest = messages.maxOfOrNull { it.at }
        val keepsWriting = !inGroup && latest != null && messages.count { !it.at.isBefore(latest.minus(burstWindow)) } >= BURST
        // Where the model read a message differently from the lists, the log says so.
        val model = messages.any { m -> clues(m.text).let { it.overruled(MessageModel.Head.Urgent) || it.overruled(MessageModel.Head.Asks) } }
        val reasons = listOfNotNull("urgent".takeIf { urgent }, "asks".takeIf { asks }, "time".takeIf { time }, "keeps writing".takeIf { keepsWriting }, "model".takeIf { model })
        val due = if (urgent) 1.0 else listOf(asks, time, keepsWriting).count { it } * 0.25 + 0.5
        return Reading(due.coerceAtMost(1.0), reasons, urgent)
    }

    /** Urgent, unless it says it isn't, is over, is a joke, or only quotes someone. */
    fun isUrgent(text: String): Boolean = clues(text).decide(MessageModel.Head.Urgent)

    /** A question mark in any script, a question word, a request or an instruction, outside quotes and reactions. */
    fun asks(text: String): Boolean = clues(text).decide(MessageModel.Head.Asks)

    /** Only "ok", "thanks", laughter, emoji or a reaction: nothing is owed back. */
    fun acknowledges(text: String): Boolean = clues(text).decide(MessageModel.Head.Ack)

    /** The engine reads each message three ways in a row, so the last one's clues are kept. */
    @Volatile
    private var last: Clues? = null

    fun clues(text: String): Clues = last?.takeIf { it.text == text } ?: read(text).also { last = it }

    private fun read(text: String): Clues {
        val norm = normalize(text)
        val reaction = reactions.containsMatchIn(norm.trim().lowercase())
        val plain = unquoted(norm)
        val clauses = plain.split(clauseBreak).map { it.trim() }
        val found = linkedMapOf(
            "reaction" to reaction,
            "quoted" to (plain != norm),
            "calm" to calm.containsMatchIn(plain),
            "over" to over.containsMatchIn(plain),
            "joking" to joking.containsMatchIn(plain),
            "urgent words" to urgentWords.containsMatchIn(plain),
            "shouted" to isShouted(plain),
            "question mark" to Classifier.hasQuestionMark(plain),
            "offer" to offer.containsMatchIn(plain),
            "request words" to requestWords.containsMatchIn(plain),
            "question words" to questionWords.containsMatchIn(plain),
            "question start" to clauses.any { questionStart.containsMatchIn(it) },
            "instruction" to clauses.any { instruction.containsMatchIn(it) },
            "polite ending" to clauses.any { politeEnding.containsMatchIn(it) },
            "time" to Classifier.hasTimeWord(plain),
            "greeting" to greeting.containsMatchIn(plain),
        )
        val urgent = !reaction && found.none { (k, v) -> v && k in setOf("calm", "over", "joking") } && (found.getValue("urgent words") || found.getValue("shouted"))
        val asks = !reaction && !found.getValue("offer") && if (found.getValue("question mark")) {
            // "did u see? nvm": only what comes after the question can still ask.
            val after = plain.substring(plain.indexOfLast { Classifier.hasQuestionMark(it.toString()) } + 1)
            found["withdrawn"] = withdrawn.containsMatchIn(after)
            !found.getValue("withdrawn") || asksByRules(after)
        } else {
            found.filterKeys { it in askingClues }.containsValue(true)
        }
        val words = ackWords(norm)
        found["ack words"] = words.isNotEmpty() && words.all { it in acknowledgements || it in fillers || laughs.matches(it) } && words.any { it !in fillers }
        found["only emoji"] = words.isEmpty()
        val acknowledges = reaction || (!Classifier.hasQuestionMark(text) && (found.getValue("ack words") || words.isEmpty()))
        return Clues(text, found.filterValues { it }.keys.toList(), urgent, asks, acknowledges)
    }

    /** The clues that make a message without a question mark ask, as [asksByRules] reads them. */
    private val askingClues = setOf("request words", "question words", "question start", "instruction", "polite ending")

    private fun asksByRules(plain: String): Boolean {
        if (requestWords.containsMatchIn(plain) || questionWords.containsMatchIn(plain)) return true
        return plain.split(clauseBreak).map { it.trim() }.any { questionStart.containsMatchIn(it) || instruction.containsMatchIn(it) || politeEnding.containsMatchIn(it) }
    }

    /** The words of a message with phrases such as "thank you" and "ठीक है" made one word, and emoji gone. */
    private fun ackWords(norm: String): List<String> {
        var plain = notWords.replace(norm.lowercase(), " ").replace(spaces, " ").trim()
        plain = phrase.replace(plain) { phrases.getValue(it.value) }
        return plain.split(' ').filter { it.isNotEmpty() }
    }

    /** "CALL ME", "WHERE ARE YOU": a request in capitals is pressing. */
    private fun isShouted(plain: String): Boolean {
        val letters = plain.filter { it.isLetter() && (it.isUpperCase() || it.isLowerCase()) }
        return letters.length >= 5 && letters.count { it.isUpperCase() } >= letters.length * 0.8 &&
            (Classifier.hasQuestionMark(plain) || asksByRules(plain.lowercase()))
    }

    /**
     * Your name from your contact card, its first word, or the app's @name for you (WhatsApp's "@You"), as a
     * whole word, in Latin letters or written in an Indian script ("प्रणव" for Pranav). The plain word "you" never
     * counts. Names shorter than two letters are left out.
     */
    fun mentions(text: String, names: List<String>, selfName: String?): Boolean {
        val plain = normalize(text)
        val forms = names.flatMap { listOf(it.trim(), it.trim().substringBefore(' ')) }.filter { it.length >= 2 }.distinct()
        val initials = names.mapNotNull { it.trim().split(' ').getOrNull(1)?.firstOrNull()?.uppercaseChar() }.toSet()
        val named = forms.any { form ->
            Regex("""$BEFORE@?${Regex.escape(form)}$AFTER""", RegexOption.IGNORE_CASE).findAll(plain).any { !isSomeoneElse(plain, it.range.last + 1, initials) }
        }
        if (named) return true
        if (forms.any { name -> Indic.words(plain).any { Indic.sameName(it, name) } }) return true
        val self = selfName?.trim()?.takeIf { it.length >= 2 } ?: return false
        return Regex("""@${Regex.escape(self)}$AFTER""", RegexOption.IGNORE_CASE).containsMatchIn(plain)
    }

    /** "Pranav K aur Riddhi": the name, then someone else's initial, is a different Pranav. "Pranav I need…" isn't an initial. */
    private val initial = Regex("""^\s+([A-Z])\.?(?![\p{L}'])""")

    private fun isSomeoneElse(text: String, after: Int, mine: Set<Char>): Boolean {
        val other = initial.find(text.substring(after))?.groupValues?.get(1)?.single() ?: return false
        return mine.isNotEmpty() && other !in mine && other != 'I' && other != 'A'
    }

    /** Composed Hindi letters, curly apostrophes and stretched letters, so the word lists match however the keyboard typed them. */
    fun normalize(text: String): String =
        stretched.replace(Normalizer.normalize(text, Normalizer.Form.NFC).replace('’', '\''), "$1")

    private fun unquoted(text: String): String = quoted.replace(text, " ")

    private fun words(vararg patterns: String): Regex =
        Regex("""$BEFORE(?:${patterns.joinToString("|")})$AFTER""", RegexOption.IGNORE_CASE)

    private const val BEFORE = """(?<![\p{L}\p{M}\p{N}])"""
    private const val AFTER = """(?![\p{L}\p{M}\p{N}])"""
}
