package com.huawei.strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.huawei.model.GameContext;
import com.huawei.model.GameRequest;
import com.huawei.model.GameResponse;
import com.huawei.model.Pos;
import com.huawei.model.Role;
import com.huawei.model.RoleCommand;
import com.huawei.model.ShopItem;
import com.huawei.model.Zone;
import com.huawei.util.Constants;
import com.huawei.util.MapUtil;
import com.huawei.util.RoleUtil;

/**
 * 工人行为逻辑。
 *
 * <p>白天优先级：背包快满卖矿 → 武器不足建围墙 → 升级/购买升级券 → 采集 → 武器商店待命。
 * <p>夜晚优先级：血量低用药 → 移动到最近武器旁待命。
 */
public class WorkerStrategy {

    /** 基地附近围墙建造候选位（相对基地左上角的偏移） */
    private static final Pos[] BASE_BUILD_CANDIDATES = {
            new Pos(-1, -1), new Pos(2, -1), new Pos(-1, 2), new Pos(2, 2), new Pos(-1, 3)
    };

    public void execute(List<Role> workers, GameResponse response, GameRequest request, GameContext ctx,
                        Set<Integer> occupiedRoles, boolean isDay) {
        if (workers == null || workers.isEmpty()) {
            return;
        }
        for (Role worker : workers) {
            if (occupiedRoles.contains(worker.id)) {
                continue;
            }
            if (!RoleUtil.isAlive(request.teamOur.roles, worker.id)) {
                continue;
            }
            RoleCommand cmd = decideWorkerAction(worker, response, request, ctx, occupiedRoles, isDay);
            if (cmd != null) {
                response.roleCommandMap.put(worker.id, cmd);
                occupiedRoles.add(worker.id);
            }
        }
    }

    /** 总调度：按优先级链决策 */
    private RoleCommand decideWorkerAction(Role worker, GameResponse response, GameRequest request, GameContext ctx,
                                           Set<Integer> occupiedRoles, boolean isDay) {
        if (!isDay) {
            return decideNightAction(worker, response, request, ctx, occupiedRoles);
        }
        // 1. 背包快满（剩余 ≤5）→ 卖最值钱矿石
        if (RoleUtil.backpackRemaining(worker) <= 5) {
            RoleCommand cmd = sellMostValuable(worker, response, request, ctx, occupiedRoles);
            if (cmd != null) {
                return cmd;
            }
        }
        // 2. 武器工事 <3 且有石头 → 建围墙
        if (ctx.weaponCount < Constants.MAX_WEAPONS) {
            RoleCommand cmd = tryBuildNearBase(worker, response, request, ctx, occupiedRoles);
            if (cmd != null) {
                return cmd;
            }
        }
        // 3. 升级优先：有券用券，无券且金币足够则购买
        RoleCommand upgrade = tryUpgrade(worker, response, request, ctx, occupiedRoles);
        if (upgrade != null) {
            return upgrade;
        }
        // 4. 正常采集
        RoleCommand collect = collectOre(worker, response, request, ctx, occupiedRoles);
        if (collect != null) {
            return collect;
        }
        // 5. 无事可做 → 移动到武器商店待命
        return moveToZone(worker, request, ctx, occupiedRoles, Constants.NEUTRAL_WEAPON_SHOP);
    }

    /** 夜晚：血量低用药品；否则移动到最近武器旁待命 */
    private RoleCommand decideNightAction(Role worker, GameResponse response, GameRequest request, GameContext ctx,
                                          Set<Integer> occupiedRoles) {
        if (worker.health < 66 && RoleUtil.hasInBackpack(worker, Constants.ITEM_MEDICINE)) {
            return RoleCommand.use(Constants.ITEM_MEDICINE);
        }
        Role weapon = findNearestWeapon(request, worker.pos);
        if (weapon == null) {
            return null;
        }
        if (MapUtil.isAdjacent(worker.pos, weapon.pos)) {
            // 已在武器旁待命
            return null;
        }
        return moveToward(worker, weapon.pos, request, ctx, occupiedRoles);
    }

