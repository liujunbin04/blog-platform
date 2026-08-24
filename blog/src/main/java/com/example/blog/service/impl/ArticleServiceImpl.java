package com.example.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.blog.common.BusinessException;
import com.example.blog.common.PageResult;
import com.example.blog.dto.ArticleDTO;
import com.example.blog.dto.ArticlePageDTO;
import com.example.blog.entity.Article;
import com.example.blog.entity.User;
import com.example.blog.mapper.ArticleMapper;
import com.example.blog.repository.ArticleSearchRepository;
import com.example.blog.service.ArticleService;
import com.example.blog.service.UserService;
import com.example.blog.utils.SecurityUtil;
import com.example.blog.vo.ArticleVO;
import lombok.RequiredArgsConstructor;
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
@Service
@RequiredArgsConstructor
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleService {

    private final UserService userService;

    private final ArticleSearchRepository articleSearchRepository;

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
        article.setStatus(1);
        save(article);
        // 回读：create_time 由数据库默认值生成，需重新查询才能返回
        return ArticleVO.from(getById(article.getId()), currentUser());
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
        updateById(article);
        return ArticleVO.from(getById(id), currentUser());
    }

    @Override
    // 删除成功后清除该文章的缓存
    @CacheEvict(value = "article", key = "#id")
    public void deleteArticle(Long id) {
        getOwnedArticle(id);
        // @TableLogic：removeById 实际执行 UPDATE deleted=1，数据仍保留在表中
        removeById(id);
        // 同步删除 ES 文档：ArticleSyncTask 查不到已删除文章（MP 过滤 deleted=1），
        // 不在这里删的话搜索结果会残留已删除文章。
        // 注：ES 删除在 MySQL 事务之外、失败不会回滚，教程场景可接受，
        // 生产可改走 MQ 最终一致（见 docs/design.md 的 RabbitMQ 方案）
        articleSearchRepository.deleteById(id);
    }

    @Override
    public PageResult<ArticleVO> pageArticles(ArticlePageDTO dto) {
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        // 列表只展示已发布的文章
        wrapper.eq(Article::getStatus, 1);
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
        // 我的文章固定按创建时间倒序，忽略列表页的 time/hot 排序参数
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
