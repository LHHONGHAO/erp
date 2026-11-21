/**
 * 打印标签预览弹层
 *
 * 交互：商品信息页点击「打印标签」→ 本弹层按当前商品所用标签模板的实际尺寸显示预览
 *   - 打印：按模板直接打印（不进入编辑界面）
 *   - 编辑：交给标准复用标签编辑弹层页（LabelEditModalPage）修改模板/样式
 */
<template>
  <div>
  <!-- 手机端：两个全屏页面（打印标签页 / 设备列表页） -->
  <template v-if="isMobile">
    <!-- 页面 1：打印标签预览（标准页面弹层：蓝底顶栏 + 标题左对齐 + 副标题同行 + 右侧关闭，无返回） -->
    <mobile-modal-page
      :visible="visible && !showPrinterSheet"
      :title="mobileTitle"
      :subtitle="mobileSubtitle"
      @close="handleCancel">
      <template #actions>
        <a-icon v-if="template || directEntry" class="mmp-icon" type="edit" title="编辑模板" @click="handleEdit" />
      </template>

      <!-- 主内容 -->
      <div class="lmp-content" :class="{ 'lmp-empty': !template }">
        <template v-if="!template">
          <div v-if="templateLoading" class="lmp-loading">
            <a-spin size="small" />
            <span class="lmp-loading-text">正在读取模板…</span>
          </div>
          <template v-else>
          <a-alert type="info" show-icon :message="emptyMessage">
            <template #description>
              <span v-if="templateLoadError">{{ templateLoadError }}</span>
              <span v-else>可点击右上角编辑图标创建模板</span>
              <a
                v-if="outdatedTemplate"
                class="lmp-empty-link"
                @click="deleteOutdatedTemplate">删除旧模板</a>
              <a
                v-else-if="templateLoadError"
                class="lmp-empty-link"
                @click="reloadRouteTemplate">重新加载</a>
              <a
                v-else-if="type !== 'member' && type !== 'address'"
                class="lmp-empty-link"
                @click="handleEdit">去添加模版</a>
            </template>
          </a-alert>
          <a-button
            v-if="(type === 'member' || type === 'address') && !templateLoadError"
            type="primary"
            size="large"
            class="lmp-empty-create"
            icon="plus"
            @click="handleCreateTemplate">{{ type === 'address' ? '创建地址打印模板' : '创建会员标签模板' }}</a-button>
          </template>
        </template>
        <template v-else>
          <!-- 预览区（取消卡片，标签缩放填满空白） -->
          <div class="lmp-preview-area" ref="previewArea" :class="{ 'is-stack': stackPages }">
            <span v-if="pageCount > 1 && !stackPages" class="lmp-page-badge">{{ previewPage + 1 }} / {{ pageCount }}</span>
            <!-- 多页标签：所有页同页纵向堆叠，上下滑动预览，不再翻页 -->
            <div class="lmp-stack" v-if="stackPages">
              <div v-for="pg in stackPageList" :key="pg.index" class="lmp-stack-slot" :style="stackSlotStyle">
                <div class="lmp-preview lmp-preview-page" :style="stackPageStyle">
                  <label-paper
                    v-if="templateSchema"
                    :schema="pg.schema"
                    :values="pg.values"
                    :rows="pg.rows"
                    :texts="pg.texts"
                    :overflow-mode="overflowMode"
                    :scale="1" />
                  <template v-else>
                    <div
                      v-for="item in renderItems"
                      :key="item.key"
                      class="lp-item"
                      :class="{ 'is-multiline': !item.isCode && item.attr.value != null && String(item.attr.value).indexOf('\n') > -1, 'is-code': item.isCode }"
                      :style="itemStyle(item.key, pg.layout)"
                    >
                      <template v-if="item.isCode">
                        <img v-if="codeImages[item.key]" :src="codeImages[item.key]" class="lp-code-img" :style="codeImgStyle(item.key)" />
                      </template>
                      <template v-else-if="isGoodsTable(item.key)">
                        <div class="lp-goods-table">
                          <div class="lp-goods-row lp-goods-head" :style="{ height: (pg.layout.table ? pg.layout.table.headHeight : 0) + 'px' }">
                            <span v-for="(col, ci) in goodsTableColumns" :key="ci" class="lp-goods-cell"
                                  :style="{ width: getGoodsColumnWidth(ci, pg.layout) + 'px' }">{{ col }}</span>
                          </div>
                          <div class="lp-goods-row" v-for="(row, ri) in (pg.layout.table ? pg.layout.table.rows : [])" :key="ri"
                               :style="{ height: getGoodsRowHeight(ri, pg.layout) + 'px' }">
                            <span v-for="(cell, ci) in row.cells" :key="ci" class="lp-goods-cell"
                                  :style="{ width: getGoodsColumnWidth(ci, pg.layout) + 'px' }">{{ cell }}</span>
                          </div>
                        </div>
                      </template>
                      <template v-else>{{ pageText(pg, item) }}</template>
                    </div>
                  </template>
                </div>
              </div>
            </div>
            <div class="lmp-preview" v-else :style="previewStyle">
              <!-- 新版模板：按 elements 直接渲染（mm 坐标、条码/二维码、明细循环） -->
              <label-paper
                v-if="templateSchema"
                :schema="previewSchema"
                :values="previewValues"
                :rows="pageDetailRows"
                :texts="previewTexts"
                :overflow-mode="overflowMode"
                :scale="1" />
              <template v-else>
                <div
                  v-for="item in renderItems"
                  :key="item.key"
                  class="lp-item"
                  :class="{ 'is-multiline': !item.isCode && item.attr.value != null && String(item.attr.value).indexOf('\n') > -1, 'is-code': item.isCode }"
                  :style="itemStyle(item.key)"
                >
                  <template v-if="item.isCode">
                    <img v-if="codeImages[item.key]" :src="codeImages[item.key]" class="lp-code-img" :style="codeImgStyle(item.key)" />
                  </template>
                  <template v-else-if="isGoodsTable(item.key)">
                    <div class="lp-goods-table">
                      <div class="lp-goods-row lp-goods-head" :style="{ height: (goodsTableData ? goodsTableData.headHeight : 0) + 'px' }">
                        <span v-for="(col, ci) in goodsTableColumns" :key="ci" class="lp-goods-cell"
                              :style="{ width: getGoodsColumnWidth(ci) + 'px' }">{{ col }}</span>
                      </div>
                      <div class="lp-goods-row" v-for="(row, ri) in (goodsTableData ? goodsTableData.rows : [])" :key="ri"
                           :style="{ height: getGoodsRowHeight(ri) + 'px' }">
                        <span v-for="(cell, ci) in row.cells" :key="ci" class="lp-goods-cell"
                              :style="{ width: getGoodsColumnWidth(ci) + 'px' }">{{ cell }}</span>
                      </div>
                    </div>
                  </template>
                  <template v-else>
                    {{ displayText(item) }}
                  </template>
                </div>
              </template>
            </div>
          </div>

          <!-- 超出标签边界提示：只裁切不缩放字号 -->
          <div class="lmp-overflow-tip" v-if="overflowPixels">
            内容超出标签底部 {{ overflowPixels }}px，已按标签边界裁切<template v-if="goodsPageMode === 'none'">；可在设置中开启分页</template>
          </div>

          <!-- 多商品分页：翻页（同页堆叠模式下改为上下滑动） -->
          <div class="lmp-pager" v-if="pageCount > 1 && !stackPages">
            <a-button size="small" icon="left" :disabled="previewPage <= 0" @click="previewPage--">上一页</a-button>
            <span class="lmp-pager-text">第 {{ previewPage + 1 }} / {{ pageCount }} 张</span>
            <a-button size="small" :disabled="previewPage >= pageCount - 1" @click="previewPage++">下一页<a-icon type="right" /></a-button>
          </div>

          <!-- 更多模板：预览下方切换模板 -->
          <div class="lmp-template-more" v-if="templateChoices.length > 1">
            <div class="lmp-template-more-head">
              <span>更多模板</span>
              <span class="lmp-template-more-count">共 {{ templateChoices.length }} 个</span>
            </div>
            <div class="lmp-template-cards">
              <div
                v-for="t in templateChoices"
                :key="t.id"
                class="lmp-template-card"
                :class="{ active: String(currentTemplateId) === String(t.serverId || t.id) }"
                @click="switchTemplate(t)"
              >
                <span class="lmp-template-card-name">{{ t.name }}</span>
                <span class="lmp-template-card-meta">{{ t.type === 'default' ? (t.categoryName || '通用模板') : '普通模板' }}</span>
              </div>
            </div>
          </div>

          <!-- 底部单列纵向控制区 -->
          <div class="lmp-bottom-wrap">
            <!-- 设备状态行：左标签 + 中间设备名居中 + 箭头 -->
            <div class="lmp-control-row" @click="showMobilePrinterSheet">
              <div class="lmp-control-label">设备状态</div>
              <div class="lmp-control-value lmp-control-value-center">
                <template v-if="mobileDeviceLabel">
                  <span class="lmp-device-name" :class="{ 'online': mobileDeviceOnline }">{{ mobileDeviceLabel }}</span>
                </template>
                <template v-else><span class="lmp-device-name offline">设备未连接</span></template>
              </div>
              <a-icon type="right" class="lmp-row-arrow" />
            </div>

            <!-- 打印方向行 -->
            <div class="lmp-control-row">
              <div class="lmp-control-label">打印方向</div>
              <div class="lmp-control-value">
                <div class="lmp-rotation-tabs">
                  <div
                    v-for="r in rotationOptions"
                    :key="r.value"
                    class="lmp-rot-tab"
                    :class="{ active: printRotation === r.value }"
                    @click="printRotation = r.value"
                  >{{ r.label }}</div>
                </div>
              </div>
            </div>

            <!-- 打印份数行 -->
            <div class="lmp-control-row">
              <div class="lmp-control-label">打印份数</div>
              <div class="lmp-control-value">
                <div class="lmp-qty">
                  <span class="lmp-qty-btn" :class="{ disabled: copies <= 1 }" @click="copies = Math.max(1, copies - 1)">
                    <a-icon type="minus" />
                  </span>
                  <span class="lmp-qty-num">{{ copies }} 份</span>
                  <span class="lmp-qty-btn" @click="copies = Math.min(99, copies + 1)">
                    <a-icon type="plus" />
                  </span>
                </div>
              </div>
            </div>
          </div>
        </template>
      </div>

      <!-- 底部打印按钮 -->
      <template #footer>
        <div class="lmp-footer">
          <div v-if="printHint && template" class="lmp-print-hint">{{ printHint }}</div>
          <a-button
            type="primary"
            block
            size="large"
            class="lmp-print-btn"
            :disabled="!template"
            :loading="printing"
            @click="handlePrint"
          >打印</a-button>
        </div>
      </template>
    </mobile-modal-page>

    <!-- 页面 2：设备列表（标准弹层页面） -->
    <mobile-modal-page
      :visible="visible && showPrinterSheet && !wifiAddStep && !printerEditVisible"
      title="设备列表"
      @close="showPrinterSheet = false">
      <div class="lmpl-body">
        <!-- 列表区 -->
        <a-spin :spinning="loadingPrinters || loadingAgents" tip="加载中...">
          <div class="lmpl-list">
          <!-- 云打印：ERP 打印代理设备上的远程打印机 -->
          <template v-if="deviceTab === 'cloud'">
            <template v-if="agentPrinterItems.length > 0">
              <div
                v-for="p in agentPrinterItems"
                :key="p.key"
                class="lmpl-item"
                :class="{ active: printerType === 'backend' && selectedPrinter === p.key }"
                @click="selectCloudAgentPrinter(p)"
              >
                <div class="lmpl-item-left">
                  <a-icon type="printer" class="lmpl-item-icon" />
                  <div class="lmpl-item-text">
                    <div class="lmpl-item-title">{{ p.printerName }}</div>
                    <div class="lmpl-item-sub">
                      {{ p.deviceName }} · {{ p.cmdSet || 'TSPL' }}<template v-if="p.host"> · {{ p.host }}:{{ p.port }}</template>
                    </div>
                  </div>
                </div>
                <span class="lmpl-status" :class="p.online ? 'online' : 'offline'">{{ p.online ? '在线' : '离线' }}</span>
                <a class="lmpl-edit" @click.stop="openPrinterEdit(p)">编辑</a>
                <a-icon v-if="printerType === 'backend' && selectedPrinter === p.key" type="check-circle" theme="filled" class="lmpl-check" />
              </div>
            </template>
            <div v-if="agentPrinterItems.length === 0 && !loadingAgents" class="lmpl-empty">
              <div class="lmpl-empty-icon"><a-icon type="cloud" /></div>
              <div class="lmpl-empty-text">暂无云打印设备</div>
              <div class="lmpl-empty-sub">点击下方「添加云打印打印机」，填入配对码与链接地址完成配对</div>
            </div>
          </template>

          <!-- 网络打印 -->
          <template v-else-if="deviceTab === 'network'">
            <template v-if="wifiPrinters.length > 0">
              <div
                v-for="p in wifiPrinters"
                :key="p.id"
                class="lmpl-item"
                :class="{ active: printerType === 'wifi' && activeWifiId === p.id }"
                @click="selectNetworkPrinter(p)"
              >
                <div class="lmpl-item-left">
                  <a-icon type="wifi" class="lmpl-item-icon" />
                  <div class="lmpl-item-text">
                    <div class="lmpl-item-title">{{ p.name }}</div>
                    <div class="lmpl-item-sub">{{ p.ip }}:{{ p.port }} · {{ p.cmdSet || 'TSPL' }}</div>
                  </div>
                </div>
                <a-tag class="lmpl-tag" @click.stop="openPrinterCmdSheet(p)">{{ p.cmdSet || 'TSPL' }}</a-tag>
                <template v-if="activeWifiId === p.id">
                  <a-tag v-if="wifiStatus[p.id]" color="green" class="lmpl-tag">已连接</a-tag>
                  <a-tag v-else class="lmpl-tag">未连接</a-tag>
                </template>
                <a-icon v-if="printerType === 'wifi' && activeWifiId === p.id" type="check-circle" theme="filled" class="lmpl-check" />
              </div>
            </template>
            <div v-else class="lmpl-empty">
              <div class="lmpl-empty-icon"><a-icon type="wifi" /></div>
              <div class="lmpl-empty-text">未添加网络打印机</div>
              <div class="lmpl-empty-sub">点击下方按钮添加局域网 Wi-Fi 打印机</div>
            </div>
          </template>

          <!-- 本地打印：本机打印机 + 蓝牙打印机 -->
          <template v-else>
            <template v-if="printers.length > 0">
              <div class="lmpl-section-title">本机打印机</div>
              <div
                v-for="p in printers"
                :key="p.name"
                class="lmpl-item"
                :class="{ active: printerType === 'backend' && selectedPrinter === p.name }"
                @click="selectLocalPrinter(p)"
              >
                <div class="lmpl-item-left">
                  <a-icon type="desktop" class="lmpl-item-icon" />
                  <div class="lmpl-item-text">
                    <div class="lmpl-item-title">{{ p.name }}</div>
                    <div v-if="p.isDefault" class="lmpl-item-sub">默认打印机</div>
                  </div>
                </div>
                <a-icon v-if="printerType === 'backend' && selectedPrinter === p.name" type="check-circle" theme="filled" class="lmpl-check" />
              </div>
            </template>
            <template v-if="btDevice">
              <div class="lmpl-section-title">蓝牙打印机</div>
              <div class="lmpl-item" :class="{ active: printerType === 'bluetooth' && selectedPrinter === btDevice.id }" @click="selectBluetoothPrinter">
                <div class="lmpl-item-left">
                  <a-icon type="bluetooth" class="lmpl-item-icon" />
                  <div class="lmpl-item-text">
                    <div class="lmpl-item-title">{{ btDevice.name }}</div>
                    <div class="lmpl-item-sub">已连接</div>
                  </div>
                </div>
                <a-icon v-if="printerType === 'bluetooth' && selectedPrinter === btDevice.id" type="check-circle" theme="filled" class="lmpl-check" />
              </div>
            </template>
            <div v-if="printers.length === 0 && !btDevice" class="lmpl-empty">
              <div class="lmpl-empty-icon"><a-icon type="desktop" /></div>
              <div class="lmpl-empty-text">暂无本地打印机</div>
              <div class="lmpl-empty-sub">可添加本机打印机或搜索蓝牙打印机</div>
            </div>
          </template>
          </div>
        </a-spin>

        <!-- 对应类型的打印机添加入口 -->
        <div class="lmpl-footer-add">
          <a-button
            v-if="deviceTab === 'cloud'"
            type="primary"
            ghost
            block
            icon="plus"
            class="lmpl-add-btn"
            @click="openCloudPrinterManage"
          >添加云打印打印机</a-button>
          <a-button
            v-else-if="deviceTab === 'network'"
            type="primary"
            ghost
            block
            icon="plus"
            class="lmpl-add-btn"
            @click="openAddWifiPrinter"
          >添加网络打印机</a-button>
          <template v-else>
            <a-button type="primary" ghost block icon="plus" class="lmpl-add-btn" @click="handleAddLocalPrinter">添加本地打印机</a-button>
            <a-button
              v-if="printerCaps.bluetooth"
              type="primary"
              ghost
              block
              icon="bluetooth"
              class="lmpl-add-btn"
              :loading="btSearching"
              @click="handleAddBluetooth"
            >添加蓝牙打印机</a-button>
          </template>
        </div>
      </div>

      <!-- 类型切换：底部栏 -->
      <template #footer>
        <div class="lmpl-bottom-tabs">
          <div
            v-for="t in printerTabList"
            :key="t.key"
            class="lmpl-bottom-tab"
            :class="{ active: deviceTab === t.key }"
            @click="switchDeviceTab(t.key)"
          >{{ t.label }}</div>
        </div>
      </template>
    </mobile-modal-page>

    <!-- 云打印：配对配置（配对码 + 链接地址 + 打印机品牌选择） -->
    <mobile-modal-page
      :visible="visible && cloudAddVisible"
      title="云打印配置"
      :subtitle="cloudBrandName"
      @close="closeCloudConfig">
      <div class="lmc-form">
        <div class="lmw-card">
          <div class="lmw-row">
            <div class="lmw-label">配对码</div>
            <input v-model.trim="cloudForm.pairCode" class="lmw-input" placeholder="电脑端「新增设备」生成的配对码" />
          </div>
          <div class="lmw-row">
            <div class="lmw-label">链接地址</div>
            <input v-model.trim="cloudForm.serverUrl" class="lmw-input" placeholder="如 https://www.innas.xyz:9999" />
          </div>
        </div>
        <div class="lmc-tip">配对码 10 分钟内有效、一次性；链接地址为 ERP 主机地址，配对成功后云打印机将出现在「云打印」列表中。</div>
        <div class="lmc-section">打印机品牌</div>
      </div>
      <div class="lmb-grid lmc-grid">
        <div
          v-for="b in wifiBrandList"
          :key="b.key"
          class="lmb-cell"
          :class="{ active: cloudForm.brand === b.key }"
          @click="chooseCloudBrand(b)">
          <div class="lmb-logo" :style="{ color: b.color }">{{ b.en || b.name }}</div>
          <div class="lmb-name">{{ b.name }}</div>
          <div class="lmb-cmd" @click.stop="openCloudCmdSheet(b)">
            <span>{{ b.cmd }}</span>
            <a-icon type="down" />
          </div>
        </div>
      </div>
      <template #footer>
        <div class="lmp-footer">
          <a-button
            type="primary"
            block
            size="large"
            class="lmp-print-btn"
            :loading="cloudSaving"
            @click="saveCloudPrinter">保存</a-button>
        </div>
      </template>
      <!-- 信号传输格式底部弹层 -->
      <div v-if="cmdSheetBrand" class="lmb-sheet-mask" @click="cmdSheetBrand = null">
        <div class="lmb-sheet" @click.stop>
          <div class="lmb-sheet-group">
            <div
              v-for="c in wifiCmdOptions"
              :key="c"
              class="lmb-sheet-item"
              @click="confirmCmdSheet(c)"
            >{{ c }}</div>
          </div>
          <div class="lmb-sheet-cancel" @click="cmdSheetBrand = null">取消</div>
        </div>
      </div>
    </mobile-modal-page>

    <!-- 页面 3：选择品牌（标准弹层页面） -->
    <mobile-modal-page
      :visible="visible && showPrinterSheet && wifiAddStep === 'brand'"
      title="选择品牌"
      @close="wifiAddStep = ''">
      <div class="lmb-grid">
        <div v-for="b in wifiBrandList" :key="b.key" class="lmb-cell" @click="selectWifiBrand(b)">
          <div class="lmb-logo" :style="{ color: b.color }">{{ b.en || b.name }}</div>
          <div class="lmb-name">{{ b.name }}</div>
          <div class="lmb-cmd" @click.stop="openCmdSheet(b)">
            <span>{{ b.cmd }}</span>
            <a-icon type="down" />
          </div>
        </div>
      </div>
      <!-- 信号传输格式底部弹层 -->
      <div v-if="cmdSheetBrand" class="lmb-sheet-mask" @click="cmdSheetBrand = null">
        <div class="lmb-sheet" @click.stop>
          <div class="lmb-sheet-group">
            <div
              v-for="c in wifiCmdOptions"
              :key="c"
              class="lmb-sheet-item"
              @click="confirmCmdSheet(c)"
            >{{ c }}</div>
          </div>
          <div class="lmb-sheet-cancel" @click="cmdSheetBrand = null">取消</div>
        </div>
      </div>
      <!-- 已保存打印机的指令集切换弹层 -->
      <div v-if="cmdSheetPrinter" class="lmb-sheet-mask" @click="cmdSheetPrinter = null">
        <div class="lmb-sheet" @click.stop>
          <div class="lmb-sheet-group">
            <div
              v-for="c in wifiCmdOptions"
              :key="c"
              class="lmb-sheet-item"
              @click="confirmPrinterCmdSheet(c)"
            >{{ c }}</div>
          </div>
          <div class="lmb-sheet-cancel" @click="cmdSheetPrinter = null">取消</div>
        </div>
      </div>
    </mobile-modal-page>

    <!-- 页面 4：网络打印配置（标准弹层页面） -->
    <mobile-modal-page
      :visible="visible && showPrinterSheet && wifiAddStep === 'config'"
      title="网络打印配置"
      @close="wifiAddStep = ''">
      <div class="lmw-form">
        <div class="lmw-card">
          <div class="lmw-row">
            <div class="lmw-label">品牌</div>
            <div class="lmw-value">{{ selectedWifiBrand ? selectedWifiBrand.name : '' }}</div>
          </div>
          <div class="lmw-row">
            <div class="lmw-label">传输格式</div>
            <div class="lmw-value">{{ selectedWifiBrand ? selectedWifiBrand.cmd : '' }}</div>
          </div>
          <div class="lmw-row">
            <div class="lmw-label">名称</div>
            <input v-model="addWifiForm.name" class="lmw-input" placeholder="如 汉印 D45" />
          </div>
          <div class="lmw-row">
            <div class="lmw-label">IP 地址</div>
            <input v-model="addWifiForm.ip" class="lmw-input" placeholder="如 192.168.10.10" />
          </div>
          <div class="lmw-row">
            <div class="lmw-label">端口号</div>
            <input v-model.number="addWifiForm.port" type="number" class="lmw-input" placeholder="9100" />
          </div>
        </div>
        <div class="lmw-tip">端口默认 9100（原始打印端口），请确保手机与打印机在同一局域网。</div>
      </div>
      <template #footer>
        <div class="lmp-footer">
          <a-button
            type="primary"
            block
            size="large"
            class="lmp-print-btn"
            :loading="addWifiTesting"
            @click="handleSaveWifiPrinter"
          >保存</a-button>
        </div>
      </template>
    </mobile-modal-page>

    <!-- 页面 5：编辑设备（打印机设备 / 打印机 / 品牌与指令集） -->
    <printer-device-config
      :visible="visible && showPrinterSheet && printerEditVisible"
      :devices="agentDevices"
      :device-id="printerEditDeviceId"
      :printer-id="printerEditPrinterId"
      @close="printerEditVisible = false"
      @saved="handlePrinterSaved" />
  </template>

  <!-- 桌面端：保留原有 modal 布局 -->
  <a-modal
    v-if="!isMobile"
    :title="printTitle || '打印标签'"
    :width="modalWidth"
    :visible="visible"
    :maskClosable="false"
    :footer="null"
    :zIndex="2900"
    :wrapClassName="isMobile ? 'lp-modal-mobile' : ''"
    @cancel="handleCancel"
  >
    <div class="lp-body" :class="{ 'lp-mobile': isMobile }">
      <template v-if="!template">
        <a-alert
          type="info"
          show-icon
          message="该商品暂无可用标签模板"
          description="可直接点击下方「编辑」创建标签模板后再打印"
        />
      </template>
      <template v-else>
        <div class="lp-size-tip">
          标签实际尺寸：{{ labelWidthMm }}mm × {{ labelHeightMm }}mm
          <span class="lp-template-name">{{ template.name }}</span>
        </div>
        <div class="lp-main">
          <!-- 左：标签预览 -->
          <div class="lp-preview-wrap">
            <div class="lp-preview" :class="{ 'lp-preview-schema': templateSchema }" :style="{ width: labelWidth + 'px', height: labelHeight + 'px' }">
              <span v-if="pageCount > 1" class="lp-page-badge">{{ previewPage + 1 }} / {{ pageCount }}</span>
              <!-- 新版模板：按 elements 直接渲染（mm 坐标、条码/二维码、明细循环） -->
              <label-paper
                v-if="templateSchema"
                :schema="previewSchema"
                :values="previewValues"
                :rows="pageDetailRows"
                :texts="previewTexts"
                :overflow-mode="overflowMode"
                :scale="1" />
              <template v-else>
                <div
                  v-for="item in renderItems"
                  :key="item.key"
                  class="lp-item"
                  :class="{ 'is-multiline': !item.isCode && item.attr.value != null && String(item.attr.value).indexOf('\n') > -1, 'is-code': item.isCode }"
                  :style="itemStyle(item.key)"
                >
                  <template v-if="item.isCode">
                    <img v-if="codeImages[item.key]" :src="codeImages[item.key]" class="lp-code-img" :style="codeImgStyle(item.key)" />
                  </template>
                  <template v-else-if="isGoodsTable(item.key)">
                    <div class="lp-goods-table">
                      <div class="lp-goods-row lp-goods-head" :style="{ height: (goodsTableData ? goodsTableData.headHeight : 0) + 'px' }">
                        <span v-for="(col, ci) in goodsTableColumns" :key="ci" class="lp-goods-cell"
                              :style="{ width: getGoodsColumnWidth(ci) + 'px' }">{{ col }}</span>
                      </div>
                      <div class="lp-goods-row" v-for="(row, ri) in (goodsTableData ? goodsTableData.rows : [])" :key="ri"
                           :style="{ height: getGoodsRowHeight(ri) + 'px' }">
                        <span v-for="(cell, ci) in row.cells" :key="ci" class="lp-goods-cell"
                              :style="{ width: getGoodsColumnWidth(ci) + 'px' }">{{ cell }}</span>
                      </div>
                    </div>
                  </template>
                  <template v-else>
                    {{ displayText(item) }}
                  </template>
                </div>
              </template>
            </div>
            <!-- 超出标签边界提示：只裁切不缩放字号 -->
            <div class="lp-overflow-tip" v-if="overflowPixels">
              内容超出标签底部 {{ overflowPixels }}px，已按标签边界裁切<template v-if="goodsPageMode === 'none'">；可在模板设置中开启分页</template>
            </div>
            <!-- 多商品分页：翻页 -->
            <div class="lp-pager" v-if="pageCount > 1">
              <a-button size="small" icon="left" :disabled="previewPage <= 0" @click="previewPage--">上一页</a-button>
              <span class="lp-pager-text">第 {{ previewPage + 1 }} / {{ pageCount }} 张</span>
              <a-button size="small" :disabled="previewPage >= pageCount - 1" @click="previewPage++">下一页<a-icon type="right" /></a-button>
            </div>
            <!-- 更多模板：预览下方切换模板 -->
            <div class="lp-template-more" v-if="templateChoices.length > 1">
              <div class="lp-template-more-head">
                <span>更多模板</span>
                <span class="lp-template-more-count">共 {{ templateChoices.length }} 个</span>
              </div>
              <div class="lp-template-cards">
                <div
                  v-for="t in templateChoices"
                  :key="t.id"
                  class="lp-template-card"
                  :class="{ active: String(currentTemplateId) === String(t.serverId || t.id) }"
                  @click="switchTemplate(t)"
                >
                  <span class="lp-template-card-name">{{ t.name }}</span>
                  <span class="lp-template-card-meta">{{ t.type === 'default' ? (t.categoryName || '通用模板') : '普通模板' }}</span>
                </div>
              </div>
            </div>
          </div>
          <!-- 右：打印设置 -->
          <div class="lp-settings">
            <div class="lp-settings-title">
              打印设置
              <a class="lp-refresh" @click="loadPrinters">刷新</a>
            </div>
            <div class="lp-field">
              <div class="lp-field-label">
                打印机
                <span v-if="!loadingPrinters && !localAgentOnline" class="lp-agent-hint">
                  打印组件未安装
                  <a :href="agentDownloadUrl" target="_blank" rel="noopener" download="print-agent.exe">下载安装</a>
                </span>
              </div>
              <div class="lp-printer-picker">
                <div class="lp-printer-current" :class="{ empty: !currentPrinterLabel }">
                  {{ currentPrinterLabel || '未选择打印机' }}
                </div>
                <a-button
                  class="lp-printer-add"
                  type="primary"
                  ghost
                  icon="plus"
                  :loading="loadingPrinters"
                  @click="openPrinterSettings"
                >添加</a-button>
              </div>
              <div v-if="localAgentOnline && !loadingPrinters && printers.length === 0 && wifiPrinters.length === 0" class="lp-tip">
                后端未返回打印机（远程部署时后端运行在服务器上），可点击「添加」配置本机或网络打印机。
              </div>
            </div>
            <div class="lp-field">
              <div class="lp-field-label">打印份数</div>
              <a-input-number v-model="copies" :min="1" :max="99" style="width:100%" />
            </div>
            <div class="lp-tip">
              打印由后端直接发送到所选打印机；若后端不可用，将自动改用浏览器打印对话框。
              Wi-Fi 打印机按 IP:端口 由后端下发 TSPL 指令，需保证后端所在电脑能访问该地址。
            </div>
          </div>
        </div>
      </template>

      <!-- 打印机设置弹层：添加卡片 + 全部打印机列表 + 网络配置页 -->
      <a-modal
        :visible="printerSettingsVisible"
        title="打印机设置"
        width="520px"
        :footer="null"
        :zIndex="2910"
        @cancel="closePrinterSettings"
      >
        <div class="lps-body">
          <!-- 网络打印机配置页 -->
          <template v-if="wifiAddStep === 'config'">
            <div class="lps-back" @click="wifiAddStep = ''">
              <a-icon type="arrow-left" />返回
            </div>
            <div class="lps-form">
              <div class="lp-add-item">
                <div class="lp-add-label">名称</div>
                <a-input v-model="addWifiForm.name" placeholder="如 汉印 D45" />
              </div>
              <div class="lp-add-item">
                <div class="lp-add-label">IP 地址</div>
                <a-input v-model="addWifiForm.ip" placeholder="如 192.168.10.10" />
              </div>
              <div class="lp-add-item">
                <div class="lp-add-label">端口号</div>
                <a-input-number v-model="addWifiForm.port" :min="1" :max="65535" style="width:100%" />
              </div>
            </div>
            <div class="lp-tip" style="margin-top:10px">
              指令集固定为 TSPL，端口默认 9100。需要 ERP 服务端与打印机在同一局域网；若 ERP 部署在远端服务器，服务器无法访问你本地局域网的打印机，请改用「本地添加」并在本机运行打印助手。
            </div>
            <div class="lps-form-footer">
              <a-button :loading="addWifiTesting" @click="handleTestAddWifi">测试连接</a-button>
              <a-button type="primary" @click="handleSaveWifiPrinter">保存</a-button>
            </div>
          </template>

          <!-- 添加卡片 + 全部打印机列表 -->
          <template v-else>
            <div class="lps-add-card">
              <div class="lps-add-title">添加打印机</div>
              <div class="lps-add-btns">
                <div class="lps-add-btn" @click="startAddNetwork">
                  <a-icon type="global" /><span>网络添加</span>
                </div>
                <div class="lps-add-btn" @click="handleAddBluetooth">
                  <a-icon type="bluetooth" /><span>蓝牙添加</span>
                </div>
                <div class="lps-add-btn" @click="handleAddLocalPrinter">
                  <a-icon type="desktop" /><span>本地添加</span>
                </div>
              </div>
            </div>
            <div class="lps-list-title">
              <span>全部打印机</span>
              <a class="lp-refresh" @click="loadPrinters">刷新</a>
            </div>
            <a-spin :spinning="loadingPrinters">
              <div class="lps-list">
                <div
                  v-for="item in allPrinterItems"
                  :key="item.key"
                  class="lps-item"
                  :class="{ active: isPrinterActive(item) }"
                  @click="handleSelectPrinterItem(item)"
                >
                  <a-icon :type="item.icon" class="lps-item-icon" />
                  <div class="lps-item-text">
                    <div class="lps-item-title">{{ item.name }}</div>
                    <div class="lps-item-sub">{{ item.sub }}</div>
                  </div>
                  <a-icon
                    v-if="item.deletable"
                    type="delete"
                    class="lps-item-del"
                    @click.stop="handleRemoveWifiPrinter(item.printer)"
                  />
                  <a-icon v-if="isPrinterActive(item)" type="check-circle" theme="filled" class="lps-check" />
                </div>
                <div v-if="allPrinterItems.length === 0" class="lps-empty">暂无可用打印机</div>
              </div>
            </a-spin>
          </template>
        </div>
      </a-modal>
      <div class="lp-footer">
        <a-button type="primary" icon="printer" :disabled="!template" :loading="printing" @click="handlePrint">打印</a-button>
        <a-button icon="edit" @click="handleEdit">编辑</a-button>
        <a-button @click="handleCancel">取消</a-button>
      </div>
    </div>
  </a-modal>
    <!-- 标签编辑：统一走标准复用弹层页面 -->
    <label-edit-modal-page ref="labelEditor" @close="handleEditorClosed" />
  </div>
