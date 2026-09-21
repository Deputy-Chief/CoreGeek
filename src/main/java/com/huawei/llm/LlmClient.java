package com.huawei.llm;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.google.gson.*;
import com.huawei.sandbox.SandboxExecutor;

import com.huawei.model.GameContext;
import com.huawei.model.WorldNews;

/**
 * LLM 客户端：prompt 构造与响应解析（全静态）。
 */
public final class LlmClient {

    private static final Pattern CODE_BLOCK_PATTERN =
            Pattern.compile("```(bash|python|sh|shell)?[ \\t]*\\r?\\n([\\s\\S]*?)```");

    private LlmClient() {
    }

    /**
     * 是否可调用 LLM。
     * 任务期间（phaseTask 非空）恒 true；非任务期间每个游戏日限 3 次。
     */
    public static boolean canCallLlm(GameContext ctx, boolean inTask) {
        if (inTask) {
            return true;
        }
        return ctx.llmCallCountToday < 3;
    }

    /** 构造自进化任务解题 prompt，附任务描述 + 上次 LLM 返回 */
    public static String buildTaskPrompt(String taskDescription, String lastLlmResp) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是《未来战争》编程大赛中的任务解题助手。请根据任务描述，在沙盒环境中执行命令并得出答案。\n");
        sb.append("任务描述：\n").append(taskDescription == null ? "" : taskDescription).append("\n\n");
        if (lastLlmResp != null && !lastLlmResp.isEmpty()) {
            sb.append("上次返回：\n").append(lastLlmResp).append("\n\n");
        }
        sb.append("输出要求：\n");
        sb.append("仅返回 JSON：{\"answer\":\"最终答案或空串\",\"command\":\"shell命令或空串\",\"language\":\"shell或python\"}。\n");
        sb.append("查询/探索命令的输出会回传给你继续推理，不直接作为答案。\n");
        sb.append("能直接求解的脚本请只输出 JSON {\"answer\":\"最终答案\"}，以减少回合。\n");
        sb.append("命令超时15秒；复用提供的成功脚本时必须按当前题目更新参数，不能沿用旧题答案。");
        return sb.toString();
    }

    /** 构造宝藏推理 prompt，要求 JSON 格式输出位置/物品/时间 */
    public static String buildTreasurePrompt(WorldNews news, String lastLlmResp) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是《未来战争》中的宝藏推理助手。请根据连续多天的民间传闻，推断祭坛宝藏的位置、开启条件和所需献祭物品。\n");
        sb.append("民间传闻：\n");
        sb.append(news == null || news.folkLegends == null ? "" : news.folkLegends).append("\n\n");
        if (lastLlmResp != null && !lastLlmResp.isEmpty()) {
            sb.append("上次返回：\n").append(lastLlmResp).append("\n\n");
        }
        sb.append("仅在全部条件明确时回答 JSON：{\"position\":{\"x\":..,\"y\":..},\"items\":[\"..\"],\"day\":1,\"roundInDay\":0}，day为1到10，roundInDay为0到129。未知时position为null，不猜测。\n");
        sb.append("物品只可用 AcientTablet, StarSand, FlameBreath, FrostPotion, ThornAmulet, IronWhistle；重复元素表示数量。\n");
        sb.append("祭坛不等于任务点；结合失败反馈修正位置、时间或物品组合。");
        return sb.toString();
    }

    /** 从 LLM 响应中提取答案文本，去除 markdown 代码块标记 */
    public static String extractAnswer(String llmResp) {
        if (llmResp == null) {
            return "";
        }
        String s = llmResp.trim();
        JsonObject json = parseObject(s);
        if (json != null) return string(json, "answer");
        s = s.replaceAll("```[a-zA-Z]*\\s*", "").replaceAll("```", "").trim();
        return s;
    }

    /** 从 LLM 响应中提取 bash/python 代码块内容作为沙盒命令；无代码块返回空串 */
    public static String extractCommand(String llmResp) {
        if (llmResp == null) {
            return "";
        }
        JsonObject json = parseObject(llmResp);
        if (json != null) {
            String cmd = string(json, "command");
            return "python".equalsIgnoreCase(string(json, "language")) && !cmd.isEmpty()
                    ? SandboxExecutor.buildPythonCmd(cmd) : cmd;
        }
        if (llmResp.trim().startsWith("{") || llmResp.trim().startsWith("```json")) return "";
        Matcher m = CODE_BLOCK_PATTERN.matcher(llmResp);
        if (m.find()) {
            String cmd = m.group(2).trim();
            if (!cmd.isEmpty()) {
                return "python".equals(m.group(1)) ? SandboxExecutor.buildPythonCmd(cmd) : cmd;
            }
        }
        return "";
    }

    public static JsonObject parseObject(String text) {
        if (text == null) return null;
        String s = text.trim().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        try {
            JsonElement e = new JsonParser().parse(s);
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (RuntimeException e) { return null; }
    }

    public static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString().trim() : "";
    }
}
