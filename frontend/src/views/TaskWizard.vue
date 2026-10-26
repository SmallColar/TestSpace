<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api'
import StageTag from '../components/StageTag.vue'
import StepRecord from '../components/steps/StepRecord.vue'
import StepArrange from '../components/steps/StepArrange.vue'
import StepGenerate from '../components/steps/StepGenerate.vue'

const route = useRoute()
const router = useRouter()

const taskId = computed(() => route.params.id || null)
const isNew = computed(() => route.name === 'task-new')
const task = ref(null)
const loading = ref(true)
const err = ref('')
const notice = ref('')
const selectedStage = ref(null)
const followLatest = ref(true)
const recordingStarted = ref(false)

const STEPS = [
  { key: 'RECORD', no: '①', label: '录制信息', desc: '指定网址并录制接口' },
  { key: 'PARSE', no: '②', label: '接口与场景编排', desc: '按场景组织录制接口' },
  { key: 'GENERATE', no: '③', label: 'AI 生成用例', desc: '生成并保存自动化用例' }
]

const statusText = {
  PENDING: '待执行',
  RUNNING: '执行中',
  COMPLETED: '已完成',
  FAILED: '失败',
  CANCELLED: '已终止',
  REJECTED: '已驳回'
}

let timer = null

const currentIndex = computed(() => {
  if (!task.value) return 0
  if (task.value.status === 'COMPLETED') return STEPS.length
  if (task.value.stage === 'DONE') return STEPS.length
  return STEPS.findIndex((s) => s.key === task.value.stage)
})

const running = computed(() => task.value && task.value.status === 'RUNNING')

function goPrevious() {
  const index = STEPS.findIndex((step) => step.key === selectedStage.value)
  if (index > 0) {
    selectedStage.value = STEPS[index - 1].key
    followLatest.value = false
  }
}

async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    if (isNew.value) {
      const sourceId = route.query.copy
      if (sourceId) {
        const source = await api.getTask(Number(sourceId))
        task.value = { ...source, id: null, name: '', browserProvider: 'chrome-devtools-mcp', status: 'PENDING', stage: 'RECORD', createdAt: null, resultSummary: '' }
      } else {
        task.value = { id: null, name: '', targetUrl: '', recordingGoal: '', browserProvider: 'chrome-devtools-mcp', status: 'PENDING', stage: 'RECORD' }
      }
    } else task.value = await api.getTask(taskId.value)
    if (followLatest.value) selectedStage.value = task.value.stage
    err.value = ''
  } catch (e) {
    err.value = e.message
  } finally {
    loading.value = false
  }
}

async function createAndStartRecording(recordingInfo) {
  err.value = ''
  try {
    const payload = { ...recordingInfo, name: recordingInfo.name.trim(), targetUrl: recordingInfo.targetUrl.trim() }
    const created = route.query.copy
      ? await api.copyTask({ sourceTaskId: Number(route.query.copy), ...payload })
      : await api.createTask(payload)
    await api.startRecording(created.id)
    recordingStarted.value = true
    await router.replace(`/tasks/${created.id}`)
    await load()
    startPolling()
  } catch (e) { err.value = e.message }
}

async function enableRecordingPolling() {
  recordingStarted.value = true
  await load(true)
  startPolling()
}

function startPolling() {
  if (timer || !recordingStarted.value || task.value?.stage !== 'RECORD' || task.value?.status !== 'RUNNING') return
  timer = setInterval(async () => {
    if (!recordingStarted.value || task.value?.stage !== 'RECORD' || task.value?.status !== 'RUNNING') {
      recordingStarted.value = false
      stopPolling()
      return
    }
    await load(true)
    if (task.value?.stage !== 'RECORD' || task.value?.status !== 'RUNNING') {
      recordingStarted.value = false
      stopPolling()
    }
  }, 2500)
}
function stopPolling() {
  if (timer) clearInterval(timer)
  timer = null
}

async function retry() {
  try {
    if (task.value?.stage === 'RECORD') {
      await api.startRecording(taskId.value)
      recordingStarted.value = true
      await load(true)
      startPolling()
    }
    else if (task.value?.stage === 'GENERATE') await api.generateCases(Number(taskId.value))
    notice.value = '已重新启动当前节点。'
    await load(true)
  } catch (e) {
    err.value = e.message
  }
}

async function terminate() {
  if (!task.value || !window.confirm('确定终止当前录制任务吗？')) return
  err.value = ''
  try {
    task.value = await api.terminateRecording(Number(taskId.value))
    recordingStarted.value = false
    stopPolling()
  } catch (e) {
    err.value = e.message
  }
}

function onStageAdvanced(updated) {
  if (updated) {
    task.value = updated
    selectedStage.value = updated.stage
    notice.value = updated.stage === 'PARSE' ? '接口已确认，进入场景编排。' : '场景编排已保存，进入 AI 用例生成。'
  } else {
    load(true)
  }
}

onMounted(async () => {
  await load()
  recordingStarted.value = task.value?.status === 'RUNNING'
  startPolling()
})
onUnmounted(stopPolling)
</script>

