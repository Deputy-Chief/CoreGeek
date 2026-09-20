package com.huawei.strategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import com.huawei.model.GameContext;
import com.huawei.model.GameRequest;
import com.huawei.model.GameResponse;
import com.huawei.model.Pos;
import com.huawei.model.RobotRole;
import com.huawei.model.Role;
import com.huawei.model.RoleCommand;
import com.huawei.util.Constants;
import com.huawei.util.MapUtil;

/**
 * 武器操控与目标选择。
 *
 * <p>目标筛选：优先攻击 targetTeam 为我方的机器人，无则攻击全部可见机器人。
 * <p>威胁值：机器人基础分 × 当前血量（BOSS 10 &gt; 大型 4 &gt; 中型 2 &gt; 小型 1）。
 * <p>指令格式：roleCommandMap 的 key 为武器 ID，controllerId 为操控角色 ID。
 */
public class CombatStrategy {

    public void execute(GameResponse response, GameRequest request, GameContext ctx, Set<Integer> occupiedRoles) {
        if (request.robot == null || request.robot.roles == null || request.robot.roles.isEmpty()) {
            return;
        }
        if (request.teamOur == null || request.teamOur.roles == null) {
            return;
        }
        List<RobotRole> targets = filterTargetRobots(request.robot.roles, ctx.teamType);
        if (targets.isEmpty()) {
            return;
        }

        for (Role weapon : request.teamOur.roles) {
            if (weapon == null || weapon.pos == null || !isWeapon(weapon.roleType)) {
                continue;
            }
            // 火箭发射台 3 回合冷却
            if (Constants.ROLE_ROCKET.equals(weapon.roleType) && weapon.cooldown > 0) {
                continue;
            }
            Role controller = findController(weapon, request, occupiedRoles);
            if (controller == null) {
                continue;
            }
            List<Pos> aim = selectTargets(weapon, targets);
            if (aim == null || aim.isEmpty()) {
                continue;
            }
            RoleCommand cmd = RoleCommand.attack(String.valueOf(controller.id), aim);
            response.roleCommandMap.put(weapon.id, cmd);
            occupiedRoles.add(controller.id);
        }
    }

    /** 筛选 targetTeam 为我方的机器人；无则返回全部可见机器人 */
    private List<RobotRole> filterTargetRobots(List<RobotRole> robots, String teamType) {
        List<RobotRole> mine = new ArrayList<RobotRole>();
        for (RobotRole r : robots) {
            if (r != null && teamType != null && teamType.equals(r.targetTeam)) {
                mine.add(r);
            }
        }
        return mine.isEmpty() ? robots : mine;
    }

    /** 找武器周围一格内的非建筑、未占用角色；无则返回 null（本回合不攻击，待角色移动到武器旁） */
    private Role findController(Role weapon, GameRequest request, Set<Integer> occupiedRoles) {
        Role best = null;
        int bestDist = Integer.MAX_VALUE;
        for (Role r : request.teamOur.roles) {
            if (r == null || r.pos == null || isBuilding(r.roleType)) {
                continue;
            }
            if (occupiedRoles.contains(r.id)) {
                continue;
            }
            int d = MapUtil.chebyshev(r.pos, weapon.pos);
            if (d < bestDist) {
                bestDist = d;
                best = r;
            }
        }
        // 操控者必须站在武器周围一格内
        if (best != null && bestDist <= 1) {
            return best;
        }
        return null;
    }

    /** 按武器类型选择攻击目标 */
    private List<Pos> selectTargets(Role weapon, List<RobotRole> targets) {
        if (Constants.ROLE_GATLING.equals(weapon.roleType)) {
            return selectGatlingTargets(weapon, targets);
        }
        if (Constants.ROLE_RAILGUN.equals(weapon.roleType)) {
            return selectRailgunTarget(weapon, targets);
        }
        if (Constants.ROLE_ROCKET.equals(weapon.roleType)) {
            return selectRocketTargets(weapon, targets);
        }
        return null;
    }

    /** 加特林：等级决定目标数（1/2/3），多目标须在同一 90° 锥形内，按威胁值排序 */
    private List<Pos> selectGatlingTargets(Role weapon, List<RobotRole> targets) {
        int maxTargets = Math.max(1, weapon.level);
        List<RobotRole> sorted = sortByThreatDesc(targets);
        List<Pos> result = new ArrayList<Pos>();
        for (RobotRole r : sorted) {
            if (result.size() >= maxTargets) {
                break;
            }
            if (r.health <= 0) {
                continue;
            }
            if (MapUtil.chebyshev(weapon.pos, r.pos) > weapon.attackRange) {
                continue;
            }
            boolean inCone = true;
            for (Pos p : result) {
                if (angleDiffDeg(weapon.pos, p, r.pos) > 90.0) {
                    inCone = false;
                    break;
                }
            }
            if (inCone) {
                result.add(r.pos);
            }
        }
        return result;
    }

