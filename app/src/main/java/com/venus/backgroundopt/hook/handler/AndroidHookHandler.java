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
                    
package com.venus.backgroundopt.hook.handler;

import android.os.Build;

import com.venus.backgroundopt.core.RunningInfo;
import com.venus.backgroundopt.environment.SystemProperties;
import com.venus.backgroundopt.hook.base.PackageHook;
import com.venus.backgroundopt.hook.handle.android.ActivityManagerConstantsHookNew;
import com.venus.backgroundopt.hook.handle.android.ActivityManagerServiceHook;
import com.venus.backgroundopt.hook.handle.android.ActivityManagerServiceHookKt;
import com.venus.backgroundopt.hook.handle.android.ActivityManagerServiceHookNew;
import com.venus.backgroundopt.hook.handle.android.ActivityTaskSupervisorHook;
import com.venus.backgroundopt.hook.handle.android.AppProfilerHook;
import com.venus.backgroundopt.hook.handle.android.CachedAppOptimizerHook;
import com.venus.backgroundopt.hook.handle.android.DeletePackageHelperHook;
import com.venus.backgroundopt.hook.handle.android.DeviceConfigHookNew;
import com.venus.backgroundopt.hook.handle.android.LowMemDetectorHook;
import com.venus.backgroundopt.hook.handle.android.OomAdjusterHookNew;
import com.venus.backgroundopt.hook.handle.android.PackageManagerServiceHookKt;
import com.venus.backgroundopt.hook.handle.android.PackageManagerServiceHookNew;
import com.venus.backgroundopt.hook.handle.android.PhantomProcessListHook;
import com.venus.backgroundopt.hook.handle.android.PowerManagerServiceHook;
import com.venus.backgroundopt.hook.handle.android.OomAdjProbeHook;
import com.venus.backgroundopt.hook.handle.android.ProcessListHookKt;
import com.venus.backgroundopt.hook.handle.android.ProcessListHookNew;
import com.venus.backgroundopt.hook.handle.android.ProcessRecordHook;
import com.venus.backgroundopt.hook.handle.android.RecentTasksHook;
import com.venus.backgroundopt.hook.handle.android.ServiceManagerHook;
import com.venus.backgroundopt.hook.handle.android.SystemPropertiesHook;
import com.venus.backgroundopt.hook.handle.android.WindowProcessControllerHook;
import com.venus.backgroundopt.hook.handle.android.entity.ProcessList;
import com.venus.backgroundopt.hook.handle.android.function.CleanUpRemovedTaskHook;
import com.venus.backgroundopt.hook.handle.android.function.DefaultApplicationChangeHook;

import java.util.HashMap;

import com.venus.backgroundopt.utils.BgDebugLog;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * @author XingC
 * @version 1.0
 * @date 2023/2/17
 */
//@HookPackageName("android")
public class AndroidHookHandler extends PackageHook {
    public AndroidHookHandler(XC_LoadPackage.LoadPackageParam packageParam) {
        super(packageParam);
    }

