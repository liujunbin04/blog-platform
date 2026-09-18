package com.example.blog.task;

import com.example.blog.entity.Article;
import com.example.blog.entity.ArticleDocument;
import com.example.blog.repository.ArticleSearchRepository;
import com.example.blog.service.ArticleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
     * 应用启动完成后全量回填 ES 索引（saveAll 为按 id 的幂等 upsert）：
     * 增量任务只覆盖「最近 6 分钟更新」的文章，发布当时未同步成功、
     * 或后端停机期间错过的文章，若不回填将永远搜不到（索引残缺，
     * 表现为任何关键词都搜不出结果）。回填以数据库为准，可修复一切索引漂移
     */
    @EventListener(ApplicationReadyEvent.class)
    public void backfillSearchIndex() {
        try {
            reconcileSearchVisibility();
            List<Article> articles = articleService.lambdaQuery()
                    .eq(Article::getStatus, 1)
                    .eq(Article::getHidden, 0)
                    .list();
            if (articles.isEmpty()) {
                log.info("ES 全量回填：无已发布文章，跳过");
                return;
            }
            List<ArticleDocument> documents = articles.stream()
                    .map(ArticleDocument::from)
                    .toList();
            articleSearchRepository.saveAll(documents);
            log.info("ES 全量回填完成，共 {} 篇", documents.size());
        } catch (Exception e) {
            // ES 未启动 / 索引建不出来时不能阻断应用启动：搜索功能降级，其余功能不受影响
            log.warn("ES 全量回填失败，搜索功能暂不可用（不影响其他功能）: {}", e.getMessage());
        }
    }

    private void reconcileSearchVisibility() {
        articleSearchRepository.findAll().forEach(document -> {
            Article article = articleService.getById(document.getId());
            if (!isPublic(article)) {
                articleSearchRepository.deleteById(document.getId());
            }
        });
    }

    private boolean isPublic(Article article) {
        return article != null
                && article.getStatus() != null
                && article.getStatus() == 1
                && article.getHidden() != null
                && article.getHidden() == 0;
    }

    /**
     * 每 5 分钟第 30 秒执行（12:00:30、12:05:30 ...），
     * 与热门文章任务（整点第 0 秒）错开 30 秒，减少数据库/ES 争抢
     */
    @Scheduled(cron = "30 0/5 * * * ?")
    public void syncRecentArticles() {
        try {
            // 查询最近 6 分钟更新过的公开文章（已发布且未隐藏）。
            // 边界说明：任务间隔 5 分钟、窗口放宽到 6 分钟留 1 分钟重叠，
            // 消除恰好落在边界上的更新被漏同步的风险（代价是极少量文章被重复写入 ES，幂等无害）
            List<Article> articles = articleService.lambdaQuery()
                    .eq(Article::getStatus, 1)
                    .eq(Article::getHidden, 0)
                    .ge(Article::getUpdateTime, LocalDateTime.now().minusMinutes(6))
                    .list();
            if (articles.isEmpty()) {
                log.debug("近 6 分钟无文章更新，跳过 ES 同步");
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
