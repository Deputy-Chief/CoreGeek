package com.huawei.model;

import java.util.List;
import java.util.Map;

/**
 * 判题器每回合下发的顶层 Request。
 */
public class GameRequest {

    public int roundNo;
    public MapInfo mapInfo;
    public TeamOur teamOur;
    public TeamEnemy teamEnemy;
    public Robot robot;
    /** 当前已领取任务的原文描述，空字符串表示无任务 */
    public String phaseTask;
    /** 上回合各角色动作是否合法 */
    public Map<String, Boolean> lastRoundRoleActionResults;
    /** 上回合召唤宝藏结果码：0=未探测 1=成功 2=无宝藏/未到时间 3=献祭物品错误 4=宝藏已空 */
    public int lastSummonTreasureResult;
    /** 上回合 LLM 返回的响应内容 */
    public String llmResp;
    public WorldNews worldNews;
    /** 上回合 executeCmd 执行结果，格式 [exitCode:N]\n<输出> */
    public String lastCmdResult;
    /** 小贩收购矿石清单 */
    public List<ShopItem> vendorShopList;
    /** 武器商店出售物品清单 */
    public List<ShopItem> weaponShopList;
    public List<ErrorInfo> errors;
}
