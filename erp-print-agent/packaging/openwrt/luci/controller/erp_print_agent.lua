module("luci.controller.erp_print_agent", package.seeall)

local http = require "luci.http"
local jsonc = require "luci.jsonc"
local fs = require "nixio.fs"
local sys = require "luci.sys"
local disp = require "luci.dispatcher"
local tpl = require "luci.template"

local CONFIG = "/etc/erp-print-agent/config.json"
local INIT = "/etc/init.d/erp-print-agent"
local BIN = "/usr/bin/erp-print-agent"

function index()
	local page = entry({"admin", "services", "erp_print_agent"}, call("action_page"), _("远程打印"), 60)
	page.dependent = false
	page.icon = "print"

	local save = entry({"admin", "services", "erp_print_agent", "save"}, call("action_save"))
	save.leaf = true
	save.post = true

	local pair = entry({"admin", "services", "erp_print_agent", "pair"}, call("action_pair"))
	pair.leaf = true
	pair.post = true

	local test = entry({"admin", "services", "erp_print_agent", "test"}, call("action_test"))
	test.leaf = true
	test.post = true

	local svc = entry({"admin", "services", "erp_print_agent", "service"}, call("action_service"))
	svc.leaf = true
	svc.post = true
end

local function trim(s)
	s = tostring(s or "")
	s = s:gsub("^%s+", "")
	s = s:gsub("%s+$", "")
	return s
end

local function read_config()
	local raw = fs.readfile(CONFIG) or ""
	local ok, obj = pcall(jsonc.parse, raw)
	if ok and type(obj) == "table" then
		return obj, raw
	end
	return nil, raw
end

local function is_running()
	return sys.call("pgrep -f " .. BIN .. " >/dev/null 2>&1") == 0
end

local function is_enabled()
	return fs.access("/etc/rc.d/S95erp-print-agent") and true or false
end

-- 注意：LuCI 模板环境的查找顺序是 viewns(含 _G 与 ucode 桥接 L) 优先于 scope，
-- 因此像 config / error / version 这类与全局同名的变量名会被全局值覆盖，必须换名。
local function render(opts)
	local cfg, raw = read_config()
	opts.agentcfg = opts.agentcfg or cfg
	opts.cfgraw = opts.cfgraw or raw
	opts.running = is_running()
	opts.enabled = is_enabled()
	opts.binver = trim(sys.exec(BIN .. " version 2>/dev/null"))
	opts.token = disp.context.authtoken

	local current = opts.agentcfg or {}
	opts.paired = (trim(current.deviceId) ~= "" and trim(current.token) ~= "")
	local printers = current.printers or {}
	-- 嵌入 script 前转义 "<"，避免打印机名称中出现 </script> 截断页面
	opts.printers_json = (jsonc.stringify(printers):gsub("<", "\\u003c"))
	tpl.render("erp_print_agent/index", opts)
end

local function back(msg)
	http.redirect(disp.build_url("admin", "services", "erp_print_agent") .. "?msg=" .. msg)
end

local function collect_printers()
	local raw = trim(http.formvalue("printersJson"))
	if raw == "" then
		return nil, "打印机配置为空"
	end
	local ok, list = pcall(jsonc.parse, raw)
	if not ok or type(list) ~= "table" then
		return nil, "打印机配置格式错误"
	end
	local printers = {}
	for i, item in ipairs(list) do
		if type(item) ~= "table" then
			return nil, "打印机配置格式错误"
		end
		local id = trim(item.id)
		if id == "" then
			id = (i == 1) and "default" or ("printer" .. i)
		end
		local port = tonumber(item.port) or 9100
		if port < 1 or port > 65535 then
			return nil, "打印机 " .. id .. " 的端口非法"
		end
		printers[#printers + 1] = {
			id = id,
			name = trim(item.name),
			host = trim(item.host),
			port = port,
			connectTimeoutMs = tonumber(item.connectTimeoutMs) or 3000,
			writeTimeoutMs = tonumber(item.writeTimeoutMs) or 15000,
			enabled = item.enabled ~= false
		}
	end
	if #printers == 0 then
		return nil, "至少保留一台打印机"
	end
	return printers
end

-- 保存表单；deviceId/token 只保留原值，由配对流程下发
local function persist_form()
	local custom = trim(http.formvalue("rawjson"))
	local cfg

	if custom ~= "" then
		local ok, obj = pcall(jsonc.parse, custom)
		if not ok or type(obj) ~= "table" then
			return nil, "JSON 语法错误，未保存"
		end
		cfg = obj
	else
		cfg = read_config() or {}
		cfg.server = cfg.server or {}
		cfg.log = cfg.log or {}
		cfg.server.url = trim(http.formvalue("serverUrl"))
		cfg.server.serverName = trim(http.formvalue("serverName"))
		cfg.server.insecureSkipVerify = (http.formvalue("insecureSkipVerify") == "1")
		cfg.storeName = trim(http.formvalue("storeName"))
		cfg.printerModel = trim(http.formvalue("printerModel"))
		cfg.log.level = http.formvalue("logLevel") or cfg.log.level or "info"

		local printers, perr = collect_printers()
		if not printers then
			return nil, perr
		end
		cfg.printers = printers
	end

	local ok, err = pcall(function()
		fs.writefile(CONFIG, jsonc.stringify(cfg) .. "\n")
	end)
	if not ok then
		return nil, "写入失败: " .. tostring(err)
	end
	sys.call("chmod 600 " .. CONFIG)
	return cfg
end

function action_page()
	render({ msg = http.formvalue("msg") })
end

function action_save()
	local cfg, err = persist_form()
	if not cfg then
		render({ errtext = err })
		return
	end
	-- 代理只在启动时读取配置，运行中保存后重启使其生效
	if is_running() then
		sys.exec(INIT .. " restart >/dev/null 2>&1")
	end
	back("saved")
end

-- 用配对码向主机换取 deviceId/token，写回配置后重启代理建立连接
function action_pair()
	local cfg, err = persist_form()
	if not cfg then
		render({ errtext = err })
		return
	end

	local code = trim(http.formvalue("pairCode"))
	if not code:match("^%d%d%d%d%d%d$") then
		render({ errtext = "配对码应为 6 位数字" })
		return
	end

	local out = sys.exec(BIN .. " pair -config " .. CONFIG .. " -code " .. code .. " 2>&1") or ""
	if not out:find("配对成功", 1, true) then
		render({ errtext = "配对失败：" .. trim(out) })
		return
	end

	sys.exec(INIT .. " restart >/dev/null 2>&1")
	back("paired")
end

function action_test()
	local cfg, err = persist_form()
	if not cfg then
		render({ errtext = err })
		return
	end

	local out = sys.exec(BIN .. " selftest -config " .. CONFIG .. " 2>&1") or ""
	if not out:find("已发送", 1, true) then
		render({ errtext = "测试打印失败：" .. trim(out) })
		return
	end
	back("tested")
end

function action_service()
	local action = http.formvalue("action")
	local allowed = {
		start = true, stop = true, restart = true,
		enable = true, disable = true
	}

	if not allowed[action] then
		back("unknown")
		return
	end

	sys.exec(INIT .. " " .. action .. " >/dev/null 2>&1")
	back("service")
end
