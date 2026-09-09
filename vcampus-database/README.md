# vCampus Access 数据库

服务器首次启动时会通过 UCanAccess 自动创建：

```text
vcampus-database/vCampus.accdb
```

初始数据库包含：

- `tblUser`：登录账号。
- `tblDepartment`：院系。
- `tblSchoolClass`：班级。
- `tblStudent`：学生学籍。
- `tblTeacher`：教师档案。
- `tblCourse`：实际开课与选课容量、教师、时间信息。
- `tblCourseEnrollment`：学生选课记录。
- `tblBookWish`：好书推荐。
- `tblReservation`：预约委托申请。
- `tblReservationPatron`：预约违约次数与停权截止。
- `tblOperationLog`：登录与管理员操作审计日志。
- `tblCatalogSource`：院系、专业和课程快照的官方来源。
- `tblMajor`：本科专业目录。
- `tblCatalogCourse`：培养方案课程目录，与实际开课的 `tblCourse` 分离。
- `tblMajorCourse`：专业与课程的建议学年、学期及必修关系。
- `tblProduct`：商品。
- `tblCartItem`：账号购物车，`userId` 引用 `tblUser`。
- `tblOrder`：订单，`userId` 引用 `tblUser`。
- `tblOrderItem`：订单明细。

数据库包含超级管理员、子系统管理员、学生和教师四个演示账号；与学生、教师账号关联的演示学籍，以及 10 种演示图书。演示学生账号有一条在借记录和一条逾期未还记录。密码以 PBKDF2 加盐哈希保存，不保存明文密码。

服务端还会幂等导入仓库内经审核的东南大学公开数据快照。重复启动只更新同一业务主键，
不重复插入；完整来源和重新采集方式见 `docs/东南大学公开数据快照.md`。

`tblBorrowRecord.userId` 以外键引用 `tblUser.userId`。借阅人统一使用登录账号标识，
需要学号或工号时再通过学籍表的 `userId` 关联。
`tblCartItem.userId` 和 `tblOrder.userId` 采用相同的账号关联规则。

## 用户表结构（tblUser）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `userId` | TEXT(32) | 账号，主键 |
| `passwordHash` | TEXT(128) | PBKDF2 哈希值（Base64），不保存明文 |
| `passwordSalt` | TEXT(64) | 随机盐（Base64） |
| `displayName` | TEXT(64) | 显示名 |
| `roleName` | TEXT(16) | 角色：`STUDENT` / `TEACHER` / `SUBSYSADMIN` / `SUPER_ADMIN` |
| `adminScopes` | TEXT(64) | 子系统管理员可管理的子系统 key（逗号分隔，如 `student,course`）；其他角色为空 |
| `active` | YESNO | 是否启用，管理员可禁用账号 |

演示账号：`superadmin / super123`（超级管理员）、`admin / admin123`（子系统管理员）、`student / student123`（学生）、`teacher / teacher123`（教师）。

账号由管理员统一创建，登录界面不提供自助注册：学生可经 CSV 批量导入；教师由管理员手动注册；子系统管理员（`SUBSYSADMIN`）与超级管理员（`SUPER_ADMIN`）仅能由现有超级管理员创建。注册、修改密码与注销均在服务端完成密码哈希处理。

## 使用规则

- 只有服务器端可以访问该数据库文件。
- 客户端不得直接连接 Access。
- 默认使用相对路径，禁止提交个人电脑绝对路径。
- Access 打开数据库时产生的 `.laccdb` 或 `.ldb` 锁文件不得提交。
- 修改表结构前先更新 E-R 图和数据字典。
- 表名使用 `tbl` 前缀，如 `tblUser`、`tblStudent`、`tblCourse`。
- 字段名使用英文小驼峰命名。
- 所有 SQL 使用 `PreparedStatement`，不拼接用户输入。

## 已实现业务表

| 模块 | 建议表 |
| --- | --- |
| 用户 | `tblUser`、`tblOperationLog` |
| 学籍 | `tblStudent`、`tblTeacher`、`tblDepartment`、`tblSchoolClass` |
| 选课 | `tblCourse`、`tblCourseEnrollment` |
| 图书馆 | `tblBook`、`tblBookCopy`、`tblBorrowRecord`、`tblBookWish`、`tblReservation`、`tblReservationPatron`（已自动创建） |
| 商店 | `tblProduct`、`tblCartItem`、`tblOrder`、`tblOrderItem` |

Access 不适合大量并发写入。库存扣减、选课、借书等关键业务应由服务器端使用短事务完成。
