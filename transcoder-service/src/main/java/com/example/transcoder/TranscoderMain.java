package com.example.transcoder;

import java.io.*;
import java.net.*;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

/**
 * 转码服务主程序
 * 提供HTTP API接口用于控制转码服务
 */
public class TranscoderMain {
    private static final Logger logger = Logger.getLogger(TranscoderMain.class.getName());
    
    private static final int DEFAULT_PORT = 8081;
    private static final String DEFAULT_OUTPUT_DIR = "/app/hls";
    private static final String DEFAULT_RTMP_URL = "rtmp://rtmp-server:1935/live";
    
    private final TranscoderManager transcoderManager;
    private final int port;
    private HttpServer server;
    private ScheduledExecutorService scheduler;
    
    public TranscoderMain(int port, String outputDir, String rtmpBaseUrl) {
        this.port = port;
        this.transcoderManager = new TranscoderManager(outputDir, rtmpBaseUrl);
        this.scheduler = Executors.newScheduledThreadPool(2);
    }
    
    /**
     * 启动转码服务
     */
    public void start() throws IOException {
        // 创建HTTP服务器
        server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // 注册API端点
        server.createContext("/start", this::handleStart);
        server.createContext("/stop", this::handleStop);
        server.createContext("/status", this::handleStatus);
        server.createContext("/health", this::handleHealth);
        
        // 启动服务器
        server.setExecutor(null);
        server.start();
        
        logger.info("转码服务已启动，监听端口: " + port);
        logger.info("API端点:");
        logger.info("  POST /start?streamKey=<key>  - 启动转码");
        logger.info("  POST /stop?streamKey=<key>   - 停止转码");
        logger.info("  GET  /status?streamKey=<key> - 查询状态");
        logger.info("  GET  /health                 - 健康检查");
        
        // 启动定期清理任务
        scheduler.scheduleAtFixedRate(
            transcoderManager::cleanupInactiveTranscoders,
            30, 30, TimeUnit.SECONDS
        );
        
        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }
    
    /**
     * 处理启动转码请求
     */
    private void handleStart(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed");
            return;
        }
        
        String streamKey = getQueryParameter(exchange, "streamKey");
        if (streamKey == null || streamKey.trim().isEmpty()) {
            sendResponse(exchange, 400, "Missing streamKey parameter");
            return;
        }
        
        logger.info("收到启动转码请求: " + streamKey);
        
        boolean success = transcoderManager.startTranscoding(streamKey);
        if (success) {
            TranscoderManager.TranscoderStatus status = transcoderManager.getTranscoderStatus(streamKey);
            String response = String.format(
                "{\"success\": true, \"message\": \"转码已启动\", \"streamKey\": \"%s\", \"playlistUrl\": \"%s\"}",
                streamKey, status.getPlaylistUrl()
            );
            sendResponse(exchange, 200, response);
        } else {
            sendResponse(exchange, 500, "{\"success\": false, \"message\": \"转码启动失败\"}");
        }
    }
    
    /**
     * 处理停止转码请求
     */
    private void handleStop(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed");
            return;
        }
        
        String streamKey = getQueryParameter(exchange, "streamKey");
        if (streamKey == null || streamKey.trim().isEmpty()) {
            sendResponse(exchange, 400, "Missing streamKey parameter");
            return;
        }
        
        logger.info("收到停止转码请求: " + streamKey);
        
        boolean success = transcoderManager.stopTranscoding(streamKey);
        if (success) {
            sendResponse(exchange, 200, "{\"success\": true, \"message\": \"转码已停止\"}");
        } else {
            sendResponse(exchange, 404, "{\"success\": false, \"message\": \"转码服务不存在\"}");
        }
    }
    
    /**
     * 处理状态查询请求
     */
    private void handleStatus(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed");
            return;
        }
        
        String streamKey = getQueryParameter(exchange, "streamKey");
        if (streamKey == null || streamKey.trim().isEmpty()) {
            // 返回所有转码状态
            String[] activeKeys = transcoderManager.getActiveStreamKeys();
            StringBuilder response = new StringBuilder("{\"activeTranscoders\": [");
            for (int i = 0; i < activeKeys.length; i++) {
                if (i > 0) response.append(", ");
                TranscoderManager.TranscoderStatus status = transcoderManager.getTranscoderStatus(activeKeys[i]);
                response.append(String.format(
                    "{\"streamKey\": \"%s\", \"isRunning\": %s, \"playlistUrl\": \"%s\"}",
                    status.getStreamKey(), status.isRunning(), status.getPlaylistUrl()
                ));
            }
            response.append("]}");
            sendResponse(exchange, 200, response.toString());
        } else {
            // 返回特定流的状态
            TranscoderManager.TranscoderStatus status = transcoderManager.getTranscoderStatus(streamKey);
            String response = String.format(
                "{\"streamKey\": \"%s\", \"isRunning\": %s, \"playlistUrl\": \"%s\"}",
                status.getStreamKey(), status.isRunning(), status.getPlaylistUrl()
            );
            sendResponse(exchange, 200, response);
        }
    }
    
    /**
     * 处理健康检查请求
     */
    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed");
            return;
        }
        
        int activeCount = transcoderManager.getActiveTranscoderCount();
        String response = String.format(
            "{\"status\": \"UP\", \"activeTranscoders\": %d}",
            activeCount
        );
        sendResponse(exchange, 200, response);
    }
    
    /**
     * 发送HTTP响应
     */
    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }
    
    /**
     * 获取查询参数
     */
    private String getQueryParameter(HttpExchange exchange, String paramName) {
        String query = exchange.getRequestURI().getQuery();
        if (query == null) return null;
        
        for (String param : query.split("&")) {
            String[] pair = param.split("=");
            if (pair.length == 2 && paramName.equals(pair[0])) {
                try {
                    return URLDecoder.decode(pair[1], "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    return pair[1];
                }
            }
        }
        return null;
    }
    
    /**
     * 关闭服务
     */
    public void shutdown() {
        logger.info("正在关闭转码服务...");
        
        if (scheduler != null) {
            scheduler.shutdown();
        }
        
        if (transcoderManager != null) {
            transcoderManager.stopAllTranscoders();
        }
        
        if (server != null) {
            server.stop(5);
        }
        
        logger.info("转码服务已关闭");
    }
    
    /**
     * 主方法
     */
    public static void main(String[] args) {
        try {
            // 解析命令行参数
            int port = DEFAULT_PORT;
            String outputDir = DEFAULT_OUTPUT_DIR;
            String rtmpUrl = DEFAULT_RTMP_URL;
            
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--port":
                        if (i + 1 < args.length) {
                            port = Integer.parseInt(args[++i]);
                        }
                        break;
                    case "--output-dir":
                        if (i + 1 < args.length) {
                            outputDir = args[++i];
                        }
                        break;
                    case "--rtmp-url":
                        if (i + 1 < args.length) {
                            rtmpUrl = args[++i];
                        }
                        break;
                }
            }
            
            // 启动转码服务
            TranscoderMain main = new TranscoderMain(port, outputDir, rtmpUrl);
            main.start();
            
            // 保持运行
            Thread.currentThread().join();
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "转码服务启动失败", e);
            System.exit(1);
        }
    }
}
