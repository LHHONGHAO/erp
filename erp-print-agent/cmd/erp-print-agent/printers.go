package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"net"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"time"
)

// defaultP910ndConfig OpenWrt p910nd 的 UCI 配置路径。
const defaultP910ndConfig = "/etc/config/p910nd"

// localPrinter 本机可用的打印输出，供 LuCI 设置页「选择打印机」展示。
// 数据来源：p910nd 队列（device/port）与 /dev 下的 USB 打印设备节点。
type localPrinter struct {
	ID      string `json:"id"`
	Name    string `json:"name"`
	Device  string `json:"device,omitempty"`
	Host    string `json:"host"`
	Port    int    `json:"port"`
	Online  bool   `json:"online"`
	Enabled bool   `json:"enabled"`
	Source  string `json:"source"`
}

// uciSection 仅保留解析 p910nd 所需的最小结构。
type uciSection struct {
	name    string
	typ     string
	options map[string]string
}

// runPrinters 枚举本机打印机，输出 JSON 数组供 LuCI 设置页选择。
func runPrinters(args []string) error {
	fs := flag.NewFlagSet("printers", flag.ExitOnError)
	_ = fs.String("config", defaultConfigPath, "配置文件路径（保留参数，保持命令一致）")
	uciPath := fs.String("uci", defaultP910ndConfig, "p910nd 配置文件路径")
	_ = fs.Parse(args)

	out, err := json.Marshal(listLocalPrinters(*uciPath))
	if err != nil {
		return err
	}
	fmt.Println(string(out))
	return nil
}

// listLocalPrinters 汇总 p910nd 队列与未纳管的 USB 打印设备。
func listLocalPrinters(uciPath string) []localPrinter {
	var list []localPrinter
	used := make(map[string]bool, 4)
	idSet := make(map[string]bool, 4)

	for _, section := range parseUciFile(uciPath) {
		if section.typ != "p910nd" {
			continue
		}
		device := strings.TrimSpace(section.options["device"])
		port := atoiDefault(section.options["port"], 9100)
		id := section.name
		if id == "" {
			id = deviceID(device)
		}
		if id == "" {
			id = "p" + strconv.Itoa(port)
		}
		id = uniqueID(id, idSet)
		name := strings.TrimSpace(section.options["name"])
		if name == "" {
			name = deviceName(device)
		}
		enabled := strings.TrimSpace(section.options["enabled"]) != "0"
		list = append(list, localPrinter{
			ID:      id,
			Name:    name,
			Device:  device,
			Host:    "127.0.0.1",
			Port:    port,
			Online:  enabled && probePort(port),
			Enabled: enabled,
			Source:  "p910nd",
		})
		if device != "" {
			used[device] = true
		}
	}

	// 未被 p910nd 纳管的 USB 打印设备：提示用户先在 p910nd 里启用
	for _, pattern := range []string{"/dev/usb/lp*", "/dev/lp*", "/dev/usbmisc/lp*"} {
		files, err := filepath.Glob(pattern)
		if err != nil {
			continue
		}
		for _, device := range files {
			if used[device] {
				continue
			}
			list = append(list, localPrinter{
				ID:      uniqueID(deviceID(device), idSet),
				Name:    deviceName(device),
				Device:  device,
				Host:    "127.0.0.1",
				Port:    0,
				Online:  false,
				Enabled: false,
				Source:  "usb",
			})
			used[device] = true
		}
	}

	sort.Slice(list, func(i, j int) bool {
		if list[i].Port != list[j].Port {
			return list[i].Port < list[j].Port
		}
		return list[i].ID < list[j].ID
	})
	if list == nil {
		list = []localPrinter{}
	}
	return list
}

// parseUciFile 读取并解析 UCI 配置文件，文件不存在时返回空列表。
func parseUciFile(path string) []uciSection {
	raw, err := os.ReadFile(path)
	if err != nil {
		return nil
	}
	return parseUci(string(raw))
}

// parseUci 解析 `config <type> ['name']` 与 `option key 'value'` 形式的最小 UCI 子集。
func parseUci(text string) []uciSection {
	var sections []uciSection
	index := -1
	for _, line := range strings.Split(text, "\n") {
		line = strings.TrimSpace(line)
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		fields := strings.Fields(line)
		switch fields[0] {
		case "config":
			if len(fields) < 2 {
				index = -1
				continue
			}
			section := uciSection{typ: fields[1], options: map[string]string{}}
			if len(fields) >= 3 {
				section.name = strings.Trim(fields[2], "'\"")
			}
			sections = append(sections, section)
			index = len(sections) - 1
		case "option":
			if index < 0 || len(fields) < 2 {
				continue
			}
			value := strings.TrimSpace(strings.TrimPrefix(line, "option"))
			value = strings.TrimSpace(strings.TrimPrefix(value, fields[1]))
			sections[index].options[fields[1]] = strings.Trim(value, "'\"")
		}
	}
	return sections
}

// deviceID 取设备节点的 basename 作为稳定的打印机 id。
func deviceID(device string) string {
	base := strings.TrimSpace(filepath.Base(device))
	if base == "" || base == "." || base == "/" {
		return ""
	}
	return base
}

// deviceName 生成可读名称，例如 lp0 → USB 打印机 lp0。
func deviceName(device string) string {
	if name := deviceID(device); name != "" {
		return "USB 打印机 " + name
	}
	return "USB 打印机"
}

// uniqueID 重复 id 追加序号，保证同一页面的打印机可区分。
func uniqueID(id string, used map[string]bool) string {
	if id == "" {
		id = "printer"
	}
	if !used[id] {
		used[id] = true
		return id
	}
	for i := 2; ; i++ {
		candidate := id + "-" + strconv.Itoa(i)
		if !used[candidate] {
			used[candidate] = true
			return candidate
		}
	}
}

// probePort 探测本机 p910nd 端口是否可连接。
func probePort(port int) bool {
	if port <= 0 {
		return false
	}
	conn, err := net.DialTimeout("tcp", net.JoinHostPort("127.0.0.1", strconv.Itoa(port)), 400*time.Millisecond)
	if err != nil {
		return false
	}
	_ = conn.Close()
	return true
}

func atoiDefault(value string, fallback int) int {
	value = strings.TrimSpace(value)
	if value == "" {
		return fallback
	}
	num, err := strconv.Atoi(value)
	if err != nil || num < 1 || num > 65535 {
		return fallback
	}
	return num
}