</template>

<script>
  import { printLabel } from './utils/labelPrint'
  import { fitCodeSize, renderCodeDataUrl, isCodeKey, codeSourceKey, migrateCodeStyles, CODE_KEY_PREFIX } from './utils/labelCode'
  import { mmToPx, PX_PER_MM } from './utils/labelSchema'
  import { v2ToEditorState, v2ToSchemaView, isLabelTemplateV2, parseTemplateRaw, getCollectionPath, resolvePath, DEFAULT_ITEM_PATH } from './utils/labelTemplateV2'
  import { fetchTemplateList, resolveTemplate, deleteTemplate, applyReceiptRowsToState } from './utils/labelTemplateFlow'
  import { resolveTemplateData } from './utils/labelDataResolver'
  import { buildTemplateEditContext } from './utils/templateEditDataModel'
  import { ADDRESS_FORM_TITLE, buildAddressAttrs } from './utils/addressFormFields'
  import { buildFormFieldAttrs } from './config/formFields'
  import { readLabelEditSnapshot, saveLabelEditSnapshot, valuesFromAttrs } from './utils/labelEditSnapshot'
  import { GOODS_TABLE_KEY, GOODS_KEY, GOODS_TABLE_COLUMNS, resolveLabelLayout, paginateGoodsRows, hasGoodsValue, isGoodsTableKey, buildSpecValuesFromRows } from './utils/goodsLabelLayout'
  import { LABEL_CATEGORY_KEY, LABEL_SPEC_KEY, flattenSpecMapping, resolveSpecTextFromDefs, resolvePrimaryCategoryName, resolveRowCategoryId } from './utils/categorySpec'
  import { parseSpecItemKey, specNameKey, isSpecItemKey, buildSpecBlocksFromCategory, specBlocksFromRow } from './utils/goodsSpecBlocks'
  import { buildSpecBlockItems, resolveSpecBlockLines, SPEC_BLOCK_PAD_X, SPEC_BLOCK_GAP } from './utils/specBlockLayout'
  import LabelPaper from './LabelPaper'
  import LabelEditModalPage from '@/views/material/modules/LabelEditModalPage'
  import MobileModalPage from '@/components/mobile/MobileModalPage'
  import { getAction, postAction } from '@/api/manage'
  import { queryMaterialCategoryTreeList } from '@/api/api'
  import {
    DEFAULT_PRINTER_PORT,
    getPrinterCapabilities,
    searchBluetoothPrinter,
    getWifiPrinters,
    saveWifiPrinter,
    removeWifiPrinter,
    testWifiPrinter,
    printToWifiPrinter
  } from '@/utils/printerSearch'
  import { fetchLocalPrinters, printToLocalPrinter, isLocalPrinter, localPrinterName, LOCAL_PRINTER_PREFIX, detectLocalAgent, LOCAL_AGENT_DOWNLOAD_URL } from '@/utils/localPrintAgent'
  import { getPrintAgentList, printViaAgent } from '@/api/printAgent'
  import PrinterDeviceConfig from '@/views/print/agent/PrinterDeviceConfig'
  export default {
    name: 'LabelPrintModal',
    // 异步引入，避免与内嵌在标签编辑页里的本组件形成循环依赖
    components: { LabelPaper, MobileModalPage, PrinterDeviceConfig, LabelEditModalPage },
    data() {
      return {
        visible: false,
        // 设备列表当前分类：cloud / network / local
        deviceTab: 'cloud',
        record: {},
        sku: {},
        // 当前模板业务类型：label / delivery / receipt
        type: 'label',
        // 打印页标题（调用方可覆盖，如地址识别页传「地址打印」）
        printTitle: '',
        template: null,
        // 加载到旧结构模板：不再兼容，空状态提示删除后重建
        outdatedTemplate: false,
        // 旧模板描述（platformConfig 单 key 模板删除时需要 key）
        outdatedTemplateInfo: null,
        // 路由模板加载失败原因：区分「加载失败」与「确实没有模板」
        templateLoadError: '',
        // 更多模板：可切换的模板列表与当前模板 id
        templateChoices: [],
        currentTemplateId: '',
        // 模板读取中：读取完成前不渲染空状态，避免把「还没回来」显示成「没有模板」
        templateLoading: false,
        allAttrs: [],
        selectedAttrs: [],
        hiddenReceiptKeys: [],
        attrStyles: {},
        attrLabels: {},
        // 商品信息（组合值）显示哪些字段：由模板保存，缺省显示全部
        goodsFields: null,
        // 数据源明细行（新版模板 table 元素循环渲染用）
        detailRows: [],
        // 多商品分页：当前预览页 / 当前打印页
        previewPage: 0,
        printPage: 0,
        _batchPrint: false,
        // 条码/二维码图片缓存（key → dataURL）
        codeImages: {},
        labelWidth: 320,
        labelHeight: 240,
        // 小票单品元素是否已按明细行生成（每次套用模板后重置）
        receiptItemsApplied: false,
        // 打印机（由后端枚举本机打印机）
        printers: [],
        // 本机打印机（由本地打印助手提供，仅电脑端可用）
        localPrinters: [],
        localAgentOnline: false,
        agentDownloadUrl: LOCAL_AGENT_DOWNLOAD_URL,
        selectedPrinter: undefined,
        loadingPrinters: false,
        printing: false,
        // 打印失败原因（直接显示在按钮上方，避免手机端提示被遮挡）
        printHint: '',
        copies: 1,
        // 手机浏览器（窄屏）时预览/设置改为上下布局
        isMobile: false,
        // ===== 打印机（手机端）=====
        printerType: 'backend', // backend | bluetooth | wifi | cloud
        printerCaps: getPrinterCapabilities(),
        // 已发现的打印机对象（蓝牙/云，非后端枚举的）
        discoveredPrinter: null,
        // 蓝牙
        btSearching: false,
        btDevice: null,
        // Wi-Fi 打印机：手动配置（名称/IP/端口），保存在本地
        wifiPrinters: [],
        activeWifiId: null,
        wifiStatus: {},
        // 远程打印机（ERP 打印代理设备）
        agentDevices: [],
        loadingAgents: false,
        // 云打印配对配置页
        cloudAddVisible: false,
        cloudSaving: false,
        cloudForm: { pairCode: '', serverUrl: '', brand: '' },
        // 打印机编辑（品牌/指令集）
        printerEditVisible: false,
        printerEditDeviceId: '',
        printerEditPrinterId: '',
        // 网络打印机测试状态
        addWifiTesting: false,
        addWifiForm: { name: '', ip: '', port: DEFAULT_PRINTER_PORT },
        // 手机端添加 Wi-Fi 打印机：'' | 'brand'(选择品牌) | 'config'(Wi-Fi 配置)
        wifiAddStep: '',
        // 品牌列表（cmd 为该品牌当前选中的信号传输格式）
        wifiBrandList: [
          { key: 'chiteng', name: '驰腾', en: '驰腾', color: '#1d39c4', cmd: 'TSPL' },
          { key: 'gainscha', name: '佳博', en: 'Gainscha', color: '#1890ff', cmd: 'TSPL' },
          { key: 'sprt', name: '思普瑞特', en: 'SPRT', color: '#1d39c4', cmd: 'TSPL' },
          { key: 'hprt', name: '汉印', en: '汉印 HPRT', color: '#cf1322', cmd: 'CPCL' },
          { key: 'clabel', name: 'Clabel', en: 'Clabel', color: '#1d39c4', cmd: 'TSPL' },
          { key: 'kuaimai', name: '快麦', en: '快麦', color: '#fa541c', cmd: 'TSPL' },
          { key: 'qirui', name: '启锐', en: '启锐', color: '#1d39c4', cmd: 'TSPL' },
          { key: 'tsc', name: 'TSC', en: 'TSC', color: '#003a8c', cmd: 'TSPL' },
          { key: 'ure', name: '优迩', en: 'URPRTCN', color: '#fa8c16', cmd: 'TSPL' }
        ],
        // 信号传输格式选项
        wifiCmdOptions: ['CPCL', 'DDPL', 'TSPL', 'ESC/POS'],
        // 正在调整传输格式的品牌（底部弹层），null 表示关闭
        cmdSheetBrand: null,
        // 正在调整指令集的已保存打印机（底部弹层），null 表示关闭
        cmdSheetPrinter: null,
        // 已选中的品牌（进入配置页）
        selectedWifiBrand: null,
        // 手机端：打印方向（0 / 90 / 180）
        printRotation: 0,
        // 手机端：打印机选择弹层
        showPrinterSheet: false,
        // 桌面端：打印机设置弹层
        printerSettingsVisible: false,
        // 手机端：预览标签缩放因子（让标签填满空白区域）
        previewScale: 1
      }
    },
    computed: {
      // 打印机类型标签列表（三分类：云 / 网络 / 本地）
      printerTabList() {
        return [
          { key: 'cloud', label: '云打印' },
          { key: 'network', label: '网络打印' },
          { key: 'local', label: '本地打印' }
        ]
      },
      // 主机下发的全部远程打印机（打印代理设备下的打印机，扁平化）
      agentAllPrinters() {
        var list = []
        ;(this.agentDevices || []).forEach(function(dev) {
          if (dev.enabled === 0 || dev.enabled === '0' || dev.enabled === false) return
          var printers = dev.printers
          if (typeof printers === 'string') {
            try { printers = JSON.parse(printers) } catch (e) { printers = [] }
          }
          if (!printers || !printers.length) return
          printers.forEach(function(p) {
            list.push({
              key: 'agent:' + dev.deviceId + ':' + p.id,
              deviceRowId: dev.id,
              deviceId: dev.deviceId,
              deviceName: dev.name || dev.deviceId,
              printerId: p.id,
              printerName: p.name || p.id,
              host: p.host || '',
              port: p.port || '',
              brand: p.brand || '',
              cmdSet: p.cmdSet || 'TSPL',
              online: !!dev.online
            })
          })
        })
        return list
      },
      // 云打印列表：直接展示主机已配置（下发）的全部云打印机，供用户选择
      agentPrinterItems() {
        return this.agentAllPrinters
      },
      // 云打印配置页副标题：当前选中的品牌 + 指令
      cloudBrandName() {
        var brand = this.selectedCloudBrand
        return brand ? brand.name + ' · ' + brand.cmd : ''
      },
      selectedCloudBrand() {
        var key = this.cloudForm.brand
        if (!key) return null
        return this.wifiBrandList.find(function(b) { return b.key === key }) || null
      },
      rotationOptions() {
        return [
          { value: 0, label: '0°', icon: 'edit' },
          { value: 90, label: '90°', icon: 'edit' },
          { value: 180, label: '180°', icon: 'edit' }
        ]
      },
      // 手机端顶栏标题：调用方指定标题优先，其次模板名，无模板时取业务类型
      mobileTitle() {
        if (this.printTitle) return this.printTitle
        if (this.template) return this.template.name || '标签打印'
        var titles = { delivery: '发货单打印', receipt: '小票打印', member: '会员打印', address: '地址打印' }
        return titles[this.type] || '标签打印'
      },
      // 空状态提示：按模板类型区分文案
      emptyMessage() {
        if (this.templateLoadError) return '模板加载失败'
        if (this.outdatedTemplate) return '有旧模板已不支持，请删除后重新创建'
        var msgs = { delivery: '暂无可用发货单模板', receipt: '暂无可用小票模板', member: '暂无可用会员标签模板', address: '暂无可用地址打印模板' }
        return msgs[this.type] || '该商品暂无可用标签模板'
      },
      // 手机端顶栏副标题：尺寸 + 列数
      mobileSubtitle() {
        if (!this.template) return ''
        return this.labelWidthMm + 'mm × ' + this.labelHeightMm + 'mm' + (this.template.columns ? ', ' + this.template.columns + '列' : '')
      },
      // 手机端：当前打印机显示名
      mobileDeviceLabel() {
        if (this.printerType === 'backend') {
          // 远程打印机使用友好的「远程：打印机名」，不直接显示 agent: 内部标识
          return this.currentPrinterLabel
        }
        if (this.printerType === 'wifi' && this.activeWifiId) {
          var p = this.wifiPrinters.find(function(x) { return x.id === this.activeWifiId }.bind(this))
          if (p) return p.name
          return ''
        }
        if (this.printerType === 'bluetooth' && this.btDevice) return this.btDevice.name
        if (this.printerType === 'cloud') return '系统打印机'
        return ''
      },
      mobileDeviceOnline() {
        if (this.printerType === 'wifi' && this.activeWifiId) {
          return !!this.wifiStatus[this.activeWifiId]
        }
        // 远程打印机按打印代理设备的在线状态显示
        if (this.printerType === 'backend' && this.selectedPrinter
          && String(this.selectedPrinter).indexOf('agent:') === 0) {
          var agent = this.agentPrinterItems.find(function(p) { return p.key === this.selectedPrinter }.bind(this))
          return !!(agent && agent.online)
        }
        return !!this.mobileDeviceLabel
      },
      // 是否已添加过任何可用打印机（用于点击打印时的引导判断）
      hasAnyPrinter() {
        return this.printers.length > 0 || this.agentPrinterItems.length > 0
          || this.wifiPrinters.length > 0 || !!this.btDevice
      },
      // 手机端：预览标签样式（宽度优先填满，高度自然溢出）
      previewStyle() {
        return {
          width: this.labelWidth + 'px',
          height: this.labelHeight + 'px',
          transform: 'scale(' + this.previewScale + ') rotate(' + this.printRotation + 'deg)',
          // center top：缩放从顶部向下展开，超出高度时从底部溢出可滚动
          transformOrigin: 'center top'
        }
      },
      // 手机端多页标签：一律在同一页内纵向堆叠、上下滑动预览，不再翻页
      stackPages() {
        return this.isMobile && this.pageCount > 1
      },
      // 堆叠页数据：每页各自的布局、数值、明细行与 schema
      stackPageList() {
        if (!this.stackPages) return []
        var self = this
        return this.pageIndexes.map(function(p) {
          var layout = self.layoutForPage(p)
          var values = self.valuesForPage(p, layout)
          return {
            index: p,
            layout: layout,
            schema: self.schemaForLayout(layout, values, p),
            values: values,
            rows: self.rowsForPage(p),
            texts: layout.texts || {}
          }
        })
      },
      pageIndexes() {
        var arr = []
        for (var i = 0; i < this.pageCount; i++) arr.push(i)
        return arr
      },
      // 堆叠占位：按缩放后的实际高度撑开，滚动条长度才准确
      stackSlotStyle() {
        return {
          position: 'relative',
          width: '100%',
          height: (this.labelHeight * this.previewScale + 12) + 'px'
        }
      },
      stackPageStyle() {
        return {
          position: 'absolute',
          top: '0',
          left: '50%',
          width: this.labelWidth + 'px',
          height: this.labelHeight + 'px',
          marginLeft: (-this.labelWidth / 2) + 'px',
          transform: 'scale(' + this.previewScale + ')',
          transformOrigin: 'center top'
        }
      },
      modalWidth() {
        return this.isMobile ? '100%' : 820
      },
      // 当前选中打印机的展示名（只读展示框用）
      currentPrinterLabel() {
        if (this.printerType === 'wifi') {
          var wifi = this.wifiPrinters.find(function(p) { return p.id === this.activeWifiId }.bind(this))
          return wifi ? wifi.name : ''
        }
        if (this.printerType === 'bluetooth') return this.btDevice ? this.btDevice.name : ''
        if (this.selectedPrinter === '__browser__') return '浏览器打印（系统对话框）'
        if (this.selectedPrinter && String(this.selectedPrinter).indexOf('agent:') === 0) {
          var agent = this.agentPrinterItems.find(function(p) { return p.key === this.selectedPrinter }.bind(this))
          return agent ? '远程：' + agent.printerName : '远程打印机'
        }
        if (this.selectedPrinter) {
          return isLocalPrinter(this.selectedPrinter)
            ? '本机：' + localPrinterName(this.selectedPrinter)
            : this.selectedPrinter
        }
        return ''
      },
      // 弹层「全部打印机」合并列表：本机 / 后端 / 网络 / 蓝牙 / 浏览器
      allPrinterItems() {
        var items = []
        this.localPrinters.forEach(function(name) {
          items.push({
            key: 'local:' + name,
            value: LOCAL_PRINTER_PREFIX + name,
            name: '本机：' + name,
            sub: '本机打印机（直接打印）',
            icon: 'desktop',
            source: 'backend',
            deletable: false
          })
        })
        this.printers.forEach(function(p) {
          items.push({
            key: 'backend:' + p.name,
            value: p.name,
            name: p.name,
            sub: p.isDefault ? '后端打印机 · 默认' : '后端打印机',
            icon: 'printer',
            source: 'backend',
            deletable: false
          })
        })
        this.wifiPrinters.forEach(function(p) {
          items.push({
            key: p.id,
            value: p.id,
            name: p.name,
            sub: p.ip + ':' + p.port,
            icon: 'wifi',
            source: 'wifi',
            deletable: true,
            printer: p
          })
        })
        if (this.btDevice) {
          items.push({
            key: 'bt:' + this.btDevice.id,
            value: this.btDevice.id,
            name: this.btDevice.name,
            sub: '蓝牙打印机 · 已连接',
            icon: 'bluetooth',
            source: 'bluetooth',
            deletable: false
          })
        }
        items.push({
          key: '__browser__',
          value: '__browser__',
          name: '浏览器打印',
          sub: '系统打印对话框',
          icon: 'printer',
          source: 'browser',
          deletable: false
        })
        return items
      },
      selectedAttrList() {
        var self = this
        return this.selectedAttrs
          .map(function(key) { return self.allAttrs.find(function(a) { return a.key === key }) })
          .filter(function(attr) { return !!attr })
      },
      labelWidthMm() {
        return Math.round(this.labelWidth / PX_PER_MM)
      },
      labelHeightMm() {
        return Math.round(this.labelHeight / PX_PER_MM)
      },
      // 渲染条目：属性文字元素 + 独立的条码/二维码元素
      // 新版模板（v3 elements）直接渲染；旧模板没有 schema，继续走下面的兼容渲染
      templateSchema() {
        return this.template && this.template.schema && this.template.schema.elements
          ? this.template.schema
          : null
      },
      // 新版模板渲染用的字段值（来自统一数据接口）
      attrValues() {
        var map = {}
        this.allAttrs.forEach(function(attr) {
          map[attr.key] = attr.value == null ? '' : attr.value
        })
        // 注意：分页标签文案依赖 pageCount，不能放在这里，否则会和分页计划互相递归
        return map
      },
      // 明细行：优先取快照 rows，其次按模板约定的集合路径从记录中取值
      // 条数由实际数组长度决定，模板不保存条数
      goodsRowsForLayout() {
        if (this.detailRows && this.detailRows.length) return this.detailRows
        if (!this.record) return []
        var path = (this.template && this.template.collectionPath) || DEFAULT_ITEM_PATH
        var rows = resolvePath(this.record, path)
        if (!Array.isArray(rows) && path !== DEFAULT_ITEM_PATH) {
          rows = resolvePath(this.record, DEFAULT_ITEM_PATH)
        }
        return Array.isArray(rows) ? rows : []
      },
      // 商品明细表样式的实际元素 key（小票为 rc_table，其余为 goods_table）
      goodsTableKey() {
        return this.selectedAttrs.filter(isGoodsTableKey)[0] || GOODS_TABLE_KEY
      },
      // 多商品分页模式：随模板保存（none / fit / each）
      goodsPageMode() {
        var tpl = this.template
        var mode = tpl && tpl.pageMode
        return mode === 'fit' || mode === 'each' ? mode : 'none'
      },
      // 参与分页的商品行（忽略空行）
      validGoodsRows() {
        return (this.goodsRowsForLayout || []).filter(hasGoodsValue)
      },
      // 分页计划：none=单页全量，each=每行一页，fit=按标签可用高度拆分
      goodsPagePlan() {
        if (this.goodsPageMode === 'none') {
          return { pages: [{ rows: this.goodsRowsForLayout, rowIndexes: [] }], count: 1 }
        }
        var table = null
        // fit 表格模式：用全量行算出表头与行高，再交给分页函数
        if (this.goodsPageMode === 'fit' && this.selectedAttrs.some(isGoodsTableKey)) {
          table = this.layoutWithRows(this.validGoodsRows).table
        }
        var style = this.attrStyles[this.goodsTableKey] || this.attrStyles[GOODS_KEY] || {}
        return paginateGoodsRows({
          rows: this.validGoodsRows,
          style: style,
          table: table,
          paperWidth: this.labelWidth,
          paperHeight: this.labelHeight,
          goodsFields: this.goodsFields,
          goodsLabel: this.attrLabels[GOODS_KEY] || '',
          mode: this.goodsPageMode,
          // 已移除「每页商品行数」设置，自动分页只按标签高度续页
          maxRowsPerPage: 0
        })
      },
      pageCount() {
        // 无有效商品行时仍保留 1 张，避免打印按钮无输出
        return Math.max(1, this.goodsPagePlan.count || 0)
      },
      // 内容超出标签底部的像素数：只按标签边界裁切并提示，不缩小字号
      overflowPixels() {
        var styles = this.labelLayout.styles || {}
        // 单页多商品：模板的名称/类别/规格由逐商品行组替代，下方元素随行组顺移
        var groups = this.multiGoodsGroups(this.rowsForPage(this.previewPage))
        var plan = groups ? this.multiGoodsGroupPlan(this.labelLayout, groups) : null
        var bottom = 0
        Object.keys(styles).forEach(function(key) {
          var s = styles[key]
          if (!s) return
          if (plan && plan.keys.indexOf(key) > -1) return
          var y = s.y || 0
          if (plan && plan.belowShift > 0 && y >= plan.belowFrom - 0.5) y += plan.belowShift
          bottom = Math.max(bottom, y + (s.height || 0))
        })
        if (plan) bottom = Math.max(bottom, plan.belowFrom + plan.belowShift)
        var over = Math.ceil(bottom - this.labelHeight)
        return over > 1 ? over : 0
      },
      // 当前预览页的商品明细行（供明细循环渲染）
      pageDetailRows() {
        return this.rowsForPage(this.previewPage)
      },
      // 当前预览页的业务值：商品组合只取本页
      previewValues() {
        return this.valuesForPage(this.previewPage, this.labelLayout)
      },
      /**
       * 标签最终布局（编辑预览 / 打印预览 / 打印数据共用同一函数）
       * 商品明细表、商品组合、文字折行、上下归位都在这里一次算完
       */
      labelLayout() {
        return this.layoutForPage(this.previewPage)
      },
      goodsLayout() {
        return this.labelLayout
      },
      goodsTableData() {
        return this.labelLayout.table
      },
      // 超长文本处理模式：随模板保存，wrap=自动折行并按实际行数上下归位，ellipsis=单行省略号裁切
      overflowMode() {
        return this.template && this.template.overflowMode === 'ellipsis' ? 'ellipsis' : 'wrap'
      },
      // 折行 / 省略号裁切结果（预览与打印共用）
      overflowLayout() {
        return this.labelLayout
      },
      // 预览 schema：套用折行后的实际坐标与高度
      previewSchema() {
        return this.schemaForLayout(this.overflowLayout, this.previewValues, this.previewPage)
      },
      // 省略号模式预览文本：直接用裁切后的文本
      previewTexts() {
        // 统一走布局结果：商品多行逐行带前缀，省略模式为截断后的文字
        return this.overflowLayout.texts
      },
      goodsTableColumns() {
        return GOODS_TABLE_COLUMNS
      },
      renderItems() {
        var items = []
        var styles = this.attrStyles
        this.selectedAttrList.forEach(function(attr) {
          // 自定义码元素：样式与内容都挂在元素自身 key 上，不走文字渲染
          if (attr.isCodeElem) {
            items.push({ key: attr.key, attr: attr, isCode: true })
            return
          }
          items.push({ key: attr.key, attr: attr, isCode: false })
          if (styles[CODE_KEY_PREFIX + attr.key]) {
            items.push({ key: CODE_KEY_PREFIX + attr.key, attr: attr, isCode: true })
          }
        })
        return items
      },
      // 编码内容签名：属性值、编码类型、尺寸变化时重新生成条码/二维码图片
      codeSignature() {
        var self = this
        return this.renderItems.filter(function(item) { return item.isCode }).map(function(item) {
          var s = self.attrStyles[item.key] || {}
          return [item.key, s.codeType || item.attr.codeType || '', item.attr.value == null ? '' : item.attr.value, s.width || '', s.height || ''].join('|')
        }).join(';')
      }
    },
    watch: {
      codeSignature() {
        this.refreshCodeImages()
      },
      // 分页方式 / 商品变化导致总页数减少时，预览页码回落到有效范围
      pageCount(val) {
        if (this.previewPage > val - 1) this.previewPage = Math.max(0, val - 1)
      },
      // 模板异步加载完成后重新计算缩放
      template() {
        if (this.isMobile && this.visible) this.updatePreviewScale()
      }
    },
    created() {
      var self = this
      this._mql = window.matchMedia('(max-width: 768px)')
      this.isMobile = this._mql.matches
      this._onMqChange = function(e) { self.isMobile = e.matches }
      if (this._mql.addEventListener) {
        this._mql.addEventListener('change', this._onMqChange)
      } else if (this._mql.addListener) {
        this._mql.addListener(this._onMqChange)
      }
      // 手机端：窗口大小变化时重新计算预览缩放
      this._onResize = function() {
        if (self.isMobile && self.visible) self.updatePreviewScale()
      }
      window.addEventListener('resize', this._onResize)
    },
    beforeDestroy() {
      if (!this._mql) return
      if (this._mql.removeEventListener) {
        this._mql.removeEventListener('change', this._onMqChange)
      } else if (this._mql.removeListener) {
        this._mql.removeListener(this._onMqChange)
      }
      window.removeEventListener('resize', this._onResize)
    },
    methods: {
      // 指定页的页码文案（如 1/2）：带参数调用，必须放在 methods，放在 computed 里会被当成取值
      pageNoTextFor(page) {
        var idx = Math.max(0, page || 0)
        return (idx + 1) + '/' + this.pageCount
      },
      // 手机端：按标签实际物理尺寸预览（设计像素 = 96dpi，1px ≈ 1/3.78mm）
      //   默认 1:1 展示，不放大；仅当屏幕放不下时按宽/高等比缩小
      updatePreviewScale() {
        var self = this
        this.$nextTick(function() {
          var area = self.$refs.previewArea
          if (!area) { self.previewScale = 1; return }
          var rect = area.getBoundingClientRect()
          if (rect.width <= 0 || rect.height <= 0) { self.previewScale = 1; return }
          var padLR = 8    // 左右各留 8px
          var padT = 16    // 上边固定距
          // 预览区下方还有底部控制区（高度由 flex 自动分配，这里用一个合理估算）
          var availW = rect.width - padLR * 2
          var availH = rect.height - padT
          var scaleW = availW / self.labelWidth
          var scaleH = availH / self.labelHeight
          self.previewScale = Math.min(scaleW, scaleH, 1)
        })
      },
      // 编码元素的图形布局：尺寸取元素自身宽高，按打印机能力换算
      codeLayout(codeKey) {
        var s = this.attrStyles[codeKey] || {}
        var sourceKey = codeSourceKey(codeKey)
        var attr = this.allAttrs.find(function(a) { return a.key === sourceKey })
        if (!attr) {
          // 自定义码元素：属性与样式共用元素自身 key
          var selfAttr = this.allAttrs.find(function(a) { return a.key === codeKey })
          if (selfAttr && selfAttr.isCodeElem) attr = selfAttr
        }
        var value = attr && attr.value != null ? attr.value : ''
        var codeType = s.codeType || (attr && attr.isCodeElem ? attr.codeType : '')
        var isQr = codeType === 'qrcode'
        var boxWidth = s.width || (isQr ? 80 : 160)
        var boxHeight = s.height || (isQr ? 80 : 50)
        var fitted = fitCodeSize(codeType, value, boxWidth, boxHeight)
        return {
          value: value,
          imgWidth: fitted.width,
          imgHeight: fitted.height,
          // 硬件参数随布局一起下发：窄条宽度（条码）/ 单元宽度（二维码）
          narrow: fitted.narrow,
          cell: fitted.cell
        }
      },
      codeImgStyle(codeKey) {
        var layout = this.codeLayout(codeKey)
        return { width: layout.imgWidth + 'px', height: layout.imgHeight + 'px' }
      },
      // 为所有独立编码元素生成图片
      refreshCodeImages() {
        var self = this
        var alive = {}
        this.renderItems.forEach(function(item) {
          if (!item.isCode) return
          var s = self.attrStyles[item.key]
          if (!s) return
          alive[item.key] = true
          var layout = self.codeLayout(item.key)
          renderCodeDataUrl(s.codeType, layout.value, layout.imgWidth, layout.imgHeight).then(function(url) {
            self.$set(self.codeImages, item.key, url)
          })
        })
        Object.keys(this.codeImages).forEach(function(key) {
          if (!alive[key]) self.$delete(self.codeImages, key)
        })
      },
      // 以弹层方式直接打开（不跳路由）：零售出库「发货」、会员打印、标签编辑页「打印」等场景调用
      // 数据来源：模板取模板存储，业务值取 sessionStorage 快照
      // options.templateId / options.name：指定标签模板（编辑页打印时传入当前模板）
      // options.directEntry === false：编辑按钮不做路由跳转（由父页面自行处理）
      // options.title：打印页标题（不传则按业务类型显示）
      openByType(type, options) {
        var self = this
        var opts = options || {}
        this.directEntry = opts.directEntry !== false
        this.printTitle = opts.title || ''
        // 重新打开打印页时关闭可能残留的编辑弹层
        if (this.$refs.labelEditor) this.$refs.labelEditor.close()
        this.type = type || 'delivery'
        // 切换业务类型时先清空上一套模板，避免模板加载失败或为空时残留上次的标签内容
        this.template = null
        this.outdatedTemplate = false
        this.outdatedTemplateInfo = null
        this.templateLoadError = ''
        this.templateChoices = []
        this.currentTemplateId = ''
        this.selectedAttrs = []
        this.attrStyles = {}
        this.attrLabels = {}
        this.detailRows = []
        this.record = opts.record || {}
        this.sku = opts.sku || {}
        this.allAttrs = this.buildAttrList()
        this.copies = 1
        this.printRotation = 0
        this.printing = false
        this._batchPrint = false
        this._printFailed = 0
        this.printPage = 0
        this.discoveredPrinter = null
        this.btDevice = null
        this.activeWifiId = null
        this.showPrinterSheet = false
        this.cloudAddVisible = false
        this.wifiAddStep = ''
        this.printHint = ''
        this.visible = true
        // 临时诊断：记录模板加载失败的具体环节（定位完成后移除）
        this._diag = ''
        // 商品标签：按商品/类目匹配模板；其它类型未显式指定时沿用上一次使用的模板
        var lastTplId = ''
        if (this.type !== 'label' && !opts.templateId) {
          try { lastTplId = localStorage.getItem('jsh.lastLabelTemplateId') || '' } catch (e) { lastTplId = '' }
        }
        var preferredId = opts.templateId || lastTplId
        this.currentTemplateId = preferredId ? String(preferredId) : ''
        this.templateLoading = true
        this.loadRouteTemplate({ type: this.type, templateId: preferredId, name: opts.name }).then(function(raw) {
          self.templateLoading = false
          var tpl = null
          try {
            tpl = self.normalizeTemplate(raw)
          } catch (e) {
            self.templateLoadError = 'diag:throw:' + ((e && (e.message || e.name)) || String(e))
            return
          }
          if (!tpl && !self.templateLoadError && !self.outdatedTemplate && preferredId) {
            self.templateLoadError = 'diag:' + (self._diag || 'normalize-null') + '|raw=' + (raw == null ? 'null' : typeof raw)
          }
          if (tpl) {
            self.applyTemplate(tpl, true)
            self.restoreMissingAttrs(self.selectedAttrs)
            self.applySnapshotValues()
            if (self.currentTemplateId) self.rememberTemplateId(self.currentTemplateId)
          } else if (!self.outdatedTemplate) {
            // 商品标签：没有指定/找不到上次的模板时，按当前商品所属类目重新匹配
            if (self.type === 'label' && self.record && self.record.id) {
              self.templateLoadError = ''
              self.loadTemplate().then(function(matched) {
                if (matched) {
                  var matchedId = matched.serverId || matched.id
                  if (matchedId) {
                    self.currentTemplateId = String(matchedId)
                    self.rememberTemplateId(matchedId)
                  }
                  self.applySnapshotValues()
                } else {
                  self.applySnapshot()
                }
                self.$nextTick(function() { self.updatePreviewScale() })
              })
            } else {
              self.applySnapshot()
            }
          }
          self.$nextTick(function() { self.updatePreviewScale() })
        }).catch(function() {
          self.templateLoading = false
          if (!self.outdatedTemplate) self.applySnapshot()
          self.$nextTick(function() { self.updatePreviewScale() })
        }).then(function() {
          self.loadTemplateChoices()
        })
        // 恢复上次使用的打印机并加载设备列表，保证「打印」按钮可用
        this.restoreLastPrinterState(false)
        // 恢复上次设备分类
        this.deviceTab = this.printerType === 'wifi'
          ? 'network'
          : (this.printerType === 'backend' && this.selectedPrinter && this.selectedPrinter.indexOf('agent:') === 0 ? 'cloud' : (this.printerType === 'cloud' ? 'cloud' : 'local'))
      },
      open(record, sku, preferredTpl) {
        this.directEntry = false
        this.printTitle = ''
        this.record = record || {}
        this.sku = sku || {}
        this.allAttrs = this.buildAttrList()
        // 商品标签业务快照：编辑页「打印」按同一快照合并（type=label），
        // 保证「预览 → 编辑 → 打印」后商品字段/明细不丢；无业务数据时不写，避免覆盖有效快照
        var source = this.record
        if (source.id != null || (Array.isArray(source.goodsRows) && source.goodsRows.length)) {
          saveLabelEditSnapshot({
            type: 'label',
            number: source.number || source.code || '',
            values: valuesFromAttrs(this.allAttrs),
            rows: Array.isArray(source.goodsRows) ? source.goodsRows : []
          })
        }
        if (preferredTpl) {
          // 由标签编辑页打开时直接使用其当前编辑内容，保持所见即所得
          this.template = null
          this.applyTemplate(preferredTpl)
        } else {
          this.loadTemplate()
        }
        this.copies = 1
        this.printRotation = 0
        this.printing = false
        this.printHint = ''
        this._batchPrint = false
        this._printFailed = 0
        this.printPage = 0
        this.discoveredPrinter = null
        this.btDevice = null
        this.activeWifiId = null
        this.showPrinterSheet = false
        this.cloudAddVisible = false
        this.wifiAddStep = ''
        this.visible = true
        // 恢复上次使用的打印机并加载设备列表
        this.restoreLastPrinterState(true)
      },
      // 恢复上次使用的打印机并加载设备列表（open / openByType 共用）
      // fallbackToBackend：无历史记录时是否强制回到后端打印机（open 场景需要，openByType 保持现状）
      restoreLastPrinterState(fallbackToBackend) {
        var self = this
        var last = null
        try { last = JSON.parse(localStorage.getItem('jsh.lastLabelPrinter') || 'null') } catch (e) { last = null }
        if (last && last.type) {
          this.printerType = last.type
          if (last.type === 'backend') this.selectedPrinter = last.name || undefined
          else if (last.type === 'wifi') { this.activeWifiId = last.name || null; this.selectedPrinter = last.name || undefined }
          else this.selectedPrinter = undefined
        } else if (fallbackToBackend) {
          this.printerType = 'backend'
          this.selectedPrinter = undefined
        }
        this.loadPrinters()
        this.loadWifiPrinters()
        // 手机端：打开后计算预览缩放（等 DOM 渲染完；模板可能异步加载，延迟再算一次）
        if (this.isMobile) {
          this.$nextTick(function() { self.updatePreviewScale() })
          setTimeout(function() { self.updatePreviewScale() }, 300)
        }
      },
      // 保存当前打印机选择到 localStorage
      persistLastPrinter() {
        var payload = { type: this.printerType, name: null }
        if (this.printerType === 'backend') payload.name = this.selectedPrinter || null
        else if (this.printerType === 'wifi') payload.name = this.activeWifiId || this.selectedPrinter || null
        try { localStorage.setItem('jsh.lastLabelPrinter', JSON.stringify(payload)) } catch (e) {}
      },
      // 打开打印机设置：移动端走全屏页，桌面走弹层（同一套数据）
      openPrinterSettings() {
        if (this.isMobile) {
          this.showMobilePrinterSheet()
          return
        }
        this.wifiAddStep = ''
        this.printerSettingsVisible = true
        this.loadPrinters()
        this.loadWifiPrinters()
      },
      closePrinterSettings() {
        this.printerSettingsVisible = false
        this.wifiAddStep = ''
      },
      // 未添加打印机时的提示弹层：文字按钮「添加打印机」→ 打开打印机设置 / 打印设备页面
      showAddPrinterDialog() {
        var self = this
        this.$confirm({
          title: '尚未添加打印机',
          width: 400,
          zIndex: 3100,
          content: '当前还没有添加任何可用打印机，请先添加打印机后再打印。',
          okText: '添加打印机',
          cancelText: '取消',
          onOk: function() {
            self.openPrinterSettings()
          }
        })
      },
      // 网络添加：桌面直接进配置页，移动端先选品牌
      startAddNetwork() {
        this.selectedWifiBrand = null
        this.addWifiForm = { name: '', ip: '', port: DEFAULT_PRINTER_PORT }
        this.wifiAddStep = this.isMobile ? 'brand' : 'config'
        if (!this.isMobile) this.printerSettingsVisible = true
      },
      // 蓝牙添加：切到蓝牙并触发搜索
      handleAddBluetooth() {
        this.printerType = 'bluetooth'
        this.selectedPrinter = undefined
        this.discoveredPrinter = null
        this.handleBluetoothSearch()
      },
      // 本地添加：刷新本机打印机（依赖本地打印助手）
      handleAddLocalPrinter() {
        var self = this
        if (this.isMobile) {
          this.$message.info('请在电脑端使用「本地添加」')
          return
        }
        this.loadPrinters().then(function() {
          if (self.localAgentOnline) {
            self.$message.success('已刷新本机打印机（共 ' + self.localPrinters.length + ' 台）')
          } else {
            self.$message.warning('未检测到本地打印助手，请先下载安装并运行 print-agent.exe')
          }
        })
      },
      // 测试网络打印机连通性
      handleTestAddWifi() {
        var self = this
        var form = this.addWifiForm || {}
        var ip = String(form.ip || '').trim()
        if (!/^(\d{1,3}\.){3}\d{1,3}$/.test(ip)) {
          this.$message.warning('请输入正确的 IP 地址，如 192.168.10.10')
          return
        }
        this.addWifiTesting = true
        testWifiPrinter(ip, Number(form.port) || DEFAULT_PRINTER_PORT).then(function(res) {
          self.addWifiTesting = false
          if (res && res.connected) self.$message.success('连接成功：' + (res.message || ip))
          else self.$message.warning('连接失败：' + ((res && res.message) || '请检查 IP 与端口'))
        }).catch(function() {
          self.addWifiTesting = false
          self.$message.error('测试失败，请检查网络')
        })
      },
      // 列表项是否为当前选中
      isPrinterActive(item) {
        if (item.source === 'wifi') return this.printerType === 'wifi' && this.activeWifiId === item.value
        if (item.source === 'bluetooth') return this.printerType === 'bluetooth' && this.selectedPrinter === item.value
        return this.selectedPrinter === item.value
      },
      // 列表项选中：回写选择并持久化
      handleSelectPrinterItem(item) {
        if (item.source === 'wifi') {
          this.printerType = 'wifi'
          this.selectWifiPrinter(item.printer)
        } else if (item.source === 'bluetooth') {
          this.printerType = 'bluetooth'
          this.selectedPrinter = item.value
          this.discoveredPrinter = this.btDevice || null
        } else if (item.source === 'browser') {
          this.printerType = 'backend'
          this.selectedPrinter = '__browser__'
          this.discoveredPrinter = null
        } else {
          this.printerType = 'backend'
          this.selectedPrinter = item.value
          this.discoveredPrinter = null
        }
        this.persistLastPrinter()
        this.closePrinterSettings()
        if (this.isMobile) this.showPrinterSheet = false
      },
      // 手机端：打开打印机选择弹层
      showMobilePrinterSheet() {
        // 根据当前选中打印机定位到对应分类
        this.deviceTab = this.printerType === 'wifi'
          ? 'network'
          : (this.printerType === 'bluetooth'
            ? 'local'
            : (this.printerType === 'backend' && this.selectedPrinter && this.selectedPrinter.indexOf('agent:') === 0 ? 'cloud' : (this.printerType === 'cloud' ? 'cloud' : 'local')))
        this.loadPrinters()
        if (this.deviceTab === 'network') this.loadWifiPrinters()
        this.cloudAddVisible = false
        this.showPrinterSheet = true
      },
      // 手机端：切换设备分类
      switchDeviceTab(tab) {
        this.deviceTab = tab
        if (tab === 'network') this.loadWifiPrinters()
        if (tab === 'cloud') this.loadAgentPrinters()
      },
      // 云打印：选中 ERP 打印代理设备下的远程打印机
      selectCloudAgentPrinter(p) {
        this.printerType = 'backend'
        this.selectPrinterAndClose(p.key)
      },
      // 网络打印：选中 Wi-Fi 打印机
      selectNetworkPrinter(p) {
        this.printerType = 'wifi'
        this.selectPrinterAndClose(p.id)
      },
      // 本地打印：选中本机打印机
      selectLocalPrinter(p) {
        this.printerType = 'backend'
        this.selectPrinterAndClose(p.name)
      },
      // 本地打印：选中已连接的蓝牙打印机
      selectBluetoothPrinter() {
        this.printerType = 'bluetooth'
        this.selectPrinterAndClose(this.btDevice ? this.btDevice.id : undefined)
      },
      // 关闭云打印配置页，回到设备列表
      closeCloudConfig() {
        this.cloudAddVisible = false
        this.cmdSheetBrand = null
        this.showPrinterSheet = true
      },
      // 云打印：打开配对配置页（配对码 + 链接地址 + 打印机品牌）
      openCloudPrinterManage() {
        this.showPrinterSheet = false
        this.cmdSheetBrand = null
        this.cloudForm = {
          pairCode: '',
          serverUrl: (typeof window !== 'undefined' && window.location) ? window.location.origin : '',
          brand: ''
        }
        this.cloudAddVisible = true
        this.loadAgentPrinters()
      },
      // 云打印配置：选择品牌
      chooseCloudBrand(brand) {
        this.cloudForm.brand = brand.key
      },
      // 云打印配置：调整品牌的信号传输格式
      openCloudCmdSheet(brand) {
        this.cloudForm.brand = brand.key
        this.cmdSheetBrand = brand
      },
      // 链接地址归一化：兼容 ws(s):// 通信地址与裸域名，去掉 /ws/print-agent 后缀
      normalizeCloudBase(url) {
        var u = String(url || '').trim().replace(/\s+/g, '')
        if (!u) return ''
        u = u.replace(/^wss:\/\//i, 'https://').replace(/^ws:\/\//i, 'http://')
        if (!/^https?:\/\//i.test(u)) u = 'https://' + u
        u = u.replace(/\/+$/, '').replace(/\/ws\/print-agent$/i, '')
        return u
      },
      // 云打印配置：用配对码向主机登记云打印机
      saveCloudPrinter() {
        var self = this
        var pairCode = String(this.cloudForm.pairCode || '').trim()
        if (!pairCode) {
          this.$message.warning('请输入配对码')
          return
        }
        var base = this.normalizeCloudBase(this.cloudForm.serverUrl)
        if (!base) {
          this.$message.warning('请输入链接地址')
          return
        }
        var brand = this.selectedCloudBrand
        if (!brand) {
          this.$message.warning('请选择打印机品牌')
          return
        }
        var payload = {
          pairCode: pairCode,
          storeName: '',
          printerModel: brand.name,
          version: 'mobile',
          printers: [{
            id: 'default',
            name: brand.name + ' ' + brand.cmd,
            brand: brand.key,
            cmdSet: brand.cmd,
            host: '',
            port: DEFAULT_PRINTER_PORT
          }]
        }
        this.cloudSaving = true
        fetch(base + '/printAgent/device/pair', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload)
        }).then(function(resp) {
          return resp.json()
        }).then(function(json) {
          self.cloudSaving = false
          var data = (json && json.data) || {}
          if (!json || json.code !== 200 || !data.deviceId) {
            self.$message.error(typeof data === 'string' ? data : (data.message || '配对失败'))
            return
          }
          self.cloudAddVisible = false
          self.deviceTab = 'cloud'
          self.showPrinterSheet = true
          self.loadAgentPrinters().then(function() {
            self.printerType = 'backend'
            self.selectedPrinter = 'agent:' + data.deviceId + ':default'
            self.persistLastPrinter()
          })
          self.$message.success('云打印机已添加')
        }).catch(function() {
          self.cloudSaving = false
          self.$message.error('无法连接链接地址，请检查地址与网络')
        })
      },
      // 手机端：选中打印机后关闭弹层
      selectPrinterAndClose(name) {
        if (this.printerType === 'bluetooth') {
          var dev = this.btDevice
          this.selectedPrinter = dev ? dev.id : undefined
          this.discoveredPrinter = dev || null
        } else if (this.printerType === 'wifi') {
          this.activeWifiId = name
          this.selectedPrinter = name
        } else {
          this.selectedPrinter = name
          this.discoveredPrinter = null
        }
        this.persistLastPrinter()
        this.showPrinterSheet = false
      },
      // 加载本机打印机列表（后端枚举），默认选中默认打印机
      loadPrinters() {
        var self = this
        this.loadingPrinters = true
        // 手机端「后端」页同时展示 ERP 上的远程打印机（打印代理设备）
        if (this.isMobile) this.loadAgentPrinters()
        // 电脑端同时探测本地打印助手，获取本机打印机
        var localTask = this.isMobile
          ? Promise.resolve({ online: false, list: [] })
          : detectLocalAgent().then(function(base) {
            if (!base) return { online: false, list: [] }
            return fetchLocalPrinters().then(function(list) {
              return { online: true, list: list }
            })
          })
        return localTask.then(function(local) {
          self.localPrinters = local.list || []
          // 区分「助手未安装」和「助手在线但没有打印机」
          self.localAgentOnline = !!local.online
          // 电脑端未安装打印组件：后端打印机在服务器上，本机用不了，不再加载打印机
          if (!self.isMobile && !self.localAgentOnline) {
            self.printers = []
            self.loadingPrinters = false
            if (self.printerType === 'backend') self.selectedPrinter = undefined
            return
          }
          return getAction('/print/printers').catch(function() { return null }).then(function(res) {
            var data = (res && res.data) || {}
            var list = data.printers || []
            self.loadingPrinters = false
            self.printers = list
            // 非后端类型（Wi-Fi / 蓝牙 / 云）的选中项不受枚举结果影响
            if (self.printerType !== 'backend') return
            // 已有有效选择则保留（实现「回显设置页选中的打印机」）
            var keep = self.selectedPrinter
            if (keep === '__browser__') return
            // 远程打印机（agent:xxx）由 loadAgentPrinters 校验：它异步加载，此处不能按
            // 后端打印机列表判断，否则上次选中的远程打印机在页面加载时会被默认打印机覆盖
            if (keep && String(keep).indexOf('agent:') === 0) return
            if (keep && (isLocalPrinter(keep)
              ? self.localPrinters.indexOf(localPrinterName(keep)) >= 0
              : list.some(function(p) { return p.name === keep }))) {
              return
            }
            if (self.localPrinters.length > 0) {
              // 优先使用本机打印机（直接调用本地电脑打印机，无打印对话框）
              self.selectedPrinter = LOCAL_PRINTER_PREFIX + self.localPrinters[0]
            } else if (list.length > 0) {
              var def = list.find(function(p) { return p.isDefault })
              self.selectedPrinter = (def || list[0]).name
            } else {
              // 后端枚举不到打印机且本机助手未运行（远程访问场景）：
              // 不自动选中浏览器打印，保持未选择，点击打印时引导安装本地打印助手
              self.selectedPrinter = undefined
            }
          })
        })
      },
      // 加载远程打印机（ERP 打印代理设备），供「后端」页展示与选择
      loadAgentPrinters() {
        var self = this
        this.loadingAgents = true
        return getPrintAgentList().then(function(res) {
          self.loadingAgents = false
          var data = (res && res.data) || []
          if (!Array.isArray(data)) data = []
          self.agentDevices = data
          // 已选中的远程打印机失效时清空，避免指向不存在的设备/打印机
          var selected = self.selectedPrinter
          if (selected && String(selected).indexOf('agent:') === 0) {
            var exists = self.agentPrinterItems.some(function(p) { return p.key === selected })
            if (!exists) self.selectedPrinter = undefined
          }
        }).catch(function() {
          self.loadingAgents = false
        })
      },
      // 远程打印：前端渲染整张标签，后端生成 TSPL 指令经打印代理下发
      printViaAgentPrinter(agentPrinter) {
        var self = this
        var items
        var rendering
        // 渲染阶段为同步逻辑，异常时给出提示并复位按钮状态
        try {
          items = this.buildLayoutItems()
          this.printing = true
          rendering = this.renderLabelToPng()
        } catch (err) {
          this.printing = false
          this.msgError('生成打印数据失败：' + ((err && err.message) || err))
          return Promise.resolve(false)
        }
        return rendering.then(function(image) {
          return printViaAgent({
            deviceId: agentPrinter.deviceId,
            printerId: agentPrinter.printerId,
            copies: self.copies,
            width: self.labelWidth,
            height: self.labelHeight,
            image: image,
            items: items
          })
        }).then(function(res) {
          self.printing = false
          var data = (res && res.data) || {}
          if (res && res.code === 200) {
            self.msgSuccess('已发送到远程打印机：' + agentPrinter.printerName)
            self.notifyPrintSuccess()
            return true
          }
          self.msgError((typeof data === 'string' ? data : data.message) || '远程打印失败')
          return false
        }).catch(function(err) {
          self.printing = false
          self.msgError((err && err.message) || '远程打印失败，请检查打印代理是否在线')
          return false
        })
      },
      // 打开打印机配置页（仅远程打印机卡片可用）
      openPrinterEdit(p) {
        this.printerEditDeviceId = p.deviceId
        this.printerEditPrinterId = p.printerId
        this.printerEditVisible = true
      },
      // 配置页保存成功后刷新远程打印机列表
      handlePrinterSaved() {
        this.printerEditVisible = false
        this.loadAgentPrinters()
      },
      // 蓝牙打印机搜索
      handleBluetoothSearch() {
        var self = this
        this.btSearching = true
        searchBluetoothPrinter().then(function(device) {
          self.btSearching = false
          self.btDevice = device
          self.selectDiscoveredPrinter(device)
        }).catch(function(err) {
          self.btSearching = false
          if (err && err.message && err.message.indexOf('取消') === -1) {
            self.$message.warning(err.message || '蓝牙搜索失败')
          }
        })
      },
      // 读取本地已配置的 Wi-Fi 打印机，并自动选中第一台
      loadWifiPrinters() {
        var self = this
        this.wifiPrinters = getWifiPrinters()
        if (this.printerType !== 'wifi') return
        var exists = this.wifiPrinters.some(function(p) { return p.id === self.activeWifiId })
        if (!exists) {
          this.activeWifiId = null
          this.selectedPrinter = undefined
        }
        if (this.wifiPrinters.length > 0 && !this.activeWifiId) {
          this.selectWifiPrinter(this.wifiPrinters[0])
        }
      },
      // 选中一台 Wi-Fi 打印机（同时测试连通性）
      selectWifiPrinter(printer) {
        var self = this
        this.activeWifiId = printer.id
        this.selectedPrinter = printer.id
        this.discoveredPrinter = null
        this.testWifiPrinterStatus(printer)
      },
      // 测试 Wi-Fi 打印机连通性（后端 TCP 探测）
      testWifiPrinterStatus(printer) {
        var self = this
        testWifiPrinter(printer.ip, printer.port).then(function(res) {
          self.$set(self.wifiStatus, printer.id, res.connected)
        }).catch(function() {
          self.$set(self.wifiStatus, printer.id, false)
        })
      },
      // 打开「添加打印机」：手机端进入选择品牌页，桌面端进入网络配置页
      openAddWifiPrinter() {
        if (this.isMobile) {
          this.selectedWifiBrand = null
          this.cmdSheetBrand = null
          this.wifiAddStep = 'brand'
        } else {
          this.startAddNetwork()
        }
      },
      // 选择品牌页：打开信号传输格式底部弹层
      openCmdSheet(brand) {
        this.cmdSheetBrand = brand
      },
      // 选择品牌页：确认信号传输格式
      confirmCmdSheet(cmd) {
        if (this.cmdSheetBrand) this.cmdSheetBrand.cmd = cmd
        this.cmdSheetBrand = null
      },
      // 已保存打印机：打开指令集切换
      openPrinterCmdSheet(printer) {
        this.cmdSheetPrinter = printer
      },
      // 已保存打印机：切换指令集并持久化
      confirmPrinterCmdSheet(cmd) {
        var printer = this.cmdSheetPrinter
        this.cmdSheetPrinter = null
        if (!printer) return
        this.wifiPrinters = saveWifiPrinter({
          name: printer.name,
          ip: printer.ip,
          port: printer.port,
          brand: printer.brand,
          cmdSet: cmd
        })
        this.$message.success('指令集已切换为 ' + cmd)
      },
      // 选择品牌页：选中品牌，进入 Wi-Fi 配置页
      selectWifiBrand(brand) {
        this.selectedWifiBrand = brand
        this.addWifiForm = { name: brand.name, ip: '', port: DEFAULT_PRINTER_PORT }
        this.wifiAddStep = 'config'
      },
      // 保存打印机配置：名称 + IP + 端口（手机端附加品牌/传输格式）
      handleSaveWifiPrinter() {
        var self = this
        var form = this.addWifiForm || {}
        var ip = String(form.ip || '').trim()
        if (!/^(\d{1,3}\.){3}\d{1,3}$/.test(ip)) {
          this.$message.warning('请输入正确的 IP 地址，如 192.168.10.10')
          return
        }
        var port = Number(form.port) || DEFAULT_PRINTER_PORT
        var brand = this.selectedWifiBrand
        this.addWifiTesting = true
        this.wifiPrinters = saveWifiPrinter({
          name: form.name,
          ip: ip,
          port: port,
          brand: brand ? brand.name : '',
          cmdSet: brand ? brand.cmd : 'TSPL'
        })
        this.addWifiTesting = false
        this.wifiAddStep = ''
        this.selectedWifiBrand = null
        var saved = this.wifiPrinters.find(function(p) { return p.ip === ip && p.port === port })
        if (saved) {
          this.printerType = 'wifi'
          this.selectWifiPrinter(saved)
          this.persistLastPrinter()
        }
        this.$message.success('打印机已保存')
      },
      // 删除一台 Wi-Fi 打印机
      handleRemoveWifiPrinter(printer) {
        var self = this
        this.$confirm({
          title: '删除打印机',
          content: '确定删除「' + printer.name + '」？',
          okText: '删除',
          cancelText: '取消',
          onOk: function() {
            self.wifiPrinters = removeWifiPrinter(printer.id)
            self.$delete(self.wifiStatus, printer.id)
            if (self.activeWifiId === printer.id) {
              self.activeWifiId = null
              self.selectedPrinter = undefined
              self.persistLastPrinter()
            }
            self.$message.success('已删除')
          }
        })
      },
      // 选中已发现的打印机
      selectDiscoveredPrinter(printer) {
        this.discoveredPrinter = printer
        this.selectedPrinter = printer.id
      },
      handleCancel() {
        // 关闭前复位所有子页面状态，避免「关掉又出现」
        this.showPrinterSheet = false
        this.wifiAddStep = ''
        this.printerSettingsVisible = false
        this.printerEditVisible = false
        this.templateLoadError = ''
        this.visible = false
      },
      /** 更多模板：加载可切换的模板（商品标签） */
      loadTemplateChoices() {
        var self = this
        if (this.type !== 'label') return Promise.resolve([])
        return fetchTemplateList().then(function(list) {
          var choices = (list || []).filter(function(t) {
            return t && !t.outdated
          })
          self.templateChoices = choices
          var exists = choices.some(function(t) {
            return String(t.serverId || t.id) === String(self.currentTemplateId)
          })
          if (!exists) self.currentTemplateId = ''
          if (!self.currentTemplateId && self.template) {
            var hit = choices.find(function(t) { return t.name === self.template.name })
            if (hit) {
              self.currentTemplateId = String(hit.serverId || hit.id)
              self.rememberTemplateId(self.currentTemplateId)
            }
          }
          if (!self.template && !self.outdatedTemplate && choices.length) {
            self.switchTemplate(choices[0])
          }
          return choices
        }).catch(function() { return [] })
      },
      rememberTemplateId(id) {
        if (!id) return
        try { localStorage.setItem('jsh.lastLabelTemplateId', String(id)) } catch (e) { /* 忽略 */ }
      },
      /** 切换预览模板：重新读取并应用，记住本次选择 */
      switchTemplate(tpl) {
        if (!tpl || this.templateLoading) return
        var self = this
        var id = tpl.serverId || tpl.id
        if (!id || String(id) === String(this.currentTemplateId)) return
        this.templateLoading = true
        this.loadRouteTemplate({ type: this.type, templateId: id, name: tpl.name }).then(function(raw) {
          self.templateLoading = false
          var norm = self.normalizeTemplate(raw || tpl)
          if (!norm) {
            self.$message.warning('模板加载失败，请稍后重试')
            return
          }
          self.allAttrs = self.buildAttrList()
          self.applyTemplate(norm, true)
          self.restoreMissingAttrs(self.selectedAttrs)
          self.applySnapshotValues()
          self.currentTemplateId = String(id)
          self.rememberTemplateId(id)
          self.$nextTick(function() { self.updatePreviewScale() })
        }).catch(function() {
          self.templateLoading = false
          self.$message.warning('模板加载失败，请稍后重试')
        })
      },
      // 空状态「重新加载」：重新读取模板并应用
      reloadRouteTemplate() {
        var self = this
        this.templateLoadError = ''
        this.outdatedTemplate = false
        this.outdatedTemplateInfo = null
        // 商品标签：按当前商品/品类重新匹配模板
        if (this.type === 'label' && this.record && this.record.id) {
          this.loadTemplate().then(function(tpl) {
            if (!tpl) {
              if (!self.templateLoadError && !self.outdatedTemplate) self.templateLoadError = '未读取到模板，请稍后重试'
              return
            }
            self.$message.success('模板已加载')
          })
          return
        }
        this.loadRouteTemplate({ type: this.type }).then(function(raw) {
          var tpl = self.normalizeTemplate(raw)
          if (!tpl) {
            if (!self.templateLoadError) self.templateLoadError = '未读取到模板，请稍后重试'
            return
          }
          self.applyTemplate(tpl, true)
          self.restoreMissingAttrs(self.selectedAttrs)
          self.applySnapshotValues()
          self.$message.success('模板已加载')
        })
      },
      // 模板优先级：当前商品的普通模板 > 商品所属类目的默认模板（逐级向上）> 全部类型模板
      loadTemplate() {
        var self = this
        this.templateLoading = true
        return Promise.all([
          fetchTemplateList(),
          // 类目树只用于把商品的末级类目回溯到上级（模板按一级类目绑定）
          queryMaterialCategoryTreeList({ id: '' }).catch(function() { return [] })
        ]).then(function(results) {
          self.templateLoading = false
          var list = results[0] || []
          var tree = Array.isArray(results[1]) ? results[1] : []
          var record = self.record || {}
          var usable = list.filter(function(t) { return t && !t.outdated })
          var normal = usable.find(function(t) {
            return t.type === 'normal' && record.id != null && String(t.materialId) === String(record.id)
          })
          // 从商品自身类目向上找，最近的一级优先
          var chain = self.collectCategoryChain(tree, record.categoryId)
          var categoryTpl = null
          for (var i = 0; i < chain.length && !categoryTpl; i++) {
            categoryTpl = usable.find(function(t) {
              return t.type === 'default' && t.categoryId && String(t.categoryId) === chain[i]
            }) || null
          }
          // 未绑定类目的默认模板 = 全部类型
          var globalTpl = usable.find(function(t) { return t.type === 'default' && !t.categoryId }) || null
          var tpl = normal || categoryTpl || globalTpl || null
          // 没匹配到可用模板时，只提示旧模板（旧结构不读取、不用其它模板顶替）
          var outdated = list.filter(function(t) { return t && t.outdated })
          if (!tpl && outdated.length) {
            self.outdatedTemplate = true
            self.confirmDeleteOutdated(outdated)
          }
          var norm = tpl ? self.normalizeTemplate(tpl) : null
          self.applyTemplate(norm)
          return norm
        }).catch(function(err) {
          self.templateLoading = false
          self.templateLoadError = (err && (err.message || err.msg)) || '模板读取失败'
          return null
        })
      },
      // 类目链：从商品自身类目到根，用于模板按上级类目匹配；找不到时按自身类目兜底
      collectCategoryChain(tree, categoryId) {
        if (!categoryId) return []
        var target = String(categoryId)
        var chain = []
        var walk = function(nodes, trail) {
          if (!nodes || chain.length) return
          for (var i = 0; i < nodes.length; i++) {
            var node = nodes[i]
            var nodeId = String(node.value != null ? node.value : node.id)
            var next = trail.concat(nodeId)
            if (nodeId === target) {
              chain = next.slice().reverse()
              return
            }
            if (node.children && node.children.length) {
              walk(node.children, next)
              if (chain.length) return
            }
          }
        }
        walk(tree || [], [])
        return chain.length ? chain : [target]
      },
      // 旧模板不再兼容：只提示是否删除
      confirmDeleteOutdated(list) {
        var self = this
        this.$confirm({
          title: '有旧模板',
          content: '检测到 ' + list.length + ' 个旧版模板已不支持，是否删除？',
          okText: '删除',
          cancelText: '取消',
          onOk: function() {
            var tasks = list.map(function(t) { return deleteTemplate(null, t) })
            return Promise.all(tasks).then(function() {
              self.$message.success('旧模板已删除')
              self.outdatedTemplate = false
              self.templateLoadError = ''
              return self.loadTemplate()
            }).catch(function() {
              self.$message.error('删除失败，请稍后重试')
            })
          }
        })
      },
      // platformConfig 单 key 旧模板：确认后删除并重新加载预览
      deleteOutdatedTemplate() {
        var self = this
        var info = this.outdatedTemplateInfo
        if (!info) return
        this.$confirm({
          title: '有旧模板',
          content: '模板「' + (info.name || '该模板') + '」为旧结构，已不支持，是否删除？',
          okText: '删除',
          cancelText: '取消',
          onOk: function() {
            return Promise.resolve(deleteTemplate(null, info)).then(function() {
              self.$message.success('旧模板已删除')
              // 删除后回到空状态，重新进入编辑即可新建模板
              self.template = null
              self.outdatedTemplate = false
              self.outdatedTemplateInfo = null
              self.templateLoadError = ''
            }).catch(function() {
              self.$message.error('删除失败，请稍后重试')
            })
          }
        })
      },
      // 路由页：按类型从统一模板流程读取（label 支持按 templateId/name 定位）
      // 会员标签 / 地址打印 / 小票各自独立的 platformConfig key，互不复用
      loadRouteTemplate(q) {
        var self = this
        if (this.type === 'delivery') {
          return resolveTemplate({ storage: 'deliveryTemplate' }).then(function(res) {
            return res.template || null
          })
        }
        var configs = {
          member: { key: 'member_layout', name: '会员标签模板' },
          address: { key: 'address_layout', name: '地址打印模板' },
          receipt: { key: 'receipt_layout', name: '小票模板' }
        }
        var cfgInfo = configs[this.type]
        if (cfgInfo) {
          return resolveTemplate({
            storage: 'platformConfig',
            platformConfigKey: cfgInfo.key,
            title: cfgInfo.name.replace('模板', '')
          }, { useCache: true }).then(function(res) {
            if (res.error) {
              self.templateLoadError = res.error
              console.warn('[LabelPrintModal] 模板加载失败', cfgInfo.key, res.error)
            }
            if (res.outdated && res.outdated.length) {
              self.outdatedTemplate = true
              self.outdatedTemplateInfo = res.outdated[0]
            }
            return res.template || null
          })
        }
        return resolveTemplate({ storage: 'localTemplates' }, { templateId: q && q.templateId }).then(function(res) {
          var tpl = res.template
          if (!tpl && q && q.name && Array.isArray(res.list)) {
            tpl = res.list.find(function(t) { return t.name === q.name }) || null
            if (tpl && tpl.outdated) {
              self.outdatedTemplate = true
              return null
            }
          }
          if (res.outdated && res.outdated.length) self.outdatedTemplate = true
          return tpl || null
        }).catch(function() { return null })
      },
      // 模板结构标准化：只接受 v2（唯一存储格式）
      // 统一转换成本页预览使用的编辑器结构，保证打印预览与编辑器显示一致
      normalizeTemplate(raw) {
        var names = { label: '标签模板', delivery: '发货单模板', receipt: '小票模板', member: '会员标签模板', address: '地址打印模板' }
        var src = parseTemplateRaw(raw)
        if (!src || src.outdated || !isLabelTemplateV2(src)) return null
        var base = v2ToEditorState(src)
        var schema = v2ToSchemaView(src)
        var selectedAttrs = Array.isArray(base && base.selectedAttrs) ? base.selectedAttrs : []
        selectedAttrs = selectedAttrs
          .filter(function(key) { return key != null && key !== '' })
          .map(function(key) { return String(key) })
        // 没有任何可渲染字段 → 视为无模板（走空状态/快照兜底）
        if (!selectedAttrs.length) return null
        var size = (schema && schema.size) || {}
        var defaults = { label: [320, 240], delivery: [560, 800], receipt: [302, 600], member: [320, 240], address: [320, 240] }
        var fallback = defaults[this.type] || defaults.label
        var width = size.width ? mmToPx(size.width) : (base.labelWidth || base.width)
        var height = size.height ? mmToPx(size.height) : (base.labelHeight || base.height)
        var styles = base.attrStyles && typeof base.attrStyles === 'object' ? base.attrStyles : {}
        var labels = base.attrLabels && typeof base.attrLabels === 'object' && !Array.isArray(base.attrLabels) ? base.attrLabels : {}
        return {
          id: src.id || src.serverId || ('storage_' + this.type),
          serverId: src.serverId || null,
          name: src.name || (schema && schema.name) || names[this.type] || '模板',
          businessType: src.businessType || (schema && schema.businessType) || '',
          provider: base.provider || (schema && schema.dataSource && schema.dataSource.provider) || '',
          selectedAttrs: selectedAttrs,
          attrStyles: styles,
          attrLabels: labels,
          customLabels: Array.isArray(base.customLabels) ? base.customLabels : [],
          labelWidth: width || fallback[0],
          labelHeight: height || fallback[1],
          width: width || fallback[0],
          height: height || fallback[1],
          goodsFields: Array.isArray(base.goodsFields) ? base.goodsFields : ((src && src.goodsFields) || null),
          overflowMode: (schema && schema.overflowMode) || base.overflowMode || src.overflowMode || 'wrap',
          pageMode: base.pageMode || 'none',
          printListMode: base.printListMode || null,
          // 参与多行/分页打印的元素（打印设置里勾选）
          flowElementKeys: Array.isArray(base.flowElementKeys) ? base.flowElementKeys : null,
          // 明细集合取值路径：由模板约定，预览按它读取真实条数
          collectionPath: base.collectionPath || (isLabelTemplateV2(src) ? getCollectionPath(src) : DEFAULT_ITEM_PATH),
          recordMode: base.recordMode || 'oneLabelPerRecord',
          recordsPerPage: Math.max(1, parseInt(base.recordsPerPage, 10) || 1),
          maxRowsPerPage: Math.max(0, parseInt(base.maxRowsPerPage, 10) || 0),
          repeatHeader: base.repeatHeader !== false,
          // 编辑页关闭的规格名/规格值，预览与打印时保持隐藏
          hiddenItemKeys: Array.isArray(src.hiddenItemKeys) ? src.hiddenItemKeys : (Array.isArray(base.hiddenItemKeys) ? base.hiddenItemKeys : []),
          // 规格块结构（属性名 + 属性值名），回传给编辑页按模板恢复
          specBlocks: Array.isArray(base.specBlocks) ? base.specBlocks : [],
          // 规格数据映射表（唯一权威，按类别分组），回传给编辑页恢复选择
          specMapping: Array.isArray(base.specMapping) ? base.specMapping : [],
          // 模板已有映射表时预览/打印只加载已选规格；无映射表沿用规格块（旧路径）
          specMappingReady: base.specMappingReady === true,
          // 编辑面板字段结构单独保存；预览/打印渲染仍只读 layout.elements。
          editorData: base.editorData || (src && src.editorData) || null,
          schema: schema
        }
      },
      // 路由页兜底：存储里没有模板时，用编辑页快照还原预览
      applySnapshot() {
        var tpl = this.normalizeTemplate(readLabelEditSnapshot())
        if (!tpl) return
        this.applyTemplate(tpl, true)
        this.applySnapshotValues()
      },
      // 快照里的业务值：模板结构标准化完成后按字段 key 回填，保证预览读的是统一结构
      applySnapshotValues() {
        var s = readLabelEditSnapshot()
        if (!s || !s.values || typeof s.values !== 'object') return
        var self = this
        var exists = {}
        this.allAttrs.forEach(function(attr) { exists[attr.key] = true })
        Object.keys(s.values).forEach(function(key) {
          // 商品明细行数组不是普通字段，只通过 detailRows 参与明细表排版
          if (key === 'goodsRows') return
          if (exists[key]) {
            var attr = self.allAttrs.find(function(a) { return a.key === key })
            if (attr) attr.value = s.values[key]
            return
          }
          self.allAttrs.push({ key: key, label: self.attrLabels[key] || '', value: s.values[key], isCustom: true, group: '自定义' })
          exists[key] = true
        })
        if (Array.isArray(s.rows)) this.detailRows = s.rows
        this.hiddenReceiptKeys = Array.isArray(s.hiddenItemKeys) ? s.hiddenItemKeys : []
        // 小票：商品明细按单品拆成独立元素（预览与打印同一套布局）
        this.applyReceiptItemElements()
        // 地址打印：历史模板缺失品类规格结构时按商品行类目补回
        this.ensureSpecBlocks()
        // 类别字段统一约定：快照未携带时按明细行类目解析一级类目名回填
        this.ensureCategoryValues()
      },
      // 类别属性取值约定：取一级类目，一级类目下的二级子类目都引用一级类目的配置读映射表。
      // 快照 values 未携带 label_category 时，按明细行 categoryId 拉类目树解析一级类目名回填。
      ensureCategoryValues() {
        var self = this
        var attr = this.allAttrs.find(function(a) { return a.key === LABEL_CATEGORY_KEY })
        if (!attr) return
        var value = attr.value == null ? '' : String(attr.value)
        // 已有真实值（快照回填/映射器注入）时不再覆盖；「类别」为字段占位
        if (value && value !== '类别') return
        var rows = Array.isArray(this.detailRows) ? this.detailRows : []
        var categoryId = null
        rows.some(function(row) {
          var id = resolveRowCategoryId(row)
          if (id != null) {
            categoryId = id
            return true
          }
          return false
        })
        if (categoryId == null) return
        queryMaterialCategoryTreeList({ id: '' }).then(function(tree) {
          var name = resolvePrimaryCategoryName(tree, categoryId)
          if (!name) return
          var target = self.allAttrs.find(function(a) { return a.key === LABEL_CATEGORY_KEY })
          if (!target) return
          var cur = target.value == null ? '' : String(target.value)
          if (!cur || cur === '类别') target.value = name
        }).catch(function() { /* 类目解析失败保持占位 */ })
      },
      /**
       * 通用兼容：类别/规格归一为 label_category / label_spec 后，模板 specBlocks 可能为空，
       * 打印时按商品行所属一级类目补回规格结构，保证「规格」按映射表取值。
       */
      ensureSpecBlocks() {
        var self = this
        if (['label', 'address', 'delivery', 'receipt'].indexOf(this.type) === -1) return
        var tpl = this.template
        // 已建立数据映射表的模板（即使映射为空）以模板选择为准，不再自动补回规格
        if (!tpl || tpl.specMappingReady === true) return
        var ids = []
        ;(this.detailRows || []).forEach(function(row) {
          var id = resolveRowCategoryId(row)
          if (id != null && ids.map(String).indexOf(String(id)) === -1) ids.push(id)
        })
        if (!ids.length) return
        var blocks = Array.isArray(tpl.specBlocks) ? tpl.specBlocks : []
        // 行类目已被规格块覆盖（一级类目或其成员）时不需要补回；
        // 模板规格块缺失或只覆盖部分商品类目时，按行类目重建，避免多商品取不到规格
        var covered = function(id) {
          return blocks.some(function(block) {
            if (!block || block.categoryId == null) return false
            if (String(block.categoryId) === String(id)) return true
            return Array.isArray(block.categoryMemberIds) &&
              block.categoryMemberIds.map(String).indexOf(String(id)) > -1
          })
        }
        var missing = ids.filter(function(id) { return !covered(id) })
        if (!missing.length) return
        queryMaterialCategoryTreeList({ id: '' }).then(function(tree) {
          var next = blocks.slice()
          ids.forEach(function(id) {
            var chain = self.collectCategoryChain(tree, id)
            var rootId = chain.length ? chain[chain.length - 1] : ''
            if (!rootId) return
            var node = self.categoryNode(tree, rootId)
            var name = self.categoryNodeName(tree, rootId)
            if (!name) return
            // 按类目保存的规格配置重建该类目的规格块（保留规格名，值由商品行提供）
            var specs = buildSpecBlocksFromCategory(node && node.specConfig)
            if (!specs.length) specs = [{ name: '', values: [] }]
            // 先移除该类目的旧规格块，再按配置顺序写入，避免旧结构缺 memberIds 导致匹配失败
            next = next.filter(function(block) {
              return String(block.categoryId) !== String(rootId)
            })
            specs.forEach(function(spec, index) {
              next.push({
                categoryId: rootId,
                categoryName: name,
                categoryMemberIds: chain,
                blockNo: index + 1,
                name: spec.name,
                values: spec.values,
                sourceValues: spec.values
              })
            })
          })
          if (next.length) self.$set(self.template, 'specBlocks', next)
        }).catch(function() { /* 类目读取失败保持原样 */ })
      },
      /** 类目树节点（用于读取 specConfig） */
      categoryNode(nodes, id) {
        var target = String(id)
        var walk = function(list) {
          if (!Array.isArray(list)) return null
          for (var i = 0; i < list.length; i++) {
            var node = list[i] || {}
            var nodeId = String(node.value != null ? node.value : node.id)
            if (nodeId === target) return node
            var found = walk(node.children)
            if (found) return found
          }
          return null
        }
        return walk(nodes)
      },
      /** 类目树节点名称 */
      categoryNodeName(nodes, id) {
        var target = String(id)
        var walk = function(list) {
          if (!Array.isArray(list)) return ''
          for (var i = 0; i < list.length; i++) {
            var node = list[i] || {}
            var nodeId = String(node.value != null ? node.value : node.id)
            if (nodeId === target) return node.name || node.label || node.title || ''
            var found = walk(node.children)
            if (found) return found
          }
          return ''
        }
        return walk(nodes)
      },
      /**
       * 小票预览/打印：把明细行拆成「每件商品 × 每个字段」的独立元素。
       * 拆分/隐藏过滤/schema 重建统一走共享链路 applyReceiptRowsToState（与编辑页同一入口），
       * 预览页只负责准备数据来源（真实明细行与模板配置）
       */
      applyReceiptItemElements() {
        if (this.type !== 'receipt' || this.receiptItemsApplied) return
        if (!Array.isArray(this.detailRows) || !this.detailRows.length) return
        var hidden = (this.hiddenReceiptKeys && this.hiddenReceiptKeys.length)
          ? this.hiddenReceiptKeys
          : ((this.template && this.template.hiddenItemKeys) || [])
        var next = applyReceiptRowsToState({
          rows: this.detailRows,
          specBlocks: this.template && this.template.specBlocks,
          specMappingReady: !!(this.template && this.template.specMappingReady),
          compactHidden: true,
          hiddenKeys: hidden,
          selectedAttrs: this.selectedAttrs,
          attrStyles: this.attrStyles,
          attrLabels: this.attrLabels,
          allAttrs: this.allAttrs,
          paperWidth: this.labelWidth,
          labelHeight: this.labelHeight,
          overflowMode: this.overflowMode,
          template: this.template
        })
        this.allAttrs = next.allAttrs
        this.selectedAttrs = next.selectedAttrs
        this.attrStyles = next.attrStyles
        this.attrLabels = next.attrLabels
        // 模板带 schema 时预览按 schema 渲染，共享链路里同步重建，单品元素才会显示
        if (next.schema) {
          this.template = Object.assign({}, this.template, { schema: next.schema })
        }
        this.receiptItemsApplied = true
      },
      applyTemplate(tpl, skipProvider) {
        var self = this
        this.template = tpl
        this.selectedAttrs = []
        this.attrStyles = {}
        this.attrLabels = {}
        this.detailRows = []
        this.receiptItemsApplied = false
        this.previewPage = 0
        this.printPage = 0
        // 商品组合字段：模板未配置（旧模板）时为空 = 显示全部
        this.goodsFields = tpl && Array.isArray(tpl.goodsFields) ? tpl.goodsFields.slice() : null
        if (!tpl) return
        // 自定义标签的值保存在模板里，需要合并回属性列表
        if (tpl.customLabels && tpl.customLabels.length > 0) {
          tpl.customLabels.forEach(function(custom) {
            self.allAttrs.push(JSON.parse(JSON.stringify(custom)))
          })
        }
        this.selectedAttrs = JSON.parse(JSON.stringify(tpl.selectedAttrs || []))
        this.selectedAttrs.forEach(function(key) {
          var style = tpl.attrStyles && tpl.attrStyles[key]
          var idx = self.selectedAttrs.indexOf(key)
          if (style) {
            var copy = JSON.parse(JSON.stringify(style))
            // 与标签编辑页一致：旧模板没有字间距/行距时补默认值
            if (copy.letterSpacing == null) copy.letterSpacing = 0
            if (copy.lineHeight == null) copy.lineHeight = 1.3
            // 旧模板默认写死的宽高视为自动，避免多行规格被裁掉
            if (copy.width === 120 && copy.height === 24) {
              delete copy.width
              delete copy.height
            }
            self.$set(self.attrStyles, key, copy)
            // 恢复该属性对应的独立编码元素
            var codeKey = CODE_KEY_PREFIX + key
            if (tpl.attrStyles[codeKey]) {
              self.$set(self.attrStyles, codeKey, JSON.parse(JSON.stringify(tpl.attrStyles[codeKey])))
            }
          } else {
            self.$set(self.attrStyles, key, { fontSize: 14, x: 10, y: 10 + idx * 30, fontWeight: 'normal', letterSpacing: 0, lineHeight: 1.3 })
          }
          // 模板里存在该属性名的记录就按记录恢复（含空字符串，留空表示只显示值）
          if (tpl.attrLabels && Object.prototype.hasOwnProperty.call(tpl.attrLabels, key)) {
            self.$set(self.attrLabels, key, tpl.attrLabels[key])
          } else {
            var attr = self.allAttrs.find(function(a) { return a.key === key })
            self.$set(self.attrLabels, key, attr ? attr.label : '')
          }
        })
        // 分页标签只显示页码本身，不加“属性名:”前缀
        if (this.selectedAttrs.indexOf('page_no') >= 0) {
          this.$set(this.attrLabels, 'page_no', '')
        }
        this.labelWidth = tpl.labelWidth || 320
        this.labelHeight = tpl.labelHeight || 240
        // 旧模板（文字与条码合在一个元素里）自动拆分为独立编码元素
        this.attrStyles = migrateCodeStyles(this.attrStyles, this.allAttrs, this.attrLabels)
        // 模板绑定了业务数据源：走统一适配器取数，预览与打印共用同一份数据
        if (!skipProvider) this.applyProviderData(tpl)
      },
      // 按模板数据源加载统一数据（不直接访问业务数据库）
      applyProviderData(tpl) {
        var self = this
        var provider = tpl && tpl.provider
        if (!provider) return
        resolveTemplateData({
          provider: provider,
          documentId: this.record.id,
          subId: this.sku && (this.sku.id || this.sku.skuId),
          businessType: this.type,
          businessMode: this.type,
          record: this.record,
          sku: this.sku,
          rows: (this.detailRows && this.detailRows.length)
            ? this.detailRows
            : ((this.record && this.record.goodsRows) || [])
        }).then(function(result) {
          if (!self.template || self.template.id !== tpl.id) return
          var attrs = (result.attrs || []).map(function(attr) {
            return { key: attr.key, label: attr.label, value: attr.value, type: attr.type, isCustom: false }
          })
          var exists = {}
          attrs.forEach(function(attr) { exists[attr.key] = true })
          self.allAttrs.forEach(function(attr) {
            if (attr.isCustom && !exists[attr.key]) attrs.push(attr)
          })
          self.allAttrs = attrs
          // 明细数组：供新版模板 table 元素循环渲染
          self.detailRows = result.rows || []
          // 模板未显式设置标签名的字段，按数据源字段名补上
          attrs.forEach(function(attr) {
            if (self.attrLabels[attr.key] === undefined) {
              self.$set(self.attrLabels, attr.key, attr.label)
            }
          })
          // 地址打印：历史模板缺失品类规格结构时按商品行类目补回
          self.ensureSpecBlocks()
        })
      },
      // 用指定商品行计算布局（不依赖当前预览页，供分页与打印复用）
      layoutWithRows(rows, values, pageIdx) {
        var layoutValues = values || this.attrValues
        var groups = this.multiGoodsGroups(rows)
        if (groups) {
          // 单页多商品：名称/类别/规格改由逐商品行组输出，模板原元素留空不参与排版
          layoutValues = Object.assign({}, layoutValues, { name: '' })
          layoutValues[LABEL_CATEGORY_KEY] = ''
          layoutValues[LABEL_SPEC_KEY] = ''
        } else if (this.type === 'address' && this.selectedAttrs.indexOf(GOODS_KEY) === -1 &&
          Array.isArray(rows) && rows.length) {
          // 分页打印（每页一行商品）时，名称必须取本页商品，不能沿用表单里第一个商品
          var pageNames = rows.map(function(row) {
            return row && row.name != null ? String(row.name).trim() : ''
          }).filter(function(name) { return !!name })
          if (pageNames.length) {
            layoutValues = Object.assign({}, layoutValues, { name: pageNames.join('\n') })
          }
        }
        // 单页多商品行组依赖完整行（raw.categoryId/specJson）匹配映射表，
        // resolveLabelLayout 返回的 goodsRows 已被 normalizeGoodsRows 精简为
        // {name,spec1..3}，会丢失类别/规格匹配依据，这里用原始行覆盖
        // 分页打印第 2 页起：只排版勾选的元素（分页标签固定打印），按模板布局整体顺移
        var contPlan = this.pagedContinuationPlanFor(pageIdx)
        var layout = resolveLabelLayout({
          selectedAttrs: contPlan ? contPlan.keys : this.selectedAttrs,
          attrStyles: contPlan ? contPlan.styles : this.attrStyles,
          values: layoutValues,
          labels: this.attrLabels,
          goodsRows: rows,
          // 规格块按位置取值：模板规格块 N 取商品规格块 N 的值
          specBlocks: groups ? [] : (this.template && this.template.specBlocks),
          // 模板已建立数据映射表：只有选中的规格值才加载业务数据
          specMappingReady: !!(this.template && this.template.specMappingReady),
          paperWidth: this.labelWidth,
          goodsLabel: this.attrLabels[GOODS_KEY] || '',
          goodsFields: this.goodsFields,
          mode: this.overflowMode
        })
        if (Array.isArray(rows) && rows.length) layout.goodsRows = rows
        return layout
      },
      // 分页打印续页计划：第 2 页起仅排版勾选元素 + 分页标签，
      // 选中的元素块按模板布局整体上移到顶部（顺移），分页标签保持原位每页打印
      pagedContinuationPlanFor(pageIdx) {
        if (this.goodsPageMode !== 'each') return null
        if (pageIdx == null || pageIdx <= 0) return null
        var self = this
        var flowKeys = (this.template && Array.isArray(this.template.flowElementKeys)) ? this.template.flowElementKeys : []
        var flow = flowKeys.filter(function(key) {
          return key !== 'page_no' && self.selectedAttrs.indexOf(key) > -1 && self.attrStyles[key]
        })
        if (!flow.length) return null
        var keys = flow.slice()
        if (this.selectedAttrs.indexOf('page_no') > -1 && this.attrStyles.page_no) keys.push('page_no')
        // 顺移量 = 勾选元素最小 y - 模板全部元素最小 y，勾选块整体上移到模板顶部
        var minY = null
        var selMin = null
        this.selectedAttrs.forEach(function(key) {
          var s = self.attrStyles[key]
          if (!s || key === 'page_no') return
          var y = s.y || 0
          if (minY == null || y < minY) minY = y
        })
        flow.forEach(function(key) {
          var s = self.attrStyles[key]
          if (!s) return
          var y = s.y || 0
          if (selMin == null || y < selMin) selMin = y
        })
        var shift = (selMin != null && minY != null && selMin > minY) ? selMin - minY : 0
        var styles = this.attrStyles
        if (shift > 0) {
          styles = {}
          this.selectedAttrs.forEach(function(key) {
            var s = self.attrStyles[key]
            if (!s) return
            if (flow.indexOf(key) > -1) {
              styles[key] = Object.assign({}, s, { y: (s.y || 0) - shift })
            } else if (key === 'page_no') {
              // 分页标签固定原位每页打印，不参与顺移
              styles[key] = s
            }
          })
        }
        return { keys: keys, styles: styles }
      },
      // 地址打印单页多商品：每个商品输出「名称 / 类别 / 规格」一组，
      // 全部数据仍打印在同一张标签上，但各商品的值一一对应不再上下错位
      multiGoodsGroups(rows) {
        if (this.type !== 'address') return null
        if (this.goodsPageMode !== 'none') return null
        if (this.selectedAttrs.indexOf(GOODS_KEY) > -1) return null
        if (this.selectedAttrs.some(isGoodsTableKey)) return null
        var list = (Array.isArray(rows) ? rows : []).filter(hasGoodsValue)
        if (list.length < 2) return null
        var self = this
        var template = this.template || {}
        // 模板可能只持久化了映射表，规格块按映射表还原
        var blocks = Array.isArray(template.specBlocks) && template.specBlocks.length
          ? template.specBlocks
          : flattenSpecMapping(template.specMapping)
        var categoryGroups = this.groupSpecDefs(blocks)
        var groups = []
        list.forEach(function(row) {
          var name = row && row.name != null ? String(row.name).trim() : ''
          // 商品行规格以 SKU 的 spec_json 为准（没有时才退回显示文本）
          var rowBlocks = specBlocksFromRow(row)
          var pairs = rowBlocks.map(function(block) {
            return {
              name: block.name,
              value: (block.values || []).join('/')
            }
          })
          var matched = self.matchGoodsSpecGroup(pairs, row, categoryGroups)
          var category = matched ? (matched.categoryName || '') : ''
          // 只取映射表里勾选的规格值，未勾选的不输出
          var spec = matched ? resolveSpecTextFromDefs(matched.specs, rowBlocks) : ''
          // 模板没有映射表时才退回商品本身的规格显示值；
          // 有映射表但类别不匹配时宁可不打，也不读错规格
          if (!spec && !categoryGroups.length) {
            spec = pairs.map(function(pair) { return pair.value }).join('/')
              || (row && row.spec ? String(row.spec).trim() : '')
          }
          if (!name && !category && !spec) return
          groups.push({ name: name, category: category, spec: spec })
        })
        return groups.length > 1 ? groups : null
      },
      // 规格块按类别分组：每组记录类别名、成员类别与勾选的规格名
      groupSpecDefs(blocks) {
        var out = []
        var map = {}
        ;(blocks || []).forEach(function(def) {
          if (!def) return
          var key = String(def.categoryId || def.categoryName || def.name || '')
          if (!map[key]) {
            map[key] = {
              categoryId: def.categoryId || '',
              categoryName: def.categoryName || '',
              memberIds: def.categoryMemberIds || [],
              specs: []
            }
            out.push(map[key])
          }
          // 保留勾选值 / 品类配置值，取值时才能定位该规格下的具体子规格
          if (def.name) map[key].specs.push(def)
        })
        return out
      },
      specNameKey(name) {
        return String(name == null ? '' : name).replace(/\s+/g, '').toLowerCase()
      },
      sameSpecName(a, b) {
        return this.specNameKey(a) === this.specNameKey(b)
      },
      // 映射表优先：先按商品类别锁定映射表里的类别组，只读该组勾选的规格；
      // 类别不在映射表内就不输出规格，避免读错数据
      matchGoodsSpecGroup(pairs, row, categoryGroups) {
        var self = this
        var rowCategoryId = (row && ((row.raw && row.raw.categoryId) || row.categoryId)) || ''
        if (rowCategoryId) {
          var hit = null
          ;(categoryGroups || []).forEach(function(group) {
            if (hit) return
            var memberIds = (group.memberIds || []).map(function(id) { return String(id) })
            if (String(group.categoryId) === String(rowCategoryId) ||
              memberIds.indexOf(String(rowCategoryId)) > -1) hit = group
          })
          return hit
        }
        // 没有类别信息时，只有唯一一个类别组能覆盖商品全部规格名才使用
        var candidates = (categoryGroups || []).filter(function(group) {
          var slots = group.specs || []
          if (!slots.length) return false
          return slots.every(function(slot) {
            return pairs.some(function(pair) { return self.sameSpecName(pair.name, slot.name) })
          })
        })
        return candidates.length === 1 ? candidates[0] : null
      },
      // 一组商品的行组排版计划：沿用模板中「商品名称 / 类别 / 规格」元素的坐标与样式，
      // 下一组在上组下方顺移；位于行组下方的其它元素（页码等）顺移到最后一组下方
      multiGoodsGroupPlan(layout, groups) {
        var self = this
        var styles = (layout && layout.styles) || {}
        var styleOf = function(key) { return styles[key] || self.attrStyles[key] || {} }
        var hidden = (self.template && self.template.hiddenItemKeys) || []
        var keys = ['name', LABEL_CATEGORY_KEY, LABEL_SPEC_KEY].filter(function(key) {
          if (hidden.indexOf(key) > -1) return false
          return !!(styles[key] || self.attrStyles[key])
        })
        var members = keys.map(function(key) {
          var style = styleOf(key)
          var fontSize = style.fontSize || 12
          var lineHeight = style.lineHeight || 1.2
          return {
            key: key,
            x: style.x || 0,
            y: style.y || 0,
            width: style.width || 0,
            height: style.height || 0,
            fontSize: fontSize,
            fontWeight: style.fontWeight || 'normal',
            letterSpacing: style.letterSpacing || 0,
            lineHeight: lineHeight,
            lineHeightPx: fontSize * lineHeight,
            textAlign: style.textAlign === 'center' || style.textAlign === 'right' ? style.textAlign : 'left'
          }
        })
        // 未选中/无数据的项不占位：所有分组都取不到文本的成员直接剔除，
        // 避免 groupHeight 与组间距仍为隐藏项保留空间
        members = members.filter(function(m) {
          return groups.some(function(group) {
            return !!String(self.multiGoodsGroupText(group, m.key) || '').trim()
          })
        })
        if (!members.length) return null
        var baseY = Math.min.apply(null, members.map(function(m) { return m.y }))
        members.forEach(function(m) { m.offsetY = m.y - baseY })
        var groupHeight = members.reduce(function(max, m) {
          var h = m.height || m.lineHeightPx
          return Math.max(max, m.offsetY + h)
        }, 0)
        var belowFrom = baseY + groupHeight
        // 组与组之间的间距：优先沿用模板里组内元素的间距（如名称到规格），
        // 只有一个元素时才取行组下方元素与行组底部的距离。
        // members 按 y 排序取第二个的偏移，避免 keys 顺序（名称/类别/规格）
        // 与模板 y 顺序不一致时间距被放大成整行留白
        var gap = 0
        if (members.length > 1) {
          var sorted = members.slice().sort(function(a, b) { return a.offsetY - b.offsetY })
          gap = sorted[1].offsetY
        } else {
          Object.keys(styles).forEach(function(key) {
            if (keys.indexOf(key) > -1) return
            var style = styles[key]
            if (!style) return
            var y = style.y || 0
            if (y < belowFrom - 0.5) return
            var d = y - belowFrom
            if (!gap || d < gap) gap = d
          })
        }
        var stride = groupHeight + gap
        return {
          baseY: baseY,
          groupHeight: groupHeight,
          stride: stride,
          belowFrom: belowFrom,
          belowShift: (groups.length - 1) * stride,
          members: members,
          keys: keys
        }
      },
      multiGoodsGroupText(group, key) {
        if (!group) return ''
        if (key === 'name') return group.name || ''
        if (key === LABEL_CATEGORY_KEY) return group.category || ''
        if (key === LABEL_SPEC_KEY) return group.spec || ''
        return ''
      },
      // 行组 → schema 文本元素（毫米坐标，预览与打印同一份内容）
      multiGoodsSchemaElements(groups, plan) {
        var self = this
        var elems = []
        groups.forEach(function(group, gi) {
          plan.members.forEach(function(m, mi) {
            var text = self.multiGoodsGroupText(group, m.key)
            if (!text) return
            var y = plan.baseY + gi * plan.stride + m.offsetY
            var el = {
              id: 'mg_' + gi + '_' + mi,
              type: 'text',
              x: Math.round(m.x / PX_PER_MM * 100) / 100,
              y: Math.round(y / PX_PER_MM * 100) / 100,
              fontSize: m.fontSize,
              fontWeight: m.fontWeight,
              letterSpacing: m.letterSpacing,
              lineHeight: m.lineHeight,
              text: text
            }
            if (m.width) el.width = Math.round(m.width / PX_PER_MM * 100) / 100
            if (m.height) el.height = Math.round(m.height / PX_PER_MM * 100) / 100
            elems.push(el)
          })
        })
        return elems
      },
      // 把某一页的布局坐标套进 schema（堆叠预览逐页调用）
      schemaForLayout(layout, values, pageIdx) {
        var schema = this.templateSchema
        if (!schema || !schema.elements) return schema
        var styles = layout.styles
        // 分页打印续页：只保留勾选元素与分页标签，其余元素整页移除
        var contPlan = this.pagedContinuationPlanFor(pageIdx)
        var contKeys = contPlan ? contPlan.keys : null
        var contShiftPx = contPlan ? contPlan.shift : 0
        var changed = false
        var elements = schema.elements.map(function(el) {
          if (!el) return el
          var isCode = el.type === 'barcode' || el.type === 'qrcode'
          var key = isCode ? CODE_KEY_PREFIX + el.field : (el.field || el.id)
          // 规格块由共享模块整体输出（type: specblock），schema 中不再逐个元素输出
          if (!isCode && isSpecItemKey(key)) {
            changed = true
            return null
          }
          // 续页只输出勾选元素：编码元素按其绑定的属性键判断
          if (contKeys) {
            var baseKey = isCode ? (el.field || '') : key
            if (contKeys.indexOf(baseKey) === -1) {
              changed = true
              return null
            }
          }
          var style = styles[key]
          if (!style) {
            // 续页顺移：布局未覆盖的编码元素跟随其属性键上移
            if (contShiftPx > 0 && contKeys && contKeys.indexOf(isCode ? (el.field || '') : key) > -1) {
              var shiftedY = Math.round((((el.y || 0) * PX_PER_MM) - contShiftPx) / PX_PER_MM * 100) / 100
              if (Math.abs((el.y || 0) - shiftedY) > 0.01) {
                changed = true
                return Object.assign({}, el, { y: shiftedY })
              }
            }
            return el
          }
          var next = el
          var y = Math.round((style.y || 0) / PX_PER_MM * 100) / 100
          if (Math.abs((el.y || 0) - y) > 0.01) {
            next = Object.assign({}, next, { y: y })
            changed = true
          }
          var height = style.height / PX_PER_MM
          if (style.height > 0 && Math.abs((el.height || 0) - height) > 0.01) {
            next = Object.assign({}, next, { height: Math.round(height * 100) / 100 })
            changed = true
          }
          return next
        }).filter(function(el) { return !!el })
        // 单页多商品：模板的名称/类别/规格元素由逐商品行组替代，保证一个商品对应自己的值
        var groups = this.multiGoodsGroups(layout.goodsRows)
        var plan = groups ? this.multiGoodsGroupPlan(layout, groups) : null
        if (plan) {
          elements = elements.filter(function(el) {
            var key = el && (el.field || el.key || el.id)
            return plan.keys.indexOf(key) === -1
          })
          // 行组下方的其它元素顺移到最后一组下方
          if (plan.belowShift > 0) {
            var shiftMm = plan.belowShift / PX_PER_MM
            elements = elements.map(function(el) {
              if (!el) return el
              if ((el.y || 0) * PX_PER_MM < plan.belowFrom - 0.5) return el
              return Object.assign({}, el, { y: Math.round(((el.y || 0) + shiftMm) * 100) / 100 })
            })
          }
          elements = elements.concat(this.multiGoodsSchemaElements(groups, plan))
          changed = true
        }
        // 多商品行组已经逐个商品输出规格，不再叠加整体规格块
        var specElements = plan ? [] : this.specElementsFor(layout, values)
        if (specElements.length) {
          elements = elements.concat(specElements)
          changed = true
        }
        return changed ? Object.assign({}, schema, { elements: elements }) : schema
      },
      // 规格块 → schema 元素（type: specblock），由 LabelPaper 模块统一输出到屏幕与打印
      specElementsFor(layout, values) {
        return this.specBlockViewsFor(layout, values).map(function(view) {
          return {
            id: 'specblock_' + view.id,
            type: 'specblock',
            field: view.anchorKey,
            x: Math.round(parseFloat(view.frameStyle.left || 0) / PX_PER_MM * 100) / 100,
            y: Math.round(parseFloat(view.frameStyle.top || 0) / PX_PER_MM * 100) / 100,
            width: Math.round(parseFloat(view.frameStyle.width || 0) / PX_PER_MM * 100) / 100,
            height: Math.round(parseFloat(view.frameStyle.height || 0) / PX_PER_MM * 100) / 100,
            fontSize: parseFloat(view.frameStyle.fontSize || 12),
            fontWeight: view.frameStyle.fontWeight || 'normal',
            letterSpacing: parseFloat(view.frameStyle.letterSpacing || 0),
            lineHeight: parseFloat(view.frameStyle.lineHeight || 1.3),
            textAlign: view.frameStyle.textAlign || 'left',
            lineHeightPx: view.layout.lineHeightPx,
            lines: view.layout.lines.map(function(line) { return line.text })
          }
        })
      },
      // 指定页的业务值（分页标签文案随页变化，商品组合取该页内容）
      valuesForPage(page, layout) {
        var base = this.goodsPageMode === 'none' ? this.attrValues : Object.assign({}, this.attrValues, {
          [GOODS_KEY]: (layout && layout.goodsText) || ''
        })
        var values = Object.assign({}, base)
        values['page_no'] = this.pageNoTextFor(page)
        return values
      },
      // 指定页的商品明细行
      rowsForPage(page) {
        if (this.goodsPageMode === 'none') return this.detailRows
        var p = this.goodsPagePlan.pages[page]
        return p ? p.rows : []
      },
      // 堆叠预览取文案：优先该页布局文本
      pageText(pg, item) {
        var text = pg.texts[item.attr.key]
        if (text != null) return text
        if (item.attr.key === GOODS_KEY) return pg.layout.goodsText
        return item.attr.value
      },
      // 指定页的标签布局：页码越界时回落到最后一页
      layoutForPage(page) {
        var idx = Math.max(0, page || 0)
        // 每页的分页标签文案不同，布局时按该页的值计算
        var values = Object.assign({}, this.attrValues)
        values['page_no'] = this.pageNoTextFor(idx)
        if (this.goodsPageMode === 'none') return this.layoutWithRows(this.goodsRowsForLayout, values, idx)
        var plan = this.goodsPagePlan
        idx = Math.min(idx, plan.pages.length - 1)
        return this.layoutWithRows(plan.pages[idx] ? plan.pages[idx].rows : [], values, idx)
      },
      // 规格块预览视图：与编辑页 specBlockViews 使用同一套组装与行布局
      specBlockViewsFor(layout, values) {
        var self = this
        var styles = (layout && layout.styles) || {}
        var map = {}
        var order = []
        ;(this.selectedAttrList || []).forEach(function(attr) {
          var item = parseSpecItemKey(attr.key)
          if (!item) return
          var id = item.rowNo + '-' + (item.categoryId || '') + '-' + (item.blockIdentity || item.blockNo)
          if (!map[id]) {
            map[id] = { id: id, keys: [] }
            order.push(id)
          }
          map[id].keys.push(attr.key)
        })
        var labelWidth = this.labelWidth || 320
        var lastRight = 0
        var lastBottom = 0
        // 打印只输出本页商品所属品类的规格块；品类名挂在每个品类的第一个规格块上
        var categoryIds = (layout && Array.isArray(layout.categoryIds)) ? layout.categoryIds : []
        var templateBlocks = (this.template && Array.isArray(this.template.specBlocks)) ? this.template.specBlocks : []
        // 明细行类目落在某个一级类目下时，该一级类目的规格段才算命中
        var allowedCategoryKeys = {}
        if (categoryIds.length) {
          var categoryIdSet = categoryIds.map(String)
          templateBlocks.forEach(function(block) {
            if (block.categoryId == null) return
            var members = Array.isArray(block.categoryMemberIds) && block.categoryMemberIds.length
              ? block.categoryMemberIds.map(String)
              : [String(block.categoryId)]
            var matched = members.some(function(categoryId) { return categoryIdSet.indexOf(categoryId) > -1 })
            if (matched) allowedCategoryKeys[String(block.categoryId)] = true
          })
        }
        var categoryNameOf = function(parsed) {
          if (parsed.categoryId == null) return ''
          var found = templateBlocks.filter(function(block) {
            return block.categoryId != null && String(block.categoryId) === String(parsed.categoryId)
          })[0]
          if (found && found.categoryName) return String(found.categoryName)
          var nameAttr = self.allAttrs.find(function(a) { return a.categoryName && String(a.categoryId || '') === String(parsed.categoryId) })
          return nameAttr ? String(nameAttr.categoryName) : ''
        }
        var lastCategory = null
        return order.map(function(id) {
          var block = map[id]
          var parsed = parseSpecItemKey(block.keys[0])
          var categoryKey = parsed.categoryId == null ? '' : String(parsed.categoryId)
          if (categoryKey !== '' && categoryIds.length && !allowedCategoryKeys[categoryKey]) return null
          var firstOfCategory = categoryKey !== '' && categoryKey !== lastCategory
          lastCategory = categoryKey
          var categoryText = firstOfCategory ? categoryNameOf(parsed) : ''
          var nameKey = specNameKey(parsed.rowNo, parsed.blockNo, parsed.categoryId, parsed.blockIdentity)
          var hasName = block.keys.indexOf(nameKey) > -1
          var nameAttr = self.allAttrs.find(function(a) { return a.key === nameKey })
          var nameValue = hasName && values ? values[nameKey] : ''
          var nameText = hasName
            ? String(nameValue || (nameAttr && (nameAttr.value || nameAttr.label)) || '').replace(/：$/, '')
            : ''
          // 几何只认属性名锚点；名称隐藏时退回块内第一个元素
          var anchorKey = styles[nameKey] ? nameKey : block.keys[0]
          var style = styles[anchorKey] || {}
          var items = buildSpecBlockItems({
            nameKey: nameKey,
            categoryText: categoryText,
            nameText: nameText ? nameText + '：' : '',
            nameWrap: !!style.specWrap,
            values: block.keys.filter(function(key) { return key !== nameKey }).map(function(key) {
              return { key: key, text: values && values[key] != null ? values[key] : '' }
            })
          })
          if (!items.length) return null
          var lines = resolveSpecBlockLines({
            items: items,
            // 与编辑页一致：未手动拖宽过的规格块按自然宽度并列排列
            width: style.specWidthLocked && style.width > 0 ? style.width : 0,
            fontSize: style.fontSize || 12,
            fontWeight: style.fontWeight || 'normal',
            letterSpacing: style.letterSpacing || 0,
            lineHeight: style.lineHeight || 1.3,
            textAlign: style.textAlign || 'left'
          })
          // 锚点缺几何时顺排到上一块右侧/下一行，避免打印/预览跳到左上角
          var hasGeom = style.x != null && style.y != null && isFinite(style.x) && isFinite(style.y)
          var x, y
          if (hasGeom) {
            x = Number(style.x)
            y = Number(style.y)
          } else if (categoryKey !== '') {
            // 多品类规格：每个规格块独占一行，新品类前空一行
            x = 20
            y = lastBottom > 0 ? lastBottom + (firstOfCategory ? lines.lineHeightPx : 4) : 10
          } else {
            x = lastRight > 0 ? lastRight + SPEC_BLOCK_GAP : 20
            y = lastBottom > 0 ? lastBottom + 4 : 10
            if (x + lines.width > labelWidth - 10) {
              x = 20
              y = lastBottom > 0 ? lastBottom + 4 : 10
            }
          }
          lastRight = Math.max(lastRight, x + lines.width)
          lastBottom = Math.max(lastBottom, y + lines.height)
          return {
            id: id,
            anchorKey: anchorKey,
            keys: block.keys,
            layout: lines,
            frameStyle: {
              left: x + 'px',
              top: y + 'px',
              width: lines.width + 'px',
              height: lines.height + 'px',
              fontSize: (style.fontSize || 12) + 'px',
              fontWeight: style.fontWeight || 'normal',
              letterSpacing: (style.letterSpacing || 0) + 'px',
              lineHeight: String(style.lineHeight || 1.3),
              textAlign: lines.textAlign,
              padding: '0 ' + SPEC_BLOCK_PAD_X + 'px',
              boxSizing: 'border-box',
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              display: 'block'
            }
          }
        }).filter(function(view) { return !!view })
      },
      itemStyle(key, layoutOpt) {
        // 商品明细表/商品组合按商品行数动态变高，使用重排后的坐标
        // layoutOpt 传入时使用指定页的布局（堆叠预览逐页调用）
        var layout = layoutOpt || this.overflowLayout
        var s = layout.styles[key] || (layoutOpt ? null : this.goodsLayout.styles[key]) || this.attrStyles[key]
        if (!s) return {}
        // 独立编码元素：宽高即图形实际可打印尺寸
        if (isCodeKey(key)) {
          var codeSize = this.codeLayout(key)
          return {
            left: (s.x || 0) + 'px',
            top: (s.y || 0) + 'px',
            width: codeSize.imgWidth + 'px',
            height: codeSize.imgHeight + 'px'
          }
        }
        if (isGoodsTableKey(key)) {
          var table = layoutOpt ? layout.table : this.goodsTableData
          return {
            left: (s.x || 0) + 'px',
            top: (s.y || 0) + 'px',
            width: ((table && table.width) || 0) + 'px',
            fontSize: (s.fontSize || 12) + 'px',
            lineHeight: String(s.lineHeight || 1.3)
          }
        }
        var style = {
          left: s.x + 'px',
          top: s.y + 'px',
          fontSize: s.fontSize + 'px',
          fontWeight: s.fontWeight,
          letterSpacing: (s.letterSpacing || 0) + 'px',
          lineHeight: String(s.lineHeight || 1.3)
        }
        // 折行宽度 = min(元素边框宽度, 标签可用宽度)：元素宽于标签时按标签边界折行，
        // 与布局计算 / 打印下发保持一致，避免文字溢出标签被裁切
        var labelLimit = Math.max(20, this.labelWidth - (s.x || 0))
        var wrapWidth = s.width > 0 ? Math.min(s.width, labelLimit) : labelLimit
        style.width = wrapWidth + 'px'
        style.whiteSpace = 'pre-wrap'
        style.wordBreak = 'break-all'
        style.overflowWrap = 'anywhere'
        if (s.height) {
          style.height = s.height + 'px'
          style.overflow = 'hidden'
        }
        return style
      },
      isGoodsTable(key) {
        return isGoodsTableKey(key)
      },
      getGoodsColumnWidth(index, layoutOpt) {
        var table = layoutOpt ? layoutOpt.table : this.goodsTableData
        return table && table.widths[index] ? table.widths[index] : 0
      },
      getGoodsRowHeight(index, layoutOpt) {
        var table = layoutOpt ? layoutOpt.table : this.goodsTableData
        if (table && table.rows[index]) return table.rows[index].height
        var s = this.attrStyles[this.goodsTableKey] || {}
        return Math.round((s.fontSize || 12) * (s.lineHeight || 1.3)) + 4
      },
      // 商品组合元素按商品行逐行显示（名称 + 规格值1/2/3）
      getItemText(item) {
        if (item.attr.key === GOODS_KEY) return this.goodsLayout.goodsDisplay
        return item.attr.value
      },
      // 预览文字：优先用布局结果（商品多行已逐行带前缀；省略模式已截断）
      displayText(item, page) {
        // 同页堆叠预览：按各自页的布局取文案（分页标签/商品组合随页变化）
        if (page != null) {
          var lay = this.layoutForPage(page)
          var pageText = lay.texts && lay.texts[item.attr.key]
          if (pageText != null) return pageText
          if (item.attr.key === GOODS_KEY) return lay.goodsText
        }
        var text = this.overflowLayout.texts && this.overflowLayout.texts[item.attr.key]
        return text != null ? text : this.getItemText(item)
      },
      // 与标签编辑弹层共用同一套表单字段配置，避免模板里的属性在这里丢失
      buildAttrList() {
        var list = buildFormFieldAttrs(this.type, { record: this.record, sku: this.sku })
        // 分页标签：模板元素，文案由当前预览/打印页决定
        if (!list.some(function(a) { return a.key === 'page_no' })) {
          list.push({ key: 'page_no', label: '分页标签', value: '', isCustom: true, isPage: true, group: '模板元素' })
        }
        return list
      },
      // 发货单/小票模板里的字段不在 label attrs 里 → 补回缺失 key
      restoreMissingAttrs(keys) {
        var self = this
        var exists = {}
        this.allAttrs.forEach(function(a) { exists[a.key] = true })
        ;(keys || []).forEach(function(key) {
          if (exists[key]) return
          var label = self.attrLabels[key] || '自定义'
          // 未知字段只补结构，值必须来自真实数据，避免预览里把字段名当成值显示
          self.allAttrs.push({ key: key, label: label, value: '', isCustom: true, group: '自定义' })
          exists[key] = true
        })
      },
      // 无模板时：带当前业务数据进入统一标签编辑页，编辑页按类型加载字段
      // 会员标签 → 会员数据表字段；地址打印 → 只取地址识别页面输入框字段
      handleCreateTemplate() {
        var isAddress = this.type === 'address'
        this.visible = false
        var payload = {
          type: isAddress ? 'address' : 'member',
          businessType: isAddress ? '' : 'member',
          provider: isAddress ? '' : 'table.jsh_supplier',
          templateId: null,
          record: this.record || {},
          sku: {},
          template: null
        }
        // 地址打印：元素回填统一走编辑上下文模块，由表单标题与字段结构生成
        if (isAddress) {
          var createContext = buildTemplateEditContext({
            record: this.record || {},
            rows: this.detailRows,
            fields: buildAddressAttrs(this.record),
            title: ADDRESS_FORM_TITLE,
            includeProductAttributes: true
          })
          payload.record = createContext.record
          payload.editorData = createContext.editorData
        }
        this.$refs.labelEditor.open(payload)
      },
      // 编辑：先关闭预览层，动画结束后再交给标签编辑弹层，避免两层重叠
      // 同时带上当前预览使用的模板 id，编辑页按它加载同一套标签数据
      handleEdit() {
        // 小票：编辑页只加载模板布局，不携带单据业务数据
        var isReceipt = this.type === 'receipt'
        // 商品标签/小票编辑统一只接收模板结构与品类标识，不带入业务值。
        var isAddress = this.type === 'address'
        // 地址打印：元素回填统一由地址表单的标题与字段结构生成
        var editContext = buildTemplateEditContext({
          record: this.record,
          rows: this.detailRows,
          fields: isAddress ? buildAddressAttrs(this.record) : this.allAttrs,
          title: isAddress ? ADDRESS_FORM_TITLE : undefined,
          includeProductAttributes: isAddress || this.type === 'label' || this.type === 'receipt' || this.type === 'delivery',
          productFields: this.type === 'label' ? buildFormFieldAttrs('label', { record: this.record, sku: this.sku }) : [],
          specBlocks: this.template && this.template.specBlocks
        })
        var record = editContext.record
        var payload = {
          record: record,
          sku: {},
          editorData: editContext.editorData,
          templateId: this.template ? this.template.id : null,
          template: this.template || null
        }
        this.visible = false
        // 弹层直达方式（无父组件监听 edit）→ 直接打开标准标签编辑弹层页面
        if (this.directEntry && !(this.$listeners && this.$listeners.edit)) {
          var businessType = (isAddress || isReceipt) ? '' : (this.type === 'delivery' ? 'delivery' : (this.type === 'member' ? 'member' : 'product'))
          this.$refs.labelEditor.open({
            type: this.type || 'label',
            businessType: businessType,
            provider: isReceipt ? '' : ((this.template && this.template.provider) || (isAddress ? '' : (this.type === 'delivery' ? 'sales.delivery' : (this.type === 'member' ? 'table.jsh_supplier' : 'product.label')))),
            templateId: payload.templateId || null,
            record: record || {},
            sku: {},
            editorData: payload.editorData,
            template: this.template || null
          })
          return
        }
        setTimeout(() => this.$emit('edit', payload), 200)
      },
      // 标签编辑关闭后回到打印预览：编辑入口来自预览页本身，关闭时应回退到预览
      handleEditorClosed() {
        if (!this.directEntry) return
        this.visible = true
        this.reloadRouteTemplate()
      },
      // 通过搜索发现的打印机打印（蓝牙/云）
      handleDiscoveredPrint() {
        var printer = this.discoveredPrinter
        var type = printer.type
        if (type === 'bluetooth') {
          return this.printViaBluetooth(printer)
        }
        this.printing = true
        var items = this.buildLayoutItems()
        if (type === 'cloud') {
          this.printing = false
          this.printByBrowser(items)
          return Promise.resolve(true)
        }
        return Promise.resolve(false)
      },
      // 蓝牙打印：复用后端指令生成模块（与网络打印同一套 TSPL/CPCL 排版指令），
      // 仅传输通道由「后端远程下发」改为「本地蓝牙 GATT 直发」
      printViaBluetooth(printer) {
        var self = this
        var device = printer.device
        if (!device) {
          this.$message.error('蓝牙设备未连接')
          return Promise.resolve(false)
        }
        this.printing = true
        // 指令集与网络打印共用一套配置；蓝牙标签机默认 TSPL
        var cmdSet = String(printer.cmdSet || 'TSPL').toUpperCase()
        var needsImage = cmdSet === 'CPCL' || cmdSet === 'ESC/POS'
        var items = this.buildLayoutItems()
        var prepare = needsImage ? this.renderLabelToPng() : Promise.resolve(null)
        return prepare.then(function(image) {
          var payload = {
            cmdSet: cmdSet,
            copies: self.copies,
            width: self.labelWidth,
            height: self.labelHeight,
            items: items
          }
          if (image) payload.image = image
          return postAction('/print/labelCommands', payload)
        }).then(function(res) {
          var data = (res && res.data) || {}
          if (!res || res.code !== 200 || !data.commands) {
            self.printing = false
            self.msgError((typeof data === 'string' ? data : data.message) || '打印指令生成失败')
            return false
          }
          // Base64 → 字节流 → 本地蓝牙写入
          var raw = atob(data.commands)
          var bytes = new Uint8Array(raw.length)
          for (var i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i)
          return self.sendViaBluetooth(device, printer, bytes)
        }).catch(function(err) {
          self.printing = false
          self.msgError('蓝牙打印失败：' + ((err && err.message) || err))
          return false
        })
      },
      // 连接蓝牙 GATT 并分包写入指令字节
      sendViaBluetooth(device, printer, bytes) {
        var self = this
        return device.gatt.connect().then(function(server) {
          // 获取打印服务（取第一个可用的打印服务）
          var servicePromises = printer.capabilities.services.map(function(uuid) {
            return server.getPrimaryService(uuid).catch(function() { return null })
          })
          return Promise.all(servicePromises).then(function(services) {
            var svc = services.find(function(s) { return s !== null })
            if (!svc) throw new Error('未找到打印服务')
            return svc.getCharacteristics()
          })
        }).then(function(chars) {
          // 取第一个可写特征值
          var writable = chars.find(function(c) {
            return c.properties.write || c.properties.writeWithoutResponse
          })
          if (!writable) throw new Error('未找到可写特征值')
          return self.sendChunks(writable, bytes)
        }).then(function() {
          self.printing = false
          self.msgSuccess('已发送到蓝牙打印机：' + printer.name)
          self.notifyPrintSuccess()
          return true
        }).catch(function(err) {
          self.printing = false
          self.msgError('蓝牙打印失败：' + ((err && err.message) || err))
          return false
        })
      },
      // 分包发送（避免 GATT 写入长度超限）
      sendChunks(char, data, chunkSize) {
        chunkSize = chunkSize || 128
        var i = 0
        var sendNext = function() {
          if (i >= data.length) return Promise.resolve()
          var chunk = data.slice(i, i + chunkSize)
          i += chunkSize
          return char.writeValue(chunk).then(sendNext)
        }
        return sendNext()
      },
      // 打印成功后通知父页面（地址打印页据此生成并保存会员信息）
      notifyPrintSuccess() {
        // 批量分页时只在全部结束后通知一次，避免重复保存
        if (this._batchPrint) return
        this.$emit('print-success', { type: this.type })
      },
      // 批量分页时抑制单页提示，由 printAllPages 统一提示
      msgSuccess(text) {
        if (!this._batchPrint) this.$message.success(text)
      },
      msgError(text) {
        if (!this._batchPrint) {
          // 页内也显示一份：手机端全屏弹层页上 toast 可能看不到
          this.printHint = text
          this.$message.error(text)
        }
      },
      msgWarn(text) {
        this.printHint = text
        this.$message.warning(text)
      },
      // 打印：直接发送到所选打印机（后端执行），后端不可用时回退浏览器打印
      handlePrint() {
        // 清空上一次的失败提示
        this.printHint = ''
        // 同步异常兜底：避免点击后无任何提示、按钮卡在 loading
        try {
          this.doHandlePrint()
        } catch (err) {
          this.printing = false
          this._batchPrint = false
          this._printFailed = 0
          this.printPage = 0
          var reason = '打印失败：' + ((err && err.message) || err)
          this.printHint = reason
          this.$message.error(reason)
        }
      },
      doHandlePrint() {
        if (!this.template) {
          this.msgWarn('标签模板未加载完成，请稍后重试')
          return
        }
        // 尚未添加任何打印机：弹层提示，并引导进入打印机设置 / 打印设备页面
        if (this.printerType !== 'cloud' && !this.hasAnyPrinter) {
          this.showAddPrinterDialog()
          return
        }
        // 多商品分页：逐页下发；浏览器打印一次性生成多页
        if (this.pageCount > 1) {
          if (this.selectedPrinter === '__browser__') {
            this.printByBrowserAll()
            return
          }
          this.printAllPages()
          return
        }
        this._batchPrint = false
        this.printPage = 0
        this.sendLabel()
      },
      // 逐页打印：每页一张标签，公共字段由模板重复渲染
      printAllPages() {
        var self = this
        this._batchPrint = true
        this._printFailed = 0
        this.printing = true
        var i = 0
        var next = function() {
          if (i >= self.pageCount) {
            self._batchPrint = false
            self.printing = false
            self.printPage = 0
            if (self._printFailed > 0) {
              self.printHint = '部分标签打印失败：' + self._printFailed + ' / ' + self.pageCount + ' 张'
              self.$message.error(self.printHint)
            } else {
              self.$message.success('已发送 ' + self.pageCount + ' 张标签')
              self.notifyPrintSuccess()
            }
            return
          }
          self.printPage = i++
          Promise.resolve(self.sendLabel()).then(function(ok) {
            if (ok === false) self._printFailed++
            next()
          }, function() {
            self._printFailed++
            next()
          })
        }
        next()
      },
      // 发送当前页（批量时由 printAllPages 逐页调用）
      sendLabel() {
        var self = this
        // Wi-Fi 打印机：由后端按 IP:端口 下发 TSPL 指令
        if (this.printerType === 'wifi') {
          return this.printViaWifi()
        }
        // 远程打印机（打印代理设备）：由后端转 TSPL 指令，经 WebSocket 下发到代理
        if (this.selectedPrinter && String(this.selectedPrinter).indexOf('agent:') === 0) {
          var agentPrinter = this.agentPrinterItems.find(function(p) { return p.key === self.selectedPrinter })
          if (!agentPrinter) {
            // 设备列表可能尚未加载完成：刷新一次后重试
            return this.loadAgentPrinters().then(function() {
              var printer = self.agentPrinterItems.find(function(p) { return p.key === self.selectedPrinter })
              if (!printer) {
                self.msgWarn('未找到已选云打印机，请重新选择')
                return false
              }
              if (!printer.online) {
                self.msgWarn('打印代理「' + printer.deviceName + '」当前离线，请检查设备网络')
                return false
              }
              return self.printViaAgentPrinter(printer)
            })
          }
          if (!agentPrinter.online) {
            this.msgWarn('打印代理「' + agentPrinter.deviceName + '」当前离线，请检查设备网络')
            return Promise.resolve(false)
          }
          return this.printViaAgentPrinter(agentPrinter)
        }
        if (!this.selectedPrinter) {
          // 远程部署时后端枚举不到本机打印机，此时应引导安装本地打印助手
          if (this.printerType === 'backend' && !this.localAgentOnline && this.printers.length === 0
            && this.agentPrinterItems.length === 0) {
            this.showInstallGuide()
          } else {
            this.msgWarn('请先选择打印机')
          }
          return Promise.resolve(false)
        }
        // 浏览器打印：走系统打印对话框，可选本机任意打印机（远程部署场景）
        if (this.selectedPrinter === '__browser__') {
          this.printByBrowser(this.buildLayoutItems())
          return Promise.resolve(true)
        }
        // 本机打印机：交给本地打印助手直接打印，不弹浏览器对话框
        if (isLocalPrinter(this.selectedPrinter)) {
          // 打印前再确认一次助手仍在运行，避免中途退出后无提示
          return detectLocalAgent().then(function(base) {
            if (!base) {
              self.localAgentOnline = false
              self.showInstallGuide()
              return false
            }
            return self.printViaLocalAgent()
          })
        }
        // 若选择的是搜索发现的打印机（蓝牙/云），走对应打印通道
        if (this.discoveredPrinter && this.discoveredPrinter.id === this.selectedPrinter) {
          return this.handleDiscoveredPrint()
        }
        var items = this.buildLayoutItems()
        this.printing = true
        // 打印机只能打点阵，交给前端把整张标签渲染成图片，后端转单色位图下发
        return this.renderLabelToPng().then(function(image) {
          return postAction('/print/label', {
            printerName: self.selectedPrinter,
            copies: self.copies,
            width: self.labelWidth,
            height: self.labelHeight,
            image: image,
            items: items
          })
        }).then(function(res) {
          self.printing = false
          var data = (res && res.data) || {}
          if (res && res.code === 200) {
            self.msgSuccess('已发送到打印机：' + self.selectedPrinter)
            self.notifyPrintSuccess()
            return true
          }
          self.msgError(data.message || '打印失败')
          if (!self._batchPrint) self.printByBrowser(items)
          return false
        }).catch(function() {
          self.printing = false
          if (!self._batchPrint) self.printByBrowser(items)
          return false
        })
      },
      // 组装标签打印项（后端打印机与网络打印机共用同一份布局数据）
      // 按界面元素 1:1 输出：文字项与独立编码项各用自身 x/y/宽高，不再做二次拆分
      buildLayoutItems(page) {
        var self = this
        var items = []
        // 不传页码时跟随当前预览页；批量打印期间跟随当前打印页
        if (page == null) page = this._batchPrint ? this.printPage : this.previewPage
        var layout = this.layoutForPage(page)
        var resolved = layout.styles
        // 分页打印续页：仅输出勾选元素（分页标签固定打印）
        var contPlan = this.pagedContinuationPlanFor(page)
        // 单页多商品：名称/类别/规格按模板布局样式逐组输出
        var groups = this.multiGoodsGroups(layout.goodsRows)
        var plan = groups ? this.multiGoodsGroupPlan(layout, groups) : null
        this.renderItems.forEach(function(item) {
          if (plan && plan.keys.indexOf(item.key) > -1) return
          if (contPlan) {
            // 绑定属性的编码元素 key 带 code__ 前缀，续页勾选按属性键匹配；自定义码元素 key 即属性键
            var contKey = item.isCode && item.key.indexOf(CODE_KEY_PREFIX) === 0
              ? item.key.slice(CODE_KEY_PREFIX.length)
              : item.key
            if (contPlan.keys.indexOf(contKey) === -1) return
          }
          var style = resolved[item.key] || self.attrStyles[item.key]
          if (!style) return
          // 续页顺移：布局未覆盖的编码项跟随其属性键上移
          if (contPlan && contPlan.shift > 0 && !resolved[item.key]) {
            style = Object.assign({}, style, { y: (style.y || 0) - contPlan.shift })
          }
          // 多行顺移后超出标签底边的内容直接裁掉
          if ((style.y || 0) >= self.labelHeight) return
          // 商品明细表：按行展开为“表头 + 每行各列”文字项，行高按实际折行行数
          if (isGoodsTableKey(item.key)) {
            var table = layout.table
            if (!table) return
            var left = style.x || 0
            var colLeft = 0
            table.columns.forEach(function(col, ci) {
              items.push({
                x: left + colLeft,
                y: style.y || 0,
                width: table.widths[ci],
                height: table.headHeight,
                fontSize: style.fontSize,
                fontWeight: style.fontWeight,
                letterSpacing: style.letterSpacing || 0,
                lineHeight: style.lineHeight || 1.3,
                text: col
              })
              colLeft += table.widths[ci]
            })
            var rowY = (style.y || 0) + table.headHeight
            table.rows.forEach(function(row) {
              var cellLeft = 0
              row.cells.forEach(function(cell, ci) {
                items.push({
                  x: left + cellLeft,
                  y: rowY,
                  width: table.widths[ci],
                  height: row.height,
                  fontSize: style.fontSize,
                  fontWeight: style.fontWeight,
                  letterSpacing: style.letterSpacing || 0,
                  lineHeight: style.lineHeight || 1.3,
                  text: cell
                })
                cellLeft += table.widths[ci]
              })
              rowY += row.height
            })
            return
          }
          if (item.isCode) {
            // 编码元素：宽高取与预览一致的可打印尺寸
            // 注意：变量名不能与上方的 layout 同名，var 会提升并遮蔽外层布局对象
            var code = self.codeLayout(item.key)
            items.push({
              x: style.x || 0,
              y: style.y || 0,
              width: code.imgWidth,
              height: code.imgHeight,
              codeType: style.codeType || (item.attr.isCodeElem ? item.attr.codeType : ''),
              codeText: code.value,
              // 硬件取整参数：后端直接使用，不能自行按宽度重算
              narrow: code.narrow,
              cell: code.cell,
              // image 仅用于浏览器兜底打印，后端按 codeType + codeText 下发硬件指令
              image: self.codeImages[item.key] || '',
              text: ''
            })
            return
          }
          var attr = item.attr
          var labelText = self.attrLabels[attr.key] || ''
          // 商品组合：每个商品一行（名称 + 规格值1/2/3）
          var value = attr.key === GOODS_KEY
            ? layout.goodsText
            : (attr.value == null ? '' : attr.value)
          var displayText = labelText ? labelText + ': ' + value : value
          // 超宽处理后的最终文本（省略号裁切模式下已截断）
          var overflowText = layout.texts[attr.key]
          if (overflowText != null) displayText = overflowText
          items.push({
            x: style.x || 0,
            y: style.y || 0,
            // 折行宽度 = min(元素边框宽度, 标签可用宽度)，与布局计算保持一致
            width: (function() {
              var limit = Math.max(20, self.labelWidth - (style.x || 0))
              return style.width > 0 ? Math.min(style.width, limit) : limit
            })(),
            // 高度按模板：折行模式下布局已把高度更新为实际行高，省略模式下为模板原始高度
            height: style.height || null,
            fontSize: style.fontSize,
            fontWeight: style.fontWeight,
            letterSpacing: style.letterSpacing || 0,
            lineHeight: style.lineHeight || 1.3,
            textAlign: style.textAlign === 'center' || style.textAlign === 'right' ? style.textAlign : 'left',
            text: displayText
          })
        })
        // 行组下方的其它元素顺移到最后一组下方
        if (plan && plan.belowShift > 0) {
          items.forEach(function(item) {
            if ((item.y || 0) >= plan.belowFrom - 0.5) item.y += plan.belowShift
          })
        }
        if (plan) {
          groups.forEach(function(group, gi) {
            plan.members.forEach(function(m) {
              var text = self.multiGoodsGroupText(group, m.key)
              if (!text) return
              var y = plan.baseY + gi * plan.stride + m.offsetY
              if (y >= self.labelHeight) return
              items.push({
                x: m.x,
                y: y,
                width: m.width || null,
                height: m.height || null,
                fontSize: m.fontSize,
                fontWeight: m.fontWeight,
                letterSpacing: m.letterSpacing,
                lineHeight: m.lineHeight,
                textAlign: m.textAlign || 'left',
                text: text
              })
            })
          })
        }
        return items
      },
      // 打印到 Wi-Fi 打印机：后端通过 Socket 发送 TSPL 指令到 ip:端口
      printViaWifi() {
        var self = this
        var printer = this.wifiPrinters.find(function(p) { return p.id === self.activeWifiId })
        if (!printer) {
          this.msgWarn('请先点击「添加打印机」配置打印机')
          return Promise.resolve(false)
        }
        this.printing = true
        var self2 = this
        var items = this.buildLayoutItems()
        // TSPL / DDPL 走打印机内置字库的文本+条码指令（数据量小、出纸稳定，后端已按框宽折行、按框高截断）；
        // CPCL / ESC/POS 只支持位图，才需要把整张标签渲染成图片
        var cmdSet = String(printer.cmdSet || 'TSPL').toUpperCase()
        var needsImage = cmdSet === 'CPCL' || cmdSet === 'ESC/POS'
        var prepare = needsImage ? this.renderLabelToPng() : Promise.resolve(null)
        return prepare.then(function(image) {
          var payload = {
            copies: self.copies,
            width: self.labelWidth,
            height: self.labelHeight,
            items: items
          }
          if (image) payload.image = image
          return printToWifiPrinter(printer, payload)
        }).then(function(res) {
          self2.printing = false
          if (res && res.code === 200) {
            self2.msgSuccess('已发送到打印机：' + printer.name)
            self2.$set(self2.wifiStatus, printer.id, true)
            self2.notifyPrintSuccess()
            return true
          }
          // 后端把失败原因放在 data.message 中
          var data = (res && res.data) || {}
          var message = (typeof data === 'string' ? data : data.message) || '打印失败'
          self2.msgError(message)
          return false
        }).catch(function(err) {
          self2.printing = false
          self2.msgError((err && err.message) || '网络打印失败，请检查打印机与后端网络')
          return false
        })
      },
      // 浏览器打印（后端打印不可用时兜底）
      printByBrowser(items) {
        var html = this.itemsToHtml(items)
        var copies = Math.max(1, Math.min(99, parseInt(this.copies, 10) || 1))
        var pages = []
        for (var i = 0; i < copies; i++) pages.push(html)
        printLabel({
          widthPx: this.labelWidth,
          heightPx: this.labelHeight,
          pages: pages
        })
      },
      // 把打印项拼成绝对定位 HTML（浏览器打印复用）
      itemsToHtml(items) {
        var bodyHtml = ''
        items.forEach(function(item) {
          var boxStyle = 'position:absolute;left:' + item.x + 'px;top:' + item.y + 'px;'
          if (item.image) {
            bodyHtml += '<img src="' + item.image + '" style="' + boxStyle +
              'width:' + item.width + 'px;height:' + item.height + 'px;display:block;" />'
            return
          }
          var sizeStyle = ''
          var wrapStyle = ''
          if (item.width) {
            sizeStyle += 'width:' + item.width + 'px;'
            // 指定宽度：按框宽自动换行
            wrapStyle += 'white-space:pre-wrap;word-break:break-all;overflow-wrap:anywhere;'
          }
          if (item.height) {
            sizeStyle += 'height:' + item.height + 'px;'
            // 指定高度：超出部分不打印
            wrapStyle += 'overflow:hidden;'
          }
          bodyHtml += '<div style="' + boxStyle +
            sizeStyle +
            'font-size:' + item.fontSize + 'px;' +
            'font-weight:' + item.fontWeight + ';' +
            'letter-spacing:' + (item.letterSpacing || 0) + 'px;' +
            'line-height:' + (item.lineHeight || 1.3) + ';' +
            'text-align:' + (item.textAlign || 'left') + ';' +
            wrapStyle +
            '">' + item.text + '</div>'
        })
        return bodyHtml
      },
      // 浏览器打印多页：每个商品分页一页，份数在每页基础上再乘，只弹一次系统打印框
      printByBrowserAll() {
        var pages = []
        var copies = Math.max(1, Math.min(99, parseInt(this.copies, 10) || 1))
        for (var c = 0; c < copies; c++) {
          for (var page = 0; page < this.pageCount; page++) {
            pages.push(this.itemsToHtml(this.buildLayoutItems(page)))
          }
        }
        printLabel({
          widthPx: this.labelWidth,
          heightPx: this.labelHeight,
          pages: pages
        })
      },
      // 未检测到本地打印助手时，引导用户下载安装
      showInstallGuide() {
        var self = this
        var h = this.$createElement
        this.$confirm({
          title: '需要安装本地打印助手',
          width: 440,
          content: h('div', { style: 'line-height:1.9' }, [
            h('p', '网页无法直接调用你电脑上的打印机，需要安装并运行「本地打印助手」。'),
            h('p', '安装后会自动识别本机所有打印机，打印时不再弹出系统对话框。'),
            h('p', [
              h('a', {
                attrs: {
                  href: LOCAL_AGENT_DOWNLOAD_URL,
                  target: '_blank',
                  rel: 'noopener',
                  download: 'print-agent.exe'
                }
              }, '① 下载 print-agent.exe')
            ]),
            h('p', { style: 'color:#8c8c8c;font-size:12px' },
              '② 下载后双击运行，程序会常驻后台并自动开机自启。')
          ]),
          okText: '我已安装，重新检测',
          cancelText: '稍后再说',
          onOk: function() {
            return self.loadPrinters().then(function() {
              if (self.localAgentOnline) {
                self.$message.success('已检测到本地打印助手')
              } else {
                self.$message.warning('仍未检测到打印助手，请确认程序已运行后重试')
              }
            })
          }
        })
      },
      // 本机打印：把标签渲染为图片，交给本地打印助手直接调用本地打印机
      printViaLocalAgent() {
        var self = this
        var printer = localPrinterName(this.selectedPrinter)
        this.printing = true
        return this.renderLabelToPng().then(function(image) {
          return printToLocalPrinter({
            printer: printer,
            image: image,
            widthMm: self.labelWidthMm,
            heightMm: self.labelHeightMm,
            copies: self.copies
          })
        }).then(function(res) {
          self.printing = false
          if (res && res.code === 200) {
            self.msgSuccess('已发送到打印机：' + printer)
            self.notifyPrintSuccess()
            return true
          }
          self.msgError((res && (res.message || res.msg)) || '打印失败')
          return false
        }).catch(function(err) {
          self.printing = false
          if (err && err.message && err.message.indexOf('未运行') >= 0) {
            self.localAgentOnline = false
            self.showInstallGuide()
          } else {
            self.msgError('打印失败：' + ((err && err.message) || '请确认打印助手已启动'))
          }
          return false
        })
      },
      // 按打印布局把标签绘制为 PNG，供本地打印助手输出
      renderLabelToPng() {
        // 提高源图分辨率，让打印助手按打印机 DPI 重采样时有充足细节
        var scale = 5
        var canvas = document.createElement('canvas')
        canvas.width = Math.round(this.labelWidth * scale)
        canvas.height = Math.round(this.labelHeight * scale)
        var ctx = canvas.getContext('2d')
        ctx.fillStyle = '#fff'
        ctx.fillRect(0, 0, canvas.width, canvas.height)
        ctx.scale(scale, scale)
        var self = this
        var tasks = this.buildLayoutItems().map(function(item) {
          if (item.image) {
            return new Promise(function(resolve) {
              var img = new Image()
              img.onload = function() {
                ctx.drawImage(img, item.x || 0, item.y || 0, item.width || 0, item.height || 0)
                resolve()
              }
              img.onerror = function() { resolve() }
              img.src = item.image
            })
          }
          ctx.fillStyle = '#000'
          ctx.font = (item.fontWeight || 'normal') + ' ' + item.fontSize + 'px sans-serif'
          if ('letterSpacing' in ctx) ctx.letterSpacing = (item.letterSpacing || 0) + 'px'
          ctx.textBaseline = 'top'
          var lh = item.fontSize * (item.lineHeight || 1.3)
          // 文本内容与宽度均来自模板布局结果：折行模式为完整文本，省略模式为已裁切文本
          var lines = self.wrapCanvasText(ctx, String(item.text || ''), item.width)
          // 元素高度按模板；四舍五入以容纳布局算出的实际行高，至少保留一行避免整张标签空白
          var maxLines = item.height ? Math.max(1, Math.round(item.height / lh)) : lines.length
          if (item.width || item.height) {
            ctx.save()
            ctx.beginPath()
            ctx.rect(item.x || 0, item.y || 0, item.width || 1e5, item.height || 1e5)
            ctx.clip()
          }
          for (var i = 0; i < Math.min(lines.length, maxLines); i++) {
            ctx.fillText(lines[i], item.x || 0, (item.y || 0) + i * lh + (lh - item.fontSize) / 2)
          }
          if (item.width || item.height) ctx.restore()
          return Promise.resolve()
        })
        return Promise.all(tasks).then(function() {
          return canvas.toDataURL('image/png')
        })
      },
      /** 画布文字换行：按元素宽度逐字折行，保留显式换行符 */
      wrapCanvasText(ctx, text, maxWidth) {
        if (!maxWidth) return text.split('\n')
        var result = []
        text.split('\n').forEach(function(segment) {
          var line = ''
          for (var i = 0; i < segment.length; i++) {
            var next = line + segment.charAt(i)
            if (line && ctx.measureText(next).width > maxWidth) {
              result.push(line)
              line = segment.charAt(i)
            } else {
              line = next
            }
          }
          result.push(line)
        })
        return result
      }
    }
  }
