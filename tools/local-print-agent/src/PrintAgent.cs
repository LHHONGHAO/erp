/*
 * JSH 通用打印组件（Windows 客户端）
 *
 * 作用：网页脚本无法直接调用本机打印机，本组件在用户电脑常驻，提供本机 HTTP 接口：
 *   GET  /            浏览器打开可查看运行状态与已安装打印机
 *   GET  /ping        健康检查：{ code:200, app:"jsh-print-agent", version, port, data:"ok" }
 *   GET  /printers    返回本机打印机名称数组
 *   POST /print       传入标签图片（base64）按毫米尺寸直接打印，无系统打印对话框
 *   POST /raw         向打印机下发原始指令（TSPL / ESC-POS / ZPL 等）
 *
 * 特性：单实例、端口顺延（17890/17891/17892）、托盘常驻、开机自启、日志文件、
 *       首次运行自动安装到 %LOCALAPPDATA%\JshPrintAgent。
 *
 * 编译：见同目录 build.ps1（csc /target:winexe）。
 */

using System;
using System.Collections;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Printing;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Windows.Forms;
using Microsoft.Win32;

namespace JshPrintAgent
{
    internal static class Program
    {
        internal const string AppId = "jsh-print-agent";
        internal const string AppName = "JSH 打印组件";
        internal const string Version = "1.0.1";
        internal static readonly int[] DefaultPorts = new int[] { 17890, 17891, 17892 };

        [STAThread]
        private static void Main(string[] args)
        {
            Logger.Init();

            bool portable = Contains(args, "--portable");
            int port = ReadIntArg(args, "--port", 0);

            // 首次运行（在下载目录里双击）：复制自身到安装目录并启动副本
            if (!portable && !SelfInstaller.EnsureInstalled())
            {
                return;
            }

            int[] ports = port > 0 ? new int[] { port } : DefaultPorts;

            bool createdNew = false;
            Mutex mutex = new Mutex(true, "Global\\JshPrintAgent_SingleInstance", out createdNew);
            if (!createdNew)
            {
                // 已有实例：把已有实例的地址展示给用户，避免"点了没反应"
                MessageBox.Show(AppName + " 已经在运行了。\n如无法使用，请在任务栏右下角托盘图标上右键查看状态。",
                    AppName, MessageBoxButtons.OK, MessageBoxIcon.Information);
                return;
            }

            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            try
            {
                Application.Run(new AgentContext(ports));
            }
            finally
            {
                try { mutex.ReleaseMutex(); } catch (Exception) { }
            }
        }

        private static bool Contains(string[] args, string value)
        {
            for (int i = 0; i < args.Length; i++)
            {
                if (args[i] == value) return true;
            }
            return false;
        }

        private static int ReadIntArg(string[] args, string name, int fallback)
        {
            for (int i = 0; i < args.Length - 1; i++)
            {
                if (args[i] == name)
                {
                    int v = 0;
                    if (int.TryParse(args[i + 1], out v)) return v;
                }
            }
            return fallback;
        }
    }

    /// <summary>托盘 + 服务生命周期</summary>
    internal sealed class AgentContext : ApplicationContext
    {
        private readonly AgentHttpServer _server;
        private readonly NotifyIcon _tray;
        private readonly ToolStripMenuItem _autoStartItem;

        internal AgentContext(int[] ports)
        {
            _server = new AgentHttpServer();
            if (!_server.Start(ports))
            {
                MessageBox.Show("打印组件启动失败：端口 " + string.Join(" / ", Array.ConvertAll(ports, delegate(int p) { return p.ToString(); }))
                    + " 全部被占用。\n请关闭占用这些端口的程序后重启打印组件。",
                    Program.AppName, MessageBoxButtons.OK, MessageBoxIcon.Error);
                ExitThread();
                return;
            }

            Logger.Log(Program.AppName + " 启动，版本 " + Program.Version + "，端口 " + _server.Port);

            ContextMenuStrip menu = new ContextMenuStrip();
            ToolStripMenuItem statusItem = new ToolStripMenuItem("运行中 · 127.0.0.1:" + _server.Port);
            statusItem.Enabled = false;
            menu.Items.Add(statusItem);
            menu.Items.Add(new ToolStripSeparator());

            ToolStripMenuItem openItem = new ToolStripMenuItem("打开状态页面");
            openItem.Click += delegate { OpenBrowser(_server.Url); };
            menu.Items.Add(openItem);

            ToolStripMenuItem copyItem = new ToolStripMenuItem("复制服务地址");
            copyItem.Click += delegate
            {
                try { Clipboard.SetText(_server.Url); } catch (Exception) { }
            };
            menu.Items.Add(copyItem);

            ToolStripMenuItem logItem = new ToolStripMenuItem("打开日志文件");
            logItem.Click += delegate
            {
                try { System.Diagnostics.Process.Start("notepad.exe", Logger.LogPath); } catch (Exception) { }
            };
            menu.Items.Add(logItem);

            _autoStartItem = new ToolStripMenuItem("开机自动启动");
            _autoStartItem.Checked = AutoStart.IsEnabled();
            _autoStartItem.Click += delegate
            {
                AutoStart.SetEnabled(!_autoStartItem.Checked);
                _autoStartItem.Checked = AutoStart.IsEnabled();
            };
            menu.Items.Add(_autoStartItem);
            menu.Items.Add(new ToolStripSeparator());

            ToolStripMenuItem exitItem = new ToolStripMenuItem("退出打印组件");
            exitItem.Click += delegate { Shutdown(); };
            menu.Items.Add(exitItem);

            _tray = new NotifyIcon();
            _tray.Icon = SystemIcons.Application;
            _tray.Text = Program.AppName + " · 端口 " + _server.Port;
            _tray.ContextMenuStrip = menu;
            _tray.Visible = true;
            _tray.DoubleClick += delegate { OpenBrowser(_server.Url); };

            try
            {
                _tray.ShowBalloonTip(3000, Program.AppName,
                    "已启动，服务地址 " + _server.Url + "，双击托盘图标查看状态。",
                    ToolTipIcon.Info);
            }
            catch (Exception) { }
        }

