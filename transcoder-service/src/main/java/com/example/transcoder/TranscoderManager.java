package com.example.transcoder;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.nio.file.Paths;

/**
 * 转码管理器
 * 管理多个转码服务实例
 */
public class TranscoderManager {
    private static final Logger logger = Logger.getLogger(TranscoderManager.class.getName());
    
    private final ConcurrentMap<String, TranscoderService> activeTranscoders = new ConcurrentHashMap<>();
    private final String baseOutputDir;
    private final String rtmpBaseUrl;
    
    public TranscoderManager(String baseOutputDir, String rtmpBaseUrl) {
        this.baseOutputDir = baseOutputDir;
        this.rtmpBaseUrl = rtmpBaseUrl;
    }
    
    /**
     * 启动转码服务
     */
    public boolean startTranscoding(String streamKey) {
        if (activeTranscoders.containsKey(streamKey)) {
            logger.warning("转码服务已存在: " + streamKey);
            return false;
        }
        
        try {
            // 构建输入和输出路径
            String inputUrl = rtmpBaseUrl + "/" + streamKey;
            String outputDir = Paths.get(baseOutputDir, streamKey).toString();
            
            // 创建转码服务
            TranscoderService transcoder = new TranscoderService(streamKey, inputUrl, outputDir);
            
            // 启动转码
            if (transcoder.start()) {
                activeTranscoders.put(streamKey, transcoder);
                logger.info("转码服务启动成功: " + streamKey);
                return true;
            } else {
                logger.warning("转码服务启动失败: " + streamKey);
                return false;
            }
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "启动转码服务时出错: " + streamKey, e);
            return false;
        }
    }
    
    /**
     * 停止转码服务
     */
    public boolean stopTranscoding(String streamKey) {
        TranscoderService transcoder = activeTranscoders.remove(streamKey);
        if (transcoder != null) {
            transcoder.stop();
            logger.info("转码服务已停止: " + streamKey);
            return true;
        } else {
            logger.warning("转码服务不存在: " + streamKey);
            return false;
        }
    }
    
    /**
     * 检查转码服务是否运行中
     */
    public boolean isTranscoding(String streamKey) {
        TranscoderService transcoder = activeTranscoders.get(streamKey);
        return transcoder != null && transcoder.isRunning();
    }
    
    /**
     * 获取转码服务
     */
    public TranscoderService getTranscoder(String streamKey) {
        return activeTranscoders.get(streamKey);
    }
    
    /**
     * 获取活跃转码数量
     */
    public int getActiveTranscoderCount() {
        return activeTranscoders.size();
    }
    
    /**
     * 获取所有活跃的流密钥
     */
    public String[] getActiveStreamKeys() {
        return activeTranscoders.keySet().toArray(new String[0]);
    }
    
    /**
     * 停止所有转码服务
     */
    public void stopAllTranscoders() {
        logger.info("停止所有转码服务...");
        
        for (String streamKey : activeTranscoders.keySet()) {
            stopTranscoding(streamKey);
        }
        
        activeTranscoders.clear();
        logger.info("所有转码服务已停止");
    }
    
    /**
     * 清理无效的转码服务
     */
    public void cleanupInactiveTranscoders() {
        activeTranscoders.entrySet().removeIf(entry -> {
            String streamKey = entry.getKey();
            TranscoderService transcoder = entry.getValue();
            
            if (!transcoder.isRunning()) {
                logger.info("清理无效转码服务: " + streamKey);
                transcoder.stop();
                return true;
            }
            return false;
        });
    }
    
    /**
     * 获取转码状态信息
     */
    public TranscoderStatus getTranscoderStatus(String streamKey) {
        TranscoderService transcoder = activeTranscoders.get(streamKey);
        if (transcoder == null) {
            return new TranscoderStatus(streamKey, false, null, null);
        }
        
        return new TranscoderStatus(
            streamKey,
            transcoder.isRunning(),
            transcoder.getPlaylistUrl(),
            transcoder.getOutputDir()
        );
    }
    
    /**
     * 转码状态信息类
     */
    public static class TranscoderStatus {
        private final String streamKey;
        private final boolean isRunning;
        private final String playlistUrl;
        private final String outputDir;
        
        public TranscoderStatus(String streamKey, boolean isRunning, String playlistUrl, String outputDir) {
            this.streamKey = streamKey;
            this.isRunning = isRunning;
            this.playlistUrl = playlistUrl;
            this.outputDir = outputDir;
        }
        
        public String getStreamKey() { return streamKey; }
        public boolean isRunning() { return isRunning; }
        public String getPlaylistUrl() { return playlistUrl; }
        public String getOutputDir() { return outputDir; }
        
        @Override
        public String toString() {
            return String.format("TranscoderStatus{streamKey='%s', isRunning=%s, playlistUrl='%s', outputDir='%s'}", 
                streamKey, isRunning, playlistUrl, outputDir);
        }
    }
}
