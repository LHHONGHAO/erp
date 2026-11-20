<template>
  <a-modal
    title="表单数据"
    :visible="visible"
    :width="'100%'"
    :maskClosable="true"
    :zIndex="3020"
    wrapClassName="lem-form-sheet-wrap"
    @cancel="$emit('cancel')"
  >
    <div class="lem-form-search">
      <a-input :value="keyword" placeholder="搜索字段" allowClear @change="$emit('update:keyword', $event.target.value)" />
    </div>

    <!-- 上：规格选择（品类 → 规格名 → 规格值） -->
    <div class="lem-form-section">
      <div class="lem-form-section-title">规格选择</div>
      <div v-for="cat in categoryGroups" :key="'cat-' + cat.key" class="lem-form-group lem-form-cat" :class="{ 'is-expanded': cat.expanded }">
        <div class="lem-form-group-head lem-form-cat-head" @click="$emit('toggle-category-expand', cat.key)">
          <a-checkbox
            :checked="cat.checked"
            :indeterminate="cat.indeterminate"
            @click.stop
            @change="$emit('toggle-category', cat)"
          />
          <span class="lem-form-group-name">{{ cat.name }}</span>
          <span class="lem-form-group-count">已选 {{ cat.selectedCount }}/{{ cat.count }}</span>
          <a-icon class="lem-form-cat-arrow" :type="cat.expanded ? 'up' : 'down'" />
        </div>
        <template v-if="cat.expanded">
          <div v-for="item in cat.items" :key="item.key" class="lem-form-spec">
            <div class="lem-form-spec-head" @click="$emit('toggle-item', item)">
              <a-checkbox :checked="item.checked" @click.stop @change="$emit('toggle-item', item)" />
              <span class="lem-form-row-label">{{ item.label }}</span>
              <span class="lem-form-group-count">{{ item.selectedCount }}/{{ item.valueCount }}</span>
            </div>
            <div class="lem-form-chips" v-if="item.valueItems.length">
              <span
                v-for="value in item.valueItems"
                :key="value.key"
                class="lem-form-chip"
                :class="{ 'is-on': value.checked }"
                @click="$emit('toggle-key', value.key)"
              >{{ value.label }}</span>
            </div>
          </div>
        </template>
      </div>
      <div v-if="!categoryGroups.length" class="lem-form-section-empty">当前表单没有可选的规格</div>
    </div>

    <!-- 下：数据字段选择（按分组） -->
    <div class="lem-form-section">
      <div class="lem-form-section-title">数据字段选择</div>
      <div v-for="group in fieldGroups" :key="group.name" class="lem-form-group">
        <div class="lem-form-group-head">
          <a-checkbox
            :checked="group.checked"
            :indeterminate="group.indeterminate"
            @change="$emit('toggle-group', group)"
          />
          <span class="lem-form-group-name">{{ group.name }}</span>
          <span class="lem-form-group-count">{{ group.selectedCount }}/{{ group.count }}</span>
        </div>
        <div
          v-for="item in group.items"
          :key="item.key"
          class="lem-form-row"
          @click="$emit('toggle-item', item)"
        >
          <a-checkbox :checked="item.checked" @click.stop @change="$emit('toggle-item', item)" />
          <span class="lem-form-row-label">{{ item.label }}</span>
          <span class="lem-form-row-value">{{ item.valueText }}</span>
        </div>
      </div>
    </div>

    <div v-if="!fieldGroups.length && !categoryGroups.length" class="lem-form-empty">没有匹配的字段</div>
    <template slot="footer">
      <a-button type="primary" block @click="$emit('confirm')">确定</a-button>
    </template>
  </a-modal>
</template>

<script>
  /** 标准「表单数据」数据表：上方规格选择、下方数据字段选择，供各表单复用 */
  export default {
    name: 'LabelFormDataSheet',
    props: {
      visible: { type: Boolean, default: false },
      keyword: { type: String, default: '' },
      categoryGroups: { type: Array, default: function() { return [] } },
      fieldGroups: { type: Array, default: function() { return [] } }
    }
  }
</script>

<style>
.lem-form-sheet-wrap {
  display: flex;
  align-items: flex-end;
}

.lem-form-sheet-wrap .ant-modal {
  position: relative;
  top: auto;
  bottom: 0;
  width: 100% !important;
  max-width: 100%;
  margin: 0;
  padding-bottom: 0;
}

.lem-form-sheet-wrap .ant-modal-content {
  border-radius: 16px 16px 0 0;
  padding-bottom: env(safe-area-inset-bottom);
  overflow: hidden;
}

.lem-form-sheet-wrap .ant-modal-header {
  border-radius: 16px 16px 0 0;
  border-bottom: none;
  padding: 10px 16px 2px;
}

.lem-form-sheet-wrap .ant-modal-title {
  text-align: center;
  font-size: 15px;
}

.lem-form-sheet-wrap .ant-modal-body {
  padding: 4px 16px;
  max-height: 62vh;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

.lem-form-sheet-wrap .ant-modal-footer {
  padding: 8px 16px 10px;
  border-top: 1px solid #f0f0f0;
}

.lem-form-sheet-wrap .ant-modal-footer .ant-btn {
  width: 100%;
  height: 40px;
  border-radius: 20px;
}

.lem-form-search {
  margin-bottom: 8px;
}

.lem-form-section {
  margin-bottom: 14px;
}

.lem-form-section-title {
  padding: 6px 2px 4px;
  font-size: 12px;
  font-weight: 600;
  color: #999;
}

.lem-form-section-empty {
  padding: 8px 2px;
  font-size: 12px;
  color: #bbb;
}

.lem-form-group {
  margin-bottom: 10px;
}

.lem-form-group-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 2px;
  border-bottom: 1px solid #f0f0f0;
}

.lem-form-group-name {
  flex: 1 1 auto;
  font-size: 13px;
  font-weight: 600;
  color: #333;
}

.lem-form-group-count {
  flex: none;
  font-size: 11px;
  color: #999;
}

.lem-form-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 2px;
}

.lem-form-row-label {
  flex: none;
  max-width: 56%;
  font-size: 13px;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lem-form-row-value {
  flex: 1 1 auto;
  min-width: 0;
  font-size: 12px;
  color: #999;
  text-align: right;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lem-form-cat .lem-form-group-name {
  color: #1f6feb;
}

.lem-form-cat-head {
  cursor: pointer;
}

.lem-form-cat-arrow {
  flex: none;
  margin-left: 4px;
  font-size: 12px;
  color: #999;
  transition: transform 0.15s;
}

.lem-form-cat.is-expanded .lem-form-cat-arrow {
  color: #1f6feb;
}

.lem-form-spec {
  padding: 4px 2px 6px;
  border-bottom: 1px dashed #f0f0f0;
}

.lem-form-spec:last-child {
  border-bottom: none;
}

.lem-form-spec-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 2px 0;
}

.lem-form-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 2px 0 0 26px;
}

.lem-form-chip {
  display: inline-flex;
  align-items: center;
  height: 26px;
  padding: 0 10px;
  border: 1px solid #d9d9d9;
  border-radius: 13px;
  font-size: 12px;
  color: #666;
  background: #fff;
}

.lem-form-chip.is-on {
  border-color: #1f6feb;
  color: #1f6feb;
  background: #eaf2ff;
}

.lem-form-empty {
  padding: 24px 0;
  text-align: center;
  font-size: 13px;
  color: #999;
}
</style>
