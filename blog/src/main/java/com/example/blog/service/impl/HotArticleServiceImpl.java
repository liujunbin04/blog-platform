package com.example.blog.service.impl;

import com.example.blog.entity.Article;
import com.example.blog.entity.User;
import com.example.blog.service.ArticleService;
import com.example.blog.service.HotArticleService;
import com.example.blog.service.UserService;
import com.example.blog.vo.ArticleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 热门文章服务实现
 * <p>ZSet key = {@code hot:articles}，member = 文章 id，score = 浏览量。
 * 读取顺序用 ZREVRANGE（score 倒序）。
 * 设计文档中的热度公式（view×1 + like×5 + comment×10 + favorite×8）
 * 可在 refreshHotArticles 里替换 score 计算</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotArticleServiceImpl implements HotArticleService {

    public static final String HOT_KEY = "hot:articles";

    /** ZSet 只保留浏览量最高的前 N 篇 */
    public static final int HOT_TOP_N = 100;

    private final StringRedisTemplate stringRedisTemplate;
    private final ArticleService articleService;
    private final UserService userService;

    @Override
    public void refreshHotArticles() {
        // 查浏览量 Top N 的公开文章（已发布且未隐藏；MP 自动过滤 deleted=0）
        List<Article> top = articleService.lambdaQuery()
                .eq(Article::getStatus, 1)
                .eq(Article::getHidden, 0)
                .orderByDesc(Article::getViewCount)
                .last("LIMIT " + HOT_TOP_N)
                .list();
        // DEL + ZADD 重建。窗口期读到空 ZSet 也没关系：
        // 读取侧 getHotArticleIds 对空 ZSet 有回源数据库兜底
        stringRedisTemplate.delete(HOT_KEY);
        for (Article article : top) {
            stringRedisTemplate.opsForZSet().add(
                    HOT_KEY,
                    String.valueOf(article.getId()),
                    article.getViewCount().doubleValue()
            );
        }
        log.info("热门文章刷新完成，共 {} 篇", top.size());
    }

    @Override
    public List<Long> getHotArticleIds(int topN) {
        Set<String> ids = stringRedisTemplate.opsForZSet().reverseRange(HOT_KEY, 0, topN - 1L);
        if (ids == null || ids.isEmpty()) {
            // 兜底：定时任务还没跑过（或正在重建），直接回源数据库
            return articleService.lambdaQuery()
                    .eq(Article::getStatus, 1)
                    .eq(Article::getHidden, 0)
                    .orderByDesc(Article::getViewCount)
                    .last("LIMIT " + topN)
                    .list().stream().map(Article::getId).toList();
        }
        return ids.stream().map(Long::valueOf).toList();
    }

    @Override
    public List<ArticleVO> getHotArticles(int topN) {
        List<Long> ids = getHotArticleIds(topN);
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        // listByIds 不保序：建 Map 索引，按 ZSet 顺序组装（见收藏列表同款处理）
        Map<Long, Article> articleMap = articleService.listByIds(ids).stream()
                .collect(Collectors.toMap(Article::getId, Function.identity()));
        Set<Long> authorIds = articleMap.values().stream()
                .map(Article::getAuthorId)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = authorIds.isEmpty() ? Collections.emptyMap()
                : userService.listByIds(authorIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));

        List<ArticleVO> list = new ArrayList<>();
        for (Long id : ids) {
            Article article = articleMap.get(id);
            // 文章在两次查询间被删除则跳过；已隐藏的文章在 ZSet 刷新间隙
            // （最长 5 分钟）可能残留，同样跳过
            if (article == null
                    || article.getStatus() == null || article.getStatus() != 1
                    || (article.getHidden() != null && article.getHidden() == 1)) {
                continue;
            }
            list.add(ArticleVO.from(article, userMap.get(article.getAuthorId())));
        }
        return list;
    }
}
