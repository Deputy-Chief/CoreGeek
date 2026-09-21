package com.huawei.strategy;

import java.util.*;
import com.google.gson.*;
import com.huawei.llm.LlmClient;
import com.huawei.model.*;
import com.huawei.util.*;

/** 任务优先，累积传闻推理真实祭坛；夜间异步求解与防御并行。 */
public class PioneerStrategy {
    private final TaskSolver solver = new TaskSolver();

    public void execute(Role p, GameResponse res, GameRequest req, GameContext ctx, Set<Integer> used, boolean day) {
        if (p == null) return;
        receiveTreasure(req, ctx);
        boolean active = req.phaseTask != null && !req.phaseTask.trim().isEmpty();
        boolean defending = !day || StrategySupport.prepareNight(p, req, ctx);
        RoleCommand emergency = used.contains(p.id) ? null : StrategySupport.emergency(p, req, ctx, !day);
        // 宝藏推理和任务解题共享一个异步响应通道，用明确状态防止串线。
        if (!active && (ctx.taskState == GameContext.TaskState.IDLE
                || ctx.taskState == GameContext.TaskState.MOVING_TO_TASK_POINT
                || ctx.taskState == GameContext.TaskState.SUBMITTED)) inferTreasure(res, req, ctx);
        boolean treasureTrip = !active && ctx.currentDay >= 5 && !ctx.treasureOpened && ctx.treasurePos != null
                && req.roundNo >= ctx.treasureRetryRound && ctx.currentDay + 1 >= ctx.treasureDay
                && canSupplyTreasure(p, req, ctx);
        RoleCommand task = solver.solve(p, res, req, ctx, !used.contains(p.id) && emergency == null
                && !defending && !treasureTrip);
        if (used.contains(p.id)) return;
        RoleCommand cmd = emergency;
        if (cmd == null && defending) cmd = StrategySupport.defend(p, req, ctx);
        if (cmd == null && !defending) {
            cmd = task;
            if (cmd == null && treasureTrip) cmd = treasure(p, req, ctx);
            if (cmd == null && p.health < Constants.PIONEER_HP * 3 / 10)
                cmd = StrategySupport.buy(p, Constants.ITEM_MEDICINE, req, ctx, 0);
            // 任务点均冷却时利用空档备齐六种用品，每种一件。
            if (cmd == null && !active && ctx.currentDay >= 2 && !ctx.treasureOpened
                    && ctx.taskState == GameContext.TaskState.IDLE && EconomyStrategy.defenseReady(req)) {
                for (String item : StrategySupport.TASK_ITEMS) if (!RoleUtil.hasInBackpack(p, item)) {
                    cmd = StrategySupport.buy(p, item, req, ctx, 150);
                    if (cmd != null) break;
                }
            }
        }
        StrategySupport.emit(p, cmd, res, ctx, used);
    }

    private void inferTreasure(GameResponse res, GameRequest req, GameContext ctx) {
        if (ctx.treasureOpened || ctx.treasureAwaitingLlm || ctx.currentDay < 2 || ctx.folkHistory.length() == 0
                || ctx.treasureInferenceDay == ctx.currentDay || !LlmClient.canCallLlm(ctx, false)) return;
        WorldNews history = new WorldNews();
        history.folkLegends = ctx.folkHistory.toString();
        res.prompt = LlmClient.buildTreasurePrompt(history, ctx.treasureFeedback);
        ctx.llmCallCountToday++;
        ctx.treasureAwaitingLlm = true;
        ctx.treasureLlmRound = req.roundNo;
        ctx.treasureInferenceDay = ctx.currentDay;
    }

