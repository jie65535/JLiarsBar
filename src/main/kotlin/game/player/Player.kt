package top.jie65535.mirai.game.player

import net.mamoe.mirai.contact.Member

/**
 * 玩家类
 */
abstract class Player(
    /**
     * 用户
     */
    val user: Member
) {
    /**
     * 是否已出局
     */
    val isOut: Boolean
        get() = hp <= 0

    /**
     * 是否存活
     */
    val isAlive: Boolean
        get() = hp > 0

    /**
     * 玩家生命值，归 0 时出局
     */
    var hp: Int = 1

    /**
     * 玩家失败时调用，返回玩家是否出局
     *
     * @return 是否出局
     */
    abstract fun loss(): Boolean

    /**
     * 玩家出局
     */
    fun getOut() {
        hp = 0
    }

    override fun hashCode(): Int {
        return user.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Player

        return user == other.user
    }
}