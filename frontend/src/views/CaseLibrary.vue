<script setup>
import { ref, computed, onMounted } from 'vue'
import draggable from 'vuedraggable'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { store } from '../store'

const router = useRouter()
const cases = ref([])
const tasks = ref([])
const keyword = ref('')
const sourceType = ref('')
const ownerFilter = ref('')
const taskFilter = ref('')
const loading = ref(true)
const err = ref('')
const detailCase = ref(null)
const openStep = ref(null)
const tab = ref('input')
const editorOpen = ref(false)
const editorMode = ref('create')
const saving = ref(false)
const editStepIndex = ref(0)
const editTab = ref('input')
let nextStepKey = 1
const form = ref({ id: null, name: '', description: '', publicVars: [], steps: [], assertions: '[]' })

const filtered = computed(() => cases.value.filter(c => {
  const q = keyword.value.trim().toLowerCase()
  return (!q || (c.name || '').toLowerCase().includes(q) || String(c.id).includes(q)) && (!sourceType.value || (c.sourceType || 'TASK_GENERATED') === sourceType.value) && (!ownerFilter.value || creator(c).includes(ownerFilter.value)) && (!taskFilter.value || taskName(c.taskId).toLowerCase().includes(taskFilter.value.trim().toLowerCase()))
}))
function parse(value, fallback = []) { try { return typeof value === 'string' ? JSON.parse(value || JSON.stringify(fallback)) : (value ?? fallback) } catch { return fallback } }
function steps(c) { return parse(c.steps) }
function publicParams(c) { return parse(c.publicVars) }
function sourceLabel(c) { return c.sourceType === 'MANUAL_CREATED' ? '人工新建' : '录制生成' }
function creator(c) { return c.creatorName || (c.userId === store.user?.id ? '我' : `用户 #${c.userId ?? '—'}`) }
function taskName(id) { return tasks.value.find(t => t.id === id)?.name || `任务 #${id}` }
async function load() {
  loading.value = true
  try { [cases.value, tasks.value] = await Promise.all([api.listCases(), api.listTasks()]); err.value = '' }
  catch (e) { err.value = e.message }
  finally { loading.value = false }
}
function viewCase(c) { detailCase.value = c; openStep.value = null; tab.value = 'input' }
function openEditor(mode, c = null) {
  editorMode.value = mode
  if (c) {
    form.value = { id: mode === 'edit' ? c.id : null, sourceCaseId: c.id, name: mode === 'copy' ? '' : c.name, description: c.description || '', publicVars: publicParams(c).map(p => ({ ...p })), steps: steps(c).map((s, i) => ({ ...s, editorKey: nextStepKey++, input: { format: s.input?.format || 'JSON', ...s.input, jsonText: JSON.stringify(s.input?.jsonBody ?? s.params ?? {}, null, 2) }, precondition: s.precondition || '', postcondition: s.postcondition || '' })), assertions: c.assertions || '[]' }
  } else form.value = { id: null, sourceCaseId: null, name: '', description: '', publicVars: [], steps: [emptyStep()], assertions: '[]' }
  editStepIndex.value = 0; editTab.value = 'input'
  editorOpen.value = true
}
function emptyStep() { return { editorKey: nextStepKey++, sequence: form.value.steps.length + 1, method: 'POST', url: '', input: { format: 'JSON', jsonBody: {}, jsonText: '{\n  \n}' }, precondition: '', postcondition: '' } }
function addStep() { form.value.steps.push(emptyStep()); editStepIndex.value = form.value.steps.length - 1; editTab.value = 'input' }
function addPublicParam() { form.value.publicVars.push({ name: '', source: '', value: '' }) }
function addFormField(step) { step.input.formFields ||= []; step.input.formFields.push({ name: '', value: '' }) }
function addFile(step) { step.input.files ||= []; step.input.files.push({ fieldName: '', fileName: '', contentType: '', size: '' }) }
async function save() {
  err.value = ''
  if (!form.value.name.trim()) { err.value = '请输入用例名称'; return }
  const stepsPayload = []
  try {
    for (const [index, step] of form.value.steps.entries()) {
      const input = { format: step.input?.format || 'JSON' }
      if (input.format === 'JSON') input.jsonBody = JSON.parse(step.input.jsonText || '{}')
      if (input.format === 'FORM') input.formFields = (step.input.formFields || []).map(({ name, key, value }) => ({ name: name || key, value }))
      if (input.format === 'FILE') input.files = step.input.files || []
      stepsPayload.push({ ...step, sequence: index + 1, input, precondition: step.precondition || '', postcondition: step.postcondition || '' })
    }
  } catch { err.value = '第 ' + (editStepIndex.value + 1) + ' 个接口的 JSON 入参格式不正确'; return }
  saving.value = true
  const payload = { name: form.value.name.trim(), description: form.value.description, steps: JSON.stringify(stepsPayload), publicVars: JSON.stringify(form.value.publicVars), assertions: form.value.assertions }
  try { if (editorMode.value === 'edit') await api.updateCase(form.value.id, payload); else if (editorMode.value === 'copy') await api.copyCase({ sourceCaseId: form.value.sourceCaseId, caseData: payload }); else await api.createCase(payload); editorOpen.value = false; await load() }
  catch (e) { err.value = e.message }
  finally { saving.value = false }
}
async function remove(c) {
  if (!window.confirm(`确定删除用例「${c.name}」吗？`)) return
  try { await api.deleteCase(c.id); await load() } catch (e) { err.value = e.message }
}
function fmt(t) { return t ? String(t).replace('T', ' ').slice(0, 16) : '—' }
function inputView(step) { const input = step.input || {}; return input }
onMounted(load)
</script>

