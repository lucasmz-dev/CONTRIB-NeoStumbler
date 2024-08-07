package xyz.malkki.neostumbler.extensions

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration

fun <A, B> Flow<Pair<A?, B?>>.filterNotNullPairs(): Flow<Pair<A, B>> = filter { it.first != null && it.second != null }.map { it.first!! to it.second!! }

fun <A, B, C> Flow<A>.combineAny(other: Flow<B>, combiner: suspend (A?, B?) -> C): Flow<C> = channelFlow {
    var latestA: A? = null
    var latestB: B? = null

    launch {
        collect {
            latestA = it

            send(combiner(latestA, latestB))
        }
    }
    launch {
        other.collect {
            latestB = it

            send(combiner(latestA, latestB))
        }
    }
}

fun <A, B, C, D> Flow<A>.combineAny(other1: Flow<B>, other2: Flow<C>, combiner: suspend (A?, B?, C?) -> D): Flow<D> = channelFlow {
    var latestA: A? = null
    var latestB: B? = null
    var latestC: C? = null

    launch {
        collect {
            latestA = it

            send(combiner(latestA, latestB, latestC))
        }
    }
    launch {
        other1.collect {
            latestB = it

            send(combiner(latestA, latestB, latestC))
        }
    }
    launch {
        other2.collect {
            latestC = it

            send(combiner(latestA, latestB, latestC))
        }
    }
}

inline fun <reified A, B> Collection<Flow<A>>.combineAny(crossinline combiner: suspend (Array<A?>) -> B): Flow<B> = channelFlow {
    val values = arrayOfNulls<A?>(size)

    forEachIndexed { index, flow ->
        launch {
            flow.collect {
                values[index] = it

                send(combiner(values.copyOf()))
            }
        }
    }
}

fun <T> Flow<T>.buffer(window: Duration): Flow<List<T>> = channelFlow {
    val items: MutableList<T> = mutableListOf<T>()
    var finished = false

    launch {
        collect {
            items.add(it)
        }

        finished = true
    }

    while (true) {
        delay(window)

        send(items.toList())
        items.clear()

        if (finished) {
            break
        }
    }
}

fun <T, R> Flow<T>.parallelMap(
    context: CoroutineContext = EmptyCoroutineContext,
    transform: suspend (T) -> R
): Flow<R> {
    val scope = CoroutineScope(context + SupervisorJob())

    return map {
            scope.async { transform(it) }
        }
        .buffer()
        .map { it.await() }
        .flowOn(context)
}