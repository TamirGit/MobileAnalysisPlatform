package com.mobileanalysis.orchestrator.service;

import com.mobileanalysis.common.domain.FileType;
import com.mobileanalysis.orchestrator.domain.AnalysisConfigEntity;
import com.mobileanalysis.orchestrator.domain.TaskConfigEntity;
import com.mobileanalysis.orchestrator.exception.ConfigNotFoundException;
import com.mobileanalysis.orchestrator.repository.AnalysisConfigRepository;
import com.mobileanalysis.orchestrator.repository.TaskConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service for loading and caching analysis configurations.
 * Implements cache-aside pattern: try cache first, fallback to DB, then populate cache.
 * <p>
 * Database is source of truth, Redis is best-effort cache.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ConfigurationService {
    
    private static final String CACHE_KEY_PREFIX = "analysis-config:";
    
    private final AnalysisConfigRepository configRepository;
    private final TaskConfigRepository taskConfigRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    
    /**
     * Get analysis configuration for a file type.
     * First checks Redis cache, then falls back to database if cache miss.
     * 
     * @param fileType APK or IPA
     * @return AnalysisConfigEntity with associated task configurations
     * @throws ConfigNotFoundException if configuration not found
     */
    @Transactional(readOnly = true)
    public AnalysisConfigEntity getAnalysisConfig(FileType fileType) {
        String cacheKey = CACHE_KEY_PREFIX + fileType.name();
        
        // Try cache first
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof AnalysisConfigEntity) {
                log.debug("Configuration for {} found in cache", fileType);
                return (AnalysisConfigEntity) cached;
            }
        } catch (Exception e) {
            log.warn("Redis cache read failed for {}, falling back to database", fileType, e);
            // Continue to database lookup
        }
        
        // Cache miss - read from database
        log.debug("Cache miss for {}, loading from database", fileType);
        AnalysisConfigEntity config = configRepository.findByFileType(fileType)
            .orElseThrow(() -> new ConfigNotFoundException(
                String.format("No analysis configuration found for file type: %s", fileType)));
        
        // Load associated task configurations
        List<TaskConfigEntity> tasks = taskConfigRepository
            .findByAnalysisConfigIdOrderByTaskOrder(config.getId());
        config.setTasks(tasks);
        
        // Populate cache (best-effort, no TTL for config data)
        try {
            redisTemplate.opsForValue().set(cacheKey, config);
            log.info("Configuration for {} loaded from database and cached", fileType);
        } catch (Exception e) {
            log.warn("Failed to cache configuration for {}, will work without cache", fileType, e);
            // Don't throw - database is source of truth, cache failure is acceptable
        }
        
        return config;
    }
    
    /**
     * Invalidate cache for a specific file type.
     * Used when configuration is updated.
     * 
     * @param fileType File type to invalidate
     */
    public void invalidateCache(FileType fileType) {
        String cacheKey = CACHE_KEY_PREFIX + fileType.name();
        try {
            redisTemplate.delete(cacheKey);
            log.info("Cache invalidated for file type: {}", fileType);
        } catch (Exception e) {
            log.warn("Failed to invalidate cache for {}", fileType, e);
        }
    }
    
    /**
     * Clear all configuration cache entries using SCAN command.
     * SCAN is non-blocking and safe for production use unlike KEYS.
     * Useful for bulk configuration updates.
     */
    public void clearAllCache() {
        try {
            Set<String> keys = new HashSet<>();
            
            // Use SCAN instead of KEYS for non-blocking iteration
            redisTemplate.execute((RedisCallback<Set<String>>) connection -> {
                ScanOptions options = ScanOptions.scanOptions()
                    .match(CACHE_KEY_PREFIX + "*")
                    .count(100) // Hint for iteration batch size
                    .build();
                
                Cursor<byte[]> cursor = connection.scan(options);
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next()));
                }
                return keys;
            });
            
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("All configuration cache entries cleared: {} keys deleted", keys.size());
            } else {
                log.info("No configuration cache entries found to clear");
            }
        } catch (Exception e) {
            log.warn("Failed to clear configuration cache", e);
        }
    }
}
