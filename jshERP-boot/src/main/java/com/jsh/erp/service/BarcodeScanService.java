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
            // 过大的图片先等比缩小到最长边 1280，显著降低识别耗时
            int maxSide = Math.max(image.getWidth(), image.getHeight());
            if (maxSide > 1280) {
                double scale = 1280.0 / maxSide;
                int w = (int) (image.getWidth() * scale);
                int h = (int) (image.getHeight() * scale);
                BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                scaled.getGraphics().drawImage(image, 0, 0, w, h, null);
                image = scaled;
            }
            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            MultiFormatReader reader = new MultiFormatReader();
            reader.setHints(hints);
            GenericMultipleBarcodeReader multiReader = new GenericMultipleBarcodeReader(reader);
            LuminanceSource source = new BufferedImageLuminanceSource(image);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Result[] results;
            try {
                results = multiReader.decodeMultiple(bitmap);
            } catch (NotFoundException e) {
                // 一帧都没有也算正常返回
                results = new Result[0];
            }
            if (results == null) {
                return out;
            }
            for (Result r : results) {
                JSONObject item = new JSONObject();
                item.put("code", r.getText());
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
                    float h = Math.max(maxY - minY, image.getHeight() / 12f);
                    item.put("x", (int) minX);
                    item.put("y", (int) Math.max(0, (minY + maxY) / 2 - h / 2));
                    item.put("w", (int) Math.max(maxX - minX, image.getWidth() / 10f));
                    item.put("h", (int) h);
                }
                out.add(item);
            }
        } catch (Exception e) {
            logger.warn("条码识别失败: {}", e.getMessage());
        }
        return out;
    }
}