    /** 在小贩旁卖最值钱矿石；否则移动到小贩旁 */
    private RoleCommand sellMostValuable(Role worker, GameResponse response, GameRequest request, GameContext ctx,
                                         Set<Integer> occupiedRoles) {
        Zone vendor = findZone(request, Constants.NEUTRAL_VENDOR);
        if (vendor == null || vendor.pos == null) {
            return null;
        }
        if (MapUtil.isAdjacentOrSame(worker.pos, vendor.pos)) {
            String ore = mostValuableOre(worker, request);
            if (ore != null) {
                int num = RoleUtil.countInBackpack(worker, ore);
                return RoleCommand.sell(ore, Math.max(1, num));
            }
            return null;
        }
        return moveToward(worker, vendor.pos, request, ctx, occupiedRoles);
    }

    /** 在基地附近候选位中找空位建造围墙 */
    private RoleCommand tryBuildNearBase(Role worker, GameResponse response, GameRequest request, GameContext ctx,
                                         Set<Integer> occupiedRoles) {
        if (!RoleUtil.hasInBackpack(worker, Constants.ORE_STONE)) {
            return null;
        }
        Pos station = ctx.stationPos;
        if (station == null) {
            return null;
        }
        List<Pos> obstacles = MapUtil.collectObstacles(request.teamOur.roles, request.teamEnemy.roles,
                request.robot == null ? null : request.robot.roles, request.mapInfo.zones);
        for (Pos offset : BASE_BUILD_CANDIDATES) {
            Pos candidate = new Pos(station.x + offset.x, station.y + offset.y);
            if (!MapUtil.isValidPos(candidate, request.mapInfo.width, request.mapInfo.height)) {
                continue;
            }
            if (MapUtil.isBlocked(candidate, obstacles)) {
                continue;
            }
            if (MapUtil.isAdjacent(worker.pos, candidate)) {
                return RoleCommand.build(Constants.ROLE_WALL, candidate);
            }
            return moveToward(worker, candidate, request, ctx, occupiedRoles);
        }
        return null;
    }

    /** 有升级券 → 移动到建筑旁使用；无券且金币足够 → 到武器商店购买 */
    private RoleCommand tryUpgrade(Role worker, GameResponse response, GameRequest request, GameContext ctx,
                                   Set<Integer> occupiedRoles) {
        Role weapon = findWeaponToUpgrade(request);
        String voucher = hasUpgradeVoucher(worker);
        if (voucher != null && weapon != null) {
            if (MapUtil.isAdjacent(worker.pos, weapon.pos)) {
                return RoleCommand.use(voucher, weapon.pos);
            }
            return moveToward(worker, weapon.pos, request, ctx, occupiedRoles);
        }
        if (request.teamOur.goldNum >= 100 && !RoleUtil.hasInBackpack(worker, Constants.ITEM_WEAPON_UPGRADE_1)) {
            Zone shop = findZone(request, Constants.NEUTRAL_WEAPON_SHOP);
            if (shop != null && shop.pos != null) {
                if (MapUtil.isAdjacentOrSame(worker.pos, shop.pos)) {
                    return RoleCommand.buy(Constants.ITEM_WEAPON_UPGRADE_1, 1);
                }
                return moveToward(worker, shop.pos, request, ctx, occupiedRoles);
            }
        }
        return null;
    }

    /** 正常采集：选择最近矿点 → 移动到矿旁 → collect */
    private RoleCommand collectOre(Role worker, GameResponse response, GameRequest request, GameContext ctx,
                                   Set<Integer> occupiedRoles) {
        Pos target = selectBestMine(worker, request, ctx);
        if (target == null) {
            return null;
        }
        if (MapUtil.isAdjacent(worker.pos, target)) {
            if (RoleUtil.backpackRemaining(worker) <= 0) {
                return null;
            }
            ctx.workerTargetMine.put(worker.id, target);
            ctx.workerMining.put(worker.id, Boolean.TRUE);
            return RoleCommand.collect(target);
        }
        return moveToward(worker, target, request, ctx, occupiedRoles);
    }

