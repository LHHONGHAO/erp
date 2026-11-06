package com.jsh.erp.label;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据源注册中心：收集所有业务数据适配器，按 provider 标识查找
 */
@Component
public class LabelProviderRegistry {

    @Autowired(required = false)
    private List<LabelDataProvider> providerList;

    private Map<String, LabelDataProvider> providerMap;

    private void init() {
        if (providerMap != null) {
            return;
        }
        providerMap = new LinkedHashMap<>();
        if (providerList != null) {
            for (LabelDataProvider provider : providerList) {
                providerMap.put(provider.getProvider(), provider);
            }
        }
    }

    public LabelDataProvider get(String provider) {
        init();
        return providerMap.get(provider);
    }

    public List<LabelDataProvider> list() {
        init();
        return new ArrayList<>(providerMap.values());
    }

    /** 数据源列表（含字段元数据），供编辑器下拉与字段绑定使用 */
    public JSONArray describe() {
        JSONArray array = new JSONArray();
        for (LabelDataProvider provider : list()) {
            JSONObject item = new JSONObject();
            item.put("provider", provider.getProvider());
            item.put("name", provider.getName());
            item.put("fields", provider.getFields());
            array.add(item);
        }
        return array;
    }
}
