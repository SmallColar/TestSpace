<script setup>
import { ref, computed, onMounted } from 'vue'
import draggable from 'vuedraggable'
import { api } from '../api'
import { store } from '../store'

const rules = ref([])
const keyword = ref('')
const creatorFilter = ref('')
const loading = ref(true)
const err = ref('')
const openId = ref(null)
const detailRule = ref(null)
const editorOpen = ref(false)
const mode = ref('create')
const saving = ref(false)
const form = ref({ id: null, sourceId: null, name: '', description: '', source: 'MANUAL', steps: [] })
const file = ref(null)
const analyzing = ref(false)
const uploadLabel = ref('')
const sourceTab = ref('manual')
const filtered = computed(() => rules.value.filter(r => (!keyword.value.trim() || (r.name || '').toLowerCase().includes(keyword.value.trim().toLowerCase())) && (!creatorFilter.value || creator(r).includes(creatorFilter.value))))
function parseSteps(r) { try { return JSON.parse(r.steps || '[]') } catch { return [] } }
function creator(r) { return r.creatorName || (r.userId === store.user?.id ? '我' : `用户 #${r.userId ?? '—'}`) }
function openDetail(rule) { detailRule.value = rule }
async function load() { loading.value = true; try { rules.value = await api.listRules(); err.value = '' } catch (e) { err.value = e.message } finally { loading.value = false } }
function openEditor(kind, rule = null) {
  mode.value = kind
  form.value = rule ? { id: kind === 'edit' ? rule.id : null, sourceId: rule.id, name: kind === 'copy' ? '' : rule.name, description: rule.description || '', source: kind === 'copy' ? 'HISTORY' : (rule.source || 'MANUAL'), steps: parseSteps(rule).map(s => ({ ...s })) } : { id: null, sourceId: null, name: '', description: '', source: 'MANUAL', steps: [] }
  file.value = null; uploadLabel.value = ''; editorOpen.value = true
  sourceTab.value = 'manual'
}
function addStep() { form.value.steps.push({ seq: form.value.steps.length + 1, method: 'GET', name: '', url: '' }) }
async function analyzeFile() {
  if (!file.value) { err.value = '请选择测试用例文件'; return }
  analyzing.value = true; err.value = ''
  try { const response = await api.analyzeRuleFile(file.value); form.value.name ||= response.ruleName; form.value.steps = JSON.parse(response.steps || '[]'); form.value.source = 'UPLOAD'; uploadLabel.value = `AI 已提取 ${form.value.steps.length} 个接口步骤，可继续拖拽调整或手工补充。` }
  catch (e) { err.value = e.message }
  finally { analyzing.value = false }
}
async function save() {
  err.value = ''
  if (!form.value.name.trim()) { err.value = '请输入编排规则名称'; return }
  if (!form.value.steps.length) { err.value = '请至少添加一个接口步骤'; return }
  const steps = form.value.steps.map((s, i) => ({ ...s, seq: i + 1, apiId: s.apiId ?? null }))
  const payload = { name: form.value.name.trim(), description: form.value.description, source: form.value.source, steps: JSON.stringify(steps) }
  saving.value = true
  try { if (mode.value === 'edit') await api.updateRule(form.value.id, payload); else await api.createRule(payload); editorOpen.value = false; await load() }
  catch (e) { err.value = e.message }
  finally { saving.value = false }
}
async function remove(rule) { if (!confirm(`确定删除编排规则「${rule.name}」吗？`)) return; try { await api.deleteRule(rule.id); await load() } catch (e) { err.value = e.message } }
function methodClass(m) { return `m-${(m || 'other').toLowerCase()}` }
onMounted(load)
</script>

