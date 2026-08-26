# 遗留可选优化清零实施计划（M1 + B7 根治 + AppHeader 三小修）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 清零上轮审查记录的全部 defer 项——参数校验异常返回 400、Element Plus 按需导入消除 chunk 告警、通知入口容错与登录态联动。

**Architecture:** 后端一个新 exception handler；前端为结构性改造（main.ts 拆全量注册、App.vue 包 el-config-provider、图标改局部导入）加三处小修。任务彼此独立可并行审。

**Tech Stack:** Spring Boot 3.5 / Spring 6.2 方法级校验；Vue 3 + Vite 8.2.2(rolldown) + element-plus 2.14.5 + unplugin-auto-import 21.x + unplugin-vue-components 32.x。

**设计文档:** `docs/superpowers/specs/2026-08-27-cleanup-final-design.md`

## Global Constraints

- 验证门槛：后端 `./mvnw.cmd -q compile` 退出码 0；前端 `npm run build && npm run lint` 退出码 0。
- **B7 通过硬指标：构建输出无任何 >500kB chunk 告警。**
- npm 安装若 ERESOLVE/超时失败，用 `--registry=https://registry.npmmirror.com` 重试（两 unplugin 包的 @nuxt/kit、@vueuse/core 为 optional peers，正常不会 ERESOLVE）。
- 图标清单（grep 已核实全项目就这 7 种、8 处）：AppHeader 的 search/bell/arrow-down；ArticleList 的 histogram/view/star；ArticleDetail 的 star/collection-tag。改为局部导入后模板必须用 PascalCase（如 `<Search />`），kebab-case 全局注册已不复存在。
- ElMessage/ElMessageBox/FormInstance/FormRules 等 TS 值与类型的显式 import 全部保留（resolver 会补样式注入；auto-import 不强制接管它们）。
- auto-imports.d.ts 与 components.d.ts 提交入库（vue-tsc 需要）；若 eslint 报两者内符号未使用之类误报，将生成文件加入 .oxlintrc.json/eslint 忽略或按实际情况最小处置，并在报告说明。
- vite.config.ts 既有 codeSplitting.groups 四组保留不动。
- 中文注释解释「为什么」；提交信息 `类型(范围): 中文描述`。Windows git-bash 执行。

---

### Task 1: 后端 M1 — HandlerMethodValidationException 返回 400

**Files:**
- Modify: `blog/src/main/java/com/example/blog/common/GlobalExceptionHandler.java`

**Interfaces:**
- Produces: 参数约束校验失败（如 GET /api/articles/hot?topN=0）返回 `{code:400, message:<第一条校验消息, 如 "topN 最小为 1">}`；其余行为不变。

- [ ] **Step 1: 添加 handler**

import 区新增：

```java
import org.springframework.context.MessageSourceResolvable;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import java.util.Objects;
```

在现有「参数绑定异常」handler 之后插入：

```java
    /**
     * 方法级参数约束校验失败（Spring 6.2 起对 @RequestParam/@PathVariable 直接校验，
     * 无需类上 @Validated）。不补这条会把边界错误落进兜底 handler 返回 500「系统繁忙」，
     * 校验注解里的自定义提示词（如「topN 最小为 1」）永远不可见
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public Result<Void> handleHandlerMethodValidationException(HandlerMethodValidationException e) {
        String message = e.getAllValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream())
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("参数校验失败");
        return Result.error(400, message);
    }
```

- [ ] **Step 2: 编译验证**

Run: `cd c:/Users/vannnn/Desktop/blog-platform/blog && ./mvnw.cmd -q compile`
Expected: 退出码 0

- [ ] **Step 3: Commit**

```bash
cd c:/Users/vannnn/Desktop/blog-platform
git add blog/src/main/java/com/example/blog/common/GlobalExceptionHandler.java
git commit -m "fix(blog): 补充方法级参数校验异常处理器"
```

---

### Task 2: 前端 B7 根治 — Element Plus 按需导入改造

**Files:**
- Modify: `frontend/package.json`（npm install 写入）
- Modify: `frontend/vite.config.ts`
- Modify: `frontend/src/main.ts`
- Modify: `frontend/src/App.vue`
- Modify: `frontend/src/components/AppHeader.vue`
- Modify: `frontend/src/views/ArticleList.vue`
- Modify: `frontend/src/views/ArticleDetail.vue`
- Create: `frontend/auto-imports.d.ts`、`frontend/components.d.ts`（构建生成后随代码提交）

**Interfaces:**
- Consumes: 现有组件结构与模板。
- Produces: 所有 el-* 组件经 resolver 按需注入；7 种图标局部 PascalCase 导入；locale 经 App.vue 的 `<el-config-provider>` 提供；构建无 >500kB 告警。

- [ ] **Step 1: 安装依赖**

Run: `cd c:/Users/vannnn/Desktop/blog-platform/frontend && npm install -D unplugin-auto-import unplugin-vue-components`
Expected: 退出码 0（网络问题加 npmmirror registry 重试）

- [ ] **Step 2: vite.config.ts 注册插件**

import 区新增：

```ts
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
```

plugins 数组改为：

```ts
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
```

build.rolldownOptions 配置保留不动。

- [ ] **Step 3: main.ts 拆除全量注册**

整体替换为：

```ts
import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'

import './assets/markdown.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
```

- [ ] **Step 4: App.vue 包裹 locale provider**

整体替换为：

