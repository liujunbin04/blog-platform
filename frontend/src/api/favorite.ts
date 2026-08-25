import request from '@/utils/request'
import type { FavoriteVO, PageResult } from '@/types/api'

/** 收藏 */
export function favoriteArticle(articleId: number | string) {
  return request.post<never, null>(`/favorites/${articleId}`)
}

/** 取消收藏 */
export function unfavoriteArticle(articleId: number | string) {
  return request.delete<never, null>(`/favorites/${articleId}`)
}

/** 当前用户是否已收藏 */
export function getFavoriteStatus(articleId: number | string) {
  return request.get<never, boolean>(`/favorites/${articleId}/status`)
}

/** 我的收藏分页 */
export function getMyFavorites(params: { pageNum: number; pageSize: number }) {
  return request.get<never, PageResult<FavoriteVO>>('/favorites', { params })
}
