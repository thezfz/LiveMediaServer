package com.example.livemediaserver.repository;

import com.example.livemediaserver.model.Stream;
import com.example.livemediaserver.model.StreamStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository interface for Stream entities
 */
@Repository
public interface StreamRepository extends JpaRepository<Stream, String> {

    /**
     * Find streams by status
     */
    List<Stream> findByStatus(StreamStatus status);

    /**
     * Find streams by multiple statuses
     */
    List<Stream> findByStatusIn(List<StreamStatus> statuses);

    /**
     * Find live streams
     */
    @Query("SELECT s FROM Stream s WHERE s.status = 'LIVE'")
    List<Stream> findLiveStreams();

    /**
     * Find streams created after a specific date
     */
    List<Stream> findByCreatedAtAfter(LocalDateTime date);

    /**
     * Find streams by name containing (case insensitive)
     */
    List<Stream> findByNameContainingIgnoreCase(String name);

    /**
     * Count streams by status
     */
    long countByStatus(StreamStatus status);

    /**
     * Find streams that have been live for more than specified minutes
     */
    @Query("SELECT s FROM Stream s WHERE s.status = 'LIVE' AND s.startedAt < :cutoffTime")
    List<Stream> findLongRunningStreams(LocalDateTime cutoffTime);
}
