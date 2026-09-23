# 发布构建

```bash
infra/scripts/create-secrets.sh
cd android
./gradlew clean test lintDebug assembleRelease
```

发布文件位于 `android/app/build/outputs/apk/release/app-release.apk`。复制后生成校验信息：

```bash
cp android/app/build/outputs/apk/release/app-release.apk dist/zhiti-v1.0.1.apk
shasum -a 256 dist/zhiti-v1.0.1.apk > dist/zhiti-v1.0.1.apk.sha256
keytool -list -v -keystore .secrets/zhiti-release.jks -alias zhiti
```

`.secrets/release.env` 记录本机密钥库路径和密码。密钥库丢失后无法对已安装应用做覆盖升级，必须至少保存两份离线备份。
