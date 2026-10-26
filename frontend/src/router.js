import { createRouter, createWebHashHistory } from 'vue-router'
import { store } from './store'
import Login from './views/Login.vue'
import TaskList from './views/TaskList.vue'
import TaskWizard from './views/TaskWizard.vue'
import RuleLibrary from './views/RuleLibrary.vue'
import CaseLibrary from './views/CaseLibrary.vue'
import Profile from './views/Profile.vue'

const routes = [
  { path: '/login', name: 'login', component: Login, meta: { public: true, title: '登录' } },
  { path: '/', redirect: '/tasks' },
  { path: '/tasks', name: 'tasks', component: TaskList, meta: { title: '自动化任务' } },
  { path: '/tasks/new', name: 'task-new', component: TaskWizard, meta: { title: '新建自动化任务' } },
  { path: '/tasks/:id', name: 'wizard', component: TaskWizard, meta: { title: '自动化任务' } },
  { path: '/rules', name: 'rules', component: RuleLibrary, meta: { title: '编排规则库' } },
  { path: '/cases', name: 'cases', component: CaseLibrary, meta: { title: '自动化用例库' } },
  { path: '/profile', name: 'profile', component: Profile, meta: { title: '个人中心' } }
]

export const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to) => {
  if (!to.meta.public && !store.user) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.path === '/login' && store.user) {
    return { path: '/tasks' }
  }
  return true
})
