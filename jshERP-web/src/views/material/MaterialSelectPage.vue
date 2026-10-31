<template>
  <div class="material-select-page">
    <!-- 顶部导航栏 -->
    <div class="msp-header">
      <a-icon type="left" class="msp-back" @click="handleBack" />
      <span class="msp-title">选择商品</span>
      <a-icon type="search" class="msp-search-icon" @click="showSearch = !showSearch" />
    </div>

    <!-- 搜索栏 -->
    <div v-if="showSearch" class="msp-search-bar">
      <a-input
        placeholder="请输入条码、名称、助记码、规格、型号"
        v-model="searchKeyword"
        @pressEnter="handleSearch"
      >
        <a-icon slot="prefix" type="search" />
      </a-input>
    </div>

    <!-- 商品分类（横向滚动） -->
    <div class="msp-category">
      <span
        class="msp-cat-item"
        :class="{ active: !currentCategory }"
        @click="selectCategory(null)"
      >全部</span>
      <span
        v-for="cat in categoryList"
        :key="cat.id"
        class="msp-cat-item"
        :class="{ active: currentCategory === cat.id }"
        @click="selectCategory(cat)"
      >{{ cat.name }}</span>
    </div>

    <!-- 商品卡片列表 -->
    <div class="msp-list" v-loading="loading">
      <div v-if="!loading && materialList.length === 0" class="msp-empty">
        <a-icon type="inbox" />
        <p>暂无商品数据</p>
      </div>

      <div
        v-for="item in materialList"
        :key="item.id + '_' + (item.meId != null ? item.meId : 0)"
        class="msp-card"
      >
        <!-- 左侧商品图片 -->
        <div class="msp-card-img">
          <img v-if="getImgUrl(item)" :src="getImgUrl(item)" />
          <a-icon v-else type="picture" />
        </div>

        <!-- 右侧信息区：商品名 + 规格 并列左对齐，右侧数量选择按钮 -->
        <div class="msp-card-body">
          <div class="msp-card-row">
            <span class="msp-card-name">{{ item.name }}</span>
            <span class="msp-card-spec" v-if="getSpec(item)">{{ getSpec(item) }}</span>
            <span class="msp-card-price" v-if="item.commodityDecimal != null">¥{{ item.commodityDecimal }}</span>
            <!-- 数量选择按钮（规格右侧） -->
            <div class="msp-card-qty" v-if="selectedQty(item) > 0">
              <a-button size="small" icon="minus" @click.stop="changeQty(item, -1)" />
              <span class="msp-qty-num">{{ selectedQty(item) }}</span>
              <a-button size="small" icon="plus" @click.stop="changeQty(item, 1)" />
            </div>
            <a-button
              v-else
              size="small"
              type="primary"
              icon="plus"
              class="msp-card-add"
              @click.stop="changeQty(item, 1)"
            >加入</a-button>
          </div>
          <!-- 辅助信息行：单位、库存、SKU码 -->
          <div class="msp-card-sub" v-if="item.unit || item.currentStock != null || item.skuCode">
            <span v-if="item.unit">单位：{{ item.unit }}</span>
            <span v-if="item.currentStock != null" class="msp-sub-stock">库存：{{ item.currentStock }}</span>
            <span v-if="item.skuCode" class="msp-sub-code">SKU：{{ item.skuCode }}</span>
          </div>
        </div>
      </div>

      <!-- 加载更多 / 分页 -->
      <div class="msp-pagination" v-if="materialList.length > 0">
        <a-button
          v-if="ipagination.current < totalPages"
          type="primary"
          :loading="loading"
          @click="loadMore"
        >加载更多</a-button>
        <span v-else class="msp-no-more">没有更多了</span>
      </div>
    </div>
  </div>
</template>

