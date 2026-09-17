package com.example.blog.service.impl;

import com.example.blog.mapper.ArticleMapper;
import com.example.blog.service.ViewCountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 浏览量计数实现：Redis 存全量值，定时回写 MySQL
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ViewCountServiceImpl implements ViewCountService {

    private static final String KEY_PREFIX = "blog:view:count:";

    private final StringRedisTemplate stringRedisTemplate;

    private final ArticleMapper articleMapper;

    @Override
    public long incrementAndGet(Long articleId, int dbBaseline) {
        String key = KEY_PREFIX + articleId;
        // key 不存在时（首次访问、或 Redis 丢过数据）用数据库当前值初始化。
        // setIfAbsent 保证并发下只有一个线程能写基线：
        // 若用 GET 判空再 SET，两个线程可能同时读到空、各自写一次基线，后写的会把先写的覆盖掉
        stringRedisTemplate.opsForValue().setIfAbsent(key, String.valueOf(dbBaseline));
        Long value = stringRedisTemplate.opsForValue().increment(key);
        return value == null ? dbBaseline : value;
    }

    @Override
    public int flushToDatabase() {
        List<String> keys = scanKeys();
        if (keys.isEmpty()) {
            log.debug("浏览量回写：无待回写计数，跳过");
            return 0;
        }
        int flushed = 0;
        for (String key : keys) {
            Long articleId = parseId(key);
            String raw = stringRedisTemplate.opsForValue().get(key);
            if (articleId == null || raw == null) {
                continue;
            }
            try {
                // 用绝对值覆盖而非累加：Redis 里是全量值，重复回写不会重复计数
                articleMapper.updateViewCount(articleId, Long.parseLong(raw));
                flushed++;
            } catch (NumberFormatException e) {
                log.warn("浏览量回写跳过非法值: key={} value={}", key, raw);
            } catch (Exception e) {
                // 单篇失败不能中断整批，否则后面的文章永远刷不回去
                log.warn("浏览量回写失败: articleId={} 错误={}", articleId, e.getMessage());
            }
        }
        if (flushed > 0) {
            log.info("浏览量回写 MySQL 完成，共 {} 篇", flushed);
        }
        return flushed;
    }

    @Override
    public Map<Long, Long> snapshot() {
        Map<Long, Long> values = new LinkedHashMap<>();
        for (String key : scanKeys()) {
            Long articleId = parseId(key);
            String raw = stringRedisTemplate.opsForValue().get(key);
            if (articleId == null || raw == null) {
                continue;
            }
            try {
                values.put(articleId, Long.parseLong(raw));
            } catch (NumberFormatException e) {
                log.warn("跳过非法浏览量值: key={} value={}", key, raw);
            }
        }
        return values;
    }

    @Override
    public void remove(Long articleId) {
        stringRedisTemplate.delete(KEY_PREFIX + articleId);
    }

    /**
     * 扫描浏览量 key
     * <p>用 SCAN 而不是 KEYS：KEYS 会一次性遍历并阻塞 Redis 单线程，
     * key 一多就会拖垮整个实例。SCAN 分批游标式遍历，不会长时间阻塞。</p>
     */
    private List<String> scanKeys() {
        ScanOptions options = ScanOptions.scanOptions()
                .match(KEY_PREFIX + "*")
                .count(500)
                .build();
        // 用 execute(RedisCallback) 而不是自己 getConnection()：
        // 回调形式由模板负责借还连接，不会漏关；且走 keyCommands() 是官方推荐入口，
        // 直接调 connection.scan() 在新版 Spring Data Redis 中已弃用
        return stringRedisTemplate.execute((RedisCallback<List<String>>) connection -> {
            List<String> keys = new ArrayList<>();
            try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                cursor.forEachRemaining(b -> keys.add(new String(b, StandardCharsets.UTF_8)));
            }
            return keys;
        });
    }

    private Long parseId(String key) {
        try {
            return Long.parseLong(key.substring(KEY_PREFIX.length()));
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            return null;
        }
    }
}
