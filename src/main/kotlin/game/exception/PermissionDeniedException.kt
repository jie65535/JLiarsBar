package top.jie65535.mirai.game.exception

import top.jie65535.mirai.game.Game

class PermissionDeniedException(
    override val game: Game
) : Exception("你不是房主，无法进行此操作"), GameException