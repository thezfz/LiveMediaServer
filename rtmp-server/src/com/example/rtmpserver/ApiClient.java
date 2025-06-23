package com.example.rtmpserver;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * HTTP客户端，用于与Web API服务器通信
 * 负责通知流的开始、结束等事件
 */
public class ApiClient {
    
    private final String baseUrl;
    private static final int CONNECT_TIMEOUT = 5000; // 5秒连接超时
    private static final int READ_TIMEOUT = 10000;   // 10秒读取超时
    
    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        System.out.println("🔗 ApiClient initialized with base URL: " + this.baseUrl);
    }
    
    /**
     * 通知流开始
     */
    public boolean notifyStreamStart(String streamKey, String clientIp) {
        try {
            String jsonPayload = createStreamStartPayload(streamKey, clientIp);
            String response = post("/api/streams/start", jsonPayload);
            System.out.println("✅ Stream start notification sent successfully for: " + streamKey);
            System.out.println("📝 Response: " + response);
            return true;
        } catch (Exception e) {
            System.err.println("❌ Failed to notify stream start for: " + streamKey);
            System.err.println("🔍 Error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 通知流结束
     */
    public boolean notifyStreamStop(String streamKey) {
        try {
            String jsonPayload = createStreamStopPayload(streamKey);
            String response = post("/api/streams/stop", jsonPayload);
            System.out.println("✅ Stream stop notification sent successfully for: " + streamKey);
            System.out.println("📝 Response: " + response);
            return true;
        } catch (Exception e) {
            System.err.println("❌ Failed to notify stream stop for: " + streamKey);
            System.err.println("🔍 Error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 发送POST请求
     */
    private String post(String endpoint, String jsonPayload) throws IOException {
        @SuppressWarnings("deprecation")
        URL url = new URL(baseUrl + endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        try {
            // 设置请求属性
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setDoOutput(true);
            
            // 发送请求体
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            // 读取响应
            int responseCode = connection.getResponseCode();
            InputStream inputStream = responseCode >= 200 && responseCode < 300 
                ? connection.getInputStream() 
                : connection.getErrorStream();
                
            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
            }
            
            if (responseCode >= 200 && responseCode < 300) {
                return response.toString();
            } else {
                throw new IOException("HTTP " + responseCode + ": " + response.toString());
            }
            
        } finally {
            connection.disconnect();
        }
    }
    
    /**
     * 创建流开始事件的JSON负载
     */
    private String createStreamStartPayload(String streamKey, String clientIp) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return String.format(
            "{\"streamKey\":\"%s\",\"clientIp\":\"%s\",\"timestamp\":\"%s\",\"action\":\"start\"}",
            escapeJson(streamKey),
            escapeJson(clientIp),
            timestamp
        );
    }
    
    /**
     * 创建流结束事件的JSON负载
     */
    private String createStreamStopPayload(String streamKey) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return String.format(
            "{\"streamKey\":\"%s\",\"timestamp\":\"%s\",\"action\":\"stop\"}",
            escapeJson(streamKey),
            timestamp
        );
    }
    
    /**
     * 简单的JSON字符串转义
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    /**
     * 测试与API服务器的连接
     */
    public boolean testConnection() {
        try {
            @SuppressWarnings("deprecation")
            URL url = new URL(baseUrl + "/api/actuator/health");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            
            int responseCode = connection.getResponseCode();
            connection.disconnect();
            
            boolean isHealthy = responseCode == 200;
            System.out.println(isHealthy ? 
                "✅ API server connection test successful" : 
                "❌ API server connection test failed with code: " + responseCode);
            return isHealthy;
            
        } catch (Exception e) {
            System.err.println("❌ API server connection test failed: " + e.getMessage());
            return false;
        }
    }
}
