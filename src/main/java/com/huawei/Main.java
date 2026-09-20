package com.huawei;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;

/**
 * 程序入口：启动 HttpServer，绑定命令行传入的端口，路径 / 映射到 MyHttpHandler。
 * 用法：java -cp "bin;lib/*" com.huawei.Main 8080
 */
public class Main {

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/", new MyHttpHandler());
        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();
        System.out.println("CoreGeek server started, listening on 0.0.0.0:" + port);
    }
}
