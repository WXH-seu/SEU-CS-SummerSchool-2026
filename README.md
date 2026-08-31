# SEU CS Summer School 2026 - Virtual Campus

本仓库是面向专业技能实训课程的虚拟校园 C/S 项目。工程严格保持 Java 8 兼容，使用 Swing 客户端、Socket 对象流、多线程服务器和 Microsoft Access 数据库。

## 技术基线

- Eclipse
- JDK 1.8
- Maven 多模块工程
- Swing + FlatLaf
- TCP Socket + `ObjectInputStream`/`ObjectOutputStream`
- `ExecutorService` 多客户端线程池
- Microsoft Access + UCanAccess 5.0.1
- JUnit 4

## 工程结构

```text
vcampus-common/   公共消息、DTO、枚举和协议常量
vcampus-server/   Socket 服务端、请求分发、业务服务和 DAO
vcampus-client/   Swing 客户端、网络连接和业务页面
vcampus-database/ Access 数据库和数据库说明
docs/             课程要求、计划和项目文档
```

## 当前可运行功能

- 客户端与服务器端对象流通信。
- 服务器端固定线程池处理多个客户端。
- 首次运行自动创建 `vcampus-database/vCampus.accdb`。
- 自动建立用户与学籍数据表，并创建演示账号和演示学籍。
- 登录、会话创建和登出。
- 学生、教师、院系和班级查询。
- 管理员维护学生、教师、院系和班级。
- 学生仅查看本人学籍，教师只读，管理员可维护的服务端权限控制。
- 课程查询、选课、退课和个人课表查询。
- 重复选课、容量限制和上课时间冲突校验。
- 教师仅查看本人课程，管理员课程维护的服务端权限控制。
- 五个必做模块的客户端导航和协议操作入口。
- 图书检索、详情查看和可借 / 馆藏数量显示。

校园商店目前保留协议与页面入口，由对应成员继续实现。图书馆借阅、归还和管理维护仍待接入。

## 演示账号

| 角色 | 账号 | 密码 |
| --- | --- | --- |
| 管理员 | `admin` | `admin123` |
| 学生 | `student` | `student123` |
| 教师 | `teacher` | `teacher123` |

演示密码仅用于初始开发。正式提交前必须更换，并准备专门的答辩数据。

## Eclipse 导入

1. 安装并选择 JDK 1.8。
2. 打开 Eclipse。
3. 选择 `File` → `Import` → `Maven` → `Existing Maven Projects`。
4. 选择仓库根目录。
5. 确认三个子模块全部被选中并完成导入。
6. 检查各项目的 `Java Compiler` 和 `Java Build Path` 均为 Java 8。

不建议手工维护 `.classpath` 和 `.project`，由 Eclipse Maven 集成自动生成即可。

## 构建

在仓库根目录执行：

```bash
mvn clean package
```

成功后生成：

```text
vcampus-server/target/vCampusServer.jar
vcampus-client/target/vCampusClient.jar
```

## 启动

必须从仓库根目录启动，以便默认相对路径指向正确数据库位置。

先启动服务器：

```bash
java -jar vcampus-server/target/vCampusServer.jar
```

再启动客户端：

```bash
java -jar vcampus-client/target/vCampusClient.jar
```

客户端默认连接 `127.0.0.1:4444`。

## 外部配置

如需改变服务器端口或数据库路径，新建服务器配置文件：

```properties
server.port=4444
server.workerThreads=16
database.path=vcampus-database/vCampus.accdb
```

启动时指定：

```bash
java -Dvcampus.server.config=config/server.properties \
  -jar vcampus-server/target/vCampusServer.jar
```

客户端配置：

```properties
server.host=127.0.0.1
server.port=4444
```

启动时指定：

```bash
java -Dvcampus.client.config=config/client.properties \
  -jar vcampus-client/target/vCampusClient.jar
```

## 模块开发约定

每个业务模块都应包含：

1. `vcampus-common` 中的 DTO 和 `Operation`。
2. `vcampus-client` 中的 Swing 页面和客户端服务。
3. `vcampus-server` 中的 Service、DAO 和请求处理器。
4. Access 数据表、约束和测试数据。
5. 单元测试、集成测试和 JavaDoc。

禁止在 Swing 页面中直接访问数据库，也禁止客户端直接打开 Access 文件。

## 文档

- [总体计划](docs/计划安排/项目总体计划.md)
- [Git 与 GitHub 协作指南](docs/Git与GitHub协作操作指南.md)
- [架构与扩展指南](docs/架构与扩展指南.md)
- [数据库设计说明](docs/数据库设计说明.md)
- [数据库变更记录](docs/数据库变更记录.md)
- [学籍管理模块说明](docs/学籍管理模块说明.md)
- [学籍模块测试记录](docs/学籍模块测试记录.md)
- [选课管理模块说明](docs/选课模块说明.md)
- [选课模块测试记录](docs/选课模块测试记录.md)
- [通信协议说明](docs/通信协议说明.md)
- [图书馆模块说明](docs/图书馆模块说明.md)
