<template>
  <div class="addBrand-container">
    <div class="container">
      <el-form ref="ruleForm"
               :model="ruleForm"
               :rules="rules"
               :inline="true"
               label-width="180px"
               class="demo-ruleForm">
        <div>
          <el-form-item label="药品组合名称:"
                        prop="name">
            <el-input v-model="ruleForm.name"
                      placeholder="请填写药品组合名称"
                      maxlength="14" />
          </el-form-item>
          <el-form-item label="药品组合分类:"
                        prop="idType">
            <el-select v-model="ruleForm.idType"
                       placeholder="请选择药品组合分类"
                       @change="$forceUpdate()">
              <el-option v-for="(item, index) in drugComboList"
                         :key="index"
                         :label="item.name"
                         :value="item.id" />
            </el-select>
          </el-form-item>
        </div>
        <div>
          <el-form-item label="药品组合价格:"
                        prop="price">
            <el-input v-model="ruleForm.price"
                      placeholder="请设置药品组合价格" />
          </el-form-item>
        </div>
        <div>
          <el-form-item label="药品组合药品:"
                        required>
            <el-form-item>
              <div class="addDrug">
                <span v-if="drugTable.length == 0"
                      class="addBut"
                      @click="openAddDrug('new')">
                  + 添加药品</span>
                <div v-if="drugTable.length != 0"
                     class="content">
                  <div class="addBut"
                       style="margin-bottom: 20px"
                       @click="openAddDrug('change')">
                    + 添加药品
                  </div>
                  <div class="table">
                    <el-table :data="drugTable"
                              style="width: 100%">
                      <el-table-column prop="name"
                                       label="名称"
                                       width="180"
                                       align="center" />
                      <el-table-column prop="price"
                                       label="原价"
                                       width="180"
                                       align="center">
                        <template slot-scope="scope">
                          {{ (Number(scope.row.price).toFixed(2) * 100) / 100 }}
                        </template>
                      </el-table-column>
                      <el-table-column prop="address"
                                       label="数量"
                                       align="center">
                        <template slot-scope="scope">
                          <el-input-number v-model="scope.row.quantity"
                                           size="small"
                                           :min="1"
                                           :max="99"
                                           label="描述文字" />
                        </template>
                      </el-table-column>
                      <el-table-column prop="address"
                                       label="操作"
                                       width="180px;"
                                       align="center">
                        <template slot-scope="scope">
                          <el-button type="text"
                                     size="small"
                                     class="delBut non"
                                     @click="delDrugHandle(scope.$index)">
                            删除
                          </el-button>
                        </template>
                      </el-table-column>
                    </el-table>
                  </div>
                </div>
              </div>
            </el-form-item>
          </el-form-item>
        </div>
        <div>
          <el-form-item label="药品组合图片:"
                        required
                        prop="image">
            <image-upload :prop-image-url="imageUrl"
                          @imageChange="imageChange">
              图片大小不超过2M<br>仅能上传 PNG JPEG JPG类型图片<br>建议上传200*200或300*300尺寸的图片
            </image-upload>
          </el-form-item>
        </div>
        <div class="address">
          <el-form-item label="药品组合描述:">
            <el-input v-model="ruleForm.description"
                      type="textarea"
                      :rows="3"
                      maxlength="200"
                      placeholder="药品组合描述，最长200字" />
          </el-form-item>
        </div>
        <div class="subBox address">
          <el-form-item>
            <el-button @click="() => $router.back()">
              取消
            </el-button>
            <el-button type="primary"
                       :class="{ continue: actionType === 'add' }"
                       @click="submitForm('ruleForm', false)">
              保存
            </el-button>
            <el-button v-if="actionType == 'add'"
                       type="primary"
                       @click="submitForm('ruleForm', true)">
              保存并继续添加
            </el-button>
          </el-form-item>
        </div>
      </el-form>
    </div>
    <el-dialog v-if="dialogVisible"
               title="添加药品"
               class="addDrugList"
               :visible.sync="dialogVisible"
               width="60%"
               :before-close="handleClose">
      <AddDrug v-if="dialogVisible"
               ref="adddrug"
               :check-list="checkList"
               :seach-key="seachKey"
               :drug-list="drugList"
               @checkList="getCheckList" />
      <span slot="footer"
            class="dialog-footer">
        <el-button @click="handleClose">取 消</el-button>
        <el-button type="primary"
                   @click="addTableList">添 加</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-property-decorator'
