FROM golang:1.25-alpine AS build
WORKDIR /src
COPY go.mod ./
COPY cmd ./cmd
COPY internal ./internal
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -trimpath -ldflags="-s -w" -o /out/zhiti-server ./cmd/zhiti-server && \
    CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -trimpath -ldflags="-s -w" -o /out/zhiti-admin ./cmd/zhiti-admin

FROM alpine:3.22
RUN addgroup -g 1000 -S zhiti && adduser -u 1000 -S -G zhiti zhiti
COPY --from=build /out/zhiti-server /usr/local/bin/zhiti-server
COPY --from=build /out/zhiti-admin /usr/local/bin/zhiti-admin
USER zhiti
ENTRYPOINT ["/usr/local/bin/zhiti-server"]
