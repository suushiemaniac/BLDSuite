package com.suushiemaniac.cubing.bld.analyze

import com.suushiemaniac.cubing.alglib.alg.Algorithm
import com.suushiemaniac.cubing.alglib.alg.SimpleAlg
import com.suushiemaniac.cubing.alglib.lang.ImageStringReader
import com.suushiemaniac.cubing.alglib.move.Move
import com.suushiemaniac.cubing.bld.model.enumeration.piece.LetterPairImage
import com.suushiemaniac.cubing.bld.model.enumeration.piece.PieceType
import com.suushiemaniac.cubing.bld.model.enumeration.puzzle.TwistyPuzzle
import com.suushiemaniac.cubing.bld.model.source.AlgSource
import com.suushiemaniac.cubing.bld.util.ArrayUtil.countingArray
import com.suushiemaniac.cubing.bld.util.ArrayUtil.cycleLeft
import com.suushiemaniac.cubing.bld.util.ArrayUtil.deepInnerIndex
import com.suushiemaniac.cubing.bld.util.ArrayUtil.deepOuterIndex
import com.suushiemaniac.cubing.bld.util.ArrayUtil.filledArray
import com.suushiemaniac.cubing.bld.util.MapUtil.allTo
import com.suushiemaniac.cubing.bld.util.MapUtil.alwaysTo
import com.suushiemaniac.cubing.bld.util.MapUtil.reset
import com.suushiemaniac.cubing.bld.util.MapUtil.increment
import com.suushiemaniac.cubing.bld.util.SpeffzUtil
import com.suushiemaniac.lang.json.JSON
import java.util.*
import kotlin.math.max

abstract class BldPuzzle(val model: TwistyPuzzle) : Cloneable {
    constructor(model: TwistyPuzzle, scramble: Algorithm) : this(model) {
        this.parseScramble(scramble)
    }

    val permutations = this.loadPermutations()
    val cubies = this.getDefaultCubies() - this.getOrientationPieceTypes()

    val state = initState()
    val lastScrambledState = initState().toMutableMap()

    val cycles = this.getPieceTypes() alwaysTo { mutableListOf<Int>() }
    val cycleCount = (this.getPieceTypes() alwaysTo 0).toMutableMap()

    val solvedPieces = this.getPieceTypes() allTo { it.numPieces.filledArray(false) }
    val preSolvedPieces = this.getPieceTypes() allTo { it.numPieces.filledArray(false) }
    val misOrientedPieces = this.getPieceTypes() allTo { pt -> pt.targetsPerPiece.filledArray { pt.numPieces.filledArray(false) } }

    val parities = (this.getPieceTypes() alwaysTo false).toMutableMap()

    var scramble: Algorithm = SimpleAlg()
    var scrambleOrientationPremoves: Algorithm = SimpleAlg()

    var letterPairLanguage = System.getProperty("user.language")

    val letterSchemes = (this.getPieceTypes() alwaysTo SpeffzUtil.FULL_SPEFFZ).toMutableMap()
    val avoidBreakIns = this.getPieceTypes() alwaysTo true
    val optimizeBreakIns = this.getPieceTypes() alwaysTo true

    val mainBuffers = (this.getPieceTypes() allTo this::getBuffer).toMutableMap()
    val backupBuffers = this.getPieceTypes() alwaysTo { mutableListOf<Int>() }
    val bufferFloats = this.getPieceTypes() alwaysTo { mutableMapOf<Int, Int>() }

    var algSource: AlgSource? = null
    var misOrientMethod = MisOrientMethod.SOLVE_DIRECT

    fun loadPermutations(): Map<Move, Map<PieceType, Array<Int>>> {
        val filename = "permutations/$model.json"
        val fileURL = this.javaClass.getResource(filename)

        val json = JSON.fromURL(fileURL)

        val permutations = hashMapOf<Move, Map<PieceType, Array<Int>>>()

        for (key in json.nativeKeySet()) {
            val typeMap = hashMapOf<PieceType, Array<Int>>()
            val moveJson = json.get(key)

            for (type in this.getPieceTypes(true)) {
                val permutationList = moveJson.get(type.name).nativeList(JSON::intValue)
                val permutationArray = permutationList.toTypedArray()

                typeMap[type] = permutationArray
            }

            val move = model.reader.parse(key).firstMove()
            permutations[move] = typeMap
        }

        return permutations
    }

