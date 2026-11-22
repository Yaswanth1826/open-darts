package com.opendarts.game.engine

import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Player

/** What a single dart (or turn action) meant for the game; used by the UI for feedback. */
enum class Outcome { SCORED, NO_SCORE, NOT_IN, BUST, LEG_WON, SET_WON, GAME_WON, ELIMINATED, SPECIAL, IGNORED }

class StepResult<S>(
    val state: S,
    val outcome: Outcome = Outcome.SCORED,
    /** End the turn now (bust, win, ...) even if fewer than the allowed darts were thrown. */
    val turnOver: Boolean = false,
    /** Non-null once the game is decided; may hold several players on a tie. */
    val winners: List<Int>? = null,
)

class FinishResult<S>(val state: S, val winners: List<Int>? = null)
class AdvanceResult<S>(val state: S, val nextPlayer: Int? = null, val newRound: Int? = null)

/** One dart that still counts, with the game as it was just before and just after it. */
class Dart<S>(val player: Int, val hit: DartHit, val before: S, val after: S)

/**
 * Base for every game. A game is a stack of immutable snapshots, so undo is exact and cheap and
 * manual taps and camera detections drive exactly the same API.
 *
 * Turn flow: throwDart() until the turn is over (3 darts, bust, win...), then endTurn() moves to the
 * next player. Throwing a dart after the turn is over implicitly calls endTurn() first.
 */
abstract class DartGame<S : Any>(initialPlayers: List<Player>, val dartsPerTurn: Int = 3) {
    /** The players still in the game; shrinks when [removePlayer] is used. */
    var players: List<Player> = initialPlayers
        private set

    init {
        require(players.isNotEmpty()) { "a game needs at least one player" }
        require(dartsPerTurn > 0) { "dartsPerTurn must be positive" }
    }

    protected abstract fun initialState(): S

    /** [state] with player [index] taken out, every later player moving up one place. */
    protected abstract fun withoutPlayer(state: S, index: Int): S

    /** Applies one dart. [turnBefore] holds the darts already thrown this turn by [player]. */
    protected abstract fun onThrow(state: S, player: Int, hit: DartHit, turnBefore: List<DartHit>, round: Int): StepResult<S>

    /** Runs once per turn when it finishes (before advancing); e.g. Halve-It halving or Bob's 27 penalties. */
    protected open fun onTurnFinished(state: S, player: Int, turn: List<DartHit>, round: Int): FinishResult<S> = FinishResult(state)

    /** Runs when moving to the next player; lets a game reset a leg or pick the next player. */
    protected open fun onAdvance(state: S, round: Int): AdvanceResult<S> = AdvanceResult(state)

    protected open fun isActive(state: S, player: Int): Boolean = true

    protected fun isLastActivePlayerInRound(state: S, player: Int): Boolean =
        (player + 1 until players.size).none { isActive(state, it) }

    private data class Snapshot<S>(
        val game: S,
        val player: Int,
        val turn: List<DartHit>,
        val turnOver: Boolean,
        val finalised: Boolean,
        val round: Int,
        val winners: List<Int>?,
        val outcome: Outcome,
        val transition: Boolean = false,
    )

    private val history = ArrayList<Snapshot<S>>()

    private fun current(): Snapshot<S> {
        if (history.isEmpty()) {
            history += Snapshot(initialState(), 0, emptyList(), false, false, 1, null, Outcome.SCORED)
        }
        return history.last()
    }

    val gameState: S get() = current().game
    val currentPlayerIndex: Int get() = current().player
    val currentPlayer: Player get() = players[current().player]
    val turnHits: List<DartHit> get() = current().turn
    val turnOver: Boolean get() = current().turnOver
    val round: Int get() = current().round
    val winners: List<Int>? get() = current().winners
    val isFinished: Boolean get() = current().winners != null
    val lastOutcome: Outcome get() = current().outcome
    val canUndo: Boolean get() = history.size > 1

    /** Every dart that still counts, in the order thrown: undone darts are gone and edited ones show as edited. */
    val trail: List<Dart<S>>
        get() {
            current()
            return history.indices.filter { i -> i > 0 && !history[i].transition && history[i].turn.isNotEmpty() }
                .map { i -> Dart(history[i].player, history[i].turn.last(), history[i - 1].game, history[i].game) }
        }
    val dartsLeftInTurn: Int get() = if (turnOver) 0 else dartsPerTurn - turnHits.size

