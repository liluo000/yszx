# 云枢智学

在线学习、刷题与 AI 面试平台。需求和设计见 `docs/`，架构见 `docs/design/02.整体架构设计.md`。

## 目录

| 目录 | 内容 |
|---|---|
| `docs/` | 需求文档、设计文档、数据库与接口规范 |
| `prototype/` | 页面原型，仅作交互参考，开发以需求文档为准 |
| `server/` | 后端：JDK 17、Spring Boot 4.1、MyBatis-Plus、Sa-Token、Flyway，单 Maven 工程 |
| `web/` | 前端：Vue 3、TypeScript、Vite，pnpm 工作区（学员端 `apps/student`、管理后台 `apps/admin`） |
| `deploy/dev/` | 本地开发依赖服务：MySQL 8.4、Redis 7.4、SeaweedFS |
| `scripts/sql/` | 初始建表脚本 `schema.sql`，与 Flyway 的 V1 迁移内容一致 |

## 本地环境要求

| 工具 | 版本 |
|---|---|
| JDK | 17（Eclipse Temurin） |
| Maven | 3.9 及以上 |
| Node.js | 22.12 及以上 |
| pnpm | 12 |
| Docker Desktop | 用于启动本地依赖服务 |

## 启动顺序

**1. 启动本地依赖服务**（MySQL、Redis、对象存储），详见 `deploy/dev/README.md`：

```bash
cd deploy/dev && docker compose up -d
```

**2. 启动后端**，端口 8080。第一次启动时 Flyway 会自动建表；账号密码自动读取 `deploy/dev/.env`：

```bash
cd server && mvn spring-boot:run
```

在 IntelliJ IDEA 中也可以直接运行 `com.yunshuzhixue.YunshuApplication`。健康检查地址：<http://127.0.0.1:8080/actuator/health>

**3. 安装前端依赖**（只需一次）：

```bash
cd web && pnpm install
```

**4. 启动前端**，学员端 <http://127.0.0.1:5173/>，管理后台 <http://127.0.0.1:5174/>：

```bash
cd web && pnpm dev:student
```

```bash
cd web && pnpm dev:admin
```

前端开发服务器会把 `/api` 和 `/ws` 请求转发到本地后端。

**5. 首次登录管理后台**：账号和初始密码见 `deploy/dev/.env` 中的 `ADMIN_INIT_ACCOUNT`、`ADMIN_INIT_PASSWORD`。第一次登录后必须修改密码，之后用新密码登录。这个账号由后端在库中没有任何后台账号时自动创建，只创建一次。

## 常用命令

| 位置 | 命令 | 作用 |
|---|---|---|
| `server/` | `mvn -DskipTests package` | 打包后端 |
| `web/` | `pnpm typecheck` | 类型检查 |
| `web/` | `pnpm lint` | ESLint 检查 |
| `web/` | `pnpm format` | Prettier 格式化 |
| `web/` | `pnpm build` | 构建学员端和管理后台 |

## 数据库变更

表结构由 Flyway 管理，迁移脚本在 `server/src/main/resources/db/migration/`。已执行过的迁移脚本不要修改；修改表结构时新增 `V2__说明.sql` 等脚本，并同步更新 `docs/design/03.数据库设计.md`。
