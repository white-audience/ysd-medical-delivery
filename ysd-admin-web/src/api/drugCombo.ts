import request from '@/utils/request'
/**
 *
 * 药品组合管理
 *
 **/

//药品组合分页查询
export const getDrugComboPage = (params: any) => {
    return request({
        url: '/drug-combo/page',
        method: 'GET',
        params: params
    })
}

//药品组合在售停售
export const enableOrDisableDrugCombo = (params: any) => {
    return request({
        url: `/drug-combo/status/${params.status}`,
        method: 'POST',
        params: {id: params.id}
    })
}

//删除药品组合
export const deleteDrugCombo = (ids: string) => {//1,2,3
    return request({
        url: '/drug-combo',
        method: 'DELETE',
        params: {ids: ids}
    })
}


  
// 修改数据接口
export const editDrugCombo = (params: any) => {
    return request({
        url: '/drug-combo',
        method: 'put',
        data: { ...params }
    })
}

// 新增数据接口
export const addDrugCombo = (params: any) => {
    return request({
        url: '/drug-combo',
        method: 'post',
        data: { ...params }
    })
}

// 查询详情接口
export const queryDrugComboById = (id: string | (string | null)[]) => {
    return request({
        url: `/drug-combo/${id}`,
        method: 'get'
    })
}
