package com.example.blog.task;

import com.example.blog.service.ViewCountService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时把 Redis 里累积的浏览量回写 MySQL
 * <p>
 * 详情页访问时只写 Redis，不碰数据库；这里负责定期落盘，
 * 让 MySQL 里的 view_count 最终追上 Redis。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ViewCountFlushTask {

    private final ViewCountService viewCountService;

    /**
     * 每 5 分钟回写一次
     * <p>间隔是权衡结果：浏览量不是关键业务数据，丢了无感知；
     * 而回写是批量 UPDATE，5 分钟一次对数据库几乎没有压力。</p>
     */
    @Scheduled(cron = "0 0/5 * * * ?")
    public void flush() {
        try {
            viewCountService.flushToDatabase();
        } catch (Exception e) {
            // 定时任务抛异常会导致后续调度全部中止，必须兜住
            log.error("浏览量定时回写失败: {}", e.getMessage());
        }
    }

    /**
     * 停机前回写一次，避免最后几分钟的浏览量丢失
     */
    @PreDestroy
    public void flushOnShutdown() {
        try {
            int n = viewCountService.flushToDatabase();
            log.info("停机前浏览量回写完成，共 {} 篇", n);
        } catch (Exception e) {
            log.warn("停机前浏览量回写失败: {}", e.getMessage());
        }
    }
}
