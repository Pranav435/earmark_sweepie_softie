package com.sweepiesoftie.earmark.core

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Runs the rules over the labelled cases in benchmark/data and writes how often they were right to benchmark/results:
 * results.json for the numbers, misses.md for every case they got wrong. benchmark/LABELS.md says how the cases were
 * written and labelled. `./gradlew :core:benchmark`
 */
fun main(args: Array<String>) {
    val root = File(args.firstOrNull() ?: "benchmark")
    // "rules" measures the word lists alone, without the message model (ADR 0031).
    MessageModel.enabled = args.getOrNull(1) != "rules"
    val bench = Bench(File(root, "data"), File(root, if (MessageModel.enabled) "results" else "results/rules-only").apply { mkdirs() })
    bench.classifier()
    bench.urgency()
    bench.money()
    bench.guesses()
    bench.replies()
    bench.write()
}

private val zone: ZoneId = ZoneId.of("Asia/Kolkata")

/** Tuesday 13 October 2026, 4 PM, as in the demo stories. */
private val base: Instant = ZonedDateTime.of(2026, 10, 13, 16, 0, 0, 0, zone).toInstant()

/** A new install with the owner's name from his contact card, as ProfileReader reads it. */
private val settings = Settings(ownPackage = "com.sweepiesoftie.earmark", zone = zone, myNames = listOf("Pranav Savla", "Pranav"))

private class Bench(private val data: File, private val out: File) {
    private val results = JsonObject()
    private val misses = StringBuilder("# Benchmark misses\n\nEvery case the rules got wrong. Written by `./gradlew :core:benchmark`; the cases are in `benchmark/data`.\n")

    /** One label per notification, as Earmark would act on it. */
    fun classifier() {
        val rows = read("notifications.json").map { case ->
            val signal = signal(case.obj("notification"), "k-${case.str("id")}", base)
            val result = Classifier.classify(signal, settings)
            Labelled(case, case.obj("expected"), label(result), result)
        }
        val part = JsonObject()
        part.add("all", hits(rows.count { it.right }, rows.size))
        part.add("byDifficulty", split(rows, { it.case.str("difficulty") }, { it.right }))
        part.add("byLanguage", split(rows, { language(it.case.str("language")) }, { it.right }))

        val loop = Binary()
        rows.forEach { loop.add(it.expected.str("label") in LOOPS, it.got in LOOPS) }
        part.add("isLoop", loop.json())

        val labels = JsonObject()
        (rows.map { it.expected.str("label")!! } + rows.map { it.got }).distinct().sortedBy { ORDER.indexOf(it) }.forEach { label ->
            labels.add(label, Binary().apply { rows.forEach { add(it.expected.str("label") == label, it.got == label) } }.json())
        }
        part.add("byLabel", labels)

        val confusion = JsonObject()
        rows.groupBy { it.expected.str("label")!! }.toSortedMap(compareBy { ORDER.indexOf(it) }).forEach { (label, group) ->
            confusion.add(label, JsonObject().apply { group.groupingBy { it.got }.eachCount().forEach { (got, n) -> addProperty(got, n) } })
        }
        part.add("confusion", confusion)

        // The chip names the person and the amount, so a loop of the right kind can still read wrong.
        val named = rows.filter { it.right && it.result is Classification.Open && it.expected.str("who") != null }
        part.add("who", hits(named.count { same(it.expected.str("who"), (it.result as Classification.Open).loop.who) }, named.size))
        val priced = rows.filter { it.right && it.result is Classification.Open && it.expected.maybe("amount") != null }
        part.add("amount", hits(priced.count { sameMoney(it.expected.obj("amount"), (it.result as Classification.Open).loop.detail?.let(MoneyParser::find)) }, priced.size))
        // How long one notification takes to read, after the JIT has warmed up, on the machine running this.
        val signals = read("notifications.json").map { signal(it.obj("notification"), "k-${it.str("id")}", base) }
        part.addProperty("microsecondsPerNotification", micros(signals) { Classifier.classify(it, settings) })
        results.add("classifier", part)

        section("Classifier", "expected → got")
        rows.filterNot { it.right }.forEach { miss(it.case, "`${it.expected.str("label")}` → `${it.got}` (${why(it.result)})", gist(it.case.obj("notification"))) }
        section("Classifier names and amounts", "expected → got")
        named.filterNot { same(it.expected.str("who"), (it.result as Classification.Open).loop.who) }.forEach {
            miss(it.case, "who “${it.expected.str("who")}” → “${(it.result as Classification.Open).loop.who}”", gist(it.case.obj("notification")))
        }
        priced.forEach {
            val got = (it.result as Classification.Open).loop.detail?.let(MoneyParser::find)
            if (!sameMoney(it.expected.obj("amount"), got)) miss(it.case, "amount ${money(it.expected.obj("amount"))} → ${got?.full}", gist(it.case.obj("notification")))
        }
    }

