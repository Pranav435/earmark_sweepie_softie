package com.sweepiesoftie.earmark.core

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Trains [MessageModel] on the labelled messages in model/data (model/README.md), never on the benchmark's, and
 * writes its weights beside the core's code. Five-fold cross-validation picks each head's two thresholds from
 * predictions on messages the model didn't train on; then the model trains on everything. Writes model/results.json.
 * `./gradlew :core:trainModel`
 */
fun main(args: Array<String>) {
    val root = File(args.firstOrNull() ?: ".")
    val buckets = 1 shl 15
    val rows = File(root, "model/data").listFiles { f -> f.name.startsWith("train") && f.extension == "json" }!!.sorted()
        .flatMap { file -> JsonParser.parseString(file.readText()).asJsonArray.map { it.asJsonObject } }
        .distinctBy { it["text"].asString.trim().lowercase() }
    MessageModel.enabled = false
    val clues = rows.map { Urgency.clues(it["text"].asString) }
    val xs = rows.indices.map { MessageModel.features(clues[it].text, clues[it].fired, buckets) }
    val labels = MessageModel.Head.entries.map { head -> rows.map { it[key(head)].asBoolean } }
    println("${rows.size} messages from ${File(root, "model/data").list()!!.count { it.startsWith("train") }} files, $buckets slots")

    val results = JsonObject()
    val folds = rows.indices.shuffled(Random(31)).withIndex().associate { (i, row) -> row to i % 5 }
    val heads = MessageModel.Head.entries.map { head ->
        val ys = labels[head.ordinal]
        val outOfFold = DoubleArray(rows.size)
        for (k in 0 until 5) {
            val train = rows.indices.filter { folds[it] != k }
            val trained = train(train.map { xs[it] }, train.map { ys[it] }, buckets)
            rows.indices.filter { folds[it] == k }.forEach { outOfFold[it] = trained.predict(xs[it]) }
        }
        val rule = rows.indices.map { clues[it].rule(head) }
        val (ifRule, otherwise) = thresholds(head, rule, outOfFold, ys)
        val combined = rows.indices.map { decide(head, clues[it], outOfFold[it], ifRule, otherwise) }
        results.add(head.name.lowercase(), JsonObject().apply {
            add("rulesCrossValidated", Counts(rule, ys).json())
            add("withModelCrossValidated", Counts(combined, ys).json())
            addProperty("ifRule", ifRule)
            addProperty("otherwise", otherwise)
        })
        println("%-7s rules %s | rules + model (5-fold) %s | thresholds %.3f / %.3f".format(head, Counts(rule, ys), Counts(combined, ys), ifRule, otherwise))
        val final = train(xs, ys, buckets)
        quantize(final, ifRule, otherwise)
    }

    val bytes = ByteArrayOutputStream().also { out ->
        DataOutputStream(out).use { data ->
            data.writeInt(0x454d4b31)
            data.writeInt(buckets)
            heads.forEach { head ->
                data.writeFloat(head.bias)
                data.writeFloat(head.scale)
                data.writeFloat(head.ifRule)
                data.writeFloat(head.otherwise)
                data.write(head.weights)
            }
        }
    }.toByteArray()
    val target = File(root, "core/src/main/resources/com/sweepiesoftie/earmark/core/message-model.bin").apply { parentFile.mkdirs() }
    target.writeBytes(bytes)
    println("Wrote ${target.path}, ${bytes.size / 1024} KB")

    // The benchmark's messages, which the model never saw: the lists alone, then with the model, as it will run on the phone.
    val model = MessageModel.read(DataInputStream(ByteArrayInputStream(bytes)))
    val dev = JsonParser.parseString(File(root, "benchmark/data/messages.json").readText()).asJsonArray.map { it.asJsonObject }
    val devClues = dev.map { Urgency.clues(it["text"].asString) }
    val devFeatures = devClues.map { MessageModel.features(it.text, it.fired, buckets) }
    val dev2 = JsonObject()
    MessageModel.Head.entries.forEach { head ->
        val ys = dev.map { it[key(head)].asBoolean }
        val weights = model.heads[head.ordinal]
        val rule = devClues.map { it.rule(head) }
        val combined = devClues.indices.map { decide(head, devClues[it], MessageModel.probability(weights, devFeatures[it]), weights.ifRule.toDouble(), weights.otherwise.toDouble()) }
        dev2.add(head.name.lowercase(), JsonObject().apply { add("rules", Counts(rule, ys).json()); add("withModel", Counts(combined, ys).json()) })
        println("%-7s benchmark/data: rules %s | rules + model %s".format(head, Counts(rule, ys), Counts(combined, ys)))
    }
    results.add("benchmarkMessages", dev2)

    // How long one message takes, rules and model together, after the JIT has warmed up.
    val texts = rows.map { it["text"].asString }
    repeat(3) { texts.forEach { text -> Urgency.clues("$text ").let { c -> model.heads.forEach { MessageModel.probability(it, MessageModel.features(c.text, c.fired, buckets)) } } } }
    val start = System.nanoTime()
    texts.forEach { text -> Urgency.clues("$text  ").let { c -> model.heads.forEach { MessageModel.probability(it, MessageModel.features(c.text, c.fired, buckets)) } } }
    val micros = (System.nanoTime() - start) / 1000.0 / texts.size
    println("%.0f µs a message on this laptop, rules and model".format(micros))
    results.addProperty("messages", rows.size)
    results.addProperty("buckets", buckets)
    results.addProperty("bytes", bytes.size)
    results.addProperty("microsecondsPerMessageOnLaptop", micros.roundToInt())
    File(root, "model/results.json").writeText(GsonBuilder().setPrettyPrinting().create().toJson(results))
}

