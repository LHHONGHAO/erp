# -*- coding: utf-8 -*-
"""
本地打印助手（Windows）

作用：
  在用户电脑上常驻一个本地 HTTP 服务，仅供 ERP 网页调用。
  网页把标签图片（base64）发过来，助手用 Windows 打印驱动静默输出到指定打印机。

特点：
  - 只监听 127.0.0.1，外网不可访问
  - 校验 Origin 白名单，防止其他网站偷偷调用打印
  - 自动开机自启（写入 HKCU Run 键）
  - 端口 17890 被占用时自动尝试 17891、17892，并在 /ping 中返回实际端口
"""

import base64
import ctypes
import io
import json
import logging
import os
import re
import socket
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

import win32con
import win32print
import win32ui
from PIL import Image, ImageWin

VERSION = '1.1.0'
BASE_PORT = 17890
MAX_PORT = 17892

# 允许调用本助手的页面来源（按需追加）
ALLOWED_ORIGINS = {
    'https://innas.xyz:9999',
    'http://innas.xyz:9999',
    'https://www.innas.xyz:9999',
    'http://www.innas.xyz:9999',
    'https://192.168.100.1:9999',
    'http://192.168.100.1:9999',
    'http://localhost:9999',
    'http://127.0.0.1:9999',
}

LOG_DIR = os.path.join(os.environ.get('LOCALAPPDATA', os.path.expanduser('~')), 'jshPrintAgent')
os.makedirs(LOG_DIR, exist_ok=True)
logging.basicConfig(
    filename=os.path.join(LOG_DIR, 'agent.log'),
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    encoding='utf-8',
)


def write_log(msg):
    logging.info(msg)


def setup_autostart():
    """写入注册表 Run 键，实现开机自启。失败不影响主流程。"""
    try:
        import winreg
        exe_path = sys.executable if getattr(sys, 'frozen', False) else os.path.abspath(__file__)
        if not getattr(sys, 'frozen', False):
            # 源码运行时不设置自启，避免把 python.exe 写进注册表
            return
        key = winreg.OpenKey(winreg.HKEY_CURRENT_USER,
                             r'Software\Microsoft\Windows\CurrentVersion\Run', 0,
                             winreg.KEY_SET_VALUE)
        winreg.SetValueEx(key, 'JshPrintAgent', 0, winreg.REG_SZ, '"%s"' % exe_path)
        winreg.CloseKey(key)
        write_log('已设置开机自启: %s' % exe_path)
    except Exception as e:
        write_log('设置开机自启失败: %s' % e)


def is_port_busy(port):
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.settimeout(0.5)
    try:
        return s.connect_ex(('127.0.0.1', port)) == 0
    finally:
        s.close()


def probe_self(port):
    """若端口上跑的是本助手的另一个实例，返回 True。"""
    try:
        import urllib.request
        with urllib.request.urlopen('http://127.0.0.1:%d/ping' % port, timeout=1) as resp:
            data = json.loads(resp.read().decode('utf-8'))
            return data.get('app') == 'jsh-print-agent'
    except Exception:
        return False


def list_printers():
    """枚举本机所有打印机。"""
    result = []
    try:
        default_name = ''
        try:
            default_name = win32print.GetDefaultPrinter()
        except Exception:
            default_name = ''
        flags = win32print.PRINTER_ENUM_LOCAL | win32print.PRINTER_ENUM_CONNECTIONS
        items = win32print.EnumPrinters(flags, None, 2)
        for item in items:
            name = item.get('pPrinterName', '')
            if not name:
                continue
            result.append({
                'name': name,
                'isDefault': name == default_name,
                'status': 'available',
            })
    except Exception as e:
        write_log('枚举打印机失败: %s' % e)
    return result


