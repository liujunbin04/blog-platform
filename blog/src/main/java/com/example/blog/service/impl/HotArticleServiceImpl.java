package com.example.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.blog.entity.Article;
import com.example.blog.entity.User;
import com.example.blog.mapper.ArticleMapper;
import com.example.blog.service.AuthorAssembler;
import com.example.blog.service.HotArticleService;
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
    public static final String HOT_KEY_TEMP = HOT_KEY + ":building";

    /** ZSet 只保留浏览量最高的前 N 篇 */
    public static final int HOT_TOP_N = 100;

    private final StringRedisTemplate stringRedisTemplate;
    private final ArticleMapper articleMapper;
    private final AuthorAssembler authorAssembler;

    @Override
    public void refreshHotArticles() {
        List<Article> top = loadTopArticles();
        stringRedisTemplate.delete(HOT_KEY_TEMP);
        for (Article article : top) {
            stringRedisTemplate.opsForZSet().add(
                    HOT_KEY_TEMP,
                    String.valueOf(article.getId()),
                    article.getViewCount().doubleValue()
            );
        }
        if (top.isEmpty()) {
            stringRedisTemplate.delete(HOT_KEY);
        } else {
            stringRedisTemplate.rename(HOT_KEY_TEMP, HOT_KEY);
        }
        log.info("热门文章刷新完成，共 {} 篇", top.size());
    }

    protected List<Article> loadTopArticles() {
        return articleMapper.selectList(publicOrderedByViews()
                .last("LIMIT " + HOT_TOP_N)
        );
    }

    @Override
    public List<Long> getHotArticleIds(int topN) {
        int candidateSize = Math.max(topN, topN * 3);
        Set<String> ids = stringRedisTemplate.opsForZSet()
                .reverseRange(HOT_KEY, 0, candidateSize - 1L);
        if (ids == null || ids.isEmpty()) {
            // 兜底：定时任务还没跑过（或正在重建），直接回源数据库
            return articleMapper.selectList(publicOrderedByViews()
                    .last("LIMIT " + topN)
            ).stream().map(Article::getId).toList();
        }
        return ids.stream().map(Long::valueOf).toList();
    }

    @Override
    public long countHotArticles() {
        Long count = stringRedisTemplate.opsForZSet().zCard(HOT_KEY);
        if (count != null && count > 0) {
            return count;
        }
        return articleMapper.selectCount(new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, 1)
                .eq(Article::getHidden, 0));
    }

    @Override
    public List<ArticleVO> getHotArticles(int topN) {
        List<Long> ids = getHotArticleIds(topN);
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        // listByIds 不保序：建 Map 索引，按 ZSet 顺序组装（见收藏列表同款处理）
        Map<Long, Article> articleMap = articleMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(Article::getId, Function.identity()));
        Map<Long, User> userMap = authorAssembler.loadUsers(
                articleMap.values().stream().map(Article::getAuthorId).toList()
        );

        List<ArticleVO> list = new ArrayList<>();
        for (Long id : ids) {
            if (list.size() >= topN) {
                break;
            }
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

    /**
     * 公开文章按浏览量倒序的公共查询条件，供热门榜构建与回源复用。
     */
    private LambdaQueryWrapper<Article> publicOrderedByViews() {
        return new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, 1)
                .eq(Article::getHidden, 0)
                .orderByDesc(Article::getViewCount);
    }
}
