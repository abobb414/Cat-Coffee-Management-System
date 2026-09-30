import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import './styles.css'

// 不传 locale 时 Element Plus 回落到英文，分页会显示「Total 3」、
// 二次确认框按钮会显示「OK / Cancel」，与全站中文界面不一致。
createApp(App).use(router).use(ElementPlus, { locale: zhCn }).mount('#app')
