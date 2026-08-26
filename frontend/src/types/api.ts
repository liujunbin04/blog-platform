/** 与后端 common/PageResult 对应 */
export interface PageResult<T> {
  total: number
  pageNum: number
  pageSize: number
  list: T[]
}

/** 后端 UserVO */
export interface UserVO {
  id: number
  username: string
  nickname: string | null
  email: string | null
  avatar: string | null
  createTime: string
}

/** 后端 LoginVO */
export interface LoginVO {
  token: string
  user: UserVO
}

/** 后端 AuthorVO */
export interface AuthorVO {
  id: number
  nickname: string | null
  avatar: string | null
}

/** 后端 ArticleVO */
export interface ArticleVO {
  id: number
  title: string
  content: string
  category: string | null
  tags: string | null
  viewCount: number
  likeCount: number
  status: number
  author: AuthorVO | null
  createTime: string
  updateTime: string
}

/** 后端 ArticleSearchVO（title/contentSnippet 含 <em> 高亮标签） */
export interface ArticleSearchVO {
  id: number
  title: string
  contentSnippet: string
  category: string | null
  tags: string | null
  viewCount: number
  likeCount: number
  authorId?: number | null
  author: AuthorVO | null
  createTime: string
}

/** 后端 FavoriteVO */
export interface FavoriteVO {
  favoriteId: number
  articleId: number
  title: string
  category: string | null
  tags: string | null
  viewCount: number
  likeCount: number
  author: AuthorVO | null
  articleCreateTime: string
  favoriteTime: string
}

/** 后端 CommentVO（parentId 为 0 表示一级评论） */
export interface CommentVO {
  id: number
  articleId: number
  parentId: number
  content: string
  author: AuthorVO | null
  createTime: string
  children: CommentVO[]
}

/** 后端 NotificationVO */
export interface NotificationVO {
  id: number
  senderId: number
  senderNickname: string | null
  type: string
  articleId: number
  content: string | null
  isRead: number
  createTime: string
}

/** 后端 NotificationPageVO（比 PageResult 多未读数） */
export interface NotificationPageVO {
  total: number
  pageNum: number
  pageSize: number
  list: NotificationVO[]
  unreadCount: number
}