<template>
  <section class="library">
    <p v-if="err" class="alert">{{ err }}</p>
    <div v-if="!editorOpen && !detailCase">
    <div class="filters"><label>用例名称<input v-model="keyword" class="input search" placeholder="搜索自动化用例" /></label><label>创建人<select v-model="ownerFilter" class="select"><option value="">全部用户</option><option v-for="name in [...new Set(cases.map(creator))]" :key="name">{{ name }}</option></select></label><label>来源录制任务<input v-model="taskFilter" class="input" placeholder="搜索录制任务" /></label><button class="btn" @click="load">查询</button><button class="btn" @click="keyword='';ownerFilter='';taskFilter='';sourceType=''">重置</button></div>
    <div class="list-head"><span>自动化用例</span><button class="btn btn-primary" @click="openEditor('create')">＋ 新建用例</button></div>
    <div class="table-card"><table class="table"><thead><tr><th>用例名称</th><th>录制来源</th><th>创建人</th><th>操作</th></tr></thead><tbody>
      <tr v-if="loading"><td colspan="4" class="empty-cell">加载中…</td></tr><tr v-else-if="!filtered.length"><td colspan="4" class="empty-cell">暂无自动化用例</td></tr>
      <template v-for="c in filtered" :key="c.id"><tr><td><button class="case-link" @click="viewCase(c)">{{ c.name }}</button><div class="case-meta">创建于 {{ fmt(c.createdAt) }}</div></td><td><span class="source">{{ sourceLabel(c) }}</span><button v-if="c.taskId && tasks.some(t => t.id === c.taskId)" class="task-link" @click="router.push(`/tasks/${c.taskId}`)">{{ taskName(c.taskId) }} ↗</button><span v-else-if="c.taskId" class="task-name">{{ taskName(c.taskId) }}</span></td><td>{{ creator(c) }}</td><td class="actions"><button class="link" @click="viewCase(c)">查看</button><button class="link" @click="openEditor('copy',c)">复制</button><button v-if="c.editable" class="link" @click="openEditor('edit',c)">编辑</button><button v-if="c.editable" class="link danger" @click="remove(c)">删除</button></td></tr>
      </template>
    </tbody></table></div>
    <div class="count">{{ filtered.length }} 条用例</div>
    </div>
    <div v-else-if="editorOpen" class="editor-page"><div class="editor">
      <button class="back-link" @click="editorOpen=false">‹ 返回自动化用例库</button>
      <header><div><h3>{{ editorMode==='edit'?'编辑用例':editorMode==='copy'?'复制用例':'新建用例' }}</h3><p>公共参数放在上方；每个接口单独维护入参、前置和后置内容。</p></div></header>
      <label class="case-name-field">用例名称<input v-model="form.name" class="input" placeholder="输入用例名称" /></label>
      <label class="case-name-field">说明<input v-model="form.description" class="input" placeholder="选填" /></label>
      <section class="edit-section"><div class="section-heading"><div><h4>公共参数</h4><small>场景内接口可以引用这些参数</small></div><button class="btn btn-sm" @click="addPublicParam">＋ 添加参数</button></div><div class="param-editor"><div class="param-editor-head"><span>参数名</span><span>来源 / 说明</span><span>值</span><i></i></div><div v-for="(param,index) in form.publicVars" :key="index" class="param-editor-row"><input v-model="param.name" class="input" placeholder="例如 userId" /><input v-model="param.source" class="input" placeholder="例如步骤 1 响应中的用户 ID" /><input v-model="param.value" class="input" placeholder="动态提取或固定值" /><button class="remove" @click="form.publicVars.splice(index,1)">×</button></div><div v-if="!form.publicVars.length" class="empty-small">暂无公共参数</div></div></section>
      <section class="edit-section"><div class="section-heading"><div><h4>接口步骤</h4><small>拖动接口行可调整执行顺序</small></div><button class="btn btn-sm" @click="addStep">＋ 添加接口</button></div>
        <draggable v-model="form.steps" item-key="editorKey" handle=".step-drag" :animation="150" class="edit-steps"><template #item="{element,index}"><article class="edit-step"><button class="edit-step-head" @click="editStepIndex=editStepIndex===index?-1:index"><span class="step-drag">⠿</span><span class="step-number">{{ String(index+1).padStart(2,'0') }}</span><select v-model="element.method" class="select method-select" @click.stop><option>GET</option><option>POST</option><option>PUT</option><option>PATCH</option><option>DELETE</option></select><input v-model="element.url" class="input endpoint-input" placeholder="接口 URL" @click.stop /><span class="chevron">{{ editStepIndex===index?'⌃':'⌄' }}</span><span class="remove-step" @click.stop="form.steps.splice(index,1);editStepIndex=-1">×</span></button>
          <div v-if="editStepIndex===index" class="edit-step-body"><div class="edit-tabs"><button :class="{active:editTab==='input'}" @click="editTab='input'">入参</button><button :class="{active:editTab==='pre'}" @click="editTab='pre'">前置</button><button :class="{active:editTab==='post'}" @click="editTab='post'">后置</button></div>
            <div v-if="editTab==='input'" class="edit-tab-panel"><div class="input-format"><span>入参格式</span><select v-model="element.input.format" class="select"><option value="JSON">JSON 请求体</option><option value="FORM">表单字段</option><option value="FILE">文件上传</option></select></div><textarea v-if="element.input.format==='JSON'" v-model="element.input.jsonText" class="textarea code" rows="7" placeholder="输入 JSON 请求体" /><div v-else-if="element.input.format==='FORM'" class="field-editor"><div v-for="(field,fi) in element.input.formFields||[]" :key="fi"><input v-model="field.name" class="input" placeholder="字段名" /><input v-model="field.value" class="input" placeholder="字段值" /><button class="remove" @click="element.input.formFields.splice(fi,1)">×</button></div><button class="link-btn" @click="addFormField(element)">＋ 添加表单字段</button></div><div v-else class="field-editor file-editor"><div v-for="(file,fi) in element.input.files||[]" :key="fi"><input v-model="file.fieldName" class="input" placeholder="上传字段名" /><input v-model="file.fileName" class="input" placeholder="文件名" /><input v-model="file.contentType" class="input" placeholder="文件类型" /><input v-model="file.size" class="input" placeholder="文件大小" /><button class="remove" @click="element.input.files.splice(fi,1)">×</button></div><button class="link-btn" @click="addFile(element)">＋ 添加文件</button></div></div>
            <div v-else-if="editTab==='pre'" class="edit-tab-panel"><label>前置处理<textarea v-model="element.precondition" class="textarea" rows="4" placeholder="描述调用该接口前需要完成的准备" /></label></div>
            <div v-else class="edit-tab-panel"><label>后置校验<textarea v-model="element.postcondition" class="textarea" rows="4" placeholder="描述响应结果需要满足的条件" /></label></div>
          </div></article></template></draggable><div v-if="!form.steps.length" class="empty-small">添加接口后可维护每一步的输入和校验。</div>
      </section>
      <div class="footer"><button class="btn" @click="editorOpen=false">取消</button><button class="btn btn-primary" :disabled="saving" @click="save">{{ saving?'保存中…':'保存用例' }}</button></div>
    </div></div>
    <div v-else class="case-detail-page"><button class="back-link" @click="detailCase=null">‹ 返回用例列表</button><section class="case-info-card"><div class="case-info-title">用例基本信息</div><div class="case-info-body"><h1>{{ detailCase.name }}</h1><div class="case-basic"><span>创建人：<strong>{{ creator(detailCase) }}</strong></span><span>录制来源：<strong>{{ detailCase.taskId ? taskName(detailCase.taskId) : '人工新建' }}</strong></span><button v-if="detailCase.taskId && tasks.some(t=>t.id===detailCase.taskId)" class="task-link" @click="router.push(`/tasks/${detailCase.taskId}`)">查看录制任务 ↗</button></div></div></section>
      <section class="case-info-card"><div class="case-info-title">公共参数</div><div class="case-info-body"><table v-if="publicParams(detailCase).length" class="public-table"><thead><tr><th>参数名</th><th>来源 / 说明</th><th>示例值</th></tr></thead><tbody><tr v-for="(param,index) in publicParams(detailCase)" :key="index"><td><code>{{ param.name || param.key }}</code></td><td>{{ param.source || param.description || '—' }}</td><td>{{ param.value || '动态提取' }}</td></tr></tbody></table><span v-else class="muted">暂无公共参数</span></div></section>
      <section class="case-info-card"><div class="case-info-title">接口步骤</div><div class="case-info-body"><div class="interface-list"><article v-for="(step,index) in steps(detailCase)" :key="index" class="interface-item"><button class="interface-summary" @click="openStep=openStep===`${detailCase.id}-${index}`?null:`${detailCase.id}-${index}`"><span class="interface-number">{{ String(index+1).padStart(2,'0') }}</span><span class="http-method" :class="(step.method||'').toLowerCase()">{{ step.method || 'API' }}</span><span class="interface-url">{{ step.url || step.api || '—' }}</span><span class="interface-toggle">{{ openStep===`${detailCase.id}-${index}`?'⌃':'⌄' }}</span></button><div v-if="openStep===`${detailCase.id}-${index}`" class="interface-detail"><div class="interface-tabs"><button :class="{active:tab==='input'}" @click="tab='input'">入参</button><button :class="{active:tab==='pre'}" @click="tab='pre'">前置</button><button :class="{active:tab==='post'}" @click="tab='post'">后置</button></div><div v-if="tab==='input'" class="interface-subpanel"><p class="input-kind">{{ inputView(step).format || 'JSON' }} 入参</p><pre v-if="(inputView(step).format||'JSON')==='JSON'" class="payload-json">{{ JSON.stringify(inputView(step).jsonBody ?? step.params ?? {},null,2) }}</pre><table v-else-if="inputView(step).format==='FORM'" class="detail-table"><thead><tr><th>字段</th><th>值</th></tr></thead><tbody><tr v-for="(field,fi) in inputView(step).formFields||[]" :key="fi"><td>{{ field.name || field.key }}</td><td>{{ field.value }}</td></tr></tbody></table><div v-else><p v-for="(file,fi) in inputView(step).files||[]" :key="fi"><strong>{{ file.fieldName || file.name }}</strong>　<span class="file-name">{{ file.fileName }}</span>　{{ file.contentType }}　{{ file.size }}</p></div></div><div v-else-if="tab==='pre'" class="interface-subpanel"><p>{{ step.precondition || '无前置处理' }}</p></div><div v-else class="interface-subpanel"><p>{{ step.postcondition || parse(detailCase.assertions).join('\n') || '无后置校验' }}</p></div></div></article></div></div></section>
    </div>
  </section>
