package com.example.livemediaserver.model;

/**
 * Enumeration representing the various states of a live stream
 */
public enum StreamStatus {
    /**
     * Stream has been created but not yet started
     */
    CREATED,
    
    /**
     * Stream is currently live and broadcasting
     */
    LIVE,
    
    /**
     * Stream is temporarily offline but may resume
     */
    OFFLINE,
    
    /**
     * Stream has ended and will not resume
     */
    ENDED,
    
    /**
     * Stream encountered an error
     */
    ERROR
}
