package com.example.blog.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 缓存工具：Redisson 分布式锁防缓存击穿
 * <p>击穿场景：热点 key 过期瞬间大量请求同时查库。本方法保证同一 key
 * 同一时刻只有一个线程回源查库，其余线程等锁释放后直接读缓存。</p>
 * <p>用法（key 与 @Cacheable 同一套序列化，可混用）：</p>
 * <pre>
 * ArticleVO vo = cacheHelper.getWithLock("article", id,
 *         () -> baseMapper.selectDetailWithAuthor(id));
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheHelper {

    private final RedissonClient redissonClient;
    private final CacheManager cacheManager;

    /**
     * 读缓存 → 未命中加分布式锁 → 回源查库 → 写缓存 → 释放锁
     *
     * @param cacheName 缓存名（与 @Cacheable 的 value 一致）
     * @param key       缓存 key（与 @Cacheable 的 key 一致，如文章 id）
     * @param dbLoader  回源查库的逻辑（Supplier）
     * @return 缓存值或回源值；dbLoader 返回 null 时不写缓存
     */
    public <T> T getWithLock(String cacheName, Object key, Supplier<T> dbLoader) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            throw new IllegalArgumentException("缓存不存在: " + cacheName);
        }
        // 1. 先查缓存，命中直接返回（不碰锁，性能最好）
        T cached = getValue(cache, key);
        if (cached != null) {
            log.debug("缓存命中: {}:{}", cacheName, key);
            return cached;
        }
        // 2. 未命中：抢分布式锁。只有抢到的线程回源，其余线程阻塞等待，
        //    锁释放后走步骤 3 的二次检查直接拿缓存
        RLock lock = redissonClient.getLock("lock:" + cacheName + ":" + key);
        lock.lock();
        try {
            // 3. 双重检查：等锁期间，抢到锁的线程可能已回源并写入缓存
            cached = getValue(cache, key);
            if (cached != null) {
                log.debug("等锁期间缓存已被写入: {}:{}", cacheName, key);
                return cached;
            }
            // 4. 回源查库并写缓存（null 不缓存，与 disableCachingNullValues 一致）
            log.debug("缓存未命中，回源查库: {}:{}", cacheName, key);
            T value = dbLoader.get();
            if (value != null) {
                cache.put(key, value);
            }
            return value;
        } finally {
            // 无论成功失败都释放锁，避免死锁阻塞后续请求
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T getValue(Cache cache, Object key) {
        Cache.ValueWrapper wrapper = cache.get(key);
        return wrapper == null ? null : (T) wrapper.get();
    }
}