        private static void OpenBrowser(string url)
        {
            try { System.Diagnostics.Process.Start(url); } catch (Exception) { }
        }

        private void Shutdown()
        {
            try { _tray.Visible = false; _tray.Dispose(); } catch (Exception) { }
            try { _server.Dispose(); } catch (Exception) { }
            Logger.Log(Program.AppName + " 已退出");
            ExitThread();
        }
    }

    /// <summary>极简 HTTP 服务（TcpListener 实现，无需管理员权限与 URL ACL）</summary>
    internal sealed class AgentHttpServer : IDisposable
    {
        private TcpListener _listener;
        private Thread _thread;
        private volatile bool _running;

        internal int Port { get; private set; }
        internal string Url
        {
            get { return "http://127.0.0.1:" + Port; }
        }

        internal bool Start(int[] ports)
        {
            for (int i = 0; i < ports.Length; i++)
            {
                int p = ports[i];
                try
                {
                    TcpListener listener = new TcpListener(IPAddress.Loopback, p);
                    listener.Start();
                    _listener = listener;
                    Port = p;
                    Router.SetCurrentPort(p);
                    _running = true;
                    _thread = new Thread(Loop);
                    _thread.IsBackground = true;
                    _thread.Start();
                    return true;
                }
                catch (Exception ex)
                {
                    Logger.Log("端口 " + p + " 绑定失败：" + ex.Message);
                }
            }
            return false;
        }

        private void Loop()
        {
            while (_running)
            {
                TcpClient client = null;
                try
                {
                    client = _listener.AcceptTcpClient();
                }
                catch (Exception)
                {
                    if (_running) Thread.Sleep(50);
                    continue;
                }
                ThreadPool.QueueUserWorkItem(delegate(object state)
                {
                    Handle((TcpClient)state);
                }, client);
            }
        }

        private void Handle(TcpClient client)
        {
            try
            {
                client.ReceiveTimeout = 15000;
                client.SendTimeout = 15000;
                using (NetworkStream ns = client.GetStream())
                {
                    HttpRequest req = HttpRequest.Read(ns);
                    if (req == null) return;
                    HttpResponse res;
                    try
                    {
                        res = Router.Handle(req);
                    }
                    catch (Exception ex)
                    {
                        Logger.Log("处理 " + req.Method + " " + req.RawPath + " 失败：" + ex.Message);
                        res = HttpResponse.Json(500, "打印失败：" + ex.Message);
                    }
                    HttpResponse.Write(ns, res, req.Headers.ContainsKey("Origin") ? req.Headers["Origin"] : null);
                }
            }
            catch (Exception ex)
            {
                Logger.Log("连接处理异常：" + ex.Message);
            }
            finally
            {
                try { client.Close(); } catch (Exception) { }
            }
        }

        public void Dispose()
        {
            _running = false;
            try { if (_listener != null) _listener.Stop(); } catch (Exception) { }
        }
    }

    internal sealed class HttpRequest
    {
        internal string Method = "";
        internal string RawPath = "/";
        internal string Path = "/";
        internal Dictionary<string, string> Headers = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        internal string Body = "";

