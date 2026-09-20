package com.huawei.strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.huawei.model.GameContext;
import com.huawei.model.GameRequest;
import com.huawei.model.GameResponse;
import com.huawei.model.PlayerTask;
import com.huawei.model.Pos;
import com.huawei.model.Role;
import com.huawei.model.RoleCommand;
import com.huawei.util.Constants;
import com.huawei.util.MapUtil;
import com.huawei.util.RoleUtil;

/**
 * 开拓者行为逻辑。
 *
 * <p>白天优先级：血量低用药 → 自进化任务状态机 → 召唤宝藏 → 任务点待命。
 * <p>夜晚：血量低用药 → 移动到最近武器旁待命。
 */
public class PioneerStrategy {

    private final TaskSolver taskSolver = new TaskSolver();

    public void execute(Role pioneer, GameResponse response, GameRequest request, GameContext ctx,
                        Set<Integer> occupiedRoles, boolean isDay) {
        if (pioneer == null) {
            return;
        }
        if (occupiedRoles.contains(pioneer.id)) {
            return;
        }
        if (!RoleUtil.isAlive(request.teamOur.roles, pioneer.id)) {
            return;
        }

        if (!isDay) {
            RoleCommand cmd = decideNightAction(pioneer, response, request, ctx, occupiedRoles);
            if (cmd != null) {
                response.roleCommandMap.put(pioneer.id, cmd);
                occupiedRoles.add(pioneer.id);
            }
            return;
        }

        // 1. 血量低且有药品 → 使用
        if (pioneer.health < 60 && RoleUtil.hasInBackpack(pioneer, Constants.ITEM_MEDICINE)) {
            response.roleCommandMap.put(pioneer.id, RoleCommand.use(Constants.ITEM_MEDICINE));
            occupiedRoles.add(pioneer.id);
            return;
        }

        // 2. 自进化任务状态机（有任务或可接任务时返回指令）
        List<Pos> obstacles = MapUtil.collectObstacles(request.teamOur.roles, request.teamEnemy.roles,
                request.robot == null ? null : request.robot.roles, request.mapInfo.zones);
        RoleCommand taskCmd = taskSolver.solve(pioneer, response, request, ctx, obstacles,
                request.mapInfo.width, request.mapInfo.height);
        if (taskCmd != null) {
            response.roleCommandMap.put(pioneer.id, taskCmd);
            occupiedRoles.add(pioneer.id);
            return;
        }

        // 3. 无任务时尝试召唤宝藏
        RoleCommand treasure = trySummonTreasure(pioneer, response, request, ctx, occupiedRoles);
        if (treasure != null) {
            response.roleCommandMap.put(pioneer.id, treasure);
            occupiedRoles.add(pioneer.id);
            return;
        }

        // 4. 否则移动到任务点待命
        RoleCommand standby = moveToTaskPoint(pioneer, response, request, ctx, occupiedRoles);
        if (standby != null) {
            response.roleCommandMap.put(pioneer.id, standby);
            occupiedRoles.add(pioneer.id);
        }
    }

    /** 夜晚：血量低用药品；否则移动到最近武器旁待命 */
    private RoleCommand decideNightAction(Role pioneer, GameResponse response, GameRequest request, GameContext ctx,
                                          Set<Integer> occupiedRoles) {
        if (pioneer.health < 60 && RoleUtil.hasInBackpack(pioneer, Constants.ITEM_MEDICINE)) {
            return RoleCommand.use(Constants.ITEM_MEDICINE);
        }
        Role weapon = findNearestWeapon(request, pioneer.pos);
        if (weapon == null) {
            return null;
        }
        if (MapUtil.isAdjacent(pioneer.pos, weapon.pos)) {
            return null;
        }
        return moveToward(pioneer, weapon.pos, request, ctx, occupiedRoles);
    }

