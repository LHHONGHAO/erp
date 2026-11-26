import Menu from 'ant-design-vue/es/menu'
import Icon from 'ant-design-vue/es/icon'

const { Item, SubMenu } = Menu

export default {
  name: 'SMenu',
  props: {
    menu: {
      type: Array,
      required: true
    },
    theme: {
      type: String,
      required: false,
      default: 'dark'
    },
    mode: {
      type: String,
      required: false,
      default: 'inline'
    },
    collapsed: {
      type: Boolean,
      required: false,
      default: false
    },
    // 常用页面编辑态：菜单项右侧显示 +/−
    editing: {
      type: Boolean,
      required: false,
      default: false
    },
    // 已加入常用的页面 url 列表
    favorites: {
      type: Array,
      required: false,
      default: () => []
    }
  },
  data () {
    return {
      openKeys: [],
      selectedKeys: [],
      cachedOpenKeys: []
    }
  },
  computed: {
    rootSubmenuKeys: vm => {
      const keys = []
      vm.menu.forEach(item => keys.push(item.url))
      return keys
    }
  },
  mounted () {
    this.updateMenu()
  },
  watch: {
    collapsed (val) {
      if (val) {
        this.cachedOpenKeys = this.openKeys.concat()
        this.openKeys = []
      } else {
        this.openKeys = this.cachedOpenKeys
      }
    },
    $route: function () {
      this.updateMenu()
    }
  },
  methods: {
    // select menu item
    onOpenChange (openKeys) {

      // 在水平模式下时执行，并且不再执行后续
      if (this.mode === 'horizontal') {
        this.openKeys = openKeys
        return
      }
      // 非水平模式时
      const latestOpenKey = openKeys.find(key => !this.openKeys.includes(key))
      if (!this.rootSubmenuKeys.includes(latestOpenKey)) {
        this.openKeys = openKeys
      } else {
        this.openKeys = latestOpenKey ? [latestOpenKey] : []
      }
    },
    updateMenu () {
      const routes = this.$route.matched.concat()
      const { hidden } = this.$route.meta
      if (routes.length >= 3 && hidden) {
        routes.pop()
        this.selectedKeys = [routes[routes.length - 1].path]
      } else {
        this.selectedKeys = [routes.pop().path]
      }
      const openKeys = []
      if (this.mode === 'inline') {
        routes.forEach(item => {
          openKeys.push(item.path)
        })
      }
      //update-begin-author:taoyan date:20190510 for:online表单菜单点击展开的一级目录不对
      if(!this.selectedKeys|| this.selectedKeys[0].indexOf(":")<0){
        this.collapsed ? (this.cachedOpenKeys = openKeys) : (this.openKeys = openKeys)
      }
      //update-end-author:taoyan date:20190510 for:online表单菜单点击展开的一级目录不对
    },

    // render
    renderItem (menu) {
      if (!menu.hidden) {
        return menu.children && !menu.alwaysShow ? this.renderSubMenu(menu) : this.renderMenuItem(menu)
      }
      return null
    },
    renderMenuItem (menu) {
      const target = null
      const tag = target && 'a' || 'router-link'
      let props = { to: { name: menu.name } }
      if(menu.route && menu.route === '0'){
        props = { to: { path: menu.path } }
      }

      const attrs = { href: menu.url, target: menu.text }

      if (menu.children) {
        // 把有子菜单的 并且 父菜单是要隐藏子菜单的
        // 都给子菜单增加一个 hidden 属性
        // 用来给刷新页面时， selectedKeys 做控制用
        menu.children.forEach(item => {
          item.meta = Object.assign(item.meta, { hidden: true })
        })
      }
      return (
        <Item {...{ key: menu.url }}>
          <tag {...{ props, attrs }} title={menu.text}>
            {this.renderIcon(menu.icon)}
            <span>{menu.text}</span>
            {this.renderFavToggle(menu)}
          </tag>
        </Item>
      )
    },
    // 编辑态下给可打开的页面加 +/− 图标
    renderFavToggle (menu) {
      if (!this.editing || !this.isOpenable(menu)) {
        return null
      }
      const isFav = this.favorites.indexOf(menu.url) > -1
      const props = { type: isFav ? 'minus-circle' : 'plus-circle' }
      // 绝对定位到菜单项右侧并垂直居中，避免 float 导致图标贴顶
      const style = { position: 'absolute', right: '8px', top: '50%', transform: 'translateY(-50%)', color: isFav ? '#f5222d' : '#1890ff' }
      const on = {
        click: e => {
          // 阻止 router-link 跳转，只做常用增删
          e.preventDefault()
          e.stopPropagation()
          this.$emit('toggle-favorite', { url: menu.url, text: menu.text })
        }
      }
      return (
        <Icon {...{ props, style, on }} />
      )
    },
    // 可加入常用的页面（排除目录容器与首页）
    isOpenable (menu) {
      if (!menu || !menu.url || !menu.component) {
        return false
      }
      if (menu.url === '/dashboard/analysis' || menu.url === '/') {
        return false
      }
      return menu.component !== '/layouts/TabLayout' && menu.component !== '/layouts/RouteView'
    },
    renderSubMenu (menu) {
      const itemArr = []
      if (!menu.alwaysShow) {
        menu.children.forEach(item => itemArr.push(this.renderItem(item)))
      }
      return (
        <SubMenu {...{ key: menu.url }}>
          <span slot="title">
            {this.renderIcon(menu.icon)}
            <span title={menu.text}>{menu.text}</span>
          </span>
          {itemArr}
        </SubMenu>
      )
    },
    renderIcon (icon) {
      if (icon === 'none' || icon === undefined) {
        return null
      }
      const props = {}
      typeof (icon) === 'object' ? props.component = icon : props.type = icon
      return (
        <Icon {... { props } }/>
      )
    }
  },

  render () {
    const { mode, theme, menu } = this
    const props = {
      mode: mode,
      theme: theme,
      openKeys: this.openKeys,
      inlineIndent: 12,
    }
    const on = {
      select: obj => {
        if(obj.key.indexOf('http://')>-1 || obj.key.indexOf('https://')>-1) {
          window.open(obj.key)
        } else {
          this.selectedKeys = obj.selectedKeys
          this.$emit('select', obj)
        }
      },
      openChange: this.onOpenChange
    }

    const menuTree = menu.map(item => {
      if (item.hidden) {
        return null
      }
      return this.renderItem(item)
    })
    // {...{ props, on: on }}
    return (
      <Menu vModel={this.selectedKeys} {...{ props, on: on }}>
        {menuTree}
      </Menu>
    )
  }
}
