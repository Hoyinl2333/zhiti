# 示例信任材料

这两个公开文件只用于让未配置服务的源码构建能够通过资源编译；它们不对应任何可访问服务，也不能验证生产题库。

自建服务时运行：

```bash
ZHITI_SERVER_HOST=server.example.com infra/scripts/create-secrets.sh
```

Gradle 检测到仓库根目录的 `.secrets/` 后会自动使用其中的 `ca.crt` 与 `content-ed25519-public.pem`。也可显式指定：

```bash
cd android
./gradlew -PzhitiTrustDir=../.secrets -PzhitiBaseUrl=https://server.example.com assembleDebug
```
