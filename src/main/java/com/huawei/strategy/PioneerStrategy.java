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
import com.huawei.model.Zone;
import com.huawei.util.Constants;
import com.huawei.util.MapUtil;
import com.huawei.util.RoleUtil;

/**
 * 开拓者行为逻辑。
 *
 * <p>白天优先级链：
 * <ol>
 *   <li>白天后期（回合号%130 ≥ 60）→ 回最近武器旁，准备夜晚御敌；</li>
 *   <li>自进化任务状态机（专心接任务/交任务）；</li>
 *   <li>血量低 → 用药，没药则到武器商店购买；</li>
 *   <li>未开启宝藏且背包有任务用品 → 召唤宝藏；</li>
 *   <li>到武器商店采购（补 Medicine、买任务用品备用）；</li>
 *   <li>任务点旁待命。</li>
 * </ol>
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
        int roundInDay = request.roundNo % Constants.ROUNDS_PER_DAY;

        // 0. 白天后期：回最近武器旁，准备夜晚御敌
        if (roundInDay >= Constants.NIGHT_PREP_ROUND) {
            RoleCommand cmd = moveToNearestWeapon(pioneer, request, ctx, occupiedRoles);
            if (cmd != null) {
                response.roleCommandMap.put(pioneer.id, cmd);
                occupiedRoles.add(pioneer.id);
            }
            return;
        }

        // 1. 自进化任务状态机（有任务或可接任务时返回指令）
        List<Pos> obstacles = MapUtil.collectObstacles(request.teamOur.roles, request.teamEnemy.roles,
                request.robot == null ? null : request.robot.roles, request.mapInfo.zones);
        RoleCommand taskCmd = taskSolver.solve(pioneer, response, request, ctx, obstacles,
                request.mapInfo.width, request.mapInfo.height);
        if (taskCmd != null) {
            response.roleCommandMap.put(pioneer.id, taskCmd);
            occupiedRoles.add(pioneer.id);
            return;
        }

        // 2. 血量低 → 用药；没药则到商店购买
        if (pioneer.health < 60) {
            if (RoleUtil.hasInBackpack(pioneer, Constants.ITEM_MEDICINE)) {
                response.roleCommandMap.put(pioneer.id, RoleCommand.use(Constants.ITEM_MEDICINE));
                occupiedRoles.add(pioneer.id);
                return;
            }
            RoleCommand buy = buyAtShop(pioneer, response, request, ctx, occupiedRoles, Constants.ITEM_MEDICINE, 10);
            if (buy != null) {
                response.roleCommandMap.put(pioneer.id, buy);
                occupiedRoles.add(pioneer.id);
                return;
            }
        }

        // 3. 未开启宝藏且背包有任务用品 → 召唤宝藏
        RoleCommand treasure = trySummonTreasure(pioneer, response, request, ctx, occupiedRoles);
        if (treasure != null) {
            response.roleCommandMap.put(pioneer.id, treasure);
            occupiedRoles.add(pioneer.id);
            return;
        }

        // 4. 到武器商店采购：补 Medicine / 买任务用品备用
        RoleCommand shop = tryShopShopping(pioneer, response, request, ctx, occupiedRoles);
        if (shop != null) {
            response.roleCommandMap.put(pioneer.id, shop);
            occupiedRoles.add(pioneer.id);
            return;
        }

        // 5. 否则移动到任务点待命
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
        return moveToNearestWeapon(pioneer, request, ctx, occupiedRoles);
    }

    /** 到武器商店采购：背包无药买 Medicine；否则金币充足且背包有空间买任务用品备用 */
    private RoleCommand tryShopShopping(Role pioneer, GameResponse response, GameRequest request, GameContext ctx,
                                        Set<Integer> occupiedRoles) {
        Zone shop = findShop(request);
        if (shop == null || shop.pos == null) {
            return null;
        }
        // 补 Medicine（血量不满且背包无药）
        if (pioneer.health < 200 && !RoleUtil.hasInBackpack(pioneer, Constants.ITEM_MEDICINE)
                && request.teamOur.goldNum >= 10) {
            if (MapUtil.isAdjacentOrSame(pioneer.pos, shop.pos)) {
                return RoleCommand.buy(Constants.ITEM_MEDICINE, 1);
            }
            return moveToward(pioneer, shop.pos, request, ctx, occupiedRoles);
        }
        // 买任务用品备用（金币足够、背包有空间、未开宝藏）
        if (!ctx.treasureOpened && request.teamOur.goldNum >= 45
                && RoleUtil.backpackRemaining(pioneer) >= Constants.SHOP_TASK_ITEM_KINDS) {
            if (MapUtil.isAdjacentOrSame(pioneer.pos, shop.pos)) {
                return RoleCommand.buy(Constants.ITEM_ANCIENT_TABLET, Constants.SHOP_TASK_ITEM_KINDS);
            }
            return moveToward(pioneer, shop.pos, request, ctx, occupiedRoles);
        }
        return null;
    }

    /** 在武器商店旁购买指定物品 */
    private RoleCommand buyAtShop(Role pioneer, GameResponse response, GameRequest request, GameContext ctx,
                                  Set<Integer> occupiedRoles, String itemName, int price) {
        if (request.teamOur.goldNum < price) {
            return null;
        }
        Zone shop = findShop(request);
        if (shop == null || shop.pos == null) {
            return null;
        }
        if (MapUtil.isAdjacentOrSame(pioneer.pos, shop.pos)) {
            return RoleCommand.buy(itemName, 1);
        }
        return moveToward(pioneer, shop.pos, request, ctx, occupiedRoles);
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

    /** 移动到最近武器旁；已在旁则返回 null（待命） */
    private RoleCommand moveToNearestWeapon(Role pioneer, GameRequest request, GameContext ctx,
                                            Set<Integer> occupiedRoles) {
        Role weapon = findNearestWeapon(request, pioneer.pos);
        if (weapon == null) {
            return null;
        }
        if (MapUtil.isAdjacent(pioneer.pos, weapon.pos)) {
            return null;
        }
        return moveToward(pioneer, weapon.pos, request, ctx, occupiedRoles);
    }

    private PlayerTask getFirstTask(GameRequest request) {
        if (request.teamOur == null || request.teamOur.playerTasks == null
                || request.teamOur.playerTasks.isEmpty()) {
            return null;
        }
        return request.teamOur.playerTasks.get(0);
    }

    private Zone findShop(GameRequest request) {
        if (request.mapInfo == null || request.mapInfo.zones == null) {
            return null;
        }
        List<Zone> shops = MapUtil.findZones(request.mapInfo.zones, Constants.NEUTRAL_WEAPON_SHOP);
        return shops.isEmpty() ? null : shops.get(0);
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
