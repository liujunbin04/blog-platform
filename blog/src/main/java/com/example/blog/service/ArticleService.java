package com.example.blog.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.blog.common.PageResult;
import com.example.blog.dto.ArticleDTO;
import com.example.blog.dto.ArticlePageDTO;
import com.example.blog.entity.Article;
import com.example.blog.vo.ArticleVO;

/**
 * 文章服务
 */
public interface ArticleService extends IService<Article> {

    /**
     * 发布文章
     */
    ArticleVO publish(ArticleDTO dto);

    /**
     * 更新文章（仅作者本人）
     */
    ArticleVO updateArticle(Long id, ArticleDTO dto);

    /**
     * 删除文章（硬删除：物理删除文章行及其评论/点赞/收藏/通知记录，仅作者本人）
     */
    void deleteArticle(Long id);

    /**
     * 隐藏已发布的文章（status 1 → 2，仅作者本人；他人访问详情/列表/搜索均不可见）
     */
    void hideArticle(Long id);

    /**
     * 取消隐藏，重新公开（status 2 → 1，仅作者本人）
     */
    void unhideArticle(Long id);

    /**
     * 分页查询文章列表（按创建时间倒序 / 按浏览量倒序）
     */
    PageResult<ArticleVO> pageArticles(ArticlePageDTO dto);

    /**
     * 分页查询当前登录用户的文章（个人中心「我的文章」，按创建时间倒序）
     */
    PageResult<ArticleVO> pageMyArticles(ArticlePageDTO dto);

    /**
     * 查询文章详情，浏览量 +1
     */
    ArticleVO getDetail(Long id);

    /**
     * 按 id 查询文章（含作者信息）。
     * 纯读、无浏览量副作用，带 @Cacheable 缓存；
     * 注意必须通过代理调用（controller / 其他 service），类内部调用缓存不生效
     */
    ArticleVO getArticleById(Long id);

    /**
     * 校验文章存在且对外公开（已发布、未隐藏），否则按「文章不存在」抛 404。
     * 评论 / 点赞 / 收藏等公开互动入口统一走这里，防止草稿和已隐藏文章被交互
     */
    Article requirePublicArticle(Long id);
}
