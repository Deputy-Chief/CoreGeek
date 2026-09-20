package com.huawei.model;

import java.util.List;

/**
 * 我方队伍全部信息。
 */
public class TeamOur {

    public String type;
    public String teamId;
    public String teamName;
    public int goldNum;
    public int totalScore;
    public List<PlayerTask> playerTasks;
    public List<Role> roles;
}
