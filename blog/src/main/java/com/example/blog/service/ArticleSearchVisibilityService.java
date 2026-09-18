package com.example.blog.service;

import com.example.blog.vo.ArticleSearchVO;

import java.util.List;

/**
 * 搜索索引是派生数据，返回前必须按 MySQL 可见性做最终过滤。
 */
public interface ArticleSearchVisibilityService {

    List<ArticleSearchVO> filterVisible(List<ArticleSearchVO> hits);
}
