package com.jsh.erp.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Service;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.HashPrintRequestAttributeSet;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterJob;

/**
 * 标签打印：枚举本机打印机、按标签布局直接打印到指定打印机
 * 说明：浏览器无法读取系统打印机列表，只能由后端（运行后端的机器）提供
 */
@Service
public class LabelPrinterService {

    private static final String FONT_NAME = "Microsoft YaHei";

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
     * @param items       标签内容，元素含 x/y/fontSize/fontWeight/letterSpacing/lineHeight/text
     */
    public void printLabel(String printerName, int copies, int widthPx, int heightPx, final JSONArray items) throws Exception {
        PrintService target = null;
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, new HashPrintRequestAttributeSet());
        for (PrintService service : services) {
            if (service.getName().equals(printerName)) {
                target = service;
                break;
            }
        }
        if (target == null) {
            throw new Exception("未找到打印机：" + printerName + "，请刷新打印机列表后重试");
        }

        // 纸张尺寸按标签实际尺寸设置（px 96dpi → pt 72dpi）
        double widthPt = widthPx * 72.0 / 96.0;
        double heightPt = heightPx * 72.0 / 96.0;
        Paper paper = new Paper();
        paper.setSize(widthPt, heightPt);
        paper.setImageableArea(0, 0, widthPt, heightPt);
        PageFormat pageFormat = new PageFormat();
        pageFormat.setPaper(paper);
        pageFormat.setOrientation(PageFormat.PORTRAIT);

        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintService(target);
        // 直接矢量绘制文字，避免位图缩放导致的字迹粗糙
        job.setPrintable(new Printable() {
            @Override
            public int print(Graphics graphics, PageFormat format, int pageIndex) {
                if (pageIndex > 0) {
                    return Printable.NO_SUCH_PAGE;
                }
                Graphics2D g2 = (Graphics2D) graphics;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                g2.translate(format.getImageableX(), format.getImageableY());
                g2.scale(72.0 / 96.0, 72.0 / 96.0);
                drawItems(g2, items);
                return Printable.PAGE_EXISTS;
            }
        }, pageFormat);
        job.setCopies(Math.max(1, copies));
        job.print();
    }

    /**
     * 按标签元素坐标绘制文字（坐标为标签像素坐标）
     */
    private void drawItems(Graphics2D g, JSONArray items) {
        if (items == null) {
            return;
        }
        g.setColor(Color.BLACK);
        for (int i = 0; i < items.size(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (item == null) {
                continue;
            }
            String text = item.getString("text");
            if (text == null || text.isEmpty()) {
                continue;
            }
            int fontSize = item.getIntValue("fontSize") > 0 ? item.getIntValue("fontSize") : 14;
            boolean bold = "bold".equalsIgnoreCase(item.getString("fontWeight"));
            Font font = new Font(FONT_NAME, bold ? Font.BOLD : Font.PLAIN, fontSize);
            g.setFont(font);
            FontMetrics metrics = g.getFontMetrics();

            double letterSpacing = item.getDoubleValue("letterSpacing");
            double lineHeight = item.getDoubleValue("lineHeight");
            if (lineHeight <= 0) {
                lineHeight = 1.3;
            }
            double lineStep = fontSize * lineHeight;
            int x = item.getIntValue("x");
            int y = item.getIntValue("y");

            String[] lines = text.split("\n", -1);
            for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
                // 与浏览器 line-height 一致：文字在行盒内垂直居中
                double lineTop = y + lineIndex * lineStep;
                double baseY = lineTop + (lineStep - metrics.getHeight()) / 2.0 + metrics.getAscent();
                drawLine(g, lines[lineIndex], x, baseY, letterSpacing, metrics);
            }
        }
    }

    /**
     * 绘制单行文字，letterSpacing > 0 时逐字绘制实现字间距
     */
    private void drawLine(Graphics2D g, String line, double x, double baseY, double letterSpacing, FontMetrics metrics) {
        if (line == null || line.isEmpty()) {
            return;
        }
        if (letterSpacing <= 0) {
            g.drawString(line, (float) x, (float) baseY);
            return;
        }
        double cursor = x;
        for (int i = 0; i < line.length(); i++) {
            String ch = String.valueOf(line.charAt(i));
            g.drawString(ch, (float) cursor, (float) baseY);
            cursor += metrics.stringWidth(ch) + letterSpacing;
        }
    }
}
