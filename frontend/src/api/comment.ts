import request from '@/utils/request'
import type { CommentPageVO, CommentVO } from '@/types/api'

/** 一级评论分页 */
export function getCommentPage(params: {
  articleId: number | string
  pageNum: number
  pageSize: number
}) {
  return request.get<never, CommentPageVO>('/comments/page', { params })
}

/** 发表评论 / 回复评论；replyToId 不传表示一级评论（挂树的父评论由后端推导） */
export function addComment(data: { articleId: number | string; replyToId?: number; content: string }) {
  return request.post<never, CommentVO>('/comments', data)
}

/** 删除自己的评论 */
export function deleteComment(id: number | string) {
  return request.delete<never, null>(`/comments/${id}`)
}
