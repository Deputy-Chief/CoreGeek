/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2023-2023. All rights reserved.
 */

package com.huawei;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class Main {

    public static void main(final String[] args) throws IOException {
        System.out.println("Server to started...");
        final int port = parsePort(args);
        final HttpServer httpServer = createHttpServer(port);

        // 创建一个 HttpContext, 将路径为 / 请求映射到 MyHttpHandler处理器
        httpServer.createContext("/", new MyHttpHandler());

        // 设置服务器线程对象
        httpServer.setExecutor(Executors.newFixedThreadPool(10));

        // 启动服务器
        httpServer.start();
        System.out.println("Server started on port: " + httpServer.getAddress().getPort());
    }

    /**
     * 解析启动参数中的端口号，缺省返回 8080
     */
    private static int parsePort(final String[] args) {
        if (args != null && args.length > 0) {
            try {
                return Integer.parseInt(args[0].trim());
            } catch (NumberFormatException ignored) {
                // 参数非数字时回退到默认端口
            }
        }
        return 8080;
    }

    /**
     * 在指定端口创建 HttpServer，若端口被占用则向后递增尝试，直至成功或到达上限
     */
    private static HttpServer createHttpServer(int port) throws IOException {
        final int maxRetry = 100;
        IOException lastEx = null;
        for (int i = 0; i < maxRetry; i++) {
            try {
                return HttpServer.create(new InetSocketAddress(port + i), 0);
            } catch (IOException ex) {
                lastEx = ex;
                // 端口被占用，继续尝试下一个
            }
        }
        throw lastEx;
    }
}
