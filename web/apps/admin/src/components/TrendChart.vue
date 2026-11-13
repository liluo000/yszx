<script setup lang="ts">
// 趋势图（ECharts）：一根 y 轴；多条序列时显示图例；悬停显示十字准线和数值
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([BarChart, LineChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

export interface TrendSeries {
  name: string
  data: (number | null)[]
  color: string
}

const props = defineProps<{
  type: 'line' | 'bar'
  categories: string[]
  series: TrendSeries[]
  /** 数值单位，如 "%"，用于坐标轴和提示 */
  unit?: string
  label: string
}>()

const el = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null
let observer: ResizeObserver | null = null

function render() {
  if (!chart) return
  const unit = props.unit ?? ''
  chart.setOption(
    {
      animation: false,
      grid: { left: 8, right: 16, top: props.series.length > 1 ? 36 : 16, bottom: 8, containLabel: true },
      legend:
        props.series.length > 1 ? { top: 0, left: 0, icon: 'roundRect', itemWidth: 12, itemHeight: 4 } : undefined,
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: props.type === 'line' ? 'line' : 'shadow' },
        valueFormatter: (v: unknown) => (v === null || v === undefined ? '—' : `${v}${unit}`),
      },
      xAxis: {
        type: 'category',
        data: props.categories,
        axisLine: { lineStyle: { color: '#c9ced6' } },
        axisTick: { show: false },
        axisLabel: { color: '#5b6573' },
      },
      yAxis: {
        type: 'value',
        minInterval: unit === '%' ? undefined : 1,
        max: unit === '%' ? 100 : undefined,
        splitLine: { lineStyle: { color: '#eceef1' } },
        axisLabel: { color: '#5b6573', formatter: `{value}${unit}` },
      },
      series: props.series.map((s) =>
        props.type === 'line'
          ? {
              name: s.name,
              type: 'line',
              data: s.data,
              color: s.color,
              lineStyle: { width: 2 },
              symbol: 'circle',
              symbolSize: 8,
              showSymbol: props.categories.length <= 31,
              connectNulls: false,
            }
          : {
              name: s.name,
              type: 'bar',
              data: s.data,
              color: s.color,
              barMaxWidth: 24,
              barGap: '10%',
              itemStyle: { borderRadius: [4, 4, 0, 0] },
            },
      ),
    },
    true,
  )
}

onMounted(() => {
  chart = echarts.init(el.value!)
  render()
  observer = new ResizeObserver(() => chart?.resize())
  observer.observe(el.value!)
})

watch(() => [props.categories, props.series, props.type], render, { deep: true })

onBeforeUnmount(() => {
  observer?.disconnect()
  chart?.dispose()
})
</script>

<template>
  <div ref="el" class="chart" role="img" :aria-label="label" />
</template>

<style scoped>
.chart {
  width: 100%;
  height: 260px;
}
</style>
