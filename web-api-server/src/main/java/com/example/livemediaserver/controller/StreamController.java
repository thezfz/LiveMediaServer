package com.example.livemediaserver.controller;

import com.example.livemediaserver.model.Stream;
import com.example.livemediaserver.service.StreamService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * REST Controller for stream management operations
 * 
 * Provides endpoints for:
 * - Listing active streams
 * - Getting stream details
 * - Managing stream lifecycle
 * - Serving HLS playlists and segments
 */
@RestController
@RequestMapping("/streams")
@CrossOrigin(origins = "*") // Allow CORS for frontend integration
public class StreamController {

    @Autowired
    private StreamService streamService;

    /**
     * Get all active streams
     */
    @GetMapping
    public ResponseEntity<List<Stream>> getAllStreams() {
        List<Stream> streams = streamService.getAllActiveStreams();
        return ResponseEntity.ok(streams);
    }

    /**
     * Get specific stream by ID
     */
    @GetMapping("/{streamId}")
    public ResponseEntity<Stream> getStream(@PathVariable String streamId) {
        Optional<Stream> stream = streamService.getStreamById(streamId);
        return stream.map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get stream statistics
     */
    @GetMapping("/{streamId}/stats")
    public ResponseEntity<Object> getStreamStats(@PathVariable String streamId) {
        Optional<Object> stats = streamService.getStreamStats(streamId);
        return stats.map(ResponseEntity::ok)
                   .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Start transcoding for a stream
     */
    @PostMapping("/{streamId}/transcode")
    public ResponseEntity<String> startTranscoding(@PathVariable String streamId) {
        boolean success = streamService.startTranscoding(streamId);
        if (success) {
            return ResponseEntity.ok("Transcoding started for stream: " + streamId);
        } else {
            return ResponseEntity.badRequest().body("Failed to start transcoding for stream: " + streamId);
        }
    }

    /**
     * Stop a stream
     */
    @DeleteMapping("/{streamId}")
    public ResponseEntity<String> stopStream(@PathVariable String streamId) {
        boolean success = streamService.stopStream(streamId);
        if (success) {
            return ResponseEntity.ok("Stream stopped: " + streamId);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Serve HLS playlist (.m3u8 file)
     */
    @GetMapping("/{streamId}/playlist.m3u8")
    public ResponseEntity<String> getHlsPlaylist(@PathVariable String streamId) {
        Optional<String> playlist = streamService.getHlsPlaylist(streamId);
        return playlist.map(content -> ResponseEntity.ok()
                          .header("Content-Type", "application/vnd.apple.mpegurl")
                          .body(content))
                      .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Serve HLS segments (.ts files)
     */
    @GetMapping("/{streamId}/{segmentName}.ts")
    public ResponseEntity<byte[]> getHlsSegment(
            @PathVariable String streamId,
            @PathVariable String segmentName) {
        Optional<byte[]> segment = streamService.getHlsSegment(streamId, segmentName);
        return segment.map(content -> ResponseEntity.ok()
                         .header("Content-Type", "video/mp2t")
                         .body(content))
                     .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Handle stream start event from RTMP server
     */
    @PostMapping("/start")
    public ResponseEntity<Object> handleStreamStart(@RequestBody java.util.Map<String, Object> payload) {
        try {
            String streamKey = (String) payload.get("streamKey");
            String clientIp = (String) payload.get("clientIp");
            String timestamp = (String) payload.get("timestamp");

            System.out.println("🎬 Received stream start event:");
            System.out.println("   Stream Key: " + streamKey);
            System.out.println("   Client IP: " + clientIp);
            System.out.println("   Timestamp: " + timestamp);

            if (streamKey == null || streamKey.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(java.util.Map.of(
                    "success", false,
                    "message", "Stream key is required"
                ));
            }

            // Create or update stream record
            Stream stream = streamService.createOrUpdateStream(streamKey, clientIp);

            System.out.println("✅ Stream record created/updated: " + stream.getId());

            return ResponseEntity.ok(java.util.Map.of(
                "success", true,
                "message", "Stream started successfully",
                "streamId", stream.getId(),
                "status", stream.getStatus().toString()
            ));

        } catch (Exception e) {
            System.err.println("❌ Error handling stream start: " + e.getMessage());
            e.printStackTrace();

            return ResponseEntity.internalServerError().body(java.util.Map.of(
                "success", false,
                "message", "Internal server error: " + e.getMessage()
            ));
        }
    }

    /**
     * Handle stream stop event from RTMP server
     */
    @PostMapping("/stop")
    public ResponseEntity<Object> handleStreamStop(@RequestBody java.util.Map<String, Object> payload) {
        try {
            String streamKey = (String) payload.get("streamKey");
            String timestamp = (String) payload.get("timestamp");

            System.out.println("🛑 Received stream stop event:");
            System.out.println("   Stream Key: " + streamKey);
            System.out.println("   Timestamp: " + timestamp);

            if (streamKey == null || streamKey.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(java.util.Map.of(
                    "success", false,
                    "message", "Stream key is required"
                ));
            }

            // Stop stream
            boolean success = streamService.stopStreamByKey(streamKey);

            if (success) {
                System.out.println("✅ Stream stopped successfully: " + streamKey);
                return ResponseEntity.ok(java.util.Map.of(
                    "success", true,
                    "message", "Stream stopped successfully",
                    "streamKey", streamKey
                ));
            } else {
                System.out.println("⚠️ Stream not found or already stopped: " + streamKey);
                return ResponseEntity.ok(java.util.Map.of(
                    "success", true,
                    "message", "Stream not found or already stopped",
                    "streamKey", streamKey
                ));
            }

        } catch (Exception e) {
            System.err.println("❌ Error handling stream stop: " + e.getMessage());
            e.printStackTrace();

            return ResponseEntity.internalServerError().body(java.util.Map.of(
                "success", false,
                "message", "Internal server error: " + e.getMessage()
            ));
        }
    }

    /**
     * Handle stream metadata update event from RTMP server
     */
    @PostMapping("/update")
    public ResponseEntity<Object> handleStreamUpdate(@RequestBody java.util.Map<String, Object> payload) {
        try {
            String streamKey = (String) payload.get("streamKey");
            Long bitrate = payload.get("bitrate") != null ?
                Long.valueOf(payload.get("bitrate").toString()) : null;
            String resolution = (String) payload.get("resolution");

            System.out.println("🔄 Received stream update event:");
            System.out.println("   Stream Key: " + streamKey);
            System.out.println("   Bitrate: " + bitrate);
            System.out.println("   Resolution: " + resolution);

            if (streamKey == null || streamKey.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(java.util.Map.of(
                    "success", false,
                    "message", "Stream key is required"
                ));
            }

            // Update stream metadata
            boolean success = streamService.updateStreamMetadata(streamKey, bitrate, resolution);

            if (success) {
                System.out.println("✅ Stream metadata updated: " + streamKey);
                return ResponseEntity.ok(java.util.Map.of(
                    "success", true,
                    "message", "Stream metadata updated successfully"
                ));
            } else {
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            System.err.println("❌ Error handling stream update: " + e.getMessage());
            e.printStackTrace();

            return ResponseEntity.internalServerError().body(java.util.Map.of(
                "success", false,
                "message", "Internal server error: " + e.getMessage()
            ));
        }
    }
}
