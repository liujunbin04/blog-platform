package com.example.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.blog.common.BusinessException;
import com.example.blog.common.PageResult;
import com.example.blog.dto.FavoritePageDTO;
import com.example.blog.entity.Article;
import com.example.blog.entity.Favorite;
import com.example.blog.entity.User;
import com.example.blog.mapper.FavoriteMapper;
import com.example.blog.service.ArticleService;
import com.example.blog.service.FavoriteService;
import com.example.blog.service.UserService;
import com.example.blog.utils.SecurityUtil;
import com.example.blog.vo.FavoriteVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 收藏服务实现
 */
@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl extends ServiceImpl<FavoriteMapper, Favorite> implements FavoriteService {

    private final ArticleService articleService;
    private final UserService userService;

    @Override
    @Transactional
    public void addFavorite(Long articleId) {
        // 1. 校验文章存在且公开：草稿 / 已隐藏的文章不可收藏，防止绕过可见性
        articleService.requirePublicArticle(articleId);
        Long userId = SecurityUtil.getCurrentUserId();
        // 2. 先查后插：常规重复收藏在这里拦截
        long count = count(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getArticleId, articleId));
        if (count > 0) {
            throw new BusinessException(400, "请勿重复收藏");
        }
        // 3. 插入收藏记录：并发下唯一索引 uk_user_article 是最终防线
        //    （与 LikeRecord 相同的双层防重模式）
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setArticleId(articleId);
        try {
            save(favorite);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(400, "请勿重复收藏");
        }
        // 注：article 表暂无 favorite_count 字段（见 docs/design.md 热度公式），
        // 若后续加列，应在同一事务里自增，参照 LikeRecordServiceImpl.like
    }

    @Override
    @Transactional
    public void removeFavorite(Long articleId) {
        Long userId = SecurityUtil.getCurrentUserId();
        // 物理删除收藏记录（原因同 LikeRecord：逻辑删除会占住唯一索引）。
        // 注意这里故意不校验文章是否存在：文章可能已被逻辑删除，
        // 用户仍应能清理自己的收藏记录，否则会留下永远删不掉的脏数据
        boolean removed = remove(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getArticleId, articleId));
        if (!removed) {
            throw new BusinessException(400, "尚未收藏，无法取消");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasFavorited(Long articleId) {
        return count(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, SecurityUtil.getCurrentUserId())
                .eq(Favorite::getArticleId, articleId)) > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<FavoriteVO> pageFavorites(FavoritePageDTO dto) {
        Long userId = SecurityUtil.getCurrentUserId();
        // 1. 分页查收藏记录（按收藏时间倒序）
        Page<Favorite> page = page(new Page<>(dto.getPageNum(), dto.getPageSize()),
                new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .orderByDesc(Favorite::getCreateTime));
        List<Favorite> records = page.getRecords();
        if (records.isEmpty()) {
            return PageResult.of(List.of(), page.getTotal(), page.getCurrent(), page.getSize());
        }
        // 2. 批量查文章：注意 listByIds 不保证顺序，用 Map 建索引，
        //    再按收藏记录顺序取，保证返回顺序与收藏时间倒序一致
        List<Long> articleIds = records.stream().map(Favorite::getArticleId).toList();
        Map<Long, Article> articleMap = articleService.listByIds(articleIds).stream()
                .collect(Collectors.toMap(Article::getId, Function.identity()));
        // 3. 批量查作者
        Set<Long> authorIds = articleMap.values().stream()
                .map(Article::getAuthorId)
                .collect(Collectors.toSet());
        // 注意：listByIds 传空集合会生成 IN () 非法 SQL，必须判空
        // （收藏的文章全被逻辑删除时，articleMap 为空，authorIds 即为空集合）
        Map<Long, User> userMap = authorIds.isEmpty() ? Collections.emptyMap()
                : userService.listByIds(authorIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));
        // 4. 按收藏顺序组装（文章已被逻辑删除的收藏直接跳过；
        //    如需清理这类脏记录，可在此物理删除）
        List<FavoriteVO> list = new ArrayList<>();
        for (Favorite favorite : records) {
            Article article = articleMap.get(favorite.getArticleId());
            if (article == null) {
                continue;
            }
            list.add(FavoriteVO.from(favorite, article, userMap.get(article.getAuthorId())));
        }
        return PageResult.of(list, page.getTotal(), page.getCurrent(), page.getSize());
    }
}
