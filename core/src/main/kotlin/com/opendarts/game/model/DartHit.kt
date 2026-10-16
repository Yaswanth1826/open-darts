package com.opendarts.game.model

/** A single dart landing. base is 0 (miss), 1..20, or 25 (bull). */
data class DartHit(val base: Int, val multiplier: Multiplier = Multiplier.SINGLE) {
    init {
        require(base == 0 || base in 1..20 || base == 25) { "base must be 0, 1..20, or 25" }
        require(base != 0 || multiplier == Multiplier.SINGLE) { "a miss cannot be doubled or trebled" }
        require(base != 25 || multiplier != Multiplier.TRIPLE) { "the bull cannot be trebled" }
    }

    val points: Int
        get() = when {
            base == 0 -> 0
            base == 25 && multiplier == Multiplier.DOUBLE -> 50
            else -> base * multiplier.factor
        }

    val isDouble: Boolean get() = base != 0 && multiplier == Multiplier.DOUBLE
    val isTreble: Boolean get() = base != 0 && multiplier == Multiplier.TRIPLE
    val isBull: Boolean get() = base == 25

    override fun toString(): String = when {
        base == 0 -> "Miss"
        base == 25 -> if (multiplier == Multiplier.DOUBLE) "Bull" else "25"
        else -> (when (multiplier) { Multiplier.SINGLE -> "S"; Multiplier.DOUBLE -> "D"; Multiplier.TRIPLE -> "T" }) + base
    }

    companion object {
        val MISS = DartHit(0)
        fun single(value: Int) = DartHit(value)
        fun double(value: Int) = DartHit(value, Multiplier.DOUBLE)
        fun treble(value: Int) = DartHit(value, Multiplier.TRIPLE)
        val OUTER_BULL = DartHit(25)
        val INNER_BULL = DartHit(25, Multiplier.DOUBLE)
    }
}

enum class Multiplier(val factor: Int) { SINGLE(1), DOUBLE(2), TRIPLE(3) }
