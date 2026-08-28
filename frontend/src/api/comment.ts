import request from '@/utils/request'
import type { CommentVO } from '@/types/api'

/** 某篇文章的评论树 */
export function getCommentTree(articleId: number | string) {
  return request.get<never, CommentVO[]>('/comments', { params: { articleId } })
}

/** 发表评论 / 回复评论；replyToId 不传表示一级评论（挂树的父评论由后端推导） */
export function addComment(data: { articleId: number | string; replyToId?: number; content: string }) {
  return request.post<never, CommentVO>('/comments', data)
}

/** 删除自己的评论 */
export function deleteComment(id: number | string) {
  return request.delete<never, null>(`/comments/${id}`)
}
