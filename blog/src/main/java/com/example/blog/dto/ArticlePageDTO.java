package com.example.blog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 文章分页查询参数（GET 请求 query 参数自动绑定）
 */
@Data
public class ArticlePageDTO {

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码最小为 1")
    private Integer pageNum = 1;

    @NotNull(message = "每页大小不能为空")
    @Min(value = 1, message = "每页大小最小为 1")
    @Max(value = 100, message = "每页大小最大为 100")
    private Integer pageSize = 10;

    /** 排序方式：time=按创建时间倒序（默认），hot=按浏览量倒序 */
    private String sort = "time";
}
