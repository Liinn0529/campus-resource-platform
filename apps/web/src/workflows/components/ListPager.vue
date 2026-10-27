<script setup lang="ts">
defineProps<{ offset: number; count: number; busy: boolean }>()
const emit = defineEmits<{ change: [offset: number] }>()
</script>
<template>
  <div class="pager">
    <el-button
      :disabled="busy || offset === 0"
      @click="emit('change', Math.max(0, offset - 50))"
    >
      上一页
    </el-button>
    <span>第 {{ offset / 50 + 1 }} 页</span>
    <el-button
      :disabled="busy || count < 50"
      @click="emit('change', offset + 50)"
    >
      下一页
    </el-button>
  </div>
</template>
<style scoped>.pager{display:flex;align-items:center;justify-content:center;gap:16px;margin:20px 0}</style>
