# 知题

「知题」是一款离线公务员考试刷题应用。题库内容在首次激活后按模块下载，做题记录仅保存在手机。

## 目录

- `android/`：Kotlin + Jetpack Compose Android 应用
- `content-pipeline/`：题库转换、校验、签名与打包工具
- `server/`：激活和题库包下载服务
- `infra/`：证书、容器和部署脚本
- `web-demo/`：早期浏览器 Demo 与回归样例
- `docs/`：安装、签名、部署和维护说明

上游题库固定在提交 `84ab93d4b64b61d897bece8a1c0a5bab06b4feb2`。生成的题库包、服务数据和私钥不纳入 Git。

