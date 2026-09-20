package com.huawei.strategy;

import java.util.List;

import com.huawei.llm.LlmClient;
import com.huawei.model.ErrorInfo;
import com.huawei.model.GameContext;
import com.huawei.model.GameContext.TaskState;
import com.huawei.model.GameRequest;
import com.huawei.model.GameResponse;
import com.huawei.model.PlayerTask;
import com.huawei.model.Pos;
import com.huawei.model.Role;
import com.huawei.model.RoleCommand;
import com.huawei.sandbox.SandboxExecutor;
import com.huawei.util.MapUtil;

/**
 * 自进化任务解题状态机。
 *
 * <pre>
 * IDLE → MOVING_TO_TASK_POINT → TASK_ACCEPTED → AWAIT_LLM → AWAIT_CMD → READY_TO_SUBMIT → SUBMITTED → IDLE
 * 错误（errorCode=1 任务超时 / 2 答案错误）时重置为 IDLE。
 * </pre>
 */
public class TaskSolver {

    public RoleCommand solve(Role pioneer, GameResponse response, GameRequest request, GameContext ctx,
                             List<Pos> obstacles, int mapWidth, int mapHeight) {
        if (hasTaskError(request, ctx)) {
            return null;
        }
        switch (ctx.taskState) {
            case IDLE:
                return startNewTask(pioneer, response, request, ctx, obstacles, mapWidth, mapHeight);
            case MOVING_TO_TASK_POINT:
                return continueMovingToTaskPoint(pioneer, request, ctx, obstacles, mapWidth, mapHeight);
            case TASK_ACCEPTED:
                return requestLlm(response, request, ctx);
            case AWAIT_LLM:
                return checkLlmResponse(response, request, ctx);
            case AWAIT_CMD:
                return checkCmdResult(request, ctx);
            case READY_TO_SUBMIT:
                return submitBestAnswer(ctx);
            case SUBMITTED:
                ctx.taskState = TaskState.IDLE;
                return null;
            default:
                return null;
        }
    }

    /** IDLE：找可用任务点 → 到达则 acceptTask，否则移动 */
    private RoleCommand startNewTask(Role pioneer, GameResponse response, GameRequest request, GameContext ctx,
                                     List<Pos> obstacles, int mapWidth, int mapHeight) {
        PlayerTask task = findAvailableTask(request);
        if (task == null) {
            return null;
        }
        ctx.currentTaskPointIdx = indexOfTask(request, task);
        Pos tp = task.taskPosition;
        if (MapUtil.isAdjacentOrSame(pioneer.pos, tp)) {
            ctx.taskState = TaskState.TASK_ACCEPTED;
            return RoleCommand.acceptTask();
        }
        ctx.taskState = TaskState.MOVING_TO_TASK_POINT;
        return moveStep(pioneer, tp, obstacles, mapWidth, mapHeight);
    }

    /** MOVING_TO_TASK_POINT：继续移动，到达则 acceptTask */
    private RoleCommand continueMovingToTaskPoint(Role pioneer, GameRequest request, GameContext ctx,
                                                  List<Pos> obstacles, int mapWidth, int mapHeight) {
        PlayerTask task = getCurrentTask(request, ctx);
        if (task == null || !task.isValid || task.coldDownRounds != 0) {
            ctx.taskState = TaskState.IDLE;
            return null;
        }
        Pos tp = task.taskPosition;
        if (MapUtil.isAdjacentOrSame(pioneer.pos, tp)) {
            ctx.taskState = TaskState.TASK_ACCEPTED;
            return RoleCommand.acceptTask();
        }
        return moveStep(pioneer, tp, obstacles, mapWidth, mapHeight);
    }

    /** TASK_ACCEPTED：构造 LLM prompt（任务期间不限次数） */
    private RoleCommand requestLlm(GameResponse response, GameRequest request, GameContext ctx) {
        String taskDesc = request.phaseTask;
        if (taskDesc == null || taskDesc.isEmpty()) {
            // 任务描述尚未下发，等待
            return null;
        }
        if (!LlmClient.canCallLlm(ctx, true)) {
            return null;
        }
        response.prompt = LlmClient.buildTaskPrompt(taskDesc, request.llmResp);
        ctx.taskState = TaskState.AWAIT_LLM;
        return null;
    }

