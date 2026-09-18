package com.example.blog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 评论分页查询参数。
 */
@Data
public class CommentPageDTO {

    @NotNull(message = "文章 id 不能为空")
    private Long articleId;

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码最小为 1")
    private Integer pageNum = 1;

    @NotNull(message = "每页大小不能为空")
    @Min(value = 1, message = "每页大小最小为 1")
    @Max(value = 100, message = "每页大小最大为 100")
    private Integer pageSize = 20;
}
