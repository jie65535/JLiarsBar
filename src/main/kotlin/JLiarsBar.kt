package top.jie65535.mirai

import net.mamoe.mirai.console.permission.PermissionId
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.permission.PermissionService
import net.mamoe.mirai.utils.info

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

    override fun onEnable() {
        // 注册游戏权限
        PermissionService.INSTANCE.register(gamePermission, "JLiarsBar Game Permission")
        logger.info { "Plugin loaded" }

        // 注册插件命令
        PluginCommands.register()
    }
}