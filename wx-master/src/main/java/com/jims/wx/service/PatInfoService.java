package com.jims.wx.service;

import com.google.inject.Inject;
import com.jims.wx.entity.*;
import com.jims.wx.expection.ErrorException;
import com.jims.wx.facade.*;
import com.jims.wx.vo.PatInfoVo;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.*;

import com.google.inject.Inject;
import com.jims.wx.expection.ErrorException;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

/**
 * Created by chenxy on 2016/4/17.
 */
@Path("pat-info")
@Produces("application/json")
public class PatInfoService {

    private PatInfoFacade patInfoFacade;

    private AppUserFacade appUserFacade;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private PatVsUserFacade patVsUserFacade;
    private PatMasterIndexFacade patMasterIndexFacade;
    private MedicalCardMemoFacade medicalCardMemoFacade;

    @Inject
    public PatInfoService(PatInfoFacade patInfoFacade, AppUserFacade appUserFacade, HttpServletRequest request, HttpServletResponse response, PatVsUserFacade patVsUserFacade, PatMasterIndexFacade patMasterIndexFacade, MedicalCardMemoFacade medicalCardMemoFacade) {
        this.appUserFacade = appUserFacade;
        this.patInfoFacade = patInfoFacade;
        this.request = request;
        this.response = response;
        this.patVsUserFacade = patVsUserFacade;
        this.patMasterIndexFacade = patMasterIndexFacade;
        this.medicalCardMemoFacade = medicalCardMemoFacade;
    }

    /**
     * 通过openId 查询绑定患者的list
     * @param openId
     * @return
     */
    @GET
    @Path("list")
    public List<PatInfo> getList(@QueryParam("openId") String openId) {
        if(openId==null || "".equals(openId)){
            throw new IllegalArgumentException("openId为空");
        }
        AppUser appUser = appUserFacade.findAppUserByOpenId(openId);
        return patVsUserFacade.findPatInfosByAppUserId(appUser.getId());
    }

