<template>
  <div class="container">
    <h2 class="homeTitle">
      药品组合总览<span><router-link to="drug-combo">药品组合管理</router-link></span>
    </h2>
    <div class="orderviewBox">
      <ul>
        <li>
          <span class="status"><i class="iconfont icon-open"></i>已在售</span>
          <span class="num">{{ drugComboData.sold }}</span>
        </li>
        <li>
          <span class="status"><i class="iconfont icon-stop"></i>已停售</span>
          <span class="num">{{ drugComboData.discontinued }}</span>
        </li>
        <li class="add">
          <router-link to="drug-combo/add">
            <i></i>
            <p>新增药品组合</p>
          </router-link>
        </li>
      </ul>
    </div>
  </div>
</template>

<script lang="ts">
import { Component, Vue, Prop } from 'vue-property-decorator'
@Component({
  name: 'DrugComboStatistics',
})
export default class extends Vue {
  @Prop() private drugComboData!: any
}
</script>