    /** 选择距离工人最近的矿点 */
    private Pos selectBestMine(Role worker, GameRequest request, GameContext ctx) {
        List<Zone> mines = new ArrayList<Zone>();
        if (request.mapInfo != null && request.mapInfo.zones != null) {
            for (Zone z : request.mapInfo.zones) {
                if (z != null && z.pos != null && isOre(z.neutralType)) {
                    mines.add(z);
                }
            }
        }
        Pos best = null;
        int bestDist = Integer.MAX_VALUE;
        for (Zone z : mines) {
            int dist = MapUtil.chebyshev(worker.pos, z.pos);
            if (dist < bestDist) {
                bestDist = dist;
                best = z.pos;
            }
        }
        return best;
    }

    /** 移动到指定类型中立区域旁 */
    private RoleCommand moveToZone(Role role, GameRequest request, GameContext ctx,
                                   Set<Integer> occupiedRoles, String neutralType) {
        Zone zone = findZone(request, neutralType);
        if (zone == null || zone.pos == null) {
            return null;
        }
        if (MapUtil.isAdjacentOrSame(role.pos, zone.pos)) {
            return null;
        }
        return moveToward(role, zone.pos, request, ctx, occupiedRoles);
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

    /** 从 mapInfo.zones 查找指定 neutralType */
    private Zone findZone(GameRequest request, String neutralType) {
        if (request.mapInfo == null || request.mapInfo.zones == null) {
            return null;
        }
        List<Zone> zones = MapUtil.findZones(request.mapInfo.zones, neutralType);
        return zones.isEmpty() ? null : zones.get(0);
    }

    /** 背包中最值钱的矿石（按小贩当前收购价） */
    private String mostValuableOre(Role worker, GameRequest request) {
        String best = null;
        int bestPrice = -1;
        String[] ores = {Constants.ORE_STONE, Constants.ORE_IRON, Constants.ORE_COPPER};
        for (String ore : ores) {
            if (!RoleUtil.hasInBackpack(worker, ore)) {
                continue;
            }
            int price = getShopPrice(request, ore);
            if (price > bestPrice) {
                bestPrice = price;
                best = ore;
            }
        }
        return best;
    }

    /** 从小贩收购清单查矿石价格，查不到返回 0 */
    private int getShopPrice(GameRequest request, String ore) {
        if (request.vendorShopList == null) {
            return 0;
        }
        for (ShopItem item : request.vendorShopList) {
            if (item != null && ore.equals(item.name)) {
                return item.price;
            }
        }
        return 0;
    }

    /** 找 level < 3 的武器工事 */
    private Role findWeaponToUpgrade(GameRequest request) {
        if (request.teamOur == null || request.teamOur.roles == null) {
            return null;
        }
        for (Role r : request.teamOur.roles) {
            if (r != null && isWeapon(r.roleType) && r.level < 3) {
                return r;
            }
        }
        return null;
    }

    /** 背包中是否持有任一升级券，返回券名 */
    private String hasUpgradeVoucher(Role worker) {
        String[] vouchers = {
                Constants.ITEM_WEAPON_UPGRADE_1, Constants.ITEM_WEAPON_UPGRADE_2,
                Constants.ITEM_WALL_UPGRADE_1, Constants.ITEM_WALL_UPGRADE_2,
                Constants.ITEM_STATION_UPGRADE_1, Constants.ITEM_STATION_UPGRADE_2
        };
        for (String v : vouchers) {
            if (RoleUtil.hasInBackpack(worker, v)) {
                return v;
            }
        }
        return null;
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

    private boolean isOre(String neutralType) {
        return Constants.ORE_STONE.equals(neutralType)
                || Constants.ORE_IRON.equals(neutralType)
                || Constants.ORE_COPPER.equals(neutralType);
    }

    private boolean isWeapon(String roleType) {
        return Constants.ROLE_GATLING.equals(roleType)
                || Constants.ROLE_RAILGUN.equals(roleType)
                || Constants.ROLE_ROCKET.equals(roleType);
    }
}
