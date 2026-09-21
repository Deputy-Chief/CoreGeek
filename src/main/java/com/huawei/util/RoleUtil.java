package com.huawei.util;

import java.util.ArrayList;
import java.util.List;

import com.huawei.model.Pos;
import com.huawei.model.Role;

/**
 * 角色工具：查找、背包操作、存活判定、建筑占位。
 */
public final class RoleUtil {

    private RoleUtil() {
    }

    /** 按角色类型筛选 */
    public static List<Role> filterByType(List<Role> roles, String roleType) {
        List<Role> result = new ArrayList<Role>();
        if (roles == null) {
            return result;
        }
        for (Role r : roles) {
            if (r != null && roleType.equals(r.roleType)) {
                result.add(r);
            }
        }
        return result;
    }

    /** 查找第一个指定类型角色 */
    public static Role findFirst(List<Role> roles, String roleType) {
        if (roles == null) {
            return null;
        }
        for (Role r : roles) {
            if (r != null && roleType.equals(r.roleType)) {
                return r;
            }
        }
        return null;
    }

    /** 按 ID 查找角色 */
    public static Role findById(List<Role> roles, int id) {
        if (roles == null) {
            return null;
        }
        for (Role r : roles) {
            if (r != null && r.id == id) {
                return r;
            }
        }
        return null;
    }

    /** 统计背包中指定物品数量 */
    public static int countInBackpack(Role role, String itemName) {
        if (role == null || role.backpack == null || itemName == null) {
            return 0;
        }
        int count = 0;
        for (String s : role.backpack) {
            if (itemName.equals(s)) {
                count++;
            }
        }
        return count;
    }

    /** 判断背包是否有物品 */
    public static boolean hasInBackpack(Role role, String itemName) {
        return countInBackpack(role, itemName) > 0;
    }

    /** 背包是否已满 */
    public static boolean isBackpackFull(Role role) {
        if (role == null) {
            return true;
        }
        if (role.backpack == null) {
            return false;
        }
        return role.backpack.size() >= role.backPackCapability;
    }

    /** 背包剩余空间 */
    public static int backpackRemaining(Role role) {
        if (role == null) {
            return 0;
        }
        if (role.backpack == null) {
            return role.backPackCapability;
        }
        return Math.max(0, role.backPackCapability - role.backpack.size());
    }

    /** 角色是否存活（在列表中） */
    public static boolean isAlive(List<Role> roles, int roleId) {
        return findById(roles, roleId) != null;
    }

    /** 获取基地 4 格坐标（2×2，pos 为左上角） */
    public static List<Pos> getStationTiles(Pos stationPos) {
        List<Pos> tiles = new ArrayList<Pos>();
        if (stationPos == null) {
            return tiles;
        }
        tiles.add(stationPos);
        tiles.add(new Pos(stationPos.x + 1, stationPos.y));
        tiles.add(new Pos(stationPos.x, stationPos.y + 1));
        tiles.add(new Pos(stationPos.x + 1, stationPos.y + 1));
        return tiles;
    }

    /** 角色是否在目标相邻格内（距离 <= 1） */
    public static boolean isAdjacentTo(Role role, Pos target) {
        if (role == null || role.pos == null || target == null) {
            return false;
        }
        return MapUtil.chebyshev(role.pos, target) <= 1;
    }
}
