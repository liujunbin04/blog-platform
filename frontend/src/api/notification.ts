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
