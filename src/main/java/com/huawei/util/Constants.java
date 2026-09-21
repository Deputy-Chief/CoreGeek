package com.huawei.util;

/**
 * 常量定义：集中管理所有魔法值。
 */
public final class Constants {

    private Constants() {
    }

    // ---------- 阵营 ----------
    public static final String TEAM_CHALLENGER = "challenger";
    public static final String TEAM_DEFENDER = "defender";

    // ---------- 角色类型 ----------
    public static final String ROLE_STATION = "station";
    public static final String ROLE_GATLING = "gatling";
    public static final String ROLE_RAILGUN = "railgun";
    public static final String ROLE_ROCKET = "rocket";
    public static final String ROLE_WALL = "wall";
    public static final String ROLE_PIONEER = "pioneer";
    public static final String ROLE_WORKER = "worker";

    // ---------- 机器人类型 ----------
    public static final String ROBOT_SMALL = "smallRobot";
    public static final String ROBOT_MIDDLE = "middleRobot";
    public static final String ROBOT_LARGE = "largeRobot";
    public static final String ROBOT_BOSS = "bossRobot";

    // ---------- 矿石 ----------
    public static final String ORE_STONE = "stone";
    public static final String ORE_IRON = "iron";
    public static final String ORE_COPPER = "copper";

    // ---------- 中立元素 ----------
    public static final String NEUTRAL_VENDOR = "vendor";
    public static final String NEUTRAL_WEAPON_SHOP = "weaponShop";

    // ---------- 动作码 ----------
    public static final String ACTION_MOVE = "move";
    public static final String ACTION_ATTACK = "attack";
    public static final String ACTION_SELL = "sell";
    public static final String ACTION_BUY = "buy";
    public static final String ACTION_BUILD = "build";
    public static final String ACTION_REMOVE = "remove";
    public static final String ACTION_ACCEPT_TASK = "acceptTask";
    public static final String ACTION_SUBMIT_ANSWER = "submitAnswer";
    public static final String ACTION_SUMMON_TREASURE = "summonTreasure";
    public static final String ACTION_USE = "use";
    public static final String ACTION_DROP = "drop";
    public static final String ACTION_COLLECT = "collect";

    // ---------- 升级券 ----------
    public static final String ITEM_WEAPON_UPGRADE_1 = "WeaponUpgradeVoucher1";
    public static final String ITEM_WEAPON_UPGRADE_2 = "WeaponUpgradeVoucher2";
    public static final String ITEM_WALL_UPGRADE_1 = "WallUpgradeVoucher1";
    public static final String ITEM_WALL_UPGRADE_2 = "WallUpgradeVoucher2";
    public static final String ITEM_STATION_UPGRADE_1 = "StationUpgradeVoucher1";
    public static final String ITEM_STATION_UPGRADE_2 = "StationUpgradeVoucher2";

    // ---------- 消耗品 ----------
    public static final String ITEM_WALL_FIXER = "WallFixer";
    public static final String ITEM_MEDICINE = "Medicine";
    public static final String ITEM_DIZZY_WEAPON = "DizzyWeapon";
    public static final String ITEM_BOMB = "Bomb";

    // ---------- 机器人召唤令 ----------
    public static final String ITEM_SMALL_ROBOT_SUMMON = "SmallRobotSummonOrder";
    public static final String ITEM_MIDDLE_ROBOT_SUMMON = "MiddleRobotSummonOrder";
    public static final String ITEM_LARGE_ROBOT_SUMMON = "LargeRobotSummonOrder";
    public static final String ITEM_BOSS_ROBOT_SUMMON = "BossRobotSummonOrder";

    // ---------- 任务用品 ----------
    public static final String ITEM_ANCIENT_TABLET = "AcientTablet";
    public static final String ITEM_STAR_SAND = "StarSand";
    public static final String ITEM_FLAME_BREATH = "FlameBreath";
    public static final String ITEM_FROST_POTION = "FrostPotion";
    public static final String ITEM_THORN_AMULET = "ThornAmulet";
    public static final String ITEM_IRON_WHISTLE = "IronWhistle";

    // ---------- 时间参数 ----------
    /** 一天总回合数 */
    public static final int ROUNDS_PER_DAY = 130;
    /** 白天回合数 */
    public static final int DAY_ROUNDS = 70;
    /** 夜晚回合数 */
    public static final int NIGHT_ROUNDS = 60;
    /** 单场最大回合数（10 天） */
    public static final int MAX_ROUNDS = 1300;

    // ---------- 角色属性 ----------
    public static final int PIONEER_HP = 200;
    public static final int WORKER_HP = 220;
    public static final int PIONEER_BACKPACK = 40;
    public static final int WORKER_BACKPACK = 100;
    /** 武器工事全局上限 */
    public static final int MAX_WEAPONS = 3;
    /** 角色阵亡复活等待回合数 */
    public static final int RESPAWN_ROUNDS = 20;

    // ---------- 机器人基础积分 ----------
    public static final int ROBOT_SCORE_SMALL = 1;
    public static final int ROBOT_SCORE_MIDDLE = 2;
    public static final int ROBOT_SCORE_LARGE = 4;
    public static final int ROBOT_SCORE_BOSS = 10;

    // ---------- 策略参数 ----------
    /** 白天最后 10 回合（回合号%130 >= 60）起，角色回武器旁准备夜晚防守 */
    public static final int NIGHT_PREP_ROUND = 60;
    /** 围墙满血回退值。接口若提供 maxHealth，则优先使用实时值。 */
    public static final int[] WALL_MAX_HEALTH = {0, 500, 1000, 1500};
}