    /** The three word-list readings of a message, each scored on its own. */
    fun urgency() {
        val cases = read("messages.json")
        val part = JsonObject()
        val checks = listOf<Pair<String, (String) -> Boolean>>("urgent" to Urgency::isUrgent, "asks" to Urgency::asks, "ack" to Urgency::acknowledges)
        section("Urgency", "expected → got")
        checks.forEach { (name, check) ->
            val rows = cases.map { it to (check(it.str("text")!!) == it.bool(name)) }
            val binary = Binary().apply { cases.forEach { add(it.bool(name), check(it.str("text")!!)) } }
            part.add(name, binary.json().apply {
                add("byDifficulty", split(rows, { it.first.str("difficulty") }, { it.second }))
                add("byLanguage", split(rows, { language(it.first.str("language")) }, { it.second }))
            })
            rows.filterNot { it.second }.forEach { (case, _) -> miss(case, "$name: ${case.bool(name)} → ${!case.bool(name)}", case.str("text")!!) }
        }
        part.addProperty("microsecondsPerMessage", micros(cases.map { it.str("text")!! }) { Urgency.isUrgent(it); Urgency.asks(it); Urgency.acknowledges(it) })
        results.add("urgency", part)
    }

    fun money() {
        val cases = read("money.json")
        var found = 0
        var wrong = 0
        var missed = 0
        var invented = 0
        var none = 0
        section("MoneyParser", "expected → got")
        val rows = cases.map { case ->
            val expected = case.maybe("amount")
            val got = MoneyParser.find(case.str("text")!!)
            when {
                expected == null && got == null -> none++
                expected == null -> invented++
                got == null -> missed++
                sameMoney(expected, got) -> found++
                else -> wrong++
            }
            val right = if (expected == null) got == null else sameMoney(expected, got)
            if (!right) miss(case, "${expected?.let(::money) ?: "no amount"} → ${got?.full ?: "no amount"}", case.str("text")!!)
            case to right
        }
        results.add("money", JsonObject().apply {
            add("all", hits(rows.count { it.second }, rows.size))
            add("byDifficulty", split(rows, { it.first.str("difficulty") }, { it.second }))
            addProperty("found", found)
            addProperty("wrongAmount", wrong)
            addProperty("missed", missed)
            addProperty("invented", invented)
            addProperty("rightlyNone", none)
        })
    }

