package com.example.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.blog.common.BusinessException;
import com.example.blog.common.PageResult;
import com.example.blog.dto.ArticleDTO;
import com.example.blog.dto.ArticlePageDTO;
import com.example.blog.entity.Article;
import com.example.blog.entity.ArticleDocument;
import com.example.blog.entity.User;
import com.example.blog.mapper.ArticleMapper;
import com.example.blog.mapper.CommentMapper;
import com.example.blog.mapper.FavoriteMapper;
import com.example.blog.mapper.LikeRecordMapper;
import com.example.blog.mapper.NotificationMapper;
import com.example.blog.repository.ArticleSearchRepository;
import com.example.blog.service.ArticleCacheService;
import com.example.blog.service.ArticleService;
import com.example.blog.service.ArticleVisibilityPolicy;
import com.example.blog.service.HotArticleService;
import com.example.blog.service.UserService;
import com.example.blog.service.ViewCountService;
import com.example.blog.utils.SecurityUtil;
import com.example.blog.vo.ArticleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文章服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleService {

    /** 状态常量：与 init.sql 的 status 注释保持一致；隐藏与否用独立的 hidden 列表达 */
    public static final int STATUS_DRAFT = 0;
    public static final int STATUS_PUBLISHED = 1;

    private final UserService userService;

    private final ArticleSearchRepository articleSearchRepository;

    /** 文章内容缓存（独立 Bean，避免类内自调用导致 @Cacheable 失效） */
    private final ArticleCacheService articleCacheService;

    private final HotArticleService hotArticleService;

    /** 浏览量计数：走 Redis 累加，详情页不再同步写数据库 */
    private final ViewCountService viewCountService;

    private final ArticleVisibilityPolicy visibilityPolicy;

    // 硬删除文章时同步清理的关联数据（直接注入 Mapper，避免与各 Service 相互依赖）
    private final CommentMapper commentMapper;
    private final LikeRecordMapper likeRecordMapper;
    private final FavoriteMapper favoriteMapper;
    private final NotificationMapper notificationMapper;

    @Override
    public ArticleVO publish(ArticleDTO dto) {
        Article article = new Article();
        article.setTitle(dto.getTitle());
        article.setContent(dto.getContent());
        article.setCategory(dto.getCategory());
        article.setTags(dto.getTags());
        article.setAuthorId(SecurityUtil.getCurrentUserId());
        article.setViewCount(0);
        article.setLikeCount(0);
        article.setStatus(dto.getStatus() == null ? STATUS_PUBLISHED : dto.getStatus());
        article.setHidden(0);
        save(article);
        // 回读：create_time 由数据库默认值生成，需重新查询才能返回
        Article saved = getById(article.getId());
        // 已发布文章立即同步 ES（原来只靠 5 分钟定时任务，发布后短时间内搜索不到）
        if (visibilityPolicy.isPublic(saved)) {
            indexToEsQuietly(saved);
        }
        return ArticleVO.from(saved, currentUser());
    }

    @Override
    // 更新成功后清除该文章的缓存，下次读取走数据库
    @CacheEvict(value = "article", key = "#id")
    public ArticleVO updateArticle(Long id, ArticleDTO dto) {
        Article article = getOwnedArticle(id);
        article.setTitle(dto.getTitle());
        article.setContent(dto.getContent());
        article.setCategory(dto.getCategory());
        article.setTags(dto.getTags());
        // 不传 status 保持原状态（编辑已发布文章不会因漏传被改回草稿）
        if (dto.getStatus() != null) {
            article.setStatus(dto.getStatus());
        }
        updateById(article);
        Article saved = getById(id);
        // ES 与可见性保持一致：公开中 → 建索引；草稿/已隐藏 → 删索引（搜索不可见）
        if (visibilityPolicy.isPublic(saved)) {
            indexToEsQuietly(saved);
        } else {
            deleteFromEsQuietly(id);
        }
        return ArticleVO.from(saved, currentUser());
    }

    @Override
    // 删除成功后清除该文章的缓存
    @CacheEvict(value = "article", key = "#id")
    @Transactional
    public void deleteArticle(Long id) {
        getOwnedArticle(id);
        // 硬删除：物理删除文章行及全部关联数据（评论、点赞、收藏、通知），
        // 不再用 @TableLogic 逻辑删除（removeById 只会 UPDATE deleted=1）
        commentMapper.deleteByArticleId(id);
        likeRecordMapper.deleteByArticleId(id);
        favoriteMapper.deleteByArticleId(id);
        notificationMapper.deleteByArticleId(id);
        baseMapper.hardDeleteById(id);
        // 同步删除 ES 文档（ES 操作在 MySQL 事务之外、失败不会回滚，
        // 失败仅记日志：下次启动的全量回填会以数据库为准纠正索引）
        deleteFromEsQuietly(id);
        // 清掉 Redis 里的浏览量计数，否则回写任务会不断尝试更新一个已不存在的行
        viewCountService.remove(id);
    }

    @Override
    @CacheEvict(value = "article", key = "#id")
    public void hideArticle(Long id) {
        transitionHidden(id, 0, 1, "文章不是公开状态，无法隐藏");
        // 从 ES 移除：隐藏的文章不应再被搜索到
        deleteFromEsQuietly(id);
    }

    @Override
    @CacheEvict(value = "article", key = "#id")
    public void unhideArticle(Long id) {
        transitionHidden(id, 1, 0, "文章不是隐藏状态，无法公开");
        // 重新公开 → 重建 ES 索引
        indexToEsQuietly(getById(id));
    }

    /**
     * 隐藏标志流转（仅作者本人）：from/to 校验 + 条件更新，防御并发下重复操作
     */
    private void transitionHidden(Long id, int from, int to, String wrongStateMessage) {
        Article article = getOwnedArticle(id);
        if (article.getStatus() == null || article.getStatus() != STATUS_PUBLISHED) {
            throw new BusinessException(400, "仅已发布的文章可以隐藏/公开");
        }
        if (article.getHidden() == null || article.getHidden() != from) {
            throw new BusinessException(400, wrongStateMessage);
        }
        lambdaUpdate()
                .set(Article::getHidden, to)
                .eq(Article::getId, id)
                .eq(Article::getHidden, from)
                .update();
    }


    /**
     * 同步文章到 ES（静默失败）：ES 不可用不影响发布/更新主流程，
     * ArticleSyncTask 的定时任务与启动回填会最终把索引修正
     */
    private void indexToEsQuietly(Article article) {
        try {
            User author = userService.getById(article.getAuthorId());
            String nickname = author == null ? null : author.getNickname();
            articleSearchRepository.save(ArticleDocument.from(article, nickname));
        } catch (Exception e) {
            log.error("文章同步到 ES 失败（不影响主流程）: articleId={}", article.getId(), e);
        }
    }

    /**
     * 从 ES 删除文档（静默失败，语义同 indexToEsQuietly）
     */
    private void deleteFromEsQuietly(Long id) {
        try {
            articleSearchRepository.deleteById(id);
        } catch (Exception e) {
            log.error("删除 ES 文档失败（不影响主流程）: articleId={}", id, e);
        }
    }

    @Override
    public PageResult<ArticleVO> pageArticles(ArticlePageDTO dto) {
        if ("hot".equalsIgnoreCase(dto.getSort())) {
            int required = dto.getPageNum() * dto.getPageSize();
            List<ArticleVO> all = hotArticleService.getHotArticles(required);
            int from = Math.min((dto.getPageNum() - 1) * dto.getPageSize(), all.size());
            int to = Math.min(from + dto.getPageSize(), all.size());
            PageResult<ArticleVO> result = new PageResult<>();
            result.setList(all.subList(from, to));
            result.setTotal(hotArticleService.countHotArticles());
            result.setPageNum(dto.getPageNum().longValue());
            result.setPageSize(dto.getPageSize().longValue());
            return result;
        }
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        // 列表只展示已发布且未隐藏的文章（草稿、已隐藏均不展示）
        wrapper.eq(Article::getStatus, STATUS_PUBLISHED)
                .eq(Article::getHidden, 0);
        wrapper.orderByDesc(Article::getCreateTime);
        Page<Article> page = page(new Page<>(dto.getPageNum(), dto.getPageSize()), wrapper);

        // 批量查询作者并组装，一次 IN 查询代替逐条查（避免 N+1）
        Set<Long> authorIds = page.getRecords().stream()
                .map(Article::getAuthorId)
                .collect(Collectors.toSet());
        Map<Long, User> authorMap = authorIds.isEmpty() ? Collections.emptyMap()
                : userService.listByIds(authorIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));

        List<ArticleVO> list = page.getRecords().stream()
                .map(a -> ArticleVO.from(a, authorMap.get(a.getAuthorId())))
                .toList();
        return PageResult.of(list, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PageResult<ArticleVO> pageMyArticles(ArticlePageDTO dto) {
        Long userId = SecurityUtil.getCurrentUserId();
        // 我的文章固定按创建时间倒序，忽略列表页的 time/hot 排序参数；
        // 不过滤状态：草稿 / 已隐藏的文章也要在个人中心可见可管理
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Article::getAuthorId, userId)
                .orderByDesc(Article::getCreateTime);
        Page<Article> page = page(new Page<>(dto.getPageNum(), dto.getPageSize()), wrapper);
        // 作者就是当前用户本人，一次查询即可完成组装，无需像公共列表那样批量查
        User me = userService.getById(userId);
        List<ArticleVO> list = page.getRecords().stream()
                .map(a -> ArticleVO.from(a, me))
                .toList();
        return PageResult.of(list, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 文章详情（浏览量 +1）
     * <p>
     * 性能优化（2026-08-31）：
     * 原本这里是「2 次 SELECT + 1 次 UPDATE + 1 次 Redis 写，且包在一个事务里」——
     * 每次访问都要 UPDATE view_count，既有行锁竞争，又逼着 MySQL 每次提交刷 redo log，
     * 一个纯读接口按写接口的成本在跑，实测严重拖累吞吐。
     * </p>
     * <p>
     * 现在改成：文章内容走缓存（命中时零数据库访问）+ 浏览量只在 Redis 里 INCR。
     * 去掉了事务、行锁和刷盘，浏览量由 ViewCountFlushTask 定时批量落盘。
     * 因此这里<b>不再需要 @Transactional 和 @CachePut</b>。
     * </p>
     */
    @Override
    public ArticleVO getDetail(Long id) {
        // 走缓存：公开文章命中时一次数据库都不查。
        // 注意必须经 articleCacheService 这个 Bean 调用，
        // 直接调本类方法会因 AOP 自调用导致缓存失效
        ArticleVO vo = articleCacheService.getCached(id);

        // 草稿（status=0）与已隐藏（hidden=1）的文章仅作者本人可见：其余任何人
        // （含匿名访客）一律按「不存在」处理——返回 404 而非 401/403，
        // 避免向未授权者暴露这类文章的存在性。
        // 权限判断用的 status / hidden 都来自 VO，公开文章已缓存，故不额外查库
        boolean restricted = vo.getStatus() != STATUS_PUBLISHED
                || (vo.getHidden() != null && vo.getHidden() == 1);
        if (restricted) {
            // 草稿/隐藏文章本就不进缓存，这里查一次实体拿 authorId 做归属判断。
            // 不能用 vo.getAuthor().getId() 代替：作者被逻辑删除时 selectDetailWithAuthor
            // 关联不到 user 行、author 为 null，会导致作者本人反而看不到自己的文章
            Article article = getById(id);
            Long currentUserId = currentUserIdOrNull();
            if (article == null || currentUserId == null
                    || !currentUserId.equals(article.getAuthorId())) {
                throw new BusinessException(404, "文章不存在");
            }
        }

        // 浏览量只动 Redis，不写数据库。
        // vo 里的 viewCount 是缓存/数据库中的旧值，仅作为 Redis 无计数时的初始化基线
        vo.setViewCount((int) viewCountService.incrementAndGet(id, vo.getViewCount()));
        return vo;
    }

    @Override
    public ArticleVO getArticleById(Long id) {
        // 委托给缓存 Bean：由它持有 @Cacheable，保证经代理调用
        return articleCacheService.getCached(id);
    }

    @Override
    public Article requirePublicArticle(Long id) {
        Article article = getById(id);
        // 与 getDetail 的可见性策略一致：非公开一律按「文章不存在」处理，
        // 防止通过互动接口枚举草稿 / 已隐藏的文章
        if (article == null || !visibilityPolicy.isPublic(article)) {
            throw new BusinessException(404, "文章不存在");
        }
        return article;
    }

    /**
     * 取当前登录用户 id，匿名访客返回 null
     * <p>原实现靠 catch BusinessException 判断匿名，逻辑相同但返回 null 更好用</p>
     */
    private Long currentUserIdOrNull() {
        try {
            return SecurityUtil.getCurrentUserId();
        } catch (BusinessException e) {
            // 匿名访客：SecurityUtil 取不到认证信息时会抛异常
            return null;
        }
    }

    /**
     * 校验文章存在且属于当前登录用户，返回文章
     */
    private Article getOwnedArticle(Long id) {
        Article article = getById(id);
        if (article == null) {
            throw new BusinessException(404, "文章不存在");
        }
        if (!article.getAuthorId().equals(SecurityUtil.getCurrentUserId())) {
            throw new BusinessException(403, "无权操作他人的文章");
        }
        return article;
    }

    /**
     * 当前登录用户（从认证上下文直接取，见 SecurityUtil）
     */
    private User currentUser() {
        return userService.getById(SecurityUtil.getCurrentUserId());
    }
}
