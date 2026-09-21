package com.huawei;

import com.google.gson.Gson;
import com.huawei.model.GameRequest;
import com.huawei.model.GameResponse;
import com.huawei.model.RobotRole;
import com.huawei.model.RoleCommand;
import com.huawei.strategy.DecisionEngine;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Map;

/**
 * HTTP 请求处理器：解析判题器 Request JSON，调用 DecisionEngine 生成 Response JSON
 */
public class MyHttpHandler implements HttpHandler {

    private final Gson gson = new Gson();
    private final DecisionEngine engine = new DecisionEngine();

    @Override
    public void handle(HttpExchange httpExchange) {
        try {
            String requestBody = getRequestBody(httpExchange);
            GameRequest request = gson.fromJson(requestBody, GameRequest.class);
            //logRequest(request, requestBody);
            GameResponse response = engine.decide(request);
            String responseJson = gson.toJson(response);
            logResponse(request, response);
            sendResponse(httpExchange, responseJson);
        } catch (Exception ex) {
            ex.printStackTrace();
            try {
                sendResponse(httpExchange, "{}");
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 打印入参信息：原始请求体及关键字段摘要
     */
    private void logRequest(GameRequest request, String requestBody) {
        System.out.println("========== [Request] ==========");
        System.out.println(requestBody);
        System.out.println("========== [Request] ==========");
    }

    /**
     * 打印核心重要信息：决策引擎输出的角色指令和关键响应字段
     */
    private void logResponse(GameRequest request, GameResponse response) {
        System.out.println("========== [Response] ==========");
        gson.toJson(response);
        System.out.println("========== [Response] ==========");
    }

    private String getRequestBody(HttpExchange httpExchange) throws Exception {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(httpExchange.getRequestBody(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        return sb.toString();
    }

    private void sendResponse(HttpExchange httpExchange, String responseBody) throws Exception {
        byte[] responseBytes = responseBody.getBytes("UTF-8");
        httpExchange.getResponseHeaders().set("Content-Type", "application/json;charset=utf-8");
        httpExchange.sendResponseHeaders(200, responseBytes.length);
        OutputStream out = httpExchange.getResponseBody();
        out.write(responseBytes);
        out.flush();
        out.close();
    }
}
