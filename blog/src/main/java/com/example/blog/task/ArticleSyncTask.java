package com.example.blog.task;

import com.example.blog.entity.Article;
import com.example.blog.entity.ArticleDocument;
import com.example.blog.repository.ArticleSearchRepository;
import com.example.blog.service.ArticleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务：每 5 分钟把 MySQL 中最近更新的文章增量同步到 Elasticsearch
 * <p>注意：本任务只同步"存在"的文章（MP 自动过滤 deleted=1，已删除文章查不到）。
 * 文章删除时的 ES 清理应配合 deleteArticle 同步删除 ES 文档，否则搜索结果里
 * 会残留已删除文章</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleSyncTask {

    private final ArticleService articleService;
    private final ArticleSearchRepository articleSearchRepository;

    /**
     * 每 5 分钟第 30 秒执行（12:00:30、12:05:30 ...），
     * 与热门文章任务（整点第 0 秒）错开 30 秒，减少数据库/ES 争抢
     */
    @Scheduled(cron = "30 0/5 * * * ?")
    public void syncRecentArticles() {
        try {
            // 查询最近 5 分钟更新过的已发布文章。
            // 边界说明：窗口与任务间隔同为 5 分钟，理论上恰好落在边界上的更新
            // 可能被漏掉一次，下一轮不会补（本任务无状态）；生产环境可把窗口
            // 放宽到 6 分钟或用有状态的"上次同步时间"游标
            List<Article> articles = articleService.lambdaQuery()
                    .eq(Article::getStatus, 1)
                    .ge(Article::getUpdateTime, LocalDateTime.now().minusMinutes(5))
                    .list();
            if (articles.isEmpty()) {
                log.debug("近 5 分钟无文章更新，跳过 ES 同步");
                return;
            }
            // 转成 ES 文档批量写入（saveAll 内部走 Bulk API，一次请求写入全部）
            List<ArticleDocument> documents = articles.stream()
                    .map(ArticleDocument::from)
                    .toList();
            articleSearchRepository.saveAll(documents);
            log.info("文章同步到 ES 完成，共 {} 篇", documents.size());
        } catch (Exception e) {
            // 定时任务抛异常会被调度器中止，必须兜住
            log.error("文章同步到 ES 失败", e);
        }
    }
}
