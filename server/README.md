# 知题内容服务

服务使用 Go 标准库，单进程监听 HTTPS 443。状态以原子替换的 JSON 文件保存，字段只包含激活码摘要、令牌摘要、设备摘要和时间。

```bash
export ZHITI_PEPPER='至少 32 字节随机值'
go run ./cmd/zhiti-admin create --state data/state.json --count 5
go run ./cmd/zhiti-server
```

配置项见 `internal/config/config.go`。日志不会输出激活码、令牌或设备摘要。

