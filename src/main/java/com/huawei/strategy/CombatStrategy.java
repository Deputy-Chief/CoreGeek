package com.huawei.strategy;

import java.util.*;
import com.huawei.model.*;
import com.huawei.util.*;

/** 生存优先；按武器特性选择有效伤害、击杀和防御收益最高的目标。 */
public class CombatStrategy {
    public void execute(GameResponse res, GameRequest req, GameContext ctx, Set<Integer> used) {
        for (Role r : StrategySupport.actors(req))
            StrategySupport.emit(r, StrategySupport.emergency(r, req, ctx, true), res, ctx, used);
        List<RobotRole> bots = new ArrayList<RobotRole>();
        if (req.robot != null && req.robot.roles != null) for (RobotRole b : req.robot.roles)
            if (b != null && b.pos != null && b.health > 0) bots.add(b);
        if (bots.isEmpty()) return;
        for (Role w : req.teamOur.roles) {
            if (!StrategySupport.weapon(w) || w.pos == null || w.cooldown > 0) continue;
            Role controller = controller(w, req, ctx, used);
            if (controller == null) continue;
            List<Pos> aim = Constants.ROLE_GATLING.equals(w.roleType) ? gatling(w, bots, ctx)
                    : Constants.ROLE_RAILGUN.equals(w.roleType) ? railgun(w, bots, ctx) : rocket(w, bots, req, ctx);
            if (aim.isEmpty()) continue;
            res.roleCommandMap.put(w.id, RoleCommand.attack(String.valueOf(controller.id), aim));
            used.add(controller.id);
        }
    }

    private Role controller(Role w, GameRequest req, GameContext ctx, Set<Integer> used) {
        Role fallback = null;
        for (Role r : StrategySupport.actors(req)) {
            if (used.contains(r.id) || !MapUtil.isAdjacent(r.pos, w.pos)) continue;
            Role assigned = StrategySupport.assignedWeapon(r, req, ctx);
            if (assigned != null && assigned.id == w.id) return r;
            // 不抢走已就位的其他武器操控者。
            if (assigned == null || !MapUtil.isAdjacent(r.pos, assigned.pos)) fallback = r;
        }
        return fallback;
    }

    private double priority(RobotRole b, GameContext ctx) {
        double p = ctx.teamType.equals(b.targetTeam) ? 100 : 1;
        if (ctx.stationPos != null) p *= 1 + 6.0 / (1 + MapUtil.chebyshev(b.pos, ctx.stationPos));
        return p;
    }

    private int points(RobotRole b) {
        return Constants.ROBOT_BOSS.equals(b.roleType) ? 10 : Constants.ROBOT_LARGE.equals(b.roleType) ? 4
                : Constants.ROBOT_MIDDLE.equals(b.roleType) ? 2 : 1;
    }

    private List<Pos> gatling(Role w, List<RobotRole> bots, GameContext ctx) {
        List<RobotRole> visible = new ArrayList<RobotRole>();
        for (RobotRole b : bots) if (MapUtil.chebyshev(w.pos, b.pos) <= w.attackRange) visible.add(b);
        List<Pos> best = new ArrayList<Pos>();
        double bestScore = -1;
        // 枚举锥形起始边界，避免贪心首目标导致漏掉另一侧的小型集群。
        for (RobotRole edge : visible) {
            final double start = angle(w.pos, edge.pos);
            List<RobotRole> cone = new ArrayList<RobotRole>();
            for (RobotRole b : visible) if (positiveAngle(angle(w.pos, b.pos) - start) <= Math.PI / 2 + 1e-9) cone.add(b);
            Collections.sort(cone, (a, b) -> Double.compare(gatlingValue(b, ctx), gatlingValue(a, ctx)));
            List<Pos> aim = new ArrayList<Pos>();
            double score = 0;
            for (RobotRole b : cone) {
                if (aim.size() >= Math.min(3, Math.max(1, w.level))) break;
                // 子弹被弹道上最近机器人截获，按真实首个命中计分。
                RobotRole first = firstHit(w.pos, b.pos, bots);
                if (first != b) continue;
                aim.add(b.pos); score += gatlingValue(b, ctx);
            }
            if (score > bestScore) { bestScore = score; best = aim; }
        }
        return best;
    }

