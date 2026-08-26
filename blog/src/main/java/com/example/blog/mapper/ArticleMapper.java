package com.example.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.blog.entity.Article;
import com.example.blog.vo.ArticleVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 文章 Mapper
 */
@Mapper
public interface ArticleMapper extends BaseMapper<Article> {

    /**
     * 文章详情：自定义 SQL 关联 user 表取作者昵称、头像
     * <p>SQL 见 resources/mapper/ArticleMapper.xml；返回带嵌套 author 的 ArticleVO</p>
     */
    ArticleVO selectDetailWithAuthor(@Param("id") Long id);
}
