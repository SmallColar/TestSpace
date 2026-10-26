<script setup>
import { ref, computed, watch } from 'vue'
import { api } from '../../api'
const props = defineProps({ task: { type: Object, required: true } })
const emit = defineEmits(['changed', 'create-start', 'recording-started'])
const apis = ref([])
const selectedApiIds = ref([])
const loading = ref(true)
const busy = ref(false)
const confirming = ref(false)
const err = ref('')
const expanded = ref(null)
const form = ref({ name: '', targetUrl: '', recordingGoal: '', browserProvider: 'chrome-devtools-mcp' })
const initialForm = ref(null)
let initialTaskId = null
const editable = computed(() => props.task.status !== 'RUNNING')
const infoFields = ['name', 'targetUrl', 'recordingGoal', 'browserProvider']
const infoChanged = computed(() => infoFields.some(key => (form.value[key] || '') !== (props.task[key] || (key === 'browserProvider' ? 'chrome-devtools-mcp' : ''))))
const formDirty = computed(() => initialForm.value && infoFields.some(key => (form.value[key] || '') !== (initialForm.value[key] || '')))
const displayPrompt = computed(() => props.task.recordingGoal || '')

watch(() => props.task, task => {
  // Keep unsaved edits across refreshes of the same task, while hydrating a
  // newly opened task (including a copied task with legacy filter text).
  if (initialForm.value && task.id === initialTaskId && formDirty.value && task.status !== 'RUNNING') return
  form.value = { name: task.name || '', targetUrl: task.targetUrl || '', recordingGoal: task.recordingGoal || '', browserProvider: task.browserProvider || 'chrome-devtools-mcp' }
  initialForm.value = { ...form.value }
  initialTaskId = task.id
}, { immediate: true })
async function load() {
  if (!props.task.id) { apis.value = []; loading.value = false; return }
  loading.value = true
  try {
    apis.value = await api.getApis(props.task.id)
    selectedApiIds.value = apis.value.filter(item => item.checked !== false).map(item => item.id)
    err.value = ''
  }
  catch (e) { err.value = e.message }
  finally { loading.value = false }
}
async function saveInfo() {
  if (!form.value.name.trim() || !form.value.targetUrl.trim() || !form.value.recordingGoal.trim()) {
    err.value = '请填写任务名称、目标网址和探索目标提示词'; return false
  }
  if (!infoChanged.value) return true
  form.value.name = form.value.name.trim()
  form.value.targetUrl = form.value.targetUrl.trim()
  form.value.recordingGoal = form.value.recordingGoal.trim()
  await api.updateTask({ ...form.value, id: Number(props.task.id) })
  initialForm.value = { ...form.value }
  emit('changed')
  return true
}
async function save() {
  busy.value = true; err.value = ''
  try { await saveInfo() } catch (e) { err.value = e.message }
  finally { busy.value = false }
}
async function start() {
  busy.value = true; err.value = ''
  try {
    if (!props.task.id) emit('create-start', { ...form.value })
    else if (await saveInfo()) {
      if (props.task.stage === 'RECORD') await api.startRecording(props.task.id)
      else await api.restartRecording(props.task.id)
      emit('recording-started')
    }
  }
  catch (e) { err.value = e.message }
  finally { busy.value = false }
}
async function confirmInterfaces() {
  err.value = ''
  if (!selectedApiIds.value.length) { err.value = '请至少保留一个接口再继续'; return }
  if (props.task.stage !== 'RECORD' && !window.confirm('修改已确认的接口会清空当前编排和未保存用例草稿，已保存用例保留。是否继续？')) return
  confirming.value = true
  try {
    const updated = await api.confirmRecording(Number(props.task.id), selectedApiIds.value)
    emit('advanced', updated)
  } catch (e) { err.value = e.message }
  finally { confirming.value = false }
}
function fmt(value) { if (!value) return '—'; try { return JSON.stringify(JSON.parse(value), null, 2) } catch { return value } }
watch([() => props.task.id, () => props.task.status], ([id, status]) => {
  if (id && status !== 'RUNNING') load()
}, { immediate: true })
</script>