        internal static HttpRequest Read(NetworkStream ns)
        {
            byte[] buffer = new byte[8192];
            MemoryStream head = new MemoryStream();
            int headerEnd = -1;

            while (headerEnd < 0)
            {
                int n = ns.Read(buffer, 0, buffer.Length);
                if (n <= 0) return null;
                head.Write(buffer, 0, n);
                headerEnd = FindHeaderEnd(head.ToArray());
                if (head.Length > 65536) return null;
            }

            byte[] all = head.ToArray();
            string headText = Encoding.UTF8.GetString(all, 0, headerEnd);
            string[] lines = headText.Split(new string[] { "\r\n" }, StringSplitOptions.None);

            HttpRequest req = new HttpRequest();
            string[] first = lines[0].Split(' ');
            if (first.Length >= 2)
            {
                req.Method = first[0].ToUpperInvariant();
                req.RawPath = first[1];
                int q = req.RawPath.IndexOf('?');
                req.Path = (q >= 0 ? req.RawPath.Substring(0, q) : req.RawPath).ToLowerInvariant();
            }
            for (int i = 1; i < lines.Length; i++)
            {
                int idx = lines[i].IndexOf(':');
                if (idx > 0)
                {
                    req.Headers[lines[i].Substring(0, idx).Trim()] = lines[i].Substring(idx + 1).Trim();
                }
            }

            int contentLength = 0;
            string cl = req.Headers.ContainsKey("Content-Length") ? req.Headers["Content-Length"] : null;
            if (cl != null) int.TryParse(cl, out contentLength);
            if (contentLength < 0) contentLength = 0;

            MemoryStream body = new MemoryStream();
            int bodyStart = headerEnd + 4;
            if (all.Length > bodyStart) body.Write(all, bodyStart, all.Length - bodyStart);
            while ((int)body.Length < contentLength)
            {
                int n = ns.Read(buffer, 0, buffer.Length);
                if (n <= 0) break;
                body.Write(buffer, 0, n);
            }

            byte[] bodyBytes = body.ToArray();
            int take = Math.Min(bodyBytes.Length, contentLength);
            if (take > 0)
            {
                byte[] slice = new byte[take];
                Array.Copy(bodyBytes, 0, slice, 0, take);
                req.Body = Encoding.UTF8.GetString(slice);
            }
            return req;
        }

        private static int FindHeaderEnd(byte[] data)
        {
            for (int i = 0; i + 3 < data.Length; i++)
            {
                if (data[i] == 13 && data[i + 1] == 10 && data[i + 2] == 13 && data[i + 3] == 10)
                {
                    return i;
                }
            }
            return -1;
        }
    }

    internal sealed class HttpResponse
    {
        internal int Status = 200;
        internal string ContentType = "application/json; charset=utf-8";
        internal byte[] Body = new byte[0];

        internal static HttpResponse Json(int status, string message)
        {
            return Json(status, message, null);
        }

        internal static HttpResponse Json(int status, string message, string dataJson)
        {
            StringBuilder sb = new StringBuilder();
            sb.Append("{\"code\":").Append(status);
            sb.Append(",\"app\":\"").Append(Program.AppId).Append("\"");
            sb.Append(",\"version\":\"").Append(Program.Version).Append("\"");
            sb.Append(",\"message\":\"").Append(JsonUtil.Escape(message)).Append("\"");
            if (dataJson != null) sb.Append(",\"data\":").Append(dataJson);
            sb.Append('}');
            HttpResponse res = new HttpResponse();
            res.Status = status;
            res.Body = Encoding.UTF8.GetBytes(sb.ToString());
            return res;
        }

        internal static HttpResponse Html(string html)
        {
            HttpResponse res = new HttpResponse();
            res.Status = 200;
            res.ContentType = "text/html; charset=utf-8";
            res.Body = Encoding.UTF8.GetBytes(html);
            return res;
        }

        internal static HttpResponse Empty()
        {
            HttpResponse res = new HttpResponse();
            res.Status = 204;
            res.ContentType = "text/plain";
            res.Body = new byte[0];
            return res;
        }

        internal static void Write(NetworkStream ns, HttpResponse res, string origin)
        {
            StringBuilder head = new StringBuilder();
            head.Append("HTTP/1.1 ").Append(res.Status).Append(' ').Append(StatusText(res.Status)).Append("\r\n");
            head.Append("Content-Type: ").Append(res.ContentType).Append("\r\n");
            head.Append("Content-Length: ").Append(res.Body.Length).Append("\r\n");
            // Chrome 私有网络访问（PNA）预检要求精确回显 Origin，不能使用通配符
            string allowOrigin = "*";
            if (!string.IsNullOrEmpty(origin) && origin.IndexOf('\r') < 0 && origin.IndexOf('\n') < 0)
            {
                allowOrigin = origin;
            }
            head.Append("Access-Control-Allow-Origin: ").Append(allowOrigin).Append("\r\n");
            head.Append("Vary: Origin\r\n");
            head.Append("Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS\r\n");
            head.Append("Access-Control-Allow-Headers: Content-Type, X-Requested-With, Authorization\r\n");
            head.Append("Access-Control-Allow-Private-Network: true\r\n");
            head.Append("Access-Control-Max-Age: 86400\r\n");
            head.Append("Cache-Control: no-store\r\n");
            head.Append("Connection: close\r\n\r\n");

            byte[] headBytes = Encoding.UTF8.GetBytes(head.ToString());
            ns.Write(headBytes, 0, headBytes.Length);
            if (res.Body.Length > 0) ns.Write(res.Body, 0, res.Body.Length);
            ns.Flush();
        }

