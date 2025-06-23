package com.example.livemediaserver.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity representing a live stream
 */
@Entity
@Table(name = "streams")
public class Stream {
    
    @Id
    private String id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(name = "rtmp_url")
    private String rtmpUrl;
    
    @Column(name = "hls_url")
    private String hlsUrl;
    
    @Enumerated(EnumType.STRING)
    private StreamStatus status;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "started_at")
    private LocalDateTime startedAt;
    
    @Column(name = "ended_at")
    private LocalDateTime endedAt;
    
    @Column(name = "viewer_count")
    private Integer viewerCount = 0;
    
    @Column(name = "bitrate")
    private Long bitrate;
    
    @Column(name = "resolution")
    private String resolution;
    
    @Column(name = "transcoding_enabled")
    private Boolean transcodingEnabled = false;

    // Constructors
    public Stream() {
        this.createdAt = LocalDateTime.now();
        this.status = StreamStatus.CREATED;
    }

    public Stream(String id, String name) {
        this();
        this.id = id;
        this.name = name;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRtmpUrl() {
        return rtmpUrl;
    }

    public void setRtmpUrl(String rtmpUrl) {
        this.rtmpUrl = rtmpUrl;
    }

    public String getHlsUrl() {
        return hlsUrl;
    }

    public void setHlsUrl(String hlsUrl) {
        this.hlsUrl = hlsUrl;
    }

    public StreamStatus getStatus() {
        return status;
    }

    public void setStatus(StreamStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(LocalDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public Integer getViewerCount() {
        return viewerCount;
    }

    public void setViewerCount(Integer viewerCount) {
        this.viewerCount = viewerCount;
    }

    public Long getBitrate() {
        return bitrate;
    }

    public void setBitrate(Long bitrate) {
        this.bitrate = bitrate;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    public Boolean getTranscodingEnabled() {
        return transcodingEnabled;
    }

    public void setTranscodingEnabled(Boolean transcodingEnabled) {
        this.transcodingEnabled = transcodingEnabled;
    }

    // Utility methods
    public void start() {
        this.status = StreamStatus.LIVE;
        this.startedAt = LocalDateTime.now();
    }

    public void stop() {
        this.status = StreamStatus.ENDED;
        this.endedAt = LocalDateTime.now();
    }

    public boolean isLive() {
        return this.status == StreamStatus.LIVE;
    }

    @Override
    public String toString() {
        return "Stream{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", status=" + status +
                ", viewerCount=" + viewerCount +
                '}';
    }
}
