package top.jie65535.mirai

import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.permission.PermissionService.Companion.permit
import net.mamoe.mirai.console.permission.PermitteeId.Companion.permitteeId
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.Member
import net.mamoe.mirai.contact.User

class PluginCommands : CompositeCommand(
    JLiarsBar, "jlb", description = "J Liars Bar Commands"
) {
    @SubCommand
    suspend fun CommandSender.enable(contact: Contact) {
        when (contact) {
            is Member -> contact.permitteeId.permit(JLiarsBar.gamePermission)
            is User -> contact.permitteeId.permit(JLiarsBar.gamePermission)
            is Group -> contact.permitteeId.permit(JLiarsBar.gamePermission)
        }
        sendMessage("OK")
    }

    @SubCommand
    suspend fun CommandSender.disable(contact: Contact) {
        when (contact) {
            is Member -> contact.permitteeId.permit(JLiarsBar.gamePermission)
            is User -> contact.permitteeId.permit(JLiarsBar.gamePermission)
            is Group -> contact.permitteeId.permit(JLiarsBar.gamePermission)
        }
        sendMessage("OK")
    }

    @SubCommand
    @Description("强制停止当前正在进行的游戏")
    suspend fun CommandSender.stop() {
        sendMessage("OK")
    }

}