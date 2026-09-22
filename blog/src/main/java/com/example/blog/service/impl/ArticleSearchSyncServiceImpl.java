package com.example.blog.service.impl;

import com.example.blog.entity.Article;
import com.example.blog.entity.ArticleDocument;
import com.example.blog.entity.User;
import com.example.blog.mapper.ArticleMapper;
import com.example.blog.repository.ArticleSearchRepository;
import com.example.blog.service.ArticleSearchSyncService;
import com.example.blog.service.ArticleVisibilityPolicy;
import com.example.blog.service.AuthorAssembler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleSearchSyncServiceImpl implements ArticleSearchSyncService {

    private final ArticleSearchRepository articleSearchRepository;
    private final AuthorAssembler authorAssembler;
    private final ArticleMapper articleMapper;
    private final ArticleVisibilityPolicy visibilityPolicy;

    @Override
    public void sync(Article article) {
        try {
            Map<Long, User> authors =
                    authorAssembler.loadUsers(List.of(article.getAuthorId()));
            articleSearchRepository.save(toDocument(article, authors));
        } catch (Exception e) {
            log.error("文章同步到 ES 失败: articleId={}", article.getId(), e);
        }
    }

    @Override
    public void remove(Long articleId) {
        try {
            articleSearchRepository.deleteById(articleId);
        } catch (Exception e) {
            log.error("删除 ES 文档失败: articleId={}", articleId, e);
        }
    }

    @Override
    public void syncAll(List<Article> articles) {
        if (articles.isEmpty()) {
            return;
        }
        Map<Long, User> authors = authorAssembler.loadUsers(
                articles.stream().map(Article::getAuthorId).toList()
        );
        articleSearchRepository.saveAll(articles.stream()
                .map(article -> toDocument(article, authors))
                .toList());
    }

    @Override
    public void reconcileVisibility() {
        articleSearchRepository.findAll().forEach(document -> {
            Article article = articleMapper.selectById(document.getId());
            if (!visibilityPolicy.isPublic(article)) {
                articleSearchRepository.deleteById(document.getId());
            }
        });
    }

    private ArticleDocument toDocument(Article article, Map<Long, User> authors) {
        User author = authors.get(article.getAuthorId());
        return ArticleDocument.from(article, author == null ? null : author.getNickname());
    }
}