import HeadLable from '@/components/HeadLable/index.vue'
import ImageUpload from '@/components/ImgUpload/index.vue'
import AddDrug from './components/AddDrug.vue'
import { queryDrugComboById, addDrugCombo, editDrugCombo } from '@/api/drugCombo'
import { getCategoryList } from '@/api/drug'
import { baseUrl } from '@/config.json'

@Component({
  name: 'addShop',
  components: {
    HeadLable,
    AddDrug,
    ImageUpload
  }
})
export default class extends Vue {
  private value: string = ''
  private drugComboList: [] = []
  private seachKey: string = ''
  private drugList: [] = []
  private imageUrl: string = ''
  private actionType: string = ''
  private drugTable: [] = []
  private dialogVisible: boolean = false
  private checkList: any[] = []
  private ruleForm = {
    name: '',
    categoryId: '',
    price: '',
    code: '',
    image: '',
    description: '',
    drugList: [],
    status: true,
    idType: ''
  }

  get rules() {
    return {
      name: {
        required: true,
        validator: (rule: any, value: string, callback: Function) => {
          if (!value) {
            callback(new Error('请输入药品组合名称'))
          } else {
            const reg = /^([A-Za-z0-9\u4e00-\u9fa5]){2,20}$/
            if (!reg.test(value)) {
              callback(new Error('药品组合名称输入不符，请输入2-20个字符'))
            } else {
              callback()
            }
          }
        },
        trigger: 'blur'
      },
      idType: {
        required: true,
        message: '请选择药品组合分类',
        trigger: 'change'
      },
      image: {
        required: true,
        message: '药品图片不能为空'
      },
      price: {
        required: true,
        // 'message': '请输入药品组合价格',
        validator: (rules: any, value: string, callback: Function) => {
          const reg = /^([1-9]\d{0,5}|0)(\.\d{1,2})?$/
          if (!reg.test(value) || Number(value) <= 0) {
            callback(
              new Error(
                '药品组合价格格式有误，请输入大于零且最多保留两位小数的金额'
              )
            )
          } else {
            callback()
          }
        },
        trigger: 'blur'
      },
      code: { required: true, message: '请输入商品码', trigger: 'blur' }
    }
  }

  created() {
    this.getDrugTypeList()
    this.actionType = this.$route.query.id ? 'edit' : 'add'
    if (this.actionType == 'edit') {
      this.init()
    }
  }

  private async init() {
    queryDrugComboById(this.$route.query.id).then(res => {
      if (res && res.data && res.data.code === 1) {
        this.ruleForm = res.data.data
        this.ruleForm.status = res.data.data.status == '1'
        ;(this.ruleForm as any).price = res.data.data.price
        this.imageUrl = res.data.data.image
        this.checkList = res.data.data.items
        this.drugTable = res.data.data.items.reverse()
        this.ruleForm.idType = res.data.data.categoryId
      } else {
        this.$message.error(res.data.msg)
      }
    })
  }

  // 获取药品组合分类
  private getDrugTypeList() {
    getCategoryList({ type: 2 }).then(res => {
      if (res && res.data && res.data.code === 1) {
        this.drugComboList = res.data.data.map((obj: any) => ({
          ...obj,
          idType: obj.id
        }))
      } else {
        this.$message.error(res.data.msg)
      }
    })
  }

  // 删除药品组合药品
  delDrugHandle(index: any) {
    this.drugTable.splice(index, 1)
    this.checkList = this.drugTable
    // this.checkList.splice(index, 1)
  }

  // 获取添加药品数据 - 确定加菜倒序展示
  private getCheckList(value: any) {
    this.checkList = [...value].reverse()
  }

  // 添加药品
  openAddDrug(st: string) {
    this.dialogVisible = true
  }
  // 取消添加药品
  handleClose(done: any) {
    // this.$refs.adddrug.close()
    this.dialogVisible = false
    this.checkList = JSON.parse(JSON.stringify(this.drugTable))
    // this.dialogVisible = false
  }

  // 保存添加药品列表
  public addTableList() {
    this.drugTable = JSON.parse(JSON.stringify(this.checkList))
    this.drugTable.forEach((n: any) => {
      n.quantity = 1
    })
    this.dialogVisible = false
  }

