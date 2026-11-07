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

local function render(opts)
	local cfg, raw = read_config()
	opts.config = opts.config or cfg
	opts.raw = opts.raw or raw
	opts.running = is_running()
	opts.enabled = is_enabled()
	opts.version = trim(sys.exec(BIN .. " version 2>/dev/null"))
	opts.token = disp.context.authtoken
	tpl.render("erp_print_agent/index", opts)
end

local function back(msg)
	http.redirect(disp.build_url("admin", "services", "erp_print_agent") .. "?msg=" .. msg)
end

function action_page()
	render({ msg = http.formvalue("msg") })
end

function action_save()
	local custom = trim(http.formvalue("rawjson"))
	local cfg

	if custom ~= "" then
		local ok, obj = pcall(jsonc.parse, custom)
		if not ok or type(obj) ~= "table" then
			render({ error = "JSON 语法错误，未保存", raw = custom })
			return
		end
		cfg = obj
	else
		cfg, _ = read_config()
		cfg = cfg or {}

		cfg.deviceId = trim(http.formvalue("deviceId"))
		cfg.token = trim(http.formvalue("deviceToken"))

		cfg.server = cfg.server or {}
		cfg.server.url = trim(http.formvalue("serverUrl"))
		cfg.server.serverName = trim(http.formvalue("serverName"))
		cfg.server.insecureSkipVerify = (http.formvalue("insecureSkipVerify") == "1")

		cfg.log = cfg.log or {}
		cfg.log.level = http.formvalue("logLevel") or cfg.log.level

		cfg.printers = cfg.printers or {}
		if not cfg.printers[1] then
			cfg.printers[1] = { id = "default", name = "标签打印机" }
		end
		local p = cfg.printers[1]
		p.host = trim(http.formvalue("printerHost"))
		p.port = tonumber(http.formvalue("printerPort")) or p.port or 9100
		if p.enabled == nil then
			p.enabled = true
		end
	end

	local ok, err = pcall(function()
		fs.writefile(CONFIG, jsonc.stringify(cfg) .. "\n")
	end)
	if not ok then
		render({ error = "写入失败: " .. tostring(err), config = cfg })
		return
	end

	sys.call("chmod 600 " .. CONFIG)
	back("saved")
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
