package com.suushiemaniac.cubing.bld.filter

import com.suushiemaniac.cubing.alglib.alg.Algorithm
import com.suushiemaniac.cubing.alglib.lang.NotationReader
import com.suushiemaniac.cubing.bld.analyze.BldAnalysis
import com.suushiemaniac.cubing.bld.filter.condition.BooleanCondition
import com.suushiemaniac.cubing.bld.filter.condition.IntegerCondition
import com.suushiemaniac.cubing.bld.model.PieceType
import com.suushiemaniac.cubing.bld.model.AlgSource
import com.suushiemaniac.cubing.bld.util.StringUtil.guessRegExpRange
import com.suushiemaniac.cubing.bld.util.StringUtil.toCharStrings
import com.suushiemaniac.cubing.bld.util.BruteForceUtil.permute
import com.suushiemaniac.cubing.bld.util.StickerTarget
import kotlin.math.max
import kotlin.math.min

class ConditionsBundle(protected val pieceType: PieceType, val targets: IntegerCondition = IntegerCondition.ANY(), val breakIns: IntegerCondition = IntegerCondition.ANY(), val preSolved: IntegerCondition = IntegerCondition.ANY(), val misOriented: IntegerCondition = IntegerCondition.ANY(), val parity: BooleanCondition = BooleanCondition.MAYBE(), val bufferSolved: BooleanCondition = BooleanCondition.MAYBE(), val isAllowTwistedBuffer: Boolean = ALLOW_TWISTED_BUFFER) {
    init {
        this.balanceProperties()
    }

    protected var memoRegex: String = REGEX_UNIV
    protected var predicateRegex: String = REGEX_UNIV
    protected var letterPairRegex: String = REGEX_UNIV

    protected var statisticalPredicate: (BldAnalysis) -> Boolean = { true }

    val statString: String
        get() = this.pieceType.humanName + ": " + (if (this.parity.positive) "_" else "") +
                (if (this.parity.isImportant) "! " else "? ") +
                this.targets.toString() +
                " " +
                (if (this.bufferSolved.positive) if (this.isAllowTwistedBuffer) "**" else "*" else "") +
                (if (this.bufferSolved.isImportant) "! " else "? ") +
                this.breakIns.toString("#") +
                " " +
                this.misOriented.toString("~") +
                " " +
                this.preSolved.toString("+")

    private fun balanceTargets() {
        this.targets.capMin(0)
        // C=10 E=16 W=34 XC=34 TC=34
        this.targets.capMax(this.pieceType.permutationsNoBuffer / 2 * 3 + this.pieceType.permutationsNoBuffer % 2)

        // pre-solved
        // mis-orient
        // parity
    }

    private fun balanceBreakIns() {
        // C=7 E=11 W=23 XC=23 TC=23
        this.breakIns.capMin(Math.max(if (this.bufferSolved.negative) 1 else 0, this.targets.getMin() - this.pieceType.permutationsNoBuffer))
        // C=3 E=5 W=11 XC=11 TC=11
        this.breakIns.capMax(this.pieceType.permutationsNoBuffer / 2)

        // targets
    }

    private fun balanceParity() {
        if (this.targets.isPrecise) {
            this.parity.define(this.targets.getMax() % 2 == 1)
        }
    }

    private fun balanceBufferSolved() {
        // break-ins
    }

    private fun balanceSolvedMisOriented() {
        // C=7 E=11 W=23 XC=23 TC=23
        val leftOverMin = max(0, this.pieceType.permutationsNoBuffer + this.breakIns.getMax() - this.targets.getMax())
        val leftOverMax = min(this.pieceType.permutationsNoBuffer, this.pieceType.permutationsNoBuffer + this.breakIns.getMin() - this.targets.getMin())

        this.preSolved.capMin(0)
        this.misOriented.capMin(0)

        // C=8 E=12 W=? XC=? TC=?
        this.preSolved.capMax(pieceType.permutations)
        // C=8 E=12 W=? XC=? TC=?
        this.misOriented.capMax(pieceType.permutations)

        val sumMin = this.preSolved.getMin() + this.misOriented.getMin()
        val sumMax = this.preSolved.getMax() + this.misOriented.getMax()

        if (sumMin > leftOverMax) {
            if (this.preSolved.isPrecise || !this.misOriented.isPrecise) {
                this.misOriented.setMin(this.misOriented.getMin() - sumMin + leftOverMax)
            }

            if (this.misOriented.isPrecise || !this.preSolved.isPrecise) {
                this.preSolved.setMin(this.preSolved.getMin() - sumMin + leftOverMax)
            }
        } else if (sumMax < leftOverMin) {
            if (this.preSolved.isPrecise || !this.misOriented.isPrecise) {
                this.misOriented.setMax(this.misOriented.getMax() + leftOverMin - sumMax)
            }

            if (this.misOriented.isPrecise || !this.preSolved.isPrecise) {
                this.preSolved.setMax(this.misOriented.getMax() + leftOverMin - sumMax)
            }
        }

        // targets
    }

