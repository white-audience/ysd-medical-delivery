<template>
  <div class="container">
    <h2 class="homeTitle">
      药品总览<span><router-link to="drug">药品管理</router-link></span>
    </h2>
    <div class="orderviewBox">
      <ul>
        <li>
          <span class="status"><i class="iconfont icon-open"></i>已在售</span>
          <span class="num">{{ drugesData.sold }}</span>
        </li>
        <li>
          <span class="status"><i class="iconfont icon-stop"></i>已停售</span>
          <span class="num">{{ drugesData.discontinued }}</span>
        </li>
        <li class="add">
          <router-link to="/drug/add">
            <i></i>
            <p>新增药品</p>
          </router-link>
        </li>
      </ul>
    </div>
  </div>
</template>

<script lang="ts">
import { Component, Vue, Prop } from 'vue-property-decorator'
import orderList from './orderList.vue'
@Component({
  components: { orderList },
  name: 'cuisineview',
})
export default class extends Vue {
  @Prop() private drugesData!: any
}
</script>
