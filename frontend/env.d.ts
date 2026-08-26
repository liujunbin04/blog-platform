/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 后端 API 基地址（如 https://api.example.com/api），未设置时用本地默认值 */
  readonly VITE_API_BASE?: string
}
