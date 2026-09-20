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
 * <p>白天优先级链：
 * <ol>
 *   <li>白天后期（回合号%130 ≥ 60）→ 回最近武器旁，准备夜晚御敌；</li>
 *   <li>围墙血量低且背包有 WallFixer → 修复围墙（保证围墙血量）；</li>
 *   <li>武器工事 &lt;3 → 优先建造火箭发射台（开局 3 火箭炮）；</li>
 *   <li>背包有石头 → 按阵营优先级建围墙（面向敌人的一面优先，其次两侧，后方不建）；</li>
 *   <li>升级：武器升级券优先（火箭优先），其次围墙/基地升级券，无券且金币足够 → 买武器升级券；</li>
 *   <li>背包快满 → 到小贩卖最值钱矿石；</li>
 *   <li>采集最近矿点；</li>
 *   <li>武器商店旁待命。</li>
 * </ol>
 * <p>夜晚：血量低用药 → 移动到最近武器旁待命。
 */
public class WorkerStrategy {

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
        int roundInDay = request.roundNo % Constants.ROUNDS_PER_DAY;

        // 0. 白天后期：回最近武器旁，准备夜晚御敌
        if (roundInDay >= Constants.NIGHT_PREP_ROUND) {
            return moveToNearestWeapon(worker, request, ctx, occupiedRoles);
        }

        // 1. 围墙血量低且有 WallFixer → 修复围墙（保证围墙血量）
        RoleCommand repair = tryRepairWall(worker, response, request, ctx, occupiedRoles);
        if (repair != null) {
            return repair;
        }

        // 2. 武器工事 <3 → 优先建造火箭发射台（开局 3 火箭炮）
        RoleCommand weapon = tryBuildWeapon(worker, response, request, ctx, occupiedRoles);
        if (weapon != null) {
            return weapon;
        }

        // 3. 有石头 → 按阵营优先级建围墙（面向敌人一面优先，其次两侧，后方不建）
        RoleCommand wall = tryBuildWall(worker, response, request, ctx, occupiedRoles);
        if (wall != null) {
            return wall;
        }

        // 4. 升级：武器优先，其次围墙/基地，无券则买武器升级券
        RoleCommand upgrade = tryUpgrade(worker, response, request, ctx, occupiedRoles);
        if (upgrade != null) {
            return upgrade;
        }

        // 5. 背包快满（剩余 ≤5）→ 卖最值钱矿石
        if (RoleUtil.backpackRemaining(worker) <= 5) {
            RoleCommand sell = sellMostValuable(worker, response, request, ctx, occupiedRoles);
            if (sell != null) {
                return sell;
            }
        }

        // 6. 正常采集
        RoleCommand collect = collectOre(worker, response, request, ctx, occupiedRoles);
        if (collect != null) {
            return collect;
        }

