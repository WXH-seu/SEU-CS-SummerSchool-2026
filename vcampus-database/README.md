# vCampus Access 数据库

服务器首次启动时会通过 UCanAccess 自动创建：

```text
vcampus-database/vCampus.accdb
```

初始数据库包含 `tblUser`，以及超级管理员、管理员、学生和教师四个演示账号。密码以 PBKDF2 加盐哈希保存，不保存明文密码。

## 用户表结构（tblUser）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `userId` | TEXT(32) | 账号，主键 |
| `passwordHash` | TEXT(128) | PBKDF2 哈希值（Base64），不保存明文 |
| `passwordSalt` | TEXT(64) | 随机盐（Base64） |
| `displayName` | TEXT(64) | 显示名 |
| `roleName` | TEXT(16) | 角色：`STUDENT` / `TEACHER` / `ADMIN` / `SUPER_ADMIN` |
| `active` | YESNO | 是否启用，管理员可禁用账号 |

演示账号：`superadmin / super123`（超级管理员）、`admin / admin123`（管理员）、`student / student123`（学生）、`teacher / teacher123`（教师）。

账号由管理员统一创建，登录界面不提供自助注册：学生可经 CSV 批量导入；教师由管理员手动注册；管理员（`ADMIN`）与超级管理员（`SUPER_ADMIN`）仅能由现有超级管理员创建。注册、修改密码与注销均在服务端完成密码哈希处理。

## 使用规则

- 只有服务器端可以访问该数据库文件。
- 客户端不得直接连接 Access。
- 默认使用相对路径，禁止提交个人电脑绝对路径。
- Access 打开数据库时产生的 `.laccdb` 或 `.ldb` 锁文件不得提交。
- 修改表结构前先更新 E-R 图和数据字典。
- 表名使用 `tbl` 前缀，如 `tblUser`、`tblStudent`、`tblCourse`。
- 字段名使用英文小驼峰命名。
- 所有 SQL 使用 `PreparedStatement`，不拼接用户输入。

## 建议业务表

| 模块 | 建议表 |
| --- | --- |
| 用户 | `tblUser`、`tblRole` |
| 学籍 | `tblStudent`、`tblTeacher`、`tblDepartment`、`tblClass` |
| 选课 | `tblCourse`、`tblTeachingClass`、`tblEnrollment` |
| 图书馆 | `tblBook`、`tblBookCopy`、`tblBorrowRecord` |
| 商店 | `tblProduct`、`tblCartItem`、`tblOrder`、`tblOrderItem` |

Access 不适合大量并发写入。库存扣减、选课、借书等关键业务应由服务器端使用短事务完成。
