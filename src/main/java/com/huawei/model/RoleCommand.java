package com.huawei.model;

import java.util.Collections;
import java.util.List;

import com.huawei.util.Constants;

/**
 * 单角色指令，含全部动作的工厂方法。
 */
public class RoleCommand {

    public String action;
    /** 操控武器攻击的角色 ID（仅 attack 使用） */
    public String controllerId;
    public List<Pos> targetPos;
    public String name;
    public Integer num;
    public String taskAnswer;
    public List<String> item;

    public RoleCommand() {
    }

    public RoleCommand(String action) {
        this.action = action;
    }

    /** move + targetPos[1] */
    public static RoleCommand move(Pos target) {
        RoleCommand cmd = new RoleCommand(Constants.ACTION_MOVE);
        cmd.targetPos = Collections.singletonList(target);
        return cmd;
    }

    /** attack + controllerId + targetPos[N]（加特林/火箭可多目标） */
    public static RoleCommand attack(String controllerId, List<Pos> targets) {
        RoleCommand cmd = new RoleCommand(Constants.ACTION_ATTACK);
        cmd.controllerId = controllerId;
        cmd.targetPos = targets;
        return cmd;
    }

    /** collect + targetPos[1] */
    public static RoleCommand collect(Pos target) {
        RoleCommand cmd = new RoleCommand(Constants.ACTION_COLLECT);
        cmd.targetPos = Collections.singletonList(target);
        return cmd;
    }

    /** build + name + targetPos[1] */
    public static RoleCommand build(String name, Pos target) {
        RoleCommand cmd = new RoleCommand(Constants.ACTION_BUILD);
        cmd.name = name;
        cmd.targetPos = Collections.singletonList(target);
        return cmd;
    }

    /** remove + targetPos[1] */
    public static RoleCommand remove(Pos target) {
        RoleCommand cmd = new RoleCommand(Constants.ACTION_REMOVE);
        cmd.targetPos = Collections.singletonList(target);
        return cmd;
    }

    /** sell + name + num */
    public static RoleCommand sell(String name, int num) {
        RoleCommand cmd = new RoleCommand(Constants.ACTION_SELL);
        cmd.name = name;
        cmd.num = num;
        return cmd;
    }

    /** buy + name + num */
    public static RoleCommand buy(String name, int num) {
        RoleCommand cmd = new RoleCommand(Constants.ACTION_BUY);
        cmd.name = name;
        cmd.num = num;
        return cmd;
    }

    /** use + name（无坐标） */
    public static RoleCommand use(String name) {
        RoleCommand cmd = new RoleCommand(Constants.ACTION_USE);
        cmd.name = name;
        return cmd;
    }

    /** use + name + targetPos[1]（有坐标） */
    public static RoleCommand use(String name, Pos target) {
        RoleCommand cmd = new RoleCommand(Constants.ACTION_USE);
        cmd.name = name;
        cmd.targetPos = Collections.singletonList(target);
        return cmd;
    }

    /** drop + name */
    public static RoleCommand drop(String name) {
        RoleCommand cmd = new RoleCommand(Constants.ACTION_DROP);
        cmd.name = name;
        return cmd;
    }

    /** acceptTask */
    public static RoleCommand acceptTask() {
        return new RoleCommand(Constants.ACTION_ACCEPT_TASK);
    }

    /** submitAnswer + taskAnswer */
    public static RoleCommand submitAnswer(String answer) {
        RoleCommand cmd = new RoleCommand(Constants.ACTION_SUBMIT_ANSWER);
        cmd.taskAnswer = answer;
        return cmd;
    }

    /** summonTreasure + targetPos[1] + item[N] */
    public static RoleCommand summonTreasure(Pos target, List<String> items) {
        RoleCommand cmd = new RoleCommand(Constants.ACTION_SUMMON_TREASURE);
        cmd.targetPos = Collections.singletonList(target);
        cmd.item = items;
        return cmd;
    }
}