</script>

<style scoped>
  .lp-size-tip {
    color: #666;
    font-size: 12px;
    margin-bottom: 8px;
  }
  .lp-template-name {
    margin-left: 8px;
    color: #1890ff;
  }
  .lp-main {
    display: flex;
    align-items: flex-start;
  }
  .lp-preview-wrap {
    flex: 1;
    min-width: 0;
    position: relative;
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    gap: 8px;
    min-height: 300px;
    padding: 12px;
    background: #f5f5f5;
    border-radius: 4px;
    overflow: auto;
  }
  .lp-page-badge {
    position: absolute;
    top: 6px;
    right: 6px;
    z-index: 5;
    padding: 1px 7px;
    border-radius: 9px;
    background: rgba(0, 0, 0, 0.55);
    color: #fff;
    font-size: 11px;
    line-height: 16px;
    pointer-events: none;
  }
  .lp-pager {
    flex: none;
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 12px;
    color: #666;
  }
  /* 更多模板：预览下方横向卡片 */
  .lp-template-more {
    flex: none;
    width: 100%;
    margin-top: 10px;
  }
  .lp-template-more-head {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    margin-bottom: 6px;
    font-size: 13px;
    font-weight: 600;
    color: #333;
  }
  .lp-template-more-count {
    font-size: 12px;
    font-weight: 400;
    color: #999;
  }
  .lp-template-cards {
    display: flex;
    gap: 8px;
    overflow-x: auto;
    padding-bottom: 2px;
  }
  .lp-template-card {
    flex: none;
    min-width: 132px;
    padding: 8px 10px;
    display: flex;
    flex-direction: column;
    gap: 2px;
    border: 1px solid #e5e5e5;
    border-radius: 8px;
    background: #fff;
    cursor: pointer;
  }
  .lp-template-card.active {
    border-color: #1890ff;
    box-shadow: inset 0 0 0 1px #1890ff;
  }
  .lp-template-card-name {
    font-size: 13px;
    color: #333;
  }
  .lp-template-card-meta {
    font-size: 11px;
    color: #999;
  }
  .lp-overflow-tip {
    flex: none;
    margin-top: 6px;
    padding: 4px 8px;
    border-radius: 4px;
    background: #fffbe6;
    color: #d48806;
    font-size: 12px;
    line-height: 16px;
    text-align: center;
  }
  /* 打印设置 */
  .lp-settings {
    width: 260px;
    flex: none;
    margin-left: 16px;
  }
  .lp-settings-title {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-weight: bold;
    font-size: 14px;
    padding-bottom: 6px;
    margin-bottom: 12px;
    border-bottom: 1px solid #e8e8e8;
  }
  .lp-refresh {
    font-size: 12px;
    font-weight: normal;
  }
  .lp-field {
    margin-bottom: 12px;
  }
  .lp-field-label {
    font-size: 13px;
    color: #333;
    margin-bottom: 6px;
  }
  /* 「打印机」标题后的安装状态提示 */
  .lp-agent-hint {
    margin-left: 8px;
    font-size: 12px;
    color: #999;
    font-weight: normal;
  }
  .lp-agent-hint a {
    color: #1890ff;
  }
  .lp-tip {
    font-size: 12px;
    color: #999;
    line-height: 1.5;
  }
  .lp-preview {
    position: relative;
    background: #fff;
    border: 0.5px solid rgba(0, 0, 0, 0.16);
    border-radius: 8px;
    overflow: hidden;
    box-sizing: border-box;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
  }
  /* 新版模板自带纸张边框，外层不再重复描边，避免 1~2px 内缩 */
  .lp-preview.lp-preview-schema {
    border: 0.5px solid rgba(0, 0, 0, 0.16);
    border-radius: 8px;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
  }
  .lp-item {
    position: absolute;
    line-height: 1.2;
    overflow: hidden;
    white-space: nowrap;
  }
  .lp-item.is-multiline {
    /* 规格等属性与表格中的排版一致：逐行、左对齐 */
    white-space: pre-line;
    text-align: left;
    line-height: 1.3;
  }
  .lp-item.is-code {
    overflow: visible;
    white-space: normal;
  }
  /* 商品明细表：列宽/行高由商品行数计算，单元格内折行 */
  .lp-goods-table {
    width: 100%;
  }
  .lp-goods-row {
    display: flex;
    align-items: flex-start;
    padding: 0;
    border-top: 1px solid #eee;
    box-sizing: border-box;
    overflow: hidden;
  }
  .lp-goods-head {
    color: #999;
    border-top: none;
  }
  .lp-goods-cell {
    flex: 0 0 auto;
    padding: 2px;
    box-sizing: border-box;
    word-break: break-all;
    overflow: hidden;
  }
  /* 条码 / 二维码：独立元素，只渲染图形 */
  .lp-code-img {
    display: block;
    object-fit: contain;
  }
  .lp-footer {
    margin-top: 16px;
    text-align: right;
  }
  .lp-footer .ant-btn {
    margin-left: 8px;
  }
  /* 手机浏览器：预览在上、打印设置在下 */
  .lp-mobile .lp-main {
    flex-direction: column;
  }
  .lp-mobile .lp-preview-wrap {
    width: 100%;
    min-height: 220px;
    max-height: 50vh;
  }
  .lp-mobile .lp-settings {
    width: 100%;
    margin-left: 0;
    margin-top: 16px;
  }
  .lp-mobile .lp-footer {
    text-align: center;
  }
  .lp-mobile .lp-footer .ant-btn {
    margin: 4px;
  }
  /* 打印机选择：只读展示框 + 添加按钮 */
  .lp-printer-picker {
    display: flex;
    align-items: center;
  }
  .lp-printer-current {
    flex: 1;
    min-width: 0;
    height: 32px;
    line-height: 30px;
    padding: 0 11px;
    border: 1px solid #d9d9d9;
    border-radius: 4px;
    background: #fafafa;
    font-size: 13px;
    color: #333;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    &.empty {
      color: #bfbfbf;
    }
  }
  .lp-printer-add {
    flex: none;
    margin-left: 8px;
  }
  /* ===== 打印机设置弹层（桌面 / 移动共用） ===== */
  .lps-body {
    padding: 4px 0;
  }
  .lps-back {
    display: inline-flex;
    align-items: center;
    font-size: 13px;
    color: #1890ff;
    cursor: pointer;
    margin-bottom: 12px;
    .anticon {
      margin-right: 4px;
    }
  }
  .lps-add-card {
    border: 1px solid #e8e8e8;
    border-radius: 6px;
    padding: 12px;
    margin-bottom: 16px;
  }
  .lps-add-title {
    font-size: 14px;
    font-weight: bold;
    color: #333;
    margin-bottom: 10px;
  }
  .lps-add-btns {
    display: flex;
  }
  .lps-add-btn {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 10px 0;
    border: 1px dashed #d9d9d9;
    border-radius: 6px;
    cursor: pointer;
    color: #595959;
    font-size: 13px;
    transition: all 0.2s;
    & + .lps-add-btn {
      margin-left: 8px;
    }
    .anticon {
      font-size: 20px;
      color: #1890ff;
      margin-bottom: 6px;
    }
    &:hover {
      border-color: #1890ff;
      color: #1890ff;
      background: #e6f7ff;
    }
  }
  .lps-list-title {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 13px;
    color: #333;
    margin-bottom: 8px;
  }
  .lps-list {
    max-height: 300px;
    overflow-y: auto;
    border: 1px solid #f0f0f0;
    border-radius: 6px;
  }
  .lps-item {
    display: flex;
    align-items: center;
    padding: 10px 12px;
    cursor: pointer;
    border-bottom: 1px solid #f5f5f5;
    &:last-child {
      border-bottom: none;
    }
    &:hover {
      background: #fafafa;
    }
    &.active {
      background: #e6f7ff;
    }
  }
  .lps-item-icon {
    font-size: 18px;
    color: #1890ff;
    margin-right: 10px;
  }
  .lps-item-text {
    flex: 1;
    min-width: 0;
  }
  .lps-item-title {
    font-size: 13px;
    color: #333;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .lps-item-sub {
    font-size: 11px;
    color: #999;
    margin-top: 2px;
  }
  .lps-item-del {
    color: #ff4d4f;
    margin-right: 10px;
  }
  .lps-check {
    color: #52c41a;
    font-size: 16px;
  }
  .lps-empty {
    padding: 24px 0;
    text-align: center;
    font-size: 13px;
    color: #999;
  }
  .lps-form {
    border: 1px solid #f0f0f0;
    border-radius: 6px;
    padding: 12px;
  }
  .lps-form-footer {
    margin-top: 14px;
    text-align: right;
    .ant-btn {
      margin-left: 8px;
    }
  }
  /* 添加打印机表单 */
  .lp-add-item {
    margin-bottom: 12px;
  }
  .lp-add-label {
    font-size: 12px;
    color: #666;
    margin-bottom: 4px;
  }
  /* 主内容：中间空白全部为预览区域 */
  .lmp-content {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    padding: 5px 10px 10px;
    display: flex;
    flex-direction: column;
  }
  .lmp-content.lmp-empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
  }
  /* 空状态提示占满宽度，创建模板按钮居中显示 */
  .lmp-content.lmp-empty .ant-alert {
    align-self: stretch;
  }
  /* 无模板提示后的「去添加模版」文字按钮 */
  .lmp-empty-link {
    margin-left: 8px;
    font-weight: 600;
    white-space: nowrap;
  }
  .lmp-empty-create {
    margin-top: 16px;
  }
  /* 模板读取中：读取完成前替代空状态显示 */
  .lmp-loading {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    padding: 24px 0;
    color: rgba(0, 0, 0, 0.45);
  }
  /* 预览区：flex 撑满剩余空间，Math.min 完整显示 */
  .lmp-preview-area {
    position: relative;
    flex: 1;
    min-height: 80px;
    display: flex;
    align-items: flex-start;
    justify-content: center;
    padding: 16px 8px 8px 8px;
    overflow: hidden;
  }
  /* 多标签同页：纵向堆叠，上下滑动查看 */
  .lmp-preview-area.is-stack {
    overflow-y: auto;
    -webkit-overflow-scrolling: touch;
    align-items: flex-start;
    padding-bottom: 16px;
  }
  .lmp-stack {
    width: 100%;
  }
  .lmp-stack-slot {
    width: 100%;
  }
  .lmp-preview-page {
    position: absolute;
  }
  .lmp-page-badge {
    position: absolute;
    top: 4px;
    right: 8px;
    z-index: 5;
    padding: 1px 7px;
    border-radius: 9px;
    background: rgba(0, 0, 0, 0.55);
    color: #fff;
    font-size: 11px;
    line-height: 16px;
    pointer-events: none;
  }
  .lmp-pager {
    flex: none;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    padding: 2px 0 4px;
    font-size: 12px;
    color: #666;
  }
  /* 更多模板：预览下方横向卡片 */
  .lmp-template-more {
    flex: none;
    margin: 4px 8px 8px;
  }
  .lmp-template-more-head {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    margin-bottom: 6px;
    font-size: 13px;
    font-weight: 600;
    color: #333;
  }
  .lmp-template-more-count {
    font-size: 12px;
    font-weight: 400;
    color: #999;
  }
  .lmp-template-cards {
    display: flex;
    gap: 8px;
    overflow-x: auto;
    padding-bottom: 2px;
    -webkit-overflow-scrolling: touch;
  }
  .lmp-template-card {
    flex: none;
    min-width: 118px;
    padding: 8px 10px;
    display: flex;
    flex-direction: column;
    gap: 2px;
    border: 1px solid #e5e5e5;
    border-radius: 8px;
    background: #fff;
  }
  .lmp-template-card.active {
    border-color: #1890ff;
    box-shadow: inset 0 0 0 1px #1890ff;
  }
  .lmp-template-card-name {
    font-size: 13px;
    color: #333;
  }
  .lmp-template-card-meta {
    font-size: 11px;
    color: #999;
  }
  .lmp-overflow-tip {
    flex: none;
    margin: 0 8px 4px;
    padding: 4px 8px;
    border-radius: 4px;
    background: #fffbe6;
    color: #d48806;
    font-size: 12px;
    line-height: 16px;
    text-align: center;
  }
  /* 预览标签（直接在灰色背景上，无白色卡片包裹） */
  .lmp-preview {
    position: relative;
    background: #fff;
    overflow: hidden;
    /* 圆角 + 细边线 */
    border: 0.5px solid rgba(0, 0, 0, 0.16);
    border-radius: 8px;
    box-sizing: border-box;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
    transform-origin: center center;
    transition: transform 0.2s ease;
  }
  /* 底部单列纵向控制区 */
  .lmp-bottom-wrap {
    flex: none;
    margin-top: 5px;
    display: flex;
    flex-direction: column;
    gap: 1px;
  }
  /* 统一控制行：左标签 + 右值 */
  .lmp-control-row {
    background: #fff;
    border-radius: 10px;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
    display: flex;
    align-items: center;
    padding: 12px 14px;
    cursor: pointer;
  }
  .lmp-control-row:active {
    background: #fafafa;
  }
  .lmp-control-label {
    font-size: 14px;
    color: #262626;
    font-weight: 500;
    flex: none;
    min-width: 72px;
  }
  .lmp-control-value {
    flex: 1;
    min-width: 0;
    display: flex;
    justify-content: flex-end;
    align-items: center;
    font-size: 14px;
    color: #333;
  }
  /* 设备状态行：中间名称居中对齐 */
  .lmp-control-value-center {
    justify-content: center;
    text-align: center;
  }
  .lmp-device-name {
    font-size: 14px;
    display: inline-block;
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    &.online { color: #1890ff; }
    &.offline { color: #bfbfbf; }
  }
  .lmp-row-arrow {
    color: #ccc;
    font-size: 14px;
    flex: none;
    margin-left: 8px;
  }
  /* 打印方向选项 */
  .lmp-rotation-tabs {
    display: flex;
    gap: 5px;
  }
  .lmp-rot-tab {
    display: flex;
    align-items: center;
    gap: 2px;
    padding: 4px 10px;
    border-radius: 14px;
    border: 1px solid #e0e0e0;
    font-size: 12px;
    color: #666;
    cursor: pointer;
    background: #fafafa;
    transition: all 0.15s;
    .anticon {
      font-size: 12px;
    }
    &.active {
      background: #1890ff;
      border-color: #1890ff;
      color: #fff;
    }
  }
  /* 打印份数 */
  .lmp-qty {
    display: flex;
    align-items: center;
    gap: 6px;
  }
  .lmp-qty-btn {
    width: 26px;
    height: 26px;
    border-radius: 50%;
    border: 1px solid #e0e0e0;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #666;
    cursor: pointer;
    background: #fff;
    font-size: 12px;
    &:active { background: #f0f0f0; }
    &.disabled { color: #ddd; cursor: not-allowed; }
  }
  .lmp-qty-num {
    font-size: 14px;
    color: #262626;
    min-width: 28px;
    text-align: center;
    font-weight: 500;
  }
  /* 底部打印按钮（位于标准弹层 footer 插槽内） */
  .lmp-footer {
    padding: 12px 16px calc(12px + env(safe-area-inset-bottom));
    background: #fff;
    border-top: 1px solid #eee;
  }
  .lmp-print-hint {
    margin-bottom: 8px;
    font-size: 12px;
    line-height: 1.5;
    color: #f5222d;
    text-align: center;
    word-break: break-all;
  }
  .lmp-print-btn {
    height: 46px;
    font-size: 16px;
    border-radius: 23px;
    background: #1890ff;
    border-color: #1890ff;
    font-weight: 500;
  }
  /* ===== 设备列表页（lmpl = label mobile printer list） ===== */
  /* 类型切换：底部栏 */
  .lmpl-bottom-tabs {
    display: flex;
    height: calc(58px + env(safe-area-inset-bottom));
    padding-bottom: env(safe-area-inset-bottom);
    box-sizing: border-box;
    border-top: 1px solid #f0f0f0;
  }
  .lmpl-bottom-tab {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    position: relative;
    color: #8c8c8c;
    font-size: 14px;
    cursor: pointer;
    transition: color 0.15s;
  }
  .lmpl-bottom-tab.active {
    color: #1890ff;
    font-weight: 600;
  }
  .lmpl-bottom-tab.active::after {
    content: '';
    position: absolute;
    top: 0;
    left: 50%;
    transform: translateX(-50%);
    width: 28px;
    height: 3px;
    border-radius: 0 0 2px 2px;
    background: #1890ff;
  }
  /* 弹层内容区：列表与添加入口可滚动 */
  .lmpl-body {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    -webkit-overflow-scrolling: touch;
  }
  .lmpl-footer-add {
    padding: 12px 12px 14px;
  }
  .lmpl-footer-add .lmpl-add-btn + .lmpl-add-btn {
    margin-top: 8px;
  }
  .lmpl-list {
    padding: 10px;
  }
  .lmpl-item {
    display: flex;
    align-items: center;
    padding: 14px 16px;
    background: #fff;
    border-radius: 10px;
    margin-bottom: 8px;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
    cursor: pointer;
  }
  .lmpl-item.active {
    background: #e6f7ff;
  }
  .lmpl-item-left {
    flex: 1;
    min-width: 0;
    display: flex;
    align-items: center;
    gap: 12px;
  }
  .lmpl-item-icon {
    font-size: 22px;
    color: #1890ff;
    flex: none;
  }
  .lmpl-section-title {
    padding: 4px 2px 8px;
    font-size: 13px;
    color: #8c8c8c;
  }
  .lmpl-status {
    flex: none;
    padding: 1px 8px;
    border-radius: 10px;
    font-size: 12px;
    line-height: 18px;
  }
  .lmpl-status.online {
    color: #52c41a;
    background: #f6ffed;
    border: 1px solid #b7eb8f;
  }
  .lmpl-status.offline {
    color: #8c8c8c;
    background: #fafafa;
    border: 1px solid #d9d9d9;
  }
  .lmpl-edit {
    flex: none;
    margin-left: 8px;
    padding: 1px 10px;
    border-radius: 10px;
    font-size: 12px;
    line-height: 18px;
    color: #1890ff;
    border: 1px solid #91d5ff;
    background: #e6f7ff;
  }
  .lmpl-item-text {
    flex: 1;
    min-width: 0;
  }
  .lmpl-item-title {
    font-size: 15px;
    color: #262626;
    font-weight: 500;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .lmpl-item-sub {
    font-size: 12px;
    color: #999;
    margin-top: 2px;
  }
  .lmpl-check {
    color: #1890ff;
    font-size: 18px;
    flex: none;
    margin-left: 8px;
  }
  .lmpl-tag {
    margin-right: 8px;
    font-size: 11px;
    padding: 0 6px;
  }
  /* 空状态 */
  .lmpl-empty {
    padding: 60px 20px 40px;
    text-align: center;
    background: #fff;
    border-radius: 10px;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  }
  .lmpl-empty-icon {
    width: 80px;
    height: 80px;
    margin: 0 auto 20px;
    border-radius: 50%;
    background: #f0f5ff;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 36px;
    color: #1890ff;
    opacity: 0.7;
  }
  .lmpl-empty-text {
    font-size: 16px;
    color: #333;
    margin-bottom: 6px;
  }
  .lmpl-empty-sub {
    font-size: 13px;
    color: #999;
    margin-bottom: 20px;
  }
  .lmpl-add-btn {
    margin-top: 12px;
    height: 40px;
    font-size: 14px;
    border-radius: 20px;
  }
  /* ===== 选择品牌页（lmb = label mobile brand） ===== */
  .lmb-grid {
    flex: 1;
    overflow-y: auto;
    background: #fff;
    padding: 12px 6px 24px;
    display: flex;
    flex-wrap: wrap;
    align-content: flex-start;
  }
  .lmb-cell {
    width: 33.33%;
    padding: 16px 8px 12px;
    text-align: center;
    cursor: pointer;
  }
  .lmb-logo {
    height: 56px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 19px;
    font-weight: 700;
    letter-spacing: 0.5px;
    white-space: nowrap;
    overflow: hidden;
  }
  .lmb-name {
    margin-top: 8px;
    font-size: 14px;
    color: #333;
  }
  .lmb-cmd {
    margin: 8px auto 0;
    width: 88px;
    height: 26px;
    border: 1px solid #1890ff;
    border-radius: 13px;
    color: #1890ff;
    font-size: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 3px;
  }
  .lmb-cmd .anticon {
    font-size: 10px;
  }
  /* 信号传输格式底部弹层 */
  .lmb-sheet-mask {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: rgba(0, 0, 0, 0.45);
    z-index: 3002;
  }
  .lmb-sheet {
    position: absolute;
    left: 8px;
    right: 8px;
    bottom: calc(8px + env(safe-area-inset-bottom));
  }
  .lmb-sheet-group {
    background: #fff;
    border-radius: 12px;
    overflow: hidden;
  }
  .lmb-sheet-item {
    padding: 15px 0;
    text-align: center;
    font-size: 16px;
    color: #1890ff;
    border-bottom: 1px solid #f0f0f0;
    cursor: pointer;
  }
  .lmb-sheet-item:last-child {
    border-bottom: none;
  }
  .lmb-sheet-cancel {
    margin-top: 8px;
    background: #fff;
    border-radius: 12px;
    padding: 15px 0;
    text-align: center;
    font-size: 16px;
    color: #1890ff;
    cursor: pointer;
  }
  /* ===== Wi-Fi 打印配置页（lmw = label mobile wifi） ===== */
  .lmw-form {
    flex: 1;
    overflow-y: auto;
    padding: 10px;
  }
  .lmw-card {
    background: #fff;
    border-radius: 10px;
    padding: 0 12px;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  }
  .lmw-row {
    display: flex;
    align-items: center;
    min-height: 48px;
    border-bottom: 1px solid #f5f5f5;
  }
  .lmw-row:last-child {
    border-bottom: none;
  }
  .lmw-label {
    width: 80px;
    flex: none;
    font-size: 14px;
    color: #333;
  }
  .lmw-value {
    flex: 1;
    font-size: 14px;
    color: #666;
  }
  .lmw-input {
    flex: 1;
    border: none;
    outline: none;
    font-size: 14px;
    color: #333;
    padding: 8px 0;
    background: transparent;
  }
  .lmw-input::placeholder {
    color: #bbb;
  }
  .lmw-tip {
    margin-top: 10px;
    padding: 0 4px;
    font-size: 12px;
    color: #999;
  }
  /* ===== 云打印配对配置页（lmc = label mobile cloud） ===== */
  .lmc-form {
    flex: none;
    padding: 10px 10px 0;
  }
  .lmc-tip {
    margin-top: 10px;
    padding: 0 4px;
    font-size: 12px;
    line-height: 1.6;
    color: #999;
  }
  .lmc-section {
    margin: 16px 4px 0;
    font-size: 13px;
    color: #666;
  }
  .lmc-grid {
    padding-top: 4px;
  }
  .lmb-cell.active .lmb-name {
    color: #1890ff;
    font-weight: 600;
  }
  .lmb-cell.active .lmb-cmd {
    background: #1890ff;
    color: #fff;
  }
</style>

<!-- 弹层容器类挂在组件根节点之外，需用非 scoped 样式 -->
<style>
  .lp-modal-mobile .ant-modal {
    top: 0;
    margin: 0;
    max-width: 100vw;
    padding-bottom: 0;
  }
  .lp-modal-mobile .ant-modal-content {
    border-radius: 0;
    min-height: 100vh;
  }
</style>
