import { createRouter, createWebHistory } from "vue-router"
import type { RouteRecordRaw } from "vue-router"

const routes: RouteRecordRaw[] = [
  {
    path: "/",
    name: "Home",
    component: () => import("./pages/Home.vue"),
    meta: { title: "home.title" },
  },
  {
    path: "/register",
    name: "Register",
    component: () => import("./pages/Register.vue"),
    meta: { title: "register.title" },
  },
  {
    path: "/login",
    name: "Login",
    component: () => import("./pages/Login.vue"),
    meta: { title: "login.title" },
  },
  {
    path: "/admin",
    name: "AdminPanel",
    component: () => import("./pages/AdminPanel.vue"),
    meta: { title: "admin.title", requiresAuth: true },
  },
  {
    path: "/status",
    name: "UserStatus",
    component: () => import("./pages/UserStatus.vue"),
    meta: { title: "user_status.title", requiresAuth: true },
  },
  {
    path: "/404",
    name: "NotFound",
    component: () => import("./pages/NotFound.vue"),
    meta: { title: "errors.404.title" },
  },
  {
    path: "/500",
    name: "ServerError",
    component: () => import("./pages/ServerError.vue"),
    meta: { title: "errors.500.title" },
  },
  {
    path: "/:pathMatch(.*)*",
    redirect: "/404",
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    } else {
      return { top: 0 }
    }
  },
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
