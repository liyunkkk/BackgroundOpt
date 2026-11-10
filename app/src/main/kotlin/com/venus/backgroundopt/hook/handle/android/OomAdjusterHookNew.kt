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

import android.os.SystemClock
import com.venus.backgroundopt.annotation.AndroidMethodReplacement
import com.venus.backgroundopt.core.RunningInfo
import com.venus.backgroundopt.environment.Features
import com.venus.backgroundopt.hook.base.IHook
import com.venus.backgroundopt.hook.constants.ClassConstants
import com.venus.backgroundopt.hook.constants.FieldConstants
import com.venus.backgroundopt.hook.constants.MethodConstants
import com.venus.backgroundopt.hook.handle.android.entity.ActivityManagerConstants
import com.venus.backgroundopt.hook.handle.android.entity.ActivityManagerInternal
import com.venus.backgroundopt.hook.handle.android.entity.AppProfiler
import com.venus.backgroundopt.hook.handle.android.entity.ApplicationExitInfo
import com.venus.backgroundopt.hook.handle.android.entity.ProcessCachedOptimizerRecord
import com.venus.backgroundopt.hook.handle.android.entity.ProcessList.CACHED_APP_MIN_ADJ
import com.venus.backgroundopt.hook.handle.android.entity.ProcessList.UNKNOWN_ADJ
import com.venus.backgroundopt.hook.handle.android.entity.ProcessRecord
import com.venus.backgroundopt.hook.handle.android.entity.ProcessServiceRecord
import com.venus.backgroundopt.hook.handle.android.entity.ProcessStateRecord
import com.venus.backgroundopt.utils.SystemUtils
import com.venus.backgroundopt.utils.afterConstructorHook
import com.venus.backgroundopt.utils.callMethod
import com.venus.backgroundopt.utils.getIntFieldValue
import com.venus.backgroundopt.utils.getObjectFieldValue
import com.venus.backgroundopt.utils.replaceHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam


/**
 * @author XingC
 * @date 2024/2/1
 */