    protected fun initState(): Map<PieceType, Array<Int>> {
        return this.getPieceTypes(true) allTo { (it.numPieces * it.targetsPerPiece).countingArray() }
    }

    protected fun saveState(type: PieceType) {
        val current = this.state.getValue(type)
        this.lastScrambledState[type] = current.copyOf()
    }

    open fun parseScramble(scramble: Algorithm) {
        this.resetPuzzle()

        this.scramble = scramble

        this.scramblePuzzle(scramble)
        this.solvePuzzle()
    }

    fun reSolve() {
        val current = this.scramble
        this.parseScramble(current)
    }

    fun getPieceTypes(withOrientationModel: Boolean = false): List<PieceType> {
        val pieceTypes = this.getPermutationPieceTypes().toMutableList()

        if (withOrientationModel) {
            pieceTypes.addAll(this.getOrientationPieceTypes())
        }

        return pieceTypes
    }

    protected fun resetPuzzle() {  // TODO make more concise / beautiful
        this.state.forEach { (_, state) -> state.sort() }
        this.lastScrambledState.forEach { (_, state) -> state.sort() }

        this.cycles.forEach { (_, state) -> state.clear() }
        this.cycleCount.reset { 0 }

        this.solvedPieces.forEach { (_, state) -> state.fill(false) }
        this.preSolvedPieces.forEach { (_, state) -> state.fill(false) }
        this.misOrientedPieces.forEach { (_, state) -> state.forEach { it.fill(false) } }

        this.parities.reset { false }

        this.mainBuffers.forEach { (type, newBuffer) -> this.cycleCubiesForBuffer(type, newBuffer) }
        this.bufferFloats.forEach { (_, state) -> state.clear() }
    }

    fun solvePuzzle() {
        this.getOrientationPieceTypes().forEach { this.saveState(it) }

        this.reorientPuzzle()
        this.getPieceTypes().forEach { this.saveState(it) }
        this.getPieceTypes().forEach { this.solvePieces(it) }
    }

    protected fun reorientPuzzle() {
        this.scrambleOrientationPremoves = this.getReorientationMoves()
        this.scrambleOrientationPremoves.forEach(this::permute)
    }

    open protected fun scramblePuzzle(scramble: Algorithm) {
        val preMoveScramble = this.getSolvingOrientationPremoves().merge(scramble)

        preMoveScramble.asIterable()
                .filter(this.permutations.keys::contains)
                .forEach(this::permute)
    }

    protected fun permute(permutation: Move) {
        for (type in this.getPieceTypes(true)) {
            val current = this.state.getValue(type)
            val perm = this.permutations.getValue(permutation).getValue(type)

            this.applyPermutations(current, perm)
        }
    }

    protected fun applyPermutations(current: Array<Int>, perm: Array<Int>) {
        val exchanges = perm.size.filledArray(-1)

        for (i in exchanges.indices) {
            if (perm[i] != -1) {
                exchanges[perm[i]] = current[i]
            }
        }

        for (i in exchanges.indices) {
            if (exchanges[i] != -1) {
                current[i] = exchanges[i]
            }
        }
    }

    protected open fun getBreakInPermutationsAfter(piece: Int, type: PieceType): List<Int> {
        val targetCount = type.numPieces
        return targetCount.countingArray().asList().subList(1, targetCount)
    }

    protected open fun getBreakInOrientationsAfter(piece: Int, type: PieceType): Int {
        return 0
    }

    fun getBuffer(type: PieceType): Int {
        return if (type is LetterPairImage) 0 else this.cubies.getValue(type)[0][0]
    }

    protected fun cycleCubiesForBuffer(type: PieceType, newBuffer: Int): Boolean {
        val cubies = this.cubies[type]

        if (cubies != null) {
            val outer = cubies.deepOuterIndex(newBuffer)

            if (outer > -1) {
                val inner = cubies.deepInnerIndex(newBuffer)

                if (inner > -1) {
                    for (i in 0 until outer) cubies.cycleLeft()
                    for (i in 0 until inner) cubies[0].cycleLeft()

                    return true
                }
            }
        }

        return false
    }

    protected fun increaseCycleCount(type: PieceType) {
        this.cycleCount.increment(type)
    }

    protected fun getLastTarget(type: PieceType): Int {
        return this.cycles.getValue(type).lastOrNull() ?: -1
    }