private fun key(head: MessageModel.Head): String = when (head) {
    MessageModel.Head.Urgent -> "urgent"
    MessageModel.Head.Asks -> "asks"
    MessageModel.Head.Ack -> "ack"
}

/** As [Clues.decide] does on the phone: the lists that say what a message isn't have the last word. */
private fun decide(head: MessageModel.Head, clues: Clues, p: Double, ifRule: Double, otherwise: Double): Boolean {
    if (clues.fired.any { it in Clues.vetoes.getValue(head) }) return clues.rule(head)
    return p >= if (clues.rule(head)) ifRule else otherwise
}

private class Trained(val bias: Double, val weights: DoubleArray) {
    fun predict(features: IntArray): Double {
        val each = 1.0 / sqrt(features.size.coerceAtLeast(1).toDouble())
        var z = bias
        for (f in features) z += weights[f] * each
        return 1.0 / (1.0 + exp(-z))
    }
}

/** Logistic regression by AdaGrad, each message's features scaled to unit length. */
private fun train(xs: List<IntArray>, ys: List<Boolean>, buckets: Int, epochs: Int = 25, rate: Double = 0.4, l2: Double = 1e-6): Trained {
    val weights = DoubleArray(buckets)
    val squares = DoubleArray(buckets) { 1e-8 }
    var bias = 0.0
    var biasSquares = 1e-8
    val random = Random(7)
    val order = xs.indices.toMutableList()
    repeat(epochs) {
        order.shuffle(random)
        for (i in order) {
            val x = xs[i]
            val each = 1.0 / sqrt(x.size.coerceAtLeast(1).toDouble())
            var z = bias
            for (f in x) z += weights[f] * each
            val error = 1.0 / (1.0 + exp(-z)) - if (ys[i]) 1.0 else 0.0
            for (f in x) {
                val g = error * each + l2 * weights[f]
                squares[f] += g * g
                weights[f] -= rate * g / sqrt(squares[f])
            }
            biasSquares += error * error
            bias -= rate * error / sqrt(biasSquares)
        }
    }
    return Trained(bias, weights)
}

/**
 * The two thresholds that do best on predictions for messages the model didn't train on: F1 for urgent and asks;
 * for acknowledges, F0.5, because calling a real message "ok" loses it, while missing an "ok" only opens a stray loop.
 */
private fun thresholds(head: MessageModel.Head, rule: List<Boolean>, p: DoubleArray, ys: List<Boolean>): Pair<Double, Double> {
    val grid = (2..98).map { it / 100.0 }
    val beta = if (head == MessageModel.Head.Ack) 0.5 else 1.0
    return grid.flatMap { a -> grid.map { b -> a to b } }.maxBy { (ifRule, otherwise) ->
        Counts(p.indices.map { p[it] >= if (rule[it]) ifRule else otherwise }, ys).f(beta)
    }
}

private fun quantize(trained: Trained, ifRule: Double, otherwise: Double): MessageModel.Weights {
    val scale = (trained.weights.maxOf { abs(it) } / 127.0).coerceAtLeast(1e-9)
    val bytes = ByteArray(trained.weights.size) { (trained.weights[it] / scale).roundToInt().coerceIn(-127, 127).toByte() }
    return MessageModel.Weights(trained.bias.toFloat(), scale.toFloat(), ifRule.toFloat(), otherwise.toFloat(), bytes)
}

private class Counts(got: List<Boolean>, expected: List<Boolean>) {
    val tp = got.indices.count { got[it] && expected[it] }
    val fp = got.indices.count { got[it] && !expected[it] }
    val fn = got.indices.count { !got[it] && expected[it] }
    val tn = got.size - tp - fp - fn
    val precision get() = if (tp + fp == 0) 0.0 else tp.toDouble() / (tp + fp)
    val recall get() = if (tp + fn == 0) 0.0 else tp.toDouble() / (tp + fn)
    val accuracy get() = (tp + tn).toDouble() / (tp + fp + fn + tn)

    fun f(beta: Double): Double {
        val b2 = beta * beta
        return if (precision + recall == 0.0) 0.0 else (1 + b2) * precision * recall / (b2 * precision + recall)
    }

    fun json() = JsonObject().apply {
        addProperty("tp", tp); addProperty("fp", fp); addProperty("fn", fn); addProperty("tn", tn)
        addProperty("precision", Math.round(precision * 1000) / 1000.0)
        addProperty("recall", Math.round(recall * 1000) / 1000.0)
        addProperty("accuracy", Math.round(accuracy * 1000) / 1000.0)
    }

    override fun toString() = "acc %.3f P %.3f R %.3f".format(accuracy, precision, recall)
}
