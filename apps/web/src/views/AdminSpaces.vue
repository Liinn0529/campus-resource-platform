<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, errorMessage, type Envelope } from '../lib/api'

interface Building { id: number; code: string; name: string }
const rows = ref<Building[]>([])
const page = ref(0)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const notice = ref('')
const editing = ref<number | null>(null)
const code = ref('')
const name = ref('')

function reset() { editing.value = null; code.value = ''; name.value = '' }
function edit(row: Building) { editing.value = row.id; code.value = row.code; name.value = row.name; notice.value = '' }

async function load() {
  loading.value = true
  error.value = ''
  try {
    const response = await api.get<Envelope<Building[]>>('/buildings', { params: { limit: 50, offset: page.value * 50 } })
    rows.value = response.data.data
  } catch (reason) { error.value = errorMessage(reason) }
  finally { loading.value = false }
}

async function save() {
  if (saving.value) return
  saving.value = true
  error.value = ''
  notice.value = ''
  try {
    const body = { code: code.value.trim(), name: name.value.trim() }
    if (editing.value === null) await api.post('/buildings', body)
    else await api.put(`/buildings/${editing.value}`, body)
    notice.value = editing.value === null ? '楼宇已创建。' : '楼宇信息已更新。'
    reset()
    await load()
  } catch (reason) { error.value = errorMessage(reason) }
  finally { saving.value = false }
}

onMounted(load)
</script>

<template>
  <main class="student-page catalog-page admin-page">
    <p class="eyebrow">
      管理 · 空间主数据
    </p>
    <h1>楼宇维护</h1>
    <p class="description">
      创建和维护楼宇编码与名称。空间停用策略及其对既有预约的影响仍由后端业务规则决定。
    </p>
    <div class="admin-columns">
      <section class="admin-panel">
        <h2>{{ editing === null ? '新增楼宇' : `编辑楼宇 #${editing}` }}</h2>
        <form
          class="admin-form"
          @submit.prevent="save"
        >
          <label>楼宇编码<input
            v-model="code"
            required
            minlength="1"
            maxlength="64"
            pattern="[A-Za-z0-9][A-Za-z0-9_-]{0,63}"
          ></label>
          <label>楼宇名称<input
            v-model="name"
            required
            maxlength="100"
          ></label>
          <p
            v-if="error"
            class="inline-error"
            role="alert"
          >
            {{ error }}
          </p>
          <p
            v-if="notice"
            class="success-note"
            role="status"
          >
            {{ notice }}
          </p>
          <div class="form-actions">
            <button
              class="primary-button"
              :disabled="saving"
            >
              {{ saving ? '保存中…' : '保存' }}
            </button><button
              v-if="editing !== null"
              type="button"
              class="outline-button"
              @click="reset"
            >
              取消编辑
            </button>
          </div>
        </form>
      </section>
      <section class="admin-panel">
        <div class="admin-list-heading">
          <h2>楼宇列表</h2><button
            class="outline-button"
            :disabled="loading"
            @click="load"
          >
            刷新
          </button>
        </div>
        <p
          v-if="loading"
          class="empty-state"
        >
          正在读取楼宇…
        </p>
        <p
          v-else-if="rows.length === 0"
          class="empty-state"
        >
          当前页没有楼宇记录。
        </p>
        <div
          v-else
          class="admin-table-wrap"
        >
          <table class="admin-table">
            <thead><tr><th>ID</th><th>编码</th><th>名称</th><th>操作</th></tr></thead><tbody>
              <tr
                v-for="row in rows"
                :key="row.id"
              >
                <td>{{ row.id }}</td><td>{{ row.code }}</td><td>{{ row.name }}</td><td>
                  <button
                    class="text-button"
                    @click="edit(row)"
                  >
                    编辑
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="pagination">
          <button
            class="outline-button"
            :disabled="page === 0 || loading"
            @click="page--; load()"
          >
            上一页
          </button><span>第 {{ page + 1 }} 页 · 每页 50 条</span><button
            class="outline-button"
            :disabled="rows.length < 50 || loading"
            @click="page++; load()"
          >
            下一页
          </button>
        </div>
      </section>
    </div>
  </main>
</template>
