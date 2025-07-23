import { createApp, provide, ref, h } from 'vue'
import App from './App.vue'
import router from './router'
import i18n from './i18n'
import './index.css'

// 全局错误处理
window.addEventListener('error', (event) => {
  console.error('Global error:', event.error)
})

window.addEventListener('unhandledrejection', (event) => {
  console.error('Unhandled promise rejection:', event.reason)
})

const app = createApp({
  setup() {
    const config = ref({});
    
    const loadConfig = async () => {
      try {
        const response = await fetch('/api/config');
        const data = await response.json();
        config.value = data;
      } catch (error) {
        console.error('Failed to load config:', error);
      }
    };
    
    const reloadConfig = async () => {
      try {
        await fetch('/api/reload-config', { method: 'POST' });
        await loadConfig();
      } catch (error) {
        console.error('Failed to reload config:', error);
      }
    };
    
    provide('config', config);
    provide('reloadConfig', reloadConfig);
    
    // 初始加载配置
    loadConfig();
  },
  render: () => h(App)
});

app.use(router)
app.use(i18n)

app.mount('#app') 