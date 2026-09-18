package com.example.blog.service.impl;

import com.example.blog.entity.Article;
import com.example.blog.service.ArticleSearchVisibilityService;
import com.example.blog.service.ArticleService;
import com.example.blog.vo.ArticleSearchVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArticleSearchVisibilityServiceImpl implements ArticleSearchVisibilityService {

    private final ArticleService articleService;

    @Override
    public List<ArticleSearchVO> filterVisible(List<ArticleSearchVO> hits) {
        if (hits.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = hits.stream()
                .map(ArticleSearchVO::getId)
                .collect(Collectors.toSet());
        Map<Long, Article> byId = articleService.listByIds(ids).stream()
                .collect(Collectors.toMap(Article::getId, Function.identity()));
        return hits.stream()
                .filter(hit -> isPublic(byId.get(hit.getId())))
                .toList();
    }

    private boolean isPublic(Article article) {
        return article != null
                && article.getStatus() != null
                && article.getStatus() == 1
                && article.getHidden() != null
                && article.getHidden() == 0;
    }
}
