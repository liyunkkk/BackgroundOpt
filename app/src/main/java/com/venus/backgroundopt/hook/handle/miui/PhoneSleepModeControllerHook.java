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

 package com.venus.backgroundopt.hook.handle.miui;

import com.venus.backgroundopt.hook.base.HookPoint;
import com.venus.backgroundopt.hook.base.MethodHook;
import com.venus.backgroundopt.hook.base.action.DoNotingHookAction;
import com.venus.backgroundopt.hook.base.action.HookAction;
import com.venus.backgroundopt.hook.constants.ClassConstants;
import com.venus.backgroundopt.hook.constants.MethodConstants;

/**
 * HyperOS 4 起, 手机端的 SleepModeControllerNew 已被 PhoneSleepModeController 取代。
 * 其 clearApp()V 方法会在睡眠模式下清理后台进程, 这里用 DoNotingHookAction 阻止。
 *
 * @author XingC
 * @version 1.0
 * @date 2023/6/1
 */
public class PhoneSleepModeControllerHook extends MethodHook {
    public PhoneSleepModeControllerHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public HookPoint[] getHookPoint() {
        return new HookPoint[]{
                new HookPoint(
                        ClassConstants.PhoneSleepModeController,
                        MethodConstants.clearApp,
                        new HookAction[]{
                                new DoNotingHookAction()
                        }
                )
        };
    }
}