    /** The whole engine on a fresh install: the notifications arrive while the phone is down, then it's unlocked. */
    fun guesses() {
        section("Guess at unlock", "expected → got")
        val rows = read("guesses.json").map { case ->
            val engine = Engine(settings)
            val items = mutableMapOf<String, MutableList<String>>()
            case.list("items").sortedByDescending { it.num("minutesBeforeUnlock") }.forEach { item ->
                val at = base.minusSeconds(((item.num("minutesBeforeUnlock") ?: 0.0) * 60).toLong())
                val signal = signal(item.obj("notification"), "k-${case.str("id")}-${item.str("id")}", at)
                items.getOrPut(signal.conversationKey!!) { mutableListOf() } += item.str("id")!!
                engine.handle(Event.NotificationPosted(signal), at)
            }
            val effects = engine.handle(Event.Unlocked, base)
            val chip = effects.filterIsInstance<Effect.ShowChip>().firstOrNull()?.chip
            // Several notifications from one conversation are one loop, so the chip can stand for any of them.
            val got = chip?.let { items[it.loopId.value.substringAfter(':')] ?: listOf("other") } ?: listOf("none")
            val expected = case.obj("expected")
            val best = expected.str("chip")!!
            val fine = listOf(best) + expected.get("alsoFine")?.takeIf { it.isJsonArray }?.asJsonArray?.map { it.asString }.orEmpty()
            val decision = effects.filterIsInstance<Effect.Log>().lastOrNull { it.line.startsWith("unlock:") }?.line.orEmpty()
            Guess(case, best, fine, got, chip?.title, decision)
        }
        val shows = Binary().apply { rows.forEach { add(it.best != "none", "none" !in it.got) } }
        val shown = rows.filter { it.best != "none" && "none" !in it.got }
        results.add("guess", JsonObject().apply {
            add("best", hits(rows.count { it.best in it.got }, rows.size))
            add("fine", hits(rows.count { it.fine.any(it.got::contains) }, rows.size))
            add("byDifficulty", split(rows, { it.case.str("difficulty") }, { it.fine.any(it.got::contains) }))
            add("byLanguage", split(rows, { language(it.case.str("language")) }, { it.fine.any(it.got::contains) }))
            add("showsChip", shows.json())
            add("rightItemWhenShown", hits(shown.count { it.fine.any(it.got::contains) }, shown.size))
        })
        rows.filterNot { it.fine.any(it.got::contains) }.forEach {
            val items = it.case.list("items").joinToString(" | ") { item -> "${item.str("id")}, ${item.num("minutesBeforeUnlock")?.toInt()} min: ${gist(item.obj("notification"))}" }
            miss(it.case, "`${it.fine.joinToString(" or ")}` → `${it.got.joinToString(" or ")}`${it.title?.let { t -> " “$t”" }.orEmpty()}; $items", it.decision)
        }
    }

