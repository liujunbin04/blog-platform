package com.example.blog.controller;

import com.example.blog.common.BusinessException;
import com.example.blog.common.PageResult;
import com.example.blog.common.Result;
import com.example.blog.dto.ArticleDTO;
import com.example.blog.dto.ArticlePageDTO;
import com.example.blog.dto.ArticleSearchDTO;
import com.example.blog.entity.Article;
import com.example.blog.entity.ArticleDocument;
import com.example.blog.entity.User;
import com.example.blog.repository.ArticleSearchRepository;
import com.example.blog.service.ArticleService;
import com.example.blog.service.ArticleSearchVisibilityService;
import com.example.blog.service.HotArticleService;
import com.example.blog.service.UserService;
import com.example.blog.utils.SecurityUtil;
import com.example.blog.vo.ArticleSearchVO;
import com.example.blog.vo.ArticleVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文章接口（全部需要登录，见 SecurityConfig）
 */
@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    private final ArticleSearchRepository articleSearchRepository;

    private final ArticleSearchVisibilityService articleSearchVisibilityService;

    private final UserService userService;

    private final HotArticleService hotArticleService;

    /**
     * 发布文章
     */
    @PostMapping
    public Result<ArticleVO> publish(@Valid @RequestBody ArticleDTO dto) {
        return Result.success(articleService.publish(dto));
    }

    /**
     * 更新文章（仅作者本人）
     */
    @PutMapping("/{id}")
    public Result<ArticleVO> update(@PathVariable Long id, @Valid @RequestBody ArticleDTO dto) {
        return Result.success(articleService.updateArticle(id, dto));
    }

    /**
     * 删除文章（硬删除：物理删除文章行及其评论/点赞/收藏/通知记录，仅作者本人）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        articleService.deleteArticle(id);
        return Result.success();
    }

    /**
     * 隐藏已发布的文章（仅作者本人；他人不可见、不可搜索，可随时重新公开）
     */
    @PutMapping("/{id}/hide")
    public Result<Void> hide(@PathVariable Long id) {
        articleService.hideArticle(id);
        return Result.success();
    }

    /**
     * 取消隐藏，重新公开（仅作者本人）
     */
    @PutMapping("/{id}/unhide")
    public Result<Void> unhide(@PathVariable Long id) {
        articleService.unhideArticle(id);
        return Result.success();
    }

    /**
     * 分页列表：?pageNum=1&pageSize=10&sort=time|hot
     */
    @GetMapping
    public Result<PageResult<ArticleVO>> page(@Valid ArticlePageDTO dto) {
        return Result.success(articleService.pageArticles(dto));
    }

    /**
     * 全文搜索：GET /api/articles/search?keyword=xx&page=1&size=10
     * （标题、正文 multi_match + 高亮片段，见 ArticleSearchRepositoryCustomImpl）
     */
    @GetMapping("/search")
    public Result<PageResult<ArticleSearchVO>> search(@Valid ArticleSearchDTO dto) {
        SearchHits<ArticleDocument> hits = articleSearchRepository
                .searchByKeyword(dto.getKeyword(), dto.getPage(), dto.getSize());
        List<ArticleSearchVO> list = hits.getSearchHits().stream()
                .map(ArticleSearchVO::from)
                .toList();
        list = articleSearchVisibilityService.filterVisible(list);
        // 批量回查作者：与列表页相同的防 N+1 模式；
        // 全部作者已被逻辑删除时 authorIds 可能为空集合，listByIds 传空会生成非法 SQL，必须判空
        Set<Long> authorIds = list.stream()
                .map(ArticleSearchVO::getAuthorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = authorIds.isEmpty() ? Collections.emptyMap()
                : userService.listByIds(authorIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));
        ArticleSearchVO.fillAuthors(list, userMap);
        return Result.success(PageResult.of(
                list,
                hits.getTotalHits(),
                dto.getPage().longValue(),
                dto.getSize().longValue()
        ));
    }

    /**
     * 热门文章（Redis ZSet，定时任务维护；游客可看）：
     * GET /api/articles/hot?topN=10
     */
    @GetMapping("/hot")
    public Result<List<ArticleVO>> hot(
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "topN 最小为 1") @Max(value = 50, message = "topN 最大为 50") int topN) {
        return Result.success(hotArticleService.getHotArticles(topN));
    }

    /**
     * 当前用户的文章分页（个人中心「我的文章」，需登录）
     */
    @GetMapping("/my")
    public Result<PageResult<ArticleVO>> my(@Valid ArticlePageDTO dto) {
        return Result.success(articleService.pageMyArticles(dto));
    }

    /**
     * 文章编辑数据（仅作者本人）：GET /api/articles/{id}/edit-data
     * <p>与详情接口的区别：走纯读缓存、浏览量不自增；
     * 权限校验在此处做而非 Service 层，保证 getArticleById 经代理调用、@Cacheable 生效
     * （类内直调缓存不生效，见 ArticleService.getArticleById 的注释）</p>
     */
    @GetMapping("/{id}/edit-data")
    public Result<ArticleVO> editData(@PathVariable Long id) {
        Article article = articleService.getById(id);
        if (article == null) {
            throw new BusinessException(404, "文章不存在");
        }
        if (!article.getAuthorId().equals(SecurityUtil.getCurrentUserId())) {
            throw new BusinessException(403, "无权操作他人的文章");
        }
        return Result.success(articleService.getArticleById(id));
    }

    /**
     * 文章详情（浏览量 +1）
     */
    @GetMapping("/{id}")
    public Result<ArticleVO> detail(@PathVariable Long id) {
        return Result.success(articleService.getDetail(id));
    }
}
