package com.example.livemediaserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Spring Boot application class for Live Media Server Web API
 * 
 * This application provides:
 * - REST APIs for stream management
 * - HLS file serving
 * - WebSocket connections for real-time updates
 * - Integration with RTMP server and transcoder modules
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class LiveMediaServerApplication {

    public static void main(String[] args) {
        System.out.println("🚀 Starting Live Media Server Web API...");
        SpringApplication.run(LiveMediaServerApplication.class, args);
        System.out.println("✅ Live Media Server Web API started successfully!");
    }
}
