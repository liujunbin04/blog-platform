package com.example.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.blog.config.HotScoreProperties;
import com.example.blog.dto.HotScoreRow;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 热门文章服务实现
 * <p>ZSet key = {@code hot:articles}，member = 文章 id，score = hot_score。</p>
 * <p><b>两条写路径</b>：</p>
 * <ol>
 *   <li>增量（主导）：点赞/评论/收藏事件经 MQ 到达 {@code HotScoreConsumer}，调用
 *       {@link #addScore} 做 ZINCRBY；浏览量走 Redis 计数时直接加分，不经过 MQ
 *       （它是全站最高频的写入，过 MQ 只会增加延迟和队列压力）</li>
 *   <li>校正（兜底）：每 30 分钟从数据库按公式全量重算并原子替换。
 *       增量必然漂移（消息丢失、消费失败、Redis 重启丢 AOF 尾部），
 *       只靠增量会在运行一段时间后彻底错位</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotArticleServiceImpl implements HotArticleService {

    public static final String HOT_KEY = "hot:articles";
    public static final String HOT_KEY_TEMP = HOT_KEY + ":building";

    /** ZSet 只保留热度最高的前 N 篇 */
    public static final int HOT_TOP_N = 100;

    private final StringRedisTemplate stringRedisTemplate;
    private final ArticleMapper articleMapper;
    private final AuthorAssembler authorAssembler;
    private final HotScoreProperties hotScoreProperties;

    @Override
    public void refreshHotArticles() {
        List<HotScoreRow> rows = articleMapper.selectHotScores(
                hotScoreProperties.getViewWeight(),
                hotScoreProperties.getLikeWeight(),
                hotScoreProperties.getCommentWeight(),
                hotScoreProperties.getFavoriteWeight(),
                LocalDateTime.now().minusDays(hotScoreProperties.getWindowDays()),
                HOT_TOP_N
        );
        stringRedisTemplate.delete(HOT_KEY_TEMP);
        for (HotScoreRow row : rows) {
            stringRedisTemplate.opsForZSet().add(
                    HOT_KEY_TEMP,
                    String.valueOf(row.getArticleId()),
                    row.getScore()
            );
        }
        if (rows.isEmpty()) {
            stringRedisTemplate.delete(HOT_KEY);
        } else {
            // 原子的「删旧 + 换新」：读方不会看到半成品榜单。
            // 代价是毫秒级的窗口内，恰好落在这期间的 ZINCRBY 会随临时 key 一起消失；
            // 30 分钟才发生一次，且下一次重建会校正，不做额外加锁
            stringRedisTemplate.rename(HOT_KEY_TEMP, HOT_KEY);
        }
        log.info("热门文章重建完成，共 {} 篇（窗口 {} 天）", rows.size(), hotScoreProperties.getWindowDays());
    }

    @Override
    public void addScore(Long articleId, double delta) {
        if (articleId == null || delta == 0) {
            return;
        }
        stringRedisTemplate.opsForZSet()
                .incrementScore(HOT_KEY, String.valueOf(articleId), delta);
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
        Long dbCount = articleMapper.selectCount(new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, 1)
                .eq(Article::getHidden, 0));
        // COUNT 理论不会为 null，这里保持与原链式 count() 一致的兜底，避免拆箱异常
        return dbCount == null ? 0L : dbCount;
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
            // 下面这些过滤条件必须有：增量加分会把窗口外的老文章（或刚被隐藏的文章）
            // 写进 ZSet，它们要等到下一次重建才会被裁掉。这里按当前状态再筛一遍，
            // 避免「半年前的文章被点一次赞就挂上榜单 30 分钟」
            if (article == null
                    || article.getStatus() == null || article.getStatus() != 1
                    || (article.getHidden() != null && article.getHidden() == 1)
                    || article.getCreateTime() == null
                    || article.getCreateTime().isBefore(
                            LocalDateTime.now().minusDays(hotScoreProperties.getWindowDays()))) {
                continue;
            }
            list.add(ArticleVO.from(article, userMap.get(article.getAuthorId())));
        }
        return list;
    }

    /**
     * 公开文章按浏览量倒序的公共查询条件，供 ZSet 为空时回源复用
     */
    private LambdaQueryWrapper<Article> publicOrderedByViews() {
        return new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, 1)
                .eq(Article::getHidden, 0)
                .orderByDesc(Article::getViewCount);
    }
}