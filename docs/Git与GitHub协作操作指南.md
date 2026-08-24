# Git 与 GitHub 协作操作指南

## 1. 文档说明

本文档用于虚拟校园课程项目的五人 Git 协作，适用于 Windows Git Bash。

- 远程仓库：<https://github.com/WXH-seu/SEU-CS-SummerSchool-2026>
- 远程名称：`origin`
- 稳定分支：`main`
- 集成分支：`develop`

> 所有 Git 命令都应在仓库目录中执行。若终端位于 `/F/SummerSchool`，直接运行 `git add` 会提示 `fatal: not a git repository`。

## 2. 进入仓库

打开 Git Bash 后执行：

```bash
cd ./SummerSchool/SEU-CS-SummerSchool-2026
```

确认当前位置和仓库状态：

```bash
pwd
git status
```

进入成功后，终端提示通常带有当前分支名：

```text
Lenovo@Laptop-Xuanhao MINGW64 /F/SummerSchool/SEU-CS-SummerSchool-2026 (main)
```

## 3. 首次配置 Git 身份

每台电脑只需配置一次：

```bash
git config --global user.name "你的GitHub用户名"
git config --global user.email "你的邮箱"
```

检查配置：

```bash
git config --global user.name
git config --global user.email
```

建议使用与 GitHub 账号关联的邮箱。

## 4. 组长首次上传仓库

### 4.1 检查文件

```bash
git status
```

不要提交名称以 `~$` 开头的 Word 临时文件，例如：

```text
~$实践安排(202509).docx
```

### 4.2 添加初始文件

优先明确添加需要提交的文件，不要在未检查时直接使用 `git add .`：

```bash
git add docs
git add "软件实践安排(202509).docx"
git add "软件设计说明书DEMO(20250825).docx"
```

检查暂存内容：

```bash
git status
git diff --staged
```

### 4.3 创建首次提交并推送

```bash
git commit -m "docs: initialize project requirements and plans"
git push -u origin main
```

### 4.4 创建共享开发分支

```bash
git switch -c develop
git push -u origin develop
```

此后：

- `main` 只保存经过测试、可以演示的版本。
- `develop` 用于汇总各成员已经完成的功能。
- 日常开发在 `feature/*` 分支进行。

## 5. 其他成员首次获取项目

先进入准备用来存放项目的目录：

```bash
cd /F/SummerSchool
```

克隆仓库：

```bash
git clone https://github.com/WXH-seu/SEU-CS-SummerSchool-2026.git
cd SEU-CS-SummerSchool-2026
```

切换到共享开发分支：

```bash
git switch develop
git pull origin develop
```

检查远程仓库和所有分支：

```bash
git remote -v
git branch -a
```

## 6. 五人功能分支

| 成员 | 负责内容 | 建议分支 |
| --- | --- | --- |
| 成员 A | 用户与权限、系统集成 | `feature/user-auth` |
| 成员 B | 学籍管理、数据库设计 | `feature/student-management` |
| 成员 C | 选课系统、通信框架 | `feature/course-selection` |
| 成员 D | 图书馆、Swing 界面规范 | `feature/library` |
| 成员 E | 校园商店、测试与部署 | `feature/store` |

创建个人分支前，必须先更新 `develop`：

```bash
git switch develop
git pull origin develop
git switch -c feature/user-auth
```

将最后一行替换为本人对应的分支名称。

首次推送个人分支：

```bash
git push -u origin feature/user-auth
```

## 7. 日常开发流程

### 7.1 开始工作

确认自己位于正确分支：

```bash
git branch --show-current
git status
```

从远程获取最新信息：

```bash
git fetch origin
```

把最新 `develop` 合并到个人分支：

```bash
git switch develop
git pull origin develop
git switch feature/user-auth
git merge develop
```

### 7.2 查看修改

```bash
git status
git diff
```

- `git status`：查看新增、修改、删除和暂存的文件。
- `git diff`：查看尚未暂存的具体修改。
- `git diff --staged`：查看即将提交的修改。

### 7.3 添加并提交修改

添加具体文件或目录：

```bash
git add 文件路径
```

例如：

```bash
git add vcampus-client
git add vcampus-server
git add vcampus-common
git add docs
```

检查后提交：

```bash
git status
git diff --staged
git commit -m "feat: implement user login"
```

### 7.4 推送个人分支

个人分支首次推送：

```bash
git push -u origin feature/user-auth
```

已经设置上游分支后：

```bash
git push
```

## 8. 提交信息规范

格式：

```text
类型: 简短说明
```

| 类型 | 用途 | 示例 |
| --- | --- | --- |
| `feat` | 新功能 | `feat: add course selection` |
| `fix` | 修复问题 | `fix: prevent duplicate enrollment` |
| `docs` | 文档更新 | `docs: update database design` |
| `test` | 测试 | `test: add login validation tests` |
| `refactor` | 不改变功能的代码调整 | `refactor: simplify message dispatcher` |
| `build` | 构建配置 | `build: configure Java 8 compiler` |
| `chore` | 一般维护 | `chore: update gitignore` |

建议一次提交只完成一个明确任务，不要把多个无关模块混在同一次提交中。

## 9. 通过 Pull Request 合并功能

个人功能开发完成后：

1. 确认代码已经提交并推送。
2. 打开 GitHub 仓库。
3. 创建 Pull Request。
4. Base 分支选择 `develop`。
5. Compare 分支选择个人 `feature/*` 分支。
6. 在说明中填写完成内容、测试方法和已知问题。
7. 指定另一名成员审核。
8. 审核和测试通过后再合并。

分支方向示例：

```text
feature/user-auth -> develop
```

