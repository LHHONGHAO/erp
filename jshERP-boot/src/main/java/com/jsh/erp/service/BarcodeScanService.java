package com.jsh.erp.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.GenericMultipleBarcodeReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;

/**
 * 服务端条码/二维码识别：接收前端上传的视频帧/照片（base64 JPEG），
 * 用 ZXing 多码阅读器一次返回画面内全部条码及其位置
 */
@Service
public class BarcodeScanService {
    private static final Logger logger = LoggerFactory.getLogger(BarcodeScanService.class);

    /**
     * 识别 base64 编码的 JPEG/PNG 帧
     * @param base64Image base64 数据（可带 data:image/jpeg;base64, 前缀）
     * @return JSONArray，元素：{code, x, y, w, h}；未识别到返回空数组
     */
    public JSONArray decodeFrame(String base64Image) {
        JSONArray out = new JSONArray();
        if (base64Image == null || base64Image.isEmpty()) {
            return out;
        }
        String data = base64Image;
        int comma = data.indexOf(',');
        if (comma >= 0) {
            data = data.substring(comma + 1);
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(data);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                return out;
            }
            // 手机照片通常 3000px 以上：先 1280 快速识别，失败再降到 2560 或直接用原图，
            // 保留条码细节；缩放必须用双线性插值，最邻近插值会把细条“削断”导致无法识别
            int origW = image.getWidth();
            int maxSide = Math.max(origW, image.getHeight());
            java.util.List<BufferedImage> bases = new java.util.ArrayList<>();
            if (maxSide > 1280) {
                bases.add(scaleDown(image, 1280));
                bases.add(maxSide > 2560 ? scaleDown(image, 2560) : image);
            } else {
                bases.add(image);
            }
            java.util.List<ScanAttempt> attempts = new java.util.ArrayList<>();
            // 第一轮：整图识别（画面内条码较大时常一次成功）
            for (BufferedImage base : bases) {
                attempts.add(new ScanAttempt(base, base.getWidth() / (double) origW, 0, 0));
            }
            // 第二轮：条码在整张照片里占比很小时整图会识别不到，截取局部区域再试
            for (BufferedImage base : bases) {
                double ratio = base.getWidth() / (double) origW;
                attempts.add(cropAttempt(base, ratio, 0.10, 0.00, 0.90, 0.42));
                attempts.add(cropAttempt(base, ratio, 0.10, 0.28, 0.90, 0.72));
                attempts.add(cropAttempt(base, ratio, 0.10, 0.58, 0.90, 1.00));
                attempts.add(cropAttempt(base, ratio, 0.15, 0.25, 0.85, 0.80));
            }
            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            hints.put(DecodeHintType.POSSIBLE_FORMATS, java.util.Arrays.asList(
                    BarcodeFormat.CODE_128, BarcodeFormat.CODE_39, BarcodeFormat.CODE_93,
                    BarcodeFormat.CODABAR, BarcodeFormat.ITF, BarcodeFormat.EAN_13,
                    BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E,
                    BarcodeFormat.RSS_14, BarcodeFormat.RSS_EXPANDED,
                    BarcodeFormat.QR_CODE, BarcodeFormat.DATA_MATRIX,
                    BarcodeFormat.PDF_417, BarcodeFormat.AZTEC));
            Result[] results = new Result[0];
            ScanAttempt hit = null;
            for (ScanAttempt attempt : attempts) {
                results = decode(attempt.image, hints);
                if (results.length > 0) {
                    hit = attempt;
                    break;
                }
            }
            if (hit == null) {
                return out;
            }
            for (Result r : results) {
                JSONObject item = new JSONObject();
                item.put("code", r.getText());
                BarcodeFormat barcodeFormat = r.getBarcodeFormat();
                item.put("format", barcodeFormat == null ? "UNKNOWN" : barcodeFormat.name());
                item.put("type", isTwoDimensional(barcodeFormat) ? "2D" : "1D");
                ResultPoint[] pts = r.getResultPoints();
                if (pts != null && pts.length > 0) {
                    float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
                    float maxX = 0, maxY = 0;
                    for (ResultPoint p : pts) {
                        minX = Math.min(minX, p.getX());
                        minY = Math.min(minY, p.getY());
                        maxX = Math.max(maxX, p.getX());
                        maxY = Math.max(maxY, p.getY());
                    }
                    // 一维码两点共线，y 方向外扩便于前端框选
                    float h = Math.max(maxY - minY, hit.image.getHeight() / 12f);
                    // 坐标先补回裁剪偏移，再还原到原图像素（前端按照片原始尺寸绘制框选）
                    item.put("x", (int) ((minX + hit.offsetX) / hit.ratio));
                    item.put("y", (int) ((Math.max(0, (minY + maxY) / 2 - h / 2) + hit.offsetY) / hit.ratio));
                    item.put("w", (int) (Math.max(maxX - minX, hit.image.getWidth() / 10f) / hit.ratio));
                    item.put("h", (int) (h / hit.ratio));
                }
                out.add(item);
            }
        } catch (Exception e) {
            logger.warn("条码识别失败: {}", e.getMessage());
        }
        return out;
    }

    /**
     * 按比例截取局部区域（含条码），并记录相对原图的缩放系数与偏移
     */
    private ScanAttempt cropAttempt(BufferedImage base, double ratio, double x1, double y1, double x2, double y2) {
        int w = base.getWidth();
        int h = base.getHeight();
        int x = (int) (w * x1);
        int y = (int) (h * y1);
        int cw = Math.min(Math.max(1, (int) (w * (x2 - x1))), w - x);
        int ch = Math.min(Math.max(1, (int) (h * (y2 - y1))), h - y);
        return new ScanAttempt(base.getSubimage(x, y, cw, ch), ratio, x, y);
    }

    /** 一次识别尝试：待识别图 + 还原到原图所需的缩放系数（候选图宽/原图宽）和裁剪偏移 */
    private static final class ScanAttempt {
        final BufferedImage image;
        final double ratio;
        final int offsetX;
        final int offsetY;

        ScanAttempt(BufferedImage image, double ratio, int offsetX, int offsetY) {
            this.image = image;
            this.ratio = ratio;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }
    }

    /**
     * 单张图片识别：先用多码阅读器（可返回画面内多个条码），
     * 多码阅读器在“单个小码/轻微透视”场景下容易失效，再退回单码阅读器
     */
    private Result[] decode(BufferedImage image, Map<DecodeHintType, Object> hints) {
        MultiFormatReader multiFormatReader = new MultiFormatReader();
        multiFormatReader.setHints(hints);
        try {
            Result[] results = new GenericMultipleBarcodeReader(multiFormatReader).decodeMultiple(
                    new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image))));
            if (results != null && results.length > 0) {
                return results;
            }
        } catch (NotFoundException ignore) {
            // 继续尝试单码识别
        }
        try {
            MultiFormatReader singleReader = new MultiFormatReader();
            singleReader.setHints(hints);
            Result one = singleReader.decode(
                    new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image))));
            if (one != null) {
                return new Result[]{one};
            }
        } catch (NotFoundException ignore) {
            // 未识别到
        }
        return new Result[0];
    }

    /**
     * 等比缩小到指定最长边，使用双线性插值，避免细条码被最邻近插值削断
     */
    private BufferedImage scaleDown(BufferedImage image, int maxSide) {
        int w = image.getWidth();
        int h = image.getHeight();
        int side = Math.max(w, h);
        if (side <= maxSide) {
            return image;
        }
        double scale = maxSide / (double) side;
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        BufferedImage scaled = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(image, 0, 0, nw, nh, null);
        g.dispose();
        return scaled;
    }

    private boolean isTwoDimensional(BarcodeFormat format) {
        if (format == null) {
            return false;
        }
        switch (format) {
            case QR_CODE:
            case DATA_MATRIX:
            case PDF_417:
            case AZTEC:
            case MAXICODE:
                return true;
            default:
                return false;
        }
    }
}