        private static string StatusText(int status)
        {
            switch (status)
            {
                case 200: return "OK";
                case 204: return "No Content";
                case 400: return "Bad Request";
                case 404: return "Not Found";
                case 500: return "Internal Server Error";
                default: return "OK";
            }
        }
    }

    internal static class Router
    {
        internal static HttpResponse Handle(HttpRequest req)
        {
            if (req.Method == "OPTIONS") return HttpResponse.Empty();

            if (req.Path == "/" || req.Path == "/index.html" || req.Path == "/status")
            {
                return HttpResponse.Html(StatusPage());
            }
            if (req.Path == "/ping" || req.Path == "/health")
            {
                return HttpResponse.Json(200, "ok", "\"ok\"");
            }
            if (req.Path == "/printers")
            {
                string[] printers;
                try
                {
                    printers = PrinterService.InstalledPrinters();
                }
                catch (Exception ex)
                {
                    return HttpResponse.Json(500, "读取打印机失败：" + ex.Message);
                }
                return HttpResponse.Json(200, "ok", JsonUtil.ToJsonArray(printers));
            }
            if (req.Path == "/print")
            {
                if (req.Method != "POST") return HttpResponse.Json(400, "请使用 POST 请求");
                return HandlePrint(req.Body);
            }
            if (req.Path == "/raw")
            {
                if (req.Method != "POST") return HttpResponse.Json(400, "请使用 POST 请求");
                return HandleRaw(req.Body);
            }
            return HttpResponse.Json(404, "接口不存在：" + req.RawPath);
        }

        private static HttpResponse HandlePrint(string body)
        {
            Dictionary<string, object> obj = JsonUtil.ParseObject(body);
            string printer = JsonUtil.GetString(obj, "printer");
            if (string.IsNullOrEmpty(printer)) printer = JsonUtil.GetString(obj, "printerName");
            string image = JsonUtil.GetString(obj, "image");
            if (string.IsNullOrEmpty(image)) image = JsonUtil.GetString(obj, "imageBase64");

            if (string.IsNullOrEmpty(printer)) return HttpResponse.Json(400, "缺少参数 printer");
            if (string.IsNullOrEmpty(image)) return HttpResponse.Json(400, "缺少参数 image");

            double widthMm = JsonUtil.GetDouble(obj, "widthMm", 0);
            double heightMm = JsonUtil.GetDouble(obj, "heightMm", 0);
            int copies = (int)JsonUtil.GetDouble(obj, "copies", 1);
            if (copies < 1) copies = 1;
            if (copies > 99) copies = 99;

            int ms = PrinterService.PrintImage(printer, image, widthMm, heightMm, copies);
            Logger.Log("打印图片：" + printer + " 份数 " + copies + " 耗时 " + ms + "ms");
            return HttpResponse.Json(200, "已发送到打印机：" + printer, "null");
        }

        private static HttpResponse HandleRaw(string body)
        {
            Dictionary<string, object> obj = JsonUtil.ParseObject(body);
            string printer = JsonUtil.GetString(obj, "printer");
            string data = JsonUtil.GetString(obj, "data");
            string dataBase64 = JsonUtil.GetString(obj, "dataBase64");
            string encoding = JsonUtil.GetString(obj, "encoding");

            if (string.IsNullOrEmpty(printer)) return HttpResponse.Json(400, "缺少参数 printer");
            if (string.IsNullOrEmpty(data) && string.IsNullOrEmpty(dataBase64))
            {
                return HttpResponse.Json(400, "缺少参数 data 或 dataBase64");
            }

            byte[] payload;
            if (!string.IsNullOrEmpty(dataBase64))
            {
                try { payload = Convert.FromBase64String(dataBase64.Trim()); }
                catch (Exception) { return HttpResponse.Json(400, "dataBase64 不是合法的 base64"); }
            }
            else
            {
                Encoding enc = Encoding.UTF8;
                if (!string.IsNullOrEmpty(encoding))
                {
                    try
                    {
                        if (encoding.Equals("gbk", StringComparison.OrdinalIgnoreCase)) enc = Encoding.GetEncoding(936);
                        else if (!encoding.Equals("utf8", StringComparison.OrdinalIgnoreCase) && !encoding.Equals("utf-8", StringComparison.OrdinalIgnoreCase))
                            enc = Encoding.GetEncoding(encoding);
                    }
                    catch (Exception) { enc = Encoding.UTF8; }
                }
                payload = enc.GetBytes(data);
            }

            RawPrinter.SendBytes(printer, payload);
            Logger.Log("发送原始指令：" + printer + " 字节数 " + payload.Length);
            return HttpResponse.Json(200, "已发送到打印机：" + printer, "null");
        }

