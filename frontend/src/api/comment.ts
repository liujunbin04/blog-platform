import request from '@/utils/request'
import type { CommentVO } from '@/types/api'

/** 某篇文章的评论树 */
export function getCommentTree(articleId: number | string) {
  return request.get<never, CommentVO[]>('/comments', { params: { articleId } })
}

/** 发表评论 / 回复评论；parentId 不传表示一级评论 */
export function addComment(data: { articleId: number | string; parentId?: number; content: string }) {
  return request.post<never, CommentVO>('/comments', data)
}
