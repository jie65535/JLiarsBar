package top.jie65535.mirai.game

enum class GameState {
    /**
     * 空闲状态
     *
     * 当房主开始游戏时进入游戏状态
     */
    IDLE,

    /**
     * 游戏状态
     *
     * 当游戏结束时回到空闲状态
     */
    GAMING,
}