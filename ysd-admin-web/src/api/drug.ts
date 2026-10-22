import request from '@/utils/request'
/**
 *
 * 药品管理
 *
 **/
// 查询列表接口
export const getDrugPage = (params: any) => {
  return request({
    url: '/drug/page',
    method: 'get',
    params
  })
}

// 删除接口
export const deleteDrug = (ids: string) => {
  return request({
    url: '/drug',
    method: 'delete',
    params: { ids }
  })
}

// 修改接口
export const editDrug = (params: any) => {
  return request({
    url: '/drug',
    method: 'put',
    data: { ...params }
  })
}

// 新增接口
export const addDrug = (params: any) => {
  return request({
    url: '/drug',
    method: 'post',
    data: { ...params }
  })
}

// 查询详情
export const queryDrugById = (id: string | (string | null)[]) => {
  return request({
    url: `/drug/${id}`,
    method: 'get'
  })
}

// 获取药品分类列表
export const getCategoryList = (params: any) => {
  return request({
    url: '/category/list',
    method: 'get',
    params
  })
}

// 查药品列表的接口
export const queryDrugList = (params: any) => {
  return request({
    url: '/drug/list',
    method: 'get',
    params
  })
}

// 文件down预览
export const commonDownload = (params: any) => {
  return request({
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
    },
    url: '/common/download',
    method: 'get',
    params
  })
}

// 在售停售---批量在售停售接口
export const drugStatusByStatus = (params: any) => {
  return request({
    url: `/drug/status/${params.status}`,
    method: 'post',
    params: { id: params.id }
  })
}

//药品分类数据查询
export const drugCategoryList = (params: any) => {
  return request({
    url: `/category/list`,
    method: 'get',
    params: { ...params }
  })
}
