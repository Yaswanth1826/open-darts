package com.opendarts.session

import com.opendarts.game.cricket.CricketConfig
import com.opendarts.game.cricket.CricketGame
import com.opendarts.game.cricket.CricketMode
import com.opendarts.game.engine.DartGame
import com.opendarts.game.party.BaseballGame
import com.opendarts.game.party.HalveItGame
import com.opendarts.game.party.KillerGame
import com.opendarts.game.party.ShanghaiGame
import com.opendarts.game.model.Player
import com.opendarts.game.practice.AroundTheClockConfig
import com.opendarts.game.practice.AroundTheClockGame
import com.opendarts.game.practice.Bobs27Game
import com.opendarts.game.practice.CheckoutLevel
import com.opendarts.game.practice.CheckoutPracticeConfig
import com.opendarts.game.practice.CheckoutPracticeGame
import com.opendarts.game.practice.CountUpConfig
import com.opendarts.game.practice.CountUpGame
import com.opendarts.game.practice.JdcChallengeGame
import com.opendarts.game.practice.OneTwentyOneConfig
import com.opendarts.game.practice.OneTwentyOneGame
import com.opendarts.game.practice.TargetDrillGame
import com.opendarts.game.x01.X01Config
import com.opendarts.game.x01.X01Game

enum class GameKind(val title: String, val blurb: String) {
    X01("X01", "301, 501 and more. Count down to zero."),
    CRICKET("Cricket", "Close 15-20 and the bull. Standard, cut-throat or no-score."),
    AROUND_THE_CLOCK("Around the Clock", "Hit 1 to 20, then the bull. First to finish wins."),
    COUNT_UP("Count Up", "Score as much as you can over a set number of rounds."),
    JDC_CHALLENGE("JDC Challenge", "Shanghai 10-15, every double and the bull, then Shanghai 15-20. Highest score wins."),
    BOBS_27("Bob's 27", "Hit D1 to D20. Every hit adds, every miss costs. Don't drop below zero."),
    CHECKOUT_PRACTICE("Checkout practice", "A random finish each round. Check it out in three darts."),
    ONE_TWENTY_ONE("121", "Check out 121 in nine darts. Success moves the target up one, failure sends you back."),
    SHANGHAI("Shanghai", "One number per round. Single, double and treble of it in one visit wins on the spot."),
    BASEBALL("Baseball", "Nine innings. Hit the inning's number: single 1 run, double 2, treble 3."),
    HALVE_IT("Halve It", "Hit the target of every round or your score is cut in half."),
    KILLER("Killer", "Hit your own double to become a killer, then take the others' lives. Needs 2+ players."),
    WEAK_SPOTS("Weak spots", "A drill on your own weakest doubles. Two visits at each, every hit scores."),
}

/** Games the computer opponent knows how to play. */
val GameKind.canHaveBot: Boolean get() = this == GameKind.X01 || this == GameKind.CRICKET || this == GameKind.AROUND_THE_CLOCK || this == GameKind.COUNT_UP

/** How many players a game needs before it can start. */
val GameKind.minPlayers: Int get() = if (this == GameKind.KILLER) 2 else 1

enum class InputMode { MANUAL, CAMERA }

/** The four home-screen tiles. */
enum class Category(val title: String) { X01("X01"), CRICKET("Cricket"), PRACTICE("Practice"), PARTY("Party") }

val GameKind.category: Category
    get() = when (this) {
        GameKind.X01 -> Category.X01
        GameKind.CRICKET -> Category.CRICKET
        GameKind.AROUND_THE_CLOCK, GameKind.COUNT_UP, GameKind.JDC_CHALLENGE, GameKind.BOBS_27, GameKind.CHECKOUT_PRACTICE, GameKind.ONE_TWENTY_ONE, GameKind.WEAK_SPOTS -> Category.PRACTICE
        GameKind.SHANGHAI, GameKind.BASEBALL, GameKind.HALVE_IT, GameKind.KILLER -> Category.PARTY
    }

