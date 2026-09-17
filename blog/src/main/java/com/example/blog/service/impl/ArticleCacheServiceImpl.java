package com.example.blog.service.impl;

import com.example.blog.common.BusinessException;
import com.example.blog.mapper.ArticleMapper;
import com.example.blog.service.ArticleCacheService;
import com.example.blog.vo.ArticleVO;
import com.example.blog.utils.CacheHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * 文章内容缓存实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleCacheServiceImpl implements ArticleCacheService {

    private final ArticleMapper articleMapper;

    private final CacheHelper cacheHelper;

    /**
     * 缓存命中的时候一次数据库都不查。
     * <p>
     * unless 决定「什么情况下不写入缓存」：
     * 只有已发布且未隐藏的公开文章才缓存。草稿和隐藏文章不缓存，
     * 是为了避免未发布正文常驻 Redis——接口层虽然仍会拦截、不会返回给访客，
     * 但让未发布内容长时间躺在缓存里不合适。
     * 且这类文章访问频率极低，不缓存对性能没有影响。
     * </p>
     * <p>
     * 注意 status 常量在 SpEL 里不便引用，这里直接写 1，
     * 与 ArticleServiceImpl.STATUS_PUBLISHED 保持一致。
     * </p>
     */
    @Override
    @Cacheable(value = "article", key = "#id",
            unless = "#result == null || #result.status != 1 || (#result.hidden != null && #result.hidden == 1)")
    public ArticleVO getCached(Long id) {
        // 缓存过期瞬间的并发未命中在分布式锁上串行化：只有第一个线程回源查库，
        // 其余线程等锁释放后经二次检查直读缓存（防击穿）。
        // cacheCondition 与 unless 语义一致：草稿 / 已隐藏文章不写缓存
        ArticleVO vo = cacheHelper.getWithLock("article", id,
                () -> articleMapper.selectDetailWithAuthor(id),
                v -> v != null && v.getStatus() != null && v.getStatus() == 1
                        && (v.getHidden() == null || v.getHidden() == 0));
        if (vo == null) {
            throw new BusinessException(404, "文章不存在");
        }
        return vo;
    }

    @Override
    @CacheEvict(value = "article", key = "#id")
    public void evict(Long id) {
        log.debug("清除文章缓存: id={}", id);
    }
}
