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

 package com.venus.backgroundopt.hook.handle.android.entity;

import androidx.annotation.Nullable;

import com.venus.backgroundopt.annotation.AndroidObject;
import com.venus.backgroundopt.hook.constants.ClassConstants;
import com.venus.backgroundopt.hook.constants.FieldConstants;
import com.venus.backgroundopt.utils.log.ILogger;

import de.robv.android.xposed.XposedHelpers;

/**
 * 封装了{@link ClassConstants#OomAdjuster}
 *
 * @author XingC
 * @version 1.0
 * @date 2023/6/1
 */
public class OomAdjuster implements ILogger {
    @AndroidObject(classPath = ClassConstants.OomAdjuster)
    private final Object oomAdjuster;

    @AndroidObject
    public Object getOomAdjuster() {
        return oomAdjuster;
    }

    /**
     * HyperOS 4 / Android 17 (SDK 37) 起, psc.OomAdjuster 上不存在 mCachedAppOptimizer 字段,
     * 直接 XposedHelpers.getObjectField 会抛 NoSuchFieldError,
     * 导致 ActivityManagerService 包装对象构造失败、RunningInfo.activityManagerService 永远为 null,
     * 进而整个模块静默失效。因此这里改为可空, 并逐个字段 try/catch 降级。
     */
    @Nullable
    private final CachedAppOptimizer cachedAppOptimizer;

    @Nullable
    public CachedAppOptimizer getCachedAppOptimizer() {
        return cachedAppOptimizer;
    }

    /**
     * @param oomAdjuster           系统的 OomAdjuster 对象 (psc 布局下为 psc.OomAdjuster)
     * @param activityManagerService 系统的 AMS 对象, 用于在 psc 布局下回退取 mCachedAppOptimizer/mAppProfiler
     * @param classLoader           classLoader
     */
    public OomAdjuster(@AndroidObject Object oomAdjuster, @AndroidObject Object activityManagerService, ClassLoader classLoader) {
        this.oomAdjuster = oomAdjuster;
        this.cachedAppOptimizer = initCachedAppOptimizer(oomAdjuster, activityManagerService, classLoader);
    }

    /**
     * 兼容旧构造签名: 未传入 AMS 时只能尝试从 OomAdjuster 自身取字段。
     */
    public OomAdjuster(@AndroidObject Object oomAdjuster, ClassLoader classLoader) {
        this(oomAdjuster, null, classLoader);
    }

    /**
     * 逐个候选位置尝试解析 CachedAppOptimizer, 全部失败则返回 null 而不是抛出异常。
     * 候选顺序:
     * 1. OomAdjuster.mCachedAppOptimizer (AOSP 传统布局)
     * 2. AMS.mCachedAppOptimizer      (HyperOS 4 / psc 布局, 字段移回 AMS)
     * 3. AMS.mAppProfiler             (部分版本 CachedAppOptimizer 挂在 AppProfiler 上)
     */
    @Nullable
    private CachedAppOptimizer initCachedAppOptimizer(Object oomAdjuster, Object activityManagerService, ClassLoader classLoader) {
        // 1. 传统布局: OomAdjuster 自身持有 mCachedAppOptimizer
        try {
            Object obj = XposedHelpers.getObjectField(oomAdjuster, FieldConstants.mCachedAppOptimizer);
            if (obj != null) {
                return new CachedAppOptimizer(obj, classLoader);
            }
        } catch (Throwable t) {
            // psc 布局下此处必然 NoSuchFieldError, 属预期, 仅 debug 记录
            getLogger().debug("OomAdjuster 上无 mCachedAppOptimizer 字段, 尝试从 AMS 获取");
        }

        if (activityManagerService != null) {
            // 2. psc 布局: 字段移回 AMS
            try {
                Object obj = XposedHelpers.getObjectField(activityManagerService, FieldConstants.mCachedAppOptimizer);
                if (obj != null) {
                    return new CachedAppOptimizer(obj, classLoader);
                }
            } catch (Throwable t) {
                getLogger().debug("AMS 上无 mCachedAppOptimizer 字段, 尝试 mAppProfiler");
            }

            // 3. 兜底: AppProfiler
            try {
                Object obj = XposedHelpers.getObjectField(activityManagerService, FieldConstants.mAppProfiler);
                if (obj != null) {
                    return new CachedAppOptimizer(obj, classLoader);
                }
            } catch (Throwable t) {
                getLogger().warn("无法从 AMS.mAppProfiler 获取 CachedAppOptimizer", t);
            }
        }

        getLogger().warn("CachedAppOptimizer 获取失败, 内存压缩相关功能将不可用");
        return null;
    }
}