    /** 电磁炮：单目标，选攻击距离内威胁最大的机器人 */
    private List<Pos> selectRailgunTarget(Role weapon, List<RobotRole> targets) {
        RobotRole best = null;
        int bestThreat = -1;
        for (RobotRole r : targets) {
            if (r.health <= 0) {
                continue;
            }
            if (MapUtil.chebyshev(weapon.pos, r.pos) > weapon.attackRange) {
                continue;
            }
            int threat = getRobotThreat(r);
            if (threat > bestThreat) {
                bestThreat = threat;
                best = r;
            }
        }
        if (best == null) {
            return null;
        }
        List<Pos> result = new ArrayList<Pos>();
        result.add(best.pos);
        return result;
    }

    /** 火箭：等级决定导弹数（1/2/3），选机器人最密集的 3×3 区域，落点互不重叠 */
    private List<Pos> selectRocketTargets(Role weapon, List<RobotRole> targets) {
        int missiles = Math.max(1, weapon.level);
        List<Pos> candidates = new ArrayList<Pos>();
        for (RobotRole r : targets) {
            if (r.health > 0 && r.pos != null) {
                candidates.add(r.pos);
            }
        }
        List<Pos> result = new ArrayList<Pos>();
        while (result.size() < missiles) {
            Pos bestCenter = null;
            int bestCount = -1;
            for (Pos c : candidates) {
                if (overlapsSelected(c, result)) {
                    continue;
                }
                int count = countRobotsInArea(c, targets);
                if (count > bestCount) {
                    bestCount = count;
                    bestCenter = c;
                }
            }
            if (bestCenter == null) {
                break;
            }
            result.add(bestCenter);
        }
        return result;
    }

    /** 统计中心点周围 8 格（3×3）内存活机器人数 */
    private int countRobotsInArea(Pos center, List<RobotRole> targets) {
        int count = 0;
        for (RobotRole r : targets) {
            if (r.health <= 0) {
                continue;
            }
            if (MapUtil.chebyshev(center, r.pos) <= 1) {
                count++;
            }
        }
        return count;
    }

    /** 新落点与已选落点的 3×3 溅射区是否重叠（中心距离 <= 2 视为重叠） */
    private boolean overlapsSelected(Pos center, List<Pos> selected) {
        for (Pos s : selected) {
            if (MapUtil.chebyshev(center, s) <= 2) {
                return true;
            }
        }
        return false;
    }

    /** 按威胁值降序排列机器人 */
    private List<RobotRole> sortByThreatDesc(List<RobotRole> targets) {
        List<RobotRole> list = new ArrayList<RobotRole>(targets);
        Collections.sort(list, new Comparator<RobotRole>() {
            @Override
            public int compare(RobotRole a, RobotRole b) {
                return getRobotThreat(b) - getRobotThreat(a);
            }
        });
        return list;
    }

    /** 威胁值 = 机器人基础分 × 当前血量 */
    private int getRobotThreat(RobotRole r) {
        int base;
        if (Constants.ROBOT_BOSS.equals(r.roleType)) {
            base = Constants.ROBOT_SCORE_BOSS;
        } else if (Constants.ROBOT_LARGE.equals(r.roleType)) {
            base = Constants.ROBOT_SCORE_LARGE;
        } else if (Constants.ROBOT_MIDDLE.equals(r.roleType)) {
            base = Constants.ROBOT_SCORE_MIDDLE;
        } else {
            base = Constants.ROBOT_SCORE_SMALL;
        }
        return base * Math.max(0, r.health);
    }

    /** 两个目标相对中心点的方向夹角（度），归一化到 [0, 180] */
    private double angleDiffDeg(Pos from, Pos a, Pos b) {
        double angleA = Math.atan2(a.y - from.y, a.x - from.x);
        double angleB = Math.atan2(b.y - from.y, b.x - from.x);
        double diff = Math.abs(angleA - angleB);
        while (diff > Math.PI) {
            diff = 2 * Math.PI - diff;
        }
        return Math.toDegrees(diff);
    }

    private boolean isWeapon(String roleType) {
        return Constants.ROLE_GATLING.equals(roleType)
                || Constants.ROLE_RAILGUN.equals(roleType)
                || Constants.ROLE_ROCKET.equals(roleType);
    }

    private boolean isBuilding(String roleType) {
        return isWeapon(roleType)
                || Constants.ROLE_STATION.equals(roleType)
                || Constants.ROLE_WALL.equals(roleType);
    }
}
