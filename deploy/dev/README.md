# 本地开发依赖服务

一键启动云枢智学本地开发需要的 MySQL、Redis 和对象存储。只用于本机开发，线上的 MySQL、Redis、对象存储使用云托管服务（见 `docs/design/02.整体架构设计.md`）。

## 服务

| 服务 | 镜像 | 本机地址 | 用途 |
|---|---|---|---|
| MySQL | `mysql:8.4` | `127.0.0.1:3306` | 业务数据库，容器只建空库，表由后端启动时的 Flyway 迁移创建 |
| Redis | `redis:7.4-alpine` | `127.0.0.1:6379` | 验证码、登录会话、语音名额等短期数据 |
| SeaweedFS | `chrislusf/seaweedfs:4.53` | `127.0.0.1:8333` | 本地模拟 S3 对象存储，自动创建存储桶；未签名的请求只能读取文件（用于公开访问上传的图片），不能列目录和写入 |
| SeaweedFS 网页界面 | 同上 | <http://127.0.0.1:8888/> | 在浏览器中查看和管理存储的文件，存储桶在 `/buckets/` 目录下 |

端口只绑定 `127.0.0.1`，局域网内的其他设备无法访问。

只有 SeaweedFS 网页界面能用浏览器打开。MySQL、Redis 和 S3 接口不是网页服务，需要用 Navicat、`redis-cli`、S3 客户端连接；用浏览器直接打开 8333 会显示 `AccessDenied`，这是正常的；已上传的公开文件可以通过完整地址打开，如 `http://127.0.0.1:8333/yunshu-dev/public/images/...`。

网页界面**没有登录验证**，打开就能上传和删除文件，所以只开放给本机访问，不要改成对外开放。

MinIO 官方已停止发布社区版镜像，所以本地对象存储改用 SeaweedFS。它兼容 S3 接口，线上换成云对象存储时不影响代码。

## 账号密码

都在本目录的 `.env` 文件中，密码为随机生成。`.env` 不要提交到代码仓库，模板见 `.env.example`。

| 用途 | 配置项 |
|---|---|
| 应用连接 MySQL | `MYSQL_USER`、`MYSQL_PASSWORD`，数据库 `MYSQL_DATABASE` |
| 管理 MySQL | `root` / `MYSQL_ROOT_PASSWORD` |
| Redis | `REDIS_PASSWORD` |
| 对象存储 | `S3_ACCESS_KEY`、`S3_SECRET_KEY`，存储桶 `S3_BUCKET` |
| 管理后台初始账号 | `ADMIN_INIT_ACCOUNT` / `ADMIN_INIT_PASSWORD`，首次登录必须修改密码，之后以修改后的密码为准 |

Navicat 连接 MySQL：主机 `127.0.0.1`，端口 `3306`，用户和密码见 `.env`。

## 常用命令

以下命令都在本目录（`deploy/dev`）中执行。

启动全部服务：

```bash
docker compose up -d
```

查看状态，三个服务都显示 `healthy` 即可使用，`seaweedfs-init` 显示已退出是正常的：

```bash
docker compose ps -a
```

查看某个服务的日志：

```bash
docker compose logs -f mysql
```

修改了 `docker-compose.yml` 中 SeaweedFS 的访问权限配置后，重建该容器使其生效（文件保留）：

```bash
docker compose up -d --force-recreate seaweedfs
```

停止服务，数据保留：

```bash
docker compose down
```

清空所有数据，下次启动后端时由 Flyway 重新建表（**不可恢复**）：

```bash
docker compose down -v
```

## 注意

- 表结构由 Flyway 管理（`server/src/main/resources/db/migration/`），修改表结构时新增迁移脚本，不要直接改库。
- 本机原有的 MySQL 已停止并关闭开机自启，所以 3306 端口可以给容器使用。如果重新启动本机 MySQL，需要先在 `.env` 中修改 `MYSQL_PORT`。