    fun getSolutionRaw(type: PieceType): String {
        val currentCycles = this.cycles.getValue(type)
        val bufferFloats = this.bufferFloats.getValue(type)

        val letters = this.getLetteringScheme(type)

        if (currentCycles.size > 0) {
            val solutionRaw = StringBuilder()

            for (i in currentCycles.indices) {
                if (bufferFloats.containsKey(i)) {
                    val bufferLetter = letters[bufferFloats.getValue(i)]
                    val position = SpeffzUtil.speffzToSticker(SpeffzUtil.normalize(bufferLetter, letters), type)

                    solutionRaw.append("(").append(position).append(")")
                }

                solutionRaw.append(letters[currentCycles[i]])
            }

            return solutionRaw.toString().trim { it <= ' ' }
        } else {
            return "Solved"
        }
    }

    fun getSolutionRaw(withRotation: Boolean = false): String {
        val solutionParts = mutableListOf<String>()

        if (withRotation) {
            solutionParts.add("Rotations: " + if (this.scrambleOrientationPremoves.algLength() > 0) this.scrambleOrientationPremoves.toFormatString() else "/")
        }

        solutionParts += this.getExecutionOrderPieceTypes()
                .map { "${it.humanName}: ${getSolutionRaw(it)}" }

        return solutionParts.joinToString("\n")
    }

    fun getSolutionPairs(type: PieceType): String {
        val pairs = StringBuilder()

        val currentCycles = this.cycles.getValue(type)
        val letters = this.letterSchemes.getValue(type)

        val bufferFloats = this.bufferFloats.getValue(type)

        if (currentCycles.size > 0 || this.getMisOrientedCount(type) > 0) {
            for (i in currentCycles.indices) {
                if (bufferFloats.containsKey(i)) {
                    val bufferLetter = letters[bufferFloats.getValue(i)]
                    val position = SpeffzUtil.speffzToSticker(SpeffzUtil.normalize(bufferLetter, letters), type)

                    pairs.append("(float ")
                            .append(position)
                            .append(") ")
                }

                pairs.append(letters[currentCycles[i]])

                if (i % 2 == 1) {
                    pairs.append(" ")
                }
            }

            pairs.append(if (pairs.toString().endsWith(" ")) "" else " ")
            pairs.append(this.getRotationSolutions(type))
        } else {
            return "Solved"
        }

        return pairs.toString().trim { it <= ' ' }
    }

    fun getSolutionPairs(withRotation: Boolean = false): String {
        val solutionParts = mutableListOf<String>()

        if (withRotation) {
            solutionParts.add("Rotations: " + if (this.scrambleOrientationPremoves.algLength() > 0) this.scrambleOrientationPremoves.toFormatString() else "/")
        }

        solutionParts += this.getExecutionOrderPieceTypes()
                .map { "${it.humanName}: ${getSolutionPairs(it)}" }

        return solutionParts.joinToString("\n")
    }

    fun getSolutionAlgorithms(type: PieceType): String {
        if (this.algSource == null || !this.algSource!!.isReadable) {
            return ""
        }

        val pairs = StringBuilder()

        val currentCycles = this.cycles.getValue(type)

        if (currentCycles.size > 0 || this.getMisOrientedCount(type) > 0) { // TODO alg sources buffer support
            var i = 0
            while (i < currentCycles.size) {
                if (i + 1 >= currentCycles.size) {
                    i += 2
                    continue
                }

                val pair = this.getLetteringScheme(type)[currentCycles[i]] + this.getLetteringScheme(type)[currentCycles[i + 1]]

                val caseAlgs = this.algSource!!.getAlgorithms(type, pair)
                var thisAlg: Algorithm = ImageStringReader().parse("LOL")

                if (caseAlgs.isNotEmpty()) {
                    val listAlgs = ArrayList(caseAlgs)
                    listAlgs.shuffle() // FIXME better use of alternative algs
                    thisAlg = listAlgs[0]
                }

                pairs.append(thisAlg.toFormatString())
                pairs.append("\n")
                i += 2
            }

            pairs.append(if (pairs.toString().endsWith(" ")) "" else " ")
            pairs.append(this.getRotationSolutions(type))
        } else {
            return "Solved"
        }

        return pairs.toString().trim { it <= ' ' }
    }

