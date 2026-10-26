<script setup>
import { ref, computed, onMounted } from 'vue'
import draggable from 'vuedraggable'
import { api } from '../../api'
const props = defineProps({ task: { type: Object, required: true } })
const emit = defineEmits(['advanced', 'changed'])
const apis = ref([])
const scenes = ref([])
const rules = ref([])
const sceneApis = ref({})
const sequence = ref([])
const selectedApiIds = ref([])
const name = ref('')
const search = ref('')
const loading = ref(true)
const busy = ref(false)
const err = ref('')
const saved = ref(false)
const recommended = ref([])
const recommending = ref(false)
const recommendationReady = ref(false)
const showSceneDialog = ref(false)
const historyOpen = ref(false)
const editable = computed(() => props.task.stage === 'PARSE' && props.task.status !== 'COMPLETED')
const editingHistory = ref(null)
const historySource = ref('MANUAL_HISTORY')
const eligible = computed(() => apis.value.filter(a => !sequence.value.some(s => s.id === a.id)))
const history = computed(() => rules.value
  .filter(r => r.taskId !== props.task.id && r.enabled !== false && (r.name || '').toLowerCase().includes(search.value.trim().toLowerCase()))
  .sort((a, b) => {
    const scoreA = recommended.value.find(item => item.id === a.id)?.similarityScore ?? -1
    const scoreB = recommended.value.find(item => item.id === b.id)?.similarityScore ?? -1
    return scoreB - scoreA
  }))