    /**
     * The reply strip for every message that isn't only an acknowledgement. Quick replies come from the core; Smart
     * Reply's come from the bench app on a phone (results/smart-reply.json), and whether each reply makes sense from
     * data/reply-judgments.json. Without them this writes what is still to be judged.
     */
    fun replies() {
        val messages = read("messages.json").filterNot { it.bool("ack") }
        val quick = messages.associate { it.str("id")!! to QuickReplies.forMessage(it.str("text")!!) }
        val smart = File(out, "smart-reply.json").takeIf { it.exists() }?.let { file ->
            JsonParser.parseString(file.readText()).asJsonArray.map { it.asJsonObject }.associateBy { it.str("id")!! }
        }
        val toJudge = JsonArray()
        messages.forEach { message ->
            val id = message.str("id")!!
            val replies = (quick.getValue(id) + smart?.get(id)?.strings("replies").orEmpty()).distinct().sorted()
            toJudge.add(JsonObject().apply {
                addProperty("id", id)
                addProperty("message", message.str("text"))
                add("replies", JsonArray().apply { replies.forEach(::add) })
            })
        }
        File(out, "replies-to-judge.json").writeText(gson.toJson(toJudge))
        val judged = File(data, "reply-judgments.json").takeIf { it.exists() && smart != null }?.let { file ->
            JsonParser.parseString(file.readText()).asJsonArray.map { it.asJsonObject }.associate { it.str("id")!! to it.obj("sensible") }
        } ?: return

        val unjudged = toJudge.map { it.asJsonObject }.sumOf { m -> m.strings("replies").count { judged[m.str("id")]?.has(it) != true } }
        if (unjudged > 0) {
            println("replies: $unjudged replies aren't judged yet; judge results/replies-to-judge.json into data/reply-judgments.json")
            return
        }

        fun sensible(id: String, reply: String): Boolean = judged.getValue(id).get(reply).asBoolean
        fun rate(sets: List<Pair<JsonObject, List<String>>>) = JsonObject().apply {
            add("anySensible", hits(sets.count { (m, r) -> r.any { sensible(m.str("id")!!, it) } }, sets.size))
            add("repliesSensible", hits(sets.sumOf { (m, r) -> r.count { sensible(m.str("id")!!, it) } }, sets.sumOf { it.second.size }))
        }
        val status = messages.associateWith { smart!![it.str("id")]?.str("status") ?: "missing" }
        // What the strip shows: Smart Reply's replies, quick replies when it can't read the language or fails, or nothing.
        val strip = messages.map { m ->
            val s = status.getValue(m)
            m to when (s) {
                "ok" -> smart!!.getValue(m.str("id")!!).strings("replies")
                "no confident reply" -> emptyList()
                else -> quick.getValue(m.str("id")!!)
            }
        }
        val answered = messages.filter { status[it] == "ok" }.map { it to smart!!.getValue(it.str("id")!!).strings("replies") }
        results.add("replies", JsonObject().apply {
            add("smartReplyStatus", JsonObject().apply { status.values.groupingBy { it }.eachCount().forEach { (s, n) -> addProperty(s, n) } })
            add("smartReplyAnswered", hits(answered.size, messages.size))
            add("smartReplyWhenItAnswers", rate(answered))
            add("smartReplyByLanguage", split(messages, { language(it.str("language")) }, { status[it] == "ok" }))
            add("quickReplies", rate(messages.map { it to quick.getValue(it.str("id")!!) }))
            add("quickRepliesByLanguage", split(messages, { language(it.str("language")) }, { m -> quick.getValue(m.str("id")!!).any { sensible(m.str("id")!!, it) } }))
            add("strip", rate(strip).apply { add("empty", hits(strip.count { it.second.isEmpty() }, strip.size)) })
            add("stripByLanguage", split(strip, { language(it.first.str("language")) }, { (m, r) -> r.any { sensible(m.str("id")!!, it) } }))
        })
        section("Reply strip", "where nothing on the strip makes sense")
        strip.filterNot { (m, r) -> r.any { sensible(m.str("id")!!, it) } }.forEach { (m, r) ->
            miss(m, if (r.isEmpty()) "no strip (${status[m]})" else r.joinToString(" / ") { "“$it”" }, m.str("text")!!)
        }
    }

    fun write() {
        File(out, "results.json").writeText(gson.toJson(results))
        File(out, "misses.md").writeText(misses.toString())
        fun line(name: String, hits: JsonObject?) = hits?.let { println("%-34s %4d of %4d  %5.1f%%".format(name, it["right"].asInt, it["total"].asInt, 100.0 * (it["rate"].takeUnless { r -> r.isJsonNull }?.asDouble ?: 0.0))) }
        val classifier = results.getAsJsonObject("classifier")
        line("classifier: right label", classifier["all"].asJsonObject)
        line("classifier: loop precision", classifier.getAsJsonObject("isLoop")["precision"].asJsonObject)
        line("classifier: loop recall", classifier.getAsJsonObject("isLoop")["recall"].asJsonObject)
        listOf("urgent", "asks", "ack").forEach { line("urgency: $it", results.getAsJsonObject("urgency").getAsJsonObject(it)["accuracy"].asJsonObject) }
        line("money: right amount", results.getAsJsonObject("money")["all"].asJsonObject)
        line("guess: a fine chip or none", results.getAsJsonObject("guess")["fine"].asJsonObject)
        line("replies: strip has a sensible reply", results.getAsJsonObject("replies")?.getAsJsonObject("strip")?.get("anySensible")?.asJsonObject)
        println("Wrote ${out.path}/results.json and misses.md")
    }

