import Vue from "vue";
import Router from "vue-router";
import Layout from "@/layout/index.vue";
// import {
//   getToken,
//   setToken,
//   removeToken,
//   getStoreId,
//   setStoreId,
//   removeStoreId,
//   setUserInfo,
//   getUserInfo,
//   removeUserInfo
// } from "@/utils/cookies";
// import store from "@/store";

Vue.use(Router);

const router = new Router({
  scrollBehavior: (to, from, savedPosition) => {
    if (savedPosition) {
      return savedPosition;
    }
    return { x: 0, y: 0 };
  },
  base: process.env.BASE_URL,
  routes: [
    {
      path: "/login",
      component: () => import("@/views/login/index.vue"),
      meta: { title: "药速达智慧配送平台", hidden: true, notNeedAuth: true }
    },
    {
      path: "/404",
      component: () => import("@/views/404.vue"),
      meta: { title: "药速达智慧配送平台", hidden: true, notNeedAuth: true }
    },
    {
      path: "/",
      component: Layout,
      redirect: "/dashboard",
      children: [
        {
          path: "dashboard",
          component: () =>  import("@/views/dashboard/index.vue"),
          name: "Dashboard",
          meta: {
            title: "工作台",
            icon: "dashboard",
            affix: true
          }
        },
		    {
          path: "/statistics",
          component: () => import("@/views/statistics/index.vue"),
          meta: {
            title: "数据统计",
            icon: "icon-statistics"
          }
        },
        {
          path: "order",
          component: () => import("@/views/orderDetails/index.vue"),
          meta: {
            title: "订单管理",
            icon: "icon-order"
          }
        },
        {
          path: "drug-combo",
          component: () => import("@/views/drugCombo/index.vue"),
          meta: {
            title: "药品组合管理",
            icon: "icon-combo"
          }
        },
        {
          path: "drug",
          component: () => import("@/views/drug/index.vue"),
          meta: {
            title: "药品管理",
            icon: "icon-dish"
          }
        },
        {
          path: "/drug/add",
          component: () => import("@/views/drug/addDrug.vue"),
          meta: {
            title: "添加药品",
            hidden: true
          }
        },
        
        {
          path: "category",
          component: () => import("@/views/category/index.vue"),
          meta: {
            title: "分类管理",
            icon: "icon-category"
          }
        },
        {
          path: "employee",
          component: () => import("@/views/employee/index.vue"),
          meta: {
            title: "员工管理",
            icon: "icon-employee"
          }
        },
        
        {
          path: "/employee/add",
          component: () => import("@/views/employee/addEmployee.vue"),
          meta: {
            title: "添加/修改员工",
            hidden: true
          }
        },
        
        {
          path: "/drug-combo/add",
          component: () => import("@/views/drugCombo/addDrugCombo.vue"),
          meta: {
            title: "添加药品组合",
            hidden: true
          }
        }
      ]
    },
    {
      path: "*",
      redirect: "/404",
      meta: { hidden: true }
    }
  ]
});

export default router;