    /**
     * 保存患者信息
      * @param openId
     * @param name
     * @param idCard
     * @param cellphone
     */
    @GET
    @Path("save")
    public void save(@QueryParam("openId") String openId, @QueryParam("name") String name, @QueryParam("idCard") String idCard, @QueryParam("cellphone") String cellphone, @QueryParam("patId") String patId) {
        if(openId==null || openId.trim().equals("")){
            try {
                response.sendRedirect("/views/his/public/user-bangker-failed.html");
            } catch (IOException e) {
                e.printStackTrace();
            }
            throw new IllegalArgumentException("openId为空，请重试！");
         }
        PatInfo patInfo = null;
        String patientId = null;
        try {
             if (patId != null && !"".equals(patId)) {
                patInfo = patInfoFacade.findById(patId);
                patInfo.setCellphone(cellphone);
                patInfo.setIdCard(idCard);
                patInfo.setName(name);
                patInfo.setPatientId(patientId);
                patInfo = patInfoFacade.save(patInfo);
                response.sendRedirect("/views/his/public/user-bangker-success.html?patId=" + patId+"&openId="+openId);
              }else {
                 AppUser appUser = appUserFacade.findAppUserByOpenId(openId);
                 if(appUser==null){
                     response.sendRedirect("/views/his/public/user-bangker-failed.html");
                     throw new IllegalArgumentException("appUser为空，请重试！");
                 }
                 //通过一卡通卡号查询病人Id
                 patientId = patMasterIndexFacade.checkIdCard2(idCard);
                 if(patientId==null || "".equals(patientId)){
                     response.sendRedirect("/views/his/public/user-bangker-failed.html");
                     throw new IllegalArgumentException("一卡通号无效，请重试！");
                 }
                 /**
                 * 查询之前是否绑定次idCard
                 */
                Boolean isBangker = this.patVsUserFacade.findIsBangker(idCard, openId);
                if (isBangker) {
                    response.sendRedirect("/views/his/public/user-bangker-failed.html");
                } else {
                    /**
                     * 查询之前是否有绑定 但是已经被删掉
                     * 如果有直接把 flag=1
                     */
                    PatInfo p=patInfoFacade.findByFlag(idCard);
                    if(p!=null&&!"".equals(p)){
                        patInfo=p;
                    }else{
                        patInfo = new PatInfo();
                    }
                    patInfo.setCellphone(cellphone);
                    patInfo.setIdCard(idCard);
                    patInfo.setName(name);
                    patInfo.setPatientId(patientId);
                    patInfo = patInfoFacade.save(patInfo);
                    if (StringUtils.isNotBlank(openId)) {
                        if(p!=null&&!"".equals(p)){// flag=1

                        }else{
                            PatVsUser patVsUser = new PatVsUser();
                            patVsUser.setAppUser(appUser);
                            patVsUser.setPatInfo(patInfo);
                            appUserFacade.savePatVsUser(patVsUser);
                        }
                         /**
                         * 查询用户是否是第一次绑定
                         * 如果是第一次绑定的话，那么将此
                          * patId 放入appUser表中
                         * 否则就不放
                         */
                        boolean isFirstBangker = appUserFacade.judgeIsFirstBangker(openId);
                        if (isFirstBangker) {//
                            //将patId放入appUser
                            AppUser appUser1 = appUserFacade.findAppUserByOpenId(openId);
                            appUser1.setPatId(patInfo.getId());
                            appUserFacade.saveAppUser(appUser1);
                        }
                    }

                }
                //为查看详情做准备
                response.sendRedirect("/views/his/public/user-bangker-success.html?patId=" + patInfo.getId()+"&openId="+openId);
            }
        } catch (Exception e) {
            e.printStackTrace();
            try {
                response.sendRedirect("/views/his/public/user-bangker-failed.html");
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

    /**
     * 通过用户的openId 查找我的信息
     *
     * @param openId
     * @return
     */
    @POST
    @Path("find-info-by-open-id")
    public PatInfo findPatInfoByOpenId(@QueryParam("openId") String openId) {
        if(openId==null || "".equals(openId)){
            throw new IllegalArgumentException("openId为空!");
        }
        AppUser appUser = appUserFacade.findAppUserByOpenId(openId);
        String patId = appUser.getPatId();
        if(patId==null || "".equals(patId)){
            throw new IllegalArgumentException("此appUser 的patid为空，openid="+appUser.getOpenId());
        }
        PatInfo patInfo = patInfoFacade.findById(patId);
        return patInfo;
    }

    /**
     * patId 查找patInfo
     *
     * @param patId
     * @return
     */
    @POST
    @Path("view")
    public PatInfo view(@QueryParam("patId") String patId) {
        if(patId==null || "".equals(patId)){
            throw new IllegalArgumentException("patId为空！");
        }
        PatInfo patInfo = patInfoFacade.findById(patId);
        return patInfo;
    }

    @GET
    @Path("find-all")
    @Produces(MediaType.APPLICATION_JSON)
    public List<PatInfoVo> listByOpenId(@QueryParam("openId") String openId) {
        if(openId==null ||"".equals(openId)){
            throw new IllegalArgumentException("openId为空!");
        }
        return patInfoFacade.findByOpenId(openId);
    }

    /**
     * 修改默认绑定的用户
     * @param patId
     * @param openId
     * @param flag
     * @return
     */
    @GET
    @Path("update-pat-id")
    public String updatePatId(@QueryParam("patId") String patId, @QueryParam("openId") String openId,@QueryParam("flag") String flag) {
        try {
            if(openId==null || "".equals(openId) || patId==null || "".equals(patId)){
                throw new IllegalArgumentException("参数非法！openId="+openId+"&patid="+patId);
            }
            AppUser appUser = appUserFacade.findAppUserByOpenId(openId);
            appUser.setPatId(patId);
            appUserFacade.saveAppUser(appUser);
            if(flag!=null&&!"".equals(flag)){
                return "success";
            }else{
                response.sendRedirect("/views/his/public/app-op-success.html");
            }
         } catch (Exception e) {
            e.printStackTrace();
            try {
                if(flag!=null&&!"".equals(flag)){
                    return "error";
                }else{
                    response.sendRedirect("/views/his/public/user-bangker-failed.html");
                }
             } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
        return "";
    }

    /**
     * 修改patInfo 信息
     *
     * @param patInfo
     * @return
     */
    @POST
    @Path("update")
    public PatInfo update(PatInfo patInfo) {
        patInfo = patInfoFacade.save(patInfo);
        return patInfo;
    }

    /**
     * 删除 信息
     *
     * @param patId
     * @return
     */
    @POST
    @Path("delete")
    public PatInfo delete(@QueryParam("patId") String patId) {
        try {
            if(patId==null || "".equals(patId)){
                throw new IllegalArgumentException("patId为空！");
            }
            PatInfo patInfo = patInfoFacade.findById(patId);
            patInfoFacade.deleteByObject(patInfo);
            return patInfo;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    //    check-idCard?idCard="+idCard,\
//    @POST
//    @Path("check-idCard")
//    public Map<String, Object> checkCard(@QueryParam("idCard") String idCard) {
//        Map<String, Object> map = new HashMap<String, Object>();
////        map=patMasterIndexFacade.checkIdCard(idCard);
//        return null;
//    }
}

