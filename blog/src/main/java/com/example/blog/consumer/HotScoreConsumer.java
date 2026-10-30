package com.example.blog.consumer;

import com.example.blog.config.HotScoreProperties;
import com.example.blog.dto.HotScoreMessage;
import com.example.blog.service.HotArticleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 热度加分消费者：点赞/评论/收藏事件 → ZINCRBY
 * <p><b>幂等</b>：ZINCRBY 本身不幂等，重试会重复加分，所以先用
 * {@code SETNX hot:dedup:{eventKey}} 挡一道。TTL 取 2 小时（4 个重建周期）：
 * 热度是有窗口的分数，2 小时后全量重建早已校正过，没必要留更久。
 * 注意这与通知的去重不同——通知靠 {@code notification.uk_event_key} 永久去重。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotScoreConsumer {

    /** 去重键前缀，与 docs/design.md 的 Redis Key 约定一致 */
    static final String DEDUP_KEY_PREFIX = "hot:dedup:";

    /** 去重键存活 2 小时：超过后重建任务已经校正过分数 */
    static final Duration DEDUP_TTL = Duration.ofHours(2);

    private final HotArticleService hotArticleService;
    private final HotScoreProperties hotScoreProperties;
    private final StringRedisTemplate stringRedisTemplate;

    @RabbitListener(queues = "hot.score.queue")
    public void onScore(HotScoreMessage message) {
        if (message.getArticleId() == null || message.getEventKey() == null) {
            log.warn("热度事件字段不全，忽略: {}", message);
            return;
        }
        Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(
                DEDUP_KEY_PREFIX + message.getEventKey(), "1", DEDUP_TTL);
        if (!Boolean.TRUE.equals(first)) {
            log.debug("热度事件重复投递，跳过: {}", message.getEventKey());
            return;
        }
        int weight = hotScoreProperties.weightOf(message.getType());
        if (weight == 0) {
            log.warn("未知热度事件类型，忽略: {}", message.getType());
            return;
        }
        hotArticleService.addScore(message.getArticleId(), weight);
    }
}