    fun getSolutionAlgorithms(withRotation: Boolean = false): String {
        val solutionParts = mutableListOf<String>()

        if (withRotation) {
            solutionParts.add("Rotations: " + if (this.scrambleOrientationPremoves.algLength() > 0) this.scrambleOrientationPremoves.toFormatString() else "/")
        }

        solutionParts += this.getExecutionOrderPieceTypes()
                .map { "${it.humanName}:\n${getSolutionAlgorithms(it)}" }

        return solutionParts.joinToString("\n")
    }

    fun getRawSolutionAlgorithm(type: PieceType): String {
        if (this.algSource == null || !this.algSource!!.isReadable) {
            return ""
        }

        var finalAlg: Algorithm = SimpleAlg()

        val currentCycles = this.cycles.getValue(type)

        if (currentCycles.size > 0 || this.getMisOrientedCount(type) > 0) { // TODO alg sources buffer support
            var i = 0
            while (i < currentCycles.size) {
                if (i + 1 >= currentCycles.size) {
                    i += 2
                    continue
                }

                val pair = this.getLetteringScheme(type)[currentCycles[i]] + this.getLetteringScheme(type)[currentCycles[i + 1]]

                val caseAlgs = this.algSource!!.getAlgorithms(type, pair)
                var thisAlg: Algorithm = ImageStringReader().parse("LOL")

                if (caseAlgs.isNotEmpty()) {
                    val listAlgs = ArrayList(caseAlgs)
                    listAlgs.shuffle()
                    thisAlg = listAlgs[0]
                }

                finalAlg = finalAlg.merge(thisAlg)
                i += 2
            }
        } else {
            return "Solved"
        }

        return finalAlg.toFormatString().trim { it <= ' ' }
    }

    fun getRawSolutionAlgorithm(withRotation: Boolean): String {
        val solutionParts = mutableListOf<String>()

        if (withRotation) {
            solutionParts.add("Rotations: " + if (this.scrambleOrientationPremoves.algLength() > 0) this.scrambleOrientationPremoves.toFormatString() else "/")
        }

        solutionParts += this.getExecutionOrderPieceTypes()
                .map { "${it.humanName}: ${getRawSolutionAlgorithm(it)}" }

        return solutionParts.joinToString("\n")
    }

    fun getPreSolvedCount(type: PieceType): Int {
        val solvedFlags = this.preSolvedPieces.getValue(type)

        return solvedFlags.drop(1).count { it }
    }

    fun getMisOrientedCount(type: PieceType): Int {
        return type.targetsPerPiece.countingArray().sumBy { this.getMisOrientedCount(type, it) }
    }

    fun getMisOrientedCount(type: PieceType, orientation: Int): Int {
        val actualOrient = orientation % type.targetsPerPiece
        val orientations = this.misOrientedPieces.getValue(type)[actualOrient]

        return orientations.drop(1).count { it }
    }

    protected fun getMisOrientedPieces(type: PieceType, orientation: Int): List<Int> {
        val orientations = this.misOrientedPieces.getValue(type)[orientation]
        val cubies = this.cubies.getValue(type)

        return orientations.indices.drop(1)
                .filter { orientations[it] }
                .map { cubies[it][orientation] }
    }

    protected fun getMisOrientedPieceNames(type: PieceType, orientation: Int): List<String> {
        val lettering = this.getLetteringScheme(type)
        val pieces = this.getMisOrientedPieces(type, orientation)

        return pieces.map { lettering[it] }
    }

    protected fun getRotationSolutions(type: PieceType): String {
        val pairs = StringBuilder()

        val orientations = type.targetsPerPiece

        for (i in 1 until orientations) {
            if (this.getMisOrientedCount(type, i) > 0) {
                pairs.append(if (pairs.toString().endsWith(" ")) "" else " ")

                when (this.misOrientMethod) {
                    BldPuzzle.MisOrientMethod.SOLVE_DIRECT -> {
                        pairs.append("Orient $i: ")
                        pairs.append(this.getMisOrientedPieceNames(type, i).joinToString(" "))
                    }

                    BldPuzzle.MisOrientMethod.SINGLE_TARGET -> {
                        val misOrients = this.getMisOrientedPieces(type, i)
                        val lettering = this.getLetteringScheme(type)
                        val cubies = this.cubies.getValue(type)

                        for (piece in misOrients) {
                            val outer = cubies.deepOuterIndex(piece)
                            val inner = cubies.deepInnerIndex(piece)

                            pairs.append(lettering[piece])
                            pairs.append(lettering[cubies[outer][(inner + i) % orientations]])
                            pairs.append(" ")
                        }
                    }
                }
            }
        }

        return pairs.toString().trim { it <= ' ' }
    }

