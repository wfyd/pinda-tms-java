package com.itheima.pinda.controller;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itheima.pinda.DTO.angency.AgencyScopeDto;
import com.itheima.pinda.DTO.base.GoodsTypeDto;
import com.itheima.pinda.DTO.truck.TruckTypeDto;
import com.itheima.pinda.DTO.user.CourierScopeDto;
import com.itheima.pinda.authority.api.AreaApi;
import com.itheima.pinda.authority.api.OrgApi;
import com.itheima.pinda.authority.api.UserApi;
import com.itheima.pinda.authority.entity.auth.User;
import com.itheima.pinda.authority.entity.common.Area;
import com.itheima.pinda.authority.enumeration.common.StaticStation;
import com.itheima.pinda.base.R;
import com.itheima.pinda.common.utils.EntCoordSyncJob;
import com.itheima.pinda.common.utils.PageResponse;
import com.itheima.pinda.common.utils.Result;
import com.itheima.pinda.feign.agency.AgencyScopeFeign;
import com.itheima.pinda.feign.common.GoodsTypeFeign;
import com.itheima.pinda.feign.truck.TruckTypeFeign;
import com.itheima.pinda.feign.user.CourierScopeFeign;
import com.itheima.pinda.future.PdCompletableFuture;
import com.itheima.pinda.util.BeanUtil;
import com.itheima.pinda.vo.base.AreaSimpleVo;
import com.itheima.pinda.vo.base.angency.AgencyScopeVo;
import com.itheima.pinda.vo.base.businessHall.CourierScopeVo;
import com.itheima.pinda.vo.base.businessHall.GoodsTypeVo;
import com.itheima.pinda.vo.base.transforCenter.business.TruckTypeVo;
import com.itheima.pinda.vo.base.userCenter.SysUserVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.extern.java.Log;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * ????????????
 */
@RestController
@RequestMapping("business-hall")
@Api(tags = "????????????")
@Log
public class BusinessHallController {
    @Autowired
    private GoodsTypeFeign goodsTypeFeign;
    @Autowired
    private TruckTypeFeign truckTypeFeign;
    @Autowired
    private OrgApi orgApi;
    @Autowired
    private CourierScopeFeign courierScopeFeign;
    @Autowired
    private AreaApi areaApi;
    @Autowired
    private AgencyScopeFeign agencyScopeFeign;
    @Autowired
    private UserApi userApi;

    @ApiOperation(value = "??????????????????")
    @PostMapping("/goodsType")
    public GoodsTypeVo saveGoodsType(@RequestBody GoodsTypeVo vo) {
        GoodsTypeDto dto = new GoodsTypeDto();
        BeanUtils.copyProperties(vo, dto);
        //??????????????????????????????
        if (vo.getTruckTypes() != null) {
            dto.setTruckTypeIds(vo.getTruckTypes().stream().map(truckTypeVo -> truckTypeVo.getId()).collect(Collectors.toList()));
        }
        GoodsTypeDto resultDto = goodsTypeFeign.saveGoodsType(dto);
        BeanUtils.copyProperties(resultDto, vo);
        return vo;
    }

