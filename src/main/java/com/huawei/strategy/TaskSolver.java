package com.huawei.strategy;

import java.util.List;
import com.google.gson.JsonObject;
import com.huawei.llm.LlmClient;
import com.huawei.model.*;
import com.huawei.model.GameContext.TaskState;
import com.huawei.sandbox.SandboxExecutor;
import com.huawei.util.*;

/** 异步解题状态机：只有判题确认接取/提交后才改变任务归属和缓存。 */
public class TaskSolver {
    public RoleCommand solve(Role pioneer, GameResponse res, GameRequest req, GameContext ctx,
                             List<Pos> obstacles, int width, int height) {
        return solve(pioneer, res, req, ctx, true);
    }

    public RoleCommand solve(Role pioneer, GameResponse res, GameRequest req, GameContext ctx, boolean actions) {
        boolean active = req.phaseTask != null && !req.phaseTask.trim().isEmpty();
        if (req.errors != null) for (ErrorInfo e : req.errors) if (e != null && (e.errorCode == 1 || e.errorCode == 2)) {
            ctx.exactTaskScripts.remove(ctx.taskDescription);
            reset(ctx);
            return null;
        }
        if (ctx.taskState == TaskState.SUBMITTED) {
            Boolean ok = req.lastRoundRoleActionResults == null ? null
                    : req.lastRoundRoleActionResults.get(String.valueOf(pioneer.id));
            if (!active) {
                if (Boolean.TRUE.equals(ok) && !ctx.taskSolutionScript.isEmpty()) {
                    ctx.taskScripts.put(ctx.taskType, ctx.taskSolutionScript);
                    ctx.exactTaskScripts.put(ctx.taskDescription, ctx.taskSolutionScript);
                }
                reset(ctx);
            } else if (Boolean.FALSE.equals(ok) || req.roundNo > ctx.taskRequestRound + 2) {
                ctx.taskState = TaskState.READY_TO_SUBMIT;
            } else return null;
        }
        if (ctx.taskTimeoutRounds > 0 && req.roundNo - ctx.taskAcceptRound > ctx.taskTimeoutRounds) {
            reset(ctx); return null;
        }
        if (!active && ctx.taskState != TaskState.IDLE && ctx.taskState != TaskState.MOVING_TO_TASK_POINT
                && ctx.taskState != TaskState.TASK_ACCEPTED) reset(ctx);
        if (active && (ctx.taskState == TaskState.IDLE || ctx.taskState == TaskState.MOVING_TO_TASK_POINT)) {
            ctx.taskState = TaskState.TASK_ACCEPTED;
            ctx.taskAcceptRound = req.roundNo;
        }
        if (ctx.taskState == TaskState.TASK_ACCEPTED) {
            if (!active) {
                if (req.roundNo > ctx.taskAcceptRound) reset(ctx);
                return null;
            }
            if (ctx.treasureAwaitingLlm) return null; // LLM响应属于宝藏请求，不能当任务答案。
            ctx.taskDescription = req.phaseTask;
            String cached = ctx.exactTaskScripts.get(ctx.taskDescription);
            if (cached != null && !ctx.reusedScript) {
                ctx.reusedScript = true;
                issueCommand(cached, res, req, ctx);
            } else requestLlm(res, req, ctx);
            return null;
        }
        if (ctx.taskState == TaskState.AWAIT_LLM) {
            if (req.llmResp == null || req.llmResp.trim().isEmpty()) {
                if (req.roundNo - ctx.taskRequestRound >= 3) requestLlm(res, req, ctx);
                return null;
            }
            String cmd = LlmClient.extractCommand(req.llmResp);
            if (!cmd.isEmpty()) { issueCommand(cmd, res, req, ctx); return null; }
            JsonObject json = LlmClient.parseObject(req.llmResp);
            String answer = LlmClient.extractAnswer(req.llmResp);
            if ((json == null && (req.llmResp.trim().startsWith("{") || req.llmResp.contains("```"))) || answer.isEmpty()) {
                ctx.taskFeedback = "响应格式无效，请返回约定的JSON。";
                requestLlm(res, req, ctx); return null;
            }
            ctx.bestAnswer = answer;
            ctx.taskState = TaskState.READY_TO_SUBMIT;
        }
        if (ctx.taskState == TaskState.AWAIT_CMD) {
            if (req.lastCmdResult == null || req.lastCmdResult.isEmpty()) {
                if (req.roundNo - ctx.taskRequestRound >= 3) {
                    ctx.taskFeedback = "沙盒未返回结果，请重试或换命令。";
                    ctx.awaitingCmdResult = false;
                    requestLlm(res, req, ctx);
                }
                return null;
            }
            SandboxExecutor.CmdResult result = SandboxExecutor.parseResult(req.lastCmdResult);
            ctx.awaitingCmdResult = false;
            JsonObject json = LlmClient.parseObject(result.output);
            String answer = json == null ? "" : LlmClient.string(json, "answer");
            if (result.isSuccess() && !answer.isEmpty()) {
                ctx.taskSolutionScript = ctx.lastSubmittedCmd;
                ctx.bestAnswer = answer; ctx.taskState = TaskState.READY_TO_SUBMIT;
            } else {
                ctx.taskFeedback = "命令：\n" + ctx.lastSubmittedCmd + "\n结果：\n" + req.lastCmdResult;
                if (!result.isSuccess()) ctx.exactTaskScripts.remove(ctx.taskDescription);
                requestLlm(res, req, ctx); return null;
            }
        }
        if (ctx.taskState == TaskState.READY_TO_SUBMIT) {
            if (!actions || !active || ctx.bestAnswer.trim().isEmpty()) return null;
            ctx.taskState = TaskState.SUBMITTED;
            ctx.taskRequestRound = req.roundNo;
            return RoleCommand.submitAnswer(ctx.bestAnswer.trim());
        }
        if (!actions || active || ctx.treasureAwaitingLlm) return null;
        return nextTask(pioneer, req, ctx);
    }

