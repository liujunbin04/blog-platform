package com.example.blog.service;

import com.example.blog.vo.ArticleVO;

/**
 * 文章内容缓存（与浏览量分离）
 * <p>
 * 为什么单独拎一个 Bean，而不是直接在 ArticleServiceImpl 上加 @Cacheable：
 * Spring 的缓存注解靠 AOP 代理生效，<b>类内部方法互相调用不会经过代理，注解会静默失效</b>。
 * 详情页的 getDetail 需要读缓存，若它直接调用本类的 getArticleById，
 * 缓存等于没配——这个坑在原 ArticleServiceImpl 的注释里也标注过。
 * 因此把带 @Cacheable 的读方法放到独立 Bean，getDetail 通过 Bean 调用才能命中代理。
 * </p>
 * <p>
 * 缓存只保留文章内容，浏览量不进缓存（由 ViewCountService 单独维护），
 * 否则每次访问都要回写整个缓存对象，退化成「每请求一次 Redis 写」。
 * </p>
 */
public interface ArticleCacheService {

    /**
     * 取文章详情（带缓存）
     *
     * @param id 文章 id
     * @return 文章详情
     * @throws com.example.blog.common.BusinessException 文章不存在时为 404
     */
    ArticleVO getCached(Long id);

    /**
     * 清除某篇文章的缓存
     */
    void evict(Long id);
}
