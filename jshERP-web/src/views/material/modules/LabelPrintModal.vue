/**
 * 打印标签预览弹层
 *
 * 交互：商品信息页点击「打印标签」→ 本弹层按当前商品所用标签模板的实际尺寸显示预览
 *   - 打印：按模板直接打印（不进入编辑界面）
 *   - 编辑：交给标签编辑弹层（LabelEditModal）修改模板/样式
 */
<template>
  <div>
  <!-- 手机端：两个全屏页面（打印标签页 / 设备列表页） -->
  <template v-if="isMobile">
    <!-- 页面 1：打印标签预览（标准页面弹层：标题居中 + 右侧关闭，无返回） -->
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
          <a-alert type="info" show-icon :message="emptyMessage" description="可点击右上角编辑图标创建模板" />
          <a-button
            v-if="type === 'member' || type === 'address'"
            type="primary"
            size="large"
            class="lmp-empty-create"
            icon="plus"
            @click="handleCreateTemplate">{{ type === 'address' ? '创建地址打印模板' : '创建会员标签模板' }}</a-button>
        </template>
        <template v-else>
          <!-- 预览区（取消卡片，标签缩放填满空白） -->
          <div class="lmp-preview-area" ref="previewArea">
            <div class="lmp-preview" :style="previewStyle">
              <!-- 新版模板：按 elements 直接渲染（mm 坐标、条码/二维码、明细循环） -->
              <label-paper
                v-if="templateSchema"
                :schema="templateSchema"
                :values="attrValues"
                :rows="detailRows"
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
                  <template v-else>
                    <template v-if="attrLabels[item.attr.key]">{{ attrLabels[item.attr.key] }}: </template>{{ item.attr.value }}
                  </template>
                </div>
              </template>
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
          <a-button
            type="primary"
            block
            size="large"
            class="lmp-print-btn"
            :disabled="!template || !canPrintMobile"
            :loading="printing"
            @click="handlePrint"
          >打印</a-button>
        </div>
      </template>
    </mobile-modal-page>

    <!-- 页面 2：设备列表（蓝顶栏 + 下划线 tab + 打印机列表） -->
    <div v-if="visible && showPrinterSheet && !wifiAddStep" class="lp-mobile-page">
      <!-- 顶栏：蓝底 + 白色返回 + 标题 -->
      <div class="lmp-header lmp-header-blue">
        <a-icon class="lmp-back" type="arrow-left" @click="showPrinterSheet = false" />
        <div class="lmp-title-block">
          <div class="lmp-title">设备列表</div>
        </div>
        <span class="lmp-settings placeholder" />
      </div>

      <!-- 添加打印机卡片（与桌面共用同一套入口逻辑） -->
      <div class="lps-add-card lmpl-add-card">
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

      <!-- 类型 Tab：下划线风格 -->
      <div class="lmpl-tabs-bar">
        <div
          v-for="t in printerTabList"
          :key="t.key"
          class="lmpl-tab"
          :class="{ active: printerType === t.key }"
          @click="switchPrinterType(t.key); if (printerType === t.key) loadPrinters()"
        >{{ t.label }}</div>
      </div>

      <!-- 列表区 -->
      <a-spin :spinning="loadingPrinters" tip="加载中...">
        <div class="lmpl-list">
          <!-- 后端打印机 -->
          <template v-if="printerType === 'backend'">
            <template v-if="printers.length > 0">
              <div
                v-for="p in printers"
                :key="p.name"
                class="lmpl-item"
                :class="{ active: selectedPrinter === p.name }"
                @click="selectPrinterAndClose(p.name)"
              >
                <div class="lmpl-item-left">
                  <a-icon type="printer" class="lmpl-item-icon" />
                  <div class="lmpl-item-text">
                    <div class="lmpl-item-title">{{ p.name }}</div>
                    <div v-if="p.isDefault" class="lmpl-item-sub">默认打印机</div>
                  </div>
                </div>
                <a-icon v-if="selectedPrinter === p.name" type="check-circle" theme="filled" class="lmpl-check" />
              </div>
            </template>
            <div v-else class="lmpl-empty">
              <div class="lmpl-empty-icon"><a-icon type="printer" /></div>
              <div class="lmpl-empty-text">暂无可用打印机</div>
              <div class="lmpl-empty-sub">请在后端电脑安装打印机后刷新</div>
            </div>
          </template>

          <!-- 蓝牙 -->
          <template v-else-if="printerType === 'bluetooth'">
            <template v-if="btDevice">
              <div class="lmpl-item active" @click="selectPrinterAndClose(btDevice.id)">
                <div class="lmpl-item-left">
                  <a-icon type="bluetooth" class="lmpl-item-icon" />
                  <div class="lmpl-item-text">
                    <div class="lmpl-item-title">{{ btDevice.name }}</div>
                    <div class="lmpl-item-sub">已连接</div>
                  </div>
                </div>
                <a-icon type="check-circle" theme="filled" class="lmpl-check" />
              </div>
            </template>
            <div v-else class="lmpl-empty">
              <div class="lmpl-empty-icon"><a-icon type="bluetooth" /></div>
              <div class="lmpl-empty-text">未连接过你的设备</div>
              <a-button type="primary" block :loading="btSearching" class="lmpl-primary-btn" @click="handleBluetoothSearch">
                {{ btSearching ? '搜索中...' : '搜索蓝牙打印机' }}
              </a-button>
            </div>
          </template>

          <!-- Wi-Fi -->
          <template v-else-if="printerType === 'wifi'">
            <template v-if="wifiPrinters.length > 0">
              <div
                v-for="p in wifiPrinters"
                :key="p.id"
                class="lmpl-item"
                :class="{ active: activeWifiId === p.id }"
                @click="selectPrinterAndClose(p.id)"
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
                <a-icon v-if="selectedPrinter === p.id" type="check-circle" theme="filled" class="lmpl-check" />
              </div>
              <a-button type="primary" ghost block icon="plus" class="lmpl-add-btn" @click="openAddWifiPrinter">添加 Wi-Fi 打印机</a-button>
            </template>
            <div v-else class="lmpl-empty">
              <div class="lmpl-empty-icon"><a-icon type="wifi" /></div>
              <div class="lmpl-empty-text">未连接过你的设备</div>
              <a-button type="primary" block class="lmpl-primary-btn" @click="openAddWifiPrinter">添加打印机</a-button>
            </div>
          </template>

          <!-- 云打印 -->
          <template v-else-if="printerType === 'cloud'">
            <div class="lmpl-item" :class="{ active: discoveredPrinter && discoveredPrinter.type === 'cloud' }" @click="selectCloudPrinter(); showPrinterSheet = false">
              <div class="lmpl-item-left">
                <a-icon type="cloud" class="lmpl-item-icon" />
                <div class="lmpl-item-text">
                  <div class="lmpl-item-title">系统打印机</div>
                  <div class="lmpl-item-sub">通过浏览器打印</div>
                </div>
              </div>
              <a-icon v-if="discoveredPrinter && discoveredPrinter.type === 'cloud'" type="check-circle" theme="filled" class="lmpl-check" />
            </div>
          </template>
        </div>
      </a-spin>
    </div>

    <!-- 页面 3：选择品牌（添加 Wi-Fi 打印机第一步） -->
    <div v-if="visible && showPrinterSheet && wifiAddStep === 'brand'" class="lp-mobile-page">
      <div class="lmp-header lmp-header-blue">
        <a-icon class="lmp-back" type="arrow-left" @click="wifiAddStep = ''" />
        <div class="lmp-title-block">
          <div class="lmp-title">选择品牌</div>
        </div>
        <span class="lmp-settings placeholder" />
      </div>
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
    </div>

    <!-- 页面 4：Wi-Fi 打印配置（添加 Wi-Fi 打印机第二步） -->
    <div v-if="visible && showPrinterSheet && wifiAddStep === 'config'" class="lp-mobile-page">
      <div class="lmp-header lmp-header-blue">
        <a-icon class="lmp-back" type="arrow-left" @click="wifiAddStep = 'brand'" />
        <div class="lmp-title-block">
          <div class="lmp-title">Wi-Fi 打印配置</div>
        </div>
        <span class="lmp-settings placeholder" />
      </div>
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
    </div>
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
              <!-- 新版模板：按 elements 直接渲染（mm 坐标、条码/二维码、明细循环） -->
              <label-paper
                v-if="templateSchema"
                :schema="templateSchema"
                :values="attrValues"
                :rows="detailRows"
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
                  <template v-else>
                    <template v-if="attrLabels[item.attr.key]">{{ attrLabels[item.attr.key] }}: </template>{{ item.attr.value }}
                  </template>
                </div>
              </template>
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
  </div>
