# 服务器部署与维护

服务使用独立 `zhiti-server` 容器，仅映射宿主机 443。现有 80、8080、3306 和 8025 端口不需要改动。

## 首次部署

1. 在本机执行 `infra/scripts/create-secrets.sh`。
2. 构建并签名题库包。
3. 执行 `infra/scripts/deploy.sh`。
4. 在腾讯云安全组中允许 TCP 443 入站。
5. 执行 `curl --cacert .secrets/ca.crt https://43.136.39.211/healthz` 检查服务。

## 激活码

在服务器执行：

```bash
cd /opt/zhiti/infra
docker compose run --rm --entrypoint zhiti-admin zhiti create --state /data/state.json --count 1
docker compose run --rm --entrypoint zhiti-admin zhiti status --state /data/state.json
docker compose run --rm --entrypoint zhiti-admin zhiti unbind --state /data/state.json --code ZHITI-XXXX-XXXX-XXXX-XXXX
docker compose run --rm --entrypoint zhiti-admin zhiti revoke --state /data/state.json --code ZHITI-XXXX-XXXX-XXXX-XXXX
```

每个激活码只绑定一个设备摘要。状态命令只显示摘要前 12 位。

## 备份

需备份本地 `.secrets/` 和服务器 `/opt/zhiti/infra/runtime/data/state.json`。CA 私钥、内容签名私钥和 APK 签名库只保存在本地离线备份中。

