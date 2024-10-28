package top.jie65535.mirai

import kotlinx.coroutines.*
import net.mamoe.mirai.console.permission.PermissionId
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandSender.Companion.toCommandSender
import net.mamoe.mirai.console.permission.PermissionService
import net.mamoe.mirai.console.permission.PermissionService.Companion.hasPermission
import net.mamoe.mirai.contact.isOperator
import net.mamoe.mirai.event.EventPriority
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.events.UserMessageEvent
import net.mamoe.mirai.event.globalEventChannel
import net.mamoe.mirai.event.nextEvent
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.MessageSource.Key.quote
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.utils.info
import top.jie65535.mirai.game.Game
import top.jie65535.mirai.game.GameState
import top.jie65535.mirai.game.controller.liarsdeck.LiarsDeck
import top.jie65535.mirai.game.controller.liarsdice.LiarsDice
import top.jie65535.mirai.utils.plainText
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

object JLiarsBar : KotlinPlugin(
    JvmPluginDescription(
        id = "top.jie65535.mirai.JLiarsBar",
        name = "JLiarsBar",
        version = "0.1.0",
    ) {

        author("jie65535")
    }
) {
    /**
     * 参加游戏的权限
     */
    val gamePermission = PermissionId("JLiarsBar", "Game")

    /**
     * 所有正在进行的游戏
     */
    private val games = mutableMapOf<Long, Game>()

    override fun onEnable() {
        // 注册游戏权限
        PermissionService.INSTANCE.register(gamePermission, "JLiarsBar Game Permission")
        // 注册插件命令
        PluginCommands.register()

        val eventChannel = GlobalEventChannel.parentScope(this)
        eventChannel.subscribeAlways<GroupMessageEvent> { event -> onGroupMessage(event) }
        eventChannel.subscribeAlways<UserMessageEvent> { event -> onUserMessage(event) }
        logger.info { "Plugin loaded" }
    }

    /**
     * 收到群消息时触发
     */
    private suspend fun onGroupMessage(event: GroupMessageEvent) {
        val game = games[event.group.id]
        val input = event.message.plainText()

        // 如果还没创建游戏时
        if (game == null) {
            // 关键字开始创建
            if (input != "骗子酒馆" && input != "骗子酒吧")
                return
            // 检查权限
            if (!event.toCommandSender().hasPermission(gamePermission))
                return
            // 尝试创建游戏
            tryCreateGame(event)
        } else {
            // 如果已经创建了游戏，根据输入处理
            when (input) {
                // 管理员强制结束游戏
                "结束", "停止" -> {
                    if (event.sender.isOperator()) {
                        game.stop()
                        games.remove(event.group.id)
                        event.subject.sendMessage("游戏已被管理员强制结束，欢迎下次光临~")
                    }
                }
                "骗子酒吧", "骗子酒馆", "加入", "进入", "来" -> {
                    tryJoinGame(event, game)
                }
                "开始", "继续", "开", "再来" -> {
                    tryStartGame(event, game)
                }
                "退出", "离开", "溜了" -> {
                    tryLeftGame(event, game)
                }
            }
        }
    }

    /**
     * 尝试创建游戏
     */
    private suspend fun tryCreateGame(event: GroupMessageEvent) {
        // 询问游戏模式
        event.subject.sendMessage(event.message.quote()
//                + "要来一局紧张刺激的[大话骰]还是[扑克牌]？")
                + "要来一局阴险狡诈的[骰子]吗？")
        // 等待对方回话
        val next = withTimeoutOrNull(30.seconds) {
            globalEventChannel().nextEvent<GroupMessageEvent>(EventPriority.HIGH) {
                it.subject.id == event.subject.id && it.sender.id == event.sender.id
            }
        } ?: return

        // 在回话后，先确认是否被别人创建了
        val newGame = games[next.group.id]
        val nextInput = next.message.plainText()
        val created = when (nextInput) {
            "大话骰", "骰子" -> {
                if (newGame != null) {
                    // 加入游戏
                    tryJoinGame(next, newGame)
                } else {
                    // 创建对应的游戏
                    val game = LiarsDice(next.group, next.sender, JLiarsBar.coroutineContext)
                    // 房主加入游戏
                    game.join(next.sender)
                    // 加入游戏队列
                    games[next.group.id] = game
                    // TODO 提示游戏介绍
                    event.subject.sendMessage(event.message.quote() + "游戏已创建，欢迎[加入]游戏")
                }
                true
            }
//            "扑克牌", "纸牌", "扑克", "牌" -> {
//                if (newGame != null) {
//                    // 加入游戏
//                    tryJoinGame(next, newGame)
//                } else {
//                    // 创建对应的游戏
//                    val game = LiarsDeck(next.group, next.sender, JLiarsBar.coroutineContext)
//                    // 房主加入游戏
//                    game.join(next.sender)
//                    // 加入游戏队列
//                    games[next.group.id] = game
//                    // TODO 提示游戏介绍
//                    event.subject.sendMessage(event.message.quote() + "游戏已创建，欢迎[加入]游戏")
//                }
//                true
//            }
            else -> {
                event.subject.sendMessage("不来算了 ╭(╯^╰)╮")
                false
            }
        }


        if (created) {
            // 拦截这个事件
            event.intercept()
            tryStartGcJob()
        }
    }

    private var gcJob: Job? = null
    private fun tryStartGcJob() {
        if (gcJob != null) return
        gcJob = launch {
            do {
                // 每15分钟检查一下
                delay(15.minutes)
                // 删除所有处于空闲状态超过10分钟的游戏
                val t = LocalDateTime.now().minusMinutes(10)
                games.filter { it.value.state == GameState.IDLE && it.value.updatedAt < t }
                    .forEach { games.remove(it.key) }
                // 清空则结束任务
                if (games.isEmpty()) {
                    break
                }
            } while (isActive)
            gcJob = null
        }
    }

    /**
     * 尝试加入游戏
     *
     * @param event 群消息事件
     * @param game 游戏
     */
    private suspend fun tryJoinGame(event: GroupMessageEvent, game: Game) {
        val msg = if (game.state == GameState.GAMING) {
            "游戏进行中，暂时不能加入"
        } else if (game.players.size >= game.maxPlayers) {
            "游戏人数已满，暂时不能加入"
        } else {
            val player = game.findPlayerById(event.sender.id)
            if (player == null) {
                game.join(event.sender)
                "已加入游戏(${game.players.size}/${game.maxPlayers})，房主可以[开始]游戏"
            } else {
                "你已在游戏(${game.players.size}/${game.maxPlayers})内" +
                    if (game.players.size < game.minPlayers) {
                        // 检查人数
                        "，玩家人数不足，欢迎[加入]游戏"
                    } else if (event.sender == game.master) {
                        // 房主则提示玩家开始
                        "，要[开始]游戏吗？"
                    } else {
                        ""
                    }
            }
        }
        // 回复消息
        event.subject.sendMessage(event.message.quote() + msg)
    }

    /**
     * 尝试离开游戏
     *
     * @param event 群消息事件
     * @param game 游戏
     */
    private suspend fun tryLeftGame(event: GroupMessageEvent, game: Game) {
        val msg = if (game.state == GameState.GAMING) {
            "游戏进行中，暂时不能离开"
        } else {
            // 检查玩家是否正在游戏内
            val player = game.findPlayerById(event.sender.id)
            if (player != null) {
                // 判断是否为房主
                if (game.master == event.sender) {
                    // 房主离开游戏则直接解散
                    games.remove(event.group.id)
                    "房主离开游戏，游戏已解散，欢迎下次光临~"
                } else {
                    // 其它成员则可以直接离开
                    game.leave(player)
                    "已离开游戏(${game.players.size}/${game.maxPlayers})"
                }
            } else {
                "你不在游戏中"
            }
        }
        event.subject.sendMessage(event.message.quote() + msg)
    }

    /**
     * 尝试开始游戏
     *
     * @param event 群消息事件
     * @param game 游戏
     */
    private suspend fun tryStartGame(event: GroupMessageEvent, game: Game) {
        val msg = if (game.state == GameState.GAMING) {
            "游戏已在进行中"
        } else {
            val player = game.findPlayerById(event.sender.id)
            if (player == null) {
                "你未加入游戏，要[加入]吗？"
            } else if (game.master != event.sender) {
                "仅房主可以[开始]"
            } else if (game.players.size < game.minPlayers) {
                "人数不足，无法开始"
            } else {
                game.start()
                "游戏开始！"
            }
        }
        event.subject.sendMessage(event.message.quote() + msg)
    }

    private fun findGameByUser(userId: Long): Game? {
        return games.values.firstOrNull { it.findPlayerById(userId) != null }
    }

    private fun onUserMessage(event: UserMessageEvent) {
        event.sender
    }

}