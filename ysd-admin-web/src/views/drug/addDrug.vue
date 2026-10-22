<template>
  <div :key="vueRest"
       class="addBrand-container">
    <div :key="restKey"
         class="container">
      <el-form ref="ruleForm"
               :model="ruleForm"
               :rules="rules"
               :inline="true"
               label-width="180px"
               class="demo-ruleForm">
        <div>
          <el-form-item label="药品名称:"
                        prop="name">
            <el-input v-model="ruleForm.name"
                      placeholder="请填写药品名称"
                      maxlength="20" />
          </el-form-item>
          <el-form-item label="药品分类:"
                        prop="categoryId">
            <el-select v-model="ruleForm.categoryId"
                       placeholder="请选择药品分类">
              <el-option v-for="(item, index) in drugList"
                         :key="index"
                         :label="item.name"
                         :value="item.id" />
            </el-select>
          </el-form-item>
        </div>
        <div>
          <el-form-item label="药品价格:"
                        prop="price">
            <el-input v-model="ruleForm.price"
                      placeholder="请设置药品价格" />
          </el-form-item>
        </div>
        <el-form-item label="规格做法配置:">
          <el-form-item>
            <div class="specBox">
              <span v-if="specs.length == 0"
                    class="addBut"
                    @click="addSpece">
                + 添加规格</span>
              <div v-if="specs.length != 0"
                   class="spec">
                <div class="title">
                  <span>规格名（3个字内）</span>
                  <!-- <span class="des-box">规格标签（输入标签回车添加）</span> -->
                </div>
                <div class="cont">
                  <div v-for="(item, index) in specs"
                       :key="index"
                       class="items">
                    <div class="itTit">
                      <!-- :specs-data="filterSpecsData()" -->
                      <SelectInput :specs-data="leftSpecs"
                                   :index="index"
                                   :value="item.name"
                                   @select="selectHandle" />
                    </div>
                    <div class="labItems"
                         style="display: flex">
                      <span v-for="(it, ind) in item.value"
                            :key="ind">{{ it }}
                        <i @click="delSpecLabel(index, ind)">X</i></span>
                      <div class="inputBox"
                           :style="inputStyle" />
                    </div>
                    <span class="delSpec delBut non"
                          @click="delSpec(item.name)">删除</span>
                  </div>
                </div>
                <div v-if="
                       !!this.leftSpecs.length &&
                         this.specs.length < this.specsData.length
                     "
                     class="addBut"
                     @click="addSpece">
                  添加规格
                </div>
              </div>
            </div>
          </el-form-item>
        </el-form-item>
        <div>
          <el-form-item label="药品图片:"
                        prop="image">
            <image-upload :prop-image-url="imageUrl"
                          @imageChange="imageChange">
              图片大小不超过2M<br>仅能上传 PNG JPEG JPG类型图片<br>建议上传200*200或300*300尺寸的图片
            </image-upload>
          </el-form-item>
        </div>
        <div class="address">
          <el-form-item label="药品描述:"
                        prop="region">
            <el-input v-model="ruleForm.description"
                      type="textarea"
                      :rows="3"
                      maxlength="200"
                      placeholder="药品描述，最长200字" />
          </el-form-item>
        </div>
        <div class="subBox address">
          <el-button @click="() => $router.back()">
            取消
          </el-button>
          <el-button type="primary"
                     :class="{ continue: actionType === 'add' }"
                     @click="submitForm('ruleForm')">
            保存
          </el-button>
          <el-button v-if="actionType == 'add'"
                     type="primary"
                     @click="submitForm('ruleForm', 'goAnd')">
            保存并继续添加
          </el-button>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script lang="ts">
import { Component, Vue, Watch } from 'vue-property-decorator'
import HeadLable from '@/components/HeadLable/index.vue'
import SelectInput from './components/SelectInput.vue'
import ImageUpload from '@/components/ImgUpload/index.vue'
// getSpecList规格列表暂时不做 getDrugTypeList
import {
  queryDrugById,
  addDrug,
  editDrug,
  getCategoryList,
  commonDownload
} from '@/api/drug'
import { baseUrl } from '@/config.json'
import { getToken } from '@/utils/cookies'
@Component({
  name: 'addShop',
  components: {
    HeadLable,
    SelectInput,
    ImageUpload
  }
})
export default class extends Vue {
  private restKey: number = 0
  private textarea: string = ''
  private value: string = ''
  private imageUrl: string = ''
  private actionType: string = ''
  private drugList: string[] = []
  private specsData: any[] = [] //原始规格数据
  private specs: any[] = [] //待上传规格的数据
  private leftSpecs: any[] = [] //下拉框剩余可选择的规格数据
  private vueRest = '1'
  private index = 0
  private inputStyle = { flex: 1 }
  private headers = {
    token: getToken()
  }
  private ruleForm = {
    name: '',
    id: '',
    price: '',
    code: '',
    image: '',
    description: '',
    specs: [],
    status: true,
    categoryId: ''
  }

