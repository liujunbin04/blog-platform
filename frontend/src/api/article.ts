import request from '@/utils/request'
import type { ArticleSearchVO, ArticleVO, PageResult } from '@/types/api'

export interface ArticleQuery {
  pageNum: number
  pageSize: number
  sort?: 'time' | 'hot'
}

export interface ArticleFormData {
  title: string
  category?: string
  tags?: string
  content: string
}

/** 文章分页列表 */
export function getArticlePage(params: ArticleQuery) {
  return request.get<never, PageResult<ArticleVO>>('/articles', { params })
}

/** 全文搜索（ES 高亮） */
export function searchArticles(params: { keyword: string; page: number; size: number }) {
  return request.get<never, PageResult<ArticleSearchVO>>('/articles/search', { params })
}

/** 文章详情（浏览量 +1） */
export function getArticleDetail(id: number | string) {
  return request.get<never, ArticleVO>(`/articles/${id}`)
}

/** 我的文章分页 */
export function getMyArticles(params: ArticleQuery) {
  return request.get<never, PageResult<ArticleVO>>('/articles/my', { params })
}

/** 文章编辑数据（仅作者本人，不增加浏览量） */
export function getArticleEditData(id: number | string) {
  return request.get<never, ArticleVO>(`/articles/${id}/edit-data`)
}

/** 发布文章 */
export function publishArticle(data: ArticleFormData) {
  return request.post<never, ArticleVO>('/articles', data)
}

/** 更新文章（仅作者本人） */
export function updateArticle(id: number | string, data: ArticleFormData) {
  return request.put<never, ArticleVO>(`/articles/${id}`, data)
}

/** 删除文章（仅作者本人） */
export function deleteArticle(id: number | string) {
  return request.delete<never, null>(`/articles/${id}`)
}

/** 热门文章（按浏览量，游客可看） */
export function getHotArticles(topN = 10) {
  return request.get<never, ArticleVO[]>('/articles/hot', { params: { topN } })
}
