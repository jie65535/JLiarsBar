package top.jie65535.mirai.game.exception

import top.jie65535.mirai.game.Game

class RoomFullException(
    override val game: Game
) : Exception("房间已满，无法加入"), GameException