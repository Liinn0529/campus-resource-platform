<script setup lang="ts">
import { onMounted } from 'vue'
import { ElButton, ElTag } from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/tag/style/css'
import { useRuntimeStore } from '../stores/runtime'

const runtime = useRuntimeStore()
const labels = { pending: '正在检查', up: '运行正常', unavailable: '检查异常' }
onMounted(() => runtime.refresh())

function displayTime(value: string) {
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}
</script>

<template>
  <main class="shell">
    <header class="site-header">
      <div class="identity">
        <span
          class="identity-mark"
          aria-hidden="true"
        >校</span>
        <span>校园资源服务平台</span>
      </div>
      <span class="environment">工程环境检查</span>
    </header>

    <section
      class="intro"
      aria-labelledby="page-title"
    >
      <p class="eyebrow">
        PHASE 04 · 基础预约
      </p>
      <h1 id="page-title">
        服务运行状态
      </h1>
      <p class="description">
        检查基础服务是否在线，以及当前运行的构建版本。
      </p>
      <div class="scope-note">
        已提供公共层与基础预约接口。本页用于运行检查；签到、审批、报修和完整业务页面将在后续阶段开放。
      </div>
    </section>

    <section aria-label="后端服务状态">
      <div class="section-header">
        <div>
          <h2>基础服务</h2>
          <p
            class="updated"
            aria-live="polite"
          >
            {{ runtime.checkedAt ? `最近检查：${displayTime(runtime.checkedAt)}` : '尚未完成检查' }}
          </p>
        </div>
        <ElButton
          :loading="runtime.refreshing"
          @click="runtime.refresh()"
        >
          刷新状态
        </ElButton>
      </div>
      <div
        class="service-grid"
        aria-live="polite"
        :aria-busy="runtime.refreshing"
      >
        <article
          v-for="service in runtime.services"
          :key="service.id"
          class="service-card"
        >
          <div class="card-heading">
            <span class="module">{{ service.module }}</span>
            <ElTag
              :type="service.state === 'up' ? 'success' : service.state === 'unavailable' ? 'danger' : 'info'"
              effect="plain"
            >
              {{ labels[service.state] }}
            </ElTag>
          </div>
          <h3>{{ service.title }}</h3>
          <p class="service-name">
            {{ service.info?.service ?? `${service.id}-service` }}
          </p>
          <p
            v-if="service.state !== 'pending'"
            class="health-status"
          >
            健康状态：<strong>{{ service.healthStatus ?? '未取得' }}</strong>
          </p>
          <dl
            v-if="service.info"
            class="build-details"
          >
            <div><dt>版本</dt><dd>{{ service.info.version }}</dd></div>
            <div>
              <dt>提交</dt><dd
                class="commit"
                :title="service.info.gitCommit"
              >
                {{ service.info.gitCommit }}
              </dd>
            </div>
            <div><dt>构建时间</dt><dd>{{ displayTime(service.info.buildTime) }}</dd></div>
          </dl>
          <p
            v-if="service.state === 'pending'"
            class="service-message"
          >
            正在读取服务健康和版本信息…
          </p>
          <ul
            v-if="service.errors.length"
            class="service-errors"
          >
            <li
              v-for="error in service.errors"
              :key="error"
            >
              {{ error }}
            </li>
          </ul>
        </article>
      </div>
    </section>
    <footer>状态由后端健康与版本接口实时读取，页面不会自动轮询。</footer>
  </main>
</template>
