package com.itheima.pinda.controller;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.itheima.pinda.DTO.*;
import com.itheima.pinda.DTO.angency.AgencyScopeDto;
import com.itheima.pinda.DTO.user.CourierScopeDto;
import com.itheima.pinda.authority.api.AreaApi;
import com.itheima.pinda.authority.api.OrgApi;
import com.itheima.pinda.authority.api.UserApi;
import com.itheima.pinda.authority.entity.auth.User;
import com.itheima.pinda.authority.entity.core.Org;
import com.itheima.pinda.authority.enumeration.common.StaticStation;
import com.itheima.pinda.base.R;
import com.itheima.pinda.common.context.RequestContext;
import com.itheima.pinda.common.enums.ErrorCode;
import com.itheima.pinda.common.utils.EntCoordSyncJob;
import com.itheima.pinda.common.utils.PageResponse;
import com.itheima.pinda.common.utils.Result;
import com.itheima.pinda.enums.OrderStatus;
import com.itheima.pinda.enums.driverjob.DriverJobStatus;
import com.itheima.pinda.enums.pickuptask.PickupDispatchTaskAssignedStatus;
import com.itheima.pinda.enums.pickuptask.PickupDispatchTaskStatus;
import com.itheima.pinda.enums.pickuptask.PickupDispatchTaskType;
import com.itheima.pinda.enums.transportorder.TransportOrderStatus;
import com.itheima.pinda.enums.transporttask.TransportTaskStatus;
import com.itheima.pinda.feign.*;
import com.itheima.pinda.feign.agency.AgencyScopeFeign;
import com.itheima.pinda.feign.transportline.TransportTripsFeign;
import com.itheima.pinda.feign.user.CourierScopeFeign;
import com.itheima.pinda.future.PdCompletableFuture;
import com.itheima.pinda.vo.AgencyVo;
import com.itheima.pinda.vo.AreaSimpleVo;
import com.itheima.pinda.vo.SysUserVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Controller;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * <p>
 * ????????? ???????????????
 * </p>
 *
 * @author diesel
 * @since 2020-03-19
 */
@Slf4j
@Api(tags = "???????????????")
@Controller
@RequestMapping("business/cargo")
public class CargoController {


    private final DriverJobFeign driverJobFeign;

    private final OrgApi orgApi;

    private final AreaApi areaApi;

    private final TransportTripsFeign transportTripsFeign;

    private final UserApi userApi;

    private final TransportOrderFeign transportOrderFeign;

    private final TransportTaskFeign transportTaskFeign;

    private final OrderFeign orderFeign;

    private final AgencyScopeFeign agencyScopeFeign;

    private final PickupDispatchTaskFeign pickupDispatchTaskFeign;

    private final CourierScopeFeign courierScopeFeign;

    public CargoController(PickupDispatchTaskFeign pickupDispatchTaskFeign, AgencyScopeFeign agencyScopeFeign, OrderFeign orderFeign, TransportTaskFeign transportTaskFeign, DriverJobFeign driverJobFeign, OrgApi orgApi, AreaApi areaApi, TransportOrderFeign transportOrderFeign, TransportTripsFeign transportTripsFeign, UserApi userApi,CourierScopeFeign courierScopeFeign) {
        this.pickupDispatchTaskFeign = pickupDispatchTaskFeign;
        this.agencyScopeFeign = agencyScopeFeign;
        this.orderFeign = orderFeign;
        this.transportTaskFeign = transportTaskFeign;
        this.driverJobFeign = driverJobFeign;
        this.orgApi = orgApi;
        this.areaApi = areaApi;
        this.transportOrderFeign = transportOrderFeign;
        this.transportTripsFeign = transportTripsFeign;
        this.userApi = userApi;
        this.courierScopeFeign =courierScopeFeign;
    }

