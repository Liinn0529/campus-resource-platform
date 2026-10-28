<script setup lang="ts">
defineOptions({ name: "MaintenanceDetail" })
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { routeId } from '../../workflows/policy'
import TicketDetail from '../../workflows/components/TicketDetail.vue'
const route = useRoute(), id = computed(() => routeId(route.params.id))
</script>
<template>
  <main class="page">
    <RouterLink to="/maintenance">
      返回工单列表
    </RouterLink><TicketDetail
      v-if="id !== null"
      :id="id"
    /><el-alert
      v-else
      title="工单编号无效"
      type="error"
      :closable="false"
    />
  </main>
</template>
<style scoped>.page{max-width:1000px;margin:auto;padding:24px}</style>
