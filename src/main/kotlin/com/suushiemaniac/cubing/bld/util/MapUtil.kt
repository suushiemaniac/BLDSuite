package com.suushiemaniac.cubing.bld.util

object MapUtil {
    fun <K : Comparable<K>, V> Map<K, V>.sortedPrint() {
        this.keys.sorted().forEach {
            println("$it: ${this[it]}")
        }
    }

    fun <T : Number> Map<T, Int>.freqAverage(): Double {
        val criteriaHit = this.values.sum()
        val sum = this.entries.sumByDouble { it.key.toDouble() * it.value }

        return sum / criteriaHit
    }

    fun <K> MutableMap<K, Int>.increment(key: K) {
        this[key] = this.getOrDefault(key, 0) + 1
    }

    fun <K, V> Map<K, V?>.denullify(): Map<K, V> {
        return this.mapNotNull { (key, nullableVal) ->
            nullableVal?.let { key to it }
        }.toMap()
    }
}
