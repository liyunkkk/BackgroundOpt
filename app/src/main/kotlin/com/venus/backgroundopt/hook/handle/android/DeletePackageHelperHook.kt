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

import com.venus.backgroundopt.BuildConfig
import com.venus.backgroundopt.core.RunningInfo
import com.venus.backgroundopt.hook.base.HookPoint
import com.venus.backgroundopt.hook.base.MethodHook
import com.venus.backgroundopt.hook.base.action.beforeHookAction
import com.venus.backgroundopt.hook.constants.ClassConstants
import com.venus.backgroundopt.hook.constants.MethodConstants
import de.robv.android.xposed.XC_MethodHook.MethodHookParam

/**
 * @author XingC
 * @date 2023/7/24
 */
class DeletePackageHelperHook(
    classLoader: ClassLoader?,
    hookInfo: RunningInfo?
) : MethodHook(classLoader, hookInfo) {
    override fun getHookPoint(): Array<HookPoint> {
        return arrayOf(
//            HookPoint(
//                ClassConstants.DeletePackageHelper,
//                MethodConstants.deletePackageX,
//                arrayOf(
//                    beforeHookAction {
//                        handleDeletePackageX(it)
//                    }
//                ),
//                String::class.java, /* packageName */
//                Long::class.java,   /* versionCode */
//                Int::class.java,    /* userId */
//                Int::class.java,    /* deleteFlags */
//                Boolean::class.java /* removedBySystem */
//            ),
            // HyperOS 4 / Android 17 (SDK 37) 起, deletePackageLIF 的签名发生变化,
            // 去掉 paramTypes 按方法名 hook, 避免因签名不符导致 hook 失败。
            HookPoint(
                ClassConstants.DeletePackageHelper,
                MethodConstants.deletePackageLIF,
                arrayOf(
                    beforeHookAction {
                        handleDeletePackageLIF(it)
                    }
                ),
            ).setHookAllMatchedMethod(true),
        )
    }

    private fun handleDeletePackageX(param: MethodHookParam) {
        val packageName = param.args[0] as String

        runningInfo.removeAllRecordedFindAppResult(packageName)

        if (BuildConfig.DEBUG) {
            logger.debug("卸载: $packageName")
        }
    }

    private fun handleDeletePackageLIF(param: MethodHookParam) {
        val args = param.args
        val packageName = args[0] as String
        // 签名变化后, allUserHandles 的位置可能不同, 做防御性处理
        val userIds = args.getOrNull(3) as? IntArray ?: return

        userIds.forEach { userId ->
            runningInfo.removeRecordedFindAppResult(userId, packageName)
        }
    }
}
