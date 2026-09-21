package com.huawei;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import com.google.gson.Gson;
import com.huawei.model.GameRequest;
import com.huawei.model.GameResponse;
import com.huawei.strategy.DecisionEngine;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * HTTP 请求处理器：读取 POST Body → Gson 反序列化 → 决策引擎 → 序列化返回。
 * 异常时返回 "{}" 避免 5 秒响应超时。
 */
public class MyHttpHandler implements HttpHandler {

    private final Gson gson = new Gson();
    private final DecisionEngine engine = new DecisionEngine();

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        String responseBody;
        try {
            String requestBody = getRequestBody(httpExchange);
            GameRequest request = gson.fromJson(requestBody, GameRequest.class);
            GameResponse response = engine.decide(request);
            responseBody = gson.toJson(response);
        } catch (Exception e) {
            e.printStackTrace();
            responseBody = "{}";
        }
        sendResponse(httpExchange, responseBody);
    }

    /** 读取 POST Body 为 UTF-8 字符串 */
    private String getRequestBody(HttpExchange httpExchange) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (Reader reader = new InputStreamReader(httpExchange.getRequestBody(), StandardCharsets.UTF_8)) {
            char[] buf = new char[8192];
            int len;
            while ((len = reader.read(buf)) != -1) {
                sb.append(buf, 0, len);
            }
        }
        return sb.toString();
    }

    /** 发送 JSON 响应，Content-Type=application/json;charset=utf-8 */
    private void sendResponse(HttpExchange httpExchange, String responseBody) throws IOException {
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        httpExchange.getResponseHeaders().set("Content-Type", "application/json;charset=utf-8");
        httpExchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = httpExchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
