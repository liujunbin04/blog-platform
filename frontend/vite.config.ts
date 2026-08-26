import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
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