    private void receiveTreasure(GameRequest req, GameContext ctx) {
        if (ctx.treasureAttemptRound >= 0 && req.roundNo > ctx.treasureAttemptRound) {
            int code = req.lastSummonTreasureResult;
            if (code == 1 || code == 4) ctx.treasureOpened = true;
            else if (code == 2) ctx.treasureRetryRound = req.roundNo + 10;
            else if (code == 3) {
                ctx.treasureFeedback = "祭坛" + ctx.treasurePos + "献祭" + ctx.treasureItems + "返回物品错误，请重新推断组合。";
                ctx.treasurePos = null;
                ctx.treasureItems.clear();
                ctx.treasureInferenceDay = -1;
            } else ctx.treasureRetryRound = req.roundNo + 3;
            ctx.treasureAttemptRound = -1;
        }
        if (!ctx.treasureAwaitingLlm || req.roundNo <= ctx.treasureLlmRound) return;
        if (req.llmResp == null || req.llmResp.trim().isEmpty()) {
            if (req.roundNo - ctx.treasureLlmRound >= 3) {
                ctx.treasureAwaitingLlm = false;
                ctx.treasureInferenceDay = -1;
            }
            return;
        }
        ctx.treasureAwaitingLlm = false;
        JsonObject json = LlmClient.parseObject(req.llmResp);
        if (json == null) return;
        try {
            JsonObject position = json.getAsJsonObject("position");
            if (position == null) return;
            Pos pos = new Pos(integer(position, "x"), integer(position, "y"));
            int day = integer(json, "day");
            int time = json.has("roundInDay") ? integer(json, "roundInDay") : 0;
            if (!MapUtil.isValidPos(pos, req.mapInfo.width, req.mapInfo.height) || day < 1 || day > 10
                    || time < 0 || time >= Constants.ROUNDS_PER_DAY) return;
            List<String> items = new ArrayList<String>();
            JsonArray array = json.getAsJsonArray("items");
            if (array == null || array.size() == 0 || array.size() > Constants.PIONEER_BACKPACK) return;
            for (JsonElement item : array) {
                String value = item.getAsString();
                if (!Arrays.asList(StrategySupport.TASK_ITEMS).contains(value)) return;
                items.add(value);
            }
            ctx.treasurePos = pos;
            ctx.treasureDay = day;
            ctx.treasureRoundInDay = time;
            ctx.treasureItems.clear(); ctx.treasureItems.addAll(items);
        } catch (RuntimeException ignored) { /* 不完整推理不能生成游戏指令。 */ }
    }

    private int integer(JsonObject obj, String key) {
        return obj.get(key).getAsBigDecimal().intValueExact();
    }

    private RoleCommand treasure(Role p, GameRequest req, GameContext ctx) {
        if (ctx.treasurePos == null || ctx.treasureItems.isEmpty()) return null;
        Map<String, Integer> needed = new LinkedHashMap<String, Integer>();
        for (String item : ctx.treasureItems) needed.put(item, needed.getOrDefault(item, 0) + 1);
        for (Map.Entry<String, Integer> item : needed.entrySet()) {
            if (RoleUtil.countInBackpack(p, item.getKey()) < item.getValue())
                return StrategySupport.buy(p, item.getKey(), req, ctx, EconomyStrategy.defenseReady(req) ? 0 : 150);
        }
        if (!MapUtil.isAdjacent(p.pos, ctx.treasurePos)) return StrategySupport.move(p, ctx.treasurePos, req, ctx);
        int due = (ctx.treasureDay - 1) * Constants.ROUNDS_PER_DAY + ctx.treasureRoundInDay;
        if (req.roundNo < due) return null;
        ctx.treasureAttemptRound = req.roundNo;
        return RoleCommand.summonTreasure(ctx.treasurePos, new ArrayList<String>(ctx.treasureItems));
    }

    private boolean canSupplyTreasure(Role p, GameRequest req, GameContext ctx) {
        List<String> bag = new ArrayList<String>(p.backpack == null ? Collections.<String>emptyList() : p.backpack);
        int cost = 0, slots = 0;
        for (String item : ctx.treasureItems) if (!bag.remove(item)) {
            int price = StrategySupport.price(req.weaponShopList, item);
            if (price < 0) return false;
            cost += price; slots++;
        }
        return slots <= RoleUtil.backpackRemaining(p) && (slots == 0 ||
                (StrategySupport.nearestZone(p, req, Constants.NEUTRAL_WEAPON_SHOP) != null
                && ctx.availableGold >= cost + (EconomyStrategy.defenseReady(req) ? 0 : 150)));
    }
}