    @Override
    public void hook(XC_LoadPackage.LoadPackageParam packageParam) {
        ClassLoader classLoader = packageParam.classLoader;
        RunningInfo runningInfo = new RunningInfo(classLoader);

        ProcessList.init();
        initSystemProp();

        // 资源Hook
//        new ResourcesHook(classLoader, runningInfo);

        // hook获取
//        new DeviceConfigHook(classLoader, runningInfo);
        safeHook("DeviceConfigHookNew", () -> new DeviceConfigHookNew(classLoader, runningInfo));
        // 抓取AMS, 前后台切换
        safeHook("ActivityManagerServiceHook", () -> new ActivityManagerServiceHook(classLoader, runningInfo));
        safeHook("ActivityManagerServiceHookKt", () -> new ActivityManagerServiceHookKt(classLoader, runningInfo));
        // 默认桌面
        safeHook("PackageManagerServiceHookKt", () -> new PackageManagerServiceHookKt(classLoader, runningInfo));
        // 杀后台hook
//        new ProcessHook(classLoader, runningInfo);
//        new ProcessHookKt(classLoader, runningInfo);

        // oom_adj更新hook
        // 2024.3.2: 禁用以通过ProcessList.setOomAdj知晓系统给予当前进程的oom_score_adj
        /*if (CommonProperties.INSTANCE.getOomWorkModePref().getOomMode() == OomWorkModePref.MODE_STRICT) {
            safeHook("ProcessStateRecordHook", () -> new ProcessStateRecordHook(classLoader, runningInfo));
        }*/
        safeHook("ProcessListHookKt", () -> new ProcessListHookKt(classLoader, runningInfo));
        // 安卓虚进程处理hook
        safeHook("PhantomProcessListHook", () -> new PhantomProcessListHook(classLoader, runningInfo));
//        new ActivityManagerConstantsHook(classLoader, runningInfo);

        // 最近任务可见性hook
        safeHook("RecentTasksHook", () -> new RecentTasksHook(classLoader, runningInfo));
        // 软件卸载
        // 安卓12在PackageManagerServiceHook完成
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {    // 安卓13
            safeHook("DeletePackageHelperHook", () -> new DeletePackageHelperHook(classLoader, runningInfo));
        }

        safeHook("SystemPropertiesHook", () -> new SystemPropertiesHook(classLoader, runningInfo));
        // new RoleControllerManagerHook(classLoader, runningInfo);

        safeHook("LowMemDetectorHook", () -> new LowMemDetectorHook(classLoader, runningInfo));
        safeHook("AppProfilerHook", () -> new AppProfilerHook(classLoader, runningInfo));
        safeHook("ProcessListHookNew", () -> new ProcessListHookNew(classLoader, runningInfo));
        safeHook("OomAdjusterHookNew", () -> new OomAdjusterHookNew(classLoader, runningInfo));
        safeHook("ActivityManagerServiceHookNew", () -> new ActivityManagerServiceHookNew(classLoader, runningInfo));
        safeHook("CachedAppOptimizerHook", () -> new CachedAppOptimizerHook(classLoader, runningInfo));
        safeHook("PackageManagerServiceHookNew", () -> new PackageManagerServiceHookNew(classLoader, runningInfo));
        safeHook("ServiceManagerHook", () -> new ServiceManagerHook(classLoader, runningInfo));
        safeHook("ActivityTaskSupervisorHook", () -> new ActivityTaskSupervisorHook(classLoader, runningInfo));
        safeHook("PowerManagerServiceHook", () -> new PowerManagerServiceHook(classLoader, runningInfo));
        safeHook("ActivityManagerConstantsHookNew", () -> new ActivityManagerConstantsHookNew(classLoader, runningInfo));
        safeHook("ProcessRecordHook", () -> new ProcessRecordHook(classLoader, runningInfo));
        safeHook("WindowProcessControllerHook", () -> new WindowProcessControllerHook(classLoader, runningInfo));
        safeHook("DefaultApplicationChangeHook", () -> new DefaultApplicationChangeHook(classLoader, runningInfo));
        safeHook("CleanUpRemovedTaskHook", () -> new CleanUpRemovedTaskHook(classLoader, runningInfo));
        safeHook("OomAdjProbeHook", () -> new OomAdjProbeHook(classLoader, runningInfo));
    }

    /**
     * HyperOS 4 适配: 单个 hook 类注册失败时, 不允许影响后续 hook 的注册。
     * 之前的写法是直接 new, 一旦某个类初始化抛异常, 后面所有 hook 都不会注册。
     */
    private void safeHook(String name, Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable t) {
            BgDebugLog.log("register FAILED: " + name, t);
        }
    }

    private void initSystemProp() {
        HashMap<String, String> map = new HashMap<>() {
            {
                // 米杀后台SystemProperties
                put("persist.sys.spc.enabled", "false");
            }
        };

        map.forEach((k, v) -> {
            try {
                SystemProperties.set(k, v);
            } catch (Throwable throwable) {
                getLogger().warn("[" + k + "]设置失败", throwable);
            }
        });
    }
}