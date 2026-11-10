/*
 * Copyright (C) 2023 BackgroundOpt
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.venus.backgroundopt.utils

import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * HyperOS 4 适配期使用的文件日志。
 *
 * 本机 (Xiaomi 13 / HyperOS OS4.0) 的 logd 异常, LSPosed 提示
 * "Logd maybe crashed", 模块日志既进不了 logcat 也进不了 /data/adb/lspd/log,
 * 因此这里把关键错误写入文件, 便于排查 hook 注册失败与运行期异常。
 * 只记录失败/异常, 不记录正常流程, 避免刷屏。
 *
 * 注意: 写日志失败绝不允许影响模块本身。
 */
object BgDebugLog {
    private const val LOG_FILE = "/data/system/bglog/module.log"

    private val formatter = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    @JvmStatic
    fun log(msg: String) {
        try {
            val file = File(LOG_FILE)
            file.parentFile?.mkdirs()
            file.appendText("${formatter.format(Date())} [${Process.myPid()}] $msg\n")
        } catch (ignore: Throwable) {
        }
    }

    @JvmStatic
    fun log(msg: String, t: Throwable) {
        try {
            val sw = StringWriter()
            t.printStackTrace(PrintWriter(sw))
            log("$msg -> ${t.javaClass.name}: ${t.message}\n$sw")
        } catch (ignore: Throwable) {
        }
    }
}
