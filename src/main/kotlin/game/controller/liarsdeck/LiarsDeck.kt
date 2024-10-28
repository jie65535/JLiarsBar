package top.jie65535.mirai.game.controller.liarsdeck

import kotlinx.coroutines.cancel
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.Member
import net.mamoe.mirai.event.events.GroupMessageEvent
import top.jie65535.mirai.game.Game
import top.jie65535.mirai.game.player.Player
import kotlin.coroutines.CoroutineContext

/**
 * 骗子扑克
 *
 * 玩家轮流出牌并声明牌面数值。这个数值必须与桌面上的要求匹配（例如“国王”或“皇后”）。
 *
 * 当玩家出牌时，他们可能会对牌的数值撒谎。如果你怀疑他们在撒谎，可以揭穿他们的虚张声势。
 *
 * 如果有人被揭穿并且确实撒谎了，他们必须进行一轮俄罗斯轮盘。6发子弹中活下来一发意味着你可以继续游戏，失败则意味着游戏结束。
 *
 * 进行完俄罗斯轮盘后，牌局将重置，游戏继续进行。
 */
class LiarsDeck(group: Group, master: Member, parentCoroutineContext: CoroutineContext)
    : Game(group, master, parentCoroutineContext, MAX_PLAYERS, MIN_PLAYERS) {

    companion object {
        /**
         * 扑克游戏最多4个人玩
         */
        private const val MAX_PLAYERS = 4

        /**
         * 最少2个人玩
         */
        private const val MIN_PLAYERS = 2

        /**
         * TODO 套牌
         */
        private val DECK = arrayOf(11)

    }

    override fun createPlayer(user: Member): DeckPlayer {
        return DeckPlayer(user)
    }


    /**
     * 游戏开始时调用
     */
    override fun onGameStarted() {

    }

    /**
     * 游戏结束时调用
     */
    override fun onGameStopped() {
        scope.cancel("游戏已结束")

    }
}