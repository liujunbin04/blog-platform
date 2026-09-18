package com.example.blog.vo;

import lombok.Data;

import java.util.List;

/**
 * 一级评论分页结果，子回复随当前页返回。
 */
@Data
public class CommentPageVO {

    private Long total;

    private Long pageNum;

    private Long pageSize;

    private List<CommentVO> list;
}
