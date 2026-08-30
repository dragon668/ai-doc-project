<template>
  <div>
    <h2>好友列表</h2>
    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="12">
        <el-card>
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span>搜索用户</span>
              <el-input v-model="keyword" placeholder="用户名/昵称" style="width: 180px" clearable @keyup.enter="search" />
            </div>
          </template>
          <el-button type="primary" @click="search" style="margin-bottom: 12px">搜索</el-button>
          <div v-if="searchResults.length">
            <div v-for="user in searchResults" :key="user.id" style="display:flex;justify-content:space-between;align-items:center;padding:8px 0;border-bottom:1px solid #eee">
              <div>
                <div>{{ user.nickname || user.username }}</div>
                <small>{{ user.username }}</small>
              </div>
              <el-button size="small" @click="handleAdd(user.id)">添加好友</el-button>
            </div>
          </div>
          <div v-else style="color:#999">暂无搜索结果</div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>我的好友</template>
          <div v-if="friends.length">
            <div v-for="friend in friends" :key="friend.id" style="padding:8px 0;border-bottom:1px solid #eee">
              <strong>{{ friend.nickname || friend.username }}</strong>
              <div style="color:#666;font-size:12px">{{ friend.username }}</div>
            </div>
          </div>
          <div v-else style="color:#999">你还没有好友</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { listFriends, searchUsers, addFriend } from '@/api/friend'
import { ElMessage } from 'element-plus'

const friends = ref([])
const searchResults = ref([])
const keyword = ref('')

onMounted(async () => {
  await loadFriends()
})

async function loadFriends() {
  const res = await listFriends()
  friends.value = res.data || []
}

async function search() {
  if (!keyword.value.trim()) return
  const res = await searchUsers(keyword.value)
  searchResults.value = res.data || []
}

async function handleAdd(friendId) {
  await addFriend(friendId)
  ElMessage.success('添加好友成功')
  await loadFriends()
}
</script>
