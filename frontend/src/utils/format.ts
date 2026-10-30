/** 后端 LocalDateTime 序列化为 "2026-08-24T10:30:00"，截取为 "2026-08-24 10:30" 展示 */
export function formatDateTime(iso?: string | null): string {
  if (!iso) return '-'
  return iso.replace('T', ' ').slice(0, 16)
}

/**
 * 搜索结果的标题/摘要含后端生成的 <em> 高亮标签。
 * 先整体转义 HTML 再还原 <em>，保证除高亮外不会渲染任何原始 HTML（防注入）
 */
export function highlightHtml(text?: string | null): string {
  if (!text) return ''
  const escaped = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  return escaped.replace(/&lt;(\/?)em&gt;/g, '<$1em>')
}
