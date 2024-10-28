package top.jie65535.mirai.game.controller.liarsdeck

import net.mamoe.mirai.contact.Member
import top.jie65535.mirai.game.player.Player

/**
 * 扑克游戏玩家
 */
class DeckPlayer(user: Member) : Player(user) {
    override fun loss(): Boolean {
        return false
    }
}