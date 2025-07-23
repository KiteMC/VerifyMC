import { createApp, provide, ref, h } from 'vue';
import App from './App.vue';
import './index.css';
import { i18n } from './i18n';
import router from './router';

const app = createApp({
  setup() {
    const config = ref({});
    provide('config', config);
    fetch('/api/config').then(res => res.json()).then(data => {
      config.value = data;
    });
  },
  render: () => h(App)
});

app.use(router).use(i18n).mount('#app'); 