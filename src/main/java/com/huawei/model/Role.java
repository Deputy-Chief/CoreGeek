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
    public int attackPower;
    public int attackRange;
    public int backPackCapability;
    public List<String> backpack;
    public int level;
    public int cooldown;
}