<template>
  <div class="record-step">
    <p v-if="err" class="alert">{{ err }}</p>
    <section class="panel info-panel">
      <div class="panel-head"><h3>录制信息</h3><span class="muted">{{ task.browserProvider === 'playwright-mcp' ? 'Playwright MCP' : 'Chrome DevTools MCP' }}</span></div>
      <div v-if="editable" class="info-form">
        <label>任务名称<input v-model="form.name" class="input" /></label>
        <label>目标网址<input v-model="form.targetUrl" class="input" /></label>
        <label>探索目标与要求（用户提示词）<textarea v-model="form.recordingGoal" class="textarea" rows="4" placeholder="描述希望 AI 探索的业务目标、边界，以及重点保留或排除的接口。例如：探索用户注册和登录后的主要流程，重点关注创建、查询和修改用户信息，忽略埋点与静态资源请求。" /><small class="field-hint">这段提示词会同时指导 AI 探索网页和筛选录制接口；描述目标即可，不需要逐步列出页面操作。</small></label>
        <div class="info-actions"><span class="hint">录制开始前可以修改任务信息</span><button class="btn" :disabled="busy || !infoChanged" @click="save">{{ busy ? '保存中…' : '保存录制信息' }}</button></div>
      </div>
      <div v-else class="info-grid"><div><span>任务名称</span><strong>{{ task.name }}</strong></div><div class="wide"><span>目标网址</span><strong class="target-url">{{ task.targetUrl }}</strong></div><div class="wide"><span>探索目标与要求</span><p>{{ displayPrompt }}</p></div></div>
      <div v-if="task.status !== 'RUNNING'" class="record-action"><span class="hint">{{ task.stage === 'RECORD' ? 'AI 会根据提示词自主探索网页，并按提示筛选相关接口。' : '重新录制会替换接口记录、清空当前编排和未保存用例草稿；已保存用例保留。' }}</span><button class="btn btn-primary" :disabled="busy" @click="start">{{ busy ? '启动中…' : task.stage === 'RECORD' ? (apis.length ? '重新开始录制' : '开始录制') : '返回并重新录制' }}</button></div>
    </section>
    <section class="panel result-panel">
      <div class="panel-head"><div><h3>录制结果</h3><p v-if="apis.length" class="result-hint">勾选要用于编排的接口，默认全选；确认后进入场景编排。</p></div><span class="tag tag-brand">{{ apis.length }} 个接口</span></div>
      <div v-if="loading" class="loading">读取接口…</div><div v-else-if="!apis.length" class="empty">启动 AI 录制后，采集到的接口会显示在这里。</div>
      <div v-else class="api-list"><article v-for="apiItem in apis" :key="apiItem.id" class="api-row">
        <div class="api-line"><label class="api-check" :title="selectedApiIds.includes(apiItem.id) ? '保留此接口' : '排除此接口'"><input v-model="selectedApiIds" type="checkbox" :value="apiItem.id" :aria-label="`选择接口 ${apiItem.method} ${apiItem.url}`" /></label><button class="api-summary" @click="expanded = expanded === apiItem.id ? null : apiItem.id"><span class="seq">{{ String(apiItem.seq).padStart(2, '0') }}</span><span class="method" :class="(apiItem.method || '').toLowerCase()">{{ apiItem.method }}</span><span class="api-url" :title="apiItem.url">{{ apiItem.url }}</span><span class="http-code">{{ apiItem.httpStatus || '—' }}</span><span class="chev">{{ expanded === apiItem.id ? '−' : '+' }}</span></button></div>
        <div v-if="expanded === apiItem.id" class="api-detail"><div class="detail-block"><b>请求头与入参</b><pre>{{ fmt(apiItem.requestHeaders) }}\n{{ fmt(apiItem.requestBody) }}</pre></div><div class="detail-block"><b>响应</b><pre>{{ fmt(apiItem.responseHeaders) }}\n{{ fmt(apiItem.responseBody) }}</pre></div></div>
      </article></div>
      <div v-if="apis.length && task.status !== 'RUNNING'" class="confirm-row"><span class="hint">已选 {{ selectedApiIds.length }} / {{ apis.length }} 个接口</span><button class="btn btn-primary" :disabled="confirming || !selectedApiIds.length" @click="confirmInterfaces">{{ confirming ? '确认中…' : task.stage === 'RECORD' ? '确认接口并进入编排' : '保存接口选择并返回编排' }}</button></div>
    </section>
  </div>