    /** AWAIT_LLM：收到 llmResp → 有命令则提交沙盒，仅有答案则待提交 */
    private RoleCommand checkLlmResponse(GameResponse response, GameRequest request, GameContext ctx) {
        String llmResp = request.llmResp;
        if (llmResp == null || llmResp.isEmpty()) {
            return null;
        }
        String cmd = LlmClient.extractCommand(llmResp);
        if (!cmd.isEmpty() && !ctx.awaitingCmdResult) {
            response.executeCmd = SandboxExecutor.buildShellCmd(cmd);
            ctx.lastSubmittedCmd = response.executeCmd;
            ctx.awaitingCmdResult = true;
            ctx.taskState = TaskState.AWAIT_CMD;
            return null;
        }
        String answer = LlmClient.extractAnswer(llmResp);
        if (!answer.isEmpty()) {
            ctx.bestAnswer = answer;
            ctx.taskState = TaskState.READY_TO_SUBMIT;
            return null;
        }
        // 既无命令也无答案：重新请求 LLM
        ctx.taskState = TaskState.TASK_ACCEPTED;
        return null;
    }

    /** AWAIT_CMD：收到 lastCmdResult → 解析输出作为答案 */
    private RoleCommand checkCmdResult(GameRequest request, GameContext ctx) {
        String result = request.lastCmdResult;
        if (result == null || result.isEmpty()) {
            return null;
        }
        SandboxExecutor.CmdResult parsed = SandboxExecutor.parseResult(result);
        ctx.awaitingCmdResult = false;
        if (parsed.isSuccess() && !parsed.output.isEmpty()) {
            ctx.bestAnswer = parsed.output;
            ctx.taskState = TaskState.READY_TO_SUBMIT;
            return null;
        }
        // 命令执行失败：重新请求 LLM
        ctx.taskState = TaskState.TASK_ACCEPTED;
        return null;
    }

    /** READY_TO_SUBMIT：提交答案 */
    private RoleCommand submitBestAnswer(GameContext ctx) {
        String answer = ctx.bestAnswer == null ? "" : ctx.bestAnswer.trim();
        if (answer.isEmpty()) {
            ctx.taskState = TaskState.IDLE;
            return null;
        }
        answer = answer.replaceAll("\\n", " ");
        ctx.bestAnswer = answer;
        ctx.taskState = TaskState.SUBMITTED;
        return RoleCommand.submitAnswer(answer);
    }

    /** 检查 errorCode=1（任务超时）/2（答案错误），重置状态机 */
    private boolean hasTaskError(GameRequest request, GameContext ctx) {
        if (request.errors == null || request.errors.isEmpty()) {
            return false;
        }
        boolean error = false;
        for (ErrorInfo e : request.errors) {
            if (e != null && (e.errorCode == 1 || e.errorCode == 2)) {
                error = true;
                break;
            }
        }
        if (error) {
            ctx.taskState = TaskState.IDLE;
            ctx.awaitingCmdResult = false;
            ctx.pendingCmds.clear();
            ctx.bestAnswer = "";
        }
        return error;
    }

    private PlayerTask findAvailableTask(GameRequest request) {
        if (request.teamOur == null || request.teamOur.playerTasks == null) {
            return null;
        }
        for (PlayerTask t : request.teamOur.playerTasks) {
            if (t != null && t.isValid && t.coldDownRounds == 0) {
                return t;
            }
        }
        return null;
    }

    private PlayerTask getCurrentTask(GameRequest request, GameContext ctx) {
        if (request.teamOur == null || request.teamOur.playerTasks == null) {
            return null;
        }
        int idx = ctx.currentTaskPointIdx;
        if (idx >= 0 && idx < request.teamOur.playerTasks.size()) {
            return request.teamOur.playerTasks.get(idx);
        }
        return null;
    }

    private int indexOfTask(GameRequest request, PlayerTask task) {
        if (request.teamOur == null || request.teamOur.playerTasks == null) {
            return 0;
        }
        for (int i = 0; i < request.teamOur.playerTasks.size(); i++) {
            PlayerTask t = request.teamOur.playerTasks.get(i);
            if (t != null && t.taskPosition != null && task.taskPosition != null
                    && t.taskPosition.equals(task.taskPosition)) {
                return i;
            }
        }
        return 0;
    }

    private RoleCommand moveStep(Role role, Pos target, List<Pos> obstacles, int mapWidth, int mapHeight) {
        Pos next = MapUtil.nextStepToward(role.pos, target, obstacles, mapWidth, mapHeight);
        if (next == null || next.equals(role.pos)) {
            return null;
        }
        return RoleCommand.move(next);
    }
}
