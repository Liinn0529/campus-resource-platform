<script setup lang="ts">
defineOptions({ name: "ApprovalDetail" })
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { routeId } from '../../workflows/policy'
import ClassroomDetail from '../../workflows/components/ClassroomDetail.vue'
const route = useRoute(), id = computed(() => routeId(route.params.id))
</script>
<template>
  <main class="page">
    <RouterLink to="/approvals">
      返回审批列表
    </RouterLink><ClassroomDetail
      v-if="id !== null"
      :id="id"
    /><el-alert
      v-else
      title="申请编号无效"
      type="error"
      :closable="false"
    />
  </main>
</template>
<style scoped>.page{max-width:1000px;margin:auto;padding:24px}</style>
