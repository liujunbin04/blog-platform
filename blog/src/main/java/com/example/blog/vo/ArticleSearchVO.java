package com.example.blog.vo;

import com.example.blog.entity.Article;
import com.example.blog.entity.User;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 全文搜索结果项：标题（命中高亮）+ 正文片段（命中高亮，未命中正文时取开头截断）
 * <p>高亮由后端在 SQL 结果上做字符串匹配生成，用 {@code <em>} 包裹关键词。
 * 前端 highlightHtml() 会先整体转义 HTML、再只还原 {@code <em>} 标签，
 * 因此这里不需要（也不应该）自行转义正文，否则前端会二次转义。</p>
 */
@Data
public class ArticleSearchVO {

    /** 正文片段的截取长度 */
    private static final int SNIPPET_LENGTH = 100;

    private Long id;

    /** 标题：命中的关键词用 <em> 包裹；标题未命中时为原标题 */
    private String title;

    /** 正文片段：命中的关键词用 <em> 包裹；正文未命中时取前 100 字 */
    private String contentSnippet;

    private String category;

    private String tags;

    private Integer viewCount;

    private Integer likeCount;

    /** 作者 id（用于批量回查作者） */
    private Long authorId;

    /** 作者信息（按 authorId 批量回查后填充，作者已删除则为 null） */
    private AuthorVO author;

    private LocalDateTime createTime;

    /**
     * 由 MySQL 查询结果组装，并按关键词生成高亮片段
     *
     * @param article 文章实体（正文为完整内容，用于定位命中位置）
     * @param keyword 搜索关键词，用于高亮；为空则不做高亮
     */
    public static ArticleSearchVO from(Article article, String keyword) {
        ArticleSearchVO vo = new ArticleSearchVO();
        vo.setId(article.getId());
        vo.setTitle(highlight(article.getTitle(), keyword));
        vo.setContentSnippet(snippet(article.getContent(), keyword));
        vo.setCategory(article.getCategory());
        vo.setTags(article.getTags());
        vo.setViewCount(article.getViewCount());
        vo.setLikeCount(article.getLikeCount());
        vo.setAuthorId(article.getAuthorId());
        vo.setCreateTime(article.getCreateTime());
        return vo;
    }

    /**
     * 正文片段：优先以命中位置为中心截取，正文未命中时退化为开头截断
     * <p>FULLTEXT 不返回命中偏移，这里用原文子串定位。ngram 分词后的词边界
     * 与原文子串并不总是一致（例如搜「数据库」而原文是「数据 库」），
     * 所以找不到命中时不能报错，只能退化成「正文前 N 字」，与旧实现一致。</p>
     */
    private static String snippet(String content, String keyword) {
        if (content == null) {
            return null;
        }
        int hit = indexOfIgnoreCase(content, keyword);
        if (hit < 0) {
            return content.length() > SNIPPET_LENGTH
                    ? content.substring(0, SNIPPET_LENGTH) + "..."
                    : content;
        }
        // 命中点前留 20 字的上下文，让片段读起来不是从词中间开始
        int start = Math.max(0, hit - 20);
        int end = Math.min(content.length(), start + SNIPPET_LENGTH);
        String window = content.substring(start, end);
        String highlighted = highlight(window, keyword);
        return (start > 0 ? "..." : "") + highlighted + (end < content.length() ? "..." : "");
    }

    /**
     * 把文本中所有出现的关键词用 {@code <em>} 包裹（大小写不敏感）
     */
    static String highlight(String text, String keyword) {
        if (text == null || keyword == null || keyword.isBlank()) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length() + 16);
        String lowerText = text.toLowerCase(Locale.ROOT);
        String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
        int from = 0;
        int hit;
        while ((hit = lowerText.indexOf(lowerKeyword, from)) >= 0) {
            out.append(text, from, hit)
                    .append("<em>")
                    .append(text, hit, hit + keyword.length())
                    .append("</em>");
            from = hit + keyword.length();
        }
        out.append(text, from, text.length());
        return out.toString();
    }

    private static int indexOfIgnoreCase(String text, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return -1;
        }
        return text.toLowerCase(Locale.ROOT).indexOf(keyword.toLowerCase(Locale.ROOT));
    }

    /**
     * 批量回查作者并填充：一次 IN 查询代替逐条查（防 N+1），顺序保持命中顺序
     */
    public static List<ArticleSearchVO> fillAuthors(List<ArticleSearchVO> vos,
            Map<Long, User> usersById) {
        for (ArticleSearchVO vo : vos) {
            User author = vo.getAuthorId() == null ? null : usersById.get(vo.getAuthorId());
            vo.setAuthor(author == null ? null : AuthorVO.from(author));
        }
        return vos;
    }
}