package com.jsh.erp.config;

import com.jsh.erp.utils.PinYinUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 商品助记码补齐器。
 * 历史商品（尤其是手机端/AI添加的商品）助记码为空，导致简拼搜索不到；
 * 启动时按商品名称补齐空助记码，已有助记码不覆盖，重复启动无副作用。
 */
@Component
public class MaterialMnemonicInitializer implements ApplicationRunner {
    private Logger logger = LoggerFactory.getLogger(MaterialMnemonicInitializer.class);

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "select id, name from jsh_material " +
                            "where (mnemonic is null or mnemonic = '') and name is not null and name <> '' " +
                            "and ifnull(delete_flag,'0') != '1'");
            int updated = 0;
            for (Map<String, Object> row : rows) {
                String name = String.valueOf(row.get("name"));
                String mnemonic = PinYinUtil.getFirstLettersLo(name);
                if (mnemonic.isEmpty()) continue;
                jdbcTemplate.update("update jsh_material set mnemonic = ? where id = ?", mnemonic, row.get("id"));
                updated++;
            }
            if (updated > 0) {
                logger.info("商品助记码补齐完成，共更新 {} 条", updated);
            }
        } catch (Exception e) {
            logger.error("商品助记码补齐失败: " + e.getMessage(), e);
        }
    }
}