</template>

<style scoped>
.record-step{display:grid;gap:16px}.panel{background:#fff;border:1px solid #e5e9f0;border-radius:12px;overflow:hidden}.panel-head{padding:17px 20px;display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid #edf0f4}.panel-head h3{font-size:15px;margin:0;color:#293850}.result-hint{margin:5px 0 0;color:#8793a5;font-size:12px}.info-grid{display:grid;grid-template-columns:1fr 1fr;gap:20px;padding:20px}.info-grid>div{min-width:0;display:grid;gap:7px}.info-grid .wide{grid-column:1/-1}.info-grid span{font-size:12px;color:#7c899d}.info-grid strong{font-size:14px;color:#34445c;font-weight:600}.target-url{color:#3565bd!important;word-break:break-all;font-size:15px!important}.info-grid p{margin:0;color:#45536a;line-height:1.65;white-space:pre-wrap}.info-form{display:grid;grid-template-columns:1fr 1fr;gap:16px;padding:20px}.info-form label{display:grid;gap:7px;color:#748198;font-size:12px}.info-form label:nth-child(n+3){grid-column:1/-1}.field-hint{font-size:11px;color:#909bad;line-height:1.5}.info-actions{grid-column:1/-1;display:flex;align-items:center;justify-content:space-between;gap:12px}.record-action{display:flex;align-items:center;justify-content:space-between;gap:18px;padding:15px 20px;border-top:1px solid #edf0f4}.confirm-row{display:flex;align-items:center;justify-content:space-between;gap:14px;padding:15px 20px;border-top:1px solid #edf0f4}.api-check{display:grid;place-items:center}.api-check input{width:16px;height:16px;cursor:pointer}.api-line{display:grid;grid-template-columns:48px minmax(0,1fr);align-items:center}.result-panel .empty{padding:38px 20px;text-align:center;color:#929daf}.api-row{border-bottom:1px solid #edf0f4}.api-summary{border:0;width:100%;background:#fff;display:grid;grid-template-columns:44px 68px minmax(0,1fr) 65px 22px;gap:12px;align-items:center;text-align:left;padding:15px 20px;cursor:pointer}.api-summary:hover{background:#fafbfd}.seq,.http-code{color:#8a95a6;font-size:12px;font-variant-numeric:tabular-nums}.method{font-size:11px;font-weight:700;color:#3565bd}.method.get{color:#178364}.method.post{color:#3565bd}.method.delete{color:#c14e58}.api-url{font-family:ui-monospace,Menlo,monospace;font-size:12px;color:#394960;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.chev{font-size:17px;color:#8591a3}.api-detail{display:grid;grid-template-columns:1fr 1fr;gap:12px;padding:0 20px 16px 132px}.detail-block{min-width:0}.detail-block b{font-size:11px;color:#7f8ca1}.detail-block pre{margin:6px 0 0;padding:10px;background:#f7f8fa;border-radius:7px;max-height:220px;overflow:auto;font-size:11px;white-space:pre-wrap;word-break:break-all;color:#48566d}.alert{color:#b43742;background:#fff1f1;padding:10px;border-radius:8px}@media(max-width:760px){.info-grid,.info-form{grid-template-columns:1fr}.info-form label:nth-child(n+3){grid-column:auto}.info-actions,.confirm-row{align-items:flex-start;flex-direction:column}.api-summary{grid-template-columns:28px 48px minmax(0,1fr) 40px 16px;gap:7px;padding:13px 10px}.api-detail{grid-template-columns:1fr;padding-left:58px}.record-action{align-items:flex-start;flex-direction:column}}
</style>
