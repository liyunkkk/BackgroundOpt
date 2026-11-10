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
     * HyperOS 4 / Android 17 (SDK 37) 起, 框架把 oom 相关类下沉到 psc 子包:
     * [ClassConstants.OomAdjuster] -> [ClassConstants.PscOomAdjuster],
     * ProcessStateRecord 被删除并入 [ClassConstants.PscProcessRecordInternal]。
     *
     * 是否为 psc 布局的惰性探测结果。
     * 通过「psc.OomAdjuster 类是否存在」判断, 不依赖具体版本号, 以兼容更多 ROM。
     * 探测过程中的任何异常都被吞掉并返回 false, 绝不向上抛出,
     * 以免影响 AndroidHookHandler 中后续 hook 的注册。
     *
     * 注意: 框架类位于 bootstrap classLoader, 因此用 null 作为 classLoader 探测,
     * 避免模块自身 classLoader 找不到框架类而误判。
     */
    @JvmStatic
    val isPscLayout: Boolean by lazy {
        // 注意: 之前用 Class.forName(name, false, null) 探测必然失败 ——
        // system_server 的框架类并不是由 bootstrap classLoader 加载的,
        // 结果 isPscLayout 恒为 false, 于是代码走了旧布局分支,
        // 在 Android 17 上抛 NoSuchFieldError: ProcessRecord#mState,
        // 导致进程表永远为空(界面列表空白)。
        // 这里依次尝试多个 classLoader, 任何一个能加载到 psc.OomAdjuster 就判定为新布局。
        val candidates = arrayOf<ClassLoader?>(
            SystemUtils::class.java.classLoader,          // 模块自身(其父加载器可见框架类)
            Thread.currentThread().contextClassLoader,    // 线程上下文
            ClassLoader.getSystemClassLoader(),
            null                                          // bootstrap
        )
        for (classLoader in candidates) {
            try {
                Class.forName(ClassConstants.PscOomAdjuster, false, classLoader)
                return@lazy true
            } catch (ignore: Throwable) {
                // 换下一个 classLoader 继续尝试
            }
        }
        false
    }

    /**
     * 运行时解析 OomAdjuster 的类名。
     * psc 布局返回 [ClassConstants.PscOomAdjuster], 否则返回旧的 [ClassConstants.OomAdjuster]。
     */
    @JvmStatic
    val oomAdjusterClassName: String by lazy {
        if (isPscLayout) {
            ClassConstants.PscOomAdjuster
        } else {
            ClassConstants.OomAdjuster
        }
    }
}
