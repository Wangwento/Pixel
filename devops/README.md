# Pixel DevOps

基于 Docker Compose 的中间件部署方案。

## 服务列表

| 服务 | 端口 | 说明 | 访问地址 |
|------|------|------|----------|
| MySQL | 3306 | 数据库 | `root/wang331333` |
| Redis | 6379 | 缓存 | - |
| Nacos | 8848 | 服务注册/配置中心 | http://localhost:8848/nacos (nacos/nacos) |
| Kafka | 9094 | 消息队列(外部访问) | - |
| Kafka UI | 8089 | Kafka管理界面 | http://localhost:8089 |
| MinIO | 9001 | 对象存储(可选) | http://localhost:9001 (minioadmin/minioadmin123) |

## 快速启动

```bash
# 添加执行权限
chmod +x start.sh

# 启动所有服务
./start.sh start

# 查看状态
./start.sh status

# 查看日志
./start.sh logs
./start.sh logs nacos

# 停止服务
./start.sh stop

# 清理数据(危险)
./start.sh clean
```

## 目录结构

```
devops/
├── docker-compose.yml      # Docker Compose配置
├── start.sh                # 启动脚本
├── README.md               # 说明文档
└── init/
    └── mysql/
        ├── 01-nacos-config.sql    # Nacos数据库初始化
        └── 02-pixel-init.sql      # Pixel业务数据库初始化
```

## 首次启动

1. **启动服务**
   ```bash
   ./start.sh start
   ```

2. **等待MySQL初始化完成** (约30秒)

3. **访问Nacos控制台**
   - 地址: http://localhost:8848/nacos
   - 用户名: nacos
   - 密码: nacos

4. **创建命名空间** (可选)
   - 在Nacos控制台创建 `pixel` 命名空间

## 配置说明

### 应用连接配置

```yaml
# MySQL
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/pixel?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false
    username: root
    password: wang331333

# Redis
  data:
    redis:
      host: localhost
      port: 6379

# Nacos
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848

# Kafka
  kafka:
    bootstrap-servers: localhost:9094
```

## 常见问题

### 1. Nacos启动失败

检查MySQL是否正常启动:
```bash
./start.sh logs mysql
```

### 2. 端口冲突

修改 `docker-compose.yml` 中的端口映射:
```yaml
ports:
  - "3307:3306"  # 将3306改为3307
```

### 3. 清理重新开始

```bash
./start.sh clean
./start.sh start
```

## 资源占用

| 服务 | 内存 | CPU |
|------|------|-----|
| MySQL | ~300MB | 低 |
| Redis | ~50MB | 低 |
| Nacos | ~512MB | 低 |
| Kafka | ~500MB | 中 |
| MinIO | ~100MB | 低 |

**总计**: 约 1.5GB 内存