    fun getLetteringScheme(type: PieceType): Array<String> {
        return if (type is LetterPairImage) {
            this.getPieceTypes()
                    .flatMap { this.getLetteringScheme(it).asIterable() }
                    .toSortedSet()
                    .toTypedArray()
        } else this.letterSchemes.getValue(type).copyOf()
    }

    fun getBufferTarget(type: PieceType): String {
        return if (type is LetterPairImage) this.letterPairLanguage else this.getLetteringScheme(type)[this.getBuffer(type)]
    }

    fun getBufferPiece(type: PieceType): Array<Int> {
        if (type is LetterPairImage) {
            return arrayOf()
        }

        return this.cubies.getValue(type)[0].copyOf()
    }

    fun getBufferPieceTargets(type: PieceType): Array<String> {
        if (type is LetterPairImage) {
            return arrayOf(this.letterPairLanguage)
        }

        return this.getBufferPiece(type)
                .map { this.letterSchemes.getValue(type)[it] }
                .toTypedArray()
    }

    fun getLetterPairCorrespondant(type: PieceType, piece: Int): String {
        val cubies = this.cubies[type]
        val lettering = this.getLetteringScheme(type)

        if (cubies != null) {
            val outer = cubies.deepOuterIndex(piece)

            if (outer > -1) {
                val pieceModel = cubies[outer]
                val pieces = pieceModel.toMutableList() - piece

                return pieces.joinToString("") { lettering[it] }
            }
        }

        return ""
    }

    fun getScrambleScore(type: PieceType): Double { // TODO refine
        val num = type.numPieces
        var scoreBase = (num * num).toDouble()

        scoreBase -= this.getCycleLength(type).toFloat()
        scoreBase += this.getPreSolvedCount(type).toFloat()
        scoreBase -= (this.getMisOrientedCount(type) * type.targetsPerPiece).toFloat()
        scoreBase -= (this.getBreakInCount(type) * type.numPiecesNoBuffer).toFloat()

        if (this.hasParity(type)) {
            scoreBase -= (0.25 * scoreBase).toFloat()
        }

        if (this.isBufferSolved(type)) {
            scoreBase -= (0.25 * scoreBase).toFloat()
        }

        return max(0.0, scoreBase)
    }

    fun getScrambleScore(): Double {
        val score = this.getPieceTypes().sumByDouble { it.numPieces * this.getScrambleScore(it) }
        val weight = this.getPieceTypes().sumBy { it.numPieces }

        return score / weight
    }

    fun getStatString(type: PieceType, indent: Boolean = false): String {
        val statString = StringBuilder(type.mnemonic + ": ")

        statString.append(if (this.hasParity(type)) "_" else if (indent) " " else "")

        val targets = this.getCycleLength(type)
        statString.append(targets)

        statString.append(if (this.isBufferSolved(type)) "*" else if (indent) " " else "")
        statString.append(if (this.isBufferSolvedAndMisOriented(type)) "*" else if (indent) " " else "")

        val numFloats = this.getBufferFloatNum(type)
        val floatsMax = this.getBackupBuffers(type).size
        statString.append(Collections.nCopies(numFloats, "\\").joinToString(""))

        if (indent) {
            statString.append(Collections.nCopies(floatsMax - numFloats, " ").joinToString(""))
        }

        val maxTargets = type.numPiecesNoBuffer / 2 * 3 + type.numPiecesNoBuffer % 2
        val lenDiff = Integer.toString(maxTargets).length - Integer.toString(targets).length
        statString.append(Collections.nCopies(lenDiff + 1, " ").joinToString(""))

        val breakInMax = type.numPiecesNoBuffer / 2
        val breakIns = this.getBreakInCount(type)

        statString.append(Collections.nCopies(breakIns, "#").joinToString(""))

        if (indent) {
            statString.append(Collections.nCopies(breakInMax - breakIns, " ").joinToString(""))
        }

        val misOrientPreSolvedMax = type.numPiecesNoBuffer

        if (type.targetsPerPiece > 1) {
            val misOriented = this.getMisOrientedCount(type)

            if (indent || misOriented > 0 && !statString.toString().endsWith(" ")) {
                statString.append(" ")
            }

            statString.append(Collections.nCopies(misOriented, "~").joinToString(""))

            if (indent) {
                statString.append(Collections.nCopies(misOrientPreSolvedMax - misOriented, " ").joinToString(""))
            }
        }

        val preSolved = this.getPreSolvedCount(type)

        if (indent || preSolved > 0 && !statString.toString().endsWith(" ")) {
            statString.append(" ")
        }

        statString.append(Collections.nCopies(preSolved, "+").joinToString(""))

        if (indent) {
            statString.append(Collections.nCopies(misOrientPreSolvedMax - preSolved, " ").joinToString(""))
        }

        return if (indent) statString.toString() else statString.toString().trim { it <= ' ' }
    }

