<template>
  <div :style="{ padding: '0 0 32px 32px' }">
    <h3 :style="{ marginBottom: '20px' }">{{ title }}</h3>
    <v-chart :forceFit="true" :height="height" :data="dataSource" :scale="scale" :padding="padding">
      <v-tooltip/>
      <v-axis/>
      <v-bar position="x*y" :color="color"/>
    </v-chart>
  </div>
</template>

<script>
  import { triggerWindowResizeEvent } from '@/utils/util'
  import { DEFAULT_COLOR } from "@/store/mutation-types"
  import Vue from 'vue'
  import './installViser' // viser/G2 按需注册，从 main.js 全局注册改为图表组件内部加载

  export default {
    name: 'Bar',
    props: {
      dataSource: {
        type: Array,
        required: true
      },
      yaxisText: {
        type: String,
        default: 'y'
      },
      title: {
        type: String,
        default: ''
      },
      height: {
        type: Number,
        default: 254
      }
    },
    data() {
      return {
        padding: ['auto', 'auto', '40', '50'],
        color: Vue.ls.get(DEFAULT_COLOR)
      }
    },
    computed: {
      scale() {
        return [{
          dataKey: 'y',
          alias: this.yaxisText
        }]
      }
    },
    mounted() {
      triggerWindowResizeEvent()
    }
  }
</script>