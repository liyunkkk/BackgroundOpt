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

package com.venus.backgroundopt.hook.handle.android

import com.venus.backgroundopt.core.RunningInfo
import com.venus.backgroundopt.hook.base.IHook
import com.venus.backgroundopt.utils.BgDebugLog
import com.venus.backgroundopt.utils.beforeHook

/**
 * HyperOS 4 / Android 17 适配期探针。
 *
 * 目的: 确认 Android 17 (psc 布局) 上「设置 oom_score_adj」的真实调用链。
 * 模块原本 hook com.android.server.am.ProcessList#setOomAdj 来接管 adj,
 * 但在本机实测该 hook 从未触发, 需要先确定框架实际走的是哪条路径。
 *
 * 定位完成后本类可以删除。
 */
class OomAdjProbeHook(
    classLoader: ClassLoader?,
    runningInfo: RunningInfo?
) : IHook(classLoader, runningInfo) {
    private val lastLogTime = HashMap<String, Long>()

    private fun probe(tag: String, detail: () -> String) {
        try {
            val now = System.currentTimeMillis()
            synchronized(lastLogTime) {
                val last = lastLogTime[tag] ?: 0L
                if (now - last < 2000L) {
                    return
                }
                lastLogTime[tag] = now
            }
            BgDebugLog.log("PROBE $tag: " + detail())
        } catch (ignore: Throwable) {
        }
    }

    private fun install(className: String, methodName: String, tag: String) {
        try {
            className.beforeHook(
                classLoader = classLoader!!,
                methodName = methodName,
                hookAllMethod = true
            ) { param ->
                probe(tag) {
                    val args = param.args?.joinToString(",") { it?.toString() ?: "null" }
                    "args=[$args]"
                }
            }
        } catch (t: Throwable) {
            BgDebugLog.log("PROBE install FAILED: $className#$methodName", t)
        }
    }

    override fun hook() {
        if (classLoader == null) {
            return
        }

        install("com.android.server.am.ProcessList", "setOomAdj", "ProcessList.setOomAdj")
        install(
            "com.android.server.am.psc.OomAdjuster\$Injector",
            "setOomAdj",
            "Injector.setOomAdj"
        )
        install(
            "com.android.server.am.psc.OomAdjuster",
            "applyOomAdjLSP",
            "OomAdjuster.applyOomAdjLSP"
        )
        install(
            "com.android.server.am.psc.OomAdjusterImpl",
            "applyOomAdjLSP",
            "OomAdjusterImpl.applyOomAdjLSP"
        )
        install(
            "com.android.server.am.psc.OomAdjuster",
            "applyResultsLSP",
            "OomAdjuster.applyResultsLSP"
        )
        install(
            "com.android.server.am.psc.UFwOomAdjuster",
            "applyOomAdjLSP",
            "UFwOomAdjuster.applyOomAdjLSP"
        )
    }
}