    @SneakyThrows
    @ApiOperation(value = "?????????????????????")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "page", value = "????????????", required = true),
            @ApiImplicitParam(name = "pagesize", value = "????????????", required = true)
    })
    @ResponseBody
    @GetMapping("wait")
    public Result waitDelivery(Integer page, Integer pagesize) {

        //  ????????????id ???????????????
        String driverId = RequestContext.getUserId();
        log.info("???????????????:{}", driverId);

        DriverJobDTO driverJobDTO = new DriverJobDTO();
        driverJobDTO.setPage(page);
        driverJobDTO.setPageSize(pagesize);
        driverJobDTO.setStatus(DriverJobStatus.PENDING.getCode());
        driverJobDTO.setDriverId(driverId);

        log.info("????????????????????? PARAMS:{}", driverJobDTO);
        PageResponse<DriverJobDTO> result = driverJobFeign.findByPage(driverJobDTO);
        log.info("????????????????????? RESULT:{}", result.getItems());
        if(result.getItems().size()>0){
// ????????????
            Set<String> agencySet = new HashSet<>();
            agencySet.addAll(result.getItems().stream().map(item -> item.getStartAgencyId()).collect(Collectors.toSet()));
            agencySet.addAll(result.getItems().stream().map(item -> item.getEndAgencyId()).collect(Collectors.toSet()));
            CompletableFuture<List<Org>> agencyListFuture = PdCompletableFuture.agencyListFuture(orgApi, null, agencySet, null);
            List<Org> agencyList = agencyListFuture.get();

            // ????????????
            Set<Long> areaSet = new HashSet<>();
            areaSet.addAll(agencyList.stream().map(item -> item.getProvinceId()).collect(Collectors.toSet()));
            areaSet.addAll(agencyList.stream().map(item -> item.getCityId()).collect(Collectors.toSet()));
            areaSet.addAll(agencyList.stream().map(item -> item.getCountyId()).collect(Collectors.toSet()));

            CompletableFuture<Map> areaMapFuture = PdCompletableFuture.areaMapFuture(areaApi, null, areaSet);

            Set<String> taskTransportSet = result.getItems().stream().map(item -> item.getTaskTransportId()).collect(Collectors.toSet());
            CompletableFuture<Map<String, TaskTransportDTO>> taskTransportFuture = PdCompletableFuture.taskTramsportMapFuture(transportTaskFeign, taskTransportSet);

            Map<String, TaskTransportDTO> taskTransportMap = taskTransportFuture.get();
            Map areaMap = areaMapFuture.get();

            Map<String, AgencyVo> agencyMap = agencyList.stream().map(item -> {
                AgencyVo agencyVo = new AgencyVo();
                BeanUtils.copyProperties(item, agencyVo);
                agencyVo.setId(item.getId().toString());
                agencyVo.setProvince((AreaSimpleVo) areaMap.get(item.getProvinceId()));
                agencyVo.setCity((AreaSimpleVo) areaMap.get(item.getCityId()));
                agencyVo.setCounty((AreaSimpleVo) areaMap.get(item.getCountyId()));
                return agencyVo;
            }).collect(Collectors.toMap(AgencyVo::getId, vo -> vo));


            List<CargoTranTaskDTO> cargoTranTaskDTOS = result.getItems().stream().map(item -> new CargoTranTaskDTO(item, taskTransportMap, agencyMap)).collect(Collectors.toList());

            log.info("???????????????,?????????????????????{}", cargoTranTaskDTOS);
            if (CollectionUtils.isEmpty(cargoTranTaskDTOS)) {
                return Result.ok().put("data", PageResponse.<CargoTranTaskDTO>builder()
                        .counts(0L).page(page).pagesize(pagesize).pages(0L)
                        .build());
            }


            driverJobDTO.setStatus(DriverJobStatus.PROCESSING.getCode());

            // ???????????????????????????
            PageResponse<DriverJobDTO> resultForProcessing = driverJobFeign.findByPage(driverJobDTO);
            if (resultForProcessing == null || CollectionUtils.isEmpty(resultForProcessing.getItems())) {
                // ??????????????????  ?????????????????????
                if (page == 1) { // ?????????????????????????????????
                    cargoTranTaskDTOS.get(0).setDisable(true);
                }
            }


            log.info("?????????????????????:{}", cargoTranTaskDTOS);

            return Result.ok().put("data", PageResponse.<CargoTranTaskDTO>builder()
                    .counts(result.getCounts()).page(page).pagesize(pagesize).pages(result.getPages())
                    .items(cargoTranTaskDTOS).build());
        }else{
            return Result.ok().put("data", PageResponse.<CargoTranTaskDTO>builder()
                    .counts(0L).page(page).pagesize(pagesize).pages(0L)
                    .items(Lists.newArrayList()).build());
        }

    }

    @SneakyThrows
    @ApiOperation(value = "????????????")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "page", value = "????????????", required = true),
            @ApiImplicitParam(name = "pagesize", value = "????????????", required = true),
            @ApiImplicitParam(name = "keyword", value = "????????????", required = false)
    })
    @ResponseBody
    @GetMapping("history")
    public Result history(Integer page, Integer pagesize, String keyword) {

        //  ????????????id  ???????????????
        String driverId = RequestContext.getUserId();

        DriverJobDTO driverJobDTO = new DriverJobDTO();
        driverJobDTO.setPage(page);
        driverJobDTO.setPageSize(pagesize);
        driverJobDTO.setStatus(DriverJobStatus.COMPLETED.getCode());
        driverJobDTO.setDriverId(driverId);
        driverJobDTO.setId(keyword);

        log.info("???????????? PARAMS:{}", driverJobDTO);
        PageResponse<DriverJobDTO> result = driverJobFeign.findByPage(driverJobDTO);
        log.info("???????????? RESULT:{}", result.getItems());

        List<CargoTranTaskDTO> cargoTranTaskDTOS = result.getItems().stream().map(item -> CargoTranTaskDTO.builder()
                .taskNo(item.getTaskTransportId())
                .actualArrivalTime(item.getActualArrivalTime())
                .status(item.getStatus())
                .id(item.getId())
                .build()).collect(Collectors.toList());
        log.info("???????????? ?????????{}", cargoTranTaskDTOS);
        return Result.ok().put("data", PageResponse.<CargoTranTaskDTO>builder()
                .counts(result.getCounts()).page(page).pagesize(pagesize).pages(result.getPages())
                .items(cargoTranTaskDTOS).build());

    }

    @SneakyThrows
    @ApiOperation(value = "????????????")
    @ResponseBody
    @GetMapping("onTheWay")
    public Result onTheWay() {

        //  ????????????id  ???????????????
        String driverId = RequestContext.getUserId();

        DriverJobDTO driverJobDTO = new DriverJobDTO();
        driverJobDTO.setPage(1);
        driverJobDTO.setPageSize(1);
        driverJobDTO.setStatus(DriverJobStatus.PROCESSING.getCode());
        driverJobDTO.setDriverId(driverId);

        log.info("???????????? PARAMS:{}", driverJobDTO);
        PageResponse<DriverJobDTO> result = driverJobFeign.findByPage(driverJobDTO);
        log.info("???????????? RESULT:{}", result.getItems());

        // ?????????????????????
        if (result.getCounts() <= 0) {
            return Result.ok().put("data", new CargoTranTaskDTO());
        }
        DriverJobDTO driverJob = result.getItems().get(0);

        Map<String, TaskTransportDTO> transportTaskDTOMap = new HashMap<>();
        TaskTransportDTO transportTaskDTO = transportTaskFeign.findById(driverJob.getTaskTransportId());
        transportTaskDTOMap.put(transportTaskDTO.getId(), transportTaskDTO);

        //????????????
        Set<String> agencySet = new HashSet<>();
        agencySet.add(driverJob.getStartAgencyId());
        agencySet.add(driverJob.getEndAgencyId());
        CompletableFuture<List<Org>> agencyListFuture = PdCompletableFuture.agencyListFuture(orgApi, null, agencySet, null);
        List<Org> agencyList = agencyListFuture.get();

        // ????????????
        Set<Long> areaSet = new HashSet<>();
        areaSet.addAll(agencyList.stream().map(item -> item.getProvinceId()).collect(Collectors.toSet()));
        areaSet.addAll(agencyList.stream().map(item -> item.getCityId()).collect(Collectors.toSet()));
        areaSet.addAll(agencyList.stream().map(item -> item.getCountyId()).collect(Collectors.toSet()));
        CompletableFuture<Map> areaMapFuture = PdCompletableFuture.areaMapFuture(areaApi, null, areaSet);

        Set<Long> userSet = agencyList.stream().map(item -> item.getManagerId()).collect(Collectors.toSet());
        CompletableFuture<Map> userMapFuture = PdCompletableFuture.userMapFuture(userApi, userSet, null, null, null);


        Map areaMap = areaMapFuture.get();
        Map userMap = userMapFuture.get();

        Map<String, AgencyVo> agencyMap = agencyList.stream().map(item -> {
            AgencyVo agencyVo = new AgencyVo();
            BeanUtils.copyProperties(item, agencyVo);
            agencyVo.setId(item.getId().toString());
            agencyVo.setProvince((AreaSimpleVo) areaMap.get(item.getProvinceId()));
            agencyVo.setCity((AreaSimpleVo) areaMap.get(item.getCityId()));
            agencyVo.setCounty((AreaSimpleVo) areaMap.get(item.getCountyId()));
            agencyVo.setManager((SysUserVo) userMap.get(item.getManagerId()));
            return agencyVo;
        }).collect(Collectors.toMap(AgencyVo::getId, vo -> vo));

        CargoTranTaskDTO cargoTranTaskDTO = new CargoTranTaskDTO(driverJob, transportTaskDTOMap, agencyMap);
        return Result.ok().put("data", cargoTranTaskDTO);
    }

    @SneakyThrows
    @ApiOperation(value = "??????????????????")
    @ApiImplicitParam(name = "id", value = "??????", required = true)
    @ResponseBody
    @GetMapping("detail")
    public Result detail(String id) {
        DriverJobDTO driverJob = driverJobFeign.findById(id);

        Map<String, TaskTransportDTO> transportTaskDTOMap = new HashMap<>();
        TaskTransportDTO transportTaskDTO = transportTaskFeign.findById(driverJob.getTaskTransportId());
        transportTaskDTOMap.put(transportTaskDTO.getId(), transportTaskDTO);

        //????????????
        Set<String> agencySet = new HashSet<>();
        agencySet.add(driverJob.getStartAgencyId());
        agencySet.add(driverJob.getEndAgencyId());
        CompletableFuture<List<Org>> agencyListFuture = PdCompletableFuture.agencyListFuture(orgApi, null, agencySet, null);
        List<Org> agencyList = agencyListFuture.get();

        // ????????????
        Set<Long> areaSet = new HashSet<>();
        areaSet.addAll(agencyList.stream().map(item -> item.getProvinceId()).collect(Collectors.toSet()));
        areaSet.addAll(agencyList.stream().map(item -> item.getCityId()).collect(Collectors.toSet()));
        areaSet.addAll(agencyList.stream().map(item -> item.getCountyId()).collect(Collectors.toSet()));
        CompletableFuture<Map> areaMapFuture = PdCompletableFuture.areaMapFuture(areaApi, null, areaSet);

        Set<Long> userSet = agencyList.stream().map(item -> item.getManagerId()).collect(Collectors.toSet());
        CompletableFuture<Map> userMapFuture = PdCompletableFuture.userMapFuture(userApi, userSet, null, null, null);


        Map areaMap = areaMapFuture.get();
        Map userMap = userMapFuture.get();

        Map<String, AgencyVo> agencyMap = agencyList.stream().map(item -> {
            AgencyVo agencyVo = new AgencyVo();
            BeanUtils.copyProperties(item, agencyVo);
            agencyVo.setId(item.getId().toString());
            agencyVo.setProvince((AreaSimpleVo) areaMap.get(item.getProvinceId()));
            agencyVo.setCity((AreaSimpleVo) areaMap.get(item.getCityId()));
            agencyVo.setCounty((AreaSimpleVo) areaMap.get(item.getCountyId()));
            agencyVo.setManager((SysUserVo) userMap.get(item.getManagerId()));
            return agencyVo;
        }).collect(Collectors.toMap(AgencyVo::getId, vo -> vo));

        CargoTranTaskDTO cargoTranTaskDTO = new CargoTranTaskDTO(driverJob, transportTaskDTOMap, agencyMap);
        return Result.ok().put("data", cargoTranTaskDTO);
    }

    @SneakyThrows
    @ApiOperation(value = "??????????????????(?????????)")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "??????", required = true),
            @ApiImplicitParam(name = "keyword", value = "????????????", required = false)
    })
    @ResponseBody
    @GetMapping("orders")
    public Result orders(String keyword, String id) {
        log.info("?????????????????????{} {}", keyword, id);
        if (StringUtils.isBlank(id)) {
            return Result.ok().put("data", PageResponse.<String>builder()
                    .counts(0L).page(0).pagesize(0).pages(0L).build());
        }

        DriverJobDTO driverJob = driverJobFeign.findById(id);
        log.info("?????????????????? ??????????????? {}", driverJob);
        if (driverJob == null) {
            return Result.ok().put("data", PageResponse.<String>builder()
                    .counts(0L).page(0).pagesize(0).pages(0L).build());
        }
        TaskTransportDTO transportTaskDTO = transportTaskFeign.findById(driverJob.getTaskTransportId());
        List<String> result = transportTaskDTO.getTransportOrderIds();
        log.info("?????????????????? ??????????????? {}", transportTaskDTO);

        if (StringUtils.isNotBlank(keyword)) {
            result = result.stream().filter(item -> item.contains(keyword)).collect(Collectors.toList());
        }

        log.info("?????????????????? ??????????????? {}", result);

        return Result.ok().put("data", PageResponse.<String>builder()
                .counts(Long.valueOf(transportTaskDTO.getTransportOrderCount())).page(1).pagesize(transportTaskDTO.getTransportOrderCount()).pages(1L)
                .items(result).build());
    }

    @ApiOperation(value = "??????")
    @ResponseBody
    @PutMapping("pickUp")
    public Result pickUp(@RequestBody TaskTransportDTO taskTransportDTO) {

        //  ????????????id  ???????????????
        String driverId = RequestContext.getUserId();

        DriverJobDTO driverJobDTO = new DriverJobDTO();
        driverJobDTO.setPage(1);
        driverJobDTO.setPageSize(1);
        driverJobDTO.setStatus(DriverJobStatus.PROCESSING.getCode());
        driverJobDTO.setDriverId(driverId);

        // ???????????????????????????
        PageResponse<DriverJobDTO> result = driverJobFeign.findByPage(driverJobDTO);
        if (result != null && !CollectionUtils.isEmpty(result.getItems())) {
            Result.error(ErrorCode.ONTHEWAY, "???????????????????????????????????????");
        }

        DriverJobDTO driverJob = driverJobFeign.findById(taskTransportDTO.getId());
        String taskTransportId = driverJob.getTaskTransportId();
        String startAgencyId = driverJob.getStartAgencyId();
        R<Org> orgR = orgApi.get(Long.parseLong(startAgencyId));
        Org org = orgR.getData();
        // ??????????????????
        TaskTransportDTO taskTransport = transportTaskFeign.findById(taskTransportId);
        // ?????????????????????
        driverJobDTO = new DriverJobDTO();
        driverJobDTO.setStatus(DriverJobStatus.PROCESSING.getCode());
        driverJobDTO.setStartHandover(org.getManager());
        //driverJobDTO.setActualArrivalTime(LocalDateTime.now());
        driverJobFeign.updateById(driverJob.getId(), driverJobDTO);
        // ?????????????????????
        TaskTransportDTO taskTransportUpdate = new TaskTransportDTO();
        taskTransportUpdate.setIds(taskTransport.getIds());
        taskTransportUpdate.setTransportOrderIds(taskTransport.getTransportOrderIds());
        taskTransportUpdate.setStatus(TransportTaskStatus.PROCESSING.getCode());
        taskTransportUpdate.setCargoPicture(taskTransportDTO.getCargoPicture());
        taskTransportUpdate.setCargoPickUpPicture(taskTransportDTO.getCargoPickUpPicture());
        taskTransportUpdate.setDeliveryLatitude(taskTransportDTO.getDeliveryLatitude());
        taskTransportUpdate.setDeliveryLongitude(taskTransportDTO.getDeliveryLongitude());
        taskTransportUpdate.setActualPickUpGoodsTime(LocalDateTime.now());
        taskTransportUpdate.setActualDepartureTime(taskTransportUpdate.getActualPickUpGoodsTime());

        transportTaskFeign.updateById(taskTransportId, taskTransportUpdate);


        List<String> transportOrderIds = taskTransport.getTransportOrderIds();

        // ????????????
        for (String transportOrderId : transportOrderIds) {
            TransportOrderDTO transportOrderDTO = new TransportOrderDTO();
            transportOrderDTO.setStatus(TransportOrderStatus.LOADED.getCode());
            transportOrderFeign.updateById(transportOrderId, transportOrderDTO);
            log.info("??????????????????: {} {}", transportOrderId, transportOrderDTO);
        }

        // ????????????
        for (String transportOrderId : transportOrderIds) {
            // ????????????id
            TransportOrderDTO transportOrder = transportOrderFeign.findById(transportOrderId);
            String orderId = transportOrder.getOrderId();
            // ??????????????????
            OrderDTO orderDTO = new OrderDTO();
            orderDTO.setCurrentAgencyId(OrderStatus.IN_TRANSIT.getValue());
            orderDTO.setStatus(OrderStatus.IN_TRANSIT.getCode());
            orderFeign.updateById(orderId, orderDTO);
            log.info("?????????????????????????????????: {} {}", orderId, orderDTO);
        }
        return Result.ok();
    }

    @ApiOperation(value = "??????")
    @ResponseBody
    @PutMapping("finish")
    public Result finish(@RequestBody TaskTransportDTO taskTransportDTO) {

        DriverJobDTO driverJob = driverJobFeign.findById(taskTransportDTO.getId());
        String taskTransportId = driverJob.getTaskTransportId();
        String endAgencyId = driverJob.getEndAgencyId();
        R<Org> orgR = orgApi.get(Long.parseLong(endAgencyId));
        Org org = orgR.getData();
        // ??????????????????
        TaskTransportDTO taskTransport = transportTaskFeign.findById(taskTransportId);
        // ?????????????????????
        DriverJobDTO driverJobDTO = new DriverJobDTO();
        driverJobDTO.setStatus(DriverJobStatus.COMPLETED.getCode());
        driverJobDTO.setFinishHandover(org.getManager());
        driverJobDTO.setActualArrivalTime(LocalDateTime.now());
        driverJobFeign.updateById(driverJob.getId(), driverJobDTO);
        // ?????????????????????
        TaskTransportDTO taskTransportUpdate = new TaskTransportDTO();
        taskTransportUpdate.setIds(taskTransport.getIds());
        taskTransportUpdate.setTransportOrderIds(taskTransport.getTransportOrderIds());
        taskTransportUpdate.setStatus(TransportTaskStatus.COMPLETED.getCode());
        taskTransportUpdate.setTransportCertificate(taskTransportDTO.getTransportCertificate());
        taskTransportUpdate.setDeliverPicture(taskTransportDTO.getDeliverPicture());
        taskTransportUpdate.setDeliverLatitude(taskTransportDTO.getDeliverLatitude());
        taskTransportUpdate.setDeliverLongitude(taskTransportDTO.getDeliverLongitude());
        taskTransportUpdate.setActualArrivalTime(driverJobDTO.getActualArrivalTime());
        taskTransportUpdate.setActualDeliveryTime(taskTransportUpdate.getActualDeliveryTime());

        transportTaskFeign.updateById(taskTransportId, taskTransportUpdate);

        log.info("???????????????{}.???????????????????????????{}", endAgencyId, taskTransportUpdate);

        List<String> transportOrderIds = taskTransport.getTransportOrderIds();

        // ?????????????????????????????????  ??????????????? ??????????????????
        List<AgencyScopeDto> agencyScope = agencyScopeFeign.findAllAgencyScope(null, endAgencyId, null, null);
        // ????????????????????????
        List<String> areaIds = agencyScope.stream().map(item -> item.getAreaId()).collect(Collectors.toList());
        log.info("???????????????{} ???????????????{}", endAgencyId, areaIds);
        // ????????????
        for (String transportOrderId : transportOrderIds) {
            // ????????????
            TransportOrderDTO transportOrderDTO = new TransportOrderDTO();

            // ????????????id
            TransportOrderDTO transportOrder = transportOrderFeign.findById(transportOrderId);
            String orderId = transportOrder.getOrderId();

            // ??????????????????
            OrderDTO orderDTO = orderFeign.findById(orderId);
            OrderDTO orderDTOUpdate = new OrderDTO();
            orderDTOUpdate.setCurrentAgencyId(taskTransport.getEndAgencyId());
            //????????????????????????
            OrderLocationDto orderLocationDto = orderFeign.selectByOrderId(orderId);
            boolean isFinal = false;
            if(orderLocationDto==null){
                if(areaIds.contains(orderDTO.getReceiverCountyId())){
                    isFinal=true;
                }
            }else{
                if(StringUtils.equals(endAgencyId,orderLocationDto.getReceiveAgentId())){
                    isFinal=true;
                }
            }
//            if (areaIds.contains(orderDTO.getReceiverCountyId())) {
            if(isFinal){
                log.info("???????????????????????????{},{}", transportOrderId, orderId);
                // ???????????????
                transportOrderDTO.setStatus(TransportOrderStatus.ARRIVED_END.getCode());
                orderDTOUpdate.setStatus(OrderStatus.OUTLETS_EX_WAREHOUSE.getCode());
                // ???????????????????????????
                String courierId = null;

                courierId = getCourierId(orderDTO);
                if(StringUtils.isBlank(courierId)){
                    //??????id
                    Long stationId = StaticStation.COURIER_ID;
                    R<List<User>> userRs = userApi.list(null, stationId, null, Long.valueOf(endAgencyId));
                    if (userRs.getData() != null && userRs.getData().size() > 0) {
                        User user = userRs.getData().get(0);
                        courierId = user.getId().toString();
                    }
                }

                log.info("???????????????????????????:{},?????????:{}", endAgencyId, courierId);

                TaskPickupDispatchDTO pickupDispatchTaskDTO = new TaskPickupDispatchDTO();
                pickupDispatchTaskDTO.setOrderId(orderDTO.getId());
                pickupDispatchTaskDTO.setTaskType(PickupDispatchTaskType.DISPATCH.getCode());
                pickupDispatchTaskDTO.setStatus(PickupDispatchTaskStatus.PENDING.getCode());
                pickupDispatchTaskDTO.setAssignedStatus(StringUtils.isNotBlank(courierId) ? PickupDispatchTaskAssignedStatus.DISTRIBUTED.getCode() : PickupDispatchTaskAssignedStatus.MANUAL_DISTRIBUTED.getCode());
                pickupDispatchTaskDTO.setCreateTime(LocalDateTime.now());
                pickupDispatchTaskDTO.setAgencyId(endAgencyId);
                pickupDispatchTaskDTO.setCourierId(courierId);
                pickupDispatchTaskDTO.setEstimatedStartTime(LocalDateTime.now());
                pickupDispatchTaskDTO.setEstimatedEndTime(LocalDateTime.now().plusHours(1));
                pickupDispatchTaskFeign.save(pickupDispatchTaskDTO);
                log.info("????????????????????????????????????{}", pickupDispatchTaskDTO);
            } else {
                transportOrderDTO.setStatus(TransportOrderStatus.ARRIVED.getCode());
                orderDTOUpdate.setStatus(OrderStatus.IN_TRANSIT.getCode());
            }
            transportOrderFeign.updateById(transportOrderId, transportOrderDTO);
            log.info("??????????????????: {} {}", transportOrderId, transportOrderDTO);
            orderFeign.updateById(orderId, orderDTOUpdate);
            log.info("?????????????????????????????????: {} {}", orderId, orderDTOUpdate);
        }

        return Result.ok();
    }

    private String getCourierId(OrderDTO orderDTO) {
        List<CourierScopeDto> courierScopeDtoList = courierScopeFeign.findAllCourierScope(orderDTO.getSenderCountyId(), null);
        if(courierScopeDtoList==null || courierScopeDtoList.size()==0){
            return "";
        }
        String location = EntCoordSyncJob.getCoordinate(orderDTO.getReceiverAddress());
        Result res = calcuateCourier(location, courierScopeDtoList);
        if (!res.get("code").toString().equals("0")) {
            return "";
        }
        return res.get("userId").toString();
    }

    private Result calcuateCourier(String location, List<CourierScopeDto> courierScopeDtoList) {
        try{
            Map courierMap = Maps.newHashMap();
            for (CourierScopeDto courierScopeDto : courierScopeDtoList) {
                List<List<Map>> mutiPoints = courierScopeDto.getMutiPoints();
                for (List<Map> list : mutiPoints) {
                    for (Map map :list) {
                        String point = getPoint(map);
                        Double distance = EntCoordSyncJob.getDistance(location,point);
                        courierMap.put(courierScopeDto.getUserId(),distance);
                    }
                }
            }
            //??????map????????????????????????
            List<Map.Entry<String, Double>> list = new ArrayList(courierMap.entrySet());
            list.sort(Comparator.comparingDouble(Map.Entry::getValue));
            String userId = list.get(0).getKey();
            return Result.ok().put("userId",userId);
        }catch (Exception e){
            e.printStackTrace();
            return Result.error(5000,"?????????????????????????????????");
        }
    }
    private String getPoint(Map pointMap) {
        String lng = pointMap.getOrDefault("lng", "").toString();
        String lat = pointMap.getOrDefault("lat", "").toString();
        return lng + "," + lat;
    }

}
