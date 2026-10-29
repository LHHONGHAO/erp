package com.jsh.erp.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.Encoder;
import com.google.zxing.qrcode.encoder.QRCode;
import org.springframework.stereotype.Service;

import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import javax.print.attribute.HashPrintRequestAttributeSet;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 标签打印：枚举本机打印机、按标签布局直接打印到指定打印机
 * 说明：浏览器无法读取系统打印机列表，只能由后端（运行后端的机器）提供
 *
 * 输出方式：不栅格化图片，直接把前端传来的坐标/尺寸/内容拼成 TSPL 指令
 * （TEXT / BARCODE / QRCODE），由打印机固件用硬件字库和条码引擎渲染。
 * 目标机型：汉印 D45（TSPL，203dpi，8 点/mm）。
 */
@Service
public class LabelPrinterService {

    /** 打印机分辨率（D45 为 203dpi，即 8 点/mm） */
    private static final int PRINTER_DPI = 203;
    private static final double DOTS_PER_MM = 8.0;
    /** 前端坐标按 96dpi 像素给出，需换算为打印点 */
    private static final double PX_TO_DOT = (double) PRINTER_DPI / 96.0;
    /** 中文点阵字体（24x24），打印机的硬件字库 */
    private static final String TEXT_FONT = "TSS24.BF2";
    /** 中文内容按 GBK 下发，与打印机内置中文字库编码一致 */
    private static final Charset CHARSET = Charset.forName("GBK");

