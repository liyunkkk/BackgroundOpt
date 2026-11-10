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

 package com.venus.backgroundopt.hook.handle.android;

import com.venus.backgroundopt.core.RunningInfo;
import com.venus.backgroundopt.hook.base.HookPoint;
import com.venus.backgroundopt.hook.base.MethodHook;
import com.venus.backgroundopt.hook.base.action.BeforeHookAction;
import com.venus.backgroundopt.hook.base.action.DoNotingHookAction;
import com.venus.backgroundopt.hook.base.action.HookAction;
import com.venus.backgroundopt.hook.constants.ClassConstants;
import com.venus.backgroundopt.hook.constants.MethodConstants;

import de.robv.android.xposed.XC_MethodHook;

/**
 * @author XingC
 * @version 1.0
 * @date 2023/6/1
 */
public class RecentTasksHook extends MethodHook {
    public RecentTasksHook(ClassLoader classLoader, RunningInfo hookInfo) {
        super(classLoader, hookInfo);
    }

    @Override
    public HookPoint[] getHookPoint() {
        return new HookPoint[]{
                new HookPoint(
                        ClassConstants.RecentTasks,
                        MethodConstants.isInVisibleRange,
                        new HookAction[]{
                                (BeforeHookAction) this::handleIsInVisibleRange
                        }
                ).setHookAllMatchedMethod(true),
                new HookPoint(
                        ClassConstants.RecentTasks,
                        MethodConstants.trimInactiveRecentTasks,
                        new HookAction[]{
                                new DoNotingHookAction()
                        }
                ),
        };
    }

    /**
     * HyperOS 4 / Android 17 (SDK 37) 起, isInVisibleRange 的签名从
     * (Task, int, int, boolean) 变为 (Task, int, boolean),
     * 去掉 paramTypes 按方法名 hook 后, 需要根据参数个数自适应处理。
     */
    private Object handleIsInVisibleRange(XC_MethodHook.MethodHookParam param) {
        int argCount = param.args.length;
        if (argCount >= 4) {
            // 旧签名: (Task, int numVisibleTasks, int, boolean), 将可见窗口数设为0
            param.args[2] = 0;
        } else if (argCount == 3) {
            // 新签名: (Task, int numVisibleTasks, boolean), 将可见窗口数设为0
            param.args[1] = 0;
        }

        return null;
    }
}