</template>

<script>
  import { buildLabelAttrs } from '@/views/material/utils/materialDisplay'
  import { printLabel } from '@/views/material/utils/labelPrint'
  import { fitCodeSize, renderCodeDataUrl, isCodeKey, codeSourceKey, migrateCodeStyles, CODE_KEY_PREFIX } from '@/views/material/utils/labelCode'
  import { mmToPx, schemaToLegacy } from '@/views/material/utils/labelSchema'
  import { fetchLabelTemplates } from '@/views/material/utils/labelTemplateStore'
  import { fetchDeliveryTemplate } from '@/views/bill/utils/deliveryNoteTemplateStore'
  import { resolveTemplateData } from '@/views/material/utils/labelDataResolver'
  import LabelPaper from '@/views/material/modules/LabelPaper'
  import MobileModalPage from '@/components/mobile/MobileModalPage'
  import { getAction, postAction } from '@/api/manage'
  import {
    DEFAULT_PRINTER_PORT,
    getPrinterCapabilities,
    searchBluetoothPrinter,
    getCloudPrinterInfo,
    getWifiPrinters,
    saveWifiPrinter,
    removeWifiPrinter,
    testWifiPrinter,
    printToWifiPrinter
  } from '@/utils/printerSearch'
  import { fetchLocalPrinters, printToLocalPrinter, isLocalPrinter, localPrinterName, LOCAL_PRINTER_PREFIX, detectLocalAgent, LOCAL_AGENT_DOWNLOAD_URL } from '@/utils/localPrintAgent'
  var PX_PER_MM = 3.779528
  export default {
    name: 'LabelPrintModal',
    components: { LabelPaper, MobileModalPage },
    data() {
      return {
        visible: false,
        record: {},
        sku: {},
        categoryTree: [],
        // 当前模板业务类型：label / delivery / receipt
        type: 'label',
        // 打印页标题（调用方可覆盖，如地址识别页传「地址打印」）
        printTitle: '',
        template: null,
        allAttrs: [],
        selectedAttrs: [],
        attrStyles: {},
        attrLabels: {},
        // 数据源明细行（新版模板 table 元素循环渲染用）
        detailRows: [],
        // 条码/二维码图片缓存（key → dataURL）
        codeImages: {},
        labelWidth: 320,
        labelHeight: 240,
        // 打印机（由后端枚举本机打印机）
        printers: [],
        // 本机打印机（由本地打印助手提供，仅电脑端可用）
        localPrinters: [],
        localAgentOnline: false,
        agentDownloadUrl: LOCAL_AGENT_DOWNLOAD_URL,
        selectedPrinter: undefined,
        loadingPrinters: false,
        printing: false,
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
      // 打印机类型标签列表
      printerTabList() {
        var caps = this.printerCaps
        return [
          { key: 'backend', label: '后端', icon: 'desktop' },
          { key: 'bluetooth', label: '蓝牙', icon: 'bluetooth' },
          { key: 'wifi', label: 'Wi-Fi', icon: 'wifi' },
          { key: 'cloud', label: '云', icon: 'cloud' }
        ].filter(function(t) {
          return t.key === 'backend' || caps[t.key]
        })
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
          if (this.selectedPrinter) return this.selectedPrinter
          return ''
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
        return !!this.mobileDeviceLabel
      },
      canPrintMobile() {
        if (this.printerType === 'cloud') return true
        if (this.printerType === 'bluetooth') return !!this.btDevice
        return !!this.selectedPrinter
      },
      printerTypeLabel() {
        var map = { backend: '后端', bluetooth: '蓝牙', wifi: 'Wi-Fi', cloud: '云打印' }
        return map[this.printerType] || '后端'
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
        return map
      },
      renderItems() {
        var items = []
        var styles = this.attrStyles
        this.selectedAttrList.forEach(function(attr) {
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
          return [item.key, s.codeType || '', item.attr.value == null ? '' : item.attr.value, s.width || '', s.height || ''].join('|')
        }).join(';')
      }
    },
    watch: {
      codeSignature() {
        this.refreshCodeImages()
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
      // 手机端：按容器尺寸等比缩放小票/标签
      //   宽度约束：容器宽 - 左右各 8px
      //   高度约束：容器高 - 上边距 16px - 底部控件区
      //   最终 scale = Math.min(scaleW, scaleH) → 完整显示不溢出
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
          self.previewScale = Math.min(scaleW, scaleH)
        })
      },
      // 是否为独立编码元素
      isCodeItem(key) {
        return isCodeKey(key)
      },
      // 编码元素的图形布局：尺寸取元素自身宽高，按打印机能力换算
      codeLayout(codeKey) {
        var s = this.attrStyles[codeKey] || {}
        var isQr = s.codeType === 'qrcode'
        var sourceKey = codeSourceKey(codeKey)
        var attr = this.allAttrs.find(function(a) { return a.key === sourceKey })
        var value = attr && attr.value != null ? attr.value : ''
        var boxWidth = s.width || (isQr ? 80 : 160)
        var boxHeight = s.height || (isQr ? 80 : 50)
        var fitted = fitCodeSize(s.codeType, value, boxWidth, boxHeight)
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
        this.type = type || 'delivery'
        this.record = {}
        this.sku = {}
        this.allAttrs = this.buildAttrList()
        this.copies = 1
        this.printRotation = 0
        this.discoveredPrinter = null
        this.btDevice = null
        this.activeWifiId = null
        this.showPrinterSheet = false
        this.wifiAddStep = ''
        this.visible = true
        this.loadRouteTemplate({ type: this.type, templateId: opts.templateId, name: opts.name }).then(function(raw) {
          var tpl = self.normalizeTemplate(raw)
          if (tpl) {
            self.applyTemplate(tpl, true)
            self.restoreMissingAttrs(self.selectedAttrs)
            self.applySnapshotValues()
          } else {
            self.applySnapshot()
          }
          self.$nextTick(function() { self.updatePreviewScale() })
        }).catch(function() {
          self.applySnapshot()
          self.$nextTick(function() { self.updatePreviewScale() })
        })
        // 恢复上次使用的打印机并加载设备列表，保证「打印」按钮可用
        var last = null
        try { last = JSON.parse(localStorage.getItem('jsh.lastLabelPrinter') || 'null') } catch (e) { last = null }
        if (last && last.type) {
          this.printerType = last.type
          if (last.type === 'backend') this.selectedPrinter = last.name || undefined
          else if (last.type === 'wifi') { this.activeWifiId = last.name || null; this.selectedPrinter = last.name || undefined }
          else this.selectedPrinter = undefined
        }
        this.loadPrinters()
        this.loadWifiPrinters()
        if (this.isMobile) {
          this.$nextTick(function() { self.updatePreviewScale() })
          setTimeout(function() { self.updatePreviewScale() }, 300)
        }
      },
      open(record, sku, categoryTree, preferredTpl) {
        this.directEntry = false
        this.printTitle = ''
        this.record = record || {}
        this.sku = sku || {}
        this.categoryTree = categoryTree || []
        this.allAttrs = this.buildAttrList()
        if (preferredTpl) {
          // 由标签编辑页打开时直接使用其当前编辑内容，保持所见即所得
          this.template = null
          this.applyTemplate(preferredTpl)
        } else {
          this.loadTemplate()
        }
        this.copies = 1
        this.printRotation = 0
        this.discoveredPrinter = null
        this.btDevice = null
        this.activeWifiId = null
        this.showPrinterSheet = false
        this.wifiAddStep = ''
        // 恢复上次使用的打印机（localStorage）
        var last = null
        try { last = JSON.parse(localStorage.getItem('jsh.lastLabelPrinter') || 'null') } catch (e) { last = null }
        if (last && last.type) {
          this.printerType = last.type
          if (last.type === 'backend') this.selectedPrinter = last.name || undefined
          else if (last.type === 'wifi') { this.activeWifiId = last.name || null; this.selectedPrinter = last.name || undefined }
          else if (last.type === 'cloud') { this.selectedPrinter = undefined }
          else { this.selectedPrinter = undefined }
        } else {
          this.printerType = 'backend'
          this.selectedPrinter = undefined
        }
        this.loadPrinters()
        this.loadWifiPrinters()
        this.visible = true
        // 手机端：打开后计算预览缩放（等 DOM 渲染完）
        if (this.isMobile) {
          var self = this
          this.$nextTick(function() { self.updatePreviewScale() })
          // 模板可能异步加载，延迟再算一次
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
        this.loadPrinters()
        this.showPrinterSheet = true
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
      // 切换打印机类型
      switchPrinterType(type) {
        this.printerType = type
        this.selectedPrinter = undefined
        this.discoveredPrinter = null
        if (type === 'backend') this.loadPrinters()
        if (type === 'wifi') this.loadWifiPrinters()
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
      // 选择云打印机
      selectCloudPrinter() {
        var cloud = getCloudPrinterInfo()
        this.selectDiscoveredPrinter(cloud)
      },
      handleCancel() {
        this.visible = false
      },
      // 模板优先级：当前商品的普通模板 > 品类的默认模板（云端读取，本地缓存兜底）
      loadTemplate() {
        var self = this
        fetchLabelTemplates().then(function(templates) {
          var record = self.record
          var normal = templates.find(function(t) {
            return t.type === 'normal' && String(t.materialId) === String(record.id)
          })
          var defaultTpl = templates.find(function(t) {
            return t.type === 'default' && String(t.categoryId) === String(record.categoryId)
          })
          self.applyTemplate(normal || defaultTpl || null)
        })
      },
      // 路由页：按类型从统一模板存储读取模板（label 支持按 templateId/name 定位）
      // 会员标签 / 地址打印 / 小票各自独立的 platformConfig key，互不复用
      loadRouteTemplate(q) {
        if (this.type === 'delivery') {
          return fetchDeliveryTemplate().then(function(tpl) {
            return tpl || null
          }).catch(function() { return null })
        }
        var configs = {
          member: { key: 'member_layout', name: '会员标签模板', width: 320, height: 240, businessType: 'member.layout' },
          address: { key: 'address_layout', name: '地址打印模板', width: 320, height: 240, businessType: 'address.layout' },
          receipt: { key: 'receipt_layout', name: '小票模板', width: 380, height: 600, businessType: 'receipt.layout' }
        }
        var cfgInfo = configs[this.type]
        if (cfgInfo) {
          var cfgKey = cfgInfo.key
          return getAction('/platformConfig/getUiConfig', { key: cfgKey }).then(function(res) {
            var cfg = res && res.code === 200 ? res.data : null
            if (typeof cfg === 'string') {
              try { cfg = JSON.parse(cfg) } catch (e) { cfg = null }
            }
            if (cfg && cfg.config != null && !cfg.selectedAttrs && !cfg.elements) cfg = cfg.config
            if (!cfg || !cfg.selectedAttrs || !cfg.selectedAttrs.length) return null
            return {
              id: cfgKey,
              name: cfg.name || cfgInfo.name,
              businessType: cfgInfo.businessType,
              selectedAttrs: cfg.selectedAttrs,
              attrStyles: cfg.attrStyles || {},
              attrLabels: cfg.attrLabels || {},
              labelWidth: cfg.width || cfg.labelWidth || cfgInfo.width,
              labelHeight: cfg.height || cfg.labelHeight || cfgInfo.height
            }
          }).catch(function() { return null })
        }
        return fetchLabelTemplates().then(function(list) {
          var id = q && q.templateId
          var tpl = null
          if (id) {
            tpl = (list || []).find(function(t) {
              return String(t.serverId) === String(id) || String(t.id) === String(id)
            })
          }
          if (!tpl && q && q.name) {
            tpl = (list || []).find(function(t) { return t.name === q.name })
          }
          return tpl || null
        }).catch(function() { return null })
      },
      // 模板结构标准化：把「统一模板表 v3 elements / 旧 platformConfig / 编辑器快照」
      // 统一转换成本页预览使用的编辑器结构，避免预览模块先于标准化读原始结构导致不兼容
      normalizeTemplate(raw) {
        var names = { label: '标签模板', delivery: '发货单模板', receipt: '小票模板', member: '会员标签模板', address: '地址打印模板' }
        var src = raw
        if (typeof src === 'string') {
          try { src = JSON.parse(src) } catch (e) { return null }
        }
        if (!src || typeof src !== 'object') return null
        // getUiConfig 的 { config: ... } 包装
        if (src.config != null && !src.selectedAttrs && !src.elements) {
          var inner = src.config
          if (typeof inner === 'string') {
            try { inner = JSON.parse(inner) } catch (e) { inner = null }
          }
          if (inner && typeof inner === 'object') src = inner
        }
        // v3 模板：elements 为准，编辑器结构由 schema 反推
        var schema = src.elements && Array.isArray(src.elements)
          ? src
          : (src.schema && Array.isArray(src.schema.elements) ? src.schema : null)
        var base = schema ? schemaToLegacy(schema) : src
        var selectedAttrs = Array.isArray(base && base.selectedAttrs) ? base.selectedAttrs : []
        selectedAttrs = selectedAttrs
          .filter(function(key) { return key != null && key !== '' })
          .map(function(key) { return String(key) })
        // 没有任何可渲染字段 → 视为无模板（走空状态/快照兜底）
        if (!selectedAttrs.length) return null
        var size = (schema && schema.size) || {}
        var defaults = { label: [320, 240], delivery: [560, 800], receipt: [380, 600], member: [320, 240], address: [320, 240] }
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
          schema: schema
        }
      },
      // 路由页兜底：存储里没有模板时，用编辑页快照还原预览
      applySnapshot() {
        var s = null
        try { s = JSON.parse(sessionStorage.getItem('jsh.labelEdit.snapshot') || 'null') } catch (e) { s = null }
        var tpl = this.normalizeTemplate(s)
        if (!tpl) return
        this.applyTemplate(tpl, true)
        this.applySnapshotValues()
      },
      // 快照里的业务值：模板结构标准化完成后按字段 key 回填，保证预览读的是统一结构
      applySnapshotValues() {
        var s = null
        try { s = JSON.parse(sessionStorage.getItem('jsh.labelEdit.snapshot') || 'null') } catch (e) { s = null }
        if (!s || !s.values || typeof s.values !== 'object') return
        var self = this
        var exists = {}
        this.allAttrs.forEach(function(attr) { exists[attr.key] = true })
        Object.keys(s.values).forEach(function(key) {
          if (exists[key]) {
            var attr = self.allAttrs.find(function(a) { return a.key === key })
            if (attr) attr.value = s.values[key]
            return
          }
          self.allAttrs.push({ key: key, label: self.attrLabels[key] || '', value: s.values[key], isCustom: true, group: '自定义' })
          exists[key] = true
        })
        if (Array.isArray(s.rows)) this.detailRows = s.rows
      },
      applyTemplate(tpl, skipProvider) {
        var self = this
        this.template = tpl
        this.selectedAttrs = []
        this.attrStyles = {}
        this.attrLabels = {}
        this.detailRows = []
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
          sku: this.sku
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
        })
      },
      itemStyle(key) {
        var s = this.attrStyles[key]
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
        var style = {
          left: s.x + 'px',
          top: s.y + 'px',
          fontSize: s.fontSize + 'px',
          fontWeight: s.fontWeight,
          letterSpacing: (s.letterSpacing || 0) + 'px',
          lineHeight: String(s.lineHeight || 1.3)
        }
        // 指定宽度：文字按框宽自动换行；指定高度：超出部分不打印（裁剪）
        if (s.width) {
          style.width = s.width + 'px'
          style.whiteSpace = 'pre-wrap'
          style.wordBreak = 'break-all'
          style.overflowWrap = 'anywhere'
        }
        if (s.height) {
          style.height = s.height + 'px'
          style.overflow = 'hidden'
        }
        return style
      },
      // 与标签编辑弹层共用同一套属性来源，避免模板里的属性在这里丢失
      buildAttrList() {
        // 地址打印：字段只取地址识别页面的输入框数据
        if (this.type === 'address') return this.buildAddressAttrs()
        return buildLabelAttrs(this.record, this.sku)
      },
      /** 地址打印字段（key 与地址识别页面 form 字段一致） */
      buildAddressAttrs() {
        var r = this.record || {}
        var mk = function(key, label, value) {
          return {
            key: key,
            label: label,
            value: value == null ? '' : String(value),
            isCustom: false,
            group: '地址信息'
          }
        }
        return [
          mk('contacts', '联系人', r.contacts),
          mk('telephone', '电话', r.telephone),
          mk('email', '电子邮箱', r.email),
          mk('address', '地址', r.address),
          mk('company', '公司', r.company)
        ]
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
        var ctx = null
        try { ctx = JSON.parse(sessionStorage.getItem('jsh.labelEdit.context') || 'null') } catch (e) { ctx = null }
        try {
          sessionStorage.setItem('jsh.labelEdit.context', JSON.stringify({
            record: (ctx && ctx.record) || this.record || {},
            sku: {},
            template: null,
            provider: isAddress ? '' : 'table.jsh_supplier',
            businessType: isAddress ? '' : 'member'
          }))
        } catch (e) { /* 忽略 */ }
        this.visible = false
        var query = { type: isAddress ? 'address' : 'member' }
        if (!isAddress) query.businessType = 'member'
        this.$router.push({ path: '/label/edit-mobile', query: query })
      },
      // 编辑：先关闭预览层，动画结束后再交给标签编辑弹层，避免两层重叠
      // 同时带上当前预览使用的模板 id，编辑页按它加载同一套标签数据
      handleEdit() {
        var payload = {
          record: this.record,
          sku: this.sku,
          categoryTree: this.categoryTree,
          templateId: this.template ? this.template.id : null
        }
        this.visible = false
        // 弹层直达方式（无父组件监听 edit）→ 直接跳转统一标签编辑页
        if (this.directEntry && !(this.$listeners && this.$listeners.edit)) {
          var isAddress = this.type === 'address'
          // 打开弹层时未携带 record（如地址识别页）→ 沿用进入打印前写入的上下文数据，避免编辑时字段为空
          var prevRecord = null
          try { prevRecord = (JSON.parse(sessionStorage.getItem('jsh.labelEdit.context') || 'null') || {}).record } catch (e) { prevRecord = null }
          // 把商品数据和当前模板带到编辑页，否则编辑页拿不到 record/sku，字段会全部为空
          try {
            sessionStorage.setItem('jsh.labelEdit.context', JSON.stringify({
              record: (this.record && Object.keys(this.record).length) ? this.record : (prevRecord || {}),
              sku: this.sku || {},
              template: this.template || null,
              provider: (this.template && this.template.provider) || (isAddress ? '' : (this.type === 'delivery' ? 'sales.delivery' : (this.type === 'member' ? 'table.jsh_supplier' : 'product.label'))),
              businessType: isAddress ? '' : (this.type === 'delivery' ? 'delivery' : (this.type === 'member' ? 'member' : 'product'))
            }))
          } catch (e) { /* 忽略 */ }
          var query = { type: this.type || 'label' }
          if (!isAddress) {
            query.businessType = this.type === 'delivery' ? 'delivery' : (this.type === 'member' ? 'member' : 'product')
          }
          if (payload.templateId) query.templateId = payload.templateId
          this.$router.push({ path: '/label/edit-mobile', query: query })
          return
        }
        setTimeout(() => this.$emit('edit', payload), 200)
      },
      // 通过搜索发现的打印机打印（蓝牙/云）
      handleDiscoveredPrint() {
        var self = this
        var printer = this.discoveredPrinter
        var type = printer.type
        this.printing = true
        var items = this.buildPrintItems()
        if (type === 'bluetooth') {
          this.printViaBluetooth(printer, items)
        } else if (type === 'cloud') {
          this.printing = false
          this.printByBrowser(items)
        }
      },
      // 蓝牙打印：连接 GATT 并发送打印数据
      printViaBluetooth(printer, items) {
        var self = this
        var device = printer.device
        if (!device) {
          this.printing = false
          this.$message.error('蓝牙设备未连接')
          return
        }
        device.gatt.connect().then(function(server) {
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
          // 将打印内容转为字节流（ESC/POS 简化指令：文本 + 条码）
          var data = self.buildEscPosData(items)
          return self.sendChunks(writable, data)
        }).then(function() {
          self.printing = false
          self.$message.success('已发送到蓝牙打印机：' + printer.name)
        }).catch(function(err) {
          self.printing = false
          self.$message.error('蓝牙打印失败：' + (err.message || err))
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
      // 构造 ESC/POS 打印数据（简化版）
      buildEscPosData(items) {
        var enc = new TextEncoder()
        var bytes = []
        // 初始化
        bytes.push(0x1b, 0x40)
        items.forEach(function(item) {
          if (item.text) {
            var line = item.text + '\n'
            var arr = enc.encode(line)
            for (var j = 0; j < arr.length; j++) bytes.push(arr[j])
          }
        })
        // 走纸切纸
        bytes.push(0x1d, 0x56, 0x00)
        return new Uint8Array(bytes)
      },
      // 构造打印项数组（供 discovered 打印复用）
      buildPrintItems() {
        return this.buildLayoutItems()
      },
      // 打印：直接发送到所选打印机（后端执行），后端不可用时回退浏览器打印
      handlePrint() {
        if (!this.template) return
        var self = this
        // Wi-Fi 打印机：由后端按 IP:端口 下发 TSPL 指令
        if (this.printerType === 'wifi') {
          this.printViaWifi()
          return
        }
        if (!this.selectedPrinter) {
          // 远程部署时后端枚举不到本机打印机，此时应引导安装本地打印助手
          if (this.printerType === 'backend' && !this.localAgentOnline && this.printers.length === 0) {
            this.showInstallGuide()
          } else {
            this.$message.warning('请先选择打印机')
          }
          return
        }
        // 浏览器打印：走系统打印对话框，可选本机任意打印机（远程部署场景）
        if (this.selectedPrinter === '__browser__') {
          this.printByBrowser(this.buildLayoutItems())
          return
        }
        // 本机打印机：交给本地打印助手直接打印，不弹浏览器对话框
        if (isLocalPrinter(this.selectedPrinter)) {
          // 打印前再确认一次助手仍在运行，避免中途退出后无提示
          detectLocalAgent().then(function(base) {
            if (!base) {
              self.localAgentOnline = false
              self.showInstallGuide()
              return
            }
            self.printViaLocalAgent()
          })
          return
        }
        // 若选择的是搜索发现的打印机（蓝牙/云），走对应打印通道
        if (this.discoveredPrinter && this.discoveredPrinter.id === this.selectedPrinter) {
          this.handleDiscoveredPrint()
          return
        }
        var items = this.buildLayoutItems()
        this.printing = true
        // 打印机只能打点阵，交给前端把整张标签渲染成图片，后端转单色位图下发
        this.renderLabelToPng().then(function(image) {
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
            self.$message.success('已发送到打印机：' + self.selectedPrinter)
          } else {
            self.$message.error(data.message || '打印失败')
            self.printByBrowser(items)
          }
        }).catch(function() {
          self.printing = false
          self.printByBrowser(items)
        })
      },
      // 组装标签打印项（后端打印机与网络打印机共用同一份布局数据）
      // 按界面元素 1:1 输出：文字项与独立编码项各用自身 x/y/宽高，不再做二次拆分
      buildLayoutItems() {
        var self = this
        var items = []
        this.renderItems.forEach(function(item) {
          var style = self.attrStyles[item.key]
          if (!style) return
          if (item.isCode) {
            // 编码元素：宽高取与预览一致的可打印尺寸
            var layout = self.codeLayout(item.key)
            items.push({
              x: style.x || 0,
              y: style.y || 0,
              width: layout.imgWidth,
              height: layout.imgHeight,
              codeType: style.codeType,
              codeText: layout.value,
              // 硬件取整参数：后端直接使用，不能自行按宽度重算
              narrow: layout.narrow,
              cell: layout.cell,
              // image 仅用于浏览器兜底打印，后端按 codeType + codeText 下发硬件指令
              image: self.codeImages[item.key] || '',
              text: ''
            })
            return
          }
          var attr = item.attr
          var labelText = self.attrLabels[attr.key] || ''
          var value = attr.value == null ? '' : attr.value
          var displayText = labelText ? labelText + ': ' + value : value
          items.push({
            x: style.x || 0,
            y: style.y || 0,
            width: style.width || null,
            height: style.height || null,
            fontSize: style.fontSize,
            fontWeight: style.fontWeight,
            letterSpacing: style.letterSpacing || 0,
            lineHeight: style.lineHeight || 1.3,
            text: displayText
          })
        })
        return items
      },
      // 打印到 Wi-Fi 打印机：后端通过 Socket 发送 TSPL 指令到 ip:端口
      printViaWifi() {
        var self = this
        var printer = this.wifiPrinters.find(function(p) { return p.id === self.activeWifiId })
        if (!printer) {
          this.$message.warning('请先点击「添加打印机」配置打印机')
          return
        }
        this.printing = true
        var self2 = this
        // TSPL / DDPL 走打印机内置字库的文本+条码指令（数据量小、出纸稳定）；
        // CPCL / ESC/POS 只支持位图，才需要把整张标签渲染成图片
        var cmdSet = String(printer.cmdSet || 'TSPL').toUpperCase()
        var needsImage = cmdSet === 'CPCL' || cmdSet === 'ESC/POS'
        var prepare = needsImage ? this.renderLabelToPng() : Promise.resolve(null)
        prepare.then(function(image) {
          var payload = {
            copies: self.copies,
            width: self.labelWidth,
            height: self.labelHeight,
            items: self.buildLayoutItems()
          }
          if (image) payload.image = image
          return printToWifiPrinter(printer, payload)
        }).then(function(res) {
          self2.printing = false
          if (res && res.code === 200) {
            self2.$message.success('已发送到打印机：' + printer.name)
            self2.$set(self2.wifiStatus, printer.id, true)
          } else {
            // 后端把失败原因放在 data.message 中
            var data = (res && res.data) || {}
            var message = (typeof data === 'string' ? data : data.message) || '打印失败'
            self2.$message.error(message)
          }
        }).catch(function(err) {
          self2.printing = false
          self2.$message.error((err && err.message) || '网络打印失败，请检查打印机与后端网络')
        })
      },
      // 浏览器打印（后端打印不可用时兜底）
      printByBrowser(items) {
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
            wrapStyle +
            '">' + item.text + '</div>'
        })
        printLabel({
          widthPx: this.labelWidth,
          heightPx: this.labelHeight,
          bodyHtml: bodyHtml
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
        this.renderLabelToPng().then(function(image) {
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
            self.$message.success('已发送到打印机：' + printer)
          } else {
            self.$message.error((res && (res.message || res.msg)) || '打印失败')
          }
        }).catch(function(err) {
          self.printing = false
          if (err && err.message && err.message.indexOf('未运行') >= 0) {
            self.localAgentOnline = false
            self.showInstallGuide()
          } else {
            self.$message.error('打印失败：' + ((err && err.message) || '请确认打印助手已启动'))
          }
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
          // 指定宽度时按框宽逐字换行；指定高度时只绘制框内完整行，超出部分不打印
          var lines = self.wrapCanvasText(ctx, String(item.text || ''), item.width)
          var maxLines = item.height ? Math.max(0, Math.floor(item.height / lh + 0.001)) : lines.length
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
    display: flex;
    justify-content: center;
    align-items: center;
    min-height: 300px;
    padding: 12px;
    background: #f5f5f5;
    border-radius: 4px;
    overflow: auto;
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
  .lp-default-tag {
    color: #999;
  }
  .lp-preview {
    position: relative;
    background: #fff;
    border: 1px dashed #d9d9d9;
    overflow: hidden;
    box-sizing: border-box;
  }
  /* 新版模板自带纸张边框，外层不再重复描边，避免 1~2px 内缩 */
  .lp-preview.lp-preview-schema {
    border: none;
    box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.08);
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
  /* ===== 打印机类型标签 ===== */
  .lp-printer-tabs {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-bottom: 10px;
  }
  .lp-tab {
    display: inline-flex;
    align-items: center;
    padding: 4px 10px;
    font-size: 12px;
    border-radius: 14px;
    background: #f0f0f0;
    color: #666;
    cursor: pointer;
    white-space: nowrap;
    .anticon {
      margin-right: 4px;
    }
    &.active {
      background: #1890ff;
      color: #fff;
    }
    &.disabled {
      opacity: 0.4;
      cursor: not-allowed;
    }
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
  .lmpl-add-card {
    margin: 12px 16px 16px;
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
  /* 打印机搜索区 */
  .lp-printer-search {
    margin-top: 8px;
  }
  /* Wi-Fi 打印机列表项 */
  .lp-device-main {
    flex: 1;
    min-width: 0;
    margin-right: 8px;
  }
  .lp-device-name {
    font-size: 13px;
    color: #333;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .lp-device-sub {
    font-size: 11px;
    color: #999;
    margin-top: 2px;
  }
  .lp-device-status {
    margin-right: 6px;
  }
  .lp-device-del {
    color: #ff4d4f;
    cursor: pointer;
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
  .lp-device-info {
    margin-top: 8px;
    padding: 6px 10px;
    background: #f6ffed;
    border: 1px solid #b7eb8f;
    border-radius: 4px;
    font-size: 13px;
    color: #52c41a;
    .anticon {
      margin-right: 4px;
    }
  }
  .lp-device-list {
    margin-top: 8px;
    max-height: 160px;
    overflow-y: auto;
    border: 1px solid #f0f0f0;
    border-radius: 4px;
  }
  .lp-device-item {
    display: flex;
    align-items: center;
    padding: 8px 10px;
    font-size: 13px;
    cursor: pointer;
    border-bottom: 1px solid #f5f5f5;
    .anticon {
      margin-right: 6px;
      color: #1890ff;
    }
    &:last-child {
      border-bottom: none;
    }
    &.selected {
      background: #e6f7ff;
    }
    &:hover {
      background: #fafafa;
    }
  }

  /* ===== 手机端标准页面 ===== */
  .lp-mobile-page {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: #f0f2f5;
    display: flex;
    flex-direction: column;
    z-index: 2900;
  }
  /* 顶栏：蓝底 + 白色图标文字 */
  .lmp-header {
    flex: none;
    height: 49px;
    background: #fff;
    display: flex;
    align-items: center;
    padding: 0 8px 0 0;
  }
  .lmp-header-blue {
    background: #1890ff;
    border-bottom: none;
  }
  .lmp-header-blue .lmp-title,
  .lmp-header-blue .lmp-back,
  .lmp-header-blue .lmp-settings,
  .lmp-header-blue .lmp-edit,
  .lmp-header-blue .lmp-close {
    color: #fff;
  }
  .lmp-header-blue .lmp-subtitle {
    color: rgba(255, 255, 255, 0.75);
  }
  /* 返回按钮：与底边栏等高（58px）的正方形，左边贴屏幕左边 */
  .lmp-back,
  .lmp-settings,
  .lmp-edit,
  .lmp-close {
    font-size: 22px;
    color: #333;
    cursor: pointer;
    width: 58px;
    height: 58px;
    line-height: 1;
    flex: none;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  /* 右侧「编辑」「关闭」：两个按钮并排，尺寸略小于返回按钮，避免挤压标题 */
  .lmp-edit,
  .lmp-close {
    width: 44px;
    font-size: 21px;
  }
  .lmp-back {
    margin-left: 0;
  }
  .lmp-settings.placeholder,
  .lmp-edit.placeholder {
    visibility: hidden;
  }
  .lmp-title-block {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
  }
  .lmp-title {
    font-size: 16px;
    font-weight: 500;
    color: #262626;
    max-width: 60vw;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    line-height: 1.2;
  }
  .lmp-subtitle {
    font-size: 11px;
    color: #999;
    margin-top: 1px;
  }
  /* 主内容：中间空白全部为预览区域 */
  .lmp-content {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    /* 底部 padding 必须 >= footer 高度(70px + safe-area) + 1px 间距，否则最后一张卡片被遮挡 */
    padding: 5px 10px calc(71px + env(safe-area-inset-bottom)) 10px;
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
  .lmp-empty-create {
    margin-top: 16px;
  }
  /* 预览区：flex 撑满剩余空间，Math.min 完整显示 */
  .lmp-preview-area {
    flex: 1;
    min-height: 80px;
    display: flex;
    align-items: flex-start;
    justify-content: center;
    padding: 16px 8px 8px 8px;
    overflow: hidden;
  }
  /* 预览标签（直接在灰色背景上，无白色卡片包裹） */
  .lmp-preview {
    position: relative;
    background: #fff;
    overflow: visible;
    box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.08);
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
  /* 底部打印按钮 */
  .lmp-footer {
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    padding: 12px 16px calc(12px + env(safe-area-inset-bottom));
    background: #fff;
    border-top: 1px solid #eee;
    z-index: 3001;
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
  .lmpl-tabs-bar {
    flex: none;
    display: flex;
    background: #fff;
    border-bottom: 1px solid #eee;
    padding: 0 4px;
  }
  .lmpl-tab {
    flex: 1;
    text-align: center;
    padding: 14px 0 12px;
    font-size: 15px;
    color: #888;
    position: relative;
    cursor: pointer;
    transition: color 0.15s;
  }
  .lmpl-tab.active {
    color: #1890ff;
    font-weight: 600;
  }
  .lmpl-tab.active::after {
    content: '';
    position: absolute;
    bottom: 0;
    left: 50%;
    transform: translateX(-50%);
    width: 28px;
    height: 3px;
    background: #1890ff;
    border-radius: 2px;
  }
  .lmpl-list {
    flex: 1;
    overflow-y: auto;
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
  .lmpl-primary-btn {
    max-width: 280px;
    margin: 16px auto 0;
    height: 44px;
    font-size: 15px;
    border-radius: 22px;
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