    fun balanceProperties() {
        this.balanceTargets()
        this.balanceBufferSolved()
        this.balanceBreakIns()
        this.balanceParity()
        this.balanceSolvedMisOriented()
    }

    fun setLetterPairRegex(letteringScheme: Array<String>, pairs: List<String>, conjunctive: Boolean = true, allowInverse: Boolean = false) {
        var regexPairs = pairs
        val letters = letteringScheme.joinToString("")
        var row = letters.guessRegExpRange()

        if (row == letters) {
            row = "[" + Regex.escape(row) + "]"
        }

        val anyLP = "$row{2}"
        val kleeneLP = "($anyLP)*"

        if (allowInverse) {
            regexPairs = regexPairs.map { pair -> "([$pair]{2})" }
        }

        val glue = if (conjunctive) kleeneLP else "|"
        val pieces = if (conjunctive) List(regexPairs.size) { "(" + regexPairs.joinToString("|") + ")" } else regexPairs
        var joined = pieces.joinToString(glue)

        if (!conjunctive) {
            joined = "($joined)"
        }

        this.letterPairRegex = kleeneLP + joined + kleeneLP
    }

    fun setPredicateRegex(algSource: AlgSource, reader: NotationReader, filter: (Algorithm) -> Boolean) {
        val matches = sortedSetOf<String>()
        val alphabet = ('A'..'Z').map { it.toString() }.toList() // FIXME better permutation of actual targets instead of implicit derivation through lettering

        val possPairs = alphabet.permute(2, inclusive = false, mayRepeat = false).map { it.joinToString("") }

        for (pair in possPairs) {
            val targetIndices = pair.toCharStrings().map { alphabet.indexOf(it) }
            val case = targetIndices.map { StickerTarget(it, 0) } // TODO buffer!

            matches.addAll(algSource.getAlgorithms(this.pieceType, reader, case)
                    .filter(filter)
                    .map { pair })
        }

        if (matches.size > 0) {
            this.predicateRegex = "(" + matches.joinToString("|") + ")*" + if (this.parity.positive) "[A-Z]?" else ""
        }
    }

    fun matchingConditions(inCube: BldAnalysis): Boolean {
        return (inCube.pieceTypes.contains(this.pieceType)
                && this.parity.evaluate(inCube.hasParity(this.pieceType))
                && this.bufferSolved.evaluate(inCube.isBufferSolved(this.pieceType, this.isAllowTwistedBuffer))
                && this.breakIns.evaluate(inCube.getBreakInCount(this.pieceType))
                && this.targets.evaluate(inCube.getTargetCount(this.pieceType))
                && this.preSolved.evaluate(inCube.getPreSolvedCount(this.pieceType))
                && this.misOriented.evaluate(inCube.getMisOrientedCount(this.pieceType))
                && inCube.compileSolutionTargetString(this.pieceType).matches(this.memoRegex.toRegex()) // TODO have regular expressions comply w/ buffer floats!
                && inCube.compileSolutionTargetString(this.pieceType).matches(this.letterPairRegex.toRegex())
                && inCube.compileSolutionTargetString(this.pieceType).matches(this.predicateRegex.toRegex())
                && this.statisticalPredicate(inCube))
    }

    override fun toString() = this.statString

    companion object {
        const val REGEX_UNIV = ".*"

        private var ALLOW_TWISTED_BUFFER = true

        fun setGlobalAllowTwistedBuffer(allowTwistedBuffer: Boolean) {
            ALLOW_TWISTED_BUFFER = allowTwistedBuffer
        }
    }
}