  get rules() {
    return {
      name: [
        {
          required: true,
          validator: (rule: any, value: string, callback: Function) => {
            if (!value) {
              callback(new Error('请输入药品名称'))
            } else {
              const reg = /^([A-Za-z0-9\u4e00-\u9fa5]){2,20}$/
              if (!reg.test(value)) {
                callback(new Error('药品名称输入不符，请输入2-20个字符'))
              } else {
                callback()
              }
            }
          },
          trigger: 'blur'
        }
      ],
      categoryId: [
        { required: true, message: '请选择药品分类', trigger: 'change' }
      ],
      image: {
        required: true,
        message: '药品图片不能为空'
      },
      price: [
        {
          required: true,
          // 'message': '请填写药品价格',
          validator: (rules: any, value: string, callback: Function) => {
            const reg = /^([1-9]\d{0,5}|0)(\.\d{1,2})?$/
            if (!reg.test(value) || Number(value) <= 0) {
              callback(
                new Error(
                  '药品价格格式有误，请输入大于零且最多保留两位小数的金额'
                )
              )
            } else {
              callback()
            }
          },
          trigger: 'blur'
        }
      ],
      code: [{ required: true, message: '请填写商品码', trigger: 'blur' }]
    }
  }

  created() {
    this.getDrugList()
    // 规格临时数据
    this.getSpecListHand()
    this.actionType = this.$route.query.id ? 'edit' : 'add'
    if (this.$route.query.id) {
      this.init()
    }
  }

  mounted() {}
  @Watch('specs')
  changeSpecs() {
    this.getLeftSpecs()
  }

  //过滤已选择的规格下拉框无法再次选择
  getLeftSpecs() {
    let arr = []
    this.specsData.map(item => {
      if (
        this.specs.findIndex(item1 => item.name === item1.name) === -1
      ) {
        arr.push(item)
      }
    })
    this.leftSpecs = arr
  }

  private selectHandle(val: any, key: any, ind: any) {
    const arrDate = [...this.specs]
    const index = this.specsData.findIndex(item => item.name === val)
    arrDate[key] = JSON.parse(JSON.stringify(this.specsData[index]))
    this.specs = arrDate
  }

  private async init() {
    queryDrugById(this.$route.query.id).then(res => {
      if (res && res.data && res.data.code === 1) {
        this.ruleForm = { ...res.data.data }
        this.ruleForm.price = String(res.data.data.price)
        this.ruleForm.status = res.data.data.status == '1'
        this.specs =
          res.data.data.specs &&
          res.data.data.specs.map(obj => ({
            ...obj,
            value: JSON.parse(obj.value)
          }))
        let arr = []
        this.getLeftSpecs()
        this.imageUrl = res.data.data.image
      } else {
        this.$message.error(res.data.msg)
      }
    })
  }

  // 按钮 - 添加规格
  private addSpece() {
    this.specs.push({ name: '', value: [] }) // JSON.parse(JSON.stringify(this.specsData))
  }

  // 按钮 - 删除规格
  private delSpec(name: string) {
    let ind = this.specs.findIndex(item => item.name === name)
    this.specs.splice(ind, 1)
  }

  // 按钮 - 删除规格标签
  private delSpecLabel(index: number, ind: number) {
    this.specs[index].value.splice(ind, 1)
  }

  //规格位置记录
  private specPosition(index: number) {
    this.index = index
  }

  // 添加规格标签
  private keyDownHandle(val: any) {
    if (event) {
      event.cancelBubble = true
      event.preventDefault()
      event.stopPropagation()
    }

    if (val.target.innerText.trim() != '') {
      this.specs[this.index].value.push(val.target.innerText)
      val.target.innerText = ''
    }
  }

  // 获取药品分类
  private getDrugList() {
    getCategoryList({ type: 1 }).then(res => {
      if (res.data.code === 1) {
        this.drugList = res && res.data && res.data.data
      } else {
        this.$message.error(res.data.msg)
      }
      // if (res.data.code == 200) {
      //   const {data} = res.data
      //   this.drugList = data
      // } else {
      //   this.$message.error(res.data.desc)
      // }
    })
  }

  // 获取规格列表
  private getSpecListHand() {
    // spec specData
    this.specsData = [
      { name: '剂型', value: ['片剂', '胶囊', '颗粒', '口服液'] },
      { name: '规格', value: ['0.25g', '0.5g', '10ml'] }
    ]
  }