<template>
  <section class="rules-page">
    <p v-if="err" class="alert">{{ err }}</p>
    <div v-if="!editorOpen && !detailRule">
    <div class="filters"><label>场景名称<input v-model="keyword" class="input search" placeholder="搜索场景名称" /></label><label>创建人<select v-model="creatorFilter" class="select"><option value="">全部用户</option><option v-for="name in [...new Set(rules.map(creator))]" :key="name">{{ name }}</option></select></label><button class="btn" @click="load">查询</button><button class="btn" @click="keyword='';creatorFilter=''">重置</button></div>
    <div class="list-head"><span>编排规则</span><button class="btn btn-primary" @click="openEditor('create')">＋ 新建编排规则</button></div>
    <div class="table-card"><table class="table"><thead><tr><th>编排规则名称</th><th>接口顺序</th><th>创建人</th><th>更新时间</th><th>操作</th></tr></thead><tbody>
      <tr v-if="loading"><td colspan="5" class="empty-cell">加载中…</td></tr><tr v-else-if="!filtered.length"><td colspan="5" class="empty-cell">暂无编排规则</td></tr>
      <template v-for="r in filtered" :key="r.id"><tr><td><button class="rule-link" @click="openDetail(r)">{{ r.name }}</button></td><td><div class="sequence-summary"><span v-for="(s,i) in parseSteps(r).slice(0,3)" :key="i">{{ s.method }} {{ s.url || s.name }}</span><span v-if="parseSteps(r).length>3" class="muted">+{{ parseSteps(r).length-3 }} 个</span></div></td><td>{{ creator(r) }}</td><td class="muted">{{ (r.updatedAt || '').replace('T',' ').slice(0,16) || '—' }}</td><td class="ops"><button class="link" @click="openDetail(r)">查看</button><button v-if="r.editable" class="link" @click="openEditor('edit',r)">编辑</button><button class="link" @click="openEditor('copy',r)">复制</button><button v-if="r.editable" class="link danger" @click="remove(r)">删除</button></td></tr></template>
    </tbody></table></div><div class="count">{{ filtered.length }} 条规则</div>
    </div>
    <div v-else-if="editorOpen" class="editor-page"><div class="editor"><button class="back-link" @click="editorOpen=false">‹ 返回编排规则库</button><header><div><h3>{{ mode==='edit'?'编辑编排规则':mode==='copy'?'复制编排规则':'新建编排规则' }}</h3><p>接口级顺序编排，不包含入参、前置或后置内容。</p></div></header><label>场景名称<input v-model="form.name" class="input" placeholder="例如：创建用户并查询详情" /></label><label>规则说明<input v-model="form.description" class="input" placeholder="选填，描述场景业务目标" /></label>
      <div class="source-tabs"><button :class="{active:sourceTab==='manual'}" @click="sourceTab='manual'">手动录入接口</button><button :class="{active:sourceTab==='upload'}" @click="sourceTab='upload'">上传测试用例 · AI 分析</button></div>
      <section v-if="sourceTab==='manual'" class="entry"><p class="entry-help">这里只录入接口及调用顺序；接口入参在生成用例时配置。</p><div class="entry-head"><b>接口编排</b><button class="btn btn-sm" @click="addStep">+ 添加接口</button></div><draggable v-model="form.steps" item-key="seq" handle=".handle" :animation="150" class="step-list"><template #item="{element,index}"><div class="step-editor"><span class="handle">⠿ {{ String(index+1).padStart(2,'0') }}</span><select v-model="element.method" class="select method-select"><option>GET</option><option>POST</option><option>PUT</option><option>PATCH</option><option>DELETE</option><option>API</option></select><input v-model="element.url" class="input" placeholder="接口 URL 或接口名称" /><button class="remove" @click="form.steps.splice(index,1)">×</button></div></template></draggable><div v-if="!form.steps.length" class="empty-small">添加接口后可拖拽调整顺序。</div></section>
      <section v-else class="entry upload"><b>上传测试用例，AI 分析接口顺序</b><p>支持 XMind、TXT、Markdown、JSON；分析结果只包含接口和顺序，可继续拖拽或手工补充。</p><div class="upload-controls"><input type="file" accept=".xmind,.txt,.md,.json" @change="file=$event.target.files?.[0]||null" /><button class="btn" :disabled="analyzing" @click="analyzeFile">{{ analyzing?'AI 分析中…':'AI 分析编排规则' }}</button></div><small v-if="uploadLabel">{{ uploadLabel }}</small></section>
      <div class="footer"><button class="btn" @click="editorOpen=false">取消</button><button class="btn btn-primary" :disabled="saving" @click="save">{{ saving?'保存中…':'保存编排规则' }}</button></div>
    </div></div>
    <div v-else class="rule-detail-page"><button class="back-link" @click="detailRule=null">‹ 返回规则列表</button><section class="detail-card"><div class="detail-title">基本信息</div><div class="detail-body"><h1>{{ detailRule.name }}</h1><div class="creator">创建人：{{ creator(detailRule) }}</div></div></section><section class="detail-card"><div class="detail-title">接口编排顺序</div><div class="detail-body"><div v-for="(step,index) in parseSteps(detailRule)" :key="index" class="detail-step"><b>{{ index+1 }}</b><span class="method" :class="methodClass(step.method)">{{ step.method || 'API' }}</span><code>{{ step.url || step.name || '未命名接口' }}</code></div></div></section></div>
  </section>
