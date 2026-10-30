package com.example.blog.task;

import com.example.blog.service.HotArticleService;
import com.example.blog.service.ViewCountService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 统一维护任务：先回写浏览量，再基于最新快照校正热度榜。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlogMaintenanceTask {

    private final ViewCountService viewCountService;
    private final HotArticleService hotArticleService;

    /**
     * 启动时先建一次榜：日常分数由 MQ 事件增量维护，但 Redis 重启后 ZSet 是空的，
     * 要等到下一个 30 分钟周期才恢复
     */
    @EventListener(ApplicationReadyEvent.class)
    public void seedHotArticles() {
        try {
            hotArticleService.refreshHotArticles();
        } catch (Exception e) {
            // Redis / MySQL 尚未就绪时不能阻断启动，榜单会由下一个周期补上
            log.warn("启动时重建热度榜失败，稍后由定时任务重试: {}", e.getMessage());
        }
    }

    /**
     * 每 30 分钟执行：回写浏览量 → 按公式全量重建热度榜
     * <p>重建周期从 5 分钟放宽到 30 分钟，是因为日常分数已经由
     * {@link com.example.blog.consumer.HotScoreConsumer} 增量维护，
     * 定时任务退化为「校正漂移 + 裁剪窗口外文章」的角色。</p>
     */
    @Scheduled(cron = "0 */30 * * * ?")
    public void runMaintenance() {
        try {
            int flushed = viewCountService.flushToDatabase();
            hotArticleService.refreshHotArticles();
            log.info("统一维护完成，浏览量回写 {} 篇", flushed);
        } catch (Exception e) {
            log.error("统一维护任务失败", e);
        }
    }

    @PreDestroy
    public void flushOnShutdown() {
        try {
            viewCountService.flushToDatabase();
        } catch (Exception e) {
            log.warn("停机前浏览量回写失败: {}", e.getMessage());
        }
    }
}