        // 7. 无事可做 → 移动到武器商店待命
        return moveToZone(worker, request, ctx, occupiedRoles, Constants.NEUTRAL_WEAPON_SHOP);
    }

    /** 夜晚：血量低用药品；否则移动到最近武器旁待命 */
    private RoleCommand decideNightAction(Role worker, GameResponse response, GameRequest request, GameContext ctx,
                                          Set<Integer> occupiedRoles) {
        if (worker.health < 66 && RoleUtil.hasInBackpack(worker, Constants.ITEM_MEDICINE)) {
            return RoleCommand.use(Constants.ITEM_MEDICINE);
        }
        return moveToNearestWeapon(worker, request, ctx, occupiedRoles);
    }

    /** 围墙血量低（< 阈值）且有 WallFixer → 走到围墙旁修复 */
    private RoleCommand tryRepairWall(Role worker, GameResponse response, GameRequest request, GameContext ctx,
                                      Set<Integer> occupiedRoles) {
        if (!RoleUtil.hasInBackpack(worker, Constants.ITEM_WALL_FIXER)) {
            return null;
        }
        Role wall = findLowestHpWall(request, ctx);
        if (wall == null) {
            return null;
        }
        if (MapUtil.isAdjacent(worker.pos, wall.pos)) {
            return RoleCommand.use(Constants.ITEM_WALL_FIXER, wall.pos);
        }
        return moveToward(worker, wall.pos, request, ctx, occupiedRoles);
    }

    /** 武器工事 <3 → 建造火箭发射台（选择离工人最近的空位） */
    private RoleCommand tryBuildWeapon(Role worker, GameResponse response, GameRequest request, GameContext ctx,
                                       Set<Integer> occupiedRoles) {
        if (ctx.weaponCount >= Constants.MAX_WEAPONS) {
            return null;
        }
        Pos target = nextWeaponBuildPos(worker, request, ctx);
        if (target == null) {
            return null;
        }
        if (MapUtil.isAdjacent(worker.pos, target)) {
            return RoleCommand.build(Constants.ROLE_ROCKET, target);
        }
        return moveToward(worker, target, request, ctx, occupiedRoles);
    }

    /**
     * 选择下一个火箭炮台建造位（基地旁、不与围墙位冲突）。
     * 挑战者基地在左上、敌人右下 → 炮台放在左/上/下侧外角；防守者镜像。
     */
    private Pos nextWeaponBuildPos(Role worker, GameRequest request, GameContext ctx) {
        Pos s = ctx.stationPos;
        if (s == null) {
            return null;
        }
        Pos[] candidates;
        if (Constants.TEAM_CHALLENGER.equals(ctx.teamType)) {
            candidates = new Pos[]{
                    new Pos(s.x - 1, s.y - 1),  // 后方（左上角，不建墙）
                    new Pos(s.x + 2, s.y - 1),  // 右上外角
                    new Pos(s.x - 1, s.y + 2)   // 左下外角
            };
        } else {
            candidates = new Pos[]{
                    new Pos(s.x + 2, s.y + 2),  // 后方（右下角，不建墙）
                    new Pos(s.x - 1, s.y + 2),  // 左下外角
                    new Pos(s.x + 2, s.y - 1)   // 右上外角
            };
        }
        List<Pos> obstacles = collectObstacles(request, ctx);
        Pos best = null;
        int bestDist = Integer.MAX_VALUE;
        for (Pos c : candidates) {
            if (!MapUtil.isValidPos(c, request.mapInfo.width, request.mapInfo.height)) {
                continue;
            }
            if (MapUtil.isBlocked(c, obstacles)) {
                continue;
            }
            int d = MapUtil.chebyshev(worker.pos, c);
            if (d < bestDist) {
                bestDist = d;
                best = c;
            }
        }
        return best;
    }

    /** 有石头 → 按阵营优先级建围墙（面向敌人一面优先，其次两侧，后方不建） */
    private RoleCommand tryBuildWall(Role worker, GameResponse response, GameRequest request, GameContext ctx,
                                     Set<Integer> occupiedRoles) {
        if (!RoleUtil.hasInBackpack(worker, Constants.ORE_STONE)) {
            return null;
        }
        Pos target = nextWallBuildPos(worker, request, ctx);
        if (target == null) {
            return null;
        }
        if (MapUtil.isAdjacent(worker.pos, target)) {
            return RoleCommand.build(Constants.ROLE_WALL, target);
        }
        return moveToward(worker, target, request, ctx, occupiedRoles);
    }

    /**
     * 选择下一个围墙建造位：面向敌人的一面严格优先，其次两侧，后方不建。
     * 基地 2×2 位于 (x,y)~(x+1,y+1)；挑战者（左上）敌人基地在右下，防守者镜像。
     */
    private Pos nextWallBuildPos(Role worker, GameRequest request, GameContext ctx) {
        Pos s = ctx.stationPos;
        if (s == null) {
            return null;
        }
        List<Pos> front = new ArrayList<Pos>();
        List<Pos> sides = new ArrayList<Pos>();
        if (Constants.TEAM_CHALLENGER.equals(ctx.teamType)) {
            // 面向敌人（右下）：右下角 + 右侧边 + 下侧边
            front.add(new Pos(s.x + 2, s.y + 2));
            front.add(new Pos(s.x + 2, s.y));
            front.add(new Pos(s.x + 2, s.y + 1));
            front.add(new Pos(s.x, s.y + 2));
            front.add(new Pos(s.x + 1, s.y + 2));
            // 两侧：左侧边 + 上侧边
            sides.add(new Pos(s.x - 1, s.y));
            sides.add(new Pos(s.x - 1, s.y + 1));
            sides.add(new Pos(s.x, s.y - 1));
            sides.add(new Pos(s.x + 1, s.y - 1));
            // 后方（左上角）不建
        } else {
            // 面向敌人（左上）：左上角 + 左侧边 + 上侧边
            front.add(new Pos(s.x - 1, s.y - 1));
            front.add(new Pos(s.x - 1, s.y));
            front.add(new Pos(s.x - 1, s.y + 1));
            front.add(new Pos(s.x, s.y - 1));
            front.add(new Pos(s.x + 1, s.y - 1));
            // 两侧：右侧边 + 下侧边
            sides.add(new Pos(s.x + 2, s.y));
            sides.add(new Pos(s.x + 2, s.y + 1));
            sides.add(new Pos(s.x, s.y + 2));
            sides.add(new Pos(s.x + 1, s.y + 2));
            // 后方（右下角）不建
        }
        List<Pos> obstacles = collectObstacles(request, ctx);
        // 第一阶段：面向敌人的一面
        Pos p = pickNearestEmpty(worker, front, obstacles, request.mapInfo.width, request.mapInfo.height);
        if (p != null) {
            return p;
        }
        // 第二阶段：两侧
        return pickNearestEmpty(worker, sides, obstacles, request.mapInfo.width, request.mapInfo.height);
    }

    /** 从候选位中选离 worker 最近的合法空位 */
    private Pos pickNearestEmpty(Role worker, List<Pos> candidates, List<Pos> obstacles,
                                 int mapWidth, int mapHeight) {
        Pos best = null;
        int bestDist = Integer.MAX_VALUE;
        for (Pos c : candidates) {
            if (!MapUtil.isValidPos(c, mapWidth, mapHeight)) {
                continue;
            }
            if (MapUtil.isBlocked(c, obstacles)) {
                continue;
            }
            int d = MapUtil.chebyshev(worker.pos, c);
            if (d < bestDist) {
                bestDist = d;
                best = c;
            }
        }
        return best;
    }

    /** 升级：武器升级券优先（火箭优先）→ 围墙/基地升级券 → 无券且金币足够买武器升级券 */
    private RoleCommand tryUpgrade(Role worker, GameResponse response, GameRequest request, GameContext ctx,
                                   Set<Integer> occupiedRoles) {
        // 1. 武器升级券（火箭优先）
        Role weapon = findWeaponToUpgrade(request);
        if (weapon != null) {
            String voucher = matchingWeaponVoucher(worker, weapon.level);
            if (voucher != null) {
                if (MapUtil.isAdjacent(worker.pos, weapon.pos)) {
                    return RoleCommand.use(voucher, weapon.pos);
                }
                return moveToward(worker, weapon.pos, request, ctx, occupiedRoles);
            }
        }
        // 2. 围墙升级券（给血量最低且未满级的围墙，升级回满血）
        Role wall = findLowestHpWall(request, ctx);
        if (wall != null && wall.level < 3) {
            String voucher = matchingWallVoucher(worker, wall.level);
            if (voucher != null) {
                if (MapUtil.isAdjacent(worker.pos, wall.pos)) {
                    return RoleCommand.use(voucher, wall.pos);
                }
                return moveToward(worker, wall.pos, request, ctx, occupiedRoles);
            }
        }
        // 3. 基地升级券
        Role station = findStationToUpgrade(request);
        if (station != null) {
            String voucher = matchingStationVoucher(worker, station.level);
            if (voucher != null) {
                if (MapUtil.isAdjacent(worker.pos, station.pos)) {
                    return RoleCommand.use(voucher, station.pos);
                }
                return moveToward(worker, station.pos, request, ctx, occupiedRoles);
            }
        }
        // 4. 无券且金币足够 → 到武器商店买武器升级券1
        if (weapon != null && request.teamOur.goldNum >= 100
                && !RoleUtil.hasInBackpack(worker, Constants.ITEM_WEAPON_UPGRADE_1)) {
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

    /** 移动到最近武器旁；已在旁则返回 null（待命） */
    private RoleCommand moveToNearestWeapon(Role worker, GameRequest request, GameContext ctx,
                                            Set<Integer> occupiedRoles) {
        Role weapon = findNearestWeapon(request, worker.pos);
        if (weapon == null) {
            return null;
        }
        if (MapUtil.isAdjacent(worker.pos, weapon.pos)) {
            return null;
        }
        return moveToward(worker, weapon.pos, request, ctx, occupiedRoles);
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
        Pos next = MapUtil.nextStepToward(role.pos, target, collectObstacles(request, ctx),
                request.mapInfo.width, request.mapInfo.height);
        if (next == null || next.equals(role.pos)) {
            return null;
        }
        return RoleCommand.move(next);
    }

    /** 收集所有占用坐标作为障碍物 */
    private List<Pos> collectObstacles(GameRequest request, GameContext ctx) {
        return MapUtil.collectObstacles(request.teamOur.roles, request.teamEnemy.roles,
                request.robot == null ? null : request.robot.roles, request.mapInfo.zones);
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

    /** 找 level<3 的武器工事（优先火箭发射台） */
    private Role findWeaponToUpgrade(GameRequest request) {
        Role rocket = null;
        Role other = null;
        if (request.teamOur == null || request.teamOur.roles == null) {
            return null;
        }
        for (Role r : request.teamOur.roles) {
            if (r == null || !isWeapon(r.roleType) || r.level >= 3) {
                continue;
            }
            if (Constants.ROLE_ROCKET.equals(r.roleType)) {
                if (rocket == null) {
                    rocket = r;
                }
            } else if (other == null) {
                other = r;
            }
        }
        return rocket != null ? rocket : other;
    }

    /** 找血量最低且低于修复阈值的己方围墙 */
    private Role findLowestHpWall(GameRequest request, GameContext ctx) {
        Role lowest = null;
        int lowestHp = Integer.MAX_VALUE;
        if (request.teamOur == null || request.teamOur.roles == null) {
            return null;
        }
        for (Role r : request.teamOur.roles) {
            if (r == null || !Constants.ROLE_WALL.equals(r.roleType)) {
                continue;
            }
            if (r.health < lowestHp) {
                lowestHp = r.health;
                lowest = r;
            }
        }
        if (lowest != null && lowest.health < Constants.WALL_REPAIR_THRESHOLD) {
            return lowest;
        }
        return null;
    }

    /** 找 level<3 的基地 */
    private Role findStationToUpgrade(GameRequest request) {
        if (request.teamOur == null || request.teamOur.roles == null) {
            return null;
        }
        for (Role r : request.teamOur.roles) {
            if (r != null && Constants.ROLE_STATION.equals(r.roleType) && r.level < 3) {
                return r;
            }
        }
        return null;
    }

    /** 匹配武器等级对应的升级券（level1→Voucher1，level2→Voucher2） */
    private String matchingWeaponVoucher(Role worker, int level) {
        if (level == 1 && RoleUtil.hasInBackpack(worker, Constants.ITEM_WEAPON_UPGRADE_1)) {
            return Constants.ITEM_WEAPON_UPGRADE_1;
        }
        if (level == 2 && RoleUtil.hasInBackpack(worker, Constants.ITEM_WEAPON_UPGRADE_2)) {
            return Constants.ITEM_WEAPON_UPGRADE_2;
        }
        return null;
    }

    /** 匹配围墙等级对应的升级券 */
    private String matchingWallVoucher(Role worker, int level) {
        if (level == 1 && RoleUtil.hasInBackpack(worker, Constants.ITEM_WALL_UPGRADE_1)) {
            return Constants.ITEM_WALL_UPGRADE_1;
        }
        if (level == 2 && RoleUtil.hasInBackpack(worker, Constants.ITEM_WALL_UPGRADE_2)) {
            return Constants.ITEM_WALL_UPGRADE_2;
        }
        return null;
    }

    /** 匹配基地等级对应的升级券 */
    private String matchingStationVoucher(Role worker, int level) {
        if (level == 1 && RoleUtil.hasInBackpack(worker, Constants.ITEM_STATION_UPGRADE_1)) {
            return Constants.ITEM_STATION_UPGRADE_1;
        }
        if (level == 2 && RoleUtil.hasInBackpack(worker, Constants.ITEM_STATION_UPGRADE_2)) {
            return Constants.ITEM_STATION_UPGRADE_2;
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