    /** 背包有任务用品 → 移动到任务点（作为祭坛位置）→ summonTreasure */
    private RoleCommand trySummonTreasure(Role pioneer, GameResponse response, GameRequest request, GameContext ctx,
                                          Set<Integer> occupiedRoles) {
        if (ctx.treasureOpened) {
            return null;
        }
        List<String> items = collectTreasureItems(pioneer);
        if (items.isEmpty()) {
            return null;
        }
        PlayerTask task = getFirstTask(request);
        if (task == null || task.taskPosition == null) {
            return null;
        }
        Pos altar = task.taskPosition;
        if (MapUtil.isAdjacentOrSame(pioneer.pos, altar)) {
            ctx.treasureOpened = true;
            return RoleCommand.summonTreasure(altar, items);
        }
        return moveToward(pioneer, altar, request, ctx, occupiedRoles);
    }

    /** 扫描背包中 6 种任务用品 */
    private List<String> collectTreasureItems(Role pioneer) {
        List<String> items = new ArrayList<String>();
        if (pioneer.backpack == null) {
            return items;
        }
        String[] taskItems = {
                Constants.ITEM_ANCIENT_TABLET, Constants.ITEM_STAR_SAND,
                Constants.ITEM_FLAME_BREATH, Constants.ITEM_FROST_POTION,
                Constants.ITEM_THORN_AMULET, Constants.ITEM_IRON_WHISTLE
        };
        for (String s : pioneer.backpack) {
            for (String item : taskItems) {
                if (item.equals(s)) {
                    items.add(s);
                    break;
                }
            }
        }
        return items;
    }

    /** 移动到任务点 1 坐标待命 */
    private RoleCommand moveToTaskPoint(Role pioneer, GameResponse response, GameRequest request, GameContext ctx,
                                        Set<Integer> occupiedRoles) {
        PlayerTask task = getFirstTask(request);
        if (task == null || task.taskPosition == null) {
            return null;
        }
        Pos target = task.taskPosition;
        if (MapUtil.isAdjacentOrSame(pioneer.pos, target)) {
            return null;
        }
        return moveToward(pioneer, target, request, ctx, occupiedRoles);
    }

    private PlayerTask getFirstTask(GameRequest request) {
        if (request.teamOur == null || request.teamOur.playerTasks == null
                || request.teamOur.playerTasks.isEmpty()) {
            return null;
        }
        return request.teamOur.playerTasks.get(0);
    }

    /** 查找离 pos 最近的武器工事 */
    private Role findNearestWeapon(GameRequest request, Pos pos) {
        Role best = null;
        int bestDist = Integer.MAX_VALUE;
        if (request.teamOur == null || request.teamOur.roles == null) {
            return null;
        }
        for (Role r : request.teamOur.roles) {
            if (r == null || r.pos == null || !isWeapon(r.roleType)) {
                continue;
            }
            int d = MapUtil.chebyshev(r.pos, pos);
            if (d < bestDist) {
                bestDist = d;
                best = r;
            }
        }
        return best;
    }

    /** 向目标移动一步（考虑障碍物） */
    private RoleCommand moveToward(Role role, Pos target, GameRequest request, GameContext ctx,
                                   Set<Integer> occupiedRoles) {
        if (target == null) {
            return null;
        }
        List<Pos> obstacles = MapUtil.collectObstacles(request.teamOur.roles, request.teamEnemy.roles,
                request.robot == null ? null : request.robot.roles, request.mapInfo.zones);
        Pos next = MapUtil.nextStepToward(role.pos, target, obstacles, request.mapInfo.width, request.mapInfo.height);
        if (next == null || next.equals(role.pos)) {
            return null;
        }
        return RoleCommand.move(next);
    }

    private boolean isWeapon(String roleType) {
        return Constants.ROLE_GATLING.equals(roleType)
                || Constants.ROLE_RAILGUN.equals(roleType)
                || Constants.ROLE_ROCKET.equals(roleType);
    }
}
