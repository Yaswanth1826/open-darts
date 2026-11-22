package com.opendarts.game.model

/** [bot] is 0 for a person; otherwise the three-dart X01 average the computer opponent plays at. */
data class Player(val id: Int, val name: String, val bot: Int = 0) {
    init { require(name.isNotBlank()) { "player name must not be blank" } }

    val isBot: Boolean get() = bot > 0
}
