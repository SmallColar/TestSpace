<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import StageTag from '../components/StageTag.vue'

const router = useRouter()
const tasks = ref([])
const loading = ref(true)
const err = ref('')
const keyword = ref('')
const status = ref('')
const createdWithin = ref('')

const visibleTasks = computed(() => tasks.value.filter(t => {
  const q = keyword.value.trim().toLowerCase()
  const created = t.createdAt ? new Date(t.createdAt).getTime() : 0
  const days = Number(createdWithin.value)
  return (!q || (t.name || '').toLowerCase().includes(q)) && (!status.value || t.status === status.value) && (!days || !created || Date.now() - created <= days * 86400000)
}))
const statusText = { PENDING: '待录制', RUNNING: '录制中', COMPLETED: '已完成', FAILED: '失败', CANCELLED: '已终止' }
const stageText = { RECORD: '录制信息', PARSE: '接口与场景编排', GENERATE: 'AI 生成用例', DONE: '已完成' }

async function load() {
  loading.value = true
  try { tasks.value = await api.listTasks(); err.value = '' }
  catch (e) { err.value = e.message }
  finally { loading.value = false }
}
function copyTask(task) { router.push({ path: '/tasks/new', query: { copy: task.id } }) }
function newTask() { router.push('/tasks/new') }
async function deleteTask(task) {
  if (!window.confirm(`确定删除自动化任务「${task.name}」吗？相关录制接口、编排规则和未完成用例也会一并删除。`)) return
  try { await api.deleteTask(task.id); await load() } catch (e) { err.value = e.message }
}
function fmt(t) { return t ? String(t).replace('T', ' ').slice(0, 16) : '—' }
onMounted(load)
</script>

<template>
  <section class="task-page">
    <p v-if="err" class="alert">{{ err }}</p>
    <div class="filters">
      <label>任务名称<input v-model="keyword" class="input" placeholder="输入任务名称" /></label>
      <label>任务状态<select v-model="status" class="select"><option value="">全部状态</option><option v-for="(label, key) in statusText" :key="key" :value="key">{{ label }}</option></select></label>
      <label>创建时间<select v-model="createdWithin" class="select"><option value="">全部时间</option><option value="7">最近 7 天</option><option value="30">最近 30 天</option></select></label>
      <button class="btn" @click="load">查询</button><button class="btn" @click="keyword='';status='';createdWithin=''">重置</button>
    </div>
    <div class="list-head"><span>任务列表</span><button class="btn btn-primary" @click="newTask">＋ 新建任务</button></div>
    <div class="table-card"><table class="table"><thead><tr><th>任务名称</th><th>创建时间</th><th>状态</th><th class="actions-col">操作</th></tr></thead><tbody>
      <tr v-if="loading"><td colspan="4" class="empty-cell">加载中…</td></tr><tr v-else-if="!visibleTasks.length"><td colspan="4" class="empty-cell">暂无自动化任务</td></tr>
      <tr v-for="task in visibleTasks" :key="task.id"><td class="task-name"><strong>{{ task.name || '未命名任务' }}</strong><small>{{ task.targetUrl || '—' }}</small></td><td class="muted">{{ fmt(task.createdAt) }}</td><td><StageTag :status="task.status" :text="statusText[task.status] || task.status" /></td><td class="actions-cell"><button class="link-btn" @click="router.push(`/tasks/${task.id}`)">查看</button><button class="link-btn" @click="copyTask(task)">复制</button><button class="link-btn danger" @click="deleteTask(task)">删除</button></td></tr>
    </tbody></table></div><div class="bottom-action">共 {{ visibleTasks.length }} 条</div>
  </section>
</template>

<style scoped>
.task-page{max-width:1240px;margin:0 auto}.filters{display:flex;gap:11px;align-items:end;margin:0 0 14px;padding:15px 16px;background:#fff;border:1px solid #e2e7ee;border-radius:9px}.filters label{flex:1 1 175px;color:#596679;font-size:12px}.filters input,.filters select{display:block;margin-top:6px;width:100%}.list-head{display:flex;align-items:center;justify-content:space-between;padding:11px 15px;border:1px solid #e2e7ee;border-bottom:0;border-radius:9px 9px 0 0;background:#fff;color:#526073;font-size:13px}.table-card{background:#fff;border:1px solid #e2e7ee;border-radius:0 0 9px 9px;overflow:hidden}.table{width:100%;table-layout:fixed;border-collapse:collapse}.table th,.table td{padding:13px 15px;text-align:left;vertical-align:middle;border-bottom:1px solid #edf0f4}.table th{font-size:12px;color:#77839a;font-weight:500;background:#fafbfc}.table tr:last-child td{border-bottom:0}.table th:nth-child(1){width:48%}.table th:nth-child(2){width:21%}.table th:nth-child(3){width:15%}.table th:nth-child(4){width:16%}.task-name{color:#26344b}.task-name strong{display:block;font-weight:600}.task-name small{display:block;margin-top:3px;font-size:12px;font-weight:400;color:#778394}.actions-cell{white-space:nowrap}.link-btn{border:0;background:none;color:#3b65c4;cursor:pointer;margin-right:12px;padding:3px}.link-btn.danger{color:#bf4b56}.bottom-action{padding:13px 3px;color:#7e8998;font-size:12px}.empty-cell{text-align:center!important;padding:52px!important;color:#8a95a8}.alert{color:#b43742;background:#fff1f1;border-radius:8px;padding:10px 13px;margin:0 0 12px}@media(max-width:800px){.filters{flex-wrap:wrap}.table{min-width:760px}.table-card{overflow:auto}}
</style>