def decode_image(payload):
    """把 base64 / dataURL 转成 PIL Image。"""
    raw = payload or ''
    if raw.startswith('data:'):
        raw = raw.split(',', 1)[-1]
    raw = re.sub(r'\s+', '', raw)
    missing_padding = len(raw) % 4
    if missing_padding:
        raw += '=' * (4 - missing_padding)
    data = base64.b64decode(raw)
    img = Image.open(io.BytesIO(data))
    if img.mode not in ('RGB', 'RGBA'):
        img = img.convert('RGB')
    if img.mode == 'RGBA':
        # 白底合成，避免透明区域打印成黑色
        bg = Image.new('RGB', img.size, (255, 255, 255))
        bg.paste(img, mask=img.split()[3])
        img = bg
    return img


def print_image(printer_name, img, copies=1, width_mm=None, height_mm=None):
    """
    使用 GDI 静默打印图片。

    清晰度优化（适配打印机点阵）：
      1. 读取打印机实际 DPI，按目标毫米尺寸精确换算成打印点数；
      2. 用 LANCZOS 高质量重采样到「1 像素 = 1 打印点」，避免 StretchDIBits 的模糊/锯齿；
      3. 对图像做灰度二值化，消除抗锯齿灰边，让热敏/标签打印机输出锐利的黑白文字与条码；
      4. 以 MM_TEXT 映射 1:1 绘制，每个源像素对应一个物理打印点。
    """
    copies = max(1, int(copies or 1))
    hdc = win32ui.CreateDC()
    try:
        hdc.CreatePrinterDC(printer_name)
    except Exception:
        raise RuntimeError('打印机不存在或不可用: %s' % printer_name)

    # ---- 读取打印机物理参数 ----
    dpi_x = hdc.GetDeviceCaps(win32con.LOGPIXELSX) or 203
    dpi_y = hdc.GetDeviceCaps(win32con.LOGPIXELSY) or dpi_x
    horzres = hdc.GetDeviceCaps(win32con.HORZRES)
    vertres = hdc.GetDeviceCaps(win32con.VERTRES)

    if width_mm and height_mm:
        target_w_mm = float(width_mm)
        target_h_mm = float(height_mm)
    else:
        # 未指定尺寸时铺满可打印区域
        target_w_mm = horzres / dpi_x * 25.4
        target_h_mm = vertres / dpi_y * 25.4

    # 按打印机 DPI 计算目标点数：1 源像素 = 1 打印点
    target_dots_w = max(1, round(target_w_mm / 25.4 * dpi_x))
    target_dots_h = max(1, round(target_h_mm / 25.4 * dpi_y))

    # ---- 高质量重采样到打印机点尺寸 ----
    if img.size != (target_dots_w, target_dots_h):
        img = img.resize((target_dots_w, target_dots_h), Image.LANCZOS)

    # ---- 二值化：消除抗锯齿灰边，让文字/条码在热敏打印机上锐利 ----
    gray = img.convert('L')
    # 阈值 128：灰度 < 128 变黑，否则变白；保留黑色笔画主体，去除灰色边缘
    bw = gray.point(lambda p: 0 if p < 128 else 255, '1')
    final_img = bw.convert('RGB')

    hdc.SetMapMode(win32con.MM_TEXT)  # 1 逻辑单位 = 1 设备像素（点）
    hdc.StartDoc('JSH Label')
    try:
        for _ in range(copies):
            hdc.StartPage()
            dib = ImageWin.Dib(final_img)
            # 1:1 绘制，不做任何拉伸
            dib.draw(hdc.GetHandleOutput(), (0, 0, target_dots_w, target_dots_h))
            hdc.EndPage()
    finally:
        hdc.EndDoc()
        hdc.DeleteDC()


