package top.jie65535.mirai.game.controller.liarsdice

import net.mamoe.mirai.contact.Member
import top.jie65535.mirai.game.player.Player

/**
 * 骰子玩家
 */
class DicePlayer(user: Member) : Player(user) {
    /**
     * 玩家蛊内骰子
     */
    val dices = IntArray(LiarsDice.MAX_DICES)

    /**
     * 骰子字符串，类似 `[1][2][3][3][4]`
     */
    val dicesText: String
        get() = buildString {
            for (dice in dices) {
                append('[')
                append(dice)
                append(']')
            }
        }

    /**
     * 玩家失败时，扣一点血。
     *
     * @return 是否出局
     */
    override fun loss(): Boolean {
        if (hp > 0) {
            hp -= 1
        }
        return isOut
    }
}