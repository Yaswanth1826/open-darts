package com.opendarts.game.engine

import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Multiplier

/** One thing the players did to a game. Replaying the actions on a fresh game gives back exactly the same game. */
sealed interface GameAction {
    data class Throw(val hit: DartHit) : GameAction
    data object NextTurn : GameAction
    data object Undo : GameAction
    data object Previous : GameAction
    /** [hit] null removes the dart. */
    data class Edit(val index: Int, val hit: DartHit?) : GameAction
}

/** The actions of a game so far, so an unfinished game can be saved as text and picked up again later. */
class GameLog(actions: List<GameAction> = emptyList()) {
    private val list = ArrayList(actions)
    val actions: List<GameAction> get() = list
    val size: Int get() = list.size

    fun add(action: GameAction) { list += action }

    fun clear() = list.clear()

    /** Applies every action to [game], which must be new. */
    fun replay(game: DartGame<*>) {
        for (action in list) when (action) {
            is GameAction.Throw -> game.throwDart(action.hit)
            GameAction.NextTurn -> game.endTurn()
            GameAction.Undo -> game.undo()
            GameAction.Previous -> game.previousPlayer()
            is GameAction.Edit -> game.editDart(action.index, action.hit)
        }
    }

    fun encode(): String = list.joinToString(";") {
        when (it) {
            is GameAction.Throw -> "T,${hit(it.hit)}"
            GameAction.NextTurn -> "N"
            GameAction.Undo -> "U"
            GameAction.Previous -> "P"
            is GameAction.Edit -> "E,${it.index},${it.hit?.let(::hit) ?: "x"}"
        }
    }

    private fun hit(h: DartHit) = "${h.base}.${h.multiplier.ordinal}"

    companion object {
        /** Reads text made by [encode]; returns null if it is damaged. */
        fun decode(text: String): GameLog? = runCatching {
            val actions = text.split(';').filter { it.isNotBlank() }.map { token ->
                val parts = token.split(',')
                when (parts[0]) {
                    "T" -> GameAction.Throw(parseHit(parts[1]))
                    "N" -> GameAction.NextTurn
                    "U" -> GameAction.Undo
                    "P" -> GameAction.Previous
                    "E" -> GameAction.Edit(parts[1].toInt(), if (parts[2] == "x") null else parseHit(parts[2]))
                    else -> error("unknown action $token")
                }
            }
            GameLog(actions)
        }.getOrNull()

        private fun parseHit(text: String): DartHit {
            val (base, multiplier) = text.split('.')
            return DartHit(base.toInt(), Multiplier.entries[multiplier.toInt()])
        }
    }
}