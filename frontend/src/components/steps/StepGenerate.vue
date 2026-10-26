<script setup>
import { ref, computed, onMounted } from 'vue'
import { api } from '../../api'
const props = defineProps({ task: { type: Object, required: true } })
const emit = defineEmits(['changed'])
const result = ref({ scenes: [] })
const loading = ref(true)
const generating = ref(false)
const saving = ref(false)
const saved = ref(false)
const err = ref('')
const userPrompt = ref(props.task.caseGenerationPrompt || '')
const promptSaving = ref(false)
const promptSaved = ref(Boolean(props.task.caseGenerationPrompt))
const openDraft = ref('')
const tabByStep = ref({})
const selected = ref([])
const draftCases = computed(() => {
  const scenes = Array.isArray(result.value?.scenes) ? result.value.scenes : []
  return scenes.flatMap(scene => (Array.isArray(scene?.cases) ? scene.cases : []).map(testCase => ({
    ...testCase,
    sceneName: scene.sceneName,
    publicParams: Array.isArray(scene.publicParams) ? scene.publicParams : []
  })))
})

function normalizeResult(data) {
  if (!data || typeof data !== 'object' || !Array.isArray(data.scenes)) return null
  return {
    ...data,
    scenes: data.scenes
      .filter(scene => scene && typeof scene === 'object')
      .map(scene => ({
        ...scene,
        cases: Array.isArray(scene.cases) ? scene.cases : [],
        publicParams: Array.isArray(scene.publicParams) ? scene.publicParams : []
      }))
  }
}

async function load() {
  loading.value = true
  try {
    const data = await api.getGeneratedCases(props.task.id)
    const normalized = normalizeResult(data)
    result.value = normalized || { scenes: [] }
    selected.value = draftCases.value.map(c => c.draftId).filter(Boolean)
    saved.value = props.task.status === 'COMPLETED'
    err.value = data && !normalized ? '用例草稿数据格式异常，请重新生成用例。' : ''
  }
  catch (e) { err.value = e.message }
  finally { loading.value = false }
}
async function generate() {
  generating.value = true; err.value = ''
  try {
    await persistPrompt()
    const data = await api.generateCases(props.task.id, userPrompt.value)
    const normalized = normalizeResult(data)
    result.value = normalized || { scenes: [] }
    if (!normalized) throw new Error('AI 返回的用例数据格式异常，请重试生成。')
    selected.value = draftCases.value.map(c => c.draftId).filter(Boolean)
  }
  catch (e) { err.value = e.message }
  finally { generating.value = false }
}
async function persistPrompt() {
  promptSaving.value = true
  try {
    await api.saveCaseGenerationPrompt(props.task.id, userPrompt.value)
    promptSaved.value = true
  } catch (e) {
    promptSaved.value = false
    throw e
  } finally {
    promptSaving.value = false
  }
}
function onPromptInput() {
  promptSaved.value = false
}
function onPromptBlur() {
  if (!promptSaved.value) persistPrompt().catch(e => { err.value = e.message })
}
async function save() {
  saving.value = true; err.value = ''
  try { await api.saveGeneratedCases({ taskId: Number(props.task.id), draftIds: selected.value }); saved.value = true; emit('changed') }
  catch (e) { err.value = e.message }
  finally { saving.value = false }
}
function toggle(id) { selected.value = selected.value.includes(id) ? selected.value.filter(x => x !== id) : [...selected.value, id] }
function inputText(value) { if (value == null) return '—'; return typeof value === 'string' ? value : JSON.stringify(value, null, 2) }
function activeTab(step,index) { return tabByStep.value[step.draftId || `${step.apiId}-${index}`] || 'input' }
function setTab(step,index,value) { tabByStep.value[step.draftId || `${step.apiId}-${index}`] = value }
onMounted(load)
</script>

