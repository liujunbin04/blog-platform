package com.example.blog.vo;

import com.example.blog.entity.ArticleDocument;
import com.example.blog.entity.User;
import lombok.Data;
import org.springframework.data.elasticsearch.core.SearchHit;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * 全文搜索结果项：标题（命中高亮）+ 正文片段（命中高亮，未命中正文时取开头截断）
 */
@Data
public class ArticleSearchVO {

    private Long id;

    /** 标题：命中的关键词用 <em> 包裹；标题未命中时为原标题 */
    private String title;

    /** 正文片段：命中的关键词用 <em> 包裹；正文未命中时取前 100 字 */
    private String contentSnippet;

    private String category;

    private String tags;

    private Integer viewCount;

    private Integer likeCount;

    /** 作者 id（ES 文档自带，用于批量回查作者） */
    private Long authorId;

    /** 作者信息（由 controller 按 authorId 批量回查填充，作者已删除则为 null） */
    private AuthorVO author;

    private LocalDateTime createTime;

    /**
     * 由 ES 命中结果组装（含高亮处理）
     */
    public static ArticleSearchVO from(SearchHit<ArticleDocument> hit) {
        ArticleDocument doc = hit.getContent();
        Map<String, List<String>> highlights = hit.getHighlightFields();

        ArticleSearchVO vo = new ArticleSearchVO();
        vo.setId(doc.getId());
        // 标题：优先用高亮片段（numberOfFragments=0 返回整段），否则用原标题
        vo.setTitle(hasHighlight(highlights, "title")
                ? highlights.get("title").get(0)
                : doc.getTitle());
        // 正文：优先用高亮片段；正文未命中（只命中标题）时截取开头
        if (hasHighlight(highlights, "content")) {
            vo.setContentSnippet(highlights.get("content").get(0));
        } else {
            String content = doc.getContent();
            vo.setContentSnippet(content != null && content.length() > 100
                    ? content.substring(0, 100) + "..."
                    : content);
        }
        vo.setCategory(doc.getCategory());
        vo.setTags(doc.getTags());
        vo.setViewCount(doc.getViewCount());
        vo.setLikeCount(doc.getLikeCount());
        vo.setAuthorId(doc.getAuthorId());
        // ES 里存的是 Instant（UTC），转回与 MySQL 一致的系统默认时区
        vo.setCreateTime(doc.getCreateTime() != null
                ? doc.getCreateTime().atZone(ZoneId.systemDefault()).toLocalDateTime()
                : null);
        return vo;
    }

    private static boolean hasHighlight(Map<String, List<String>> highlights, String field) {
        return highlights.containsKey(field) && !highlights.get(field).isEmpty();
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