<script>
  import { getAction } from '@/api/manage'
  import { queryMaterialCategoryTreeList } from '@/api/api'
  import { getRowImgUrl, getSpecDisplay } from '@/views/material/utils/materialDisplay'
  import { getMpListShort, filterObj } from '@/utils/util'
  import Vue from 'vue'

  export default {
    name: 'MaterialSelectPage',
    props: {
      // 选择模式：single-单选 multi-多选
      mode: {
        type: String,
        default: 'multi'
      },
      // 扫码传入的条码，用于筛选
      barCode: {
        type: String,
        default: ''
      }
    },
    data () {
      return {
        showSearch: false,
        searchKeyword: '',
        categoryList: [],
        currentCategory: '',
        materialList: [],
        // 已选商品数量映射：key=商品id_meId, value=数量
        selectedMap: {},
        loading: false,
        ipagination: {
          current: 1,
          pageSize: 20,
          total: 0
        }
      }
    },
    computed: {
      totalPages () {
        return Math.ceil(this.ipagination.total / this.ipagination.pageSize) || 1
      }
    },
    created () {
      this.loadCategories()
      this.searchKeyword = this.barCode || ''
      this.loadData(1)
    },
    methods: {
      // 加载商品分类树
      loadCategories () {
        queryMaterialCategoryTreeList({ id: '' }).then(res => {
          if (res) {
            this.categoryList = res || []
          }
        })
      },
      // 加载商品列表（SKU维度，对齐电脑版选择商品）
      loadData (page) {
        if (page) this.ipagination.current = page
        this.loading = true
        const searchObj = {
          materialParam: this.searchKeyword,
          categoryId: this.currentCategory || undefined,
          mpList: getMpListShort(Vue.ls.get('materialPropertyList'))
        }
        const params = {
          search: JSON.stringify(filterObj(searchObj)),
          currentPage: this.ipagination.current,
          pageSize: this.ipagination.pageSize
        }
        getAction('/material/listSku', params).then(res => {
          if (res && res.code === 200 && res.data) {
            const rows = res.data.rows || []
            if (page === 1) {
              this.materialList = rows
            } else {
              this.materialList = this.materialList.concat(rows)
            }
            this.ipagination.total = res.data.total || 0
          }
        }).finally(() => {
          this.loading = false
        })
      },
      // 加载更多
      loadMore () {
        if (this.ipagination.current < this.totalPages) {
          this.ipagination.current++
          this.loadData()
        }
      },
      // 搜索
      handleSearch () {
        this.loadData(1)
      },
      // 选择分类
      selectCategory (cat) {
        this.currentCategory = cat ? cat.id : ''
        this.loadData(1)
      },
      // 返回
      handleBack () {
        this.$emit('back')
      },
      // 商品图片地址
      getImgUrl (record) {
        return getRowImgUrl(record)
      },
      // 规格显示
      getSpec (record) {
        return getSpecDisplay(record)
      },
      // 生成选中映射的 key
      itemKey (item) {
        return item.id + '_' + (item.meId != null ? item.meId : 0)
      },
      // 获取某商品已选数量
      selectedQty (item) {
        return this.selectedMap[this.itemKey(item)] || 0
      },
      // 修改数量
      changeQty (item, delta) {
        const key = this.itemKey(item)
        const cur = this.selectedMap[key] || 0
        const next = cur + delta
        if (next <= 0) {
          this.$delete(this.selectedMap, key)
        } else {
          this.$set(this.selectedMap, key, next)
        }
        // 单选模式：只允许选一个
        if (this.mode === 'single' && next > 0) {
          this.selectedMap = { [key]: next }
        }
        // 通知父组件选中变化
        this.$emit('select', this.getSelectedItems())
      },
      // 获取所有已选商品（含数量）
      getSelectedItems () {
        const list = []
        Object.keys(this.selectedMap).forEach(key => {
          const qty = this.selectedMap[key]
          const item = this.materialList.find(m => this.itemKey(m) === key)
          if (item) {
            list.push(Object.assign({}, item, { operNumber: qty }))
          }
        })
        return list
      }
    }
  }
</script>

<style lang="less" scoped>
.material-select-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: #f5f5f5;
}
.msp-header {
  display: flex;
  align-items: center;
  height: 44px;
  padding: 0 12px;
  background: #fff;
  border-bottom: 1px solid #eee;
  position: sticky;
  top: 0;
  z-index: 10;
  .msp-back {
    font-size: 20px;
    cursor: pointer;
  }
  .msp-title {
    flex: 1;
    text-align: center;
    font-size: 16px;
    font-weight: 500;
  }
  .msp-search-icon {
    font-size: 20px;
    cursor: pointer;
  }
}
.msp-search-bar {
  padding: 8px 12px;
  background: #fff;
}
.msp-category {
  display: flex;
  overflow-x: auto;
  padding: 8px 12px;
  background: #fff;
  border-bottom: 1px solid #eee;
  .msp-cat-item {
    flex-shrink: 0;
    padding: 4px 12px;
    margin-right: 8px;
    font-size: 13px;
    border-radius: 12px;
    background: #f0f0f0;
    cursor: pointer;
    white-space: nowrap;
    &.active {
      background: #1890ff;
      color: #fff;
    }
  }
}
.msp-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
  .msp-empty {
    text-align: center;
    padding: 60px 0;
    color: #999;
    .anticon {
      font-size: 48px;
    }
    p {
      margin-top: 12px;
    }
  }
}
/* 商品卡片：左图 + 右侧名/规格并列 + 数量按钮 */
.msp-card {
  display: flex;
  align-items: flex-start;
  padding: 10px;
  margin-bottom: 8px;
  background: #fff;
  border-radius: 6px;
  .msp-card-img {
    width: 60px;
    height: 60px;
    margin-right: 10px;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    background: #f5f5f5;
    border-radius: 4px;
    overflow: hidden;
    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
    .anticon {
      font-size: 24px;
      color: #ccc;
    }
  }
  .msp-card-body {
    flex: 1;
    min-width: 0;
  }
  /* 商品名 + 规格 并列左对齐，右侧数量按钮 */
  .msp-card-row {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 6px;
    min-height: 28px;
    .msp-card-name {
      font-size: 14px;
      font-weight: 500;
      max-width: 40%;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .msp-card-spec {
      font-size: 12px;
      color: #666;
      flex-shrink: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .msp-card-price {
      font-size: 13px;
      color: #ff4d4f;
      font-weight: 500;
    }
    .msp-card-qty {
      display: flex;
      align-items: center;
      margin-left: auto;
      .msp-qty-num {
        display: inline-block;
        min-width: 24px;
        text-align: center;
        font-size: 13px;
      }
    }
    .msp-card-add {
      margin-left: auto;
    }
  }
  .msp-card-sub {
    margin-top: 4px;
    font-size: 11px;
    color: #999;
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    .msp-sub-stock {
      color: #52c41a;
    }
    .msp-sub-code {
      color: #bbb;
    }
  }
}
.msp-pagination {
  text-align: center;
  padding: 12px 0;
  .msp-no-more {
    font-size: 13px;
    color: #999;
  }
}
</style>
