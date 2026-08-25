import request from '@/utils/request'
import type { LoginVO, UserVO } from '@/types/api'

export interface LoginForm {
  username: string
  password: string
}

export interface RegisterForm {
  username: string
  password: string
  email?: string
  nickname?: string
}

/** 登录 */
export function login(data: LoginForm) {
  return request.post<never, LoginVO>('/auth/login', data)
}

/** 注册 */
export function register(data: RegisterForm) {
  return request.post<never, UserVO>('/auth/register', data)
}
