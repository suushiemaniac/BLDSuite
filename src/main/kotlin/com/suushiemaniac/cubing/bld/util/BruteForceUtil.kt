package com.suushiemaniac.cubing.bld.util

import com.suushiemaniac.cubing.alglib.alg.Algorithm
import com.suushiemaniac.cubing.alglib.alg.SimpleAlg
import com.suushiemaniac.cubing.alglib.move.Move
import com.suushiemaniac.cubing.bld.gsolve.GPuzzle
import com.suushiemaniac.cubing.bld.model.PieceType
import com.suushiemaniac.cubing.bld.util.CollectionUtil.countingList

object BruteForceUtil {
    fun <T> Iterable<T>.permute(length: Int, inclusive: Boolean = false, mayRepeat: Boolean = false): Sequence<List<T>> {
        when {
            length < 1 -> return emptySequence()
            length == 1 -> return this.asSequence().map { listOf(it) }
            else -> return generateSequence {
                if (inclusive)
                    for (i in 1 until length)
                        for (subPerm in permute(i, false, mayRepeat))
                            return@generateSequence subPerm

                for (prevPermute in this.permute(length - 1, false, mayRepeat)) {
                    for (blockObj in this) {
                        if (mayRepeat || !prevPermute.contains(blockObj)) {
                            val nextPermute = prevPermute.toMutableList()
                            nextPermute.add(blockObj)

                            return@generateSequence nextPermute
                        }
                    }
                }

                return@generateSequence null
            }
        }
    }

    fun <T> Array<T>.permute(length: Int, inclusive: Boolean = false, mayRepeat: Boolean = false) = asList().permute(length, inclusive, mayRepeat)

    fun bruteForceAlg(analyze: GPuzzle, cycles: List<PieceCycle>, type: PieceType, alphabet: Array<Move>, prune: Int = 21): List<Algorithm> {
        val accumulator = mutableListOf<Algorithm>()

        for (len in 1 until prune) {
            val moves = alphabet.permute(len, inclusive = false, mayRepeat = true).map { SimpleAlg(it) }

            for (alg in moves) {
                if (analyze.solves(type, alg, cycles, false)) {
                    accumulator.add(alg)
                }
            }
        }

        return accumulator
    }

    fun PieceType.fullCycles(buffer: Int): List<PieceCycle> {
        val singleTargets = this.permutations.countingList().flatMap { p ->
            this.orientations.countingList().mapNotNull { o ->
                val targetSticker = GPuzzle.pieceToTarget(this, p, o)

                targetSticker.takeUnless { it == buffer }?.let { StickerTarget(it, buffer) }
            }
        }

        return singleTargets.permute(2).toList()
    }
}