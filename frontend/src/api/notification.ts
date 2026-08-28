import request from '@/utils/request'
import type { NotificationPageVO } from '@/types/api'

/** 当前用户的通知分页（含未读数） */
export function getNotifications(params: { pageNum: number; pageSize: number }) {
  return request.get<never, NotificationPageVO>('/notifications', { params })
}

/** 全部标记已读 */
export function markAllNotifications() {
  return request.put<never, null>('/notifications/read-all')
}

/** 清空全部通知（物理删除，不可恢复） */
export function clearNotifications() {
  return request.delete<never, null>('/notifications')
}
