package com.huawei.strategy;

import java.util.*;
import com.huawei.model.*;
import com.huawei.util.*;

/** 工人1负责石头和建设，工人2负责金属收入；按日推进防御目标。 */
public class WorkerStrategy {
    public void execute(List<Role> workers, GameResponse res, GameRequest req, GameContext ctx,
                        Set<Integer> used, boolean day) {
        if (workers == null) return;
        Collections.sort(workers, Comparator.comparingInt(r -> r.id));
        for (int i = 0; i < workers.size(); i++) {
            Role worker = workers.get(i);
            if (used.contains(worker.id)) continue;
            StrategySupport.emit(worker, decide(worker, i == 0, req, ctx, day), res, ctx, used);
        }
    }

    private RoleCommand decide(Role w, boolean builder, GameRequest req, GameContext ctx, boolean day) {
        RoleCommand cmd = StrategySupport.emergency(w, req, ctx, !day);
        if (cmd != null) return cmd;
        if (!day || StrategySupport.prepareNight(w, req, ctx)) return StrategySupport.defend(w, req, ctx);
        cmd = upgrade(w, req, ctx, false);
        if (cmd != null) return cmd;
        if (w.health < Constants.WORKER_HP * 3 / 10 && !RoleUtil.hasInBackpack(w, Constants.ITEM_MEDICINE)) {
            cmd = StrategySupport.buy(w, Constants.ITEM_MEDICINE, req, ctx, 0);
            if (cmd != null) return cmd;
        }
        if (builder) {
            cmd = repair(w, req, ctx);
            if (cmd != null) return cmd;
            if (RoleUtil.countInBackpack(w, Constants.ORE_STONE) >= 10) ctx.wallBuilders.add(w.id);
            if (RoleUtil.countInBackpack(w, Constants.ORE_STONE) == 0) ctx.wallBuilders.remove(w.id);
            if (ctx.weaponCount == 0 && ctx.wallBuilders.contains(w.id) && countWalls(req) < 2) {
                cmd = buildWall(w, req, ctx);
                if (cmd != null) return cmd;
            }
            cmd = buildWeapon(w, req, ctx);
            if (cmd != null) return cmd;
            cmd = upgrade(w, req, ctx, true);
            if (cmd != null) return cmd;
            if (ctx.wallBuilders.contains(w.id)) {
                cmd = buildWall(w, req, ctx);
                if (cmd != null) return cmd;
            }
        }
        cmd = sell(w, builder, req, ctx);
        if (cmd != null) return cmd;
        if (builder) { cmd = supplies(w, req, ctx); if (cmd != null) return cmd; }
        return collect(w, builder, req, ctx);
    }

    private int countWalls(GameRequest req) {
        return RoleUtil.filterByType(req.teamOur.roles, Constants.ROLE_WALL).size();
    }

    private RoleCommand buildWeapon(Role w, GameRequest req, GameContext ctx) {
        int wanted = ctx.currentDay < 2 ? 1 : Constants.MAX_WEAPONS;
        if (ctx.weaponCount + ctx.plannedWeapons.size() >= wanted) return null;
        String type = null;
        for (String candidate : new String[]{Constants.ROLE_GATLING, Constants.ROLE_RAILGUN, Constants.ROLE_ROCKET})
            if (RoleUtil.findFirst(req.teamOur.roles, candidate) == null && !ctx.plannedWeapons.contains(candidate)) {
                type = candidate; break;
            }
        if (type == null || ctx.stationPos == null) return null;
        Pos s = ctx.stationPos;
        boolean left = Constants.TEAM_CHALLENGER.equals(ctx.teamType);
        // 沿用项目基地周边槽位约定，朝敌方的角优先，双方镜像。
        List<Pos> slots = left
                ? Arrays.asList(new Pos(s.x + 2, s.y - 1), new Pos(s.x - 1, s.y - 1), new Pos(s.x - 1, s.y + 2))
                : Arrays.asList(new Pos(s.x - 1, s.y + 2), new Pos(s.x + 2, s.y + 2), new Pos(s.x + 2, s.y - 1));
        Pos p = nearestEmpty(w, slots, req, ctx);
        return p == null ? null : MapUtil.isAdjacent(w.pos, p) ? RoleCommand.build(type, p)
                : StrategySupport.move(w, p, req, ctx);
    }

