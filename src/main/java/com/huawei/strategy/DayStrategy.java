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
 * 白天策略调度：先工人（采集/建造/升级），后开拓者（任务/宝藏）。
 */
public class DayStrategy {

    private final WorkerStrategy workerStrategy = new WorkerStrategy();
    private final PioneerStrategy pioneerStrategy = new PioneerStrategy();

    public void execute(GameResponse response, GameRequest request, GameContext ctx, Set<Integer> occupiedRoles) {
        List<Role> workers = RoleUtil.filterByType(request.teamOur.roles, Constants.ROLE_WORKER);
        workerStrategy.execute(workers, response, request, ctx, occupiedRoles, true);

        Role pioneer = RoleUtil.findFirst(request.teamOur.roles, Constants.ROLE_PIONEER);
        pioneerStrategy.execute(pioneer, response, request, ctx, occupiedRoles, true);
    }
}
