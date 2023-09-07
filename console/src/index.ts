import "./styles/tailwind.css";
import "./styles/index.css";
import { definePlugin } from "@halo-dev/console-shared";
import NavList from "@/views/NavList.vue";
import { markRaw } from "vue";
import RiNavsLine from "~icons/ri/links-line";

export default definePlugin({
  components: {},
  routes: [
    {
      parentName: "Root",
      route: {
        path: "/navs",
        name: "Navs",
        component: NavList,
        meta: {
          permissions: ["plugin:navs:view"],
          // title: "导航功能",//菜单页的浏览器 tab 标题
          // 插件对应菜单的位置
          menu: {
            name: "导航", // 菜单显示名
            group: "content", // 所在组名
            icon: markRaw(RiNavsLine),
            // priority: 0, 权重
          },
          
        },
        
      },
      
    }, 
  ],
});