    private List<Pos> wallSlots(GameContext ctx) {
        List<Pos> slots = new ArrayList<Pos>();
        Pos s = ctx.stationPos;
        if (s == null) return slots;
        boolean left = Constants.TEAM_CHALLENGER.equals(ctx.teamType);
        // 原点在左下角，左上基地的来袭面是右侧和下侧。
        for (int i = 0; i < 2; i++) {
            slots.add(new Pos(s.x + (left ? 2 : -1), s.y + i));
            slots.add(new Pos(s.x + i, s.y + (left ? -1 : 2)));
        }
        if (ctx.currentDay >= 2) for (int i = 0; i < 2; i++) {
            slots.add(new Pos(s.x + (left ? -1 : 2), s.y + i));
            slots.add(new Pos(s.x + i, s.y + (left ? 2 : -1)));
        }
        return slots;
    }

    private Pos nearestEmpty(Role w, List<Pos> slots, GameRequest req, GameContext ctx) {
        List<Pos> obstacles = StrategySupport.obstacles(req, ctx);
        Pos best = null;
        for (Pos p : slots) if (MapUtil.isValidPos(p, req.mapInfo.width, req.mapInfo.height)
                && !obstacles.contains(p) && (best == null || MapUtil.chebyshev(w.pos, p)
                < MapUtil.chebyshev(w.pos, best))) best = p;
        return best;
    }

    private RoleCommand buildWall(Role w, GameRequest req, GameContext ctx) {
        if (!RoleUtil.hasInBackpack(w, Constants.ORE_STONE)) return null;
        List<Pos> slots = wallSlots(ctx);
        Pos p = nearestEmpty(w, slots.subList(0, Math.min(4, slots.size())), req, ctx);
        if (p == null) p = nearestEmpty(w, slots, req, ctx);
        return p == null ? null : MapUtil.isAdjacent(w.pos, p) ? RoleCommand.build(Constants.ROLE_WALL, p)
                : StrategySupport.move(w, p, req, ctx);
    }

    private RoleCommand repair(Role w, GameRequest req, GameContext ctx) {
        for (Role wall : req.teamOur.roles) if (StrategySupport.damagedWall(wall)
                && !ctx.reservedBuildings.contains(wall.id)) {
            if (RoleUtil.hasInBackpack(w, Constants.ITEM_WALL_FIXER)) {
                if (StrategySupport.adjacent(w, wall)) {
                    ctx.reservedBuildings.add(wall.id);
                    return RoleCommand.use(Constants.ITEM_WALL_FIXER, wall.pos);
                }
                return StrategySupport.move(w, wall.pos, req, ctx);
            }
            return StrategySupport.buy(w, Constants.ITEM_WALL_FIXER, req, ctx, 0);
        }
        return null;
    }

    private int upgradePriority(Role r, GameContext ctx) {
        if (r == null || r.level < 1 || r.level >= 3) return -1;
        if (Constants.ROLE_STATION.equals(r.roleType))
            return r.level == 1 || ctx.currentDay >= 5 || r.health < 1000 ? 100 : 60;
        if (Constants.ROLE_GATLING.equals(r.roleType)) return ctx.currentDay >= 2 ? 90 : -1;
        if (Constants.ROLE_RAILGUN.equals(r.roleType)) return ctx.currentDay >= 2 ? (r.level == 1 ? 80 : 50) : -1;
        if (Constants.ROLE_ROCKET.equals(r.roleType)) return ctx.currentDay >= 5 ? 40 : -1;
        if (Constants.ROLE_WALL.equals(r.roleType)) return ctx.currentDay >= 5 ? 20 : -1;
        return -1;
    }

    private RoleCommand upgrade(Role w, GameRequest req, GameContext ctx, boolean purchase) {
        List<Role> buildings = new ArrayList<Role>(req.teamOur.roles);
        Collections.sort(buildings, (a, b) -> Integer.compare(upgradePriority(b, ctx), upgradePriority(a, ctx)));
        for (Role r : buildings) {
            String item = StrategySupport.voucher(r);
            if (item == null || ctx.reservedBuildings.contains(r.id)) continue;
            if (RoleUtil.hasInBackpack(w, item)) {
                ctx.reservedBuildings.add(r.id);
                return StrategySupport.adjacent(w, r) ? RoleCommand.use(item, r.pos)
                        : StrategySupport.move(w, r.pos, req, ctx);
            }
        }
        if (!purchase) return null;
        for (Role r : buildings) {
            if (upgradePriority(r, ctx) < 0 || ctx.reservedBuildings.contains(r.id)) continue;
            if (ctx.currentDay >= 8 && !Constants.ROLE_STATION.equals(r.roleType)) continue;
            String item = StrategySupport.voucher(r);
            boolean carried = false;
            for (Role actor : StrategySupport.actors(req)) if (RoleUtil.hasInBackpack(actor, item)) carried = true;
            if (carried) continue;
            // 预算不足继续存钱，低优先级升级不能挤占基地/主武器预算。
            return StrategySupport.buy(w, item, req, ctx, 0);
        }
        return null;
    }

