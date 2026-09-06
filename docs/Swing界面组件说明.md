# Swing 公共界面组件说明

负责人：赵言（成员 D）。视觉参考东南大学信息服务门户（绿色顶栏、金黄强调、白底卡片、绿色主按钮）。

包路径：`edu.seu.vcampus.client.ui.components`

## 1. 何时使用

各业务模块（学籍 / 选课 / 图书馆 / 商店）新建或改版页面时，优先使用本包工厂方法，不要再硬编码 `new Color(...)` 与随意字号。现有页面可逐步替换，不要求一次改完。

## 2. 启动

`ClientApplication` 已在创建窗口前调用：

```java
SeuTheme.install();
```

其他入口（测试、临时 main）若要弹出窗口，也应先调用一次。重复调用是安全的。

## 3. 类一览

| 类 | 用途 |
| --- | --- |
| `SeuTheme` | 配色、字号、间距、边框、FlatLaf 默认值 |
| `SeuButtons` | `primary` / `secondary` / `accent` / `danger` / `link` |
| `SeuFields` | 文本框、密码框、下拉框、占位提示 |
| `SeuTables` | 只读模型、斑马纹表格、滚动包装 |
| `SeuLabels` | 标题、字段、状态、辅助文字 |
| `SeuPanels` | 页面根、卡片、工具条、标题行、品牌条 |
| `SeuMessages` | 统一「提示 / 操作失败 / 确认」对话框 |
| `SeuNavBar` | 门户二级导航（深绿底 + 金黄激活项） |
| `SeuAppTile` | 应用中心入口卡片 |
| `SeuButtons.headerLink` | 顶栏白字链接按钮 |
| `SeuButtons.pillPrimary` | 身份认证页大号登录按钮 |
| `SeuFields.pillText` / `pillPasswordWithToggle` | 认证页大号圆角输入框 |
| `SeuBrandHeader` | 校徽示意 + 校名品牌区 |

门户壳：`MainFrame`（深绿顶栏 + `SeuNavBar` + 内容区），首页为 `HomePanel` 应用中心卡片。  
登录页：`LoginFrame` 已按身份认证中心版式接入公共组件。

## 4. 推荐页面骨架

```java
JPanel page = SeuPanels.page();
JLabel status = SeuLabels.status("准备就绪");
JPanel north = new JPanel(new BorderLayout(0, SeuTheme.SPACE_MD));
north.setOpaque(false);
north.add(SeuPanels.heading("图书馆", status), BorderLayout.NORTH);

JPanel filters = SeuPanels.toolbar();
JTextField keyword = SeuFields.text(18);
SeuFields.setPlaceholder(keyword, "请输入书名 / ISBN / 作者");
filters.add(SeuLabels.field("关键字"));
filters.add(keyword);
filters.add(SeuButtons.primary("查询"));
filters.add(SeuButtons.secondary("编辑"));
filters.add(SeuButtons.danger("删除"));
north.add(filters, BorderLayout.SOUTH);

DefaultTableModel model = SeuTables.readOnlyModel(
        new String[]{"ISBN", "书名", "作者", "可借", "馆藏"});
JTable table = SeuTables.create(model);

page.add(north, BorderLayout.NORTH);
page.add(SeuTables.scroll(table), BorderLayout.CENTER);
```

## 5. 按钮选用约定

| 操作 | 按钮 |
| --- | --- |
| 查询、保存、登录、提交 | `SeuButtons.primary` |
| 编辑、取消、次要操作 | `SeuButtons.secondary` |
| 首页入口、强调导航 | `SeuButtons.accent` |
| 删除、不可恢复操作 | `SeuButtons.danger` + `SeuMessages.confirm` |
| 「更多」类文字链 | `SeuButtons.link` |

## 6. 颜色常量（摘录）

| 常量 | 含义 |
| --- | --- |
| `SeuTheme.PRIMARY` | 顶栏 / 主品牌绿 `#3F6B3A` |
| `SeuTheme.ACCENT` | 导航金黄 `#E8C84A` |
| `SeuTheme.PAGE_BG` | 页面浅灰底 |
| `SeuTheme.SURFACE` | 卡片白底 |
| `SeuTheme.BORDER` | 分割线 / 表格边框 |
| `SeuTheme.TEXT` / `TEXT_MUTED` | 主文字 / 次要文字 |

完整列表见 `SeuTheme` 源码。

## 7. 协作说明（全组）

### 谁做什么

| 角色 | 职责 |
| --- | --- |
| 成员 D（赵言） | 维护公共组件与本说明；提供示范页；答疑；集成前做界面抽查 |
| 各模块负责人 | **自己改本模块页面**，接入公共组件；不私自改门户壳与 `ui.components` |
| 成员 A | 登录 / 账号相关页 |
| 成员 B | 学籍页 |
| 成员 C | 选课页 |
| 成员 E | 商店页 |
| 成员 D | 图书馆页（示范）+ 公共组件 |

### 各模块怎么改（最少做到）

1. 读本说明，对照 `LibraryPanel`。
2. 页面根布局用 `SeuPanels.page()` / `heading()` / `toolbar()` / `card()`。
3. 查询/保存 → `SeuButtons.primary`；编辑 → `secondary`；删除 → `danger` + `SeuMessages.confirm`。
4. 表格用 `SeuTables`；输入框用 `SeuFields`；提示用 `SeuMessages`。
5. **不要**再写 `new Color(...)` 或随意字号；缺控件先找 D 加进公共包。

### 不要动

- `MainFrame` / `HomePanel` / `SeuNavBar`（门户壳由 D 维护）
- `ui.components` 包内类（需要扩展提需求给 D）

### 技术底线（与样式无关也要遵守）

- 网络请求放在 `SwingWorker` 中。
- 业务页只使用 `SubSystemRole`，不要直接判断全局 `Role` / `adminScopes`。
- 窗口按现有 `MainFrame` 尺寸约定自测，避免机房分辨率下遮挡。

## 8. 后续计划

- [x] 主题与基础控件。
- [x] 门户壳（顶栏 / 模块导航 / 应用中心首页）；`LibraryPanel` 已示范接入。
- [ ] 其余业务页由各负责人接入公共组件。
- [ ] 易用性检查与系统使用说明截图。
