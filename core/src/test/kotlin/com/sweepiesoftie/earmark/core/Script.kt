package com.sweepiesoftie.earmark.core

import java.time.Instant

/** Replays a story through [Engine.handle] on a fake clock and keeps the visible effects (everything but Log). */
class Script(settings: Settings = Fixtures.settings, stats: Stats = Stats()) {
    val engine: Engine = Engine(settings, stats)
    val log: MutableList<String> = mutableListOf()

    fun at(now: Instant, event: Event): List<Effect> = engine.handle(event, now).filter {
        if (it is Effect.Log) log += it.line
        it !is Effect.Log
    }

    inline fun <reified T : Effect> List<Effect>.one(): T = filterIsInstance<T>().single()
}
