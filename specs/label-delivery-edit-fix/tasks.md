# Tasks：零售出库→发货模板编辑→预览回填 修复

前置：spec.md 已经用户确认后才执行；全程只构建不部署。

## T1 共享快照合并函数
- [x] labelEditSnapshot.js 新增 `mergeEditSnapshotForPrint(templateSnapshot, templateType)`：
      读旧快照，type 匹配时保留 `number/values/rows`，模板结构字段以编辑页为准；type 不匹配或无旧快照按纯模板快照返回。

## T2 编辑页打印链路接入
- [x] LabelEditMobile.vue `handlePrint`（≈3317-3352）：移除 receipt-only rows 特判，统一
      `snapshot = mergeEditSnapshotForPrint(snapshot, this.templateType)` 后写快照；
      receipt 的 `record.goodsRows` 仅作无旧快照时的兜底保留。

## T3 delivery 元素面板去「数据表」下拉
- [x] LabelEditMobile.vue `initProvider`（≈1797-1815）：`templateType==='delivery'` 直接跳过
      `fetchProviders`；provider 按 `context.template.provider || 'sales.delivery'` 兜底（存储兼容，不进 UI）。

## T4 harness 回归
- [x] D:\erp\tools\label-repro 增加 run-merge.js（4 用例：保留业务字段/结构覆盖/type 不匹配/端到端）全部通过；既有 run.js 回归通过（重叠对：无）。

## T5 构建与交付
- [x] `npm run build:pc`（node16）构建成功，产物 dist/js/app.0d41410e.js（2026-11-20 18:36）；未部署。
- [x] 用户确认「部署」后执行：本机 overlay（frontend-dist 入口=app.0d41410e.js，旧资源/mobile 保留）+ NAS deploy-nas.ps1 -SkipBuild（SYNC_OK 20261120-184704，SERVED_MD5 一致，APP_JS_OK）。