<template>
  <div class="generate-step">
    <p v-if="err" class="alert">{{ err }}</p>
    <section class="panel">
      <div class="panel-head"><div><h3>AI 生成的自动化用例</h3><p class="hint">模型会结合场景顺序、录制请求和响应补齐入参、前置处理与后置校验。</p></div><span v-if="saved" class="saved-badge">✓ 已保存</span></div>
      <div v-if="!saved" class="prompt-box">
        <label for="case-generation-prompt">用例生成提示词（选填）</label>
        <textarea id="case-generation-prompt" v-model="userPrompt" class="textarea" rows="3" :disabled="generating" placeholder="补充用例生成要求，例如：重点覆盖重复提交、无权限访问和必填字段缺失；校验创建成功后的数据可查询。" @input="onPromptInput" @blur="onPromptBlur" />
        <div class="prompt-foot"><span>补充场景侧重点、边界条件或校验要求；不会改变已编排的接口顺序。</span><span>{{ promptSaving ? '保存中…' : promptSaved ? '✓ 已保存到任务' : '修改后自动保存' }}</span></div>
        <div class="generate-action"><span>{{ draftCases.length ? '修改提示词后可重新生成用例' : '准备好提示词后开始生成' }}</span><button class="btn btn-primary" :disabled="generating || loading" @click="generate">{{ generating ? 'AI 正在生成…' : draftCases.length ? '重新生成用例' : '生成用例' }}</button></div>
      </div>
      <div v-if="loading" class="loading">读取生成结果…</div>
      <div v-else-if="!draftCases.length" class="empty"><p>尚未生成用例</p></div>
      <div v-else class="scene-groups"><section v-for="scene in result.scenes" :key="scene.sceneId" class="scene-group">
        <header class="scene-head"><div><span class="scene-kicker">大场景</span><h4>{{ scene.sceneName }}</h4></div><span class="tag tag-brand">{{ (scene.cases || []).length }} 个用例</span></header>
        <div v-if="scene.publicParams?.length" class="public-params"><b>公共参数</b><div class="param-table"><div class="param-head"><span>参数名</span><span>来源 / 说明</span><span>值</span></div><div v-for="(param,index) in scene.publicParams" :key="index" class="param-row"><code>{{ param.name || param.key }}</code><span>{{ param.source || param.description || '—' }}</span><span>{{ param.value || '动态提取' }}</span></div></div></div>
        <article v-for="draft in scene.cases" :key="draft.draftId" class="case-card">
          <div class="case-line"><label v-if="!saved" class="check"><input type="checkbox" :checked="selected.includes(draft.draftId)" @change="toggle(draft.draftId)" /></label><button class="case-title" @click="openDraft = openDraft === draft.draftId ? '' : draft.draftId">{{ draft.name }} <span class="chevron">{{ openDraft === draft.draftId ? '⌃' : '⌄' }}</span></button><span class="steps-count">{{ draft.steps?.length || 0 }} 个接口</span></div>
          <div v-if="openDraft === draft.draftId" class="case-detail"><div v-for="(step,index) in draft.steps" :key="index" class="step-detail"><div class="step-title"><span class="step-number">{{ String(index+1).padStart(2,'0') }}</span><b>{{ step.method }} {{ step.url }}</b></div><div class="tabs"><button :class="{active:activeTab(step,index)==='input'}" @click="setTab(step,index,'input')">入参</button><button :class="{active:activeTab(step,index)==='pre'}" @click="setTab(step,index,'pre')">前置</button><button :class="{active:activeTab(step,index)==='post'}" @click="setTab(step,index,'post')">后置</button></div><div v-if="activeTab(step,index)==='input'" class="tab-content"><span class="format">{{ step.input?.format || 'JSON' }}</span><pre v-if="(step.input?.format || 'JSON')==='JSON'">{{ inputText(step.input?.jsonBody) }}</pre><div v-else-if="step.input?.format==='FORM'" class="kv-list"><div v-for="(field,fi) in step.input.formFields" :key="fi"><code>{{ field.name || field.key }}</code><span>{{ field.value }}</span></div></div><div v-else class="kv-list"><div v-for="(file,fi) in step.input?.files || []" :key="fi"><code>{{ file.fieldName || file.name }}</code><span>{{ file.fileName }} · {{ file.contentType }} · {{ file.size || '—' }}</span></div></div></div><div v-else-if="activeTab(step,index)==='pre'" class="tab-content text-content">{{ step.precondition || '无前置处理' }}</div><div v-else class="tab-content text-content">{{ step.postcondition || '无后置校验' }}</div></div></div>
        </article>
      </section></div>
      <div v-if="draftCases.length && !saved" class="save-footer"><span class="hint">已选择 {{ selected.length }} / {{ draftCases.length }} 个用例</span><button class="btn btn-primary" :disabled="saving || !selected.length" @click="save">{{ saving ? '保存中…' : '保存用例' }}</button></div>
      <div v-if="saved" class="saved-footer">已保存到自动化用例库，当前任务流程结束。</div>
    </section>
  </div>