    private void requestLlm(GameResponse res, GameRequest req, GameContext ctx) {
        if (req.phaseTask == null || req.phaseTask.trim().isEmpty()) return;
        String sop = ctx.taskScripts.get(ctx.taskType);
        res.prompt = LlmClient.buildTaskPrompt(req.phaseTask, ctx.taskFeedback
                + (sop == null ? "" : "\n同类已验证脚本（须适配当前题参数）：\n" + sop));
        ctx.taskRequestRound = req.roundNo;
        ctx.taskState = TaskState.AWAIT_LLM;
    }

    private void issueCommand(String cmd, GameResponse res, GameRequest req, GameContext ctx) {
        res.executeCmd = cmd;
        ctx.lastSubmittedCmd = cmd;
        ctx.awaitingCmdResult = true;
        ctx.taskRequestRound = req.roundNo;
        ctx.taskState = TaskState.AWAIT_CMD;
    }

    private RoleCommand nextTask(Role p, GameRequest req, GameContext ctx) {
        if (req.teamOur.playerTasks == null) return null;
        int chosen = -1;
        double best = -Double.MAX_VALUE;
        for (int i = 0; i < req.teamOur.playerTasks.size(); i++) {
            PlayerTask t = req.teamOur.playerTasks.get(i);
            if (t == null || t.taskPosition == null || !t.isValid || t.coldDownRounds > 0) continue;
            double score = (i != ctx.lastTaskPointIdx ? 1000 : 0) - MapUtil.chebyshev(p.pos, t.taskPosition);
            if (ctx.lastTaskPointIdx < 0 && i == 0) score += 1000;
            if (score > best) { best = score; chosen = i; }
        }
        if (chosen < 0) { ctx.taskState = TaskState.IDLE; return null; }
        PlayerTask task = req.teamOur.playerTasks.get(chosen);
        ctx.currentTaskPointIdx = chosen;
        if (MapUtil.isAdjacent(p.pos, task.taskPosition)) {
            ctx.taskState = TaskState.TASK_ACCEPTED;
            ctx.taskAcceptRound = req.roundNo;
            ctx.taskTimeoutRounds = task.timeoutRounds;
            ctx.taskType = task.taskType == null ? "" : task.taskType;
            ctx.lastTaskPointIdx = chosen;
            return RoleCommand.acceptTask();
        }
        ctx.taskState = TaskState.MOVING_TO_TASK_POINT;
        return StrategySupport.move(p, task.taskPosition, req, ctx);
    }

    private void reset(GameContext ctx) {
        ctx.taskState = TaskState.IDLE;
        ctx.bestAnswer = "";
        ctx.awaitingCmdResult = false;
        ctx.pendingCmds.clear();
        ctx.lastSubmittedCmd = "";
        ctx.taskDescription = "";
        ctx.taskFeedback = "";
        ctx.taskSolutionScript = "";
        ctx.taskTimeoutRounds = 0;
        ctx.reusedScript = false;
    }
}
