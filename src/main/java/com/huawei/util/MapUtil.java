package com.huawei.util;

import java.util.ArrayList;
import java.util.List;

import com.huawei.model.Pos;
import com.huawei.model.RobotRole;
import com.huawei.model.Role;
import com.huawei.model.Zone;

/**
 * 地图工具：距离计算、寻路、碰撞处理、昼夜判定、中立区域查找。
 */
public final class MapUtil {

    private MapUtil() {
    }

    /** 切比雪夫距离 */
    public static int chebyshev(Pos a, Pos b) {
        return chebyshev(a, b.x, b.y);
    }

    /** 切比雪夫距离 */
    public static int chebyshev(Pos a, int bx, int by) {
        return Math.max(Math.abs(a.x - bx), Math.abs(a.y - by));
    }

    /** 是否相邻（距离 == 1） */
    public static boolean isAdjacent(Pos a, Pos b) {
        return chebyshev(a, b) == 1;
    }

    /** 是否相邻或重叠（距离 <= 1） */
    public static boolean isAdjacentOrSame(Pos a, Pos b) {
        return chebyshev(a, b) <= 1;
    }

    /** 8 方向直朝目标移动一步 */
    public static Pos stepToward(Pos from, Pos to) {
        int dx = Integer.compare(to.x, from.x);
        int dy = Integer.compare(to.y, from.y);
        return new Pos(from.x + dx, from.y + dy);
    }

    /**
     * 考虑障碍物的下一步移动。
     * BFS 八方向寻路。被占用的目标以其可达相邻格作为终点。
     * 返回 from 本身表示无法移动。
     */
    public static Pos nextStepToward(Pos from, Pos target, List<Pos> obstacles, int mapWidth, int mapHeight) {
        if (from == null || target == null || !isValidPos(from, mapWidth, mapHeight)
                || !isValidPos(target, mapWidth, mapHeight) || from.equals(target)) {
            return from;
        }
        boolean[][] blocked = new boolean[mapWidth][mapHeight];
        if (obstacles != null) for (Pos p : obstacles) {
            if (p != null && isValidPos(p, mapWidth, mapHeight)) blocked[p.x][p.y] = true;
        }
        boolean adjacentGoal = blocked[target.x][target.y];
        boolean[][] seen = new boolean[mapWidth][mapHeight];
        Pos[][] first = new Pos[mapWidth][mapHeight];
        java.util.ArrayDeque<Pos> queue = new java.util.ArrayDeque<Pos>();
        queue.add(from);
        seen[from.x][from.y] = true;
        while (!queue.isEmpty()) {
            Pos p = queue.remove();
            if (p.equals(target) || (adjacentGoal && isAdjacent(p, target))) {
                return p.equals(from) ? from : first[p.x][p.y];
            }
            // 对角线夹角允许通行，规则不采用传统网格的禁止切角限制。
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    Pos next = new Pos(p.x + dx, p.y + dy);
                    if (!isValidPos(next, mapWidth, mapHeight) || seen[next.x][next.y]
                            || blocked[next.x][next.y]) continue;
                    seen[next.x][next.y] = true;
                    first[next.x][next.y] = p.equals(from) ? next : first[p.x][p.y];
                    queue.add(next);
                }
            }
        }
        return from;
    }

    /** 坐标是否在地图范围内 */
    public static boolean isValidPos(Pos p, int mapWidth, int mapHeight) {
        return p.x >= 0 && p.x < mapWidth && p.y >= 0 && p.y < mapHeight;
    }

    /** 坐标是否被障碍物占据 */
    public static boolean isBlocked(Pos p, List<Pos> obstacles) {
        if (obstacles == null) {
            return false;
        }
        for (Pos o : obstacles) {
            if (o != null && o.equals(p)) {
                return true;
            }
        }
        return false;
    }

    /** 判定白天：roundNo % 130 < 70 */
    public static boolean isDayTime(int roundNo) {
        return roundNo % Constants.ROUNDS_PER_DAY < Constants.DAY_ROUNDS;
    }

    /** 获取游戏日索引：roundNo / 130 */
    public static int getDay(int roundNo) {
        return roundNo / Constants.ROUNDS_PER_DAY;
    }

    /** 按类型查找中立区域 */
    public static List<Zone> findZones(List<Zone> zones, String neutralType) {
        List<Zone> result = new ArrayList<Zone>();
        if (zones == null) {
            return result;
        }
        for (Zone z : zones) {
            if (z != null && neutralType.equals(z.neutralType)) {
                result.add(z);
            }
        }
        return result;
    }

    /** 查找距离最近的角色 */
    public static Role findNearestRole(List<Role> roles, Pos pos) {
        Role best = null;
        int bestDist = Integer.MAX_VALUE;
        if (roles == null) {
            return null;
        }
        for (Role r : roles) {
            if (r == null || r.pos == null) {
                continue;
            }
            int d = chebyshev(r.pos, pos);
            if (d < bestDist) {
                bestDist = d;
                best = r;
            }
        }
        return best;
    }

    /**
     * 收集所有占用坐标作为障碍物列表。
     * 建筑类（基地 2×2、围墙、武器）按实际占位收集；其余单位收集中心格。
     */
    public static List<Pos> collectObstacles(List<Role> ourRoles, List<Role> enemyRoles,
                                             List<RobotRole> robots, List<Zone> zones) {
        List<Pos> obs = new ArrayList<Pos>();
        if (ourRoles != null) {
            for (Role r : ourRoles) {
                addRoleTiles(obs, r);
            }
        }
        if (enemyRoles != null) {
            for (Role r : enemyRoles) {
                addRoleTiles(obs, r);
            }
        }
        if (robots != null) {
            for (RobotRole r : robots) {
                if (r != null && r.pos != null) {
                    obs.add(r.pos);
                }
            }
        }
        if (zones != null) {
            for (Zone z : zones) {
                if (z != null && z.pos != null) {
                    obs.add(z.pos);
                }
            }
        }
        return obs;
    }

    private static void addRoleTiles(List<Pos> obs, Role r) {
        if (r == null || r.pos == null) {
            return;
        }
        if (Constants.ROLE_STATION.equals(r.roleType)) {
            // 基地占 2×2，pos 为左上角
            obs.add(r.pos);
            obs.add(new Pos(r.pos.x + 1, r.pos.y));
            obs.add(new Pos(r.pos.x, r.pos.y + 1));
            obs.add(new Pos(r.pos.x + 1, r.pos.y + 1));
        } else {
            obs.add(r.pos);
        }
    }
}
