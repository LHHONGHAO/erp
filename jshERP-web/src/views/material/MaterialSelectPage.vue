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
    <!-- 商品分类 -->
    <div class="msp-category">
      <span
        v-for="cat in categoryList"
        :key="cat.id"
        class="msp-cat-item"
        :class="{ active: currentCategory === cat.id }"
        @click="selectCategory(cat)"
      >{{ cat.name }}</span>
    </div>
    <!-- 商品列表（预布局，后续完善数据加载） -->
    <div class="msp-list">
      <div v-if="materialList.length === 0" class="msp-empty">
        <a-icon type="inbox" />
        <p>暂无商品数据</p>
      </div>
      <div
        v-for="item in materialList"
        :key="item.id"
        class="msp-item"
        @click="handleSelect(item)"
      >
        <div class="msp-item-img">
          <img v-if="item.imgName" :src="getImgUrl(item.imgName)" />
          <a-icon v-else type="picture" />
        </div>
        <div class="msp-item-info">
          <div class="msp-item-name">{{ item.name }}</div>
          <div class="msp-item-spec">{{ item.standard }} / {{ item.model }}</div>
          <div class="msp-item-price">¥{{ item.commodityDecimal }}</div>
        </div>
        <a-icon type="plus-circle" class="msp-item-add" />
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'MaterialSelectPage',
  props: {
    // 选择模式：single-单选 multi-多选
    mode: {
      type: String,
      default: 'multi'
    }
  },
  data () {
    return {
      showSearch: false,
      searchKeyword: '',
      categoryList: [],
      currentCategory: '',
      materialList: []
    }
  },
  methods: {
    handleBack () {
      this.$emit('back')
    },
    handleSearch () {
      // 后续完善：调用商品查询接口
    },
    selectCategory (cat) {
      this.currentCategory = cat.id
      // 后续完善：按分类加载商品
    },
    handleSelect (item) {
      this.$emit('select', item)
    },
    getImgUrl (imgName) {
      // 后续完善：拼接图片地址
      return imgName
    }
  }
}
</script>

<style lang="less" scoped>
.material-select-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
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
  .msp-item {
    display: flex;
    align-items: center;
    padding: 10px;
    margin-bottom: 8px;
    background: #fff;
    border-radius: 6px;
    cursor: pointer;
    .msp-item-img {
      width: 60px;
      height: 60px;
      margin-right: 10px;
      display: flex;
      align-items: center;
      justify-content: center;
      background: #f5f5f5;
      border-radius: 4px;
      img {
        max-width: 100%;
        max-height: 100%;
      }
    }
    .msp-item-info {
      flex: 1;
      .msp-item-name {
        font-size: 14px;
        font-weight: 500;
      }
      .msp-item-spec {
        font-size: 12px;
        color: #999;
        margin-top: 4px;
      }
      .msp-item-price {
        font-size: 14px;
        color: #ff4d4f;
        margin-top: 4px;
      }
    }
    .msp-item-add {
      font-size: 22px;
      color: #1890ff;
    }
  }
}
</style>