</template>

<style scoped>
.library{max-width:1240px;margin:auto}.filters{display:grid;grid-template-columns:minmax(220px,340px) minmax(145px,180px) minmax(180px,260px) auto auto;align-items:end;justify-content:start;gap:12px;margin:0 0 15px;padding:14px 16px;background:#fff;border:1px solid #e2e7ee;border-radius:9px}.filters label{display:grid;gap:6px;min-width:0;color:#596679;font-size:12px}.filters .input,.filters .select{width:100%;min-width:0}.search{width:100%}.create{margin-left:auto}.table-card{background:#fff;border:1px solid #e5e9f0;border-radius:12px;overflow:hidden}.table{width:100%;border-collapse:collapse;table-layout:fixed}.table th,.table td{padding:14px 16px;text-align:left;vertical-align:middle;border-bottom:1px solid #edf0f4}.table th{background:#fafbfc;color:#7d899d;font-size:12px}.table th:nth-child(1){width:27%}.table th:nth-child(2){width:28%}.table th:nth-child(3){width:15%}.table th:nth-child(4){width:16%}.table th:nth-child(5){width:230px}.table tr:last-child td{border-bottom:0}.case-link,.task-link,.link{border:0;background:none;color:#3c65b7;cursor:pointer;padding:0}.case-link{font-weight:600}.source{display:inline-block;color:#5e6b7e;background:#f2f4f7;border-radius:20px;padding:4px 8px;font-size:10px}.task-link{margin-left:9px;font-size:11px}.task-name{margin-left:9px;color:#78869a;font-size:11px}.actions{white-space:nowrap}.link{margin-right:12px;font-size:12px}.danger{color:#c24955}.empty-cell{text-align:center!important;padding:50px!important;color:#8d98a9}.details{background:#fbfcfe;padding:22px 28px!important}.details h4{margin:0 0 18px;color:#2f3e55;font-size:15px}.params{margin-bottom:18px}.params>b{font-size:12px;color:#55637a}.param-grid{display:grid;grid-template-columns:1fr 2fr 1fr;margin-top:9px;border:1px solid #e8ecf1;border-radius:8px;overflow:hidden}.param-grid>*{padding:8px 11px;border-bottom:1px solid #edf0f4;font-size:11px}.param-grid>*:nth-last-child(-n+3){border-bottom:0}.param-head{background:#f5f7fa;color:#8490a2}.param-grid code{color:#3762b0}.step-list{display:grid;gap:8px}.step-row{background:#fff;border:1px solid #e8ecf1;border-radius:8px;overflow:hidden}.step-head{display:grid;grid-template-columns:36px 55px minmax(0,1fr) 20px;gap:10px;align-items:center;width:100%;border:0;background:#fff;text-align:left;padding:12px;cursor:pointer}.step-no{color:#929dae;font-size:11px}.method{font-size:10px;font-weight:700;color:#3866bb}.method.get{color:#18846a}.method.delete{color:#bc4c56}.step-url{font:11px ui-monospace,Menlo,monospace;color:#45546b;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.step-body{border-top:1px solid #edf0f4}.tabs{display:flex;padding:6px 12px 0;border-bottom:1px solid #edf0f4;gap:10px}.tabs button{border:0;background:none;padding:8px 5px;color:#7d899c;font-size:11px;cursor:pointer}.tabs button.on{color:#3966b7;border-bottom:2px solid #5279c7}.tab-content{padding:12px;font-size:12px;color:#57657b}.format{color:#426bb7;font-size:10px}.tab-content pre{padding:10px;background:#f6f8fa;border-radius:6px;white-space:pre-wrap;word-break:break-all;font:11px ui-monospace,Menlo,monospace}.field-table{display:grid;gap:6px}.field-table div{display:grid;grid-template-columns:1fr 2fr;padding:8px;background:#f7f8fa;border-radius:5px}.field-table code{color:#3966b7}.text{white-space:pre-wrap;line-height:1.6}.count{padding:12px 2px;color:#8490a2;font-size:12px}.editor-page{width:100%}.editor{background:#fff;width:100%;max-height:none;overflow:visible;border:1px solid #e2e7ee;border-radius:9px;padding:20px;display:grid;gap:13px;box-shadow:none}.back-link{border:0;background:none;color:#526176;text-align:left;padding:0 0 2px;cursor:pointer}.editor header{display:flex;justify-content:space-between;align-items:flex-start}.editor h3{margin:0;font-size:18px}.editor header p{margin:5px 0 0;color:#8490a2;font-size:12px}.editor label{display:grid;gap:6px;color:#59667b;font-size:12px}.case-name-field{max-width:560px}.edit-section{border:1px solid #e3e8ef;border-radius:8px;padding:16px}.section-heading{display:flex;align-items:center;justify-content:space-between;margin-bottom:12px}.section-heading h4{margin:0;color:#354256;font-size:14px}.section-heading small{color:#7b8797;font-size:11px}.param-editor{border:1px solid #e8ecf1;border-radius:7px;overflow:hidden}.param-editor-head,.param-editor-row{display:grid;grid-template-columns:1fr 2fr 1.2fr 28px;gap:8px;align-items:center;padding:9px 10px}.param-editor-head{background:#f7f9fb;color:#748196;font-size:11px}.param-editor-row{border-top:1px solid #edf0f4}.param-editor-row .input{min-width:0}.edit-steps{display:grid;gap:9px}.edit-step{border:1px solid #e2e7ee;border-radius:8px;overflow:hidden;background:#fff}.edit-step-head{display:grid;grid-template-columns:22px 32px 88px minmax(0,1fr) 20px 25px;gap:9px;align-items:center;width:100%;padding:11px 12px;border:0;background:#fff;text-align:left}.step-drag{cursor:grab;color:#8b96a6}.step-number{color:#245edb;font-weight:700;font-size:12px}.method-select{width:88px}.endpoint-input{min-width:0}.chevron{color:#7c8796}.remove-step,.remove{border:0;background:transparent;color:#a0a9b5;font-size:20px;cursor:pointer}.edit-step-body{padding:0 14px 14px;border-top:1px solid #edf0f4}.edit-tabs{display:flex;gap:18px;border-bottom:1px solid #e8edf3}.edit-tabs button{padding:11px 2px 9px;border:0;border-bottom:2px solid transparent;background:transparent;color:#718095;font-size:12px}.edit-tabs button.active{border-bottom-color:#245edb;color:#245edb;font-weight:600}.edit-tab-panel{padding-top:12px}.edit-tab-panel>label{display:grid;gap:6px;color:#536174;font-size:12px}.input-format{display:flex;align-items:center;justify-content:space-between;margin-bottom:8px;color:#536174;font-size:12px}.input-format .select{width:190px}.edit-tab-panel>.textarea{width:100%;font:12px/1.6 ui-monospace,SFMono-Regular,Menlo,monospace}.field-editor{display:grid;gap:8px}.field-editor>div{display:grid;grid-template-columns:1fr 1.5fr 28px;gap:8px;align-items:center}.file-editor>div{grid-template-columns:1fr 1fr 1fr 100px 28px}.field-editor .link-btn{justify-self:start}.empty-small{text-align:center;padding:15px;color:#98a3b2;font-size:12px}.code{font:11px ui-monospace,Menlo,monospace}.footer{display:flex;justify-content:flex-end;gap:9px}.alert{background:#fff1f1;color:#b43742;border-radius:8px;padding:10px;margin:0 0 12px}@media(max-width:800px){.filters{grid-template-columns:minmax(0,1fr) minmax(130px,.65fr) minmax(0,1fr) auto auto;gap:9px;padding:12px}.table-card{overflow:auto}.table{min-width:880px}.param-editor-head{display:none}.param-editor-row{grid-template-columns:1fr}.param-editor-row .remove{justify-self:end}.edit-step-head{grid-template-columns:18px 27px 72px minmax(0,1fr) 16px 20px;gap:5px}.method-select{width:72px}.file-editor>div{grid-template-columns:1fr}}@media(max-width:600px){.filters{grid-template-columns:minmax(0,1fr) minmax(0,1fr)}.filters label:nth-child(3){grid-column:1/-1}.filters button{justify-self:start}}
</style>

<style scoped>
.list-head{display:flex;justify-content:space-between;align-items:center;padding:11px 15px;border:1px solid #e2e7ee;border-bottom:0;border-radius:9px 9px 0 0;background:#fff;color:#526073;font-size:13px}
.table-card{border-radius:0 0 9px 9px}.table th:nth-child(1){width:32%}.table th:nth-child(2){width:30%}.table th:nth-child(3){width:13%}.table th:nth-child(4){width:25%}
.case-meta{margin-top:3px;color:#778394;font-size:12px}.link-btn{border:0;background:transparent;color:#245edb;cursor:pointer;padding:0 3px}
.case-detail-page{display:grid;gap:14px}.case-info-card{border:1px solid #e1e6ed;border-radius:9px;background:#fff;overflow:hidden}.case-info-title{padding:13px 16px;border-bottom:1px solid #edf0f4;color:#354256;font-weight:600}.case-info-body{padding:16px}.case-info-body h1{margin:0;color:#202b3a;font-size:20px}.case-basic{display:flex;align-items:center;gap:20px;flex-wrap:wrap;margin-top:12px;color:#697689;font-size:12px}.case-basic strong{color:#354256}.public-table,.detail-table{width:100%;border-collapse:collapse}.public-table th,.public-table td,.detail-table th,.detail-table td{padding:10px 12px;text-align:left;border-bottom:1px solid #edf0f4;font-size:12px}.public-table th,.detail-table th{background:#fafbfd;color:#748093;font-weight:500}.public-table code{color:#245edb}.interface-list{border:1px solid #e2e7ee;border-radius:8px;overflow:hidden}.interface-item+.interface-item{border-top:1px solid #edf0f4}.interface-summary{display:grid;grid-template-columns:42px 70px minmax(0,1fr) 22px;gap:10px;align-items:center;width:100%;padding:13px 14px;border:0;background:#fff;text-align:left}.interface-summary:hover{background:#fafbfd}.interface-number{color:#245edb;font-weight:700}.http-method{width:max-content;padding:3px 6px;border-radius:4px;background:#e8f6ed;color:#27794e;font-size:10px;font-weight:700}.http-method.post{background:#fff2df;color:#9b5b0d}.interface-url{overflow-wrap:anywhere;color:#354256}.interface-toggle{color:#7c8796}.interface-detail{padding:0 16px 16px 66px}.interface-tabs{display:flex;gap:17px;border-bottom:1px solid #e8edf3}.interface-tabs button{padding:11px 2px 9px;border:0;border-bottom:2px solid transparent;background:transparent;color:#718095;font-size:13px}.interface-tabs button.active{border-bottom-color:#245edb;color:#245edb;font-weight:600}.interface-subpanel{padding:12px 0 2px}.interface-subpanel p{margin:4px 0;color:#58677b;font-size:13px;line-height:1.65}.input-kind{color:#8a94a2;font-size:11px}.payload-json{margin:0;padding:13px 15px;overflow:auto;border:1px solid #e5eaf0;border-radius:7px;background:#f8fafc;color:#354256;font:12px/1.65 ui-monospace,SFMono-Regular,Menlo,monospace;white-space:pre-wrap}.file-name{color:#245edb;font-weight:600}
@media(max-width:850px){.interface-detail{padding-left:16px}.case-basic{align-items:flex-start;flex-direction:column;gap:8px}}
</style>
