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
- `tblBook`：图书书目。
- `tblBookCopy`：馆藏副本。
- `tblBorrowRecord`：借阅记录。

数据库包含管理员、学生和教师演示账号，与学生、教师账号关联的演示学籍，以及 10 种
演示图书。演示学生账号有一条在借记录和一条逾期未还记录。
密码以 PBKDF2 加盐哈希保存，不保存明文密码。

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
| 学籍 | `tblStudent`、`tblTeacher`、`tblDepartment`、`tblSchoolClass` |
| 选课 | `tblCourse`、`tblTeachingClass`、`tblEnrollment` |
| 图书馆 | `tblBook`、`tblBookCopy`、`tblBorrowRecord`（已自动创建） |
| 商店 | `tblProduct`、`tblCartItem`、`tblOrder`、`tblOrderItem` |

Access 不适合大量并发写入。库存扣减、选课、借书等关键业务应由服务器端使用短事务完成。
