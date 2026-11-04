package com.jsh.erp.service;

import com.alibaba.fastjson.JSONObject;
import com.jsh.erp.constants.ExceptionConstants;
import com.jsh.erp.datasource.entities.SerialNumber;
import com.jsh.erp.datasource.entities.SerialNumberEx;
import com.jsh.erp.datasource.mappers.SequenceMapperEx;
import com.jsh.erp.exception.BusinessRunTimeException;
import com.jsh.erp.utils.RedisLockUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Description
 *
 * @Author: jishenghua
 * @Date: 2021/3/16 16:33
 */
@Service
public class SequenceService {
    private Logger logger = LoggerFactory.getLogger(SequenceService.class);

    @Resource
    private SequenceMapperEx sequenceMapperEx;

    @Resource
    private RedisLockUtil redisLockUtil;

    private static final long LOCK_EXPIRE_TIME = 3000; // 锁有效期3秒
    private static final long LOCK_WAIT_TIME = 100;    // 等待锁时间100ms

    /**
     * 单据前缀 -> 单号中的两位类型代码（尽量保留可读性，又避免同前缀歧义）
     */
    private static final Map<String, String> PREFIX_SHORT_CODE = new HashMap<>();
    static {
        PREFIX_SHORT_CODE.put("CGDD", "CD"); // 采购订单
        PREFIX_SHORT_CODE.put("CGRK", "CR"); // 采购入库
        PREFIX_SHORT_CODE.put("CGTH", "CT"); // 采购退货
        PREFIX_SHORT_CODE.put("XSDD", "XD"); // 销售订单
        PREFIX_SHORT_CODE.put("XSCK", "XS"); // 销售出库
        PREFIX_SHORT_CODE.put("XSTH", "XT"); // 销售退货
        PREFIX_SHORT_CODE.put("LSCK", "LS"); // 零售出库
        PREFIX_SHORT_CODE.put("LSTH", "LT"); // 零售退货
        PREFIX_SHORT_CODE.put("QTRK", "QR"); // 其它入库
        PREFIX_SHORT_CODE.put("QTCK", "QC"); // 其它出库
        PREFIX_SHORT_CODE.put("DBCK", "DC"); // 调拨出库
        PREFIX_SHORT_CODE.put("DBRK", "DR"); // 调拨入库
        PREFIX_SHORT_CODE.put("ZZD", "ZZ");  // 组装单
        PREFIX_SHORT_CODE.put("CXD", "CX");  // 拆卸单
        PREFIX_SHORT_CODE.put("QGD", "QG");  // 采购申请
        PREFIX_SHORT_CODE.put("SK", "SK");    // 收款单
        PREFIX_SHORT_CODE.put("FK", "FK");    // 付款单
        PREFIX_SHORT_CODE.put("SR", "SR");    // 收入单
        PREFIX_SHORT_CODE.put("ZC", "ZC");    // 支出单
        PREFIX_SHORT_CODE.put("SYF", "SY");   // 预收款单
        PREFIX_SHORT_CODE.put("ZZ", "ZZ");    // 转账单
    }

    public SerialNumber getSequence(long id)throws Exception {
        return null;
    }

    public List<SerialNumberEx> select(String name, Integer offset, Integer rows)throws Exception {
        return null;
    }

    public Long countSequence(String name)throws Exception {
        return null;
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int insertSequence(JSONObject obj, HttpServletRequest request)throws Exception {
        return 0;
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int updateSequence(JSONObject obj, HttpServletRequest request) throws Exception{
        return 0;
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int deleteSequence(Long id, HttpServletRequest request)throws Exception {
        return 0;
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int batchDeleteSequence(String ids, HttpServletRequest request)throws Exception {
        return 0;
    }

    public int checkIsNameExist(Long id, String serialNumber)throws Exception {
        return 0;
    }

    /**
     * 获取唯一单据编号：两位类型代码 + 年月日 + 当日三位流水（如 LS261104001）
     * 每种单据类型、每天独立计数，当天单号连续
     */
    public String buildOnlyNumber(String prefixNo) throws Exception {
        String prefix = (prefixNo == null || prefixNo.trim().isEmpty()) ? "QT" : prefixNo.trim();
        String datePart = new SimpleDateFormat("yyMMdd").format(new Date());
        String seqName = prefix + "_" + datePart;
        String shortCode = PREFIX_SHORT_CODE.containsKey(prefix)
                ? PREFIX_SHORT_CODE.get(prefix)
                : (prefix.length() > 2 ? prefix.substring(0, 2) : prefix);
        String lockKey = "sequence:lock:" + seqName;
        String requestId = UUID.randomUUID().toString(); // 唯一请求ID
        boolean locked = false;
        try {
            // 尝试获取分布式锁
            locked = redisLockUtil.tryLock(
                    lockKey,
                    requestId,
                    LOCK_EXPIRE_TIME,
                    LOCK_WAIT_TIME
            );
            if (!locked) {
                throw new BusinessRunTimeException(ExceptionConstants.SEQUENCE_ONLY_FAILED_CODE, ExceptionConstants.SEQUENCE_ONLY_FAILED_MSG);
            }
            // 执行业务逻辑
            return doGenerateSequence(seqName, shortCode, datePart);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessRunTimeException(ExceptionConstants.SEQUENCE_ONLY_BREAK_CODE, ExceptionConstants.SEQUENCE_ONLY_BREAK_MSG);
        } finally {
            // 释放锁
            if (locked) {
                redisLockUtil.unlock(lockKey, requestId);
            }
        }
    }

    /**
     * 实际生成单据编号
     */
    private String doGenerateSequence(String seqName, String shortCode, String datePart) throws Exception {
        try {
            // 序列不存在时初始化
            sequenceMapperEx.insertSequenceIfAbsent(seqName, "单据编号序列");
            // 执行数据库更新
            sequenceMapperEx.updateBuildOnlyNumberBySeqName(seqName);
            Long number = sequenceMapperEx.getBuildOnlyNumberBySeqName(seqName);
            // 格式化返回
            return formatNumber(number, shortCode, datePart);
        } catch (Exception e) {
            logger.error("生成单据编号失败", e);
            throw new BusinessRunTimeException(ExceptionConstants.SEQUENCE_ONLY_BREAK_CODE, ExceptionConstants.SEQUENCE_ONLY_BREAK_MSG);
        }
    }

    /**
     * 格式化数字：类型代码 + 年月日 + 至少三位流水
     */
    private String formatNumber(Long number, String shortCode, String datePart) {
        StringBuilder sb = new StringBuilder(shortCode).append(datePart);
        String seq = number == null ? "0" : number.toString();
        for (int i = seq.length(); i < 3; i++) {
            sb.append('0');
        }
        sb.append(seq);
        return sb.toString();
    }
}
