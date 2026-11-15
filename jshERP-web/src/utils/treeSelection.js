/** Collect initial checked and expanded keys from the checked flags in a tree. */
export function getTreeSelectionState(nodes) {
  var checkedKeys = []
  var expandedKeys = []

  function visit(list) {
    var items = Array.isArray(list) ? list : []
    items.forEach(function(node) {
      if (!node) return
      if (node.checked === true && node.key != null) checkedKeys.push(node.key)
      if (Array.isArray(node.children) && node.children.length > 0) {
        if (node.key != null) expandedKeys.push(node.key)
        visit(node.children)
      }
    })
  }

  visit(nodes)
  return { checkedKeys: checkedKeys, expandedKeys: expandedKeys }
}
