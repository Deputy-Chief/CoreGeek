package com.huawei.llm;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.huawei.model.GameContext;
import com.huawei.model.WorldNews;

/**
 * LLM 客户端：prompt 构造与响应解析（全静态）。
 */
public final class LlmClient {

    private static final Pattern CODE_BLOCK_PATTERN =
            Pattern.compile("```(?:bash|python|sh|shell)?\\s*\\r?\\n?([\\s\\S]*?)```");

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
        sb.append("1. 如果需要执行沙盒命令，请用 ```bash 或 ```python 代码块给出命令，我会代为执行并回传结果。\n");
        sb.append("2. 如果已经可以得出答案，请直接给出最终答案文本（不要包裹在代码块中）。");
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
        sb.append("请以 JSON 格式回答：{\"position\":{\"x\":..,\"y\":..},\"items\":[\"..\"],\"day\":..}");
        return sb.toString();
    }

    /** 从 LLM 响应中提取答案文本，去除 markdown 代码块标记 */
    public static String extractAnswer(String llmResp) {
        if (llmResp == null) {
            return "";
        }
        String s = llmResp.trim();
        s = s.replaceAll("```[a-zA-Z]*\\s*", "").replaceAll("```", "").trim();
        return s;
    }

    /** 从 LLM 响应中提取 bash/python 代码块内容作为沙盒命令；无代码块返回空串 */
    public static String extractCommand(String llmResp) {
        if (llmResp == null) {
            return "";
        }
        Matcher m = CODE_BLOCK_PATTERN.matcher(llmResp);
        if (m.find()) {
            String cmd = m.group(1).trim();
            if (!cmd.isEmpty()) {
                return cmd;
            }
        }
        return "";
    }
}
