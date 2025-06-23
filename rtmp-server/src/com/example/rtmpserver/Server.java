package com.example.rtmpserver;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * 服务器主程序，监听RTMP标准端口1935上的连接。
 * 为每一个新的客户端连接创建一个独立的RtmpHandler线程进行处理。
 */
public class Server {

    public static void main(String[] args) {
        int port = 1935; // RTMP standard port

        // 从环境变量获取Web API服务器地址，默认为容器内地址
        String webApiUrl = System.getenv("WEB_API_URL");
        if (webApiUrl == null || webApiUrl.trim().isEmpty()) {
            webApiUrl = "http://web-api-server:8080"; // 容器内默认地址
        }

        // 创建API客户端
        ApiClient apiClient = new ApiClient(webApiUrl);
        System.out.println("🔗 Initializing API client with URL: " + webApiUrl);

        // 测试API连接
        if (apiClient.testConnection()) {
            System.out.println("✅ API server connection successful");
        } else {
            System.out.println("⚠️ API server connection failed, but continuing...");
        }

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("RTMP Server is listening on port " + port);

            while (true) {
                try {
                    Socket socket = serverSocket.accept();
                    System.out.println("New client connected from: " + socket.getRemoteSocketAddress());
                    // 为每个连接创建一个新线程，传递API客户端
                    new Thread(new RtmpHandler(socket, apiClient)).start();
                } catch (IOException e) {
                    System.err.println("Error accepting client connection: " + e.getMessage());
                }
            }
        } catch (IOException ex) {
            System.err.println("Server exception: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
} 