class AgentHandler(BaseHTTPRequestHandler):
    server_version = 'JshPrintAgent/' + VERSION
    protocol_version = 'HTTP/1.1'

    def log_message(self, fmt, *args):
        write_log('%s - %s' % (self.address_string(), fmt % args))

    # ---------- CORS ----------
    def _origin_allowed(self):
        origin = self.headers.get('Origin', '')
        if not origin:
            return True  # 非浏览器调用（如 curl 自检）
        return origin in ALLOWED_ORIGINS

    def _send_json(self, code, payload, http_status=200):
        origin = self.headers.get('Origin', '')
        body = json.dumps(payload, ensure_ascii=False).encode('utf-8')
        self.send_response(http_status)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(body)))
        if origin and origin in ALLOWED_ORIGINS:
            self.send_header('Access-Control-Allow-Origin', origin)
            self.send_header('Vary', 'Origin')
        self.send_header('Cache-Control', 'no-store')
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self):
        origin = self.headers.get('Origin', '')
        if origin and origin not in ALLOWED_ORIGINS:
            self._send_json(403, {'code': 403, 'message': '来源未被允许'}, 403)
            return
        self.send_response(204)
        if origin:
            self.send_header('Access-Control-Allow-Origin', origin)
            self.send_header('Vary', 'Origin')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        self.send_header('Access-Control-Max-Age', '86400')
        self.send_header('Content-Length', '0')
        self.end_headers()

    # ---------- GET ----------
    def do_GET(self):
        path = urlparse(self.path).path.rstrip('/')
        if not self._origin_allowed():
            self._send_json(403, {'code': 403, 'message': '来源未被允许'}, 403)
            return
        if path == '/ping':
            self._send_json(200, {
                'code': 200,
                'app': 'jsh-print-agent',
                'version': VERSION,
                'port': self.server.server_address[1],
            })
        elif path == '/printers':
            printers = list_printers()
            self._send_json(200, {'code': 200, 'data': printers, 'version': VERSION})
        else:
            self._send_json(404, {'code': 404, 'message': '接口不存在'}, 404)

    # ---------- POST ----------
    def do_POST(self):
        path = urlparse(self.path).path.rstrip('/')
        if not self._origin_allowed():
            self._send_json(403, {'code': 403, 'message': '来源未被允许'}, 403)
            return
        if path != '/print':
            self._send_json(404, {'code': 404, 'message': '接口不存在'}, 404)
            return
        try:
            length = int(self.headers.get('Content-Length') or 0)
            payload = json.loads(self.rfile.read(length).decode('utf-8') or '{}')
        except Exception as e:
            self._send_json(400, {'code': 400, 'message': '请求体解析失败: %s' % e}, 400)
            return

        printer_name = (payload.get('printer') or '').strip()
        image_data = payload.get('image') or payload.get('imageBase64') or ''
        copies = payload.get('copies') or 1
        width_mm = payload.get('widthMm')
        height_mm = payload.get('heightMm')

        if not printer_name:
            self._send_json(400, {'code': 400, 'message': '未指定打印机'}, 400)
            return
        if not image_data:
            self._send_json(400, {'code': 400, 'message': '缺少打印图片数据'}, 400)
            return

        try:
            img = decode_image(image_data)
            print_image(printer_name, img, copies, width_mm, height_mm)
            write_log('打印成功 printer=%s copies=%s size=%s' % (printer_name, copies, img.size))
            self._send_json(200, {'code': 200, 'message': '已发送到打印机 %s' % printer_name})
        except Exception as e:
            write_log('打印失败: %s' % e)
            self._send_json(500, {'code': 500, 'message': '打印失败: %s' % e}, 500)


def find_free_port():
    for port in range(BASE_PORT, MAX_PORT + 1):
        if is_port_busy(port):
            if probe_self(port):
                # 已有实例在跑，直接退出
                write_log('检测到已有打印助手在 %d 端口运行，本实例退出' % port)
                sys.exit(0)
            continue
        return port
    raise RuntimeError('端口 %d-%d 均被占用' % (BASE_PORT, MAX_PORT))


def main():
    setup_autostart()
    port = find_free_port()
    server = ThreadingHTTPServer(('127.0.0.1', port), AgentHandler)
    write_log('打印助手已启动 v%s, 监听 127.0.0.1:%d' % (VERSION, port))
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == '__main__':
    main()
