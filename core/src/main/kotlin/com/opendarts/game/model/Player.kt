package com.opendarts.game.model

data class Player(val id: Int, val name: String) {
    init { require(name.isNotBlank()) { "player name must not be blank" } }
}