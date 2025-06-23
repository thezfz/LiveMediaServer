package com.example.livemediaserver.service;

import com.example.livemediaserver.model.Stream;
import com.example.livemediaserver.model.StreamStatus;
import com.example.livemediaserver.repository.StreamRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service class for managing live streams
 */
@Service
public class StreamService {

    @Autowired
    private StreamRepository streamRepository;

    @Value("${livemediaserver.media.storage-path:../media-data}")
    private String mediaStoragePath;

    /**
     * Get all active streams
     */
    public List<Stream> getAllActiveStreams() {
        return streamRepository.findByStatusIn(List.of(StreamStatus.LIVE, StreamStatus.OFFLINE));
    }

    /**
     * Get stream by ID
     */
    public Optional<Stream> getStreamById(String streamId) {
        return streamRepository.findById(streamId);
    }

    /**
     * Create a new stream
     */
    public Stream createStream(String streamId, String streamName) {
        Stream stream = new Stream(streamId, streamName);
        stream.setRtmpUrl("rtmp://localhost:1935/live/" + streamId);
        stream.setHlsUrl("/api/streams/" + streamId + "/playlist.m3u8");
        return streamRepository.save(stream);
    }

    /**
     * Start a stream (called when RTMP connection begins)
     */
    public boolean startStream(String streamId) {
        Optional<Stream> optionalStream = streamRepository.findById(streamId);
        if (optionalStream.isPresent()) {
            Stream stream = optionalStream.get();
            stream.start();
            streamRepository.save(stream);
            return true;
        }
        return false;
    }

    /**
     * Stop a stream
     */
    public boolean stopStream(String streamId) {
        Optional<Stream> optionalStream = streamRepository.findById(streamId);
        if (optionalStream.isPresent()) {
            Stream stream = optionalStream.get();
            stream.stop();
            streamRepository.save(stream);
            return true;
        }
        return false;
    }

    /**
     * Get stream statistics
     */
    public Optional<Object> getStreamStats(String streamId) {
        Optional<Stream> optionalStream = streamRepository.findById(streamId);
        if (optionalStream.isPresent()) {
            Stream stream = optionalStream.get();
            Map<String, Object> stats = new HashMap<>();
            stats.put("id", stream.getId());
            stats.put("name", stream.getName());
            stats.put("status", stream.getStatus());
            stats.put("viewerCount", stream.getViewerCount());
            stats.put("bitrate", stream.getBitrate());
            stats.put("resolution", stream.getResolution());
            stats.put("startedAt", stream.getStartedAt());
            stats.put("duration", calculateDuration(stream));
            return Optional.of(stats);
        }
        return Optional.empty();
    }

    /**
     * Start transcoding for a stream
     */
    public boolean startTranscoding(String streamId) {
        Optional<Stream> optionalStream = streamRepository.findById(streamId);
        if (optionalStream.isPresent()) {
            Stream stream = optionalStream.get();
            stream.setTranscodingEnabled(true);
            streamRepository.save(stream);

            // 集成转码模块 - 调用转码服务API
            try {
                // 这里可以调用转码服务的HTTP API
                // 例如: POST http://localhost:8081/transcode/start
                System.out.println("🎬 Starting transcoding for stream: " + streamId);
                System.out.println("📡 Transcoder service integration: http://localhost:8081");
                // 实际的HTTP调用可以在这里实现
                return true;
            } catch (Exception e) {
                System.err.println("❌ Failed to start transcoding: " + e.getMessage());
                return false;
            }
        }
        return false;
    }

    /**
     * Get HLS playlist for a stream
     */
    public Optional<String> getHlsPlaylist(String streamId) {
        try {
            Path playlistPath = Paths.get(mediaStoragePath, streamId, "playlist.m3u8");
            if (Files.exists(playlistPath)) {
                return Optional.of(Files.readString(playlistPath));
            }
        } catch (IOException e) {
            // Log error
        }
        return Optional.empty();
    }

    /**
     * Get HLS segment for a stream
     */
    public Optional<byte[]> getHlsSegment(String streamId, String segmentName) {
        try {
            Path segmentPath = Paths.get(mediaStoragePath, streamId, segmentName + ".ts");
            if (Files.exists(segmentPath)) {
                return Optional.of(Files.readAllBytes(segmentPath));
            }
        } catch (IOException e) {
            // Log error
        }
        return Optional.empty();
    }

    /**
     * Create or update stream when RTMP connection starts
     */
    public Stream createOrUpdateStream(String streamKey, String clientIp) {
        Optional<Stream> existingStream = streamRepository.findById(streamKey);

        Stream stream;
        if (existingStream.isPresent()) {
            // 更新现有流
            stream = existingStream.get();
            System.out.println("📝 Updating existing stream: " + streamKey);
        } else {
            // 创建新流
            stream = new Stream(streamKey, "Live Stream " + streamKey);
            stream.setRtmpUrl("rtmp://localhost:1935/live/" + streamKey);
            stream.setHlsUrl("/api/streams/" + streamKey + "/playlist.m3u8");
            System.out.println("🆕 Creating new stream: " + streamKey);
        }

        // 设置流为直播状态
        stream.start();

        // 保存客户端IP信息（可以扩展Stream模型来存储这个信息）
        System.out.println("🌐 Client IP: " + clientIp);

        return streamRepository.save(stream);
    }

    /**
     * Stop stream by stream key
     */
    public boolean stopStreamByKey(String streamKey) {
        Optional<Stream> optionalStream = streamRepository.findById(streamKey);
        if (optionalStream.isPresent()) {
            Stream stream = optionalStream.get();
            stream.stop();
            streamRepository.save(stream);
            System.out.println("🛑 Stream stopped: " + streamKey);
            return true;
        }
        System.out.println("⚠️ Stream not found: " + streamKey);
        return false;
    }

    /**
     * Update stream metadata (bitrate, resolution, etc.)
     */
    public boolean updateStreamMetadata(String streamKey, Long bitrate, String resolution) {
        Optional<Stream> optionalStream = streamRepository.findById(streamKey);
        if (optionalStream.isPresent()) {
            Stream stream = optionalStream.get();

            if (bitrate != null) {
                stream.setBitrate(bitrate);
            }
            if (resolution != null && !resolution.trim().isEmpty()) {
                stream.setResolution(resolution);
            }

            streamRepository.save(stream);
            System.out.println("🔄 Stream metadata updated: " + streamKey);
            return true;
        }
        return false;
    }

    /**
     * Calculate stream duration
     */
    private String calculateDuration(Stream stream) {
        if (stream.getStartedAt() == null) {
            return "00:00:00";
        }

        LocalDateTime endTime = stream.getEndedAt() != null ? stream.getEndedAt() : LocalDateTime.now();
        Duration duration = Duration.between(stream.getStartedAt(), endTime);

        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();

        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
