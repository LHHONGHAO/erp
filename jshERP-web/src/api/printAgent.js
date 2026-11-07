import { getAction, postAction } from '@/api/manage'

/** 打印代理设备列表（含在线状态与打印机配置） */
export function getPrintAgentList() {
  return getAction('/printAgent/list')
}

/** 新增/修改设备 */
export function savePrintAgent(parameter) {
  return postAction('/printAgent/save', parameter)
}

/** 删除设备 */
export function deletePrintAgent(parameter) {
  return postAction('/printAgent/delete', parameter)
}

/** 重置设备 Token */
export function resetPrintAgentToken(parameter) {
  return postAction('/printAgent/token', parameter)
}

/** 下发测试标签 */
export function testPrintAgent(parameter) {
  return postAction('/printAgent/test', parameter)
}

/** 按标签内容下发打印 */
export function printViaAgent(parameter) {
  return postAction('/printAgent/print', parameter)
}

/** 打印任务记录 */
export function getPrintAgentTasks(parameter) {
  return getAction('/printAgent/tasks', parameter)
}
