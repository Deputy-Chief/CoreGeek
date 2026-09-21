package com.huawei.sandbox;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 沙盒命令构造与结果解析（全静态）。
 */
public final class SandboxExecutor {

    private static final Pattern EXIT_CODE_PATTERN = Pattern.compile("\\[exitCode:(\\d+)\\]");

    private SandboxExecutor() {
    }

    /**
     * 解析 lastCmdResult，识别三种格式：
     *  [exitCode:N]\n<输出>      — 正常执行
     *  [TIMEOUT]\n<部分输出>     — 命令超时
     *  [JUDGER_ERROR]\n<原因>    — 判题器异常
     * 输出末尾 [TRUNCATED] 表示超过 64KB 被截断。
     */
    public static CmdResult parseResult(String lastCmdResult) {
        CmdResult r = new CmdResult();
        r.raw = lastCmdResult == null ? "" : lastCmdResult;
        if (lastCmdResult == null || lastCmdResult.isEmpty()) {
            r.empty = true;
            return r;
        }
        String s = lastCmdResult;
        if (s.contains("[TIMEOUT]")) {
            r.timeout = true;
            s = s.replace("[TIMEOUT]", "");
        } else if (s.contains("[JUDGER_ERROR]")) {
            r.judgerError = true;
            s = s.replace("[JUDGER_ERROR]", "");
        }
        if (s.contains("[TRUNCATED]")) {
            r.truncated = true;
            s = s.replace("[TRUNCATED]", "");
        }
        Matcher m = EXIT_CODE_PATTERN.matcher(s);
        if (m.find()) {
            try {
                r.exitCode = Integer.parseInt(m.group(1));
            } catch (NumberFormatException e) {
                r.judgerError = true;
            }
            s = s.replace(m.group(), "");
        }
        r.output = s.trim();
        return r;
    }

    /** 构造 python3 -c 命令 */
    public static String buildPythonCmd(String script) {
        return "python3 -c '" + script.replace("'", "'\"'\"'") + "'";
    }

    /** 构造 shell 命令（原样返回） */
    public static String buildShellCmd(String cmd) {
        return cmd == null ? "" : cmd;
    }

    /** 沙盒执行结果封装 */
    public static class CmdResult {
        public boolean empty;
        public boolean timeout;
        public boolean judgerError;
        public boolean truncated;
        public int exitCode = -1;
        public String output = "";
        public String raw = "";

        /** 非空且非超时且非判题器错误且 exitCode==0 视为成功 */
        public boolean isSuccess() {
            return !empty && !timeout && !judgerError && !truncated && exitCode == 0;
        }
    }
}