<template>
  <div>
    <div class="wizard-toolbar">
      <button class="btn btn-sm btn-ghost" @click="router.push('/tasks')">‹ 返回列表</button>
      <div class="row">
        <button v-if="selectedStage && STEPS.findIndex(s => s.key === selectedStage) > 0" class="btn btn-sm" @click="goPrevious">‹ 上一步</button>
        <StageTag v-if="task" :status="task.status" :text="task.stage === 'RECORD' && task.status === 'PENDING' && task.resultSummary?.includes('录制完成') ? '待确认接口' : statusText[task.status] || task.status" />
        <button class="btn btn-sm" @click="load()">刷新</button>
      </div>
    </div>

    <p v-if="err" class="alert">{{ err }}</p>
    <p v-if="notice" class="alert notice">{{ notice }}</p>

    <!-- 三节点向导 -->
    <div class="steps">
      <template v-for="(s, i) in STEPS" :key="s.key">
        <button class="step" :class="{ on: selectedStage === s.key, done: i < currentIndex }" :disabled="(isNew && i > 0) || i > currentIndex" @click="selectedStage = s.key; followLatest = false">
          <span class="dot">{{ i < currentIndex ? '✓' : i + 1 }}</span><span class="meta"><strong>{{ s.label }}</strong><small>{{ s.desc }}</small></span>
        </button>
        <span v-if="i < STEPS.length - 1" class="flow-connector" :class="{ done: i < currentIndex }"></span>
      </template>
    </div>

    <div v-if="loading" class="loading">加载中…</div>

    <template v-else-if="task">
      <div v-if="running" class="panel running">
        <div class="panel-body">
          <div class="row" style="gap: 10px">
            <span class="spinner"></span>
            <div>
              <div>当前节点正在执行…</div>
              <p class="hint">录制/解析由 Agent 驱动，通常需要几秒到几十秒，页面会自动刷新。</p>
            </div>
          </div>
          <div class="row" style="justify-content: flex-end; margin-top: 14px">
            <button class="btn btn-danger" @click="terminate">终止任务</button>
          </div>
        </div>
      </div>

      <div v-else-if="task.status === 'FAILED'" class="panel running">
        <div class="panel-body row" style="justify-content: space-between">
          <div>
            <div style="color: var(--danger)">当前节点执行失败</div>
            <p class="hint">{{ task.resultSummary || '未返回失败原因' }}</p>
          </div>
          <button class="btn" @click="retry">重试</button>
        </div>
      </div>

      <template v-else>
        <StepRecord
          v-if="selectedStage === 'RECORD'"
          :task="task"
          @create-start="createAndStartRecording"
          @recording-started="enableRecordingPolling"
          @advanced="onStageAdvanced"
          @changed="load(true)"
        />
        <StepArrange
          v-else-if="selectedStage === 'PARSE'"
          :task="task"
          @advanced="onStageAdvanced"
          @changed="load(true)"
        />
        <StepGenerate
          v-else-if="selectedStage === 'GENERATE'"
          :task="task"
          @advanced="onStageAdvanced"
          @changed="load(true)"
        />
      </template>

    </template>
  </div>
</template>

<style scoped>
.wizard-toolbar { display:flex; align-items:center; justify-content:space-between; margin-bottom:13px; }
.steps { display:grid; grid-template-columns:1fr 48px 1fr 48px 1fr; align-items:center; gap:0; margin-bottom:16px; padding:14px 16px; border:1px solid #e2e7ee; border-radius:9px; background:#fff; }
.step {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 5px;
  background: transparent;
  border: 0;
  transition: .15s;
  cursor: pointer;
  text-align: left;
  font: inherit;
}
.step:disabled { opacity:.52; cursor:default; }
.step.on .meta strong { color:#245edb; }
.dot {
  width: 30px;
  height: 30px;
  flex: none;
  border-radius: 50%;
  background: #eef1f5;
  color: #778397;
  display: grid;
  place-items: center;
  font-size: 13px;
  font-weight:600;
}
.step.on .dot { background: #245edb; color: #fff; }
.step.done .dot { background: #398a69; color: #fff; }
.meta {display:grid;gap:3px}.meta strong {font-size:13px;color:#334156;font-weight:600}.meta small {font-size:11px;color:#8792a2}
.flow-connector { height:2px; background:#e2e7ee; margin:0 9px; }
.flow-connector.done { background:#65a78b; }
.back { position: absolute; top: 8px; right: 8px; }

.running { margin-bottom: 16px; }
.spinner {
  width: 16px; height: 16px; flex: none;
  border: 2px solid var(--line);
  border-top-color: var(--brand);
  border-radius: 50%;
  animation: spin .8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.alert {
  margin-bottom: 14px;
  padding: 9px 12px;
  border-radius: var(--radius-sm);
  background: var(--danger-soft);
  color: var(--danger);
  font-size: 13px;
}
.notice { background: var(--brand-soft); color: var(--brand-dark); }

.done-box { text-align: center; padding: 48px 16px; }
.done-icon {
  width: 48px; height: 48px;
  margin: 0 auto 14px;
  border-radius: 50%;
  background: var(--ok-soft);
  color: var(--ok);
  display: grid; place-items: center;
  font-size: 22px;
}
.done-box .row { justify-content: center; }

@media (max-width: 820px) {
  .steps { grid-template-columns:1fr; gap:2px; }
  .flow-connector { width:2px;height:12px;margin:0 19px; }
}
</style>
