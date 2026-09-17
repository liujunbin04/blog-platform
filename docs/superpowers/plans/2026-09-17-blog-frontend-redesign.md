# 博客平台前端视觉重设计 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不修改后端接口和数据模型的前提下，完成博客平台全部前端页面的视觉重设计、响应式优化和专业 Markdown 编辑器增强。

**Architecture:** 以 CSS 变量建立全局设计系统，用少量共享组件承载页面壳、文章条目、上下文轨道和编辑器工具。现有 Vue Router、Pinia、axios 封装、API 类型和业务状态保持不变，所有页面直接在现有 view 内重构。新增纯函数先通过 Vitest 单元测试，再接入编辑器组件。

**Tech Stack:** Vue 3、TypeScript、Vite 8、Pinia、Vue Router、Element Plus、markdown-it、Vitest、Vue Test Utils、jsdom

---

## 文件结构

创建：

- `frontend/src/styles/tokens.css`：颜色、字体、间距、圆角、阴影、动效变量与全局基础样式。
- `frontend/src/styles/element-overrides.css`：Element Plus 组件变量的局部覆盖和通用交互状态。
- `frontend/src/components/AppShell.vue`：页面宽度与布局变体。
- `frontend/src/components/ArticleListItem.vue`：文章列表行与重点文章条目。
- `frontend/src/components/ContextRail.vue`：列表与详情共享的上下文轨道容器。
- `frontend/src/components/EditorToolbar.vue`：Markdown 格式操作栏。
- `frontend/src/components/EditorOutline.vue`：编辑器标题大纲。
- `frontend/src/utils/editor.ts`：Markdown 插入、标题解析和编辑器统计纯函数。
- `frontend/src/utils/editor.test.ts`：编辑器纯函数单元测试。
- `frontend/vitest.config.ts`：Vitest 配置。

修改：

- `frontend/package.json`：增加测试脚本和开发依赖。
- `frontend/src/main.ts`：导入全局样式。
- `frontend/src/App.vue`：页面壳、路由过渡和主布局。
- `frontend/src/components/AppHeader.vue`：导航、搜索、通知和移动端响应式。
- `frontend/src/components/CommentItem.vue`：评论视觉层级。
- `frontend/src/views/ArticleList.vue`：文章发现页。
- `frontend/src/views/ArticleDetail.vue`：阅读与互动页。
- `frontend/src/views/ArticleEdit.vue`：专业写作工作台。
- `frontend/src/views/Login.vue`：登录页。
- `frontend/src/views/Register.vue`：注册页。
- `frontend/src/views/Profile.vue`：创作账本。
- `frontend/src/assets/markdown.css`：与新视觉系统统一。

不修改：

- `frontend/src/api/**`
- `frontend/src/types/api.ts`
- `frontend/src/stores/user.ts`
- `frontend/src/router/index.ts`
- `blog/**`

## Task 1: 建立前端测试承载层

**Files:**
- Modify: `frontend/package.json`
- Create: `frontend/vitest.config.ts`

- [ ] **Step 1: 添加测试依赖和脚本**

运行：

```powershell
npm install -D vitest @vue/test-utils jsdom
```

预期：`package.json` 和 `package-lock.json` 更新，命令退出码为 `0`。

在 `frontend/package.json` 的 `scripts` 中加入：

```json
{
  "test": "vitest run",
  "test:watch": "vitest"
}
```

- [ ] **Step 2: 添加 Vitest 配置**

创建 `frontend/vitest.config.ts`：

```ts
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
  },
})
```

- [ ] **Step 3: 验证测试命令可启动**

运行：

```powershell
npm run test
```

预期：Vitest 启动并返回 `No test files found`，退出码为 `1`，说明测试运行器已生效。

- [ ] **Step 4: 提交**

```powershell
git add frontend/package.json frontend/package-lock.json frontend/vitest.config.ts
git commit -m "test(frontend): 建立 Vitest 测试基础设施"
```

## Task 2: 实现编辑器纯函数

**Files:**
- Create: `frontend/src/utils/editor.test.ts`
- Create: `frontend/src/utils/editor.ts`

- [ ] **Step 1: 写失败测试**

创建 `frontend/src/utils/editor.test.ts`：

```ts
import { describe, expect, it } from 'vitest'
import {
  countEditorStats,
  insertMarkdown,
  parseOutline,
  type MarkdownAction,
} from './editor'

describe('insertMarkdown', () => {
  it('wraps selected text for bold', () => {
    expect(insertMarkdown('hello world', 6, 11, 'bold')).toEqual({
      value: 'hello **world**',
      selectionStart: 8,
      selectionEnd: 13,
    })
  })

  it('inserts heading at the current line start', () => {
    expect(insertMarkdown('标题', 2, 2, 'heading')).toEqual({
      value: '## 标题',
      selectionStart: 3,
      selectionEnd: 3,
    })
  })

  it('inserts a link placeholder when there is no selection', () => {
    expect(insertMarkdown('', 0, 0, 'link')).toEqual({
      value: '[链接文字](https://)',
      selectionStart: 1,
      selectionEnd: 5,
    })
  })

  it('prefixes each selected line for quote', () => {
    expect(insertMarkdown('第一行\n第二行', 0, 7, 'quote')).toEqual({
      value: '> 第一行\n> 第二行',
      selectionStart: 0,
      selectionEnd: 11,
    })
  })
})

describe('parseOutline', () => {
  it('parses ATX headings and ignores fenced code blocks', () => {
    const markdown = [
      '# 一级标题',
      '',
      '```md',
      '# 代码里的标题',
      '```',
      '',
      '## 二级标题',
      '### 三级标题',
    ].join('\n')

    expect(parseOutline(markdown)).toEqual([
      { level: 1, text: '一级标题', line: 1 },
      { level: 2, text: '二级标题', line: 7 },
      { level: 3, text: '三级标题', line: 8 },
    ])
  })
})

describe('countEditorStats', () => {
  it('counts non-whitespace Unicode characters and cursor position', () => {
    expect(countEditorStats('你好 世界\n第二行', 7)).toEqual({
      characters: 7,
      line: 2,
      column: 2,
    })
  })
})

describe('MarkdownAction type', () => {
  it('accepts all supported editor actions', () => {
    const actions: MarkdownAction[] = [
      'heading',
      'bold',
      'italic',
      'quote',
      'unordered-list',
      'ordered-list',
      'link',
      'code',
    ]
    expect(actions).toHaveLength(8)
  })
})
```

- [ ] **Step 2: 运行测试确认失败**

运行：

```powershell
npm run test -- src/utils/editor.test.ts
```

预期：失败，提示无法解析 `./editor`。

- [ ] **Step 3: 实现最小纯函数**

创建 `frontend/src/utils/editor.ts`：

```ts
export type MarkdownAction =
  | 'heading'
  | 'bold'
  | 'italic'
  | 'quote'
  | 'unordered-list'
  | 'ordered-list'
  | 'link'
  | 'code'

export interface EditorSelection {
  value: string
  selectionStart: number
  selectionEnd: number
}

export interface OutlineItem {
  level: number
  text: string
  line: number
}

function replaceRange(
  value: string,
  start: number,
  end: number,
  replacement: string,
  selectedOffset = 0,
  selectedLength = 0,
): EditorSelection {
  const nextValue = value.slice(0, start) + replacement + value.slice(end)
  const selectionStart = start + selectedOffset
  return {
    value: nextValue,
    selectionStart,
    selectionEnd: selectionStart + selectedLength,
  }
}

function wrapSelection(
  value: string,
  start: number,
  end: number,
  prefix: string,
  suffix = prefix,
  placeholder = '文字',
): EditorSelection {
  const selected = value.slice(start, end) || placeholder
  return replaceRange(
    value,
    start,
    end,
    `${prefix}${selected}${suffix}`,
    prefix.length,
    selected.length,
  )
}

function prefixLines(
  value: string,
  start: number,
  end: number,
  createPrefix: (index: number) => string,
): EditorSelection {
  const lineStart = value.lastIndexOf('\n', start - 1) + 1
  const lineEnd = value.indexOf('\n', end)
  const actualEnd = lineEnd === -1 ? value.length : lineEnd
  const lines = value.slice(lineStart, actualEnd).split('\n')
  const replacement = lines.map((line, index) => `${createPrefix(index)}${line}`).join('\n')
  return replaceRange(value, lineStart, actualEnd, replacement, replacement.length, 0)
}