    fun getStatString(indent: Boolean = false): String {
        return this.getExecutionOrderPieceTypes()
                .reversed()
                .joinToString(" | ") { getStatString(it, indent) }
    }

    @Deprecated("Bad naming. Use getShortStats instead", ReplaceWith("this.getShortStats(type)"))
    fun getStatistics(type: PieceType): String {
        return this.getShortStats(type)
    }

    @Deprecated("Bad naming. Use getShortStats instead", ReplaceWith("this.getShortStats()"))
    fun getStatistics(): String {
        return this.getShortStats()
    }

    fun getShortStats(type: PieceType): String {
        return type.humanName + ": " + this.getCycleLength(type) + "@" + this.getBreakInCount(type) + " w/ " + this.getPreSolvedCount(type) + "-" + this.getMisOrientedCount(type) + "\\" + this.getBufferFloatNum(type) + " > " + this.hasParity(type)
    }

    fun getShortStats(): String {
        return this.getExecutionOrderPieceTypes()
                .reversed()
                .joinToString("\n") { this.getShortStats(it) }
    }

    fun getCycleLength(type: PieceType): Int {
        return this.cycles.getValue(type).size
    }

    @Deprecated("Bad naming. Use getCycleLength instead", ReplaceWith("this.getCycleLength(type)"))
    fun getStatLength(type: PieceType): Int {
        return this.getCycleLength(type)
    }

    fun getBreakInCount(type: PieceType): Int {
        return this.cycleCount.getValue(type)
    }

    fun isSingleCycle(type: PieceType): Boolean {
        return this.getBreakInCount(type) == 0
    }

    fun isSingleCycle(): Boolean {
        return this.getPieceTypes().all(this::isSingleCycle)
    }

    open fun hasParity(type: PieceType): Boolean {
        return this.parities.getOrElse(type) { false }
    }

    fun hasParity(): Boolean {
        return this.getPieceTypes().any(this::hasParity)
    }

    fun isBufferSolved(type: PieceType, acceptMisOrient: Boolean = true): Boolean {
        val bufferSolved = this.preSolvedPieces.getValue(type)[0]

        return bufferSolved || acceptMisOrient && this.isBufferSolvedAndMisOriented(type)
    }

    fun isBufferSolvedAndMisOriented(type: PieceType): Boolean {
        var bufferTwisted = false

        val orientations = type.targetsPerPiece

        val reference = this.cubies.getValue(type)
        val state = this.lastScrambledState.getValue(type)

        for (i in 1 until orientations) {
            var bufferCurrentOrigin = true

            for (j in 0 until orientations) {
                bufferCurrentOrigin = bufferCurrentOrigin and (state[reference[0][j]] == reference[0][(j + i) % orientations])
            }

            bufferTwisted = bufferTwisted or bufferCurrentOrigin
        }

        return bufferTwisted
    }

    fun getBufferFloatNum(type: PieceType): Int {
        return this.bufferFloats.getValue(type).size
    }

    fun getBufferFloatNum(): Int {
        return this.getPieceTypes().sumBy(this::getBufferFloatNum)
    }

    fun hasBufferFloat(type: PieceType): Boolean {
        return this.getBufferFloatNum(type) > 0
    }

    fun hasBufferFloat(): Boolean {
        return this.getPieceTypes().any(this::hasBufferFloat)
    }

    protected fun getBackupBuffers(type: PieceType): List<Int> {
        return this.backupBuffers.getValue(type).toList()
    }

    fun getNoahtation(type: PieceType): String {
        val misOriented = "'".repeat(this.getMisOrientedCount(type))
        return "${type.mnemonic}: ${this.getCycleLength(type)}$misOriented"
    }