不要把日常功能分支直接合并到 `main`。

## 10. 本地合并功能分支

如果需要在本地将功能分支合并到 `develop`：

```bash
git switch develop
git pull origin develop
git merge --no-ff feature/user-auth
git push origin develop
```

`--no-ff` 会保留功能分支的合并记录，便于追踪成员贡献。

## 11. 同步其他成员的最新代码

只下载远程信息，不修改当前文件：

```bash
git fetch origin
```

更新当前 `develop`：

```bash
git switch develop
git pull origin develop
```

更新个人分支：

```bash
git switch feature/user-auth
git merge develop
```

区别：

- `git fetch`：下载远程提交和分支信息，不自动修改当前分支。
- `git pull`：下载并合并到当前分支，相当于 `fetch` 后再 `merge`。
- `git merge`：把指定分支的修改合入当前分支。

## 12. 处理合并冲突

发生冲突时先查看状态：

```bash
git status
```

冲突文件中通常出现：

```text
<<<<<<< HEAD
当前分支内容
=======
需要合并的内容
>>>>>>> develop
```

处理步骤：

1. 与相关成员确认应该保留的内容。
2. 编辑文件，保留正确结果。
3. 删除 `<<<<<<<`、`=======` 和 `>>>>>>>` 标记。
4. 添加已解决的文件并提交。

```bash
git add 冲突文件
git commit -m "merge: resolve develop conflicts"
git push
```

如果尚未决定如何处理，希望取消本次合并：

```bash
git merge --abort
```

## 13. 将稳定版本发布到 main

只有 `develop` 已通过集成测试，才执行：

```bash
git switch main
git pull origin main
git merge --no-ff develop
git push origin main
```

建议通过 GitHub Pull Request 完成：

```text
develop -> main
```

阶段版本可以添加标签：

```bash
git tag -a v0.1.0 -m "First integrated version"
git push origin v0.1.0
```

最终课程版本：

```bash
git tag -a v1.0.0 -m "Final course project release"
git push origin v1.0.0
```

## 14. 删除已合并分支

确认功能已进入 `develop` 后，删除本地分支：

```bash
git branch -d feature/user-auth
```

删除远程分支：

```bash
git push origin --delete feature/user-auth
```

如果 Git 提示分支尚未合并，应先检查，不要立即强制删除：

```bash
git log feature/user-auth --oneline
```

## 15. 常见撤销操作

### 15.1 取消暂存，保留文件修改

```bash
git restore --staged 文件路径
```

### 15.2 放弃尚未提交的文件修改

```bash
git restore 文件路径
```

该操作会丢弃文件修改，执行前必须确认。

### 15.3 修改最近一次提交说明

```bash
git commit --amend -m "新的提交信息"
```

### 15.4 安全撤销已经共享的提交

对于已经推送或被其他成员获取的提交，使用：

```bash
git revert 提交编号
git push
```

不要随意对共享分支执行 `git reset --hard` 或强制推送。

## 16. 临时保存未完成修改

需要临时切换分支，但当前修改尚不适合提交时：

```bash
git stash push -m "temporary work"
```

查看临时记录：

```bash
git stash list
```

恢复最近一次临时修改：

```bash
git stash pop
```

恢复后应检查是否产生冲突。

## 17. 推荐忽略的文件

项目根目录的 `.gitignore` 建议包含：

```gitignore
# Word 临时文件
~$*.docx

# Eclipse
.metadata/
.settings/
.classpath
.project

# Java/Maven 构建结果
target/
bin/
*.class

# 日志和临时文件
*.log
*.tmp

# 本地配置和敏感信息
credentials.txt
*.local.properties
```

如果课程要求提交 Eclipse 的 `.project` 或 `.classpath`，应从忽略列表中删除对应规则，并由全组统一决定。

## 18. 常见错误

### 18.1 `fatal: not a git repository`

原因：当前目录不是项目仓库。

解决：

```bash
cd /F/SummerSchool/SEU-CS-SummerSchool-2026
git status
```

### 18.2 `pathspec ... did not match`

原因：分支名或文件路径不存在，或者没有先下载远程分支。

解决：

```bash
git fetch origin
git branch -a
```

### 18.3 `non-fast-forward`

原因：远程分支存在本地尚未获取的提交。

解决：

```bash
git pull
git push
```

如果出现冲突，按照“处理合并冲突”一节操作。不要直接强制推送。

### 18.4 推送时要求登录

HTTPS 推送需要 GitHub 身份认证。GitHub 不接受账号密码作为 Git 密码，应使用浏览器授权、Git Credential Manager 或 Personal Access Token。

## 19. 每日操作速查

### 开始开发

```bash
cd /F/SummerSchool/SEU-CS-SummerSchool-2026
git switch develop
git pull origin develop
git switch feature/自己的分支
git merge develop
```

### 完成一项任务

```bash
git status
git diff
git add 具体文件
git diff --staged
git commit -m "feat: describe completed work"
git push
```

### 功能完成

1. 推送个人分支。
2. 在 GitHub 创建 `feature/* -> develop` Pull Request。
3. 指定另一名成员审核。
4. 通过测试后合并。
5. 确认合并成功后删除功能分支。

## 20. 团队安全规则

- 不在 `main` 上直接开发。
- 不向共享分支强制推送。
- 不提交密码、账号、令牌和个人数据库连接信息。
- 不提交 Word 临时文件、编译输出和个人绝对路径配置。
- Pull Request 合并前至少由一名其他成员审核。
- 修改公共接口、Message 或数据库结构前先通知全组。
- 每次拉取、合并和提交前后都运行 `git status`。
