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

import android.os.Build
import com.venus.backgroundopt.hook.constants.ClassConstants

/**
 * @author XingC
 * @date 2024/4/12
 */
object SystemUtils {
    @JvmField
    val androidVersionCode: Int = Build.VERSION.SDK_INT

    @JvmField
    val isUOrHigher: Boolean = androidVersionCode >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    /**
     * HyperOS 4 / Android 17 (SDK 37) 起, 框架把 oom 相关类下沉到 psc 子包。
     *
     * 注意: 不能用 Class.forName(name, false, null) 探测 —— system_server 的框架类
     * 并非由 bootstrap classLoader 加载, 那样探测恒为 false, 会导致后续读取
     * 已被删除的 ProcessRecord#mState 抛 NoSuchFieldError。
     */
    @JvmStatic
    val isPscLayout: Boolean by lazy {
        val candidates = arrayOf<ClassLoader?>(
            SystemUtils::class.java.classLoader,
            Thread.currentThread().contextClassLoader,
            ClassLoader.getSystemClassLoader(),
            null
        )
        for (classLoader in candidates) {
            try {
                Class.forName(ClassConstants.PscOomAdjuster, false, classLoader)
                return@lazy true
            } catch (ignore: Throwable) {
            }
        }
        false
    }

    /** 运行时解析 OomAdjuster 类名 */
    @JvmStatic
    val oomAdjusterClassName: String by lazy {
        if (isPscLayout) ClassConstants.PscOomAdjuster else ClassConstants.OomAdjuster
    }
}