    fun getNoahtation(): String {
        return this.getExecutionOrderPieceTypes()
                .reversed()
                .joinToString(" / ") { this.getNoahtation(it) }
    }

    fun setLetteringScheme(type: PieceType, newScheme: Array<String>): Boolean {
        if (this.getLetteringScheme(type).size == newScheme.size) {
            this.letterSchemes[type] = newScheme

            this.reSolve()
            return true
        }

        return false
    }

    fun setBuffer(type: PieceType, newBuffer: Int): Boolean {
        if (this.cycleCubiesForBuffer(type, newBuffer)) {
            this.mainBuffers.put(type, newBuffer)

            this.reSolve()
            return true
        }

        return false
    }

    fun setBuffer(type: PieceType, newBuffer: String): Boolean {
        val letterScheme = this.getLetteringScheme(type)

        return letterScheme.contains(newBuffer)
                && this.setBuffer(type, letterScheme.indexOf(newBuffer))
    }

    fun registerFloatingBuffer(type: PieceType, newBuffer: Int): Boolean {
        val floatingBuffers = this.backupBuffers.getValue(type)

        if (!floatingBuffers.contains(newBuffer) && this.mainBuffers[type] != newBuffer) {
            floatingBuffers.add(newBuffer)

            this.reSolve()
            return true
        }

        return false
    }

    fun registerFloatingBuffer(type: PieceType, newBuffer: String): Boolean {
        val letterScheme = this.getLetteringScheme(type)

        return letterScheme.contains(newBuffer)
                && this.registerFloatingBuffer(type, letterScheme.indexOf(newBuffer))
    }

    fun dropFloatingBuffers(type: PieceType) {
        this.backupBuffers.getValue(type).clear()

        this.reSolve()
    }

    fun dropFloatingBuffers() {
        this.getPieceTypes().forEach(this::dropFloatingBuffers)
    }

    protected abstract fun solvePieces(type: PieceType)

    protected abstract fun getPermutationPieceTypes(): List<PieceType>

    protected abstract fun getOrientationPieceTypes(): List<PieceType>

    protected abstract fun getExecutionOrderPieceTypes(): List<PieceType>

    protected abstract fun getOrientationSideCount(): Int

    protected abstract fun getDefaultCubies(): Map<PieceType, Array<Array<Int>>>

    protected abstract fun getReorientationMoves(): Algorithm

    protected abstract fun getSolvingOrientationPremoves(): Algorithm

    fun matchesExecution(type: PieceType, filter: (Algorithm) -> Boolean): Boolean {
        if (this.algSource == null) {
            return false
        }

        val rawSolution = this.getSolutionRaw(type)
        var matches = true

        if (rawSolution == "Solved") {
            return true
        } else {
            for (letterPair in rawSolution.split("(?<=\\G[A-Z]{2})".toRegex()).dropLastWhile { it.isEmpty() }) {
                if (letterPair.length < 2) {
                    continue
                }

                val exists = this.algSource!!.getAlgorithms(type, letterPair).any(filter)
                matches = matches and exists
            }
        }

        return matches
    }

    fun matchesExecution(filter: (Algorithm) -> Boolean): Boolean {
        return this.getPieceTypes().all { this.matchesExecution(it, filter) }
    }

    fun solves(type: PieceType, alg: Algorithm, solutionCase: String, pure: Boolean = true): Boolean {
        val currentScramble = this.scramble

        this.parseScramble(alg.inverse())

        var solves = this.getSolutionRaw(type).equals(solutionCase.replace("\\s".toRegex(), ""), ignoreCase = true) && this.getMisOrientedCount(type) == 0

        if (pure) {
            val remainingTypes = this.getPieceTypes().toMutableList()
            remainingTypes.remove(type)

            for (remainingType in remainingTypes) {
                solves = solves and (this.getSolutionRaw(remainingType) == "Solved" && this.getMisOrientedCount(remainingType) == 0)
            }
        }

        this.parseScramble(currentScramble)

        return solves
    }

    public override fun clone(): BldPuzzle {
        return super.clone() as BldPuzzle // TODO deep clone
    }

    fun clone(scramble: Algorithm): BldPuzzle {
        val clone = this.clone()
        clone.parseScramble(scramble)
        return clone
    }

    enum class MisOrientMethod {
        SOLVE_DIRECT, SINGLE_TARGET
    }
}