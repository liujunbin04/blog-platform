package com.example.blog.task;

import com.example.blog.service.HotArticleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时任务：每 5 分钟刷新一次热门文章 ZSet
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotArticleTask {

    private final HotArticleService hotArticleService;

    /**
     * cron：第 0 秒执行，每 5 分钟一次（12:00、12:05、12:10 ...）
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void refreshHotArticles() {
        try {
            hotArticleService.refreshHotArticles();
        } catch (Exception e) {
            // 定时任务抛异常会被调度器中止，必须兜住
            log.error("热门文章定时刷新失败", e);
        }
    }
}
