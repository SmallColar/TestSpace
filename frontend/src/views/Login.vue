<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { store } from '../store'
import { api } from '../api'

const router = useRouter()
const route = useRoute()

const tab = ref('login')
const username = ref('')
const password = ref('')
const confirm = ref('')
const nickname = ref('')
const err = ref('')
const busy = ref(false)

async function doRegister() {
  err.value = ''
  if (!username.value.trim() || !password.value) {
    err.value = '请填写用户名和密码'
    return
  }
  if (password.value.length < 6) {
    err.value = '密码至少 6 位'
    return
  }
  if (password.value !== confirm.value) {
    err.value = '两次输入的密码不一致'
    return
  }
  busy.value = true
  try {
    await api.register({
      username: username.value.trim(),
      password: password.value,
      nickname: nickname.value.trim() || undefined
    })
    await store.login(username.value.trim(), password.value)
    router.replace(route.query.redirect || '/tasks')
  } catch (e) {
    err.value = e.message
  } finally {
    busy.value = false
  }
}

async function doLogin() {
  err.value = ''
  if (!username.value.trim() || !password.value) {
    err.value = '请填写用户名和密码'
    return
  }
  busy.value = true
  try {
    await store.login(username.value.trim(), password.value)
    router.replace(route.query.redirect || '/tasks')
  } catch (e) {
    err.value = e.message
  } finally {
    busy.value = false
  }
}

function switchTab(t) {
  tab.value = t
  err.value = ''
}
</script>

<template>
  <div class="wrap">
    <div class="card">
      <div class="head">
        <span class="logo">TP</span>
        <h1>TestPilot</h1>
        <p class="sub">多 Agent LLM 自动化测试平台</p>
      </div>

      <div class="tabs">
        <button :class="{ on: tab === 'login' }" @click="switchTab('login')">登录</button>
        <button :class="{ on: tab === 'register' }" @click="switchTab('register')">注册</button>
      </div>

      <form class="form" @submit.prevent="tab === 'login' ? doLogin() : doRegister()">
        <div class="field">
          <label>用户名</label>
          <input v-model="username" class="input" placeholder="请输入用户名" autocomplete="username" />
        </div>

        <div v-if="tab === 'register'" class="field">
          <label>昵称（选填）</label>
          <input v-model="nickname" class="input" placeholder="展示用昵称" />
        </div>

        <div class="field">
          <label>密码</label>
          <input v-model="password" type="password" class="input" placeholder="请输入密码"
                 :autocomplete="tab === 'login' ? 'current-password' : 'new-password'" />
        </div>

        <div v-if="tab === 'register'" class="field">
          <label>确认密码</label>
          <input v-model="confirm" type="password" class="input" placeholder="请再次输入密码"
                 autocomplete="new-password" />
        </div>

        <p v-if="err" class="err">{{ err }}</p>

        <button class="btn btn-primary submit" type="submit" :disabled="busy">
          {{ busy ? '处理中…' : tab === 'login' ? '登 录' : '注 册' }}
        </button>
      </form>

      <p class="tip">本平台开放注册。管理员账号由系统预置。</p>
    </div>
  </div>
</template>

<style scoped>
.wrap {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 32px 16px;
  background:
    radial-gradient(900px 500px at 15% -10%, #e5e7ff 0%, transparent 60%),
    radial-gradient(700px 420px at 100% 110%, #dcf2ff 0%, transparent 55%),
    var(--bg);
}
.card {
  width: 100%;
  max-width: 380px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 14px;
  box-shadow: var(--shadow);
  padding: 28px 26px 22px;
}
.head { text-align: center; margin-bottom: 20px; }
.logo {
  width: 46px; height: 46px;
  margin: 0 auto 12px;
  border-radius: 13px;
  background: var(--brand);
  color: #fff;
  display: grid; place-items: center;
  font-weight: 700;
  letter-spacing: .5px;
}
.head h1 { font-size: 20px; }
.sub { font-size: 12px; color: var(--text-3); margin-top: 4px; }

.tabs {
  display: flex;
  gap: 4px;
  background: var(--line-2);
  padding: 3px;
  border-radius: 8px;
  margin-bottom: 18px;
}
.tabs button {
  flex: 1;
  height: 30px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--text-2);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
  transition: .15s;
}
.tabs button.on { background: #fff; color: var(--brand-dark); font-weight: 500; box-shadow: var(--shadow-sm); }

.form { display: flex; flex-direction: column; gap: 14px; }
.submit { height: 38px; margin-top: 4px; }
.err {
  margin: 0;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  background: var(--danger-soft);
  color: var(--danger);
  font-size: 12.5px;
}
.tip { margin-top: 16px; text-align: center; font-size: 12px; color: var(--text-3); }
</style>
