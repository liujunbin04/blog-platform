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
import com.example.blog.service.ArticleService;
import com.example.blog.service.UserService;
import com.example.blog.utils.SecurityUtil;
import com.example.blog.vo.ArticleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
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
        if (isPublic(saved)) {
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
        if (isPublic(saved)) {
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
     * 文章当前是否对外公开（已发布且未隐藏）：决定是否进 ES 与列表/搜索可见性
     */
    private boolean isPublic(Article article) {
        return article.getStatus() != null && article.getStatus() == STATUS_PUBLISHED
                && (article.getHidden() == null || article.getHidden() == 0);
    }

    /**
     * 同步文章到 ES（静默失败）：ES 不可用不影响发布/更新主流程，
     * ArticleSyncTask 的定时任务与启动回填会最终把索引修正
     */
    private void indexToEsQuietly(Article article) {
        try {
            articleSearchRepository.save(ArticleDocument.from(article));
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
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        // 列表只展示已发布且未隐藏的文章（草稿、已隐藏均不展示）
        wrapper.eq(Article::getStatus, STATUS_PUBLISHED)
                .eq(Article::getHidden, 0);
        if ("hot".equalsIgnoreCase(dto.getSort())) {
            wrapper.orderByDesc(Article::getViewCount, Article::getCreateTime);
        } else {
            wrapper.orderByDesc(Article::getCreateTime);
        }
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

    @Override
    @Transactional
    // 详情访问会刷新浏览量，方法返回前把最新数据写回缓存，保持缓存与数据库一致
    @CachePut(value = "article", key = "#id")
    public ArticleVO getDetail(Long id) {
        Article article = getById(id);
        if (article == null) {
            throw new BusinessException(404, "文章不存在");
        }
        // 草稿（status=0）与已隐藏（hidden=1）的文章仅作者本人可见：其余任何人
        // （含匿名访客）一律按「不存在」处理——返回 404 而非 401/403，
        // 避免向未授权者暴露这类文章的存在性
        boolean isOwner = false;
        try {
            isOwner = article.getAuthorId().equals(SecurityUtil.getCurrentUserId());
        } catch (BusinessException e) {
            // 匿名访客：isOwner 保持 false，走下面的 404 分支
        }
        boolean restricted = article.getStatus() != STATUS_PUBLISHED
                || (article.getHidden() != null && article.getHidden() == 1);
        if (restricted && !isOwner) {
            throw new BusinessException(404, "文章不存在");
        }
        // 原子自增浏览量（每次访问都自增，这部分不走缓存）
        lambdaUpdate().setSql("view_count = view_count + 1").eq(Article::getId, id).update();
        // 直接查库取最新数据（缓存里是旧浏览量，不能读缓存），交给 @CachePut 刷新
        ArticleVO vo = baseMapper.selectDetailWithAuthor(id);
        if (vo == null) {
            // 极端情况：存在性校验后、查询前文章被删除
            throw new BusinessException(404, "文章不存在");
        }
        return vo;
    }

    @Override
    // 纯读方法 + 缓存：命中时直接返回缓存，不查数据库
    @Cacheable(value = "article", key = "#id")
    public ArticleVO getArticleById(Long id) {
        ArticleVO vo = baseMapper.selectDetailWithAuthor(id);
        if (vo == null) {
            throw new BusinessException(404, "文章不存在");
        }
        return vo;
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
