package com.opendarts.session

import com.opendarts.game.cricket.CricketConfig
import com.opendarts.game.cricket.CricketGame
import com.opendarts.game.cricket.CricketMode
import com.opendarts.game.engine.DartGame
import com.opendarts.game.model.Player
import com.opendarts.game.practice.AroundTheClockConfig
import com.opendarts.game.practice.AroundTheClockGame
import com.opendarts.game.practice.CountUpConfig
import com.opendarts.game.practice.CountUpGame
import com.opendarts.game.x01.X01Config
import com.opendarts.game.x01.X01Game

enum class GameKind(val title: String, val blurb: String) {
    X01("X01", "301, 501 and more. Count down to zero."),
    CRICKET("Cricket", "Close 15-20 and the bull. Standard, cut-throat or no-score."),
    AROUND_THE_CLOCK("Around the Clock", "Hit 1 to 20, then the bull. First to finish wins."),
    COUNT_UP("Count Up", "Score as much as you can over a set number of rounds."),
}

enum class InputMode { MANUAL, CAMERA }

data class GameSetup(
    val kind: GameKind = GameKind.X01,
    val playerNames: List<String> = listOf("Player 1", "Player 2"),
    val x01: X01Config = X01Config(),
    val cricketMode: CricketMode = CricketMode.STANDARD,
    val clock: AroundTheClockConfig = AroundTheClockConfig(),
    val countUpRounds: Int = 8,
    val input: InputMode = InputMode.MANUAL,
) {
    val players: List<Player>
        get() = playerNames.mapIndexed { i, name -> Player(i, name.trim().ifBlank { "Player ${i + 1}" }) }

    fun buildGame(): DartGame<*> = when (kind) {
        GameKind.X01 -> X01Game(players, x01)
        GameKind.CRICKET -> CricketGame(players, CricketConfig(cricketMode))
        GameKind.AROUND_THE_CLOCK -> AroundTheClockGame(players, clock)
        GameKind.COUNT_UP -> CountUpGame(players, CountUpConfig(countUpRounds))
    }

    val subtitle: String
        get() = when (kind) {
            GameKind.X01 -> "${x01.startingScore} · ${x01.inMode.label("in")} / ${x01.outMode.label("out")}" +
                if (x01.legsToWin > 1 || x01.setsToWin > 1) " · first to ${x01.legsToWin} legs" + (if (x01.setsToWin > 1) ", ${x01.setsToWin} sets" else "") else ""
            GameKind.CRICKET -> cricketMode.name.lowercase().replace('_', '-')
            GameKind.AROUND_THE_CLOCK -> if (clock.doublesOnly) "doubles only" else if (clock.skipOnMultiplier) "doubles & trebles skip" else "standard"
            GameKind.COUNT_UP -> "$countUpRounds rounds"
        }
}

private fun com.opendarts.game.x01.InOut.label(suffix: String) = when (this) {
    com.opendarts.game.x01.InOut.STRAIGHT -> "straight $suffix"
    com.opendarts.game.x01.InOut.DOUBLE -> "double $suffix"
    com.opendarts.game.x01.InOut.MASTER -> "master $suffix"
}