</template>

<style scoped>
.generate-step{display:grid;gap:16px}.panel{background:#fff;border:1px solid #e5e9f0;border-radius:12px;overflow:hidden}.panel-head{padding:17px 20px;display:flex;align-items:center;justify-content:space-between;gap:14px;border-bottom:1px solid #edf0f4}.panel-head h3{margin:0;font-size:15px;color:#293850}.hint{font-size:12px;color:#8793a5;margin:5px 0 0}.empty{padding:54px;text-align:center;color:#8793a5}.empty p{margin:0 0 14px}.scene-group{margin:18px 20px;border:1px solid #e7ebf1;border-radius:11px;overflow:hidden}.scene-head{display:flex;align-items:center;justify-content:space-between;padding:16px 18px;background:#f8fafd;border-bottom:1px solid #e9edf2}.scene-head h4{margin:4px 0 0;font-size:15px;color:#2d3d55}.scene-kicker{font-size:10px;color:#8592a6}.public-params{padding:14px 18px;border-bottom:1px solid #edf0f4}.public-params>b{font-size:12px;color:#56647a}.param-table{margin-top:10px;border:1px solid #e9edf2;border-radius:8px;overflow:hidden}.param-head,.param-row{display:grid;grid-template-columns:1fr 2fr 1fr;gap:12px;padding:9px 12px;font-size:11px}.param-head{background:#f8f9fb;color:#8793a5}.param-row{border-top:1px solid #edf0f4;color:#58667b}.param-row code{color:#365da4}.case-card{border-bottom:1px solid #edf0f4}.case-card:last-child{border:0}.case-line{display:flex;align-items:center;gap:12px;padding:13px 18px}.check{display:flex}.case-title{flex:1;display:flex;justify-content:space-between;align-items:center;border:0;background:none;text-align:left;cursor:pointer;color:#34445d;font-size:13px;font-weight:600;padding:4px 0}.chevron{color:#8a96a8}.steps-count{font-size:11px;color:#8793a5}.case-detail{padding:0 18px 14px}.step-detail{border:1px solid #e9edf2;border-radius:9px;margin-top:10px;overflow:hidden}.step-title{display:flex;gap:10px;align-items:center;padding:11px 13px;background:#fafbfd;color:#44536a;font-size:12px}.step-number{color:#8793a5}.tabs{display:flex;gap:4px;padding:8px 12px 0;border-bottom:1px solid #edf0f4}.tabs button{border:0;background:transparent;padding:8px 12px;color:#78859a;font-size:11px;cursor:pointer}.tabs button.active{color:#3f65b5;border-bottom:2px solid #5478c7}.tab-content{padding:12px;font-size:12px;color:#53627a}.tab-content pre{margin:8px 0 0;padding:11px;background:#f7f8fa;border-radius:7px;white-space:pre-wrap;word-break:break-all;font:11px ui-monospace,Menlo,monospace}.format{display:inline-block;color:#3e69bd;background:#eef3ff;border-radius:4px;padding:3px 7px;font-size:10px}.kv-list{display:grid;gap:6px}.kv-list div{display:grid;grid-template-columns:1fr 2fr;gap:12px;padding:8px;background:#f8f9fb;border-radius:5px}.kv-list code{color:#3c65b2}.text-content{line-height:1.65;white-space:pre-wrap}.save-footer{display:flex;justify-content:space-between;align-items:center;padding:15px 20px;border-top:1px solid #edf0f4}.saved-badge,.saved-footer{color:#388066;background:#f0f8f4}.saved-badge{padding:7px 11px;border-radius:20px;font-size:12px}.saved-footer{padding:13px 20px;font-size:12px}.alert{background:#fff1f1;color:#b43742;padding:10px;border-radius:8px}
.prompt-box{padding:16px 20px;border-bottom:1px solid #edf0f4;display:grid;gap:8px}.prompt-box label{font-size:12px;font-weight:600;color:#44536a}.prompt-box .textarea{width:100%;box-sizing:border-box;resize:vertical}.prompt-foot{display:flex;justify-content:space-between;gap:12px;color:#8793a5;font-size:11px}.prompt-foot span:last-child{white-space:nowrap;color:#56816d}.generate-action{display:flex;justify-content:space-between;align-items:center;gap:12px;margin-top:4px}.generate-action>span{color:#8793a5;font-size:11px}
</style>
