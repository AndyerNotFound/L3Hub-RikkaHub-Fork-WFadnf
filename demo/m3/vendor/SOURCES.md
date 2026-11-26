# vendor 来源

这份目录是浏览器直接加载的运行时依赖，**没有构建步骤**。生成方式：

1. 从 npm 取下面这些包的发布包（tarball），时间 2026-11-27；
2. 从 demo 实际 import 的组件入口出发，沿着 `import` 关系只搬运会被加载的 `.js`；
   `.js.map`、`.d.ts`、`.scss` 与未引用的组件都没有进仓库；
3. `@material/material-color-utilities` 的发布包保留了 TypeScript 的**无扩展名**
   相对引用（`from '../hct/hct'`）。浏览器不会自动补 `.js`，所以对这些文件做了一次
   机械替换：只给能落到真实文件的相对 specifier 补上 `.js`。没有改写任何逻辑；
4. 许可证放在 `licenses/`，与包一一对应。

| 包 | 版本 | 许可证 | 用途 |
| --- | --- | --- | --- |
| `@material/web` | 2.5.0 | Apache-2.0 | M3 Web Components（按钮、对话框、菜单、Tabs、开关、滑块、Chip、文本框、分段按钮） |
| `@material/material-color-utilities` | 0.4.0 | Apache-2.0 | 由种子色生成 48 个 color roles（HCT / DynamicScheme） |
| `lit` | 3.3.3 | BSD-3-Clause | `@material/web` 的运行时 |
| `lit-html` | 3.3.3 | BSD-3-Clause | 同上 |
| `lit-element` | 4.2.2 | BSD-3-Clause | 同上 |
| `@lit/reactive-element` | 2.1.2 | BSD-3-Clause | 同上 |
| `tslib` | 2.8.1 | 0BSD | TypeScript 装饰器 helper |

`@lit/context` 只在部分组件路径里出现；如果最终没有被 import，目录里就不会有它。
入口与映射写在 `../index.html` 的 import map 里。
