<template>
  <div>
    <analysis-mobile v-if="isMobile()"></analysis-mobile>
    <div v-else style="margin: 12px 12px 0px;">
      <index-chart></index-chart>
    </div>
  </div>
</template>

<script>
  import IndexChart from './IndexChart'
  import AnalysisMobile from './AnalysisMobile'
  import { mixinDevice } from '@/utils/mixin.js'

  export default {
    name: "Analysis",
    components: {
      IndexChart,
      AnalysisMobile
    },
    mixins: [mixinDevice],
    data() {
      return {

      }
    },
    created() {

    },
    methods: {

    }
  }
</script>
