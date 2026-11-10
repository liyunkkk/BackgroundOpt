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

import com.venus.backgroundopt.hook.base.PackageHook;
import com.venus.backgroundopt.hook.handle.miui.PadSleepModeControllerHook;
import com.venus.backgroundopt.hook.handle.miui.PhoneSleepModeControllerHook;
import com.venus.backgroundopt.hook.handle.miui.PowerStateMachineHook;
import com.venus.backgroundopt.hook.handle.miui.ProcessManagerHook;
import com.venus.backgroundopt.hook.handle.miui.SleepModeControllerNewHook;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * @author XingC
 * @version 1.0
 * @date 2023/2/17
 */
//@HookPackageName("com.miui.powerkeeper")
public class PowerKeeperHookHandler extends PackageHook {
    public PowerKeeperHookHandler(XC_LoadPackage.LoadPackageParam packageParam) {
        super(packageParam);
    }

    @Override
    public void hook(XC_LoadPackage.LoadPackageParam packageParam) {
        ClassLoader classLoader = packageParam.classLoader;

        new PowerStateMachineHook(classLoader);
        new ProcessManagerHook(classLoader);
        // HyperOS 4 以下使用旧版 SleepModeControllerNew, 保留不动
        new SleepModeControllerNewHook(classLoader);
        // HyperOS 4 起手机/平板分别使用 Phone/PadSleepModeController, 类不存在时 HookPoint 内部会静默跳过
        new PhoneSleepModeControllerHook(classLoader);
        new PadSleepModeControllerHook(classLoader);
    }
}
