package com.huawei.model;

import java.util.HashMap;
import java.util.Map;

/**
 * 返回给判题器的顶层 Response。
 */
public class GameResponse {

    /** 全角色指令集合，key = 角色 ID（武器攻击时 key 为武器 ID） */
    public Map<Integer, RoleCommand> roleCommandMap = new HashMap<Integer, RoleCommand>();
    /** 提交给 LLM 的 prompt（每个游戏日限 3 次，任务期间不限） */
    public String prompt = "";
    /** 沙盒环境中执行的命令（仅任务期间可用，15 秒超时） */
    public String executeCmd = "";
}
