package com.example.blog.dto;

import lombok.Data;

/**
 * 热度重建的查询结果行：一篇文章的 id 与按公式算出的热度分
 */
@Data
public class HotScoreRow {

    private Long articleId;

    private Double score;
}