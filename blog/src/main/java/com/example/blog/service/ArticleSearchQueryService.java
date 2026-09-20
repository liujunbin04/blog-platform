package com.example.blog.service;

import com.example.blog.common.PageResult;
import com.example.blog.dto.ArticleSearchDTO;
import com.example.blog.vo.ArticleSearchVO;

/**
 * 文章全文搜索编排。
 */
public interface ArticleSearchQueryService {

    PageResult<ArticleSearchVO> search(ArticleSearchDTO dto);
}