/** Games where who throws first matters, so a bull-off is offered. */
val GameKind.hasBullOff: Boolean get() = category != Category.PRACTICE

val Category.kinds: List<GameKind> get() = GameKind.entries.filter { it.category == this }

data class GameSetup(
    val kind: GameKind = GameKind.X01,
    val playerNames: List<String> = listOf("Player 1", "Player 2"),
    val x01: X01Config = X01Config(),
    val cricketMode: CricketMode = CricketMode.STANDARD,
    val clock: AroundTheClockConfig = AroundTheClockConfig(),
    val countUpRounds: Int = 8,
    val checkoutLevel: CheckoutLevel = CheckoutLevel.MEDIUM,
    val checkoutAttempts: Int = 10,
    val oneTwentyOne: OneTwentyOneConfig = OneTwentyOneConfig(),
    val shanghaiRounds: Int = 7,
    /** Source of everything random in a game (checkout numbers, Killer numbers), kept so a saved game replays the same. */
    val seed: Int = 0,
    val input: InputMode = InputMode.CAMERA,
    /** 0 for no computer opponent; otherwise its X01 average. */
    val botLevel: Int = 0,
    /** Which player is the bot; -1 means the last one, which is where the setup screen puts it. */
    val botSeat: Int = -1,
    /** The doubles of the Weak spots drill (25 is the bull), chosen when the game is set up. */
    val drillTargets: List<Int> = DrillPlan.FALLBACK.take(DrillPlan.SIZE),
) {
    /** Index of the bot in [playerNames], or -1 when there is none. */
    val botIndex: Int get() = if (botLevel > 0) (if (botSeat >= 0) botSeat else playerNames.lastIndex) else -1

    val players: List<Player>
        get() = playerNames.mapIndexed { i, name -> Player(i, name.trim().ifBlank { "Player ${i + 1}" }, if (i == botIndex) botLevel else 0) }

    /** The same game with player [first] throwing first and the others following in their order. */
    fun startingWith(first: Int): GameSetup {
        val n = playerNames.size
        if (first <= 0 || first >= n) return this
        return copy(
            playerNames = playerNames.drop(first) + playerNames.take(first),
            botSeat = if (botIndex >= 0) (botIndex - first).mod(n) else -1,
        )
    }

    fun buildGame(): DartGame<*> = when (kind) {
        GameKind.X01 -> X01Game(players, x01)
        GameKind.CRICKET -> CricketGame(players, CricketConfig(cricketMode))
        GameKind.AROUND_THE_CLOCK -> AroundTheClockGame(players, clock)
        GameKind.COUNT_UP -> CountUpGame(players, CountUpConfig(countUpRounds))
        GameKind.JDC_CHALLENGE -> JdcChallengeGame(players)
        GameKind.BOBS_27 -> Bobs27Game(players)
        GameKind.CHECKOUT_PRACTICE -> CheckoutPracticeGame(players, CheckoutPracticeConfig(checkoutLevel, checkoutAttempts, seed))
        GameKind.ONE_TWENTY_ONE -> OneTwentyOneGame(players, oneTwentyOne)
        GameKind.SHANGHAI -> ShanghaiGame(players, shanghaiRounds)
        GameKind.BASEBALL -> BaseballGame(players)
        GameKind.HALVE_IT -> HalveItGame(players)
        GameKind.KILLER -> KillerGame(players, seed)
        GameKind.WEAK_SPOTS -> TargetDrillGame(players, drillTargets)
    }

    /** Short heading shown on the setup and game screens. */
    val title: String
        get() = when (kind) {
            GameKind.X01 -> x01.startingScore.toString()
            GameKind.CHECKOUT_PRACTICE -> "Checkout"
            GameKind.ONE_TWENTY_ONE -> "121"
            else -> kind.title
        }

    /** Two compact lines summarising the rules, shown in the badge on the setup screen. */
    val ruleBadge: Pair<String, String>
        get() {
            val second = if (input == InputMode.CAMERA) "AUTO CAMERA" else "MANUAL INPUT"
            val first = when (kind) {
                GameKind.X01 -> "${x01.inMode.letter()}I-${x01.outMode.letter()}O"
                GameKind.CRICKET -> cricketMode.name.replace('_', '-')
                GameKind.AROUND_THE_CLOCK -> if (clock.doublesOnly) "DOUBLES ONLY" else if (clock.skipOnMultiplier) "SKIP AHEAD" else "STANDARD"
                GameKind.COUNT_UP -> "$countUpRounds ROUNDS"
                GameKind.JDC_CHALLENGE -> "57 DARTS"
                GameKind.BOBS_27 -> "D1 TO D20"
                GameKind.CHECKOUT_PRACTICE -> "${checkoutLevel.label.uppercase()} · $checkoutAttempts TRIES"
                GameKind.ONE_TWENTY_ONE -> "FROM ${oneTwentyOne.start} · ${oneTwentyOne.attempts} TRIES"
                GameKind.SHANGHAI -> "$shanghaiRounds ROUNDS"
                GameKind.BASEBALL -> "9 INNINGS"
                GameKind.HALVE_IT -> "9 ROUNDS"
                GameKind.KILLER -> "3 LIVES"
                GameKind.WEAK_SPOTS -> "${drillTargets.size} DOUBLES"
            }
            return first to second
        }

    val matchLabel: String
        get() = when {
            kind != GameKind.X01 -> "GAME RULES"
            x01.setsToWin > 1 -> "FIRST TO ${x01.setsToWin} SETS"
            x01.legsToWin > 1 -> "FIRST TO ${x01.legsToWin} LEGS"
            else -> "MATCH MODE IS OFF"
        }

    val subtitle: String
        get() = when (kind) {
            GameKind.X01 -> "${x01.startingScore} · ${x01.inMode.label("in")} / ${x01.outMode.label("out")}" +
                if (x01.legsToWin > 1 || x01.setsToWin > 1) " · first to ${x01.legsToWin} legs" + (if (x01.setsToWin > 1) ", ${x01.setsToWin} sets" else "") else ""
            GameKind.CRICKET -> cricketMode.name.lowercase().replace('_', '-')
            GameKind.AROUND_THE_CLOCK -> if (clock.doublesOnly) "doubles only" else if (clock.skipOnMultiplier) "doubles & trebles skip" else "standard"
            GameKind.COUNT_UP -> "$countUpRounds rounds"
            GameKind.JDC_CHALLENGE -> "19 rounds · 57 darts"
            GameKind.BOBS_27 -> "D1 to D20"
            GameKind.CHECKOUT_PRACTICE -> "${checkoutLevel.label.lowercase()} · $checkoutAttempts checkouts"
            GameKind.ONE_TWENTY_ONE -> "from ${oneTwentyOne.start} · ${oneTwentyOne.attempts} attempts"
            GameKind.SHANGHAI -> "$shanghaiRounds rounds"
            GameKind.BASEBALL -> "9 innings"
            GameKind.HALVE_IT -> "9 rounds"
            GameKind.KILLER -> "3 lives"
            GameKind.WEAK_SPOTS -> drillTargets.joinToString(" ") { DrillPlan.label(it) }
        }
}

private fun com.opendarts.game.x01.InOut.label(suffix: String) = when (this) {
    com.opendarts.game.x01.InOut.STRAIGHT -> "straight $suffix"
    com.opendarts.game.x01.InOut.DOUBLE -> "double $suffix"
    com.opendarts.game.x01.InOut.MASTER -> "master $suffix"
}

private fun com.opendarts.game.x01.InOut.letter() = when (this) {
    com.opendarts.game.x01.InOut.STRAIGHT -> "S"
    com.opendarts.game.x01.InOut.DOUBLE -> "D"
    com.opendarts.game.x01.InOut.MASTER -> "M"
}
