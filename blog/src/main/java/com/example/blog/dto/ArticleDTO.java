package com.example.blog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 文章发布 / 更新请求参数
 */
@Data
public class ArticleDTO {

    @NotBlank(message = "文章标题不能为空")
    @Size(max = 200, message = "文章标题不能超过 200 个字符")
    private String title;

    @NotBlank(message = "文章内容不能为空")
    private String content;

    @Size(max = 50, message = "分类不能超过 50 个字符")
    private String category;

    @Size(max = 255, message = "标签不能超过 255 个字符")
    private String tags;

    /** 保存后的状态：0 存草稿，1 发布；不传默认发布。
     *  隐藏（2）不走本字段，由独立的隐藏/公开接口维护 */
    @Min(value = 0, message = "状态仅支持 0 草稿 / 1 发布")
    @Max(value = 1, message = "状态仅支持 0 草稿 / 1 发布")
    private Integer status;
}
