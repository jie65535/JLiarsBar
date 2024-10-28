package top.jie65535.mirai.game.exception

import top.jie65535.mirai.game.Game

class PlayerNotFoundException(
    override val game: Game
) : Exception("该玩家不存在于本场游戏"), GameException