package com.huawei.strategy;

import java.util.HashSet;
import java.util.Set;

import com.huawei.model.GameContext;
import com.huawei.model.GameRequest;
import com.huawei.model.GameResponse;
import com.huawei.model.Role;
import com.huawei.util.Constants;
import com.huawei.util.MapUtil;

/**
 * 决策总调度入口：初始化上下文 → 判定昼夜 → 分发到白天/夜晚策略。
 */
public class DecisionEngine {

    private final GameContext ctx = new GameContext();
    private final DayStrategy dayStrategy = new DayStrategy();
    private final NightStrategy nightStrategy = new NightStrategy();

    /** 唯一入口：接收 Request，返回完整 Response */
    public GameResponse decide(GameRequest request) {
        GameResponse response = new GameResponse();
        if (request == null) {
            return response;
        }
        try {
            initContext(request);
            ctx.refreshDay(request.roundNo);
            ctx.recordMines(request.mapInfo);
            ctx.weaponCount = countWeapons(request);

            Set<Integer> occupiedRoles = new HashSet<Integer>();
            if (MapUtil.isDayTime(request.roundNo)) {
                dayStrategy.execute(response, request, ctx, occupiedRoles);
            } else {
                nightStrategy.execute(response, request, ctx, occupiedRoles);
            }
        } catch (Exception e) {
            // 决策异常不抛出，返回已生成的部分指令，避免响应超时
            e.printStackTrace();
        }
        return response;
    }

    /** 首回合初始化阵营与基地位置 */
    private void initContext(GameRequest request) {
        if (ctx.teamType == null || ctx.teamType.isEmpty()) {
            if (request.teamOur != null && request.teamOur.type != null) {
                ctx.teamType = request.teamOur.type;
            }
        }
        if (request.teamOur != null && request.teamOur.roles != null) {
            for (Role r : request.teamOur.roles) {
                if (r != null && Constants.ROLE_STATION.equals(r.roleType) && r.pos != null) {
                    ctx.stationPos = r.pos;
                    break;
                }
            }
        }
    }

    /** 统计武器工事数量 */
    private int countWeapons(GameRequest request) {
        int count = 0;
        if (request.teamOur != null && request.teamOur.roles != null) {
            for (Role r : request.teamOur.roles) {
                if (r != null && isWeapon(r.roleType)) {
                    count++;
                }
            }
        }
        return count;
    }

    private boolean isWeapon(String roleType) {
        return Constants.ROLE_GATLING.equals(roleType)
                || Constants.ROLE_RAILGUN.equals(roleType)
                || Constants.ROLE_ROCKET.equals(roleType);
    }
}
