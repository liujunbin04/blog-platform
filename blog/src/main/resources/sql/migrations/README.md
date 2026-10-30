# 历史数据库迁移

`init.sql` 是全新数据库的唯一基线，Compose 只自动执行该文件。

本目录中的迁移脚本只用于升级已经存在的本地数据库，不会被 Compose 自动执行。

## 手动执行

```powershell
# 密码取自仓库根目录 .env 的 MYSQL_ROOT_PASSWORD（本地开发默认 123456）
Get-Content -Raw .\migration-2026-08-26.sql |
  docker compose exec -T mysql mysql -uroot -p123456 blog

Get-Content -Raw .\migration-2026-08-28.sql |
  docker compose exec -T mysql mysql -uroot -p123456 blog
```

若本地数据可以删除，优先使用全新初始化：

```powershell
docker compose down -v
docker compose up -d --build
```
