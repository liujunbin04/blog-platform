package com.example.blog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 全文搜索请求参数（GET 请求 query 参数自动绑定）
 */
@Data
public class ArticleSearchDTO {

    @NotBlank(message = "搜索关键词不能为空")
    private String keyword;

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码最小为 1")
    private Integer page = 1;

    @NotNull(message = "每页大小不能为空")
    @Min(value = 1, message = "每页大小最小为 1")
    @Max(value = 100, message = "每页大小最大为 100")
    private Integer size = 10;
}
