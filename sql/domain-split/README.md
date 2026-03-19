# 领域拆分 SQL 说明

本目录用于初始化领域拆分后的独立数据库。

## 脚本顺序

1. `01-pixel-user.sql`
2. `02-pixel-image.sql`
3. `03-pixel-trade.sql`
4. `05-pixel-image-asset-upgrade.sql`
5. `06-pixel-style-template-negative-prompt-upgrade.sql`
6. `07-pixel-admin-rbac.sql`（如需管理后台账号/角色权限）
7. `08-pixel-hot-image.sql`
8. `09-pixel-vedio.sql`
9. `10-pixel-hot-image-video-upgrade.sql`
10. `14-pixel-image-asset-multi-image-upgrade.sql`

## 使用原则

- 先建新库，不要立刻切换现有服务数据源
- 先导出现有 `pixel` 库的历史数据再做迁移
- 优先迁图片领域，最后再迁认证领域

## 说明

- 本目录脚本是“目标结构脚本”，不是一键迁移脚本
- 当前服务仍可先继续使用原有 `pixel` 库
- 等新库初始化和数据迁移完成后，再逐个服务切换数据源
