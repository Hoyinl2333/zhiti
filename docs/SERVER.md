# 服务器部署与维护

服务使用独立 `zhiti-server` 容器，仅映射宿主机 443。容器由本机交叉编译的静态 Go 二进制和空 `scratch` 镜像组成，不依赖 Docker Hub。现有 80、8080、3306 和 8025 端口不需要改动。

## 首次部署

1. 复制 `infra/.env.example` 为 `infra/.env`，再用至少 32 个字符的随机值替换 `ZHITI_PEPPER`。
2. 设置 `ZHITI_SERVER_HOST=server.example.com`，再执行 `infra/scripts/create-secrets.sh`。
3. 构建并签名你有权处理的题库包。
4. 设置 `ZHITI_DEPLOY_HOST=ubuntu@server.example.com`、`ZHITI_REMOTE_DIR=/opt/zhiti` 后执行 `infra/scripts/deploy.sh`。
5. 在云安全组中允许 TCP 443 入站。
6. 执行 `curl --cacert .secrets/ca.crt https://server.example.com/healthz` 检查服务。

## 激活码

在服务器执行：

```bash
cd "$ZHITI_REMOTE_DIR/infra"
docker compose run --rm --entrypoint /zhiti-admin zhiti create --state /data/state.json --count 1
docker compose run --rm --entrypoint /zhiti-admin zhiti status --state /data/state.json
docker compose run --rm --entrypoint /zhiti-admin zhiti unbind --state /data/state.json --code ZHITI-XXXX-XXXX-XXXX-XXXX
docker compose run --rm --entrypoint /zhiti-admin zhiti revoke --state /data/state.json --code ZHITI-XXXX-XXXX-XXXX-XXXX
```

每个激活码只绑定一个设备摘要。状态命令只显示摘要前 12 位。

## 备份

需备份本地 `.secrets/` 和服务器 `$ZHITI_REMOTE_DIR/infra/runtime/data/state.json`。CA 私钥、内容签名私钥和 APK 签名库只保存在本地离线备份中。
