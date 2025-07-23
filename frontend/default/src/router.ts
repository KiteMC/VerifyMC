import { createRouter, createWebHistory } from 'vue-router'
import Home from './pages/Home.vue'
import Register from './pages/Register.vue'
import AdminPanel from './pages/AdminPanel.vue'
import NotFound from './pages/NotFound.vue'
import ServerError from './pages/ServerError.vue'

const routes = [
  { path: '/', component: Home },
  { path: '/register', component: Register },
  { path: '/admin', component: AdminPanel },
  { path: '/500', component: ServerError },
  { path: '/:pathMatch(.*)*', name: 'NotFound', component: NotFound },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// Navigation guards
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem("admin_token")
  if (to.path === '/login' && token) {
    next('/admin')
    return
  }
  if (to.meta.requiresAuth && !token) {
    next('/login')
    return
  }
  next()
})

export default router 