    private fun read(name: String): List<JsonObject> = JsonParser.parseString(File(data, name).readText()).asJsonArray.map { it.asJsonObject }

    private fun section(title: String, how: String) {
        misses.append("\n## $title\n\nEach line: case id, difficulty and language; $how; what the case was.\n\n")
    }

    private fun miss(case: JsonObject, what: String, gist: String) {
        val note = case.str("note")?.let { " Note: $it" }.orEmpty()
        misses.append("- **${case.str("id")}** (${case.str("difficulty")}, ${case.str("language")}) $what. ${oneLine(gist)}$note\n")
    }
}

private class Labelled(val case: JsonObject, val expected: JsonObject, val got: String, val result: Classification) {
    val right: Boolean get() = expected.str("label") == got
}

private class Guess(val case: JsonObject, val best: String, val fine: List<String>, val got: List<String>, val title: String?, val decision: String)

/** A yes-or-no check, counted the four ways it can come out. */
private class Binary {
    private var tp = 0
    private var fp = 0
    private var fn = 0
    private var tn = 0

    fun add(expected: Boolean, got: Boolean) {
        when {
            expected && got -> tp++
            got -> fp++
            expected -> fn++
            else -> tn++
        }
    }

    fun json(): JsonObject = JsonObject().apply {
        add("accuracy", hits(tp + tn, tp + fp + fn + tn))
        add("precision", hits(tp, tp + fp))
        add("recall", hits(tp, tp + fn))
        add("specificity", hits(tn, tn + fp))
    }
}

private val LOOPS = setOf("reply", "callback", "email", "reminder", "payment_request", "bill")
private val ORDER = listOf("reply", "callback", "email", "reminder", "payment_request", "bill", "paid", "booked", "replied", "none")
private val gson = GsonBuilder().setPrettyPrinting().serializeNulls().disableHtmlEscaping().create()

private fun label(result: Classification): String = when (result) {
    is Classification.Open -> when (result.loop.kind) {
        LoopKind.Reply -> "reply"
        LoopKind.CallBack -> "callback"
        LoopKind.Email -> "email"
        LoopKind.Reminder -> "reminder"
        LoopKind.Payment -> if (result.loop.bill) "bill" else "payment_request"
        else -> result.loop.kind.name.lowercase()
    }
    is Classification.Replied -> "replied"
    is Classification.Booked -> "booked"
    is Classification.Paid -> "paid"
    is Classification.Quiet, is Classification.Skip -> "none"
}

private fun why(result: Classification): String = when (result) {
    is Classification.Open -> "opened “${result.loop.task}”" + result.loop.why.takeIf { it.isNotEmpty() }?.joinToString(prefix = ": ").orEmpty()
    is Classification.Skip -> "skip: ${result.reason}"
    is Classification.Quiet -> "group chatter, not for you"
    else -> result::class.simpleName.orEmpty().lowercase()
}

/**
 * A notification as NotificationReader builds it: a message with no sender is yours, the conversation is the group,
 * the last sender, or an email's or missed call's title, and there's no mention flag, which Android doesn't carry.
 */
private fun signal(n: JsonObject, key: String, postedAt: Instant): Signal {
    val messages = n.list("messages").map { m ->
        Message(
            sender = m.str("sender"),
            text = m.str("text").orEmpty(),
            at = postedAt.minusSeconds(((m.num("minutesAgo") ?: 0.0) * 60).toLong()),
            fromUser = m.bool("fromUser") || m.str("sender") == null,
            isBot = m.bool("isBot"),
        )
    }
    val app = n.str("app")!!
    val category = n.str("category")
    val title = n.str("title")
    val byTitle = title.takeIf { category == "email" || category == "missed_call" }
    val conversation = n.str("groupName") ?: messages.lastOrNull { !it.fromUser }?.sender ?: byTitle ?: key
    return Signal(
        key = key,
        packageName = app,
        appLabel = n.str("appLabel") ?: app,
        postedAt = postedAt,
        title = title,
        text = n.str("text"),
        bigText = n.str("bigText"),
        messages = messages,
        conversationKey = "$app|$conversation",
        isGroupConversation = n.bool("isGroup"),
        conversationTitle = n.str("groupName"),
        selfName = n.str("selfName"),
        hasReplyAction = n.bool("hasReplyAction"),
        isOngoing = n.bool("isOngoing"),
        isGroupSummary = n.bool("isGroupSummary"),
        category = category,
    )
}

