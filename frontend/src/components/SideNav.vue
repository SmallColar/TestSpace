<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { store } from '../store'

const route = useRoute()

const items = [
  { to: '/tasks', label: '自动化任务', icon: '◉' },
  { to: '/rules', label: '编排规则库', icon: '≡' },
  { to: '/cases', label: '自动化用例库', icon: '▤' },
  { to: '/profile', label: '个人中心', icon: '☺' }
]

const active = computed(() => route.path)
const initial = computed(() => (store.user?.nickname || store.user?.username || '?').slice(0, 1).toUpperCase())
const displayName = computed(() => store.user?.nickname || store.user?.username || '')
</script>

<template>
  <aside class="side">
    <div class="brand"><span class="brand-mark">✦</span><span class="name">TestPilot</span></div>

    <nav class="nav">
      <router-link
        v-for="it in items"
        :key="it.to"
        :to="it.to"
        class="nav-item"
        :class="{ on: active === it.to || active.startsWith(it.to + '/') }"
      >
        <span class="icon">{{ it.icon }}</span>
        <span>{{ it.label }}</span>
      </router-link>
    </nav>

    <div class="foot">
      <div class="who">
        <span class="avatar">{{ initial }}</span>
        <div class="grow">
          <div class="uname ellipsis">{{ displayName }}</div>
          <div class="urole">{{ store.isAdmin ? '管理员' : '普通用户' }}</div>
        </div>
      </div>
      <button class="btn btn-sm logout" @click="store.logout()">退出登录</button>
    </div>
  </aside>
</template>

<style scoped>
.side {
  width: var(--side-w);
  flex: none;
  background: var(--side);
  color: var(--side-text);
  display: flex;
  flex-direction: column;
  position: sticky;
  top: 0;
  height: 100vh;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 22px 18px 28px;
  border-bottom: 1px solid rgba(255, 255, 255, .07);
}
.brand-mark { color:#9ab7ff; font-size:17px; }
.name { color: #fff; font-weight: 600; font-size: 14px; }

.nav { padding: 12px 10px; display: flex; flex-direction: column; gap: 2px; flex: 1; }
.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 38px;
  padding: 0 12px;
  border-radius: var(--radius-sm);
  color: var(--side-text);
  font-size: 13px;
  transition: .15s;
}
.nav-item:hover { background: var(--side-hover); color: #fff; }
.nav-item.on { background: var(--brand); color: #fff; }
.icon { width: 16px; text-align: center; font-size: 13px; opacity: .9; }

.foot {
  padding: 12px;
  border-top: 1px solid rgba(255, 255, 255, .07);
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.who { display: flex; align-items: center; gap: 9px; }
.avatar {
  width: 30px;
  height: 30px;
  flex: none;
  border-radius: 50%;
  background: var(--side-hover);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 12px;
  font-weight: 600;
}
.uname { color: #fff; font-size: 13px; }
.urole { font-size: 11px; color: #7e879e; }
.logout {
  height: 28px;
  background: transparent;
  border-color: rgba(255, 255, 255, .14);
  color: var(--side-text);
}
.logout:hover { background: var(--side-hover); color: #fff; border-color: rgba(255, 255, 255, .2); }
</style>
