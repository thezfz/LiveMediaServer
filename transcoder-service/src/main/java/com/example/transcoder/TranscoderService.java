package com.example.transcoder;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * FFmpeg转码服务
 * 负责将RTMP流转换为HLS格式
 */
public class TranscoderService {
    private static final Logger logger = Logger.getLogger(TranscoderService.class.getName());
    
    private final String inputUrl;
    private final String outputDir;
    private final String streamKey;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private Process ffmpegProcess;
    private ExecutorService executorService;
    
    // HLS配置参数
    private static final int SEGMENT_DURATION = 6; // 分段时长（秒）
    private static final int PLAYLIST_SIZE = 5;    // 播放列表保留分段数
    private static final String VIDEO_CODEC = "libx264";
    private static final String AUDIO_CODEC = "aac";
    private static final String VIDEO_BITRATE = "1000k";
    private static final String AUDIO_BITRATE = "128k";
    
    public TranscoderService(String streamKey, String inputUrl, String outputDir) {
        this.streamKey = streamKey;
        this.inputUrl = inputUrl;
        this.outputDir = outputDir;
        this.executorService = Executors.newSingleThreadExecutor();
    }
    
    /**
     * 启动转码服务
     */
    public boolean start() {
        if (isRunning.get()) {
            logger.warning("转码服务已在运行中: " + streamKey);
            return false;
        }
        
        try {
            // 创建输出目录
            Path outputPath = Paths.get(outputDir);
            Files.createDirectories(outputPath);
            
            // 构建FFmpeg命令
            String[] command = buildFFmpegCommand();
            
            logger.info("启动转码服务: " + streamKey);
            logger.info("输入URL: " + inputUrl);
            logger.info("输出目录: " + outputDir);
            logger.info("FFmpeg命令: " + String.join(" ", command));
            
            // 启动FFmpeg进程
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            ffmpegProcess = processBuilder.start();
            
            isRunning.set(true);
            
            // 异步监控进程输出
            executorService.submit(this::monitorProcess);
            
            return true;
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "启动转码服务失败: " + streamKey, e);
            return false;
        }
    }
    
    /**
     * 停止转码服务
     */
    public void stop() {
        if (!isRunning.get()) {
            return;
        }
        
        logger.info("停止转码服务: " + streamKey);
        isRunning.set(false);
        
        if (ffmpegProcess != null && ffmpegProcess.isAlive()) {
            ffmpegProcess.destroy();
            try {
                // 等待进程结束，最多等待5秒
                if (!ffmpegProcess.waitFor(5, TimeUnit.SECONDS)) {
                    ffmpegProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ffmpegProcess.destroyForcibly();
            }
        }
        
        if (executorService != null) {
            executorService.shutdown();
        }
        
        // 清理HLS文件
        cleanupHLSFiles();
    }
    
    /**
     * 构建FFmpeg命令
     */
    private String[] buildFFmpegCommand() {
        String playlistPath = Paths.get(outputDir, "playlist.m3u8").toString();
        String segmentPattern = Paths.get(outputDir, "segment_%03d.ts").toString();
        
        return new String[] {
            "ffmpeg",
            "-i", inputUrl,                          // 输入RTMP流
            "-c:v", VIDEO_CODEC,                     // 视频编码器
            "-c:a", AUDIO_CODEC,                     // 音频编码器
            "-b:v", VIDEO_BITRATE,                   // 视频码率
            "-b:a", AUDIO_BITRATE,                   // 音频码率
            "-f", "hls",                             // 输出格式HLS
            "-hls_time", String.valueOf(SEGMENT_DURATION),  // 分段时长
            "-hls_list_size", String.valueOf(PLAYLIST_SIZE), // 播放列表大小
            "-hls_flags", "delete_segments",         // 自动删除旧分段
            "-hls_segment_filename", segmentPattern, // 分段文件名模式
            "-y",                                    // 覆盖输出文件
            playlistPath                             // 播放列表文件
        };
    }
    
    /**
     * 监控FFmpeg进程
     */
    private void monitorProcess() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(ffmpegProcess.getInputStream()))) {
            
            String line;
            while ((line = reader.readLine()) != null && isRunning.get()) {
                // 记录FFmpeg输出（可以用于调试）
                if (line.contains("frame=") || line.contains("time=")) {
                    logger.fine("FFmpeg输出: " + line);
                } else if (line.contains("error") || line.contains("Error")) {
                    logger.warning("FFmpeg错误: " + line);
                }
            }
            
        } catch (IOException e) {
            if (isRunning.get()) {
                logger.log(Level.WARNING, "读取FFmpeg输出时出错: " + streamKey, e);
            }
        } finally {
            if (isRunning.get()) {
                logger.warning("FFmpeg进程意外结束: " + streamKey);
                isRunning.set(false);
            }
        }
    }
    
    /**
     * 清理HLS文件
     */
    private void cleanupHLSFiles() {
        try {
            Path outputPath = Paths.get(outputDir);
            if (Files.exists(outputPath)) {
                Files.walk(outputPath)
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String fileName = path.getFileName().toString();
                        return fileName.endsWith(".m3u8") || fileName.endsWith(".ts");
                    })
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            logger.info("删除HLS文件: " + path);
                        } catch (IOException e) {
                            logger.warning("删除文件失败: " + path + " - " + e.getMessage());
                        }
                    });
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "清理HLS文件时出错: " + streamKey, e);
        }
    }
    
    /**
     * 检查转码服务是否运行中
     */
    public boolean isRunning() {
        return isRunning.get() && ffmpegProcess != null && ffmpegProcess.isAlive();
    }
    
    /**
     * 获取HLS播放列表URL
     */
    public String getPlaylistUrl() {
        return "/hls/" + streamKey + "/playlist.m3u8";
    }
    
    /**
     * 获取输出目录
     */
    public String getOutputDir() {
        return outputDir;
    }
    
    /**
     * 获取流密钥
     */
    public String getStreamKey() {
        return streamKey;
    }
}
