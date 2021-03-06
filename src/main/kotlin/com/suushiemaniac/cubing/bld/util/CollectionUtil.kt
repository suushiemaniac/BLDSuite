package com.suushiemaniac.cubing.bld.util

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.random.Random

object CollectionUtil {
    fun <T> List<T>.randomOrNull(): T? {
        return this.takeUnless { it.isEmpty() }
                ?.get(Random.nextInt(this.size))
    }

    fun <T> List<List<T>>.transpose(): List<List<T>> {
        val nonEmpty = this.filter { it.isNotEmpty() }

        if (nonEmpty.isEmpty()) {
            return emptyList()
        }

        val zipHead = nonEmpty.map { it.first() }
        return listOf(zipHead) + nonEmpty.map { it.drop(1) }.transpose()
    }

    fun Int.countingList(offset: Int = 0): List<Int> {
        return this.filledList { it + offset }
    }

    inline fun <reified T> Int.filledList(value: T): List<T> {
        return this.filledList { value }
    }

    inline fun <reified T> Int.filledList(value: (Int) -> T): List<T> {
        return List(this) { value(it) }
    }

    fun <T> List<T>.countOf(elem: T): Int {
        return this.count { it == elem }
    }

    fun <T> List<T>.takeWithTail(n: Int): Pair<List<T>, List<T>> {
        return this.take(n) to this.drop(n)
    }

    fun <T> List<T>.headWithTail(): Pair<T, List<T>> {
        return this.first() to this.drop(1)
    }

    fun <T> List<T>.headOrNullWithTail(): Pair<T?, List<T>> {
        return this.firstOrNull() to this.drop(1)
    }

    fun <T> Collection<T>.findByMnemonic(mnemonic: String): List<T> {
        return this.findByMnemonic(mnemonic) { toString() }
    }

    fun <T> Collection<T>.findByMnemonic(mnemonic: String, mapping: T.() -> String): List<T> {
        return this.filter { it.mapping().startsWith(mnemonic) }
    }

    fun <T> Collection<T>.mnemonic(value: T): String {
        return this.mnemonic(value) { toString() }
    }

    fun <T> Collection<T>.mnemonic(value: T, mapping: T.() -> String): String {
        val representation = value.mapping()

        for (i in 1 until representation.length) {
            val candidatePrefix = representation.substring(0, i)

            if (this.findByMnemonic(candidatePrefix, mapping).size == 1) {
                return candidatePrefix
            }
        }

        return representation
    }

    infix fun <T, V> Iterable<T>.allTo(v: V): List<Pair<T, V>> {
        return this.map { it to v }
    }

    infix fun <T, V> T.toEach(collect: Iterable<V>): List<Pair<T, V>> {
        return collect.map { this to it }
    }

    // TODO use library instead?
    fun <T> Collection<T>.powerset(): Set<Set<T>> = powerset(this, setOf(setOf()))

    private tailrec fun <T> powerset(left: Collection<T>, acc: Set<Set<T>>): Set<Set<T>> = when {
        left.isEmpty() -> acc
        else -> powerset(left.drop(1), acc + acc.map { it + left.first() })
    }

    fun <T> List<T>.permutations(): Set<List<T>> = when {
        isEmpty() -> emptySet()
        size == 1 -> setOf(listOf(this[0]))
        else -> {
            drop(1).permutations()
                    .flatMap { sublist -> (0..sublist.size).map { i -> sublist.plusAt(i, this[0]) } }
                    .toSet()
        }
    }

    internal fun <T> List<T>.plusAt(index: Int, element: T): List<T> = when (index) {
        !in 0..size -> throw Error("Cannot put at index $index because size is $size")
        0 -> listOf(element) + this
        size -> this + element
        else -> dropLast(size - index) + element + drop(index)
    }

    fun <T> Map<T, Set<T>>.topologicalSort(): List<T> {
        // remove reflexive self-dependencies
        val noReflexive = this.mapValues { it.value - it.key }

        // explicitly add empty sets (no dependencies) for all items that only occur as passive dependencies
        val extraItemsInDeps = noReflexive.values.flatten() - noReflexive.keys
        val extrasWithEmptyDeps = extraItemsInDeps.associateWith { emptySet<T>() }

        // compile graph search data
        val data = noReflexive + extrasWithEmptyDeps

        return consumeByGroups(data, emptyList())
                ?: error("A cyclic dependency exists amongst: ${data.toList().joinToString()}")
    }

    private tailrec fun <T> consumeByGroups(graph: Map<T, Set<T>>, accu: List<T>): List<T>? {
        val freeDepGroups = graph.filterValues { it.isEmpty() }

        if (freeDepGroups.isEmpty()) {
            return accu.takeUnless { graph.isNotEmpty() }
        }

        val noRemainingDependencies = freeDepGroups.keys
        val nextAccu = accu + noRemainingDependencies

        val remainingEntries = graph - noRemainingDependencies
        val remainingWithSortDeps = remainingEntries.mapValues { it.value - noRemainingDependencies }

        return consumeByGroups(remainingWithSortDeps, nextAccu)
    }

    fun <E> Int.asyncList(block: (Int) -> E): List<E> {
        return runBlocking {
            List(this@asyncList) {
                async {
                    block(it)
                }
            }.awaitAll()
        }
    }
}