        private static string StatusPage()
        {
            string[] printers;
            string listHtml;
            try
            {
                printers = PrinterService.InstalledPrinters();
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < printers.Length; i++)
                {
                    sb.Append("<li>").Append(HtmlEscape(printers[i])).Append("</li>");
                }
                if (sb.Length == 0) sb.Append("<li>未检测到打印机</li>");
                listHtml = sb.ToString();
            }
            catch (Exception ex)
            {
                listHtml = "<li>读取失败：" + HtmlEscape(ex.Message) + "</li>";
            }

            return "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>JSH 打印组件</title><style>"
                + "body{font-family:-apple-system,'Segoe UI','Microsoft YaHei',sans-serif;background:#f5f6f8;margin:0;padding:24px;color:#222}"
                + ".card{max-width:560px;margin:0 auto;background:#fff;border-radius:10px;padding:20px 24px;box-shadow:0 2px 12px rgba(0,0,0,.06)}"
                + "h1{font-size:18px;margin:0 0 4px}.sub{color:#888;font-size:13px}"
                + ".ok{display:inline-block;margin-top:12px;padding:2px 10px;border-radius:10px;background:#e8f7ee;color:#16a34a;font-size:12px}"
                + "h2{font-size:14px;margin:20px 0 8px}ul{padding-left:20px;margin:0;line-height:1.9}"
                + "</style></head><body><div class=\"card\">"
                + "<h1>JSH 打印组件</h1>"
                + "<div class=\"sub\">版本 " + Program.Version + " · 服务地址 http://127.0.0.1:" + GetCurrentPort() + "</div>"
                + "<div class=\"ok\">运行中</div>"
                + "<h2>本机打印机</h2><ul>" + listHtml + "</ul>"
                + "<h2>接口</h2><ul>"
                + "<li>GET /ping — 健康检查</li>"
                + "<li>GET /printers — 打印机列表</li>"
                + "<li>POST /print — 打印标签图片</li>"
                + "<li>POST /raw — 下发原始打印指令</li>"
                + "</ul></div></body></html>";
        }

        private static int _currentPort;

        private static int GetCurrentPort()
        {
            return _currentPort;
        }

        internal static void SetCurrentPort(int port)
        {
            _currentPort = port;
        }

