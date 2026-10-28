<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, errorMessage, type Envelope } from '../lib/api'

interface AuditEvent {
  id: number; actorUserId: number | null; sourceService: string; sourceEventId: string | null
  action: string; targetType: string; targetId: string; requestId: string; occurredAt: string
}
const rows = ref<AuditEvent[]>([])
const targetType = ref('')
const offset = ref(0)
const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true; error.value = ''
  try {
    const params: Record<string, string | number> = { limit: 50, offset: offset.value }
    if (targetType.value) params.targetType = targetType.value.trim().toUpperCase()
    const response = await api.get<Envelope<AuditEvent[]>>('/audit-events', { params })
    rows.value = response.data.data
  } catch (reason) { error.value = errorMessage(reason) }
  finally { loading.value = false }
}

onMounted(load)
</script>

<template>
  <main class="student-page catalog-page admin-page">
    <p class="eyebrow">
      管理 · 审计记录
    </p><h1>操作审计</h1>
    <p class="description">
      只显示授权范围内的必要审计字段，不包含请求正文、凭据或申请/工单详情。
    </p>
    <section class="admin-panel">
      <form
        class="audit-filter"
        @submit.prevent="offset = 0; load()"
      >
        <label>目标类型（可选）<input
          v-model="targetType"
          maxlength="32"
          pattern="[A-Za-z_]{1,32}"
          placeholder="例如 USER、ROOM"
        ></label><button
          class="primary-button"
          :disabled="loading"
        >
          查询
        </button><button
          type="button"
          class="outline-button"
          :disabled="loading"
          @click="targetType = ''; offset = 0; load()"
        >
          清除筛选
        </button>
      </form>
      <p
        v-if="error"
        class="inline-error"
        role="alert"
      >
        {{ error }}
      </p><p
        v-if="loading"
        class="empty-state"
      >
        正在读取审计记录…
      </p><p
        v-else-if="rows.length === 0"
        class="empty-state"
      >
        没有符合条件的记录。
      </p>
      <div
        v-else
        class="admin-table-wrap"
      >
        <table class="admin-table audit-table">
          <thead><tr><th>ID</th><th>时间</th><th>操作者</th><th>来源</th><th>动作</th><th>目标</th><th>请求关联</th></tr></thead><tbody>
            <tr
              v-for="event in rows"
              :key="event.id"
            >
              <td>{{ event.id }}</td><td>{{ new Date(event.occurredAt).toLocaleString('zh-CN', { hour12: false }) }}</td><td>{{ event.actorUserId ?? '系统' }}</td><td>{{ event.sourceService }}<small v-if="event.sourceEventId">{{ event.sourceEventId }}</small></td><td>{{ event.action }}</td><td>{{ event.targetType }} · {{ event.targetId }}</td><td><code>{{ event.requestId }}</code></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="pagination">
        <button
          class="outline-button"
          :disabled="offset === 0 || loading"
          @click="offset = Math.max(0, offset - 50); load()"
        >
          上一页
        </button><span>每页 50 条</span><button
          class="outline-button"
          :disabled="rows.length < 50 || loading"
          @click="offset += 50; load()"
        >
          下一页
        </button>
      </div>
    </section>
  </main>
</template>
