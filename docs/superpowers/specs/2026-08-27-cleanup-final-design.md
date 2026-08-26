# 遗留可选优化清零设计文档（M1 + B7 根治 + Task 5 minors）

日期：2026-08-27
状态：已确认
前置：2026-08-26 两轮审查记录的 defer 项

## 已确认决策

| 项 | 决策 |
|----|------|
| B7 根治 | unplugin 按需导入全套（组件 + ElMessage 显式保留 + 图标手动按需导入） |
| M1 / Task 5 minors | 按原审查建议修复 |

## 一、后端：M1 参数校验异常返回 400

`GlobalExceptionHandler` 新增专属 handler：

```java
/**
 * 方法级参数约束校验失败（Spring 6.2 起对 @RequestParam 等直接校验，
 * 如 GET /api/articles/hot?topN=0）。不补这条会把边界错误落进兜底
 * handler 返回 500「系统繁忙」，自定义提示词永远不可见
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

补充 import：`org.springframework.web.method.annotation.HandlerMethodValidationException`、
`org.springframework.context.MessageSourceResolvable`、`java.util.Objects`。

## 二、前端：B7 根治（unplugin 按需导入全套）

### 依赖（devDependencies）

```
unplugin-auto-import
unplugin-vue-components
```
（两者自带 ElementPlusResolver，无需单独安装包）

### vite.config.ts

```ts
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

plugins: [
  vue(),
  vueDevTools(),
  // 组件与常用 API 按需自动导入，替代 main.ts 全量注册
  AutoImport({ imports: ['vue', 'vue-router'], resolvers: [ElementPlusResolver()] }),
  Components({ resolvers: [ElementPlusResolver()] }),
],
```

build.rolldownOptions.output.codeSplitting.groups 保留现状（element-plus/element-icons/markdown-it/vendor 四组——按需后 element-plus chunk 大幅缩小，分组仍利于缓存）。

### main.ts 拆除

删除：
- `import ElementPlus from 'element-plus'` 与 `app.use(ElementPlus, ...)`
- 图标 `for (const [...] of Object.entries(ElementPlusIconsVue))` 全量注册循环
- 'element-plus/dist/index.css' 全量样式引入

**保留**：Pinia、router、`import './assets/markdown.css'`。

**locale 中文支持改由 el-config-provider 提供**：App.vue 外层包裹

```vue
<el-config-provider :locale="zhCn">
  <el-container class="layout"> ... </el-container>
</el-config-provider>
```

App.vue script 中 `import zhCn from 'element-plus/es/locale/lang/zh-cn'`。

注意：全量 CSS 引入移除后，组件样式由 resolver 的 `importStyle` 机制按需注入（默认行为），无需额外配置。

### 图标手动按需导入

项目实际使用的图标（grep 全部 .vue 复核为准）：search、bell、arrow-down、histogram、view、star、collection-tag。

各使用组件内显式导入并以 PascalCase 使用模板标签（同步把模板里的 kebab-case 改为 PascalCase 或保持 kebab-case——Vue 对 `<search/>` 解析依赖全局注册；改为局部导入后必须用 **PascalCase 自闭合** 形式如 `<Search />`、`<Bell />`）：
- AppHeader.vue: Search、Bell、ArrowDown
- ArticleList.vue: View、Star、Histogram
- ArticleDetail.vue: Star、CollectionTag

main.ts 中删除图标相关一切代码。

### auto-imports 生成文件

AutoImport 默认生成 `auto-imports.d.ts`（TS 声明）、Components 生成 `components.d.ts`。两文件应提交入库；若 lint 报 no-unused-vars（manual import 与 auto-import 并存处）以 manual import 优先并核对不重复声明。

script setup 内已显式 import 的 API（ref/computed 等）允许保留——与 auto-import 共存不冲突，构建器去重。

### 验证标准

`npm run build && npm run lint` 退出码 0，且 **构建输出无任何 >500kB chunk 告警**（这是本项的通过硬指标）。运行时风险（组件缺失）靠 lint 无未知组件 + 冒烟兜底。

## 三、前端：Task 5 minors 三件套（全部在 AppHeader.vue）

1. **容错**：`loadNotifications` 与 `onMarkAllRead` 包 try/catch，catch 空处理（拦截器已 toast）。
2. **登录后徽标刷新**：替换 onMounted 条件加载为 watch 驱动：

```ts
// 登录状态变化时拉取未读数：覆盖 SPA 内登录成功、退出、401 过期三种转换
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

3. **dropdown 宽度**：删除非法 `width="320"` prop；加 `popper-class="notify-popper"`，非 scoped style（popper 渲染在 body 下）定义 `.notify-popper { width: 320px; }`。

## 四、验证

1. 后端 compile 通过；handler 生效路径无法冒烟（无测试设施），以代码审读为准。
2. 前端 build+lint 通过且无 >500kB 告警。
3. 每项独立提交：`fix(blog): 补充方法级参数校验异常处理器`、`refactor(frontend): Element Plus 按需导入改造`、`fix(frontend): 通知入口容错与登录态联动`。
