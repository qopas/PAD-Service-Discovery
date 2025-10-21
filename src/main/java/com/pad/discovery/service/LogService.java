package com.pad.discovery.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing and retrieving application logs.
 */
@Service
@Slf4j
public class LogService {
    
    @Value("${logging.file.name:logs/service-discovery.log}")
    private String logFilePath;
    
    private static final DateTimeFormatter FILENAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    
    /**
     * Get the log file as a resource for download
     * 
     * @return Resource containing the log file
     * @throws IOException if log file cannot be read
     */
    public Resource getLogFile() throws IOException {
        File logFile = new File(logFilePath);
        
        if (!logFile.exists()) {
            log.warn("Log file not found at: {}", logFilePath);
            throw new IOException("Log file not found");
        }
        
        log.info("Preparing log file for download: {}", logFilePath);
        return new FileSystemResource(logFile);
    }
    
    /**
     * Get recent log entries (last N lines)
     * 
     * @param lines Number of recent lines to retrieve
     * @return List of log lines
     */
    public List<String> getRecentLogs(int lines) {
        try {
            Path path = Paths.get(logFilePath);
            if (!Files.exists(path)) {
                log.warn("Log file not found at: {}", logFilePath);
                return Collections.emptyList();
            }
            
            List<String> allLines = Files.readAllLines(path);
            int startIndex = Math.max(0, allLines.size() - lines);
            
            log.debug("Retrieved {} recent log lines from {}", lines, logFilePath);
            return allLines.subList(startIndex, allLines.size());
            
        } catch (IOException e) {
            log.error("Error reading log file: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * Get log file name for download
     * 
     * @return Generated filename with timestamp
     */
    public String getLogFileName() {
        String timestamp = LocalDateTime.now().format(FILENAME_FORMATTER);
        return String.format("service-discovery-logs_%s.log", timestamp);
    }
    
    /**
     * Get log file size in bytes
     * 
     * @return File size or -1 if file doesn't exist
     */
    public long getLogFileSize() {
        try {
            File logFile = new File(logFilePath);
            return logFile.exists() ? logFile.length() : -1;
        } catch (Exception e) {
            log.error("Error getting log file size: {}", e.getMessage());
            return -1;
        }
    }
}

