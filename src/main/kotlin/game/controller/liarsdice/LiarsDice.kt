package top.jie65535.mirai.game.controller.liarsdice

import kotlinx.coroutines.*
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.Member
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.EventPriority
import net.mamoe.mirai.event.ListeningStatus
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.globalEventChannel
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.MessageSource.Key.quote
import net.mamoe.mirai.message.data.buildMessageChain
import top.jie65535.mirai.game.Game
import top.jie65535.mirai.game.player.Player
import top.jie65535.mirai.utils.plainText
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * 大话骰
 *
 * 掷出你的骰子。没有人能看到你的点数，但所有人都在盯着你接下来的动作。
 *
 * 轮到你时，预测桌上总骰子的数量，并宣布一个具体的点数（例如：“有五个 3”）。
 *
 * 下一位玩家必须通过增加骰子数量或提高点数来加注（例如：“有六个 4”），或者质疑上一个人的预测。
 *
 * 觉得有人在撒谎？挑战他们的出价！失败者要喝下一瓶毒药。
 *
 * 喝下两瓶毒药，你就出局了！最后活下来的人获胜！
 */
class LiarsDice(group: Group, master: Member, parentCoroutineContext: CoroutineContext)
    : Game(group, master, parentCoroutineContext, MAX_PLAYERS, MIN_PLAYERS) {

    companion object {
        /**
         * 骰子游戏最多5个人玩
         */
        private const val MAX_PLAYERS = 4

        /**
         * 最少2个人玩
         */
        private const val MIN_PLAYERS = 2

        /**
         * 骰子游戏每人最多2瓶毒药
         */
        const val MAX_HP = 2

        /**
         * 最大骰子数
         */
        const val MAX_DICES = 5

        /**
         * 最大骰子点数
         */
        private const val MAX_DICE_POINTS = 6

        /**
         * 玩家报数超时时间 默认120秒
         */
        private val PLAYER_CALL_TIMEOUT = 3.minutes

        /**
         * 最少要报几个骰子
         */
        private const val PLAYER_CALL_MIN_DICES = 2

        /**
         * 报数格式 如 3个3 或 3*4 等
         */
        private val CALL_FORMAT = Regex("^(\\d+)[*个x×](\\d)$")
    }

    /**
     * 当前玩家索引
     */
    private var currentPlayerIndex: Int = 0

    /**
     * 当前玩家
     */
    private val currentPlayer: DicePlayer
        get() = players[currentPlayerIndex] as DicePlayer

    /**
     * 上一位玩家索引
     */
    private var lastPlayerIndex: Int? = null

    /**
     * 上一位玩家
     */
    private val lastPlayer: DicePlayer?
        get() = lastPlayerIndex?.let { players[it] as DicePlayer }

    /**
     * 最后一次报的骰子数量
     */
    private var lastDiceCount: Int = 0

    /**
     * 最后一次报的骰子点数
     */
    private var lastDicePoint: Int = 0

    /**
     * 游戏轮数，开蛊时增加
     */
    private var rounds = 0

    /**
     * 全场骰子点数计数器
     */
    private val pointCounts = IntArray(MAX_DICE_POINTS)

    /**
     * 创建玩家
     */
    override fun createPlayer(user: Member): DicePlayer {
        return DicePlayer(user)
    }

    /**
     * 游戏开始时调用
     */
    override fun onGameStarted() {
        for (p in players) {
            p.hp = MAX_HP
        }
        startNewRound()
    }

    /**
     * 开始新一轮游戏
     */
    private fun startNewRound() {
        // 从随机玩家开始
        do {
            currentPlayerIndex = Random.nextInt(players.indices)
        } while (currentPlayer.isOut)
        lastPlayerIndex = null
        lastDiceCount = 0
        lastDicePoint = 0
        rounds += 1

        // 开始投骰子
        roll()

        // 启动游戏流程
        scope.launch(Dispatchers.IO) {
            run()

            delay(3.seconds)

            if (checkIsWin()) {
                val p = players.first { it.isAlive }
                group.sendMessage(buildMessageChain {
                    append("游戏结束了，恭喜")
                    append(At(p.user))
                    append("笑到了最后！")
                })
                stop()
            } else {
                group.sendMessage("即将开始下一轮...")
                delay(3.seconds)
                // 游戏还没结束则再来一局
                startNewRound()
            }
        }
    }

    /**
     * 单回游戏循环主体
     */
    private suspend fun run() {
        // 私信提示
        group.sendMessage("已将新骰子结果发送到私信，请确认自己的点数")

        // 广播给每一个玩家点数
        broadcastRolls()

        // 游戏正式开始
        val currentRound = rounds
        // 游戏循环
        while (true) {
            group.sendMessage(buildMessageChain {
                val prev = lastPlayer
                if (prev == null) {
                    append("请")
                    append(At(currentPlayer.user))
                    append("先报数，你可以发送[x个y]或[x*y]开始")
                } else {
                    appendLine("${prev.user.nameCardOrNick} 喊出 $lastDiceCount 个 $lastDicePoint")
                    append("轮到")
                    append(At(currentPlayer.user))
                    appendLine("，要[开]吗？")
                    append("或用[x个y]来报数")
                }
            })
            update()
            if (currentRound == rounds) {
                nextPlayer()
            } else {
                break
            }
        }
    }

    /**
     * 等待当前玩家发言，超时将直接判负玩家，开蛊结算也在内部处理。
     */
    private suspend fun update() {
        val result = withTimeoutOrNull(PLAYER_CALL_TIMEOUT) {
            globalEventChannel()
                .subscribe<GroupMessageEvent>(priority = EventPriority.HIGH) {
                    if (it.group.id != group.id || it.sender.id != currentPlayer.user.id) {
                        // 游戏内忽略非当前玩家的发言
                        ListeningStatus.LISTENING
                    } else {
                        // 处理当前玩家发言
                        if (playerCall(it)) {
                            // 有效发言则结束监听
                            ListeningStatus.STOPPED
                        } else {
                            // 否则继续监听
                            ListeningStatus.LISTENING
                        }
                    }
                }.join()
        }
        // 等待发言超时
        if (result == null) {
            // 直接判负玩家
            currentPlayer.getOut()
            rounds += 1
            group.sendMessage(buildMessageChain {
                append("由于")
                append(At(currentPlayer.user))
                append("较长时间未回应，已被淘汰出局")
            })
        }
    }

    /**
     * 当前玩家发言
     *
     * @param event 群友消息事件
     */
    private suspend fun playerCall(event: GroupMessageEvent): Boolean {
        when (val content = event.message.plainText()) {
            "开", "劈", "质疑" -> {
                return playerCallOpen(event)
            }
            "+1", "加一" -> {
                return playerCallNumber(event, lastDiceCount + 1, lastDicePoint)
            }
            "+2", "加二" -> {
                return playerCallNumber(event, lastDiceCount + 2, lastDicePoint)
            }
            "+3", "加三" -> {
                return playerCallNumber(event, lastDiceCount + 3, lastDicePoint)
            }
            else -> {
                // 匹配自定义输入的数字
                CALL_FORMAT.find(content)?.let {
                    val (count, point) = it.destructured
                    return playerCallNumber(event, count.toInt(), point.toInt())
                }
                return false
            }
        }
    }

    /**
     * 玩家报数
     */
    private suspend fun playerCallNumber(event: GroupMessageEvent, count: Int, point: Int): Boolean {
        // 拦下事件
        event.intercept()
        val totalDices = players.count { it.isAlive } * MAX_DICES
        val msg = if (count > totalDices) {
            "一共只有 $totalDices 个骰子！"
        } else if (count < PLAYER_CALL_MIN_DICES) {
            "至少要报 $PLAYER_CALL_MIN_DICES 个起"
        } else if (point < 1 || point > MAX_DICE_POINTS) {
            "骰子点数只有 [1]~[$MAX_DICE_POINTS]！"
        } else if (lastDiceCount != 0) {
            if (count < lastDiceCount) {
                "数量不能比上家少！"
            } else if (count == lastDiceCount && point < lastDicePoint) {
                "数量相同时点数不能比上家小！"
            } else {
                ""
            }
        } else {
            ""
        }

        if (msg.isNotEmpty()) {
            event.subject.sendMessage(event.message.quote() + msg)
            return false
        }
        // 记录为最新的报数
        lastDiceCount = count
        lastDicePoint = point
        return true
    }

    /**
     * 玩家开蛊
     */
    private suspend fun playerCallOpen(event: GroupMessageEvent): Boolean {
        // 拦下事件
        event.intercept()
        val target = lastPlayer
        if (target == null || lastDiceCount == 0 || lastDicePoint == 0) {
            return false
        }

        event.subject.sendMessage(event.message.quote() +
                "${target.user.nameCardOrNick}(${target.hp}/${MAX_HP})：${target.dicesText}")

        delay(1.seconds)

        event.subject.sendMessage(buildMessageChain {
            for (p in players.filter { it.isAlive && it != target}) {
                p as DicePlayer
                appendLine("${p.user.nameCardOrNick}(${p.hp}/${MAX_HP})：${p.dicesText}")
            }
        })

        delay(3.seconds)

        event.subject.sendMessage(At(target.user) + "说有 $lastDiceCount 个 $lastDicePoint")

        delay(1.seconds)

        event.subject.sendMessage("实际上有 ${pointCounts[lastDicePoint - 1]} 个 $lastDicePoint")

        delay(3.seconds)

        // 如果上家报的骰数超过总骰数则判负
        if (lastDiceCount > pointCounts[lastDicePoint - 1]) {
            val isOut = target.loss()
            event.subject.sendMessage(At(target.user) + if (isOut) "出局！" else "输了，HP-1")
        } else {
            // 否则当前玩家判负
            val isOut = currentPlayer.loss()
            event.subject.sendMessage(At(currentPlayer.user) + if (isOut) "猜错了，出局！" else "猜错了，HP-1")
        }
        rounds += 1
        return true
    }

    /**
     * 广播给每一位玩家摇出来的骰子
     */
    private suspend fun broadcastRolls() {
        for (player in players.filter(Player::isAlive)) {
            player as DicePlayer
            // 私发消息通知
            player.user.sendMessage("你摇到了 ${player.dicesText}")
            // 人为延迟避免高频发送消息被检测
            delay(250.milliseconds)
        }
    }

    /**
     * 下一位玩家
     */
    private fun nextPlayer(): DicePlayer {
        lastPlayerIndex = currentPlayerIndex
        var nextPlayer: DicePlayer
        // 找到下一位存活的玩家
        do {
            currentPlayerIndex = (currentPlayerIndex + 1) % players.size
            nextPlayer = players[currentPlayerIndex] as DicePlayer
        } while (nextPlayer.isOut)
        return nextPlayer
    }

    /**
     * 为所有存活的玩家掷骰子
     */
    private fun roll() {
        // 清空全局点数计数器
        for (i in pointCounts.indices) {
            pointCounts[i] = 0
        }
        // 玩家点数计数器
        val playerPointCounts = IntArray(MAX_DICE_POINTS)
        // 遍历所有还未淘汰的玩家
        for (player in players.filter { it.isAlive }) {
            player as DicePlayer
            singleRoll@ do {
                // 清空玩家点数计数器
                for (i in playerPointCounts.indices) {
                    playerPointCounts[i] = 0
                }

                // 郑骰子
                for (i in player.dices.indices) {
                    val point = Random.nextInt(1..MAX_DICE_POINTS)
                    player.dices[i] = point
                    playerPointCounts[point - 1] += 1
                }

                // 检查玩家是否存在重复的骰子，如果没有则再投一次
                for (count in playerPointCounts) {
                    if (count >= 2) {
                        break@singleRoll
                    }
                }
            } while (true)

            // 记录骰子总数
            for (i in 0 until MAX_DICE_POINTS) {
                pointCounts[i] += playerPointCounts[i]
            }
        }
    }

    /**
     * 游戏结束时调用
     */
    override fun onGameStopped() {
        scope.cancel("游戏已结束")
    }


}