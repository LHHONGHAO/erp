-- MySQL dump 10.13  Distrib 8.4.9, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: jsh_erp
-- ------------------------------------------------------
-- Server version	8.4.9

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `jsh_table_column_config`
--

DROP TABLE IF EXISTS `jsh_table_column_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `jsh_table_column_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `table_key` varchar(100) NOT NULL COMMENT '表格场景标识，如 material_select',
  `table_names` varchar(500) DEFAULT NULL COMMENT '关联数据库表，逗号分隔',
  `config_json` text COMMENT '列配置JSON',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id，null表示全局共享',
  `delete_flag` varchar(1) DEFAULT '0' COMMENT '删除标记，0未删除，1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_table_key` (`table_key`)
) ENGINE=InnoDB AUTO_INCREMENT=393 DEFAULT CHARSET=utf8mb3 COMMENT='表格列显示配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `jsh_table_column_config`
--

LOCK TABLES `jsh_table_column_config` WRITE;
/*!40000 ALTER TABLE `jsh_table_column_config` DISABLE KEYS */;
INSERT INTO `jsh_table_column_config` VALUES (1,'material_select','jsh_material,jsh_material_extend','{\"columns\":[\"name\",\"code\",\"imgName\",\"skuCode\",\"specValues\",\"unit\",\"brand\",\"mnemonic\",\"categoryName\",\"weight\",\"mfrs\",\"otherField1\",\"otherField2\",\"otherField3\",\"mBarCode\",\"stock\",\"purchaseDecimal\",\"commodityDecimal\",\"wholesaleDecimal\",\"lowDecimal\"],\"widths\":{\"otherField1\":100,\"code\":133,\"lowDecimal\":80,\"specValues\":426,\"weight\":80,\"mfrs\":120,\"otherField3\":100,\"categoryName\":100,\"otherField2\":100,\"purchaseDecimal\":80,\"imgName\":142,\"unit\":100,\"commodityDecimal\":80,\"wholesaleDecimal\":91,\"name\":69,\"mnemonic\":80,\"mBarCode\":120,\"stock\":80,\"brand\":100,\"skuCode\":217,\"remark\":120},\"titles\":{},\"customColumns\":[]}',NULL,'0'),(5,'bill_retail_out','jsh_depot_head,jsh_depot_item','{\"columns\":[\"organName\",\"number\",\"materialsList\",\"operTimeStr\",\"payType\",\"userName\",\"materialCount\",\"totalPrice\",\"getAmount\",\"backAmount\",\"accountName\",\"remark\",\"status\",\"action\"],\"widths\":{\"materialsList\":300,\"totalPrice\":80,\"accountName\":80,\"backAmount\":50,\"remark\":200,\"userName\":80,\"operTimeStr\":325,\"organName\":120,\"number\":210,\"payType\":80,\"action\":180,\"materialCount\":60,\"getAmount\":80,\"status\":80},\"titles\":{},\"customColumns\":[]}',NULL,'0'),(16,'material_list','','{\"columns\":[\"name\",\"code\",\"imgName\",\"skuCode\",\"specValues\",\"unit\",\"brand\",\"mnemonic\",\"categoryName\",\"weight\",\"mfrs\",\"otherField1\",\"otherField2\",\"otherField3\",\"mBarCode\",\"stock\",\"purchaseDecimal\",\"commodityDecimal\",\"wholesaleDecimal\",\"lowDecimal\",\"action\"],\"widths\":{\"marketPrice\":128,\"code\":153,\"specDisplay\":315,\"remark\":120,\"categoryName\":135,\"purchaseDecimal\":79,\"enabled\":141,\"imgName\":181,\"skuName\":180,\"specJson\":220,\"wholesaleDecimal\":162,\"mnemonic\":141,\"action\":326,\"mBarCode\":134,\"stock\":135,\"brand\":107,\"executiveStandard\":100,\"inspection\":100,\"otherField1\":143,\"skuUpdateTime\":150,\"lowDecimal\":80,\"specValues\":338,\"weight\":114,\"mfrs\":84,\"otherField3\":40,\"otherField2\":100,\"unit\":104,\"commodityDecimal\":114,\"fabric\":83,\"safetyCategory\":100,\"skuCreateTime\":164,\"grade\":100,\"name\":126,\"filler\":87,\"position\":100,\"skuCode\":211},\"titles\":{},\"customColumns\":[]}',NULL,'0'),(18,'bill_retail_out_detail','','{\"columns\":[\"depotId\",\"barCode\",\"name\",\"code\",\"imgName\",\"skuCode\",\"specValues\",\"unit\",\"mnemonic\",\"categoryName\",\"weight\",\"mId\",\"stock\",\"purchaseDecimal\",\"commodityDecimal\",\"wholesaleDecimal\",\"lowDecimal\",\"operNumber\",\"unitPrice\",\"allPrice\",\"remark\"],\"widths\":{\"unitPrice\":70,\"code\":121,\"lowDecimal\":120,\"specValues\":250,\"depotId\":133,\"weight\":120,\"mId\":120,\"remark\":436,\"categoryName\":128,\"purchaseDecimal\":120,\"barCode\":97,\"imgName\":82,\"unit\":95,\"commodityDecimal\":110,\"allPrice\":96,\"operNumber\":78,\"wholesaleDecimal\":120,\"name\":157,\"mnemonic\":180,\"action\":473,\"stock\":251,\"skuCode\":200},\"titles\":{},\"customColumns\":[]}',NULL,'0'),(240,'bill_other_in_detail','','{\"columns\":[\"depotId\",\"barCode\",\"name\",\"standard\",\"stock\",\"unit\",\"operNumber\",\"unitPrice\",\"allPrice\",\"remark\"],\"widths\":{\"standard\":199,\"unitPrice\":199,\"unit\":43,\"allPrice\":199,\"operNumber\":199,\"depotId\":199,\"name\":164,\"action\":87,\"remark\":199,\"stock\":199,\"barCode\":199,\"sku\":402},\"titles\":{},\"customColumns\":[]}',NULL,'0'),(281,'bill_other_in_detail_v2','','{\"columns\":[\"imgName\",\"name\",\"skuCode\",\"categoryName\",\"unit\",\"fabric\",\"specDisplay\",\"filler\",\"purchaseDecimal\",\"commodityDecimal\",\"marketPrice\",\"operNumber\",\"depotId\"],\"widths\":{\"marketPrice\":101,\"specDisplay\":275,\"depotId\":180,\"categoryName\":102,\"purchaseDecimal\":121,\"imgName\":114,\"unit\":75,\"commodityDecimal\":124,\"operNumber\":114,\"fabric\":120,\"name\":118,\"filler\":129,\"action\":102,\"skuCode\":195},\"titles\":{},\"customColumns\":[]}',NULL,'0'),(350,'bill_sale_order_detail','','{\"widths\":{\"barCode\":60,\"name\":31,\"standard\":31,\"stock\":31,\"unit\":31,\"operNumber\":31,\"unitPrice\":31,\"allPrice\":31,\"taxRate\":31,\"taxMoney\":31,\"taxLastMoney\":31,\"remark\":31,\"action\":82},\"columns\":[\"barCode\",\"name\",\"standard\",\"stock\",\"unit\",\"operNumber\",\"unitPrice\",\"allPrice\",\"taxRate\",\"taxMoney\",\"taxLastMoney\",\"remark\"],\"titles\":{},\"customColumns\":[]}',NULL,'0'),(351,'material_stock','','{\"columns\":[\"imgName\",\"name\",\"materialCode\"],\"widths\":{\"currentStockPrice\":94,\"marketPrice\":80,\"depotName\":85,\"specDisplay\":282,\"mfrs\":120,\"materialCode\":228,\"categoryName\":100,\"purchaseDecimal\":80,\"imgName\":183,\"unit\":86,\"fabric\":100,\"name\":260,\"filler\":100,\"currentStock\":106,\"action\":217,\"mnemonic\":80,\"mBarCode\":120,\"brand\":100},\"titles\":{},\"customColumns\":[]}',NULL,'0'),(376,'bill_retail_out_detail_v2','','{\"widths\":{\"imgName\":131,\"name\":124,\"skuCode\":193,\"specDisplay\":242,\"unit\":81,\"commodityDecimal\":95,\"depotId\":161,\"allPrice\":117,\"remark\":136,\"operNumber\":72},\"columns\":[\"imgName\",\"name\",\"skuCode\",\"specDisplay\",\"unit\",\"commodityDecimal\",\"depotId\",\"allPrice\",\"remark\"],\"titles\":{},\"customColumns\":[]}',NULL,'0');
/*!40000 ALTER TABLE `jsh_table_column_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `jsh_material_property`
--

DROP TABLE IF EXISTS `jsh_material_property`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `jsh_material_property` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `native_name` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '原始名称',
  `enabled` bit(1) DEFAULT NULL COMMENT '是否启用',
  `sort` varchar(10) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '排序',
  `another_name` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '别名',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='产品扩展字段表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `jsh_material_property`
--

LOCK TABLES `jsh_material_property` WRITE;
/*!40000 ALTER TABLE `jsh_material_property` DISABLE KEYS */;
INSERT INTO `jsh_material_property` VALUES (4,'扩展4',NULL,NULL,'扩展4',NULL,'0'),(5,'扩展5',NULL,NULL,'扩展5',NULL,'0'),(6,'扩展6',_binary '','2','等级',63,'0'),(7,'扩展7',_binary '\0','3','检验',63,'0'),(8,'规格',_binary '\0','4','规格',63,'0'),(9,'颜色',_binary '\0','6','颜色',63,'0'),(10,'品牌',_binary '\0','5','品牌',63,'0'),(11,'重量',_binary '\0','8','重量',63,'0'),(12,'保质期(天)',_binary '\0','9','保质期(天)',63,'0'),(13,'扩展1',_binary '','11','面料',63,'0'),(14,'扩展2',_binary '','12','安全类别',63,'0'),(15,'扩展3',_binary '','13','执行标准',63,'0'),(16,'仓位货架',_binary '\0','12','仓位货架',63,'0'),(17,'序列号',_binary '\0','13','序列号',63,'0'),(18,'批号',_binary '\0','14','批号',63,'0');
/*!40000 ALTER TABLE `jsh_material_property` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `jsh_function`
--

DROP TABLE IF EXISTS `jsh_function`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `jsh_function` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `number` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '编号',
  `name` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '名称',
  `parent_number` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '上级编号',
  `url` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '链接',
  `component` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '组件',
  `state` bit(1) DEFAULT NULL COMMENT '收缩',
  `sort` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '排序',
  `enabled` bit(1) DEFAULT NULL COMMENT '启用',
  `type` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '类型',
  `push_btn` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '功能按钮',
  `icon` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '图标',
  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `url` (`url`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=263 DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='功能模块表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `jsh_function`
--

LOCK TABLES `jsh_function` WRITE;
/*!40000 ALTER TABLE `jsh_function` DISABLE KEYS */;
INSERT INTO `jsh_function` VALUES (1,'0001','系统管理','0','/system','/layouts/TabLayout',_binary '','0910',_binary '','电脑版','','setting','0'),(13,'000102','角色管理','0001','/system/role','/system/RoleList',_binary '\0','0130',_binary '','电脑版','1','profile','0'),(14,'000103','用户管理','0001','/system/user','/system/UserList',_binary '\0','0140',_binary '','电脑版','1','profile','0'),(15,'000104','日志管理','0001','/system/log','/system/LogList',_binary '\0','0160',_binary '','电脑版','','profile','0'),(16,'000105','菜单管理','0001','/system/function','/system/FunctionList',_binary '\0','0166',_binary '','电脑版','1','profile','0'),(18,'000109','租户管理','0001','/system/tenant','/system/TenantList',_binary '\0','0167',_binary '','电脑版','1','profile','0'),(21,'0101','商品管理','0','/material','/layouts/TabLayout',_binary '\0','0620',_binary '','电脑版',NULL,'shopping','0'),(22,'010101','商品类别','0101','/material/material_category','/material/MaterialCategoryList',_binary '\0','0230',_binary '','电脑版','1','profile','0'),(23,'010102','商品信息','0101','/material/material','/material/MaterialList',_binary '\0','0240',_binary '','电脑版','1,3','profile','0'),(24,'0102','基础资料','0','/systemA','/layouts/TabLayout',_binary '\0','0750',_binary '','电脑版',NULL,'appstore','0'),(25,'01020101','供应商信息','0102','/system/vendor','/system/VendorList',_binary '\0','0260',_binary '','电脑版','1,3','profile','0'),(26,'010202','仓库信息','0102','/system/depot','/system/DepotList',_binary '\0','0270',_binary '','电脑版','1','profile','0'),(31,'010206','经手人管理','0102','/system/person','/system/PersonList',_binary '\0','0284',_binary '','电脑版','1','profile','0'),(32,'0502','采购管理','0','/bill','/layouts/TabLayout',_binary '\0','0330',_binary '','电脑版','','retweet','0'),(33,'050201','采购入库','0502','/bill/purchase_in','/bill/PurchaseInList',_binary '\0','0340',_binary '','电脑版','1,2,3,7','profile','0'),(38,'0603','销售管理','0','/billB','/layouts/TabLayout',_binary '\0','0390',_binary '','电脑版','','shopping-cart','0'),(40,'080107','调拨出库','0801','/bill/allocation_out','/bill/AllocationOutList',_binary '\0','0807',_binary '','电脑版','1,2,3,7','profile','0'),(41,'060303','销售出库','0603','/bill/sale_out','/bill/SaleOutList',_binary '\0','0394',_binary '','电脑版','1,2,3,7','profile','0'),(44,'0704','财务管理','0','/financial','/layouts/TabLayout',_binary '\0','0450',_binary '','电脑版','','money-collect','0'),(59,'030101','进销存统计','0301','/report/in_out_stock_report','/report/InOutStockReport',_binary '\0','0658',_binary '','电脑版','','profile','0'),(194,'010204','收支项目','0102','/system/in_out_item','/system/InOutItemList',_binary '\0','0282',_binary '','电脑版','1','profile','0'),(195,'010205','结算账户','0102','/system/account','/system/AccountList',_binary '\0','0283',_binary '','电脑版','1','profile','0'),(197,'070402','收入单','0704','/financial/item_in','/financial/ItemInList',_binary '\0','0465',_binary '','电脑版','1,2,3,7','profile','0'),(198,'0301','报表查询','0','/report','/layouts/TabLayout',_binary '\0','0570',_binary '','电脑版',NULL,'pie-chart','0'),(199,'050204','采购退货','0502','/bill/purchase_back','/bill/PurchaseBackList',_binary '\0','0345',_binary '','电脑版','1,2,3,7','profile','0'),(200,'060305','销售退货','0603','/bill/sale_back','/bill/SaleBackList',_binary '\0','0396',_binary '','电脑版','1,2,3,7','profile','0'),(201,'080103','其它入库','0801','/bill/other_in','/bill/OtherInList',_binary '\0','0803',_binary '','电脑版','1,2,3,7','profile','0'),(202,'080105','其它出库','0801','/bill/other_out','/bill/OtherOutList',_binary '\0','0805',_binary '','电脑版','1,2,3,7','profile','0'),(203,'070403','支出单','0704','/financial/item_out','/financial/ItemOutList',_binary '\0','0470',_binary '','电脑版','1,2,3,7','profile','0'),(204,'070404','收款单','0704','/financial/money_in','/financial/MoneyInList',_binary '\0','0475',_binary '','电脑版','1,2,3,7','profile','0'),(205,'070405','付款单','0704','/financial/money_out','/financial/MoneyOutList',_binary '\0','0480',_binary '','电脑版','1,2,3,7','profile','0'),(206,'070406','转账单','0704','/financial/giro','/financial/GiroList',_binary '\0','0490',_binary '','电脑版','1,2,3,7','profile','0'),(207,'030102','账户统计','0301','/report/account_report','/report/AccountReport',_binary '\0','0610',_binary '','电脑版','','profile','0'),(208,'030103','采购统计','0301','/report/buy_in_report','/report/BuyInReport',_binary '\0','0620',_binary '','电脑版','','profile','0'),(209,'030104','销售统计','0301','/report/sale_out_report','/report/SaleOutReport',_binary '\0','0630',_binary '','电脑版','','profile','0'),(210,'040102','零售出库','0401','/bill/retail_out','/bill/RetailOutList',_binary '\0','0405',_binary '','电脑版','1,2,3,7','profile','0'),(211,'040104','零售退货','0401','/bill/retail_back','/bill/RetailBackList',_binary '\0','0407',_binary '','电脑版','1,2,3,7','profile','0'),(212,'070407','收预付款','0704','/financial/advance_in','/financial/AdvanceInList',_binary '\0','0495',_binary '','电脑版','1,2,3,7','profile','0'),(217,'01020102','客户信息','0102','/system/customer','/system/CustomerList',_binary '\0','0262',_binary '','电脑版','1,3','profile','0'),(218,'01020103','会员信息','0102','/system/member','/system/MemberList',_binary '\0','0263',_binary '','电脑版','1,3','profile','0'),(225,'0401','零售管理','0','/billC','/layouts/TabLayout',_binary '\0','0101',_binary '','电脑版','','gift','0'),(226,'030106','入库明细','0301','/report/in_detail','/report/InDetail',_binary '\0','0640',_binary '','电脑版','','profile','0'),(227,'030107','出库明细','0301','/report/out_detail','/report/OutDetail',_binary '\0','0645',_binary '','电脑版','','profile','0'),(228,'030108','入库汇总','0301','/report/in_material_count','/report/InMaterialCount',_binary '\0','0650',_binary '','电脑版','','profile','0'),(229,'030109','出库汇总','0301','/report/out_material_count','/report/OutMaterialCount',_binary '\0','0655',_binary '','电脑版','','profile','0'),(232,'080109','组装单','0801','/bill/assemble','/bill/AssembleList',_binary '\0','0809',_binary '','电脑版','1,2,3,7','profile','0'),(233,'080111','拆卸单','0801','/bill/disassemble','/bill/DisassembleList',_binary '\0','0811',_binary '','电脑版','1,2,3,7','profile','0'),(234,'000105','系统配置','0001','/system/system_config','/system/SystemConfigList',_binary '\0','0164',_binary '','电脑版','1','profile','0'),(235,'030110','客户对账','0301','/report/customer_account','/report/CustomerAccount',_binary '\0','0660',_binary '','电脑版','','profile','0'),(236,'000106','商品属性','0001','/material/material_property','/material/MaterialPropertyList',_binary '\0','0163',_binary '','电脑版','1','profile','0'),(237,'030111','供应商对账','0301','/report/vendor_account','/report/VendorAccount',_binary '\0','0665',_binary '','电脑版','','profile','0'),(239,'0801','仓库管理','0','/billD','/layouts/TabLayout',_binary '\0','0420',_binary '','电脑版','','hdd','0'),(241,'050202','采购订单','0502','/bill/purchase_order','/bill/PurchaseOrderList',_binary '\0','0335',_binary '','电脑版','1,2,3,7','profile','0'),(242,'060301','销售订单','0603','/bill/sale_order','/bill/SaleOrderList',_binary '\0','0392',_binary '','电脑版','1,2,3,7','profile','0'),(243,'000108','部门管理','0001','/system/organization','/system/OrganizationList',_binary '','0150',_binary '','电脑版','1','profile','0'),(244,'030112','库存预警','0301','/report/stock_warning_report','/report/StockWarningReport',_binary '\0','0670',_binary '','电脑版','','profile','0'),(245,'000107','插件管理','0001','/system/plugin','/system/PluginList',_binary '\0','0170',_binary '','电脑版','1','profile','0'),(246,'030113','商品库存','0301','/report/material_stock','/report/MaterialStock',_binary '\0','0605',_binary '','电脑版','','profile','0'),(248,'030150','调拨明细','0301','/report/allocation_detail','/report/AllocationDetail',_binary '\0','0646',_binary '','电脑版','','profile','0'),(258,'000112','平台配置','0001','/system/platform_config','/system/PlatformConfigList',_binary '\0','0175',_binary '','电脑版','','profile','0'),(259,'030105','零售统计','0301','/report/retail_out_report','/report/RetailOutReport',_binary '\0','0615',_binary '','电脑版','','profile','0'),(260,'000113','字典管理','0001','/system/dict','/system/DictList',_binary '\0','0172',_binary '','电脑版','','profile','0'),(261,'050203','请购单','0502','/bill/purchase_apply','/bill/PurchaseApplyList',_binary '\0','0330',_binary '','电脑版','1,2,3,7','profile','0'),(262,'000114','列显示配置','0001','/system/tableColumnConfig','/system/TableColumnConfig',_binary '\0','0176',_binary '','电脑版','1','profile','0');
/*!40000 ALTER TABLE `jsh_function` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-30 15:24:33
