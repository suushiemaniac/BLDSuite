package com.suushiemaniac.cubing.bld.util

import java.security.SecureRandom

object CollectionUtil {
    private val secRandom = SecureRandom()

    fun <T> List<T>.randomOrNull(): T? {
        if (this.isEmpty()) return null

        return this[secRandom.nextInt(this.size)]
    }

    fun Int.countingList(): List<Int> {
        return List(this) { it }
    }

    fun <T> Collection<T>.findByMnemonic(mnemonic: String): List<T> {
        return this.filter { it.toString().startsWith(mnemonic) }
    }

    fun <T> Collection<T>.mnemonic(value: T): String {
        val representation = value.toString()

        for (i in 1 until representation.length) {
            val candidatePrefix = value.toString().substring(0, i)

            if (this.findByMnemonic(candidatePrefix).size == 1) {
                return candidatePrefix
            }
        }

        return representation
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

    // TODO use library instead (is there any?)
    fun <T> Map<T, Set<T>>.topologicalSort(): List<T> {
        // remove self-dependencies
        val data = this.mapValues { it.value - it.key }.toMutableMap()

        // explicitly add empty sets (no dependencies) for all items that only occur as passive dependencies
        val extraItemsInDeps = data.values.flatten() - data.keys
        data += extraItemsInDeps.associateWith { emptySet<T>() }

        // result accumulator
        val resGroups = mutableListOf<T>()

        while (data.values.any { it.isEmpty() }) {
            val noRemainingDependencies = data.filterValues { it.isEmpty() }.keys

            resGroups += noRemainingDependencies

            data.keys.removeAll(noRemainingDependencies)
            data.entries.forEach { it.setValue(it.value - noRemainingDependencies) }
        }

        if (data.isNotEmpty()) {
            throw Exception("A cyclic dependency exists amongst: ${data.toList().joinToString()}")
        }

        return resGroups
    }
}