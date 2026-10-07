// 站点与版权信息统一出口。
// 页脚文案只在本地维护一份：应用内页脚（App.vue）与登录页页脚（LoginView.vue）
// 共用 copyrightText，避免两处各写一份导致不一致。
export const SITE = {
  name: '猫咖管理系统',
  nameEn: 'Cat Coffee Management System',
  author: 'AlistairBo',
  authorUrl: 'https://github.com/abobb414'
}

// 动态取当前年，避免跨年后写死在模板里
export const currentYear = new Date().getFullYear()

export const copyrightText = `© ${currentYear} ${SITE.author} · ${SITE.name} · ${SITE.nameEn}`
