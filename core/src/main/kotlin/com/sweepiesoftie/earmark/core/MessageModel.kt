package com.sweepiesoftie.earmark.core

import java.io.DataInputStream
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * A small text model that reads a message beside the word lists (ADR 0031): three logistic regressions, for urgent,
 * asks and acknowledges, over hashed words, word pairs, runs of two to four letters, emoji, and which rules fired.
 * It learned from labelled messages in `model/data` (`./gradlew :core:trainModel`), never from the benchmark's.
 * The weights are one byte each, about 200 KB, and reading a message takes microseconds, on the phone, with no
 * download. Without the weights file the word lists decide alone.
 */
internal object MessageModel {
    enum class Head { Urgent, Asks, Ack }

    /** Off for the benchmark's rules-only run. */
    @Volatile
    var enabled: Boolean = true

    /**
     * One head's weights, and the probability it needs to say yes: [ifRule] when the word lists already say yes,
     * [otherwise] when they say no, so the model can overrule the lists in either direction only when it is sure.
     */
    class Weights(val bias: Float, val scale: Float, val ifRule: Float, val otherwise: Float, val weights: ByteArray)

    class Model(val buckets: Int, val heads: List<Weights>)

    /** An absolute path, because R8 renames this class's package in release builds but not the resource's folder. */
    private const val RESOURCE = "/com/sweepiesoftie/earmark/core/message-model.bin"
    private const val MAGIC = 0x454d4b31 // "EMK1"

    private val model: Model? by lazy {
        runCatching { MessageModel::class.java.getResourceAsStream(RESOURCE)?.use { read(DataInputStream(it.buffered())) } }.getOrNull()
    }

    val loaded: Boolean get() = model != null

    fun read(input: DataInputStream): Model {
        require(input.readInt() == MAGIC) { "not a message model" }
        val buckets = input.readInt()
        val heads = Head.entries.map {
            val bias = input.readFloat()
            val scale = input.readFloat()
            val ifRule = input.readFloat()
            val otherwise = input.readFloat()
            Weights(bias, scale, ifRule, otherwise, ByteArray(buckets).also(input::readFully))
        }
        return Model(buckets, heads)
    }

    /** How sure the model is of each head, or null when there are no weights. */
    fun probabilities(clues: Clues): DoubleArray? {
        val model = model ?: return null
        val features = features(clues.text, clues.fired, model.buckets)
        return DoubleArray(Head.entries.size) { probability(model.heads[it], features) }
    }

    fun decide(head: Head, rule: Boolean, probability: Double): Boolean {
        val weights = model?.heads?.get(head.ordinal)
        if (!enabled || weights == null) return rule
        return probability >= if (rule) weights.ifRule else weights.otherwise
    }

    fun probability(weights: Weights, features: IntArray): Double {
        val each = 1.0 / sqrt(features.size.coerceAtLeast(1).toDouble())
        var z = weights.bias.toDouble()
        for (feature in features) z += weights.weights[feature] * weights.scale * each
        return 1.0 / (1.0 + exp(-z))
    }

    private val tokens = Regex("""[\p{L}\p{M}\p{N}']+|[?!]|[^\s\p{L}\p{M}\p{N}\p{P}\p{Z}]""")

    /** Long messages are read up to here: what they ask is near the start, and the time stays small. */
    private const val READ_UP_TO = 400

    /** Each feature hashed into one of [buckets] slots, a power of two. */
    fun features(text: String, clues: List<String>, buckets: Int): IntArray {
        val words = tokens.findAll(Urgency.normalize(text).lowercase().take(READ_UP_TO)).map { it.value }.toList()
        val out = ArrayList<Int>(words.size * 12 + clues.size + 4)
        fun add(feature: String) = out.add(hash(feature) and (buckets - 1))
        words.forEachIndexed { i, word ->
            add("w|$word")
            if (i > 0) add("b|${words[i - 1]}|$word")
            if (word.length > 1 && word[0].isLetterOrDigit()) {
                val padded = "<$word>"
                for (n in 2..4) for (start in 0..padded.length - n) add("c|" + padded.substring(start, start + n))
            }
        }
        words.firstOrNull()?.let { add("f|$it") }
        words.lastOrNull()?.let { add("l|$it") }
        add("n|" + when (words.size) { 0 -> 0; 1 -> 1; 2 -> 2; in 3..5 -> 3; in 6..12 -> 4; in 13..25 -> 5; else -> 6 })
        clues.forEach { add("r|$it") }
        return out.toIntArray()
    }

    /** FNV-1a, so the trainer and the phone put a feature in the same slot. */
    private fun hash(text: String): Int {
        var hash = -0x7ee3623b
        for (c in text) {
            hash = hash xor c.code
            hash *= 16777619
        }
        return hash
    }
}
