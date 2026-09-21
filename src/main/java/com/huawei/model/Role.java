package com.huawei.model;

import java.util.List;

/**
 * 单位通用属性（角色与建筑共用）。
 */
public class Role {

    public int id;
    public Pos pos;
    public String roleType;
    public int health;
    /** 可选扩展字段；老接口未提供时为0，围墙使用集中配置的回退值。 */
    public int maxHealth;
    public int attackPower;
    public int attackRange;
    public int backPackCapability;
    public List<String> backpack;
    public int level;
    public int cooldown;
}