    private RoleCommand sell(Role w, boolean builder, GameRequest req, GameContext ctx) {
        String best = null;
        int bestValue = -1, amount = 0;
        for (String ore : new String[]{Constants.ORE_STONE, Constants.ORE_IRON, Constants.ORE_COPPER}) {
            int n = RoleUtil.countInBackpack(w, ore);
            if (builder && Constants.ORE_STONE.equals(ore)) n = Math.max(0, n - (ctx.currentDay < 2 ? 10 : 4));
            int price = StrategySupport.price(req.vendorShopList, ore);
            if (n == 0 || price < 0) continue;
            if (EconomyStrategy.hold(ore, ctx) && RoleUtil.backpackRemaining(w) > 5 && ctx.weaponCount > 0) continue;
            Integer old = ctx.previousPrices.get(ore);
            boolean eventSale = ctx.fallingOres.contains(ore) || (old != null && price > old)
                    || (ctx.holdUntilDay.containsKey(ore) && !EconomyStrategy.hold(ore, ctx));
            if (n < 10 && RoleUtil.backpackRemaining(w) > 5 && !eventSale) continue;
            if (n * price > bestValue) { best = ore; amount = n; bestValue = n * price; }
        }
        if (best == null) return null;
        Zone vendor = StrategySupport.nearestZone(w, req, Constants.NEUTRAL_VENDOR);
        if (vendor == null) return null;
        return MapUtil.isAdjacent(w.pos, vendor.pos) ? RoleCommand.sell(best, amount)
                : StrategySupport.move(w, vendor.pos, req, ctx);
    }

    private RoleCommand supplies(Role w, GameRequest req, GameContext ctx) {
        if (w.health < Constants.WORKER_HP * 3 / 10 && !RoleUtil.hasInBackpack(w, Constants.ITEM_MEDICINE))
            return StrategySupport.buy(w, Constants.ITEM_MEDICINE, req, ctx, 0);
        if (ctx.currentDay < 5) return null;
        int reserve = EconomyStrategy.defenseReady(req) || ctx.currentDay >= 8 ? 0 : 150;
        for (String item : new String[]{Constants.ITEM_WALL_FIXER, Constants.ITEM_MEDICINE,
                Constants.ITEM_DIZZY_WEAPON, Constants.ITEM_BOMB}) if (!RoleUtil.hasInBackpack(w, item)) {
            RoleCommand cmd = StrategySupport.buy(w, item, req, ctx, reserve);
            if (cmd != null) return cmd;
        }
        String order = EconomyStrategy.summonOrder(req, ctx);
        if (order != null) {
            if (RoleUtil.hasInBackpack(w, order)) { ctx.summonCountToday++; return RoleCommand.use(order); }
            return StrategySupport.buy(w, order, req, ctx, 300);
        }
        return null;
    }

    private RoleCommand collect(Role w, boolean builder, GameRequest req, GameContext ctx) {
        if (RoleUtil.backpackRemaining(w) <= 0 || req.mapInfo.zones == null) return null;
        Zone best = null;
        double bestScore = -Double.MAX_VALUE;
        boolean needStone = builder && countWalls(req) < (ctx.currentDay < 2 ? 4 : 8)
                && RoleUtil.countInBackpack(w, Constants.ORE_STONE) < 10;
        for (Zone z : req.mapInfo.zones) {
            if (z == null || z.pos == null || !(Constants.ORE_STONE.equals(z.neutralType)
                    || Constants.ORE_IRON.equals(z.neutralType) || Constants.ORE_COPPER.equals(z.neutralType))
                    || EconomyStrategy.unavailable(z.neutralType, ctx)) continue;
            int price = Math.max(1, StrategySupport.price(req.vendorShopList, z.neutralType));
            double score = price * 2.0 - MapUtil.chebyshev(w.pos, z.pos)
                    - (ctx.stationPos == null ? 0 : MapUtil.chebyshev(ctx.stationPos, z.pos) * 0.2);
            if (needStone && Constants.ORE_STONE.equals(z.neutralType)) score += 1000;
            if (!builder && !Constants.ORE_STONE.equals(z.neutralType)) score += 30;
            if (EconomyStrategy.hold(z.neutralType, ctx)) score += 20;
            if (score > bestScore) { bestScore = score; best = z; }
        }
        if (best == null) return null;
        ctx.workerTargetMine.put(w.id, best.pos);
        return MapUtil.isAdjacent(w.pos, best.pos) ? RoleCommand.collect(best.pos)
                : StrategySupport.move(w, best.pos, req, ctx);
    }
}
