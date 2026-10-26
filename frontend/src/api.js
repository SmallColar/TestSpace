const TOKEN_KEY = 'testpilot_token'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}
export function setToken(t) {
  if (t) localStorage.setItem(TOKEN_KEY, t)
  else localStorage.removeItem(TOKEN_KEY)
}

async function request(url, options = {}) {
  const headers = { ...(options.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }), ...(options.headers || {}) }
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`

  const resp = await fetch(url, { ...options, headers })
  let body
  try {
    body = await resp.json()
  } catch (e) {
    throw new Error(`服务无响应（HTTP ${resp.status}）`)
  }
  if (body.code === 40100) {
    setToken('')
    if (!location.hash.startsWith('#/login')) location.hash = '#/login'
    throw new Error(body.message || '登录已过期')
  }
  if (!resp.ok || body.code !== 0) {
    throw new Error(body.message || '请求失败')
  }
  return body.data
}

const get = (url) => request(url)
const post = (url, data) => request(url, { method: 'POST', body: JSON.stringify(data || {}) })
const put = (url, data) => request(url, { method: 'PUT', body: JSON.stringify(data || {}) })
const del = (url) => request(url, { method: 'DELETE' })

export const api = {
  // ---- 认证 ----
  register: (data) => post('/api/auth/register', data),
  login: (data) => post('/api/auth/login', data),
  me: () => get('/api/auth/me'),

  // ---- 个人中心 ----
  profile: () => get('/api/user/profile'),
  updateProfile: (data) => put('/api/user/profile', data),
  changePassword: (data) => put('/api/user/password', data),

  // ---- 录制任务 ----
  createTask: (data) => post('/api/tasks', data),
  updateTask: (data) => put('/api/tasks/update', data),
  copyTask: (data) => post('/api/tasks/copy', data),
  deleteTask: (id) => del(`/api/tasks/delete?id=${id}`),
  startRecording: (taskId) => post('/api/tasks/recording/start', { taskId }),
  restartRecording: (taskId) => post('/api/tasks/recording/restart', { taskId }),
  terminateRecording: (taskId) => post('/api/tasks/recording/terminate', { taskId }),
  confirmRecording: (taskId, apiIds) => post('/api/tasks/recording/confirm', { taskId, apiIds }),
  listTasks: () => get('/api/tasks'),
  getTask: (id) => get(`/api/tasks/detail?id=${id}`),
  getApis: (id) => get(`/api/tasks/apis?id=${id}`),
  getTaskRules: (id) => get(`/api/tasks/rules?id=${id}`),
  generateCases: (taskId, prompt) => post('/api/tasks/cases/generate', { taskId, prompt }),
  saveCaseGenerationPrompt: (taskId, prompt) => put('/api/tasks/cases/prompt', { taskId, prompt }),
  getGeneratedCases: (id) => get(`/api/tasks/cases/generated?id=${id}`),
  saveGeneratedCases: (data) => post('/api/tasks/cases/save', data),

  // 节点②
  rulesFromRecord: (data) => post('/api/tasks/rules/from-record', data),
  rulesFromHistory: (data) => post('/api/tasks/rules/from-history', data),
  completeOrchestration: (taskId) => post('/api/tasks/rules/complete', { taskId }),

  // ---- 编排规则库 ----
  listRules: () => get('/api/rules'),
  updateRule: (id, data) => put('/api/rules/update', { ...data, id }),
  deleteRule: (id) => del(`/api/rules/delete?id=${id}`),
  createRule: (data) => post('/api/rules', data),
  recommendRules: (data) => post('/api/rules/recommend', data),
  analyzeRuleFile: (file) => {
    const body = new FormData(); body.append('file', file)
    return request('/api/rules/analyze-upload', { method: 'POST', body })
  },

  // ---- 自动化用例库 ----
  listCases: () => get('/api/cases'),
  createCase: (data) => post('/api/cases', data),
  copyCase: (data) => post('/api/cases/copy', data),
  updateCase: (id, data) => put('/api/cases/update', { ...data, id }),
  deleteCase: (id) => del(`/api/cases/delete?id=${id}`),
}