        private static string HtmlEscape(string s)
        {
            if (s == null) return "";
            return s.Replace("&", "&amp;").Replace("<", "&lt;").Replace(">", "&gt;");
        }
    }

    internal static class PrinterService
    {
        internal static string[] InstalledPrinters()
        {
            List<string> list = new List<string>();
            string defaultName = null;
            try
            {
                PrinterSettings ps = new PrinterSettings();
                defaultName = ps.PrinterName;
            }
            catch (Exception)
            {
            }
            foreach (string name in PrinterSettings.InstalledPrinters)
            {
                if (string.IsNullOrEmpty(name)) continue;
                if (defaultName != null && string.Equals(name, defaultName, StringComparison.OrdinalIgnoreCase)) continue;
                list.Add(name);
            }
            // 默认打印机排在最前，前端自动选中第一项时即为默认打印机
            if (!string.IsNullOrEmpty(defaultName)) list.Insert(0, defaultName);
            return list.ToArray();
        }

        /// <summary>按毫米尺寸打印一张图片，返回耗时毫秒数</summary>
        internal static int PrintImage(string printer, string imageBase64, double widthMm, double heightMm, int copies)
        {
            long started = DateTime.Now.Ticks;

            byte[] bytes = DecodeBase64Image(imageBase64);
            if (bytes.Length == 0) throw new Exception("打印内容为空");

            double w = widthMm > 0 ? widthMm : 50;
            double h = heightMm > 0 ? heightMm : 30;

            using (MemoryStream ms = new MemoryStream(bytes))
            using (Image img = Image.FromStream(ms))
            {
                for (int i = 0; i < copies; i++)
                {
                    using (PrintDocument doc = new PrintDocument())
                    {
                        doc.PrinterSettings.PrinterName = printer;
                        if (!doc.PrinterSettings.IsValid)
                        {
                            throw new Exception("本机未找到打印机：" + printer);
                        }
                        doc.PrintController = new StandardPrintController();
                        doc.DocumentName = "JSH Label";
                        doc.OriginAtMargins = false;
                        doc.DefaultPageSettings.Margins = new Margins(0, 0, 0, 0);
                        try
                        {
                            doc.DefaultPageSettings.PaperSize = new PaperSize("JSH Label",
                                MmToHundredthInch(w), MmToHundredthInch(h));
                        }
                        catch (Exception)
                        {
                            // 部分驱动不接受自定义纸张，退回驱动默认尺寸
                        }

                        Image source = img;
                        doc.PrintPage += delegate(object sender, PrintPageEventArgs e)
                        {
                            e.Graphics.InterpolationMode = InterpolationMode.HighQualityBicubic;
                            e.Graphics.PixelOffsetMode = PixelOffsetMode.HighQuality;
                            Rectangle bounds = e.PageBounds;
                            e.Graphics.DrawImage(source, bounds);
                        };
                        doc.Print();
                    }
                }
            }

            return (int)((DateTime.Now.Ticks - started) / 10000);
        }

        private static byte[] DecodeBase64Image(string text)
        {
            string s = text.Trim();
            int comma = s.IndexOf(',');
            if (s.StartsWith("data:", StringComparison.OrdinalIgnoreCase) && comma > 0)
            {
                s = s.Substring(comma + 1);
            }
            s = s.Replace("\r", "").Replace("\n", "").Replace(" ", "");
            return Convert.FromBase64String(s);
        }

        private static int MmToHundredthInch(double mm)
        {
            int v = (int)Math.Round(mm * 100.0 / 25.4);
            return v < 1 ? 1 : v;
        }
    }

    /// <summary>原始指令打印（TSPL / ESC-POS / ZPL）</summary>
    internal static class RawPrinter
    {
        [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
        private struct DOC_INFO
        {
            [MarshalAs(UnmanagedType.LPWStr)]
            public string DocName;
            [MarshalAs(UnmanagedType.LPWStr)]
            public string DataType;
            public int Mode;
        }

        [DllImport("winspool.drv", EntryPoint = "OpenPrinterW", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern bool OpenPrinter(string name, out IntPtr handle, IntPtr defaults);

        [DllImport("winspool.drv", EntryPoint = "ClosePrinter", SetLastError = true)]
        private static extern bool ClosePrinter(IntPtr handle);

        [DllImport("winspool.drv", EntryPoint = "StartDocPrinterW", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern bool StartDocPrinter(IntPtr handle, int level, ref DOC_INFO info);

        [DllImport("winspool.drv", EntryPoint = "EndDocPrinter", SetLastError = true)]
        private static extern bool EndDocPrinter(IntPtr handle);

        [DllImport("winspool.drv", EntryPoint = "StartPagePrinter", SetLastError = true)]
        private static extern bool StartPagePrinter(IntPtr handle);

        [DllImport("winspool.drv", EntryPoint = "EndPagePrinter", SetLastError = true)]
        private static extern bool EndPagePrinter(IntPtr handle);

        [DllImport("winspool.drv", EntryPoint = "WritePrinter", SetLastError = true)]
        private static extern bool WritePrinter(IntPtr handle, IntPtr bytes, int count, out int written);

        internal static void SendBytes(string printer, byte[] payload)
        {
            IntPtr handle = IntPtr.Zero;
            if (!OpenPrinter(printer, out handle, IntPtr.Zero) || handle == IntPtr.Zero)
            {
                throw new Exception("本机未找到打印机：" + printer);
            }
            try
            {
                DOC_INFO info = new DOC_INFO();
                info.DocName = "JSH Raw";
                info.DataType = "RAW";
                info.Mode = 0;
                if (!StartDocPrinter(handle, 1, ref info)) throw new Exception("无法创建打印任务");
                try
                {
                    if (!StartPagePrinter(handle)) throw new Exception("无法开始打印页");
                    IntPtr ptr = Marshal.AllocCoTaskMem(payload.Length);
                    try
                    {
                        Marshal.Copy(payload, 0, ptr, payload.Length);
                        int written = 0;
                        if (!WritePrinter(handle, ptr, payload.Length, out written) || written != payload.Length)
                        {
                            throw new Exception("打印指令写入失败（已写入 " + written + " 字节）");
                        }
                    }
                    finally
                    {
                        Marshal.FreeCoTaskMem(ptr);
                        EndPagePrinter(handle);
                    }
                }
                finally
                {
                    EndDocPrinter(handle);
                }
            }
            finally
            {
                ClosePrinter(handle);
            }
        }
    }

    internal static class JsonUtil
    {
        internal static Dictionary<string, object> ParseObject(string text)
        {
            Dictionary<string, object> dict = new Dictionary<string, object>(StringComparer.OrdinalIgnoreCase);
            if (string.IsNullOrEmpty(text)) return dict;

            int i = SkipWs(text, 0);
            if (i >= text.Length || text[i] != '{') return dict;
            i++;

            while (i < text.Length)
            {
                i = SkipWs(text, i);
                if (i >= text.Length) break;
                char c = text[i];
                if (c == '}') break;
                if (c == ',') { i++; continue; }
                if (c != '"')
                {
                    while (i < text.Length && text[i] != ',' && text[i] != '}') i++;
                    continue;
                }
                string key = ReadString(text, ref i);
                i = SkipWs(text, i);
                if (i < text.Length && text[i] == ':') i++;
                i = SkipWs(text, i);
                dict[key] = ReadValue(text, ref i);
            }
            return dict;
        }

        internal static string GetString(Dictionary<string, object> obj, string key)
        {
            if (obj == null || !obj.ContainsKey(key)) return null;
            object v = obj[key];
            if (v == null) return null;
            if (v is string) return (string)v;
            return v.ToString();
        }

        internal static double GetDouble(Dictionary<string, object> obj, string key, double fallback)
        {
            if (obj == null || !obj.ContainsKey(key)) return fallback;
            object v = obj[key];
            if (v is double) return (double)v;
            if (v is string)
            {
                double d = 0;
                if (double.TryParse((string)v, out d)) return d;
            }
            return fallback;
        }

        internal static string ToJsonArray(IList<string> items)
        {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < items.Count; i++)
            {
                if (i > 0) sb.Append(',');
                sb.Append('"').Append(Escape(items[i])).Append('"');
            }
            return sb.Append(']').ToString();
        }

        internal static string Escape(string s)
        {
            if (s == null) return "";
            StringBuilder sb = new StringBuilder(s.Length + 8);
            for (int i = 0; i < s.Length; i++)
            {
                char c = s[i];
                switch (c)
                {
                    case '"': sb.Append("\\\""); break;
                    case '\\': sb.Append("\\\\"); break;
                    case '\n': sb.Append("\\n"); break;
                    case '\r': sb.Append("\\r"); break;
                    case '\t': sb.Append("\\t"); break;
                    default:
                        if (c < 32 || c > 126)
                        {
                            sb.Append("\\u").Append(((int)c).ToString("x4"));
                        }
                        else
                        {
                            sb.Append(c);
                        }
                        break;
                }
            }
            return sb.ToString();
        }

        private static int SkipWs(string s, int i)
        {
            while (i < s.Length && (s[i] == ' ' || s[i] == '\r' || s[i] == '\n' || s[i] == '\t')) i++;
            return i;
        }

        private static string ReadString(string s, ref int i)
        {
            i++;
            StringBuilder sb = new StringBuilder();
            while (i < s.Length)
            {
                char c = s[i];
                if (c == '"') { i++; break; }
                if (c == '\\' && i + 1 < s.Length)
                {
                    char e = s[i + 1];
                    i += 2;
                    switch (e)
                    {
                        case '"': sb.Append('"'); break;
                        case '\\': sb.Append('\\'); break;
                        case '/': sb.Append('/'); break;
                        case 'b': sb.Append('\b'); break;
                        case 'f': sb.Append('\f'); break;
                        case 'n': sb.Append('\n'); break;
                        case 'r': sb.Append('\r'); break;
                        case 't': sb.Append('\t'); break;
                        case 'u':
                            if (i + 4 <= s.Length)
                            {
                                try { sb.Append((char)Convert.ToInt32(s.Substring(i, 4), 16)); } catch (Exception) { }
                                i += 4;
                            }
                            break;
                        default: sb.Append(e); break;
                    }
                    continue;
                }
                sb.Append(c);
                i++;
            }
            return sb.ToString();
        }

        private static object ReadValue(string s, ref int i)
        {
            if (i >= s.Length) return null;
            char c = s[i];
            if (c == '"') return ReadString(s, ref i);
            if (c == '{' || c == '[')
            {
                SkipContainer(s, ref i, c == '{' ? '}' : ']');
                return null;
            }
            int start = i;
            while (i < s.Length && s[i] != ',' && s[i] != '}' && s[i] != ']' && s[i] != ' ')
            {
                i++;
            }
            string raw = s.Substring(start, i - start).Trim();
            if (raw == "true") return true;
            if (raw == "false") return false;
            if (raw == "null" || raw.Length == 0) return null;
            double d = 0;
            if (double.TryParse(raw, out d)) return d;
            return raw;
        }

        private static void SkipContainer(string s, ref int i, char close)
        {
            int depth = 0;
            while (i < s.Length)
            {
                char c = s[i];
                if (c == '"')
                {
                    ReadString(s, ref i);
                    continue;
                }
                if (c == '{' || c == '[') depth++;
                else if (c == '}' || c == ']')
                {
                    depth--;
                    i++;
                    if (depth <= 0) return;
                    continue;
                }
                i++;
            }
        }
    }

    internal static class Logger
    {
        private static readonly object Sync = new object();
        private static string _path;

        internal static string LogPath
        {
            get { return _path; }
        }

        internal static void Init()
        {
            try
            {
                string dir = AppPaths.DataDir;
                if (!Directory.Exists(dir)) Directory.CreateDirectory(dir);
                _path = Path.Combine(dir, "agent.log");
                if (File.Exists(_path) && new FileInfo(_path).Length > 1024 * 1024)
                {
                    try { File.Delete(_path); } catch (Exception) { }
                }
                Log("===== 启动 " + Program.Version + " =====");
            }
            catch (Exception)
            {
                _path = Path.Combine(Path.GetTempPath(), "jsh-print-agent.log");
            }
        }

        internal static void Log(string message)
        {
            try
            {
                lock (Sync)
                {
                    if (_path == null) _path = Path.Combine(Path.GetTempPath(), "jsh-print-agent.log");
                    string line = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss") + "  " + message
                        + Environment.NewLine;
                    File.AppendAllText(_path, line, Encoding.UTF8);
                }
            }
            catch (Exception)
            {
                // 日志失败不影响主流程
            }
        }
    }

    internal static class AppPaths
    {
        internal static string DataDir
        {
            get
            {
                return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                    "JshPrintAgent");
            }
        }

        internal static string InstalledExe
        {
            get { return Path.Combine(DataDir, "print-agent.exe"); }
        }
    }

    internal static class AutoStart
    {
        private const string RunKey = "Software\\Microsoft\\Windows\\CurrentVersion\\Run";
        private const string ValueName = "JshPrintAgent";

        internal static bool IsEnabled()
        {
            try
            {
                using (RegistryKey key = Registry.CurrentUser.OpenSubKey(RunKey, false))
                {
                    if (key == null) return false;
                    object v = key.GetValue(ValueName);
                    return v != null && v.ToString().Length > 0;
                }
            }
            catch (Exception)
            {
                return false;
            }
        }

        internal static void SetEnabled(bool enabled)
        {
            Enable(enabled ? Application.ExecutablePath : null);
        }

        internal static void Enable(string exePath)
        {
            try
            {
                using (RegistryKey key = Registry.CurrentUser.CreateSubKey(RunKey))
                {
                    if (key == null) return;
                    if (string.IsNullOrEmpty(exePath))
                    {
                        key.DeleteValue(ValueName, false);
                    }
                    else
                    {
                        key.SetValue(ValueName, "\"" + exePath + "\"");
                    }
                }
            }
            catch (Exception ex)
            {
                Logger.Log("设置开机自启失败：" + ex.Message);
            }
        }
    }

    internal static class SelfInstaller
    {
        /// <summary>
        /// 复制自身到安装目录并启动副本；已安装则直接返回 true。
        /// 返回 false 表示已启动副本、当前进程应退出。
        /// </summary>
        internal static bool EnsureInstalled()
        {
            string current = Path.GetFullPath(Application.ExecutablePath);
            string target = Path.GetFullPath(AppPaths.InstalledExe);

            if (string.Equals(current, target, StringComparison.OrdinalIgnoreCase))
            {
                AutoStart.Enable(target);
                return true;
            }

            try
            {
                Directory.CreateDirectory(AppPaths.DataDir);
                // 升级场景：旧版本可能正在运行并占用安装目录里的 exe，先结束它
                StopInstalledInstance(target);
                File.Copy(current, target, true);
                AutoStart.Enable(target);
                Logger.Log("已安装到 " + target + "，启动副本");
                System.Diagnostics.Process.Start("\"" + target + "\"");
                return false;
            }
            catch (Exception ex)
            {
                // 安装失败（如只读目录）时，当前目录直接运行，保证可用
                Logger.Log("自动安装失败，改用当前目录运行：" + ex.Message);
                try { AutoStart.Enable(current); } catch (Exception) { }
                return true;
            }
        }

        /// <summary>结束正在运行的已安装实例，便于覆盖升级</summary>
        private static void StopInstalledInstance(string target)
        {
            try
            {
                System.Diagnostics.Process[] processes = System.Diagnostics.Process.GetProcessesByName("print-agent");
                for (int i = 0; i < processes.Length; i++)
                {
                    try
                    {
                        string path = processes[i].MainModule.FileName;
                        if (string.Equals(Path.GetFullPath(path), target, StringComparison.OrdinalIgnoreCase))
                        {
                            processes[i].Kill();
                            processes[i].WaitForExit(3000);
                            Logger.Log("已结束旧实例：" + path);
                        }
                    }
                    catch (Exception)
                    {
                    }
                }
            }
            catch (Exception)
            {
            }
        }
    }
}
