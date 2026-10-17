package com.opendarts.session

import android.content.Context
import com.opendarts.game.cricket.CricketMode
import com.opendarts.game.engine.GameLog
import com.opendarts.game.practice.AroundTheClockConfig
import com.opendarts.game.x01.InOut
import com.opendarts.game.x01.X01Config
import java.net.URLDecoder
import java.net.URLEncoder

/** Where an unfinished game is kept between app launches. */
interface GameSaver {
    fun save(setup: GameSetup, log: GameLog)
    fun load(): Pair<GameSetup, GameLog>?
    fun clear()
}

/** Keeps the game in the app's preferences as two short pieces of text: the setup and the log of what was played. */
class GameStore(context: Context) : GameSaver {
    private val prefs = context.getSharedPreferences("saved_game", Context.MODE_PRIVATE)

    override fun save(setup: GameSetup, log: GameLog) {
        prefs.edit().putString("setup", SetupCodec.encode(setup)).putString("log", log.encode()).apply()
    }

    override fun load(): Pair<GameSetup, GameLog>? {
        val setup = prefs.getString("setup", null)?.let(SetupCodec::decode) ?: return null
        val log = prefs.getString("log", null)?.let(GameLog::decode) ?: return null
        return setup to log
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }
}

/** A [GameSetup] as `key=value` lines; unknown or damaged values make [decode] return null. */
object SetupCodec {
    fun encode(s: GameSetup): String = listOf(
        "kind" to s.kind.name,
        "players" to s.playerNames.joinToString("\u0001"),
        "start" to s.x01.startingScore.toString(),
        "in" to s.x01.inMode.name,
        "out" to s.x01.outMode.name,
        "legs" to s.x01.legsToWin.toString(),
        "sets" to s.x01.setsToWin.toString(),
        "cricket" to s.cricketMode.name,
        "skip" to s.clock.skipOnMultiplier.toString(),
        "doubles" to s.clock.doublesOnly.toString(),
        "rounds" to s.countUpRounds.toString(),
        "input" to s.input.name,
    ).joinToString("\n") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }

    fun decode(text: String): GameSetup? = runCatching {
        val map = text.lines().filter { '=' in it }.associate { line ->
            line.substringBefore('=') to URLDecoder.decode(line.substringAfter('='), "UTF-8")
        }
        fun get(key: String) = map.getValue(key)
        GameSetup(
            kind = GameKind.valueOf(get("kind")),
            playerNames = get("players").split('\u0001'),
            x01 = X01Config(get("start").toInt(), InOut.valueOf(get("in")), InOut.valueOf(get("out")), get("legs").toInt(), get("sets").toInt()),
            cricketMode = CricketMode.valueOf(get("cricket")),
            clock = AroundTheClockConfig(get("skip").toBoolean(), get("doubles").toBoolean()),
            countUpRounds = get("rounds").toInt(),
            input = InputMode.valueOf(get("input")),
        )
    }.getOrNull()
}