/** Average over five passes after three to warm up, on the machine running the benchmark. */
private fun <T> micros(items: List<T>, run: (T) -> Unit): Double {
    repeat(3) { items.forEach(run) }
    val start = System.nanoTime()
    repeat(5) { items.forEach(run) }
    return Math.round((System.nanoTime() - start) / 1000.0 / (5 * items.size) * 10) / 10.0
}

private fun hits(right: Int, total: Int): JsonObject = JsonObject().apply {
    addProperty("right", right)
    addProperty("total", total)
    addProperty("rate", if (total == 0) null else Math.round(1000.0 * right / total) / 1000.0)
}

private fun <T> split(rows: List<T>, key: (T) -> String?, right: (T) -> Boolean): JsonObject = JsonObject().apply {
    rows.groupBy { key(it) ?: "unknown" }.toSortedMap().forEach { (k, group) -> add(k, hits(group.count(right), group.size)) }
}

private fun language(code: String?): String = if (code in setOf("en", "hinglish", "hi")) code!! else "other"

private fun same(expected: String?, got: String?): Boolean =
    expected != null && got != null && expected.trim().removePrefix("~").trim().equals(got.trim(), ignoreCase = true)

private val CURRENCIES = mapOf("₹" to "INR", "$" to "USD", "€" to "EUR", "£" to "GBP")

private fun sameMoney(expected: JsonObject, got: Money?): Boolean =
    got != null && CURRENCIES[got.symbol] == expected.str("currency") && got.amount.compareTo(BigDecimal.valueOf(expected.num("value")!!)) == 0

private fun money(amount: JsonObject): String = "${amount.str("currency")} ${amount.num("value")}"

/** What the notification said, for a miss: the app, its two lines, and the last message when it says more. */
private fun gist(n: JsonObject): String {
    val last = n.list("messages").lastOrNull()?.let { "${it.str("sender") ?: "you"}: ${it.str("text")}" }
    return listOfNotNull(n.str("appLabel"), n.str("category")?.let { "[$it]" }, n.str("title"), n.str("text"), last?.takeUnless { n.str("text")?.let(it::endsWith) == true })
        .joinToString(" · ")
}

private fun oneLine(text: String): String = text.replace(Regex("""\s+"""), " ").trim().let { if (it.length > 220) it.take(217) + "…" else it }

private fun JsonObject.str(name: String): String? = get(name)?.takeUnless { it.isJsonNull }?.asString
private fun JsonObject.bool(name: String): Boolean = get(name)?.takeUnless { it.isJsonNull }?.asBoolean ?: false
private fun JsonObject.num(name: String): Double? = get(name)?.takeUnless { it.isJsonNull }?.asDouble
private fun JsonObject.obj(name: String): JsonObject = getAsJsonObject(name)
private fun JsonObject.maybe(name: String): JsonObject? = get(name)?.takeIf { it.isJsonObject }?.asJsonObject
private fun JsonObject.list(name: String): List<JsonObject> = get(name)?.takeIf { it.isJsonArray }?.asJsonArray?.map { it.asJsonObject }.orEmpty()
private fun JsonObject.strings(name: String): List<String> = get(name)?.takeIf { it.isJsonArray }?.asJsonArray?.map { it.asString }.orEmpty()