</template>

<style scoped>
.rules-page{max-width:1240px;margin:auto}.filters{display:grid;grid-template-columns:minmax(240px,380px) minmax(150px,190px) auto auto;align-items:end;justify-content:start;gap:12px;margin:0 0 15px;padding:14px 16px;background:#fff;border:1px solid #e2e7ee;border-radius:9px}.filters label{display:grid;gap:6px;min-width:0;color:#596679;font-size:12px}.filters .input,.filters .select{width:100%;min-width:0}.search{width:100%}.create{margin-left:auto}.table-card{background:#fff;border:1px solid #e5e9f0;border-radius:12px;overflow:hidden}.table{width:100%;border-collapse:collapse;table-layout:fixed}.table th,.table td{padding:14px 16px;text-align:left;vertical-align:middle;border-bottom:1px solid #edf0f4}.table th{background:#fafbfc;color:#7d899d;font-size:12px}.table th:nth-child(1){width:22%}.table th:nth-child(2){width:33%}.table th:nth-child(3){width:14%}.table th:nth-child(4){width:16%}.table th:nth-child(5){width:250px}.table tr:last-child td{border-bottom:0}.rule-link,.link{border:0;background:none;color:#3d65b8;cursor:pointer;padding:0}.rule-link{font-weight:600}.link{margin-right:12px;font-size:12px;white-space:nowrap}.danger{color:#c24d58}.sequence-summary{display:flex;gap:6px;align-items:center;overflow:hidden;white-space:nowrap}.sequence-summary span{max-width:145px;overflow:hidden;text-overflow:ellipsis;color:#67758a;background:#f4f6f9;border-radius:5px;padding:4px 6px;font:10px ui-monospace,Menlo,monospace}.ops{white-space:nowrap}.detail{background:#fbfcfe;padding:18px 20px!important}.creator{margin-left:15px;color:#8290a3;font-size:11px}.detail-flow{display:flex;flex-wrap:wrap;gap:9px;margin-top:13px}.detail-flow>span{display:flex;align-items:center;gap:7px;padding:8px 10px;background:#fff;border:1px solid #e8ecf1;border-radius:7px}.detail-flow i{font-style:normal;color:#8995a7;font-size:10px}.detail-flow b{font-size:10px;color:#3f68b7}.detail-flow b.m-get{color:#187e65}.detail-flow code{font-size:10px;color:#4a5970}.detail-flow em{font-style:normal;color:#a2adba;margin-left:3px}.count{padding:12px 2px;color:#8490a2;font-size:12px}.empty-cell{text-align:center!important;padding:50px!important;color:#8d98a9}.editor-page{width:100%}.editor{background:#fff;width:100%;max-height:none;overflow:visible;border:1px solid #e2e7ee;border-radius:9px;padding:20px;display:grid;gap:15px;box-shadow:none}.back-link{border:0;background:none;color:#526176;text-align:left;padding:0 0 2px;cursor:pointer}.editor header{display:flex;justify-content:space-between;align-items:flex-start}.editor h3{margin:0;font-size:18px}.editor header p,.upload p{margin:5px 0 0;color:#8490a2;font-size:12px}.editor>label{display:grid;gap:7px;color:#58667b;font-size:12px}.entry{border:1px solid #e8ecf1;border-radius:9px;padding:14px}.entry-head{display:flex;align-items:center;justify-content:space-between;color:#495970;font-size:12px}.step-list{display:grid;gap:7px;margin-top:10px}.step-editor{display:grid;grid-template-columns:70px 100px minmax(0,1fr) 25px;gap:8px;align-items:center}.handle{cursor:grab;color:#8793a5;font-size:11px}.method-select{height:36px}.remove{border:0;background:none;color:#a0aab7;font-size:20px;cursor:pointer}.empty-small{padding:16px;color:#98a3b2;text-align:center;font-size:12px}.upload{margin-top:0}.upload>b{font-size:12px;color:#495970}.upload-controls{display:flex;align-items:center;gap:12px;margin-top:12px}.upload-controls input{flex:1;min-width:0;font-size:12px}.upload small{display:block;margin-top:9px;color:#398065}.footer{display:flex;justify-content:flex-end;gap:9px}.alert{background:#fff1f1;color:#b43742;border-radius:8px;padding:10px;margin:0 0 12px}@media(max-width:800px){.filters{grid-template-columns:minmax(0,1fr) minmax(125px,.65fr) auto auto;gap:9px;padding:12px}.table-card{overflow:auto}.table{min-width:900px}.step-editor{grid-template-columns:50px 80px minmax(0,1fr) 20px}}@media(max-width:540px){.filters{grid-template-columns:minmax(0,1fr) minmax(120px,.7fr)}.filters button{justify-self:start}}
.rule-detail-page{display:grid;gap:14px}.detail-card{border:1px solid #e1e6ed;border-radius:9px;background:#fff;overflow:hidden}.detail-title{padding:13px 16px;border-bottom:1px solid #edf0f4;color:#354256;font-weight:600}.detail-body{padding:18px 20px}.detail-body h1{margin:0;font-size:20px;color:#202b3a}.detail-body .creator{display:block;margin:10px 0 0}.detail-step{display:grid;grid-template-columns:34px 66px minmax(0,1fr);gap:10px;align-items:center;padding:12px 0;border-bottom:1px solid #edf0f4}.detail-step:last-child{border-bottom:0}.detail-step>b{color:#245edb}.detail-step code{color:#354256;font:13px ui-monospace,Menlo,monospace;overflow-wrap:anywhere}
</style>

<style scoped>
.list-head{display:flex;justify-content:space-between;align-items:center;padding:11px 15px;border:1px solid #e2e7ee;border-bottom:0;border-radius:9px 9px 0 0;background:#fff;color:#526073;font-size:13px}.table-card{border-radius:0 0 9px 9px}.filters label{display:grid;gap:5px;color:#596679;font-size:12px}.source-tabs{display:flex;gap:18px;border-bottom:1px solid #e8edf3}.source-tabs button{padding:11px 2px 9px;border:0;border-bottom:2px solid transparent;background:transparent;color:#718095;font-size:13px;cursor:pointer}.source-tabs button.active{border-bottom-color:#245edb;color:#245edb;font-weight:600}.entry-help{margin:0 0 12px;color:#7b8797;font-size:12px}
</style>