async function load() {
  loading.value = true
  try {
    const [capturedApis, savedScenes, availableRules] = await Promise.all([api.getApis(props.task.id), api.getTaskRules(props.task.id), api.listRules()])
    apis.value = capturedApis.filter(item => item.checked !== false)
    scenes.value = savedScenes
    rules.value = availableRules
    sceneApis.value = Object.fromEntries(scenes.value.map(scene => [scene.id, parseSteps(scene).map((step, index) => ({ ...step, key: `${scene.id}-${index}` }))]))
    err.value = ''
  }
  catch (e) { err.value = e.message }
  finally { loading.value = false }
}
function parseSteps(rule) { try { return JSON.parse(rule.steps || '[]') } catch { return [] } }
function recommendationScore(rule) { return recommended.value.find(item => item.id === rule.id)?.similarityScore }
async function persistSceneOrder(scene) {
  const steps = (sceneApis.value[scene.id] || []).map((step, index) => ({ ...step, seq: index + 1 }))
  try { await api.updateRule(scene.id, { name: scene.name, steps: JSON.stringify(steps) }); saved.value = true }
  catch (e) { err.value = `保存「${scene.name}」的接口顺序失败：${e.message}`; await load() }
}
async function saveScene() {
  err.value = ''
  if (!name.value.trim()) { err.value = '请输入场景名称'; return }
  if (!sequence.value.length) { err.value = '请将接口拖入场景并排好顺序'; return }
  busy.value = true
  try {
    if (editingHistory.value) {
      const steps = sequence.value.map((item, index) => ({ seq: index + 1, apiId: item.apiId || null, method: item.method, url: item.url || '', name: item.name || item.url || '' }))
      await api.rulesFromHistory({ taskId: Number(props.task.id), historyRuleId: editingHistory.value.id, mode: 'SAVE_AS_NEW', source: historySource.value, name: name.value.trim(), steps: JSON.stringify(steps) })
    } else {
      await api.rulesFromRecord({ taskId: Number(props.task.id), name: name.value.trim(), apiIds: sequence.value.map(a => a.id) })
    }
    name.value = ''; sequence.value = []; editingHistory.value = null; saved.value = true; await load()
  }
  catch (e) { err.value = e.message }
  finally { busy.value = false }
}
function createSceneFromSelection() {
  err.value = ''
  if (!selectedApiIds.value.length) { err.value = '请先勾选要加入场景的接口'; return }
  name.value = ''
  showSceneDialog.value = true
}
async function saveSelectedScene() {
  err.value = ''
  if (!name.value.trim()) { err.value = '请输入场景名称'; return }
  busy.value = true
  try {
    await api.rulesFromRecord({ taskId: Number(props.task.id), name: name.value.trim(), apiIds: [...selectedApiIds.value] })
    selectedApiIds.value = []; showSceneDialog.value = false; saved.value = true; await load()
  } catch (e) { err.value = e.message }
  finally { busy.value = false }
}
function editHistory(rule) {
  editingHistory.value = rule
  historySource.value = recommended.value.some(item => item.id === rule.id) ? 'VECTOR_RECOMMENDED_HISTORY' : 'MANUAL_HISTORY'
  name.value = rule.name
  sequence.value = parseSteps(rule).map((step, index) => ({ id: step.apiId || `history-${rule.id}-${index}`, apiId: step.apiId || null, method: step.method || 'API', url: step.url || '', name: step.name || step.url || `接口 ${index + 1}` }))
  document.querySelector('.builder')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
async function updateHistory() {
  if (!editingHistory.value?.editable) return
  if (!window.confirm(`确认更新历史规则「${editingHistory.value.name}」并保存本任务的场景副本吗？`)) return
  busy.value = true; err.value = ''
  try {
    const steps = sequence.value.map((item, index) => ({ seq: index + 1, apiId: item.apiId || null, method: item.method, url: item.url || '', name: item.name || item.url || '' }))
    await api.rulesFromHistory({ taskId: Number(props.task.id), historyRuleId: editingHistory.value.id, mode: 'UPDATE_HISTORY', source: historySource.value, name: name.value.trim(), steps: JSON.stringify(steps) })
    editingHistory.value = null; name.value = ''; sequence.value = []; saved.value = true; await load()
  } catch (e) { err.value = e.message }
  finally { busy.value = false }
}
async function addHistory(rule) {
  busy.value = true; err.value = ''
  try { await api.rulesFromHistory({ taskId: Number(props.task.id), historyRuleId: rule.id, mode: 'USE_AS_IS', name: rule.name, source: recommended.value.some(item => item.id === rule.id) ? 'VECTOR_RECOMMENDED_HISTORY' : 'MANUAL_HISTORY' }); saved.value = true; await load() }
  catch (e) { err.value = e.message }
  finally { busy.value = false }
}
async function getRecommendations() {
  recommending.value = true; err.value = ''; historyOpen.value = true; recommendationReady.value = false; recommended.value = []
  try { recommended.value = await api.recommendRules({ taskId: Number(props.task.id), apiIds: apis.value.map(a => a.id), sceneDescription: props.task.recordingGoal }); recommendationReady.value = true }
  catch (e) { err.value = e.message }
  finally { recommending.value = false }
}
async function next() {
  err.value = ''
  if (!scenes.value.length) { err.value = '请先保存至少一个场景'; return }
  busy.value = true
  try { const task = await api.completeOrchestration(props.task.id); emit('advanced', task) }
  catch (e) { err.value = e.message }
  finally { busy.value = false }
}
onMounted(load)
</script>

<template>
  <div class="arrange-step">
    <p v-if="err" class="alert">{{ err }}</p>
    <div v-if="saved" class="saved-note"><span class="saved-mark">✓</span> 编排规则已实时保存</div>
    <section v-if="editable" class="panel">
      <div class="panel-head"><div><h3>录制接口</h3><p class="hint">将接口拖入下方场景并调整执行顺序；一个任务可以保存多个大场景。</p></div><span class="tag tag-brand">{{ apis.length }} 个接口</span></div>
      <div v-if="loading" class="loading">读取接口…</div>
      <div v-else-if="!apis.length" class="empty">当前任务没有录制到接口。</div>
      <div v-else class="api-pool"><label v-for="apiItem in apis" :key="apiItem.id" class="pool-row"><input v-model="selectedApiIds" type="checkbox" :value="apiItem.id" /><span class="method" :class="(apiItem.method || '').toLowerCase()">{{ apiItem.method }}</span><span class="url">{{ apiItem.url }}</span><span class="response">{{ apiItem.httpStatus || '—' }}</span></label></div>
      <div v-if="apis.length" class="create-scene-row"><span class="hint">已勾选 {{ selectedApiIds.length }} 个接口，可在不同场景重复使用</span><button class="btn" @click="createSceneFromSelection">＋ 用选中接口创建场景</button></div>
    </section>

    <section v-if="editable" class="panel history-panel"><div class="panel-head"><div><h3>复用编排规则</h3><p class="hint">按场景说明、接口及调用顺序计算向量相似度，超过阈值的历史规则会优先显示。</p></div><div class="history-actions"><button class="btn btn-sm" @click="historyOpen=!historyOpen">{{ historyOpen ? '收起规则库' : '从规则库选择⌄' }}</button><button class="btn btn-sm" :disabled="recommending || loading || !apis.length" @click="getRecommendations">{{ recommending ? '向量检索中…' : '向量推荐匹配规则' }}</button></div></div><div v-if="historyOpen" class="history-picker"><div v-if="recommending" class="recommend-progress"><span class="spinner"></span><div><strong>正在检索向量并计算余弦相似度</strong><small>已整理 {{ apis.length }} 个录制接口，正在与历史规则的接口顺序和场景说明比较。</small></div></div><div v-else-if="recommendationReady && recommended.length" class="recommend-summary">向量检索到 {{ recommended.length }} 条超过相似度阈值的规则，已置顶显示。</div><input v-model="search" class="input history-search" placeholder="输入场景名称模糊搜索" /><div v-if="recommendationReady && !recommended.length" class="empty">没有历史规则超过相似度阈值，可继续手动选择。</div><div v-for="rule in history" :key="rule.id" class="history-row"><div><strong>{{ rule.name }}</strong><span v-if="recommended.some(item => item.id===rule.id)" class="ai-tag">向量匹配 · {{ (recommendationScore(rule) * 100).toFixed(1) }}%</span><small>{{ parseSteps(rule).map(s => `${s.method} ${s.url || s.name}`).join(' → ') }}</small></div><div class="row-actions"><button class="btn btn-sm" :disabled="busy" @click="editHistory(rule)">拖拽修改</button><button class="btn btn-sm" :disabled="busy" @click="addHistory(rule)">直接添加</button></div></div><div v-if="!history.length && !(recommendationReady && !recommended.length)" class="empty">暂无可复用的历史规则。</div></div></section>

    <section v-if="editable && editingHistory" class="panel builder">
      <div class="panel-head"><div><h3>新建场景编排</h3><p class="hint">场景名称同时作为编排规则名称。这里只描述接口及顺序。</p></div><span class="tag">接口顺序</span></div>
      <div class="builder-body">
        <label class="scene-name">场景名称<input v-model="name" class="input" placeholder="例如：创建用户并查询详情" /></label>
        <draggable v-model="sequence" item-key="id" class="drop-zone" :animation="160" ghost-class="ghost" :empty-insert-threshold="24">
          <template #item="{ element, index }"><div class="sequence-row"><span class="drag-handle">⠿</span><span class="seq">{{ String(index + 1).padStart(2,'0') }}</span><span class="method" :class="(element.method || '').toLowerCase()">{{ element.method }}</span><span class="url">{{ element.url }}</span><button class="remove" @click="sequence.splice(index,1)">×</button></div></template>
          <template #footer><div v-if="!sequence.length" class="drop-hint">从上方点击 ＋ 将接口加入场景，再拖动排序</div></template>
        </draggable>
        <div class="save-row"><span class="hint">{{ sequence.length }} 个接口 · {{ editingHistory ? '历史规则修改后可另存，或更新你有权限修改的历史规则' : '保存为一个独立场景' }}</span><div class="save-actions"><button v-if="editingHistory?.editable" class="btn" :disabled="busy" @click="updateHistory">更新历史规则</button><button class="btn btn-primary" :disabled="busy" @click="saveScene">{{ editingHistory ? '另存为新规则' : '保存场景' }}</button></div></div>
      </div>
    </section>

    <section class="panel saved-scenes">
      <div class="panel-head"><h3>已保存场景</h3><span class="tag tag-ok">{{ scenes.length }} 个</span></div>
      <div v-if="!scenes.length" class="empty">保存后的场景会显示在这里。</div>
      <article v-for="scene in scenes" :key="scene.id" class="scene-card"><div class="scene-title"><strong>{{ scene.name }}</strong><span class="source-tag">{{ ({RECORD:'人工新建',MANUAL:'人工新建',MANUAL_HISTORY:'人工选择历史',AI_RECOMMENDED_HISTORY:'AI 推荐历史',VECTOR_RECOMMENDED_HISTORY:'向量推荐历史',HISTORY:'历史规则',UPLOAD:'AI 分析上传'})[scene.source] || scene.source }}</span><span class="scene-state">✓ 已实时保存</span></div><draggable v-model="sceneApis[scene.id]" item-key="key" handle=".scene-drag" :animation="150" class="scene-steps" @end="persistSceneOrder(scene)"><template #item="{element,index}"><div class="scene-step"><span class="scene-drag">⠿</span><i>{{ index + 1 }}</i><span>{{ element.method }} {{ element.url || element.name }}</span></div></template></draggable><div class="scene-card-foot">{{ sceneApis[scene.id]?.length || 0 }} 个接口 · 调整顺序后自动保存</div></article>
    </section>

    <div v-if="editable" class="next-row"><span class="hint">{{ scenes.length }} / {{ scenes.length }} 个场景已实时保存</span><button class="btn btn-primary" :disabled="busy || !scenes.length" @click="next">下一步：AI 生成用例 →</button></div>
    <div v-if="showSceneDialog" class="dialog-backdrop" @click.self="showSceneDialog=false"><section class="scene-dialog"><h3>新建场景</h3><p>为选中的接口组命名，创建后会显示在已保存场景中。</p><label>场景名称<input v-model="name" class="input" placeholder="例如：创建用户并查询订单" @keyup.enter="saveSelectedScene" /></label><div><button class="btn" @click="showSceneDialog=false">取消</button><button class="btn btn-primary" :disabled="busy" @click="saveSelectedScene">{{ busy ? '保存中…' : '创建场景' }}</button></div></section></div>
  </div>
</template>

<style scoped>
.arrange-step{display:grid;gap:14px}.panel{background:#fff;border:1px solid #e2e7ee;border-radius:9px;overflow:hidden}.panel-head{padding:14px 16px;display:flex;align-items:center;justify-content:space-between;gap:16px;border-bottom:1px solid #edf0f4}.panel-head h3{margin:0;font-size:14px;color:#293850}.panel-head .hint{margin:5px 0 0}.hint{color:#8692a5;font-size:12px}.api-pool{padding:2px 16px}.pool-row{display:grid;grid-template-columns:22px 62px minmax(0,1fr) 50px;gap:10px;align-items:center;padding:12px 0;border-bottom:1px solid #f0f2f5;cursor:pointer}.pool-row:last-child{border-bottom:0}.pool-row input{width:15px;height:15px}.response{font-size:12px;color:#8a95a6}.method{font-size:11px;font-weight:700;color:#3565bd}.method.get{color:#178364}.method.post{color:#3565bd}.method.delete{color:#c14e58}.url{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#44536b;font:12px ui-monospace,Menlo,monospace}.create-scene-row{display:flex;align-items:center;justify-content:space-between;padding:11px 16px;border-top:1px solid #edf0f4}.builder-body{padding:20px}.scene-name{display:grid;gap:7px;width:min(520px,100%);font-size:12px;color:#78859a}.drop-zone{margin-top:16px;min-height:92px;padding:0 14px;background:#fafbfc;border:1px dashed #ccd5e2;border-radius:10px}.sequence-row{display:grid;grid-template-columns:22px 32px 66px minmax(0,1fr) 28px;gap:10px;align-items:center;padding:12px 0;border-bottom:1px solid #e8ecf2}.drag-handle{color:#9da8b8;cursor:grab;font-size:18px}.remove{color:#9ba5b3;border:0;background:transparent}.drop-hint{height:90px;display:grid;place-items:center;color:#9aa5b5;font-size:13px}.save-row,.next-row{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-top:16px}.next-row{border-top:1px solid #e9edf2;padding-top:14px}.saved-note{display:flex;align-items:center;gap:9px;padding:11px 15px;background:#eff8f4;border-radius:9px;color:#34775f;font-size:13px}.saved-mark{background:#3b9876;border-radius:50%;width:19px;height:19px;display:grid;place-items:center;color:#fff}.scene-card{padding:15px 16px;border-bottom:1px solid #edf0f4}.scene-card:last-child{border:0}.scene-title{display:flex;align-items:center;gap:10px}.scene-title strong,.history-row strong{font-size:13px;color:#35445c}.source-tag{font-size:10px;color:#78869a;background:#f1f3f6;border-radius:12px;padding:3px 8px}.scene-steps{display:flex;flex-wrap:wrap;gap:8px;margin-top:12px}.scene-step{display:inline-flex;align-items:center;gap:6px;padding:7px 9px;background:#f6f8fb;border:1px solid #edf0f4;border-radius:7px;color:#59677d;font-size:11px}.scene-step i{font-style:normal;color:#4771c5;font-weight:700}.history-search{max-width:280px}.history-actions,.save-actions,.row-actions{display:flex;align-items:center;gap:8px}.history-row{padding:12px 16px;display:flex;align-items:center;justify-content:space-between;gap:16px;border-bottom:1px solid #f0f2f5}.history-row:last-child{border:0}.history-row small{display:block;color:#8793a5;margin-top:5px;max-width:680px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.ai-tag{margin-left:8px;color:#6b50a7;background:#f2edfc;border-radius:12px;padding:3px 7px;font-size:10px}.history-picker{padding:12px 16px}.history-picker>.input{width:min(360px,100%);margin-bottom:8px}.recommend-progress{display:flex;align-items:center;gap:12px;padding:12px;margin-bottom:12px;background:#f6f8fc;border:1px solid #e9edf4;border-radius:8px}.recommend-progress .spinner{width:18px;height:18px;border-width:2px;flex:none}.recommend-progress strong,.recommend-progress small{display:block}.recommend-progress strong{font-size:12px;color:#40516a}.recommend-progress small{margin-top:3px;color:#8290a3;font-size:11px}.recommend-summary{margin-bottom:10px;color:#4e6c5f;font-size:12px}.empty{padding:24px;color:#909bad;text-align:center}.alert{background:#fff1f1;color:#b43742;padding:10px;border-radius:8px}.ghost{opacity:.45}.dialog-backdrop{position:fixed;inset:0;background:#18263c66;z-index:30;display:grid;place-items:center;padding:16px}.scene-dialog{width:min(460px,100%);background:#fff;border-radius:10px;padding:22px;box-shadow:0 20px 60px #13253a33}.scene-dialog h3{margin:0;font-size:17px}.scene-dialog p{color:#7c899b;font-size:12px}.scene-dialog label{display:grid;gap:7px;color:#59667b;font-size:12px}.scene-dialog>div{display:flex;justify-content:flex-end;gap:8px;margin-top:18px}@media(max-width:720px){.panel-head,.save-row,.next-row,.create-scene-row{align-items:flex-start;flex-direction:column}.history-search{max-width:none;width:100%}.pool-row{grid-template-columns:20px 50px minmax(0,1fr) 40px}.pool-row .response{display:none}}
</style>

<style scoped>
.scene-steps{display:grid;gap:7px}.scene-step{display:flex;align-items:center;gap:9px;width:100%;cursor:default}.scene-drag{color:#8e9aab;cursor:grab}.scene-state{margin-left:auto;color:#3b8a6d;font-size:11px}.scene-card-foot{padding-top:10px;color:#8793a5;font-size:11px}
</style>
