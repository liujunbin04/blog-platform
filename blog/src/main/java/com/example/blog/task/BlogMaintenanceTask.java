package com.example.blog.task;

import com.example.blog.service.HotArticleService;
import com.example.blog.service.ViewCountService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 统一维护任务：先回写浏览量，再基于最新快照刷新热门榜。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlogMaintenanceTask {

    private final ViewCountService viewCountService;
    private final HotArticleService hotArticleService;

    @Scheduled(cron = "0 */5 * * * ?")
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
