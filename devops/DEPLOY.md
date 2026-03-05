# Docker Compose 部署指南

## 服务列表

本docker-compose包含以下服务：

| 服务 | 端口 | 说明 | 镜像源 |
|------|------|------|--------|
| MySQL | 3306 | 数据库 | 华为云镜像 |
| Redis | 6379 | 缓存 | 华为云镜像 |
| Nacos | 8848, 9848, 9849 | 注册中心/配置中心 | 华为云镜像 |
| RocketMQ NameServer | 9876 | 消息队列名称服务器 | 阿里云镜像 |
| RocketMQ Broker | 10909, 10911, 10912 | 消息队列Broker | 阿里云镜像 |
| RocketMQ Console | 8090 | RocketMQ管理控制台 | 阿里云镜像 |
| Seata Server | 7091, 8091 | 分布式事务协调器 | 阿里云镜像 |

## 快速启动

### 1. 启动所有服务

```bash
cd devops
docker-compose up -d
```

### 2. 验证服务

- **Nacos**: http://localhost:8848/nacos (nacos/nacos)
- **RocketMQ Console**: http://localhost:8090
- **Seata Console**: http://localhost:7091 (seata/seata)

### 3. 初始化数据库

```bash
docker exec -it pixel-mysql mysql -uroot -pwang331333 pixel < ../docs/sql/local_message.sql
```

### 4. 创建RocketMQ Topic

```bash
docker exec -it pixel-rocketmq-namesrv sh mqadmin updateTopic -n localhost:9876 -t pixel-image-generation -c DefaultCluster
```

完整文档请查看项目文档。