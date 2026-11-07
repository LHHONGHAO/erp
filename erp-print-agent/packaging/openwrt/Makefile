# OpenWrt 打包（在 Linux/WSL 下使用；Windows 请用 scripts/build.ps1）
PKG_NAME    ?= erp-print-agent
PKG_VERSION ?= 0.1.0
PKG_ARCH    ?= aarch64_cortex-a53
GO          ?= go
DIST        := build
STAGE       := $(DIST)/stage
BIN         := $(DIST)/erp-print-agent

.PHONY: all build stage ipk clean local

all: ipk

build:
	CGO_ENABLED=0 GOOS=linux GOARCH=arm64 $(GO) build -trimpath \
		-ldflags "-s -w -X main.version=$(PKG_VERSION)" \
		-o $(BIN) ./cmd/erp-print-agent

stage: build
	rm -rf $(STAGE)
	mkdir -p $(STAGE)/usr/bin $(STAGE)/etc/init.d $(STAGE)/etc/erp-print-agent
	mkdir -p $(STAGE)/usr/lib/lua/luci/controller $(STAGE)/usr/lib/lua/luci/view/erp_print_agent
	install -m 0755 $(BIN) $(STAGE)/usr/bin/erp-print-agent
	install -m 0755 packaging/openwrt/files/erp-print-agent.init $(STAGE)/etc/init.d/erp-print-agent
	install -m 0600 packaging/openwrt/files/config.json $(STAGE)/etc/erp-print-agent/config.json
	install -m 0644 packaging/openwrt/luci/controller/erp_print_agent.lua $(STAGE)/usr/lib/lua/luci/controller/erp_print_agent.lua
	install -m 0644 packaging/openwrt/luci/view/erp_print_agent/index.htm $(STAGE)/usr/lib/lua/luci/view/erp_print_agent/index.htm
	@if [ -f packaging/openwrt/files/ca.pem ]; then \
		install -m 0644 packaging/openwrt/files/ca.pem $(STAGE)/etc/erp-print-agent/ca.pem; \
	else \
		echo "警告: 未找到 packaging/openwrt/files/ca.pem，IPK 内不含 CA 证书"; \
	fi

ipk: stage
	$(GO) run ./cmd/mk-ipk -stage $(STAGE) \
		-out $(DIST)/$(PKG_NAME)_$(PKG_VERSION)_$(PKG_ARCH).ipk \
		-package $(PKG_NAME) -version $(PKG_VERSION) -arch $(PKG_ARCH) \
		-depends "libc,luci-base" \
		-conffiles /etc/erp-print-agent/config.json

# 本地调试：构建 Windows 版代理与 mock-server
local:
	CGO_ENABLED=0 go build -o $(DIST)/win/erp-print-agent.exe ./cmd/erp-print-agent
	CGO_ENABLED=0 go build -o $(DIST)/win/mock-server.exe ./cmd/mock-server

clean:
	rm -rf $(DIST)