    /**
     * 本机所有打印机（含默认打印机标记）
     */
    public JSONArray listPrinters() {
        JSONArray array = new JSONArray();
        PrintService defaultService = PrintServiceLookup.lookupDefaultPrintService();
        String defaultName = defaultService == null ? "" : defaultService.getName();
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, new HashPrintRequestAttributeSet());
        for (PrintService service : services) {
            JSONObject item = new JSONObject();
            item.put("name", service.getName());
            item.put("isDefault", service.getName().equals(defaultName));
            array.add(item);
        }
        return array;
    }

    /**
     * 打印标签
     *
     * @param printerName 打印机名称
     * @param copies      打印份数
     * @param widthPx     标签宽（px，96dpi）
     * @param heightPx    标签高（px，96dpi）
     * @param items       标签内容，元素含 x/y/width/height/fontSize/lineHeight/text/codeType/codeText
     */
    public void printLabel(String printerName, int copies, int widthPx, int heightPx, JSONArray items) throws Exception {
        PrintService target = findPrinter(printerName);
        sendRaw(target, buildTsplJob(widthPx, heightPx, items, copies));
    }

    private PrintService findPrinter(String printerName) throws Exception {
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, new HashPrintRequestAttributeSet());
        for (PrintService service : services) {
            if (service.getName().equals(printerName)) {
                return service;
            }
        }
        throw new Exception("未找到打印机：" + printerName + "，请刷新打印机列表后重试");
    }

    /**
     * 拼装 TSPL 打印任务：标签尺寸 + 逐元素指令
     */
    private byte[] buildTsplJob(int widthPx, int heightPx, JSONArray items, int copies) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeLine(out, "SIZE " + toMm(toDots(widthPx)) + " mm," + toMm(toDots(heightPx)) + " mm");
        writeLine(out, "CLS");
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                JSONObject item = items.getJSONObject(i);
                if (item == null) {
                    continue;
                }
                String codeType = item.getString("codeType");
                if ("barcode".equals(codeType)) {
                    appendBarcode(out, item);
                } else if ("qrcode".equals(codeType)) {
                    appendQrcode(out, item);
                } else {
                    appendText(out, item);
                }
            }
        }
        writeLine(out, "PRINT 1," + Math.max(1, copies));
        return out.toByteArray();
    }

    /**
     * 文字：硬件字库 TEXT 指令，按前端字号换算出字体放大倍数
     */
    private void appendText(ByteArrayOutputStream out, JSONObject item) throws IOException {
        String text = item.getString("text");
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        int x = toDots(item.getIntValue("x"));
        int y = toDots(item.getIntValue("y"));
        int fontSize = item.getIntValue("fontSize") > 0 ? item.getIntValue("fontSize") : 14;
        int scale = fontScale(fontSize);
        double lineHeight = item.getDoubleValue("lineHeight");
        if (lineHeight <= 0) {
            lineHeight = 1.3;
        }
        int lineStep = (int) Math.round(fontSize * lineHeight * PX_TO_DOT);
        String[] lines = text.split("\n", -1);
        for (String line : lines) {
            if (!line.isEmpty()) {
                writeLine(out, "TEXT " + x + "," + y + ",\"" + TEXT_FONT + "\",0," + scale + "," + scale
                        + ",\"" + escape(line) + "\"");
            }
            y += lineStep;
        }
    }

    /**
     * 一维条码：CODE128 走打印机硬件条码引擎，height 为条码高度（点）
     */
    private void appendBarcode(ByteArrayOutputStream out, JSONObject item) throws IOException {
        String content = item.getString("codeText");
        if (content == null || content.trim().isEmpty() || !isAscii(content)) {
            // CODE128 只支持 ASCII，含中文时跳过该条码，避免整张标签打印异常
            return;
        }
        int x = toDots(item.getIntValue("x"));
        int y = toDots(item.getIntValue("y"));
        int height = Math.max(20, toDots(item.getIntValue("height")));
        int narrow = barcodeNarrowWidth(content.trim(), toDots(item.getIntValue("width")));
        // 第 5 个参数为是否打印可读字符，置 0 避免条码下方多出一行属性值
        writeLine(out, "BARCODE " + x + "," + y + ",\"128\"," + height + ",0,0," + narrow + "," + (narrow * 2)
                + ",\"" + escape(content.trim()) + "\"");
    }

    /**
     * 按元素宽度反算条码窄条宽度（点），保证整条条码不超出预览宽度
     * 条码总宽 = 模块数 × 窄条宽度，窄条取整后可能略窄于预览，但不会溢出
     */
    private int barcodeNarrowWidth(String content, int widthDots) {
        try {
            BitMatrix matrix = new Code128Writer().encode(content, BarcodeFormat.CODE_128, 0, 1);
            int modules = matrix.getWidth();
            if (modules > 0 && widthDots > 0) {
                return Math.max(1, Math.min(4, widthDots / modules));
            }
        } catch (Exception e) {
            // 编码失败按最窄条输出，至少保证不越界
        }
        return 1;
    }

    /**
     * 二维码：硬件 QRCODE 指令，纠错等级 M
     * 打印机按「模块数 × 单元宽度」出图，且只能是正方形
     * 取预览宽高中较小的一边作为目标边长，向下取整单元宽度，保证打印结果不超出预览框
     */
    private void appendQrcode(ByteArrayOutputStream out, JSONObject item) throws IOException {
        String content = item.getString("codeText");
        if (content == null || content.trim().isEmpty()) {
            return;
        }
        int x = toDots(item.getIntValue("x"));
        int y = toDots(item.getIntValue("y"));
        int width = toDots(item.getIntValue("width"));
        int height = toDots(item.getIntValue("height"));
        int side = (width > 0 && height > 0) ? Math.min(width, height) : Math.max(width, height);
        int cell = qrCellWidth(content, side);
        writeLine(out, "QRCODE " + x + "," + y + ",M," + cell + ",A,0,\"" + escape(content) + "\"");
    }

    /**
     * 按目标边长反算二维码单元宽度：单元宽度 = 预览边长(点) / 模块数，向下取整防止超出
     */
    private int qrCellWidth(String content, int sideDots) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 0);
            QRCode qr = Encoder.encode(content, ErrorCorrectionLevel.M, hints);
            int modules = qr.getMatrix().getWidth();
            if (modules > 0 && sideDots > 0) {
                return Math.max(1, Math.min(10, sideDots / modules));
            }
        } catch (Exception e) {
            // 内容无法编码时按默认单元宽度输出，不影响其他元素
        }
        return 4;
    }

    /**
     * 字号（96dpi 像素）换算为 24 点阵字体的放大倍数
     */
    private int fontScale(int fontSizePx) {
        int dots = (int) Math.round(fontSizePx * PX_TO_DOT);
        return Math.max(1, Math.min(10, (int) Math.round(dots / 24.0)));
    }

    private int toDots(int px) {
        return (int) Math.round(px * PX_TO_DOT);
    }

    private String toMm(int dots) {
        return String.format(Locale.US, "%.1f", dots / DOTS_PER_MM);
    }

    /**
     * 指令内容转义：反斜杠与双引号，去掉换行避免截断指令
     */
    private String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", " ");
    }

    private boolean isAscii(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) > 127) {
                return false;
            }
        }
        return true;
    }

    private void writeLine(ByteArrayOutputStream out, String line) throws IOException {
        out.write((line + "\r\n").getBytes(CHARSET));
    }

    /**
     * 以 RAW 方式把指令字节直接投递到打印队列（绕过驱动排版）
     */
    private void sendRaw(PrintService service, byte[] data) throws Exception {
        DocPrintJob job = service.createPrintJob();
        Doc doc = new SimpleDoc(data, DocFlavor.BYTE_ARRAY.AUTOSENSE, null);
        job.print(doc, null);
    }
}
