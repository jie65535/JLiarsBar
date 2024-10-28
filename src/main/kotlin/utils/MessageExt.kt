package top.jie65535.mirai.utils

import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.PlainText

fun MessageChain.plainText() = this.filterIsInstance<PlainText>().joinToString().trim()