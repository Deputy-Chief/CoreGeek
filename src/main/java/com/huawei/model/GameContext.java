package com.huawei.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import com.huawei.util.Constants;
import com.huawei.util.MapUtil;

/**
 * 跨回合状态缓存，由 DecisionEngine 持有，服务生命周期内共享。
 */
public class GameContext {

    /** 自进化任务状态机 */
    public enum TaskState {
        IDLE, MOVING_TO_TASK_POINT, TASK_ACCEPTED, AWAIT_LLM, AWAIT_CMD, READY_TO_SUBMIT, SUBMITTED
    }

    /** 阵营标识，首次请求时初始化 */
    public String teamType = "";
    /** 基地左上角坐标，每回合更新 */
    public Pos stationPos = new Pos();
    /** 本游戏日 LLM 调用次数，换日重置 */
    public int llmCallCountToday = 0;
    /** 当前游戏日索引 */
    public int currentDay = -1;
    /** 宝藏是否已开启 */
    public boolean treasureOpened = false;
    /** 已建造武器工事数量 */
    public int weaponCount = 0;
    /** 自进化任务状态机 */
    public TaskState taskState = TaskState.IDLE;
    /** 当前任务点索引 */
    public int currentTaskPointIdx = 0;
    /** 领取任务时的回合数 */
    public int taskAcceptRound = 0;
    /** 当前任务超时回合数 */
    public int taskTimeoutRounds = 0;
    /** 任务当前最佳答案 */
    public String bestAnswer = "";
    /** 沙盒待执行命令队列 */
    public LinkedList<String> pendingCmds = new LinkedList<String>();
    /** 是否正在等待沙盒结果 */
    public boolean awaitingCmdResult = false;
    /** 上次提交的沙盒命令 */
    public String lastSubmittedCmd = "";
    /** 已知矿点（按类型分组） */
    public Map<String, Set<Pos>> knownMines = new HashMap<String, Set<Pos>>();
    /** 工人 → 目标矿点 */
    public Map<Integer, Pos> workerTargetMine = new HashMap<Integer, Pos>();
    /** 工人状态（采集/运输） */
    public Map<Integer, Boolean> workerMining = new HashMap<Integer, Boolean>();

    public int lastRound = -1;
    public String teamId;
    public int availableGold;
    public int summonCountToday;
    public final Set<Pos> reservedTiles = new HashSet<Pos>();
    public final Set<Integer> reservedBuildings = new HashSet<Integer>();
    public final Set<String> plannedWeapons = new HashSet<String>();
    public final Map<Integer, Integer> weaponAssignments = new HashMap<Integer, Integer>();
    public final Set<Integer> wallBuilders = new HashSet<Integer>();
    public final Set<String> newsSeen = new HashSet<String>();
    public final StringBuilder folkHistory = new StringBuilder();
    public final Map<String, Integer> holdUntilDay = new HashMap<String, Integer>();
    public final Map<String, Integer> unavailableUntilDay = new HashMap<String, Integer>();
    public final Map<String, Integer> previousPrices = new HashMap<String, Integer>();
    public final Set<String> fallingOres = new HashSet<String>();
    public int lastTaskPointIdx = -1;
    public int taskRequestRound;
    public String taskDescription = "";
    public String taskType = "";
    public String taskFeedback = "";
    public String taskSolutionScript = "";
    /** 只将判题确认成功的脚本收入 SOP；同类新题交给 LLM 参数化。 */
    public final Map<String, String> taskScripts = new HashMap<String, String>();
    public final Map<String, String> exactTaskScripts = new HashMap<String, String>();
    public boolean reusedScript;
    public boolean treasureAwaitingLlm;
    public int treasureLlmRound;
    public int treasureInferenceDay = -1;
    public int treasureRetryRound;
    public int treasureAttemptRound = -1;
    public Pos treasurePos;
    public int treasureDay;
    public int treasureRoundInDay;
    public final java.util.List<String> treasureItems = new java.util.ArrayList<String>();
    public String treasureFeedback = "";

    /** 检测换日，重置当日 LLM 计数 */
    public void refreshDay(int roundNo) {
        int day = MapUtil.getDay(roundNo);
        if (day != currentDay) {
            currentDay = day;
            llmCallCountToday = 0;
            summonCountToday = 0;
            fallingOres.clear();
        }
    }

    /** 将 zones 中的矿点记录到 knownMines（矿点会消失并随机刷新，需跨回合记忆） */
    public void recordMines(MapInfo mapInfo) {
        knownMines.clear(); // 全图矿点来自当前快照，失效矿点不能继续导航。
        if (mapInfo == null || mapInfo.zones == null) {
            return;
        }
        for (Zone z : mapInfo.zones) {
            if (z == null || z.pos == null || z.neutralType == null) {
                continue;
            }
            if (Constants.ORE_STONE.equals(z.neutralType)
                    || Constants.ORE_IRON.equals(z.neutralType)
                    || Constants.ORE_COPPER.equals(z.neutralType)) {
                Set<Pos> set = knownMines.get(z.neutralType);
                if (set == null) {
                    set = new HashSet<Pos>();
                    knownMines.put(z.neutralType, set);
                }
                set.add(z.pos);
            }
        }
    }
}