class OomAdjusterHookNew(
    classLoader: ClassLoader?,
    runningInfo: RunningInfo?
) : IHook(classLoader, runningInfo) {
    /**
     * HyperOS 4 / Android 17 (SDK 37) 起, OomAdjuster 被移动到 psc 子包,
     * 原 com.android.server.am.OomAdjuster 不存在。这里通过 SystemUtils.oomAdjusterClassName
     * 在运行时解析正确的类名, 避免硬编码导致 ClassNotFoundError。
     */
    private val oomAdjusterClassName: String
        get() = SystemUtils.oomAdjusterClassName

    // 以下字段在 HyperOS 4 上可能缺失, 因此全部改为可空, 并在构造回调中逐个 try/catch
    private var oomAdjuster: Any? = null
    private var mProcessList: Any? = null
    private var mConstants: Any? = null

    // List<ProcessRecord>
    private var lruList: List<*>? = null
    private var mAppProfiler: Any? = null

    private fun applyOomAdjLSP(
        processRecord: Any,
        now: Long,
        nowElapsed: Long,
        oomAdjReason: Any?
    ) {
        applyOomAdjLSPMethod(processRecord, now, nowElapsed, oomAdjReason)
    }

    /**
     * applyOomAdjLSP方法是否含有oomAdjReason字段
     *
     * 通常情况下, 按照aosp源码做即可。但存在例外情况。
     *
     * 在红米note5 pro(whyred)的Nusantara v5.3 official a13中, 此方法的入参并不同于aosp, 扩展一下,
     * 可能某些rom也会对此修改。因此为了模块的通用性, 改用在第一次使用时动态生成。
     */
    @Volatile
    private var hasOomAdjReasonField: Boolean = SystemUtils.isUOrHigher

    private val applyOomAdjLSPMethod by lazy {
        if (hasOomAdjReasonField) {
            { processRecord: Any, now: Long, nowElapsed: Long, oomAdjReason: Any? ->
                oomAdjuster?.callMethod(
                    methodName = MethodConstants.applyOomAdjLSP,
                    processRecord,
                    true,
                    now,
                    nowElapsed,
                    oomAdjReason
                )
            }
        } else {
            { processRecord: Any, now: Long, nowElapsed: Long, _: Any? ->
                oomAdjuster?.callMethod(
                    methodName = MethodConstants.applyOomAdjLSP,
                    processRecord,
                    true,
                    now,
                    nowElapsed
                )
            }
        }
    }

    private fun getOomAdjReason(param: MethodHookParam): Any? = getOomAdjReasonMethod(param.args)

    @Volatile
    private var getOomAdjReasonMethod = { paramArgs: Array<Any?> ->
        hasOomAdjReasonField = paramArgs.size > 4
        val finalMethod = if (hasOomAdjReasonField) {
            { args: Array<Any?> ->
                args[4]
            }
        } else {
            { _: Array<Any?> ->
                null
            }
        }
        resetOomAdjReasonMethod(finalMethod)
        finalMethod(paramArgs)
    }

    private fun resetOomAdjReasonMethod(oomAdjReasonMethod: (Array<Any?>) -> Any?) {
        getOomAdjReasonMethod = oomAdjReasonMethod
    }

    private val isPendingFinishAttachMethod = if (SystemUtils.isUOrHigher) {
        { processRecord: Any ->
            ProcessRecord.isPendingFinishAttach(processRecord)
        }
    } else {
        { _: Any ->
            false
        }
    }

    override fun hook() {
        // 拿到一些必要的变量
        // 注意: 类名在运行时解析, psc 布局下为 com.android.server.am.psc.OomAdjuster
        oomAdjusterClassName.afterConstructorHook(
            classLoader = classLoader,
            hookAllMethod = true
        ) { param ->
            oomAdjuster = param.thisObject
            val instance = oomAdjuster ?: return@afterConstructorHook

            // 逐个字段 try/catch, 任何一个字段缺失都不影响其他字段的获取
            try {
                mProcessList = instance.getObjectFieldValue(
                    fieldName = FieldConstants.mProcessList
                )
            } catch (t: Throwable) {
                logger.warn("OomAdjuster.mProcessList 获取失败", t)
            }

            try {
                mConstants = instance.getObjectFieldValue(
                    fieldName = FieldConstants.mConstants
                )
            } catch (t: Throwable) {
                logger.warn("OomAdjuster.mConstants 获取失败", t)
            }

            try {
                lruList = mProcessList?.callMethod(
                    methodName = MethodConstants.getLruProcessesLOSP
                ) as? List<*>
            } catch (t: Throwable) {
                logger.warn("OomAdjuster.getLruProcessesLOSP 调用失败", t)
            }

            try {
                mAppProfiler = instance.getObjectFieldValue(
                    fieldName = FieldConstants.mService
                )?.getObjectFieldValue(
                    fieldName = FieldConstants.mAppProfiler
                )
            } catch (t: Throwable) {
                logger.warn("OomAdjuster.mAppProfiler 获取失败", t)
            }
        }

        /**
         * @see [OomAdjusterHook.handleUpdateAndTrimProcessLSP]
         *
         * HyperOS 4 / psc 布局下, updateAndTrimProcessLSP 的签名/内部逻辑已大幅变化,
         * 且依赖的 lruList/mAppProfiler 等字段在 psc.OomAdjuster 上可能不存在,
         * 直接 replace 会导致系统功能异常。因此 psc 布局下禁用此 replaceHook。
         */
        /*ClassConstants.OomAdjuster.beforeHook(
            classLoader = classLoader,
            methodName = MethodConstants.updateAndTrimProcessLSP,
            hookAllMethod = true,
        ) { it.args[2] = 0 }*/
        oomAdjusterClassName.replaceHook(
            enable = !SystemUtils.isPscLayout,
            classLoader = classLoader,
            methodName = MethodConstants.updateAndTrimProcessLSP,
            hookAllMethod = true,
        ) {
            updateAndTrimProcessLSP(it)
        }

        oomAdjusterClassName.replaceHook(
            enable = !SystemUtils.isUOrHigher && Features.USE_TIERED_CACHED_ADJ == true,
            classLoader = classLoader,
            methodName = MethodConstants.assignCachedAdjIfNecessary,
            hookAllMethod = true,
        ) {
            assignCachedAdjIfNecessary(it)
        }

        /**
         * @see [OomAdjusterHook.handleShouldKillExcessiveProcesses]
         */
        oomAdjusterClassName.replaceHook(
            classLoader = classLoader,
            methodName = MethodConstants.shouldKillExcessiveProcesses,
            hookAllMethod = true
        ) { false }
    }

    /* *************************************************************************
     *                                                                         *
     * updateAndTrimProcessLSP                                                 *
     *                                                                         *
     **************************************************************************/
    private val processedOomAdjReason = arrayOf(
        ActivityManagerInternal.OOM_ADJ_REASON_ACTIVITY,
        ActivityManagerInternal.OOM_ADJ_REASON_FINISH_RECEIVER,
        ActivityManagerInternal.OOM_ADJ_REASON_UNBIND_SERVICE,
        ActivityManagerInternal.OOM_ADJ_REASON_REMOVE_PROVIDER,
        ActivityManagerInternal.OOM_ADJ_REASON_UI_VISIBILITY,
        ActivityManagerInternal.OOM_ADJ_REASON_PROCESS_END,
        ActivityManagerInternal.OOM_ADJ_REASON_SHORT_FGS_TIMEOUT,
        ActivityManagerInternal.OOM_ADJ_REASON_REMOVE_TASK,
        ActivityManagerInternal.OOM_ADJ_REASON_UID_IDLE,
        ActivityManagerInternal.OOM_ADJ_REASON_STOP_SERVICE,
        ActivityManagerInternal.OOM_ADJ_REASON_COMPONENT_DISABLED,
    )

    /**
     * 对[ClassConstants.OomAdjuster].[MethodConstants.updateAndTrimProcessLSP]的重写
     *
     * 省去了一些逻辑
     * @param param MethodHookParam
     * @return Boolean
     */
    @AndroidMethodReplacement(
        classPath = ClassConstants.OomAdjuster,
        methodName = MethodConstants.updateAndTrimProcessLSP
    )
    private fun updateAndTrimProcessLSP(param: MethodHookParam): Boolean {
        val now = param.args[0] as Long
        val nowElapsed = param.args[1] as Long
        val oomAdjReason = getOomAdjReason(param)

        val currentLruList = lruList ?: return false
        val numLru = currentLruList.size

        for (i in numLru - 1 downTo 0) {
            // ProcessRecord
            val app: Any = currentLruList[i]!!
            // ProcessStateRecord (psc 布局下为 ProcessRecord 自身)
            val state: Any = ProcessRecord.getProcessStateRecord(app)
            if (!ProcessRecord.isKilledByAm(app) && ProcessRecord.getThread(app) != null) {
                // We don't need to apply the update for the process which didn't get computed
                if (ProcessStateRecord.getCompletedAdjSeq(state) == oomAdjuster?.getIntFieldValue(
                        fieldName = FieldConstants.mAdjSeq
                    )
                ) {
                    applyOomAdjLSP(
                        processRecord = app,
                        now = now,
                        nowElapsed = nowElapsed,
                        oomAdjReason = oomAdjReason
                    )
                }
                if (isPendingFinishAttachMethod(app)) {
                    // Avoid trimming processes that are still initializing. If they aren't
                    // hosting any components yet because they may be unfairly killed.
                    // We however apply the oom scores set at #setAttachingProcessStatesLSP.
                    continue
                }
                // ProcessServiceRecord
                val psr: Any = ProcessRecord.getProcessServiceRecord(app)

                if (ProcessRecord.isIsolated(app)
                    && ProcessServiceRecord.numberOfRunningServices(psr) <= 0
                    && ProcessRecord.getIsolatedEntryPoint(app) == null
                ) {
                    // If this is an isolated process, there are no services
                    // running in it, and it's not a special process with a
                    // custom entry point, then the process is no longer
                    // needed.  We agressively kill these because we can by
                    // definition not re-use the same process again, and it is
                    // good to avoid having whatever code was running in them
                    // left sitting around after no longer needed.
                    ProcessRecord.killLocked(
                        processRecord = app,
                        reason = "isolated not needed",
                        reasonCode = ApplicationExitInfo.REASON_OTHER,
                        subReason = ApplicationExitInfo.SUBREASON_ISOLATED_NOT_NEEDED,
                        noisy = true
                    )
                } else if (SystemUtils.isUOrHigher && ProcessRecord.isSdkSandbox(app)
                    && ProcessServiceRecord.numberOfRunningServices(psr) <= 0
                    && ProcessRecord.getActiveInstrumentation(app) == null
                ) {
                    // If this is an SDK sandbox process and there are no services running it, we
                    // aggressively kill the sandbox as we usually don't want to re-use the same
                    // sandbox again.
                    ProcessRecord.killLocked(
                        processRecord = app,
                        reason = "sandbox not needed",
                        reasonCode = ApplicationExitInfo.REASON_OTHER,
                        subReason = ApplicationExitInfo.SUBREASON_SDK_SANDBOX_NOT_NEEDED,
                        noisy = true
                    )
                } else {
                    // Keeping this process, update its uid.
                    oomAdjuster?.callMethod(
                        methodName = MethodConstants.updateAppUidRecLSP,
                        app
                    )
                }
            }
        }

        val appProfiler = mAppProfiler ?: return false
        return AppProfiler.updateLowMemStateLSP(
            appProfiler = appProfiler,
            numCached = 0,
            numEmpty = 0,
            numTrimming = 0,
            now = now,
        )
    }

    /**
     * 对[ClassConstants.OomAdjuster].[MethodConstants.assignCachedAdjIfNecessary]的重写
     *
     * 为a14以下版本开启分级缓存
     *
     * @param param MethodHookParam
     */
    @AndroidMethodReplacement(
        classPath = ClassConstants.OomAdjuster,
        methodName = MethodConstants.assignCachedAdjIfNecessary
    )
    private fun assignCachedAdjIfNecessary(param: MethodHookParam) {
        val lruList = param.args[0] as List<*>

        val numLru: Int = lruList.size
        val now = SystemClock.uptimeMillis()
        for (i in numLru - 1 downTo 0) {
            // ProcessRecord
            val app: Any = lruList[i]!!
            // ProcessStateRecord (psc 布局下为 ProcessRecord 自身)
            val state: Any = ProcessRecord.getProcessStateRecord(app)
            // ProcessCachedOptimizerRecord
            val opt: Any? = ProcessRecord.getProcessCachedOptimizerRecord(app)
            if (!ProcessRecord.isKilledByAm(app) && ProcessRecord.getThread(app) != null
                && ProcessStateRecord.getCurAdj(state) >= UNKNOWN_ADJ
            ) {
                // ProcessServiceRecord
                val psr: Any = ProcessRecord.getProcessServiceRecord(app)
                var targetAdj = CACHED_APP_MIN_ADJ
                targetAdj += if (opt != null && ProcessCachedOptimizerRecord.isFreezeExempt(opt)) {
                    // BIND_WAIVE_PRIORITY and the like get oom_adj 900
                    0
                } else if (ProcessStateRecord.getSetAdj(state) >= CACHED_APP_MIN_ADJ
                    && (ProcessStateRecord.getLastStateTime(state) + ActivityManagerConstants.TIERED_CACHED_ADJ_DECAY_TIME) < now
                ) {
                    // Older cached apps get 950
                    50
                } else {
                    // Newer cached apps get 910
                    10
                }
                ProcessStateRecord.setCurRawAdj(state, targetAdj)
                ProcessStateRecord.setCurAdj(
                    state,
                    ProcessServiceRecord.modifyRawOomAdj(psr, targetAdj)
                )
            }
        }
    }
}
