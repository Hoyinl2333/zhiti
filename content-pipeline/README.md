# 题库构建工具

构建程序读取固定提交的上游仓库，生成两个可独立安装的题库包：

```bash
python3 build_content.py \
  --source /path/to/kaogongzhentizhengliu \
  --output build \
  --version 2026.09.1 \
  --base-url https://43.136.39.211/v1/packs \
  --signing-key ../.secrets/content-ed25519-private.pem
```

输出包括 `judgment-<version>.zip`、`data-analysis-<version>.zip`、`catalog.json`、`catalog.sig` 和 `audit.json`。ZIP 的时间戳、排序和 SQLite 写入顺序固定，相同输入可得到相同摘要。

首次使用可执行 `../infra/scripts/create-secrets.sh` 生成内容签名密钥。私钥和题库包不会提交 Git。