    fun throwDart(hit: DartHit): Outcome {
        var snap = current()
        if (snap.winners != null) return Outcome.IGNORED
        if (snap.turnOver) {
            endTurn()
            snap = current()
        }
        val step = onThrow(snap.game, snap.player, hit, snap.turn, snap.round)
        val turn = snap.turn + hit
        var over = step.turnOver || step.winners != null || turn.size >= dartsPerTurn
        var next = Snapshot(step.state, snap.player, turn, over, false, snap.round, step.winners, step.outcome)
        if (over) next = finalise(next)
        history += next
        return next.outcome
    }

    /** Ends the current turn (early if fewer darts were thrown) and hands over to the next player. */
    fun endTurn() = handOver(finaliseTurn = true)

    private fun handOver(finaliseTurn: Boolean) {
        var snap = current()
        if (snap.winners != null) return
        if (!snap.finalised && finaliseTurn) snap = finalise(snap.copy(turnOver = true))
        if (snap.winners != null) {
            history += snap.copy(transition = true)
            return
        }
        val advanced = onAdvance(snap.game, snap.round)
        val state = advanced.state
        val n = players.size
        var next = advanced.nextPlayer ?: ((snap.player + 1) % n)
        var guard = 0
        while (!isActive(state, next) && guard++ < n) next = (next + 1) % n
        val round = advanced.newRound ?: if (advanced.nextPlayer == null && next <= snap.player) snap.round + 1 else snap.round
        history += Snapshot(state, next, emptyList(), false, false, round, null, Outcome.SCORED, transition = true)
    }

    /**
     * Takes player [index] out of the game, with all their darts and score. If it was their turn, play passes to the next player.
     * Undo and the other players' scores carry on as if the player had never been in the game. Returns false when that is not
     * possible (the game is over, or it would leave nobody).
     */
    fun removePlayer(index: Int): Boolean {
        if (index !in players.indices || players.size < 2 || isFinished) return false
        current()
        var guard = 0
        while (history.last().player == index && guard++ < players.size) handOver(finaliseTurn = history.last().turn.isNotEmpty())
        if (history.last().player == index) return false
        val shift = { p: Int -> if (p > index) p - 1 else p }
        val kept = history.filter { it.player != index }.map {
            it.copy(game = withoutPlayer(it.game, index), player = shift(it.player), winners = it.winners?.filter { w -> w != index }?.map(shift))
        }
        history.clear()
        history += kept
        history[0] = history[0].copy(transition = false)
        players = players.filterIndexed { i, _ -> i != index }
        return true
    }

    /** Removes the last dart (and any turn hand-over that followed it). */
    fun undo(): Boolean {
        if (history.size <= 1) return false
        while (history.size > 1) {
            val removed = history.removeAt(history.lastIndex)
            if (!removed.transition) break
        }
        return true
    }

    /**
     * Goes back to the previous player's turn with their darts still in place, so they can be corrected with [editDart].
     * Darts already thrown by the current player this turn are discarded. Returns false at the very start of the game.
     */
    fun previousPlayer(): Boolean {
        current()
        val handover = history.indexOfLast { it.transition }
        if (handover < 0) return false
        while (history.size > handover) history.removeAt(history.lastIndex)
        return true
    }

    /**
     * Changes ([hit]) or removes (null) the dart at [index] of the turn on screen, and replays the turn so every rule
     * (busts, wins, marks, the score of the following darts) comes out exactly as if the darts had been thrown that way.
     * Darts that can no longer be thrown, for example after a replaced dart now ends the turn, are dropped.
     */
    fun editDart(index: Int, hit: DartHit?): Boolean {
        val hits = current().turn.toMutableList()
        if (index !in hits.indices) return false
        if (hit == null) hits.removeAt(index) else hits[index] = hit
        while (history.size > 1 && history.last().turn.isNotEmpty()) history.removeAt(history.lastIndex)
        for (h in hits) {
            if (current().turnOver || current().winners != null) break
            throwDart(h)
        }
        return true
    }

    private fun finalise(snap: Snapshot<S>): Snapshot<S> {
        val finish = onTurnFinished(snap.game, snap.player, snap.turn, snap.round)
        val winners = snap.winners ?: finish.winners
        val outcome = if (snap.winners == null && winners != null) Outcome.GAME_WON else snap.outcome
        return snap.copy(game = finish.state, finalised = true, turnOver = true, winners = winners, outcome = outcome)
    }
}