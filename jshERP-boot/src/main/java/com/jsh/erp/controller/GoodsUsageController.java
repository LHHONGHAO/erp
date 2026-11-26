package com.jsh.erp.controller;

import com.alibaba.fastjson.JSONObject;
import com.jsh.erp.datasource.entities.User;
import com.jsh.erp.service.GoodsUsageService;
import com.jsh.erp.service.UserService;
import com.jsh.erp.utils.BaseResponseInfo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品最近常选：按店铺保存，店铺内所有用户共享；跨店铺隔离。
 */
@RestController
@RequestMapping(value = "/goodsUsage")
@Api(tags = {"商品最近常选"})
public class GoodsUsageController {

    private Logger logger = LoggerFactory.getLogger(GoodsUsageController.class);

    @Resource
    private GoodsUsageService goodsUsageService;

    @Resource
    private UserService userService;

    @PostMapping(value = "/record")
    @ApiOperation(value = "记录一次商品选择")
    public BaseResponseInfo record(@RequestBody JSONObject obj) {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            goodsUsageService.record(obj);
            res.code = 200;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            res.code = 500;
            res.data = "记录失败";
        }
        return res;
    }

    @GetMapping(value = "/recent")
    @ApiOperation(value = "获取本店铺最近常选商品")
    public BaseResponseInfo recent(@RequestParam(value = "limit", required = false) Integer limit) {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            Map<String, Object> data = new HashMap<>();
            List<Map<String, Object>> rows = goodsUsageService.recent(limit);
            data.put("rows", rows);
            res.code = 200;
            res.data = data;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            res.code = 500;
            res.data = "查询失败";
        }
        return res;
    }

    @DeleteMapping(value = "/clear")
    @ApiOperation(value = "清空本店铺最近常选（仅管理员）")
    public BaseResponseInfo clear() {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            User user = userService.getCurrentUser();
            if (!isShopAdmin(user)) {
                res.code = 403;
                res.data = "仅店铺管理员可清空最近常选";
                return res;
            }
            int count = goodsUsageService.clear();
            res.code = 200;
            res.data = count;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            res.code = 500;
            res.data = "清空失败";
        }
        return res;
    }

    /** 店铺管理员：账号管理员标记、系统内置管理员，或店铺账号本人 */
    private boolean isShopAdmin(User user) {
        if (user == null) return false;
        if ("admin".equals(user.getLoginName())) return true;
        if (user.getIsmanager() != null && user.getIsmanager() == 1) return true;
        return user.getId() != null && user.getId().equals(user.getTenantId());
    }
}