    private double gatlingValue(RobotRole b, GameContext ctx) {
        return priority(b, ctx) * (points(b) <= 2 ? 30 + points(b) : 2)
                * (b.health <= 10 ? 3 : 1);
    }

    private List<Pos> railgun(Role w, List<RobotRole> bots, GameContext ctx) {
        Pos best = null;
        double bestScore = -1;
        for (RobotRole endpoint : bots) {
            if (MapUtil.chebyshev(w.pos, endpoint.pos) > w.attackRange) continue;
            List<RobotRole> line = lineHits(w.pos, endpoint.pos, bots);
            int energy = Math.max(1, w.attackPower);
            double score = 0;
            for (RobotRole b : line) {
                int damage = Math.min(energy, b.health);
                score += priority(b, ctx) * (damage + (damage == b.health ? 40 * points(b) : 0));
                energy -= damage;
                if (energy == 0) break;
            }
            if (score > bestScore) { bestScore = score; best = endpoint.pos; }
        }
        return best == null ? Collections.<Pos>emptyList() : Collections.singletonList(best);
    }

    private List<Pos> rocket(Role w, List<RobotRole> bots, GameRequest req, GameContext ctx) {
        Set<Pos> candidates = new LinkedHashSet<Pos>();
        for (RobotRole b : bots) for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
            Pos p = new Pos(b.pos.x + dx, b.pos.y + dy);
            if (MapUtil.isValidPos(p, req.mapInfo.width, req.mapInfo.height)) candidates.add(p);
        }
        List<Pos> aim = new ArrayList<Pos>();
        Map<Integer, Integer> damageSoFar = new HashMap<Integer, Integer>();
        for (int shot = 0; shot < Math.min(3, Math.max(1, w.level)); shot++) {
            Pos best = null;
            double bestScore = 0;
            for (Pos p : candidates) {
                double score = 0;
                for (RobotRole b : bots) {
                    int remaining = b.health - damageSoFar.getOrDefault(b.id, 0);
                    int damage = Math.min(Math.max(0, remaining), blast(p, b.pos));
                    score += priority(b, ctx) * (damage + (damage > 0 && damage == remaining ? 30 * points(b) : 0));
                }
                if (score > bestScore) { bestScore = score; best = p; }
            }
            if (best == null) break;
            aim.add(best); // 允许重复落点：三级火箭可叠加60中心伤害。
            for (RobotRole b : bots) damageSoFar.put(b.id, damageSoFar.getOrDefault(b.id, 0) + blast(best, b.pos));
        }
        return aim;
    }

    private int blast(Pos center, Pos p) { return center.equals(p) ? 20 : MapUtil.isAdjacent(center, p) ? 10 : 0; }
    private double angle(Pos from, Pos to) { return Math.atan2(to.y - from.y, to.x - from.x); }
    private double positiveAngle(double a) { return (a + 2 * Math.PI) % (2 * Math.PI); }

    private RobotRole firstHit(Pos from, Pos to, List<RobotRole> bots) {
        List<RobotRole> line = lineHits(from, to, bots);
        return line.isEmpty() ? null : line.get(0);
    }

    private List<RobotRole> lineHits(Pos from, Pos to, List<RobotRole> bots) {
        List<RobotRole> line = new ArrayList<RobotRole>();
        for (RobotRole b : bots) if (intersects(from, to, b.pos)) line.add(b);
        Collections.sort(line, Comparator.comparingDouble(b -> distanceSquared(from, b.pos)));
        return line;
    }

    private double distanceSquared(Pos a, Pos b) {
        return (a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y);
    }

    /** 线段和机器人所在格相交，匹配格中心连线的弹道规则。 */
    private boolean intersects(Pos from, Pos to, Pos cell) {
        double lo = 0, hi = 1;
        int[] a = {from.x, from.y}, d = {to.x - from.x, to.y - from.y}, c = {cell.x, cell.y};
        for (int axis = 0; axis < 2; axis++) {
            if (d[axis] == 0) { if (Math.abs(a[axis] - c[axis]) > 0.5) return false; }
            else {
                double t1 = (c[axis] - 0.5 - a[axis]) / d[axis], t2 = (c[axis] + 0.5 - a[axis]) / d[axis];
                lo = Math.max(lo, Math.min(t1, t2)); hi = Math.min(hi, Math.max(t1, t2));
                if (lo > hi) return false;
            }
        }
        return hi > 0;
    }
}