```vue
<script setup lang="ts">
import AppHeader from '@/components/AppHeader.vue'
// 按需导入改造后不再有 app.use(ElementPlus)，中文 locale 改由 config-provider 注入
import zhCn from 'element-plus/es/locale/lang/zh-cn'
</script>

<template>
  <el-config-provider :locale="zhCn">
    <el-container class="layout">
      <el-header class="layout-header">
        <AppHeader />
      </el-header>
      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-config-provider>
</template>

<style scoped>
.layout-header {
  padding: 0;
  border-bottom: 1px solid var(--el-border-color-light);
}
.layout-main {
  max-width: 960px;
  margin: 0 auto;
  width: 100%;
}
</style>
```

- [ ] **Step 5: 图标改局部 PascalCase 导入**

**AppHeader.vue** script 头部补：

```ts
import { Bell, ArrowDown, Search } from '@element-plus/icons-vue'
```

模板替换三处：`<search />` → `<Search />`（第 61 行）、`<bell />` → `<Bell />`（第 70 行）、`<arrow-down />` → `<ArrowDown />`（第 99 行）。

**ArticleList.vue** script 头部补：

```ts
import { View, Star, Histogram } from '@element-plus/icons-vue'
```

模板替换：`<histogram />` → `<Histogram />`、`<view />` → `<View />`、`<star />` → `<Star />`。

**ArticleDetail.vue** script 头部补：

```ts
import { Star, CollectionTag } from '@element-plus/icons-vue'
```

模板替换：`<star />` → `<Star />`、`<collection-tag />` → `<CollectionTag />`。

注意：全局注册删除后 kebab-case 标签不再解析，三文件遗漏任何一处都会在 vue-tsc/build 报未知组件——以此作为遗漏检查网。

- [ ] **Step 6: 构建 + lint + 告警核对**

Run: `cd c:/Users/vannnn/Desktop/blog-platform/frontend && npm run build && npm run lint`
Expected: 两步退出码 0；**构建输出不得出现 >500kB chunk 告警**。element-plus chunk 应从 ~1130kB 显著缩小；auto-imports.d.ts 与 components.d.ts 生成于 frontend/ 根目录，确认存在并纳入 git。
若有 lint 对生成文件的误报，做最小忽略处理并在报告注明。

- [ ] **Step 7: Commit**

```bash
cd c:/Users/vannnn/Desktop/blog-platform
git add frontend/package.json frontend/package-lock.json frontend/vite.config.ts frontend/src/main.ts frontend/src/App.vue frontend/src/components/AppHeader.vue frontend/src/views/ArticleList.vue frontend/src/views/ArticleDetail.vue frontend/auto-imports.d.ts frontend/components.d.ts
git commit -m "refactor(frontend): Element Plus 按需导入改造"
```

---

### Task 3: AppHeader 三小修（容错 / 登录态联动 / dropdown 宽度）

**Files:**
- Modify: `frontend/src/components/AppHeader.vue`

**Interfaces:**
- Consumes: Task 2 完成后的 AppHeader 结构（Bell 局部导入、图标 PascalCase）。
- Produces: 通知加载失败静默；登录/登出即时刷新徽标；通知面板固定 320px 宽。

- [ ] **Step 1: loadNotifications/onMarkAllRead 容错**

两个函数体包 try/catch：

```ts
async function loadNotifications() {
  try {
    const page = await getNotifications({ pageNum: 1, pageSize: 5 })
    notifyList.value = page.list
    unreadCount.value = Number(page.unreadCount)
  } catch {
    // 错误已由拦截器 toast；保持原列表与徽标不清空
  }
}

async function onMarkAllRead() {
  try {
    await markAllNotifications()
    await loadNotifications()
  } catch {
    // 同上，静默即可
  }
}
```

- [ ] **Step 2: 登录态 watch 联动**

onMounted 条件加载整块替换为：

```ts
// 登录状态变化时拉取/清空：覆盖 SPA 内登录成功（onMounted 早已跑过）、退出、401 过期三种转换
watch(
  () => userStore.isLoggedIn,
  (loggedIn) => {
    if (loggedIn) loadNotifications()
    else {
      notifyList.value = []
      unreadCount.value = 0
    }
  },
  { immediate: true },
)
```

import 行把 `onMounted` 从 vue 导入中移除（若无其他使用）、补入 `watch`。

- [ ] **Step 3: dropdown 合法宽度**

`<el-dropdown placement="bottom-end" width="320" ...>` 删除 `width="320"`，加 `popper-class="notify-popper"`。

style 尾部追加非 scoped 块（popper 渲染挂在 body 下，scoped 选择器够不到）：

```vue
<style>
/* 通知 popper 挂在 body 下，需非 scoped 样式控制宽度 */
.notify-popper {
  width: 320px;
}
</style>
```

- [ ] **Step 4: 构建 + lint**

Run: `cd c:/Users/vannnn/Desktop/blog-platform/frontend && npm run build && npm run lint`
Expected: 两步退出码 0。

- [ ] **Step 5: Commit**

```bash
cd c:/Users/vannnn/Desktop/blog-platform
git add frontend/src/components/AppHeader.vue
git commit -m "fix(frontend): 通知入口容错与登录态联动"
```

## Self-Review 记录

- 规格覆盖：M1=Task 1；B7 根治=Task 2（依赖安装/双插件/拆除全量注册/config-provider locale/图标局部化/生成文件入库/告警硬指标）；Task 5 minors 三件套=Task 3。设计文档四节全覆盖，无遗漏。
- 类型一致性：图标清单 Task 2 Step 5 与 grep 结果逐一对应（AppHeader 3、ArticleList 3、ArticleDetail 2）；watch 替代 onMounted 后 import 变更路径已写明。
- 占位符扫描：所有代码块完整可直接落地，无 TBD/作废块。
