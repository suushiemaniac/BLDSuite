package com.suushiemaniac.cubing.bld.gsolve

import com.suushiemaniac.cubing.alglib.alg.Algorithm
import com.suushiemaniac.cubing.alglib.alg.SimpleAlg
import com.suushiemaniac.cubing.bld.analyze.BldAnalysis
import com.suushiemaniac.cubing.bld.model.cycle.MisOrientPiece
import com.suushiemaniac.cubing.bld.model.cycle.ParityCycle
import com.suushiemaniac.cubing.bld.model.cycle.PieceCycle
import com.suushiemaniac.cubing.bld.model.cycle.ThreeCycle
import com.suushiemaniac.cubing.bld.model.enumeration.piece.PieceType
import com.suushiemaniac.cubing.bld.model.source.AlgSource
import com.suushiemaniac.cubing.bld.optim.BreakInOptimizer
import com.suushiemaniac.cubing.bld.util.CollectionUtil.combinations
import com.suushiemaniac.cubing.bld.util.CollectionUtil.permutations
import com.suushiemaniac.cubing.bld.util.CollectionUtil.countingList
import com.suushiemaniac.cubing.bld.util.PuzzleState
import com.suushiemaniac.cubing.bld.util.clone
import com.suushiemaniac.cubing.bld.util.deepEquals

import java.io.File

open class GPuzzle(kCommandMap: Map<String, Map<String, List<String>>>, private val commandMap: Map<String, Map<String, List<String>>>) : KPuzzle(kCommandMap) {
    constructor(kCommandMap: Map<String, Map<String, List<String>>>, bldFile: File): this(kCommandMap, groupByCommand(bldFile.readLines()))
    constructor(defFile: File, bldFile: File): this(groupByCommand(defFile.readLines()), groupByCommand(bldFile.readLines()))

    val letterSchemes = this.loadLetterSchemes()

    val mainBuffers = this.loadBuffers()
    //val backupBuffers = this.getPieceTypes() alwaysTo { mutableListOf<Int>() }

    val reorientMethod = this.loadReorientMethod()
    val reorientState = this.loadReorientState()

    var algSource: AlgSource? = null
    val optimizer by lazy { BreakInOptimizer(this.algSource!!, *this.pieceTypes.toTypedArray(), fullCache = false) }

    val avoidBreakIns = this.pieceTypes.associateWith { true }
    val optimizeBreakIns = this.pieceTypes.associateWith { true }

    private val bruteForceRotations by lazy {
        // TODO consider not only 2-combinations
        val reorientations = this.moveDefinitions.keys.filter { it.plane.isRotation }.toSet().combinations(2)
        val nonCancelling = reorientations.map { SimpleAlg(it.toList()) }.toSet()

        nonCancelling.flatMap { it.allMoves().permutations() }.map { SimpleAlg(it) }.toSet()
    }

    fun getMainBufferTarget(type: PieceType) = this.mainBuffers.getValue(type)
    fun getMainBufferPerm(type: PieceType) = this.targetToPiece(type, this.getMainBufferTarget(type)).first

    fun pieceToTarget(type: PieceType, perm: Int, orient: Int) = (perm * type.targetsPerPiece) + orient
    fun targetToPiece(type: PieceType, target: Int) = Pair(target / type.targetsPerPiece, target % type.targetsPerPiece)
    fun targetToLetter(type: PieceType, target: Int) = this.letterSchemes.getValue(type)[target]

    fun getBufferLetter(type: PieceType) = this.targetToLetter(type, this.mainBuffers.getValue(type))

    fun permutationToTargets(type: PieceType, perm: Int) = (0 until type.targetsPerPiece)
            .map { this.pieceToTarget(type, perm, it) }
            .toTypedArray()

    fun getBufferPermTargets(type: PieceType) = this.permutationToTargets(type, this.getMainBufferPerm(type))
    fun getCurrentBufferOrientation(type: PieceType) = this.getCurrentOrientation(type, this.getMainBufferPerm(type))

    fun getLetterPairCorrespondants(type: PieceType, perm: Int) = this.permutationToTargets(type, perm)
            .map { this.targetToLetter(type, it) }

    protected fun getCurrentOrientation(type: PieceType, perm: Int) = this.puzzleState.getValue(type).second[perm]

    protected fun currentlyAtTarget(type: PieceType, target: Int): Int { // FIXME is this working?
        val (lookupPerm, lookupOrient) = this.targetToPiece(type, target)
        val (statePerm, stateOrient) = this.puzzleState.getValue(type)

        return this.pieceToTarget(type, statePerm[lookupPerm], (stateOrient[lookupPerm] + lookupOrient) % type.targetsPerPiece)
    }

    protected fun currentTargetInBuffer(type: PieceType) = this.currentlyAtTarget(type, this.getMainBufferTarget(type))

    protected fun getSolutionSpots(type: PieceType, target: Int): List<Int> { // FIXME is this working?
        val (currentPerm, currentOrient) = this.targetToPiece(type, this.currentlyAtTarget(type, target))
        val (refPerm, refOrient) = this.solvedState.getValue(type)

        val possiblePermSpots = refPerm.indices.filter { refPerm[it] == currentPerm }

        return possiblePermSpots.map { this.pieceToTarget(type, it, (refOrient[it] + currentOrient) % type.targetsPerPiece) }
    }

