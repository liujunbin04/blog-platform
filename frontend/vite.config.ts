import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
    // 组件与常用 API 按需自动导入，替代 main.ts 全量注册；
    // ElMessage 等业务里显式 import 的用法由 resolver 补样式注入，保持显式写法不变
    AutoImport({
      imports: ['vue', 'vue-router'],
      resolvers: [ElementPlusResolver()],
    }),
    Components({ resolvers: [ElementPlusResolver()] }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    rolldownOptions: {
      output: {
        codeSplitting: {
          groups: [
            { name: 'element-plus', test: /[\\/]node_modules[\\/]element-plus[\\/]/ },
            { name: 'element-icons', test: /[\\/]node_modules[\\/]@element-plus[\\/]/ },
            { name: 'markdown-it', test: /[\\/]node_modules[\\/](markdown-it|linkify-it|mdurl|decode-uri-component|entities|uc\.micro)[\\/]/ },
            { name: 'vendor', test: /[\\/]node_modules[\\/]/ },
          ],
        },
      },
    },
  },
})