  public submitForm(formName: any, st: any) {
    ;(this.$refs[formName] as any).validate((valid: any) => {
      if (valid) {
        if (this.drugTable.length === 0) {
          return this.$message.error('药品组合下药品不能为空')
        }
        if (!this.ruleForm.image) return this.$message.error('药品组合图片不能为空')
        let prams = { ...this.ruleForm } as any
        prams.items = this.drugTable.map((obj: any) => ({
          quantity: obj.quantity,
          drugId: obj.drugId,
          name: obj.name,
          price: obj.price
        }))
        ;(prams as any).status =
          this.actionType === 'add' ? 0 : this.ruleForm.status ? 1 : 0
        prams.categoryId = this.ruleForm.idType
        // delete prams.drugList
        if (this.actionType == 'add') {
          delete prams.id
          addDrugCombo(prams)
            .then(res => {
              if (res && res.data && res.data.code === 1) {
                this.$message.success('药品组合添加成功！')
                if (!st) {
                  this.$router.push({ path: '/drug-combo' })
                } else {
                  ;(this as any).$refs.ruleForm.resetFields()
                  this.drugList = []
                  this.drugTable = []
                  this.ruleForm = {
                    name: '',
                    categoryId: '',
                    price: '',
                    code: '',
                    image: '',
                    description: '',
                    drugList: [],
                    status: true,
                    id: '',
                    idType: ''
                  } as any
                  this.imageUrl = ''
                }
              } else {
                this.$message.error(res.data.msg)
              }
            })
            .catch(err => {
              this.$message.error('请求出错了：' + err.message)
            })
        } else {
          delete prams.updateTime
          editDrugCombo(prams)
            .then(res => {
              if (res.data.code === 1) {
                this.$message.success('药品组合修改成功！')
                this.$router.push({ path: '/drug-combo' })
              } else {
                // this.$message.error(res.data.desc || res.data.message)
              }
            })
            .catch(err => {
              this.$message.error('请求出错了：' + err.message)
            })
        }
      } else {
        // console.log('error submit!!')
        return false
      }
    })
  }

  imageChange(value: any) {
    this.ruleForm.image = value
  }
}
</script>
<style>
.avatar-uploader .el-icon-plus:after {
  position: absolute;
  display: inline-block;
  content: ' ' !important;
  left: calc(50% - 20px);
  top: calc(50% - 40px);
  width: 40px;
  height: 40px;
  background: url('./../../assets/icons/icon_upload@2x.png') center center
    no-repeat;
  background-size: 20px;
}
</style>
<style lang="scss">
// .el-form-item__error {
//   top: 90%;
// }
.addBrand-container {
  .avatar-uploader .el-upload {
    border: 1px dashed #d9d9d9;
    border-radius: 6px;
    cursor: pointer;
    position: relative;
    overflow: hidden;
  }

  .avatar-uploader .el-upload:hover {
    border-color: #1677ff;
  }

  .avatar-uploader-icon {
    font-size: 28px;
    color: #8c939d;
    width: 200px;
    height: 160px;
    line-height: 160px;
    text-align: center;
  }

  .avatar {
    width: 200px;
    height: 160px;
    display: block;
  }

  // .el-form--inline .el-form-item__content {
  //   width: 293px;
  // }

  .el-input {
    width: 293px;
  }

  .address {
    .el-form-item__content {
      width: 777px !important;
    }
  }
  .el-input__prefix {
    top: 2px;
  }

  .addDrug {
    .el-input {
      width: 130px;
    }

    .el-input-number__increase {
      border-left: solid 1px #b3d8ff;
      background: #f0f7ff;
    }

    .el-input-number__decrease {
      border-right: solid 1px #b3d8ff;
      background: #f0f7ff;
    }

    input {
      border: 1px solid #b3d8ff;
    }

    .table {
      border: solid 1px #ebeef5;
      border-radius: 3px;

      th {
        padding: 5px 0;
      }

      td {
        padding: 7px 0;
      }
    }
  }

  .addDrugList {
    .seachDrug {
      position: absolute;
      top: 12px;
      right: 20px;
    }

    .el-dialog__footer {
      padding-top: 27px;
    }

    .el-dialog__body {
      padding: 0;
      border-bottom: solid 1px #efefef;
    }
    .seachDrug {
      .el-input__inner {
        height: 40px;
        line-height: 40px;
      }
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
      .el-input {
        width: 350px;
      }
      .addDrug {
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

        .content {
          background: #fafafb;
          padding: 20px;
          border: solid 1px #d8dde3;
          border-radius: 3px;
        }
      }
    }
  }
}
</style>
