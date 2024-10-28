package top.jie65535.mirai.game

import kotlinx.coroutines.*
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.Member
import top.jie65535.mirai.JLiarsBar
import top.jie65535.mirai.game.exception.RoomEmptyException
import top.jie65535.mirai.game.exception.RoomFullException
import top.jie65535.mirai.game.exception.UnableOperateInGameException
import top.jie65535.mirai.game.player.Player
import java.time.LocalDateTime
import kotlin.coroutines.CoroutineContext

/**
 * 游戏核心类
 */
abstract class Game(
    /**
     * 游戏所在群聊
     */
    val group: Group,

    /**
     * 房主
     */
    val master: Member,

    /**
     * 上层协程作用域
     */
    parentCoroutineContext: CoroutineContext,

    /**
     * 最大玩家数
     */
    val maxPlayers: Int = 4,

    /**
     * 最少玩家数
     */
    val minPlayers: Int = 2,
) {
    /**
     * 游戏状态
     */
    var state: GameState = GameState.IDLE
        private set

    /**
     * 创建时间
     */
    val createdAt = LocalDateTime.now()

    /**
     * 更新时间
     */
    var updatedAt = LocalDateTime.now()
        private set

    /**
     * 游戏协程作用域
     */
    protected val scope = CoroutineScope(
        SupervisorJob(parentCoroutineContext.job)
            .plus(Dispatchers.IO)
            .plus(CoroutineExceptionHandler { _, throwable ->
                // 强行停止游戏
                //  stop()
                JLiarsBar.logger.error("游戏协程发生未处理异常", throwable)
            })
    )

    /**
     * 游戏玩家列表
     */
    val players: MutableList<Player> = mutableListOf()

    /**
     * 通过ID查找玩家
     */
    fun findPlayerById(userId: Long): Player? {
        return players.firstOrNull { it.user.id == userId }
    }

    /**
     * 加入房间
     *
     * @param user 玩家
     */
    @Throws(RoomFullException::class, UnableOperateInGameException::class)
    fun join(user: Member) {
        if (state == GameState.GAMING) {
            throw UnableOperateInGameException(this)
        }
        if (players.size >= maxPlayers) {
            throw RoomFullException(this)
        }
        if (players.any { it.user == user }) {
            return
        }
        players.add(createPlayer(user))
        updatedAt = LocalDateTime.now()
    }

    /**
     * 退出房间
     *
     * @param player 玩家
     */
    @Throws(UnableOperateInGameException::class)
    fun leave(player: Player) {
        if (state == GameState.GAMING) {
            throw UnableOperateInGameException(this)
        }
        players.remove(player)
        updatedAt = LocalDateTime.now()
    }

    /**
     * 开始游戏
     */
    @Throws(UnableOperateInGameException::class, RoomEmptyException::class)
    fun start() {
        if (state == GameState.GAMING) {
            throw UnableOperateInGameException(this)
        }
        if (players.size < minPlayers) {
            throw RoomEmptyException(this)
        }

        // 转为游戏状态
        state = GameState.GAMING
        updatedAt = LocalDateTime.now()
        onGameStarted()
    }

    /**
     * 结束游戏
     */
    fun stop() {
        state = GameState.IDLE
        updatedAt = LocalDateTime.now()
    }

    /**
     * 清理
     */
    fun clear() {
        players.clear()
        updatedAt = LocalDateTime.now()
    }


    /**
     * 检查游戏是否胜利
     */
    fun checkIsWin(): Boolean {
        var alive = 0
        for (player in players) {
            alive += if (player.isAlive) 1 else 0
        }
        // 仅剩一名玩家时胜利
        return alive == 1
    }

    /**
     * 创建一个玩家
     */
    abstract fun createPlayer(user: Member): Player

    /**
     * 当游戏开始时调用
     */
    abstract fun onGameStarted()

    /**
     * 当游戏停止时调用
     */
    abstract fun onGameStopped()
}