export function insertMarkdown(
  value: string,
  start: number,
  end: number,
  action: MarkdownAction,
): EditorSelection {
  if (action === 'bold') return wrapSelection(value, start, end, '**')
  if (action === 'italic') return wrapSelection(value, start, end, '*')
  if (action === 'code') return wrapSelection(value, start, end, '`', '`', '代码')
  if (action === 'link') {
    return wrapSelection(value, start, end, '[', '](https://)', '链接文字')
  }
  if (action === 'quote') return prefixLines(value, start, end, () => '> ')
  if (action === 'unordered-list') return prefixLines(value, start, end, () => '- ')
  if (action === 'ordered-list') return prefixLines(value, start, end, (i) => `${i + 1}. `)
  if (action === 'heading') {
    const lineStart = value.lastIndexOf('\n', start - 1) + 1
    return replaceRange(value, lineStart, lineStart, '## ', 3, 0)
  }
  return { value, selectionStart: start, selectionEnd: end }
}

export function parseOutline(markdown: string): OutlineItem[] {
  const outline: OutlineItem[] = []
  let inFence = false
  markdown.split('\n').forEach((line, index) => {
    if (/^\s*```/.test(line)) {
      inFence = !inFence
      return
    }
    if (inFence) return
    const match = /^(#{1,6})\s+(.+?)\s*$/.exec(line)
    if (!match) return
    outline.push({
      level: match[1].length,
      text: match[2],
      line: index + 1,
    })
  })
  return outline
}

export function countEditorStats(value: string, cursor: number) {
  const beforeCursor = value.slice(0, cursor)
  const lines = beforeCursor.split('\n')
  return {
    characters: Array.from(value.replace(/\s/g, '')).length,
    line: lines.length,
    column: Array.from(lines.at(-1) ?? '').length + 1,
  }
}
```

- [ ] **Step 4: 运行测试确认通过**

运行：

```powershell
npm run test -- src/utils/editor.test.ts
```

预期：`Test Files 1 passed`，所有测试通过。

- [ ] **Step 5: 提交**

```powershell
git add frontend/src/utils/editor.ts frontend/src/utils/editor.test.ts
git commit -m "feat(frontend): 添加编辑器文本工具"
```

## Task 3: 建立全局设计系统和页面壳

**Files:**
- Create: `frontend/src/styles/tokens.css`
- Create: `frontend/src/styles/element-overrides.css`
- Create: `frontend/src/components/AppShell.vue`
- Modify: `frontend/src/main.ts`
- Modify: `frontend/src/App.vue`

- [ ] **Step 1: 添加全局 token 与基础样式**

创建 `frontend/src/styles/tokens.css`：

```css
:root {
  --blog-ink: #10273d;
  --blog-ink-soft: #34485d;
  --blog-red: #e5593f;
  --blog-red-dark: #c84630;
  --blog-moss: #dceae5;
  --blog-moss-ink: #405e51;
  --blog-blueprint: #eef3f7;
  --blog-paper: #ffffff;
  --blog-line: #cad4df;
  --blog-line-soft: #e1e7ed;
  --blog-muted: #68778a;
  --blog-radius-sm: 4px;
  --blog-radius-md: 8px;
  --blog-shadow-sm: 0 6px 18px rgb(16 39 61 / 8%);
  --blog-shadow-md: 0 18px 45px rgb(16 39 61 / 12%);
  --blog-content-width: 1180px;
  --blog-workspace-width: 1440px;
  --blog-font-display: Georgia, "Noto Serif SC", "Songti SC", serif;
  --blog-font-body: "PingFang SC", "Microsoft YaHei", system-ui, sans-serif;
  --blog-font-mono: Consolas, "SFMono-Regular", monospace;
  --blog-transition: 180ms ease;
}

* {
  box-sizing: border-box;
}

html {
  background: var(--blog-blueprint);
  color: var(--blog-ink);
  font-family: var(--blog-font-body);
  scroll-behavior: smooth;
}

body {
  margin: 0;
  min-width: 320px;
  min-height: 100vh;
  background:
    linear-gradient(var(--blog-line-soft) 1px, transparent 1px) 0 0 / 100% 32px,
    var(--blog-blueprint);
}

button,
input,
textarea {
  font: inherit;
}

a {
  color: inherit;
}

:focus-visible {
  outline: 2px solid var(--blog-red);
  outline-offset: 3px;
}

@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    scroll-behavior: auto !important;
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}
```

- [ ] **Step 2: 添加 Element Plus 覆盖样式**

创建 `frontend/src/styles/element-overrides.css`：

```css
:root {
  --el-color-primary: var(--blog-ink);
  --el-color-primary-light-3: #38536c;
  --el-color-primary-light-5: #6d8194;
  --el-color-primary-light-7: #aab7c3;
  --el-color-danger: var(--blog-red);
  --el-border-color: var(--blog-line);
  --el-border-color-light: var(--blog-line-soft);
  --el-fill-color-light: var(--blog-blueprint);
  --el-fill-color-lighter: #f6f8fa;
  --el-text-color-primary: var(--blog-ink);
  --el-text-color-regular: var(--blog-ink-soft);
  --el-text-color-secondary: var(--blog-muted);
  --el-border-radius-base: var(--blog-radius-sm);
}

.el-button {
  font-weight: 650;
  transition:
    transform var(--blog-transition),
    border-color var(--blog-transition),
    background-color var(--blog-transition);
}

.el-button:not(.is-disabled):active {
  transform: translateY(1px);
}

.el-button--primary {
  --el-button-bg-color: var(--blog-ink);
  --el-button-border-color: var(--blog-ink);
  --el-button-hover-bg-color: #1c3b57;
  --el-button-hover-border-color: #1c3b57;
}

.el-input__wrapper,
.el-textarea__inner,
.el-select__wrapper {
  box-shadow: 0 0 0 1px var(--blog-line) inset;
}

.el-input__wrapper.is-focus,
.el-textarea__inner:focus,
.el-select__wrapper.is-focused {
  box-shadow: 0 0 0 1px var(--blog-ink) inset;
}

.el-card {
  border-radius: var(--blog-radius-sm);
  border-color: var(--blog-line);
  box-shadow: none;
}

.el-pagination.is-background .el-pager li.is-active {
  background: var(--blog-ink);
}
```

- [ ] **Step 3: 创建页面壳**

创建 `frontend/src/components/AppShell.vue`：

```vue
<script setup lang="ts">
withDefaults(
  defineProps<{
    variant?: 'content' | 'workspace' | 'auth'
  }>(),
  { variant: 'content' },
)
</script>

<template>
  <main class="app-shell" :class="`app-shell--${variant}`">
    <slot />
  </main>
</template>

<style scoped>
.app-shell {
  width: 100%;
  margin: 0 auto;
  padding: 28px 24px 64px;
}

.app-shell--content {
  max-width: var(--blog-content-width);
}

.app-shell--workspace {
  max-width: var(--blog-workspace-width);
  padding: 18px 18px 40px;
}

.app-shell--auth {
  max-width: 1040px;
  padding-top: 48px;
}

@media (max-width: 767px) {
  .app-shell {
    padding: 18px 14px 48px;
  }
}
</style>
```

- [ ] **Step 4: 接入全局样式和路由过渡**

修改 `frontend/src/main.ts` 的样式导入：

```ts
import './styles/tokens.css'
import './styles/element-overrides.css'
import './assets/markdown.css'
```

修改 `frontend/src/App.vue`：

```vue
<script setup lang="ts">
import AppHeader from '@/components/AppHeader.vue'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
</script>

<template>
  <el-config-provider :locale="zhCn">
    <div class="layout">
      <header class="layout-header">
        <AppHeader />
      </header>
      <router-view v-slot="{ Component }">
        <transition name="page" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </div>
  </el-config-provider>
</template>

<style scoped>
.layout {
  min-height: 100vh;
}

.layout-header {
  position: sticky;
  top: 0;
  z-index: 30;
}

.page-enter-active,
.page-leave-active {
  transition:
    opacity 180ms ease,
    transform 180ms ease;
}

.page-enter-from,
.page-leave-to {
  opacity: 0;
  transform: translateY(6px);
}
</style>
```

- [ ] **Step 5: 运行构建检查**

运行：

```powershell
npm run type-check
npm run build
```

预期：两条命令退出码均为 `0`。

- [ ] **Step 6: 提交**

```powershell
git add frontend/src/styles frontend/src/components/AppShell.vue frontend/src/main.ts frontend/src/App.vue
git commit -m "feat(frontend): 建立全局视觉设计系统"
```

## Task 4: 重设计顶部导航

**Files:**
- Modify: `frontend/src/components/AppHeader.vue`

- [ ] **Step 1: 重构导航模板**

将 `AppHeader.vue` 的模板替换为以下结构，保留现有脚本方法和通知逻辑：

```vue
<template>
  <div class="app-header">
    <div class="header-inner">
      <button class="brand" type="button" @click="router.push('/articles')">我的博客</button>
      <nav class="primary-nav" aria-label="文章导航">
        <button type="button" class="nav-link is-active" @click="router.push('/articles')">发现</button>
        <button type="button" class="nav-link" @click="router.push({ path: '/articles', query: { sort: 'hot' } })">热榜</button>
      </nav>
      <div class="search-wrap">
        <el-input v-model="keyword" class="search" placeholder="搜索文章、作者或标签" clearable @keyup.enter="onSearch">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </div>
      <div class="actions">
        <template v-if="userStore.isLoggedIn">
          <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99">
            <el-dropdown placement="bottom-end" popper-class="notify-popper" @visible-change="(v: boolean) => v && loadNotifications()">
              <button class="icon-button" type="button" aria-label="查看通知">
                <el-icon :size="19"><Bell /></el-icon>
              </button>
              <template #dropdown>
                <el-dropdown-menu>
                  <div class="notify-head">
                    <span>通知</span>
                    <span class="notify-head-actions">
                      <el-button v-if="unreadCount > 0" link type="primary" size="small" @click="onMarkAllRead">全部已读</el-button>
                      <el-button v-if="notifyList.length > 0" link type="danger" size="small" @click="onClearNotifications">清空</el-button>
                    </span>
                  </div>
                  <el-empty v-if="notifyList.length === 0" description="暂无通知" :image-size="60" />
                  <el-dropdown-item v-for="n in notifyList" :key="n.id" class="notify-item" @click="$router.push(`/articles/${n.articleId}`)">
                    <span class="notify-dot" :class="{ unread: n.isRead === 0 }" />
                    {{ n.senderNickname || '匿名' }}
                    {{ n.type === 'LIKE' ? '赞了你的文章' : (n.isRead === 0 ? '评论/回复了你' : '评论了你') }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </el-badge>
          <el-button class="publish-button" type="primary" @click="router.push('/publish')">写文章</el-button>
          <el-dropdown @command="onUserCommand">
            <button class="user-trigger" type="button">
              <span class="user-avatar">{{ (userStore.user?.nickname || userStore.user?.username || '?').charAt(0) }}</span>
              <el-icon><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <el-button @click="router.push('/login')">登录</el-button>
          <el-button type="primary" @click="router.push('/register')">注册</el-button>
        </template>
      </div>
    </div>
  </div>
</template>
```

- [ ] **Step 2: 替换导航样式**

将 scoped 样式替换为：

```css
.app-header {
  border-bottom: 1px solid var(--blog-line);
  background: rgb(255 255 255 / 94%);
  backdrop-filter: blur(12px);
}

.header-inner {
  max-width: var(--blog-content-width);
  height: 64px;
  margin: 0 auto;
  padding: 0 24px;
  display: flex;
  align-items: center;
  gap: 24px;
}

.brand,
.nav-link,
.icon-button,
.user-trigger {
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
}

.brand {
  font-family: var(--blog-font-display);
  font-size: 19px;
  font-weight: 700;
}

.primary-nav {
  display: inline-flex;
  align-self: stretch;
}

.nav-link {
  padding: 0 4px;
  color: var(--blog-muted);
  font-size: 13px;
}

.nav-link.is-active {
  color: var(--blog-ink);
  box-shadow: inset 0 -2px var(--blog-red);
}

.search-wrap {
  flex: 1;
  display: flex;
  justify-content: flex-end;
}

.search {
  max-width: 360px;
}

.actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.icon-button,
.user-trigger {
  min-width: 40px;
  min-height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.user-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--blog-moss);
  color: var(--blog-moss-ink);
  font-weight: 700;
}

.notify-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 12px;
  border-bottom: 1px solid var(--blog-line-soft);
}

.notify-head-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.notify-item {
  white-space: normal;
  line-height: 1.5;
}

.notify-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-right: 6px;
  border-radius: 50%;
  background: transparent;
}

.notify-dot.unread {
  background: var(--blog-red);
}

@media (max-width: 900px) {
  .primary-nav {
    display: none;
  }

  .header-inner {
    gap: 10px;
    padding: 0 14px;
  }

  .search {
    max-width: none;
  }
}

@media (max-width: 640px) {
  .header-inner {
    height: auto;
    min-height: 58px;
    flex-wrap: wrap;
    padding-block: 8px;
  }

  .search-wrap {
    order: 3;
    flex-basis: 100%;
  }

  .publish-button {
    display: none;
  }
}
```

- [ ] **Step 3: 验证类型与样式**

运行：

```powershell
npm run type-check
npm run lint
```

预期：退出码均为 `0`。

- [ ] **Step 4: 提交**

```powershell
git add frontend/src/components/AppHeader.vue
git commit -m "feat(frontend): 重构顶部导航"
```

## Task 5: 重设计文章列表

**Files:**
- Create: `frontend/src/components/ArticleListItem.vue`
- Create: `frontend/src/components/ContextRail.vue`
- Modify: `frontend/src/views/ArticleList.vue`

- [ ] **Step 1: 创建文章条目组件**

创建 `frontend/src/components/ArticleListItem.vue`：

```vue
<script setup lang="ts">
import { View, Star } from '@element-plus/icons-vue'
import type { ArticleSearchVO, ArticleVO } from '@/types/api'
import { formatDateTime, highlightHtml } from '@/utils/format'

type ListItem = ArticleVO & Partial<Pick<ArticleSearchVO, 'contentSnippet'>>

defineProps<{
  article: ListItem
  featured?: boolean
}>()

defineEmits<{
  open: [id: number]
}>()
</script>

<template>
  <article class="article-item" :class="{ featured }" @click="$emit('open', article.id)">
    <div class="eyebrow">{{ article.category || '随笔' }}</div>
    <div class="body">
      <h2 class="title" v-html="highlightHtml(article.title)" />
      <p v-if="article.contentSnippet" class="snippet" v-html="highlightHtml(article.contentSnippet)" />
    </div>
    <div class="meta">
      <span>{{ article.author?.nickname || '匿名' }}</span>
      <span>{{ formatDateTime(article.createTime) }}</span>
      <span><el-icon><View /></el-icon>{{ article.viewCount }}</span>
      <span><el-icon><Star /></el-icon>{{ article.likeCount }}</span>
    </div>
  </article>
</template>

<style scoped>
.article-item {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr) 110px;
  gap: 18px;
  padding: 22px 4px;
  border-bottom: 1px solid var(--blog-line);
  cursor: pointer;
  transition: padding var(--blog-transition), color var(--blog-transition);
}

.article-item:hover {
  padding-left: 10px;
  color: var(--blog-red);
}

.article-item.featured {
  display: block;
  padding: 28px;
  border: 0;
  background: var(--blog-ink);
  color: var(--blog-paper);
  box-shadow: var(--blog-shadow-sm);
}

.eyebrow,
.meta,
.snippet {
  color: var(--blog-muted);
  font-family: var(--blog-font-mono);
  font-size: 11px;
}

.featured .eyebrow {
  color: #9fc0b4;
  letter-spacing: 0.12em;
}

.title {
  margin: 0;
  font-family: var(--blog-font-display);
  font-size: 20px;
  line-height: 1.35;
}

.featured .title {
  max-width: 680px;
  margin-top: 10px;
  font-size: 34px;
  line-height: 1.22;
}

.snippet {
  margin: 9px 0 0;
  font-family: var(--blog-font-body);
  line-height: 1.7;
}

.featured .snippet {
  color: #c6d4df;
}

.meta {
  display: flex;
  justify-content: flex-end;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 8px 12px;
}

.featured .meta {
  justify-content: flex-start;
  margin-top: 18px;
  color: #a9becc;
}

.meta span {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.title :deep(em),
.snippet :deep(em) {
  color: var(--blog-red);
  font-style: normal;
}

@media (max-width: 767px) {
  .article-item {
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .meta {
    justify-content: flex-start;
  }

  .featured .title {
    font-size: 27px;
  }
}
</style>
```

- [ ] **Step 2: 创建上下文轨道组件**

创建 `frontend/src/components/ContextRail.vue`：

```vue
<script setup lang="ts">
defineProps<{
  label?: string
}>()
</script>

<template>
  <aside class="context-rail">
    <div class="rail-label">{{ label || 'CONTEXT RAIL' }}</div>
    <div class="rail-line" />
    <slot />
  </aside>
</template>

<style scoped>
.context-rail {
  padding: 24px 20px;
  border-left: 1px solid #b8cbc2;
  background: var(--blog-moss);
  color: var(--blog-moss-ink);
}

.rail-label {
  font-family: var(--blog-font-mono);
  font-size: 10px;
  letter-spacing: 0.12em;
}

.rail-line {
  height: 1px;
  margin: 12px 0 18px;
  background: #b8cbc2;
}

@media (max-width: 1199px) {
  .context-rail {
    border-left: 0;
    border-top: 1px solid #b8cbc2;
  }
}
</style>
```

- [ ] **Step 3: 重构文章列表模板**

在 `ArticleList.vue` 中把排序初始化改为：

```ts
const sort = ref<'time' | 'hot'>(route.query.sort === 'hot' ? 'hot' : 'time')
```

并在关键词监听后增加：

```ts
watch(
  () => route.query.sort,
  (value) => {
    sort.value = value === 'hot' ? 'hot' : 'time'
  },
)
```

其余脚本逻辑保持不变，将模板替换为：

```vue
<template>
  <AppShell>
    <div class="article-list">
      <div class="list-layout">
        <section class="main-column" aria-label="文章列表">
          <div class="toolbar">
            <el-radio-group v-model="sort" :disabled="!!keyword.trim()">
              <el-radio-button value="time">最新</el-radio-button>
              <el-radio-button value="hot">热门</el-radio-button>
            </el-radio-group>
            <el-tag v-if="keyword.trim()" closable @close="onClearKeyword">搜索：{{ keyword }}</el-tag>
          </div>

          <el-empty
            v-if="!loading && list.length === 0"
            :description="keyword.trim() ? '没有找到相关文章' : '暂时还没有文章'"
          >
            <el-button v-if="keyword.trim()" type="primary" @click="onClearKeyword">清除搜索</el-button>
            <el-button v-else type="primary" @click="$router.push('/publish')">写第一篇文章</el-button>
          </el-empty>

          <el-skeleton v-if="loading && list.length === 0" :rows="8" animated />
          <template v-else>
            <ArticleListItem
              v-if="list[0]"
              :article="list[0]"
              featured
              @open="$router.push(`/articles/${$event}`)"
            />
            <ArticleListItem
              v-for="item in list.slice(1)"
              :key="item.id"
              :article="item"
              @open="$router.push(`/articles/${$event}`)"
            />
          </template>

          <el-pagination
            v-model:current-page="pageNum"
            v-model:page-size="pageSize"
            :total="total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            class="pagination"
          />
        </section>

        <ContextRail>
          <section class="rail-section">
            <h3>热门文章</h3>
            <button
              v-for="(article, index) in hotList"
              :key="article.id"
              type="button"
              class="hot-item"
              @click="$router.push(`/articles/${article.id}`)"
            >
              <span>{{ String(index + 1).padStart(2, '0') }}</span>
              <strong>{{ article.title }}</strong>
            </button>
          </section>
          <section class="rail-section">
            <h3>主题索引</h3>
            <div class="topic-list">
              <span v-for="topic in ['技术', '思考', '写作', '生活']" :key="topic">{{ topic }}</span>
            </div>
          </section>
        </ContextRail>
      </div>
    </div>
  </AppShell>
</template>
```

在 script setup 中增加导入：

```ts
import AppShell from '@/components/AppShell.vue'
import ArticleListItem from '@/components/ArticleListItem.vue'
import ContextRail from '@/components/ContextRail.vue'
```

- [ ] **Step 4: 替换文章列表样式**

将 `ArticleList.vue` 的 scoped 样式替换为：

```css
.list-layout {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(280px, 0.9fr);
  border: 1px solid var(--blog-line);
  background: var(--blog-paper);
}

.main-column {
  min-width: 0;
  padding: 24px 28px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.rail-section + .rail-section {
  margin-top: 28px;
}

.rail-section h3 {
  margin: 0 0 12px;
  font-size: 13px;
}

.hot-item {
  width: 100%;
  display: grid;
  grid-template-columns: 30px 1fr;
  gap: 8px;
  padding: 10px 0;
  border: 0;
  border-bottom: 1px solid #cbdad3;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.hot-item span {
  font-family: var(--blog-font-mono);
  color: var(--blog-red);
}

.hot-item strong {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.55;
}

.topic-list {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.topic-list span {
  padding: 6px 9px;
  background: #d4e5de;
  font-size: 11px;
}

.pagination {
  justify-content: center;
  margin-top: 24px;
}

@media (max-width: 1199px) {
  .list-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 767px) {
  .main-column {
    padding: 18px 14px;
  }

  .toolbar {
    align-items: flex-start;
    flex-direction: column;
  }
}
```

- [ ] **Step 5: 验证并提交**

运行：

```powershell
npm run type-check
npm run lint
npm run test
```

预期：退出码均为 `0`。

```powershell
git add frontend/src/components/ArticleListItem.vue frontend/src/components/ContextRail.vue frontend/src/views/ArticleList.vue
git commit -m "feat(frontend): 重设计文章发现页"
```

## Task 6: 重设计文章详情

**Files:**
- Modify: `frontend/src/views/ArticleDetail.vue`
- Modify: `frontend/src/components/CommentItem.vue`

- [ ] **Step 1: 重构详情模板**

保留 `ArticleDetail.vue` 的脚本逻辑，将模板替换为：

```vue
<template>
  <AppShell>
    <div v-loading="loading" class="article-detail">
      <template v-if="article">
        <div class="detail-layout">
          <article class="article-main">
            <header class="article-header">
              <div class="article-kicker">
                {{ article.category || '随笔' }}
                <span v-if="article.tags">{{ article.tags }}</span>
              </div>
              <h1 class="title">{{ article.title }}</h1>
              <div class="meta">
                <span>{{ article.author?.nickname || '匿名' }}</span>
                <span>{{ formatDateTime(article.createTime) }}</span>
                <span>浏览 {{ article.viewCount }}</span>
                <el-tag v-if="article.status === 0" size="small" type="warning">草稿</el-tag>
                <el-tag v-else-if="article.hidden === 1" size="small" type="warning">已隐藏</el-tag>
                <span v-if="isAuthor" class="owner-actions">
                  <el-button link type="primary" @click="router.push(`/publish/${article.id}`)">编辑</el-button>
                  <el-button link type="danger" @click="onDelete">删除</el-button>
                </span>
              </div>
            </header>

            <div class="markdown-body" v-html="renderedContent" />
          </article>

          <ContextRail label="IN THIS ARTICLE">
            <section class="rail-block">
              <h3>作者</h3>
              <strong>{{ article.author?.nickname || '匿名' }}</strong>
            </section>
            <section class="rail-block">
              <h3>标签</h3>
              <div class="tag-list">
                <span v-for="tag in (article.tags || '').split(',').filter((t) => t.trim())" :key="tag">
                  {{ tag.trim() }}
                </span>
              </div>
            </section>
            <section class="rail-block">
              <h3>互动</h3>
              <el-button :type="liked ? 'primary' : 'default'" class="rail-action" @click="onToggleLike">
                <el-icon><Star /></el-icon>
                点赞 {{ article.likeCount }}
              </el-button>
              <el-button :type="favorited ? 'warning' : 'default'" class="rail-action" @click="onToggleFavorite">
                <el-icon><CollectionTag /></el-icon>
                {{ favorited ? '已收藏' : '收藏' }}
              </el-button>
            </section>
          </ContextRail>
        </div>

        <section class="comments-section">
          <h2>评论（{{ countComments(comments) }}）</h2>
          <div v-if="replyTo" class="reply-banner">
            回复 @{{ replyTo.author?.nickname || '匿名' }}
            <el-button link size="small" @click="cancelReply">取消</el-button>
          </div>
          <div class="comment-editor">
            <el-input
              v-model="commentContent"
              type="textarea"
              :rows="3"
              maxlength="1000"
              show-word-limit
              placeholder="写下你的评论"
            />
            <el-button type="primary" :loading="submitting" @click="onSubmitComment">发表评论</el-button>
          </div>
          <el-empty v-if="comments.length === 0" description="还没有评论，来抢沙发" :image-size="80" />
          <CommentItem
            v-for="c in comments"
            :key="c.id"
            :comment="c"
            @reply="onReply"
            @delete="onDeleteComment"
          />
        </section>

        <div class="mobile-actions">
          <button type="button" @click="onToggleLike">点赞 {{ article.likeCount }}</button>
          <button type="button" @click="onToggleFavorite">{{ favorited ? '已收藏' : '收藏' }}</button>
          <button type="button" @click="focusCommentEditor">评论</button>
        </div>
      </template>
    </div>
  </AppShell>
</template>
```

增加导入：

```ts
import AppShell from '@/components/AppShell.vue'
import ContextRail from '@/components/ContextRail.vue'
```

并增加评论聚焦方法：

```ts
function focusCommentEditor() {
  document.querySelector<HTMLTextAreaElement>('.comment-editor textarea')?.focus()
}
```

- [ ] **Step 2: 添加详情样式**

将 `ArticleDetail.vue` 的 scoped 样式替换为：

```css
.detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(280px, 0.9fr);
  border: 1px solid var(--blog-line);
  background: var(--blog-paper);
}

.article-main {
  min-width: 0;
  padding: 46px 56px 56px;
}

.article-kicker,
.meta {
  color: var(--blog-muted);
  font-family: var(--blog-font-mono);
  font-size: 11px;
}

.article-kicker {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  color: var(--blog-red);
}

.title {
  margin: 14px 0 16px;
  font-family: var(--blog-font-display);
  font-size: clamp(34px, 4vw, 54px);
  line-height: 1.15;
}

.meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding-bottom: 22px;
  border-bottom: 1px solid var(--blog-line);
}

.owner-actions {
  margin-left: auto;
}

.markdown-body {
  padding-top: 26px;
  font-size: 17px;
  line-height: 1.9;
}

.rail-block + .rail-block {
  margin-top: 26px;
}

.rail-block h3 {
  margin: 0 0 10px;
  font-size: 12px;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.tag-list span {
  padding: 5px 8px;
  background: #d4e5de;
  font-size: 11px;
}

.rail-action {
  width: 100%;
  margin: 0 0 8px;
}

.comments-section {
  max-width: 840px;
  margin-top: 28px;
  padding: 28px;
  border: 1px solid var(--blog-line);
  background: var(--blog-paper);
}

.comments-section h2 {
  margin-top: 0;
  font-family: var(--blog-font-display);
}

.reply-banner {
  margin-bottom: 10px;
  padding: 10px 12px;
  background: var(--blog-blueprint);
  font-size: 13px;
}

.comment-editor {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
  margin-bottom: 20px;
}

.comment-editor :deep(.el-textarea) {
  width: 100%;
}

.mobile-actions {
  display: none;
}

@media (max-width: 1199px) {
  .detail-layout {
    grid-template-columns: 1fr;
  }

  .article-main {
    padding: 38px 42px 48px;
  }
}

@media (max-width: 767px) {
  .article-main {
    padding: 26px 18px 36px;
  }

  .title {
    font-size: 34px;
  }

  .comments-section {
    padding: 20px 16px 90px;
  }

  .mobile-actions {
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 40;
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    min-height: 58px;
    background: var(--blog-ink);
  }

  .mobile-actions button {
    border: 0;
    background: transparent;
    color: var(--blog-paper);
    cursor: pointer;
  }
}
```

- [ ] **Step 3: 调整评论组件视觉**

将 `CommentItem.vue` 的样式替换为：

```css
.comment-item {
  padding: 16px 0;
  border-bottom: 1px solid var(--blog-line-soft);
}

.head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.nickname {
  font-weight: 700;
}

.reply-to {
  color: var(--blog-red);
  font-size: 12px;
}

.time {
  color: var(--blog-muted);
  font-family: var(--blog-font-mono);
  font-size: 10px;
}

.actions {
  margin-left: auto;
  display: inline-flex;
}

.content {
  margin: 9px 0 0 36px;
  line-height: 1.75;
  white-space: pre-wrap;
}

.children {
  margin-left: 36px;
}

.deep .children {
  margin-left: 0;
}

@media (max-width: 767px) {
  .head {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .actions {
    flex-basis: 100%;
    margin-left: 36px;
  }
}
```

- [ ] **Step 4: 验证并提交**

运行：

```powershell
npm run type-check
npm run lint
```

预期：退出码均为 `0`。

```powershell
git add frontend/src/views/ArticleDetail.vue frontend/src/components/CommentItem.vue
git commit -m "feat(frontend): 重设计文章阅读与互动页"
```

## Task 7: 重设计登录与注册

**Files:**
- Modify: `frontend/src/views/Login.vue`
- Modify: `frontend/src/views/Register.vue`

- [ ] **Step 1: 重构登录模板**

保留 `Login.vue` 的脚本逻辑，将模板替换为：

```vue
<template>
  <AppShell variant="auth">
    <div class="auth-layout">
      <section class="auth-brand" aria-label="博客介绍">
        <div class="brand-kicker">READ · WRITE · RESPOND</div>
        <h1>写作是一张持续展开的索引</h1>
        <p>记录想法，整理判断，与读者建立长期的交流。</p>
      </section>
      <section class="auth-form-panel">
        <div class="form-heading">
          <p class="brand-kicker">WELCOME BACK</p>
          <h2>欢迎回来</h2>
          <p>继续阅读和写作</p>
        </div>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="0" size="large">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="用户名" />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              placeholder="密码"
              @keyup.enter="onSubmit"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" class="submit-btn" :loading="loading" @click="onSubmit">登录</el-button>
          </el-form-item>
        </el-form>
        <div class="footer-link">还没有账号？<router-link to="/register">去注册</router-link></div>
      </section>
    </div>
  </AppShell>
</template>
```

增加导入：

```ts
import AppShell from '@/components/AppShell.vue'
```

- [ ] **Step 2: 添加登录样式**

将 scoped 样式替换为：

```css
.auth-layout {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  min-height: 560px;
  border: 1px solid var(--blog-line);
  background: var(--blog-paper);
}

.auth-brand {
  padding: 54px 48px;
  background: var(--blog-ink);
  color: var(--blog-paper);
}

.brand-kicker {
  color: var(--blog-red);
  font-family: var(--blog-font-mono);
  font-size: 10px;
  letter-spacing: 0.14em;
}

.auth-brand .brand-kicker {
  color: #9fc0b4;
}

.auth-brand h1 {
  margin: 18px 0 22px;
  font-family: var(--blog-font-display);
  font-size: 42px;
  line-height: 1.3;
}

.auth-brand p {
  max-width: 330px;
  color: #b8c9d7;
  line-height: 1.8;
}

.auth-form-panel {
  align-self: center;
  padding: 54px 72px;
}

.form-heading h2 {
  margin: 8px 0 6px;
  font-family: var(--blog-font-display);
  font-size: 34px;
}

.form-heading p:last-child {
  margin: 0 0 28px;
  color: var(--blog-muted);
}

.submit-btn {
  width: 100%;
}

.footer-link {
  color: var(--blog-muted);
  font-size: 13px;
  text-align: center;
}

.footer-link a {
  color: var(--blog-red);
}

@media (max-width: 767px) {
  .auth-layout {
    grid-template-columns: 1fr;
  }

  .auth-brand {
    padding: 26px 22px;
  }

  .auth-brand h1 {
    margin: 10px 0 0;
    font-size: 27px;
  }

  .auth-brand p {
    display: none;
  }

  .auth-form-panel {
    padding: 34px 22px 42px;
  }
}
```

- [ ] **Step 3: 对注册页应用同一结构**

在 `Register.vue` 中导入 `AppShell`，将模板按登录页结构替换。品牌区文案改为：

```html
<div class="brand-kicker">START YOUR INDEX</div>
<h1>从第一篇文章开始建立自己的知识索引</h1>
<p>公开写作、持续记录，并与读者保持交流。</p>
```

表单标题改为：

```html
<p class="brand-kicker">CREATE ACCOUNT</p>
<h2>创建账号</h2>
<p>开始你的写作与阅读记录</p>
```

注册页样式与登录页一致，并在底部保留：

```html
<div class="footer-link">已有账号？<router-link to="/login">去登录</router-link></div>
```

- [ ] **Step 4: 验证并提交**

运行：

```powershell
npm run type-check
npm run lint
```

预期：退出码均为 `0`。

```powershell
git add frontend/src/views/Login.vue frontend/src/views/Register.vue
git commit -m "feat(frontend): 重设计登录与注册页"
```

## Task 8: 重设计个人中心

**Files:**
- Modify: `frontend/src/views/Profile.vue`

- [ ] **Step 1: 重构个人中心模板**

保留现有脚本方法，将模板替换为：

```vue
<template>
  <AppShell>
    <div class="profile">
      <header class="profile-header">
        <div>
          <p class="ledger-kicker">CREATOR LEDGER</p>
          <h1>你好，{{ userStore.user?.nickname || userStore.user?.username }}</h1>
        </div>
        <div class="profile-actions">
          <el-button type="primary" @click="router.push('/publish')">写新文章</el-button>
        </div>
      </header>

      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane label="我的文章" name="articles">
          <el-skeleton v-if="myLoading && myList.length === 0" :rows="6" animated />
          <el-empty v-else-if="myList.length === 0" description="你还没有文章">
            <el-button type="primary" @click="router.push('/publish')">写第一篇文章</el-button>
          </el-empty>
          <div v-else class="ledger">
            <div class="ledger-head">
              <span>文章</span><span>状态</span><span>数据</span><span>操作</span>
            </div>
            <article v-for="row in myList" :key="row.id" class="ledger-row">
              <div class="ledger-title">
                <button type="button" @click="router.push(`/articles/${row.id}`)">{{ row.title }}</button>
                <span>{{ row.category || '未分类' }} · {{ formatDateTime(row.createTime) }}</span>
              </div>
              <el-tag v-if="row.status === 0" size="small" type="warning">草稿</el-tag>
              <el-tag v-else-if="row.hidden === 1" size="small" type="info">已隐藏</el-tag>
              <el-tag v-else size="small" type="success">已发布</el-tag>
              <div class="ledger-data">{{ row.viewCount }} 浏览<br />{{ row.likeCount }} 点赞</div>
              <div class="ledger-actions">
                <el-button link type="primary" @click="router.push(`/publish/${row.id}`)">编辑</el-button>
                <el-button v-if="row.status === 1 && row.hidden !== 1" link type="warning" @click="onToggleHide(row)">隐藏</el-button>
                <el-button v-else-if="row.status === 1 && row.hidden === 1" link type="success" @click="onToggleHide(row)">公开</el-button>
                <el-button link type="danger" @click="onDeleteArticle(row)">删除</el-button>
              </div>
            </article>
          </div>
          <el-pagination
            v-model:current-page="myPage.pageNum"
            v-model:page-size="myPage.pageSize"
            :total="myTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            class="pagination"
            @current-change="loadMyArticles"
            @size-change="myPage.pageNum = 1; loadMyArticles()"
          />
        </el-tab-pane>

        <el-tab-pane label="我的收藏" name="favorites">
          <el-skeleton v-if="favLoading && favList.length === 0" :rows="6" animated />
          <el-empty v-else-if="favList.length === 0" description="你还没有收藏文章" />
          <div v-else class="ledger">
            <div class="ledger-head favorites-head">
              <span>文章</span><span>作者</span><span>收藏时间</span><span>操作</span>
            </div>
            <article v-for="row in favList" :key="row.id" class="ledger-row favorites-row">
              <div class="ledger-title">
                <button type="button" @click="router.push(`/articles/${row.articleId}`)">{{ row.title }}</button>
                <span>发布于 {{ formatDateTime(row.articleCreateTime) }}</span>
              </div>
              <span>{{ row.author?.nickname || '匿名' }}</span>
              <span class="ledger-data">{{ formatDateTime(row.favoriteTime) }}</span>
              <div class="ledger-actions">
                <el-button link type="danger" @click="onCancelFavorite(row)">取消收藏</el-button>
              </div>
            </article>
          </div>
          <el-pagination
            v-model:current-page="favPage.pageNum"
            v-model:page-size="favPage.pageSize"
            :total="favTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            class="pagination"
            @current-change="loadFavorites"
            @size-change="favPage.pageNum = 1; loadFavorites()"
          />
        </el-tab-pane>
      </el-tabs>
    </div>
  </AppShell>
</template>
```

增加导入：

```ts
import AppShell from '@/components/AppShell.vue'
```

- [ ] **Step 2: 添加创作账本样式**

将 scoped 样式替换为：

```css
.profile {
  border: 1px solid var(--blog-line);
  background: var(--blog-paper);
}

.profile-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  padding: 30px 32px 24px;
  border-bottom: 1px solid var(--blog-line);
  background: var(--blog-blueprint);
}

.ledger-kicker {
  margin: 0 0 8px;
  color: var(--blog-red);
  font-family: var(--blog-font-mono);
  font-size: 10px;
  letter-spacing: 0.12em;
}

.profile-header h1 {
  margin: 0;
  font-family: var(--blog-font-display);
  font-size: 34px;
}

:deep(.el-tabs__header) {
  margin: 0;
  padding: 0 32px;
}

:deep(.el-tabs__content) {
  padding: 26px 32px 32px;
}

.ledger-head,
.ledger-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 90px 110px 190px;
  gap: 16px;
  align-items: center;
}

.ledger-head {
  padding: 0 4px 10px;
  border-bottom: 1px solid var(--blog-line);
  color: var(--blog-muted);
  font-family: var(--blog-font-mono);
  font-size: 10px;
}

.ledger-row {
  padding: 16px 4px;
  border-bottom: 1px solid var(--blog-line-soft);
}

.ledger-title {
  min-width: 0;
}

.ledger-title button {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--blog-ink);
  font-weight: 700;
  text-align: left;
  cursor: pointer;
}

.ledger-title span,
.ledger-data {
  display: block;
  margin-top: 6px;
  color: var(--blog-muted);
  font-family: var(--blog-font-mono);
  font-size: 10px;
  line-height: 1.6;
}

.ledger-actions {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
}

.pagination {
  justify-content: center;
  margin-top: 22px;
}

@media (max-width: 767px) {
  .profile-header {
    align-items: flex-start;
    flex-direction: column;
    padding: 22px 18px;
  }

  .profile-header h1 {
    font-size: 27px;
  }

  :deep(.el-tabs__header) {
    padding: 0 18px;
  }

  :deep(.el-tabs__content) {
    padding: 18px;
  }

  .ledger-head {
    display: none;
  }

  .ledger-row {
    grid-template-columns: 1fr auto;
    gap: 10px;
  }

  .ledger-title {
    grid-column: 1 / -1;
  }

  .ledger-actions {
    justify-content: flex-start;
  }
}
```

- [ ] **Step 3: 验证并提交**

运行：

```powershell
npm run type-check
npm run lint
```

预期：退出码均为 `0`。

```powershell
git add frontend/src/views/Profile.vue
git commit -m "feat(frontend): 重设计个人创作账本"
```

## Task 9: 实现专业编辑器组件

**Files:**
- Create: `frontend/src/components/EditorToolbar.vue`
- Create: `frontend/src/components/EditorOutline.vue`

- [ ] **Step 1: 创建格式工具栏**

创建 `frontend/src/components/EditorToolbar.vue`：

```vue
<script setup lang="ts">
import type { MarkdownAction } from '@/utils/editor'

defineEmits<{
  apply: [action: MarkdownAction]
}>()

const actions: Array<{ action: MarkdownAction; label: string; title: string }> = [
  { action: 'heading', label: 'H2', title: '二级标题' },
  { action: 'bold', label: 'B', title: '粗体' },
  { action: 'italic', label: 'I', title: '斜体' },
  { action: 'quote', label: '引用', title: '引用' },
  { action: 'unordered-list', label: '• 列表', title: '无序列表' },
  { action: 'ordered-list', label: '1. 列表', title: '有序列表' },
  { action: 'link', label: '链接', title: '链接' },
  { action: 'code', label: '</>', title: '行内代码' },
]
</script>

<template>
  <div class="editor-toolbar" role="toolbar" aria-label="Markdown 格式">
    <button
      v-for="item in actions"
      :key="item.action"
      type="button"
      :title="item.title"
      :aria-label="item.title"
      @click="$emit('apply', item.action)"
    >
      {{ item.label }}
    </button>
  </div>
</template>

<style scoped>
.editor-toolbar {
  min-height: 42px;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0 14px;
  border-bottom: 1px solid var(--blog-line);
  background: #f3f6f8;
  overflow-x: auto;
}

button {
  flex: 0 0 auto;
  min-width: 34px;
  min-height: 30px;
  padding: 0 8px;
  border: 1px solid transparent;
  background: transparent;
  color: var(--blog-ink-soft);
  cursor: pointer;
}

button:hover {
  border-color: var(--blog-line);
  background: var(--blog-paper);
}
</style>
```

- [ ] **Step 2: 创建标题大纲**

创建 `frontend/src/components/EditorOutline.vue`：

```vue
<script setup lang="ts">
import type { OutlineItem } from '@/utils/editor'

defineProps<{
  items: OutlineItem[]
}>()

defineEmits<{
  navigate: [line: number]
}>()
</script>

<template>
  <section class="editor-outline" aria-label="文章大纲">
    <div class="outline-label">OUTLINE</div>
    <p v-if="items.length === 0" class="empty">输入 Markdown 标题后自动生成大纲。</p>
    <button
      v-for="item in items"
      :key="`${item.line}-${item.text}`"
      type="button"
      :style="{ paddingLeft: `${8 + (item.level - 1) * 12}px` }"
      @click="$emit('navigate', item.line)"
    >
      {{ item.text }}
    </button>
  </section>
</template>

<style scoped>
.editor-outline {
  padding: 14px;
  border-right: 1px solid var(--blog-line);
  background: var(--blog-blueprint);
}

.outline-label {
  margin-bottom: 12px;
  color: var(--blog-muted);
  font-family: var(--blog-font-mono);
  font-size: 9px;
  letter-spacing: 0.12em;
}

.empty {
  color: var(--blog-muted);
  font-size: 11px;
  line-height: 1.7;
}

button {
  width: 100%;
  padding: 7px 8px;
  border: 0;
  background: transparent;
  color: var(--blog-ink-soft);
  font-size: 11px;
  line-height: 1.5;
  text-align: left;
  cursor: pointer;
}

button:hover {
  background: var(--blog-paper);
  color: var(--blog-ink);
}
</style>
```

- [ ] **Step 3: 运行构建检查**

运行：

```powershell
npm run type-check
npm run test
```

预期：退出码均为 `0`。

- [ ] **Step 4: 提交**

```powershell
git add frontend/src/components/EditorToolbar.vue frontend/src/components/EditorOutline.vue
git commit -m "feat(frontend): 添加写作编辑器组件"
```

## Task 10: 重构文章编辑器

**Files:**
- Modify: `frontend/src/views/ArticleEdit.vue`

- [ ] **Step 1: 增加编辑状态与纯函数接入**

在 `ArticleEdit.vue` 的脚本 setup 中加入：

```ts
import { nextTick } from 'vue'
import type { MarkdownAction } from '@/utils/editor'
import { countEditorStats, insertMarkdown, parseOutline } from '@/utils/editor'
import AppShell from '@/components/AppShell.vue'
import EditorToolbar from '@/components/EditorToolbar.vue'
import EditorOutline from '@/components/EditorOutline.vue'

const contentTextarea = ref<HTMLTextAreaElement | null>(null)
const cursorPosition = ref(0)
const mobilePanel = ref<'edit' | 'preview' | 'outline' | 'info'>('edit')

const outline = computed(() => parseOutline(form.content))
const editorStats = computed(() => countEditorStats(form.content, cursorPosition.value))

function onContentBlur(event: FocusEvent) {
  cursorPosition.value = (event.target as HTMLTextAreaElement).selectionStart
}

function onContentSelect(event: Event) {
  cursorPosition.value = (event.target as HTMLTextAreaElement).selectionStart
}

async function applyMarkdown(action: MarkdownAction) {
  const textarea = contentTextarea.value
  if (!textarea) return
  const result = insertMarkdown(
    form.content,
    textarea.selectionStart,
    textarea.selectionEnd,
    action,
  )
  form.content = result.value
  await nextTick()
  textarea.focus()
  textarea.setSelectionRange(result.selectionStart, result.selectionEnd)
  cursorPosition.value = result.selectionEnd
}

async function navigateToLine(line: number) {
  const textarea = contentTextarea.value
  if (!textarea) return
  const lines = form.content.split('\n')
  const offset = lines.slice(0, line - 1).join('\n').length + (line > 1 ? 1 : 0)
  textarea.focus()
  textarea.setSelectionRange(offset, offset)
  cursorPosition.value = offset
  mobilePanel.value = 'edit'
}

function onCursorUpdate(event: Event) {
  cursorPosition.value = (event.target as HTMLTextAreaElement).selectionStart
}
```

- [ ] **Step 2: 替换编辑器模板**

将 `ArticleEdit.vue` 的模板替换为：

```vue
<template>
  <AppShell variant="workspace">
    <div v-loading="pageLoading" class="editor-app">
      <header class="document-bar">
        <el-button link @click="router.back()">返回</el-button>
        <span class="document-state">{{ articleId ? 'EDITING' : 'DRAFT' }}</span>
        <strong class="document-title">{{ form.title || '未命名文章' }}</strong>
        <span class="document-count">{{ editorStats.characters }} 字</span>
        <el-button v-if="showDraftButton" :disabled="saving" @click="onSubmit(0)">存草稿</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmit(1)">
          {{ articleId && loadedStatus === 1 ? '保存修改' : '发布文章' }}
        </el-button>
      </header>

      <el-alert
        v-if="articleId && loadedStatus === 0"
        class="draft-alert"
        type="warning"
        :closable="false"
        show-icon
        title="当前是草稿，仅自己可见；发布后才会对外展示"
      />

      <EditorToolbar @apply="applyMarkdown" />

      <div class="editor-grid" :class="`mobile-panel-${mobilePanel}`">
        <EditorOutline class="desktop-outline" :items="outline" @navigate="navigateToLine" />

        <section class="document-panel">
          <div class="metadata-fields">
            <el-input v-model="form.title" class="title-input" placeholder="文章标题" />
            <el-input v-model="form.category" placeholder="分类" />
            <el-input v-model="form.tags" placeholder="标签，使用英文逗号分隔" />
          </div>
          <textarea
            ref="contentTextarea"
            v-model="form.content"
            class="markdown-input"
            placeholder="使用 Markdown 开始写作"
            @blur="onContentBlur"
            @select="onContentSelect"
            @click="onCursorUpdate"
            @keyup="onCursorUpdate"
          />
        </section>

        <section class="preview-panel">
          <div class="panel-label">PREVIEW</div>
          <div class="editor-preview markdown-body" v-html="previewHtml" />
        </section>

        <section class="mobile-outline-panel">
          <EditorOutline :items="outline" @navigate="navigateToLine" />
        </section>

        <section class="mobile-info-panel">
          <div class="panel-label">DOCUMENT</div>
          <p>分类：{{ form.category || '未分类' }}</p>
          <p>标签：{{ form.tags || '暂无标签' }}</p>
          <p>状态：{{ form.status === 0 ? '草稿' : '已发布' }}</p>
        </section>
      </div>

      <footer class="status-bar">
        <span>{{ editorStats.characters }} 字</span>
        <span>行 {{ editorStats.line }}，列 {{ editorStats.column }}</span>
        <span>UTF-8</span>
        <span>Markdown</span>
      </footer>

      <nav class="mobile-editor-nav" aria-label="编辑器面板">
        <button type="button" :class="{ active: mobilePanel === 'edit' }" @click="mobilePanel = 'edit'">编辑</button>
        <button type="button" :class="{ active: mobilePanel === 'preview' }" @click="mobilePanel = 'preview'">预览</button>
        <button type="button" :class="{ active: mobilePanel === 'outline' }" @click="mobilePanel = 'outline'">大纲</button>
        <button type="button" :class="{ active: mobilePanel === 'info' }" @click="mobilePanel = 'info'">信息</button>
      </nav>
    </div>
  </AppShell>
</template>
```

- [ ] **Step 3: 替换编辑器样式**

将 scoped 样式替换为：

```css
.editor-app {
  border: 1px solid var(--blog-line);
  background: var(--blog-paper);
  box-shadow: var(--blog-shadow-sm);
}

.document-bar {
  min-height: 54px;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 0 16px;
  background: var(--blog-ink);
  color: var(--blog-paper);
}

.document-state,
.document-count,
.panel-label {
  color: #a9becc;
  font-family: var(--blog-font-mono);
  font-size: 9px;
  letter-spacing: 0.1em;
}

.document-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.document-count {
  margin-left: auto;
}

.draft-alert {
  margin: 0;
}

.editor-grid {
  display: grid;
  grid-template-columns: 190px minmax(0, 1fr) minmax(0, 1fr);
  min-height: 620px;
}

.document-panel,
.preview-panel {
  min-width: 0;
  padding: 20px 24px;
}

.document-panel {
  border-right: 1px solid var(--blog-line);
}

.preview-panel {
  background: #f9fbfc;
}

.metadata-fields {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(120px, 0.7fr) minmax(160px, 1fr);
  gap: 10px;
  margin-bottom: 14px;
}

.title-input :deep(input) {
  font-family: var(--blog-font-display);
  font-size: 20px;
  font-weight: 700;
}

.markdown-input {
  width: 100%;
  min-height: 520px;
  padding: 8px 2px;
  border: 0;
  outline: 0;
  font-family: var(--blog-font-mono);
  font-size: 13px;
  line-height: 1.9;
  resize: vertical;
}

.editor-preview {
  min-height: 520px;
}

.status-bar {
  min-height: 30px;
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 0 14px;
  border-top: 1px solid var(--blog-line);
  background: var(--blog-blueprint);
  color: var(--blog-muted);
  font-family: var(--blog-font-mono);
  font-size: 9px;
}

.mobile-outline-panel,
.mobile-info-panel,
.mobile-editor-nav {
  display: none;
}

@media (max-width: 1199px) {
  .editor-grid {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  }

  .desktop-outline {
    display: none;
  }
}

@media (max-width: 767px) {
  .document-bar {
    flex-wrap: wrap;
    padding-block: 8px;
  }

  .document-title {
    flex: 1;
  }

  .document-count {
    display: none;
  }

  .editor-grid {
    display: block;
    min-height: 560px;
    padding-bottom: 56px;
  }

  .document-panel,
  .preview-panel,
  .mobile-outline-panel,
  .mobile-info-panel {
    display: none;
    padding: 16px;
  }

  .metadata-fields {
    grid-template-columns: 1fr;
  }

  .mobile-panel-edit .document-panel,
  .mobile-panel-preview .preview-panel,
  .mobile-panel-outline .mobile-outline-panel,
  .mobile-panel-info .mobile-info-panel {
    display: block;
  }

  .mobile-outline-panel {
    border: 0;
  }

  .mobile-editor-nav {
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 40;
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    min-height: 54px;
    background: var(--blog-ink);
  }

  .mobile-editor-nav button {
    border: 0;
    background: transparent;
    color: #b8c9d7;
  }

  .mobile-editor-nav button.active {
    color: var(--blog-paper);
    box-shadow: inset 0 -2px var(--blog-red);
  }

  .status-bar {
    display: none;
  }
}
```

- [ ] **Step 4: 运行测试和构建**

运行：

```powershell
npm run test
npm run type-check
npm run lint
npm run build
```

预期：所有命令退出码为 `0`，构建无新增大于 `500kB` 的 chunk 警告。

- [ ] **Step 5: 提交**

```powershell
git add frontend/src/views/ArticleEdit.vue
git commit -m "feat(frontend): 重构专业写作编辑器"
```

## Task 11: 统一 Markdown 阅读样式

**Files:**
- Modify: `frontend/src/assets/markdown.css`

- [ ] **Step 1: 替换 Markdown 样式**

将 `frontend/src/assets/markdown.css` 替换为：

```css
.markdown-body {
  color: var(--blog-ink-soft);
  overflow-wrap: anywhere;
}

.markdown-body h1,
.markdown-body h2,
.markdown-body h3,
.markdown-body h4 {
  margin: 34px 0 14px;
  color: var(--blog-ink);
  font-family: var(--blog-font-display);
  line-height: 1.35;
}

.markdown-body h1 {
  padding-bottom: 10px;
  border-bottom: 1px solid var(--blog-line);
  font-size: 2em;
}

.markdown-body h2 {
  padding-bottom: 8px;
  border-bottom: 1px solid var(--blog-line-soft);
  font-size: 1.55em;
}

.markdown-body h3 {
  font-size: 1.25em;
}

.markdown-body p,
.markdown-body ul,
.markdown-body ol {
  margin: 16px 0;
}

.markdown-body ul,
.markdown-body ol {
  padding-left: 28px;
}

.markdown-body li + li {
  margin-top: 7px;
}

.markdown-body blockquote {
  margin: 24px 0;
  padding: 14px 18px;
  border-left: 3px solid var(--blog-red);
  background: var(--blog-blueprint);
  color: var(--blog-ink);
  font-family: var(--blog-font-display);
}

.markdown-body blockquote p {
  margin: 4px 0;
}

.markdown-body table {
  width: 100%;
  margin: 22px 0;
  border-collapse: collapse;
  font-size: 14px;
}

.markdown-body th,
.markdown-body td {
  padding: 10px 12px;
  border: 1px solid var(--blog-line);
  text-align: left;
}

.markdown-body th {
  background: var(--blog-blueprint);
}

.markdown-body tr:nth-child(2n) td {
  background: #f8fafb;
}

.markdown-body hr {
  margin: 30px 0;
  border: 0;
  border-top: 1px solid var(--blog-line);
}

.markdown-body a {
  color: var(--blog-red);
  text-decoration: none;
}

.markdown-body a:hover {
  text-decoration: underline;
}

.markdown-body img {
  max-width: 100%;
  border-radius: var(--blog-radius-sm);
}

.markdown-body pre {
  margin: 22px 0;
  padding: 16px;
  border: 1px solid #263f56;
  background: var(--blog-ink);
  color: #dce7f0;
  overflow-x: auto;
}

.markdown-body code {
  padding: 2px 5px;
  background: var(--blog-blueprint);
  color: var(--blog-red-dark);
  font-family: var(--blog-font-mono);
  font-size: 0.92em;
}

.markdown-body pre code {
  padding: 0;
  background: transparent;
  color: inherit;
}
```

- [ ] **Step 2: 验证并提交**

运行：

```powershell
npm run build
```

预期：退出码为 `0`。

```powershell
git add frontend/src/assets/markdown.css
git commit -m "style(frontend): 统一 Markdown 阅读样式"
```

## Task 12: 响应式、可访问性与最终验证

**Files:**
- Modify: `frontend/src/components/AppHeader.vue`
- Modify: `frontend/src/views/ArticleList.vue`
- Modify: `frontend/src/views/ArticleDetail.vue`
- Modify: `frontend/src/views/ArticleEdit.vue`
- Modify: `frontend/src/views/Login.vue`
- Modify: `frontend/src/views/Register.vue`
- Modify: `frontend/src/views/Profile.vue`

- [ ] **Step 1: 全局占位文本审查**

运行：

```powershell
rg -n "TODO|TBD|暂未实现|敬请期待|AI" frontend/src
```

预期：不需要新增 AI 文案；已有业务错误文本中包含 AI 的情况按实际语境保留。

- [ ] **Step 2: 检查可操作元素语义**

运行：

```powershell
rg -n "<span[^>]*(click|@click)|<div[^>]*@click" frontend/src
```

预期：没有依赖普通 `span` 或 `div` 的页面级点击操作；文章条目内部允许 `article @click`，但必须有明确键盘替代入口或改为按钮。

- [ ] **Step 3: 启动前端开发服务**

运行：

```powershell
npm run dev -- --host 127.0.0.1
```

预期：Vite 输出本地 URL，服务保持运行。

- [ ] **Step 4: 人工检查桌面视口**

在浏览器中将视口设置为 `1440×900`，依次检查：

```text
/articles
/articles/:id
/publish
/profile
/login
/register
```

预期：

1. 顶部导航不换行遮挡内容。
2. 文章列表主栏与上下文轨道不溢出。
3. 详情正文与评论阅读顺序清楚。
4. 编辑器工具栏、大纲、编辑区和预览区同时可见。
5. 登录注册分栏完整。
6. 个人账本不出现默认后台表格的横向滚动。

- [ ] **Step 5: 人工检查平板视口**

在浏览器中将视口设置为 `1024×768`。

预期：

1. 文章轨道移动到主内容之后。
2. 编辑器隐藏左侧大纲，编辑和预览保持可用。
3. 导航搜索与操作按钮不重叠。

- [ ] **Step 6: 人工检查移动视口**

在浏览器中将视口设置为 `390×844`。

预期：

1. 页面无横向滚动。
2. 详情页底部互动条固定且不遮挡最后一段内容。
3. 编辑器底部面板切换可用。
4. 个人中心条目可以完整显示标题、状态和操作。
5. 登录和注册表单可完整滚动。

- [ ] **Step 7: 运行最终自动化验证**

运行：

```powershell
npm run test
npm run type-check
npm run lint
npm run build
```

预期：所有命令退出码为 `0`，构建产物没有新增大于 `500kB` 的 chunk 警告。

- [ ] **Step 8: 提交最终修正**

```powershell
git add frontend/src
git commit -m "fix(frontend): 完成响应式与可访问性验收"
```

## 最终验收清单

- [ ] 所有现有页面完成视觉重设计
- [ ] 桌面、平板、移动视口无重叠与横向滚动
- [ ] 搜索、排序、分页、点赞、收藏、评论、登录、发布和草稿逻辑保持
- [ ] Markdown 工具栏、大纲和状态栏工作正常
- [ ] 未添加 AI 功能、AI 假入口或外部模型调用
- [ ] 未修改后端接口、数据模型和路由
- [ ] `npm run test`、`npm run type-check`、`npm run lint`、`npm run build` 全部通过
