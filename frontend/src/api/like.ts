import request from '@/utils/request'

/** 点赞 */
export function likeArticle(articleId: number | string) {
  return request.post<never, null>(`/likes/${articleId}`)
}

/** 取消点赞 */
export function unlikeArticle(articleId: number | string) {
  return request.delete<never, null>(`/likes/${articleId}`)
}

/** 当前用户是否已点赞 */
export function getLikeStatus(articleId: number | string) {
  return request.get<never, boolean>(`/likes/${articleId}/status`)
}
