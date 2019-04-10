package com.sinoiov.lhjh.modules.order.service.impl;

import com.alibaba.fastjson.JSON;
import com.github.pagehelper.PageInfo;
import com.sinoiov.lhjh.common.exception.BusinessException;
import com.sinoiov.lhjh.common.pojo.dto.PageInfoDto;
import com.sinoiov.lhjh.common.pojo.dto.Result;
import com.sinoiov.lhjh.common.service.impl.BaseServiceImpl;
import com.sinoiov.lhjh.modules.consumetest.remoteService.RemoteBeancardScoreService;
import com.sinoiov.lhjh.modules.consumetest.remoteService.pojo.dto.BeancardScoreDto;
import com.sinoiov.lhjh.modules.consumetest.remoteService.pojo.vo.UpdateBeancardScoreVo;
import com.sinoiov.lhjh.modules.order.entity.Orders;
import com.sinoiov.lhjh.modules.order.pojo.dto.OrdersDto;
import com.sinoiov.lhjh.modules.order.pojo.vo.SearchOrdersVo;
import com.sinoiov.lhjh.modules.order.service.OrdersService;
import com.sinoiov.lhjh.modules.order.mapper.OrdersMapper;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * TODO
 *
 * @author songaw
 * @date 2019-56-10  16:56:29
 */
@Slf4j
@Data
@Service
public class OrdersServiceImpl extends BaseServiceImpl<Orders, Long> implements OrdersService {
    @Autowired
    RemoteBeancardScoreService remoteBeancardScoreService;

    @Autowired
    OrdersMapper baseMapper;


    @Override
    public boolean pay(Long id) {
        //非常弱的补偿版 服务一挂就完了
        //检查是否支付
        //锁订单id
        Orders orders = selectByPk(id);
        //简单点 0 未支付 1 支付
        if ("1".equals(orders.getPayStatus())) {
            throw new BusinessException("订单已经支付了");
        } else if ("0".equals(orders.getPayStatus())) {
            Result<BeancardScoreDto> result = remoteBeancardScoreService.getBeancardScoreByUserId(orders.getUserId());
            if (!result.getSuccess()) {
                throw new BusinessException(result.getCode(), result.getMessage());
            }
            //检查买家的卡豆是否充足
            BeancardScoreDto beancardScoreDto = result.getData();
            if (beancardScoreDto.getTotalscore() < orders.getBeanNums()) {
                throw new BusinessException("error.500", "余额不够不能支付");
            } else {
                //扣除买家卡豆
                UpdateBeancardScoreVo updateBeancardScoreVo = new UpdateBeancardScoreVo();
                updateBeancardScoreVo.setUserid(orders.getUserId());
                updateBeancardScoreVo.setType("0");
                updateBeancardScoreVo.setTotalscore(orders.getBeanNums());
                Result result1 = remoteBeancardScoreService.updateBeancardScore(updateBeancardScoreVo);
                if (result1.getSuccess()) {
                    //添加卖家卡豆
                    UpdateBeancardScoreVo updateBeancardScoreVo2 = new UpdateBeancardScoreVo();
                    updateBeancardScoreVo2.setUserid(orders.getFormUserId());
                    updateBeancardScoreVo2.setType("1");
                    updateBeancardScoreVo2.setTotalscore(orders.getBeanNums());
                    Result result2 = remoteBeancardScoreService.updateBeancardScore(updateBeancardScoreVo2);
                    if (result2.getSuccess()) {
                        //订单状态改变
                        orders.setUpdateTime(System.currentTimeMillis());
                        orders.setPayStatus("1");
                        int result3 =0;
                        try {
                            result3 = update(orders);
                        }catch (Exception e){
                            e.printStackTrace();
                            result3=0;
                        }
                        if (result3 > 0) {
                            return true;
                        } else {
                            //回滚逻辑1
                            //回滚逻辑买家
                            updateBeancardScoreVo.setType("1");
                            updateBeancardScoreVo.setTotalscore(orders.getBeanNums());
                            Result rollbackResult =  remoteBeancardScoreService.updateBeancardScore(updateBeancardScoreVo);
                            if(!rollbackResult.getSuccess()){
                                //钉钉报警
                                log.error(JSON.toJSONString(rollbackResult));
                            }
                            //回滚卖家
                            updateBeancardScoreVo2.setType("0");
                            updateBeancardScoreVo2.setTotalscore(orders.getBeanNums());
                            Result rollbackResult2 =  remoteBeancardScoreService.updateBeancardScore(updateBeancardScoreVo2);
                            if(!rollbackResult2.getSuccess()){
                                //钉钉报警
                                log.error(JSON.toJSONString(rollbackResult2));
                            }

                            //回滚逻辑
                            throw new BusinessException("修改订单失败!");
                        }
                    } else {
                        //回滚逻辑1
                        //回滚逻辑买家
                        updateBeancardScoreVo.setType("1");
                        updateBeancardScoreVo.setTotalscore(orders.getBeanNums());
                        Result rollbackResult =  remoteBeancardScoreService.updateBeancardScore(updateBeancardScoreVo);
                        if(!rollbackResult.getSuccess()){
                            //钉钉报警
                            log.error(JSON.toJSONString(rollbackResult));
                        }
                        //回滚卖家
                        updateBeancardScoreVo2.setType("0");
                        updateBeancardScoreVo2.setTotalscore(orders.getBeanNums());
                        Result rollbackResult2 =  remoteBeancardScoreService.updateBeancardScore(updateBeancardScoreVo2);
                        if(!rollbackResult2.getSuccess()){
                            //钉钉报警
                            log.error(JSON.toJSONString(rollbackResult2));
                        }
                        //回滚上面的逻辑
                        throw new BusinessException(result2.getCode(), result2.getMessage());
                    }
                } else {
                    //回滚逻辑
                    updateBeancardScoreVo.setType("1");
                    updateBeancardScoreVo.setTotalscore(orders.getBeanNums());
                    Result rollbackResult =  remoteBeancardScoreService.updateBeancardScore(updateBeancardScoreVo);
                    if(!rollbackResult.getSuccess()){
                       //钉钉报警
                        log.error(JSON.toJSONString(rollbackResult));
                    }
                    throw new BusinessException(result1.getCode(), result1.getMessage());
                }
            }
        } else {
            throw new BusinessException("订单状态出错");
        }
    }
}
