package me.rerere.rikkahub.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * 读取剪贴板文本内容
 */
fun readClipboardText(context: Context): String {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return clipboard.primaryClip?.let { clip ->
        buildString {
            repeat(clip.itemCount) { index ->
                append(clip.getItemAt(index).text ?: "")
            }
        }
    } ?: ""
}

/**
 * 写入文本到剪贴板
 */
fun writeClipboardText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("RikkaHub", text)
    clipboard.setPrimaryClip(clip)
}

/**
 * 扩展函数：从 ClipData 获取文本
 */
fun ClipData.getText(): String {
    return buildString {
        repeat(itemCount) {
            append(getItemAt(it).text ?: "")
        }
    }
}
