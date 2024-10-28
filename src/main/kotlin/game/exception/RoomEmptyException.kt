package top.jie65535.mirai.game.exception

import top.jie65535.mirai.game.Game

class RoomEmptyException(
    override val game: Game
) : Exception("人数不足，无法开始"), GameException