    @ApiOperation(value = "??????????????????")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "????????????id", required = true, example = "1", paramType = "{path}")
    })
    @PutMapping("/goodsType/{id}")
    public GoodsTypeVo updateGoodsType(@PathVariable(name = "id") String id, @RequestBody GoodsTypeVo vo) {
        vo.setId(id);
        GoodsTypeDto dto = new GoodsTypeDto();
        BeanUtils.copyProperties(vo, dto);
        //??????????????????????????????
        if (vo.getTruckTypes() != null) {
            dto.setTruckTypeIds(vo.getTruckTypes().stream().map(truckTypeVo -> truckTypeVo.getId()).collect(Collectors.toList()));
        }
        GoodsTypeDto resultDto = goodsTypeFeign.update(id, dto);
        BeanUtils.copyProperties(resultDto, vo);
        return vo;
    }

    @ApiOperation(value = "??????????????????????????????")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "page", value = "??????", required = true, example = "1"),
            @ApiImplicitParam(name = "pageSize", value = "?????????", required = true, example = "10"),
            @ApiImplicitParam(name = "name", value = "??????????????????"),
            @ApiImplicitParam(name = "truckTypeId", value = "????????????Id"),
            @ApiImplicitParam(name = "truckTypeName", value = "??????????????????")
    })
    @GetMapping("/goodsType/page")
    public PageResponse<GoodsTypeVo> findGoodsTypeByPage(@RequestParam(name = "page") Integer page,
                                                         @RequestParam(name = "pageSize") Integer pageSize,
                                                         @RequestParam(name = "name", required = false) String name,
                                                         @RequestParam(name = "truckTypeId", required = false) String truckTypeId,
                                                         @RequestParam(name = "truckTypeName", required = false) String truckTypeName) {
        PageResponse<GoodsTypeDto> goodsTypePage = goodsTypeFeign.findByPage(page, pageSize, name, truckTypeId, truckTypeName);
        //????????????
        List<GoodsTypeDto> goodsTypeDtoList = goodsTypePage.getItems();
        Set<String> truckTypeSet = new HashSet<>();
        goodsTypeDtoList.forEach(goodsTypeDto -> {
            if (goodsTypeDto.getTruckTypeIds() != null) {
                truckTypeSet.addAll(goodsTypeDto.getTruckTypeIds());
            }
        });
        CompletableFuture<Map> truckTypeFuture = PdCompletableFuture.truckTypeMapFuture(truckTypeFeign, truckTypeSet);
        CompletableFuture.allOf(truckTypeFuture).join();
        List<GoodsTypeVo> goodsTypeVoList = goodsTypeDtoList.stream().map(goodsTypeDto -> {
            GoodsTypeVo vo = new GoodsTypeVo();
            BeanUtils.copyProperties(goodsTypeDto, vo);
            try {
                if (goodsTypeDto.getTruckTypeIds() != null) {
                    List<TruckTypeVo> truckTypeVoList = new ArrayList<>();
                    for (String typeId : goodsTypeDto.getTruckTypeIds()) {
                        truckTypeVoList.add((TruckTypeVo) truckTypeFuture.get().get(typeId));
                    }
                    vo.setTruckTypes(truckTypeVoList);
                }
            } catch (Exception e) {
                // TODO: 2020/1/2 ????????????????????????????????????????????????????????????????????????????????????
                e.printStackTrace();
            }
            return vo;
        }).collect(Collectors.toList());
        return PageResponse.<GoodsTypeVo>builder().items(goodsTypeVoList).pagesize(pageSize).page(page).counts(goodsTypePage.getCounts()).pages(goodsTypePage.getPages()).build();
    }

    @ApiOperation(value = "????????????????????????")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "????????????id", required = true, example = "1", paramType = "{path}")
    })
    @GetMapping("/goodsType/{id}")
    public GoodsTypeVo findGoodsTypeById(@PathVariable(name = "id") String id) {
        GoodsTypeDto dto = goodsTypeFeign.fineById(id);
        GoodsTypeVo vo = new GoodsTypeVo();
        BeanUtils.copyProperties(dto, vo);
        if (dto.getTruckTypeIds() != null && dto.getTruckTypeIds().size() > 0) {
            CompletableFuture<List<TruckTypeDto>> truckTypeFuture = PdCompletableFuture.truckTypeListFuture(truckTypeFeign, dto.getTruckTypeIds());
            CompletableFuture.allOf(truckTypeFuture);
            try {
                vo.setTruckTypes(truckTypeFuture.get().stream().map(truckTypeDto -> {
                    TruckTypeVo truckTypeVo = new TruckTypeVo();
                    BeanUtils.copyProperties(truckTypeDto, truckTypeVo);
                    return truckTypeVo;
                }).collect(Collectors.toList()));
            } catch (Exception e) {
                // TODO: 2020/1/2 ????????????????????????????????????????????????????????????????????????????????????
                e.printStackTrace();
            }
        }
        return vo;
    }

    @ApiOperation(value = "??????????????????")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "????????????id", required = true, example = "1", paramType = "{path}")
    })
    @DeleteMapping("/goodsType/{id}")
    public Result deleteGoodsType(@PathVariable(name = "id") String id) {
        // TODO: 2020/1/7 ?????????????????????????????????????????????????????????????????????????????????????????????
        goodsTypeFeign.disable(id);
        return Result.ok();
    }

    @ApiOperation(value = "???????????????????????????")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "page", value = "??????", required = true, example = "1"),
            @ApiImplicitParam(name = "pageSize", value = "?????????", required = true, example = "10"),
            @ApiImplicitParam(name = "name", value = "???????????????"),
            @ApiImplicitParam(name = "mobile", value = "???????????????")
    })
    @GetMapping("/courier/page")
    public PageResponse<SysUserVo> findCourierByPage(@RequestParam(name = "page") Integer page,
                                                     @RequestParam(name = "pageSize") Integer pageSize,
                                                     @RequestParam(name = "name", required = false) String name,
                                                     @RequestParam(name = "mobile", required = false) String mobile) {
        R<Page<User>> result = userApi.page(page.longValue(), pageSize.longValue(), null, StaticStation.COURIER_ID, name, null, mobile);
        if (result.getIsSuccess() && result.getData() != null) {
            IPage<User> userPage = result.getData();
            //??????????????????
            List<SysUserVo> voList = userPage.getRecords().stream().map(user -> BeanUtil.parseUser2Vo(user, null, orgApi)).collect(Collectors.toList());
            return PageResponse.<SysUserVo>builder().items(voList).page(page).pagesize(pageSize).counts(userPage.getTotal()).pages(userPage.getPages()).build();
        }
        return PageResponse.<SysUserVo>builder().items(new ArrayList<>()).page(page).pagesize(pageSize).counts(0L).pages(0L).build();
    }

    @ApiOperation(value = "?????????????????????")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "?????????id", required = true, example = "1", paramType = "{path}")
    })
    @GetMapping("/courier/{id}")
    public SysUserVo findCourierById(@PathVariable(name = "id") String id) {
        R<User> result = userApi.get(Long.valueOf(id));
        SysUserVo vo = null;
        if (result.getIsSuccess() && result.getData() != null) {
            vo = BeanUtil.parseUser2Vo(result.getData(), null, orgApi);
        }
        return vo;
    }

    @ApiOperation(value = "???????????????????????????")
    @PostMapping("/courier/scope")
    public Result saveCourierScope(@RequestBody CourierScopeVo vo) {
        //????????????????????????????????????
        Result result = validateParam(vo);
        if (!"0".equals(result.get("code").toString())) {
            return result;
        }
        //????????????????????????
        CourierScopeDto deleteDto = new CourierScopeDto();
        deleteDto.setUserId(vo.getCourier().getUserId());
        courierScopeFeign.deleteCourierScope(deleteDto);
        //????????????
        List<CourierScopeDto> saveList = vo.getAreas().stream().map(areaVo -> {
            CourierScopeDto dto = new CourierScopeDto();
            dto.setAreaId(areaVo.getId());
            dto.setUserId(vo.getCourier().getUserId());
            dto.setMutiPoints(areaVo.getMutiPoints());
            return dto;
        }).collect(Collectors.toList());
        courierScopeFeign.batchSaveCourierScope(saveList);
        return Result.ok();
    }

    /**
     * ??????????????????????????????id
     *
     * @param vo
     * @return
     */
    private Result validateParam(CourierScopeVo vo) {
        List<AreaSimpleVo> areas = vo.getAreas();
        if (areas == null || areas.size() == 0) {
            return Result.error(5000, "??????????????????");
        } else {
            for (AreaSimpleVo areaSimpleVo : areas) {
                String adcodeOld = "";
                Area area = new Area();
                //???????????????????????????
                List<List<Map>> list = areaSimpleVo.getMutiPoints();
                if (list == null || list.size() == 0) {
                    return Result.error(5000, "??????????????????");
                } else {
                    for (List<Map> listMap : list) {
                        for(int i=0;i<listMap.size();i++){
                            Map pointMap = listMap.get(i);
                            String point = getPoint(pointMap);
                            Map map = EntCoordSyncJob.getLocationByPosition(point);
                            String adcode = map.getOrDefault("adcode", "").toString();
                            if (StringUtils.isBlank(adcode)) {
                                return Result.error(5000, "????????????????????????????????????");
                            } else {
                                if (!StringUtils.equals(adcode, adcodeOld) && i>0) {
                                    return Result.error(5000, "????????????????????????????????????????????????");
                                }
                                R<Area> r = areaApi.getByCode(adcode + "000000");
                                if (r.getIsSuccess() && r.getData() != null) {
                                    area = r.getData();
                                }
                            }
                            adcodeOld = adcode;
                        }

                    }
                }
                areaSimpleVo.setId(area.getId() + "");
                areaSimpleVo.setName(area.getName());
            }

        }
        return Result.ok();
    }

    private String getPoint(Map pointMap) {
        String lng = pointMap.getOrDefault("lng","").toString();
        String lat = pointMap.getOrDefault("lat","").toString();
        return lng+","+lat;
    }
    @ApiOperation(value = "???????????????????????????")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "?????????id", required = true, example = "1", paramType = "{path}")
    })
    @GetMapping("/courier/scope/{id}")
    public CourierScopeVo findAllCourierScope(@PathVariable(name = "id") String id) {
        List<CourierScopeDto> courierScopeDtoList = courierScopeFeign.findAllCourierScope(null, id);
        List<Long> areaIds = courierScopeDtoList.stream().map(dto -> Long.valueOf(dto.getAreaId())).collect(Collectors.toList());
        CourierScopeVo vo = new CourierScopeVo();
        R<User> result = userApi.get(Long.valueOf(id));
        User user = null;
        if (result.getIsSuccess() && result.getData() != null) {
            user = result.getData();
            vo.setCourier(BeanUtil.parseUser2Vo(user, null, null));
        }
        //??????????????????
        if (areaIds != null && areaIds.size() > 0) {
            List<Area> areaDtoList = areaApi.findAll(null, areaIds).getData();
            List<AreaSimpleVo> areas = areaDtoList.stream().map(BeanUtil::parseArea2Vo).collect(Collectors.toList());
            vo.setAreas(addMutiPoints(areas,courierScopeDtoList));
        }
        //????????????????????????
//        if (user != null && user.getOrgId() != null) {
//            List<Long> optionAreaIds = agencyScopeFeign.findAllAgencyScope(null, String.valueOf(user.getOrgId()), null, null).stream().map(areaScope -> Long.valueOf(areaScope.getAreaId())).collect(Collectors.toList());
//            if (optionAreaIds != null && optionAreaIds.size() > 0) {
//                List<Area> optionAreaDtoList = areaApi.findAll(null, optionAreaIds).getData();
//                if (optionAreaDtoList != null && optionAreaDtoList.size() > 0) {
//                    vo.setOptionAreas(optionAreaDtoList.stream().map(BeanUtil::parseArea2Vo).collect(Collectors.toList()));
//                }
//            }
//        }
        return vo;
    }

    private List<AreaSimpleVo> addMutiPoints(List<AreaSimpleVo> areas, List<CourierScopeDto> courierScopeDtoList) {
        for (AreaSimpleVo areaSimpleVo : areas) {
            for (CourierScopeDto courierScopeDto : courierScopeDtoList) {
                if (courierScopeDto.getAreaId().equals(areaSimpleVo.getId())){
                    areaSimpleVo.setMutiPoints(courierScopeDto.getMutiPoints());
                }
            }
        }
        return areas;
    }
}