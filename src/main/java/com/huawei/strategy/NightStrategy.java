package com.huawei.strategy;

import java.util.List;
import java.util.Set;

import com.huawei.model.GameContext;
import com.huawei.model.GameRequest;
import com.huawei.model.GameResponse;
import com.huawei.model.Role;
import com.huawei.util.Constants;
import com.huawei.util.RoleUtil;

/**
 * 夜晚策略调度：先武器攻击（CombatStrategy 优先分配操控者），后角色移动到武器旁待命。
 */
public class NightStrategy {

    private final CombatStrategy combatStrategy = new CombatStrategy();
    private final WorkerStrategy workerStrategy = new WorkerStrategy();
    private final PioneerStrategy pioneerStrategy = new PioneerStrategy();

    public void execute(GameResponse response, GameRequest request, GameContext ctx, Set<Integer> occupiedRoles) {
        combatStrategy.execute(response, request, ctx, occupiedRoles);

        List<Role> workers = RoleUtil.filterByType(request.teamOur.roles, Constants.ROLE_WORKER);
        workerStrategy.execute(workers, response, request, ctx, occupiedRoles, false);

        Role pioneer = RoleUtil.findFirst(request.teamOur.roles, Constants.ROLE_PIONEER);
        pioneerStrategy.execute(pioneer, response, request, ctx, occupiedRoles, false);
    }
}
