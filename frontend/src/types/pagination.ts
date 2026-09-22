/**
 * 分页组件的页码状态。
 * <p>这是前端持有的请求参数状态（当前第几页、每页多少条），与后端返回的分页
 * 载荷字段同名但不是同一个概念。</p>
 */
export interface PageState {
  pageNum: number
  pageSize: number
}

/** 可选的分页尺寸；与后端分页 DTO 的 pageSize 上限保持一致 */
export const PAGE_SIZES: number[] = [10, 20, 50]
