package top.jie65535.mirai.game.exception

import top.jie65535.mirai.game.Game

/**
 * 游戏中无法进行操作时抛出
 */
class UnableOperateInGameException(
    override val game: Game
) : Exception("游戏中无法进行此操作"), GameException