  private submitForm(formName: any, st: any) {
    ;(this.$refs[formName] as any).validate((valid: any) => {
      console.log(valid, 'valid')
      if (valid) {
        if (!this.ruleForm.image) return this.$message.error('药品图片不能为空')
        let params: any = { ...this.ruleForm }
        // params.specs = this.specs
        params.status =
          this.actionType === 'add' ? 0 : this.ruleForm.status ? 1 : 0
        // params.price *= 100
        params.categoryId = this.ruleForm.categoryId
        params.specs = this.specs.map(obj => ({
          ...obj,
          value: JSON.stringify(obj.value)
        }))
        if (this.actionType == 'add') {
          delete params.id
          addDrug(params)
            .then(res => {
              if (res.data.code === 1) {
                this.$message.success('药品添加成功！')
                if (!st) {
                  this.$router.push({ path: '/drug' })
                } else {
                  this.specs = []
                  // this.specsData = []
                  this.imageUrl = ''
                  this.ruleForm = {
                    name: '',
                    id: '',
                    price: '',
                    code: '',
                    image: '',
                    description: '',
                    specs: [],
                    status: true,
                    categoryId: ''
                  }
                  this.restKey++
                }
              } else {
                this.$message.error(res.data.desc || res.data.msg)
              }
            })
            .catch(err => {
              this.$message.error('请求出错了：' + err.message)
            })
        } else {
          delete params.createTime
          delete params.updateTime
          editDrug(params)
            .then(res => {
              if (res && res.data && res.data.code === 1) {
                this.$router.push({ path: '/drug' })
                this.$message.success('药品修改成功！')
              } else {
                this.$message.error(res.data.desc || res.data.msg)
              }
              // if (res.data.code == 200) {
              //   this.$router.push({'path': '/drug'})
              //   this.$message.success('药品修改成功！')
              // } else {
              //   this.$message.error(res.data.desc || res.data.message)
              // }
            })
            .catch(err => {
              this.$message.error('请求出错了：' + err.message)
            })
        }
      } else {
        return false
      }
    })
  }

  imageChange(value: any) {
    this.ruleForm.image = value
  }
}
</script>
<style lang="scss" scoped>
.addBrand-container {
  .el-form--inline .el-form-item__content {
    width: 293px;
  }

  .el-input {
    width: 350px;
  }

  .address {
    .el-form-item__content {
      width: 777px !important;
    }
  }
}
</style>
<style lang="scss" scoped>
.addBrand {
  &-container {
    margin: 30px;

    .container {
      position: relative;
      z-index: 1;
      background: #fff;
      padding: 30px;
      border-radius: 4px;
      min-height: 500px;

      .subBox {
        padding-top: 30px;
        text-align: center;
        border-top: solid 1px $gray-5;
      }
      .upload-item {
        .el-form-item__error {
          top: 90%;
        }
      }
    }
  }
}

.specBox {
  width: 777px;

  .addBut {
    background: #1677ff;
    display: inline-block;
    padding: 0px 20px;
    border-radius: 3px;
    line-height: 40px;
    cursor: pointer;
    border-radius: 4px;
    color: #333333;
    font-weight: 500;
  }

  .spec {
    border: solid 1px #dfe2e8;
    border-radius: 3px;
    padding: 15px;
    background: #fafafb;

    .title {
      color: #606168;
      .des-box {
        padding-left: 44px;
      }
    }

    .cont {
      .items {
        display: flex;
        margin: 10px 0;

        .itTit {
          width: 150px;
          margin-right: 15px;

          input {
            width: 100%;
            // line-height: 40px;
            // border-radius: 3px;
            // padding: 0 10px;
          }
        }

        .labItems {
          flex: 1;
          display: flex;
          flex-wrap: wrap;
          border-radius: 3px;
          min-height: 39px;
          border: solid 1px #d8dde3;
          background: #fff;
          padding: 0 5px;

          span {
            display: inline-block;
            color: #1677ff;
            margin: 5px;
            line-height: 26px;
            padding: 0 10px;
            background: #f0f7ff;
            border: 1px solid #b3d8ff;
            border-radius: 4px;
            font-size: 12px;

            i {
              cursor: pointer;
              font-style: normal;
            }
          }

          .inputBox {
            display: inline-block;
            width: 100%;
            height: 36px;
            line-height: 36px;
            overflow: hidden;
          }
        }

        .delSpec {
          display: inline-block;
          padding: 0 10px;
          color: #f19c59;
          cursor: pointer;
        }
      }
    }
  }
}
</style>