    protected fun getNextTargetSolutionSpots(type: PieceType) = this.getSolutionSpots(type, this.getMainBufferTarget(type))

    protected fun targetCurrentlySolved(type: PieceType, target: Int) = target in this.getSolutionSpots(type, this.currentlyAtTarget(type, target))
    protected fun bufferCurrentlySolved(type: PieceType) = this.targetCurrentlySolved(type, this.getMainBufferTarget(type))

    protected fun getHypotheticalState(scramble: Algorithm): PuzzleState {
        val clonedState = this.puzzleState.clone()
        scramblePuzzle(clonedState, scramble, this.moveDefinitions)

        return clonedState
    }

    protected open fun compilePermuteSolutionCycles(type: PieceType): List<PieceCycle> { // TODO!!
        val currentCycles = this.cycles.getValue(type)
        val mainBuffer = this.mainBuffers.getValue(type)

        var currentBuffer = mainBuffer
        val bufferFloats = this.bufferFloats.getValue(type)

        val cycles = mutableListOf<PieceCycle>()

        for (c in currentCycles.indices.chunked(2)) {
            if (c[0] in bufferFloats.keys) {
                currentBuffer = bufferFloats.getValue(c[0])
            }

            if (c.size == 2) {
                cycles.add(ThreeCycle(currentBuffer, currentCycles[c[0]], currentCycles[c[1]]))
            } else {
                cycles.add(ParityCycle(currentBuffer, currentCycles[c[0]]))
            }
        }

        return cycles
    }

    fun compileSolutionCycles(type: PieceType): List<PieceCycle> { // TODO!!
        val mainBuffer = this.mainBuffers.getValue(type)

        val cycles = mutableListOf<PieceCycle>()
        cycles.addAll(this.compilePermuteSolutionCycles(type))

        for (i in 1 until type.targetsPerPiece) {
            val misOrients = this.getMisOrientedPieces(type, i)

            when (this.misOrientMethod) {
                "Single" -> {
                    for (piece in misOrients) {
                        val (perm, orient) = this.targetToPiece(type, piece)
                        val next = this.pieceToTarget(type, perm, (orient + i) % type.targetsPerPiece)

                        cycles.add(ThreeCycle(mainBuffer, piece, next))
                    }
                }
                "Compound" ->
                    cycles.addAll(misOrients.map { MisOrientPiece(it, i) })
            }
        }

        return cycles
    }

    fun getAnalysis(scramble: Algorithm): BldAnalysis {
        this.applyScramble(scramble, true)

        // TODO resolve parities (according to dependency definitions)

        val reorient = this.getReorientationMoves()
        val cycles = this.pieceTypes.associateWith { this.compileSolutionCycles(it) }

        return BldAnalysis(this, scramble, reorient, cycles, this.letterSchemes, this.algSource)
    }

    fun getNextTarget(type: PieceType): Int? { // TODO how to handle null return?
        val avoidBreakIns = this.avoidBreakIns.getValue(type)

        return if (this.bufferCurrentlySolved(type)) {
            val targetedPieces = this.getTargetedPieces(type)
            val lastTarget = this.getLastTarget(type)

            // TODO mark buffer float?

            this.getBreakInTargetsAfter(type, lastTarget, targetedPieces).find { !this.targetCurrentlySolved(type, it) }
        } else {
            this.getNextTargetSolutionSpots(type).find { // TODO use "current" buffer (according to floats) instead of "main" buffer?
                val alternativeSolved = this.targetCurrentlySolved(type, it)
                val alternativeSuitable = this.getMainBufferTarget(type) !in this.getSolutionSpots(type, it)

                !alternativeSolved && (!avoidBreakIns || alternativeSuitable)
            }
        }
    }

    protected open fun getBreakInTargetsAfter(type: PieceType, piece: Int, targetedPieces: List<Int>): List<Int> {
        if (this.algSource == null || !this.optimizeBreakIns.getValue(type)) {
            //return this.targetMap.getValue(type).sortedBy { it.min() }.reduce { a, b -> a + b }.toList()
            return type.numTargets.countingList() // TODO only use/return unsolved by default
        }

        return this.optimizer.optimizeBreakInTargetsAfter(piece, type)
    }

    protected fun getReorientationMoves() = when {
        this.reorientMethod == "Fixed" -> this.bruteForceRotations.find { this.getHypotheticalState(it).deepEquals(this.reorientState) } // TODO deepEquals with wildcards
                ?: SimpleAlg()
        this.reorientMethod == "Dynamic" -> this.bruteForceRotations.maxBy {
            // TODO consider no rotations at all
            val rotatedState = this.getHypotheticalState(it)

            val solvedCenters = rotatedState.countEquals(this.solvedState) // TODO implement template counting
            val solvedBadCenters = rotatedState.countEquals(this.reorientState)

            2 * solvedCenters + 3 * solvedBadCenters // FIXME is this an appropriate / intuitive weighting?
        } ?: SimpleAlg()
        else -> SimpleAlg()
    }

    fun solves(type: PieceType, alg: Algorithm, case: PieceCycle, pure: Boolean = true): Boolean {
        return false // FIXME
    }
}
