package com.jims.wx.service;
import com.google.inject.Inject;
import com.jims.wx.entity.AppUser;
import com.jims.wx.entity.PatVsUser;
import com.jims.wx.expection.ErrorException;
import com.jims.wx.facade.AppUserFacade;
import com.jims.wx.facade.PatVsUserFacade;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import com.google.inject.Inject;
import com.jims.wx.entity.PatInfo;
import com.jims.wx.expection.ErrorException;
import com.jims.wx.facade.PatInfoFacade;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

/**
 * Created by chenxy on 2016/4/17.
 */
@Path("pat-info")
@Produces("application/json")
public class PatInfoService {

    private PatInfoFacade patInfoFacade;

    private AppUserFacade appUserFacade;
    private HttpServletRequest request ;
    private HttpServletResponse response ;
    private PatVsUserFacade patVsUserFacade;

    @Inject
    public PatInfoService(PatInfoFacade patInfoFacade,AppUserFacade appUserFacade,HttpServletRequest request,HttpServletResponse response,PatVsUserFacade patVsUserFacade) {
        this.appUserFacade = appUserFacade;
        this.patInfoFacade = patInfoFacade;
        this.request = request;
        this.response = response;
        this.patVsUserFacade = patVsUserFacade;
    }
    /**
     * 通过openId 查询患者的list
     * @param openId
     * @return
     */
    @GET
    @Path("list")
    public List<PatInfo> getList(@QueryParam("openId") String openId){
        List<AppUser> list=appUserFacade.findByOpenId(openId);
        String appUserId=list.get(0).getId();
        return patVsUserFacade.findPatInfosByAppUserId(appUserId);
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
    public void save(@QueryParam("openId")String openId,@QueryParam("name") String name,@QueryParam("idCard") String idCard,@QueryParam("cellphone") String cellphone) {
        try {
            /**
             * 查询之前是否绑定
             */
            Boolean isBangker=this.patVsUserFacade.findIsBangker(idCard,openId);
            if(isBangker){
                response.sendRedirect("/views/his/public/user-bangker-failed.html");
            }else{
                PatInfo patInfo = new PatInfo();
                patInfo.setCellphone(cellphone);
                patInfo.setIdCard(idCard);
                patInfo.setName(name);
                patInfo = patInfoFacade.save(patInfo);
                if (StringUtils.isNotBlank(openId)) {
                    AppUser appUser = appUserFacade.findAppUserByOpenId(openId);
                    PatVsUser patVsUser = new PatVsUser();
                    patVsUser.setAppUser(appUser);
                    patVsUser.setPatInfo(patInfo);
                    appUserFacade.savePatVsUser(patVsUser);
                }
                response.sendRedirect("/views/his/public/user-bangker-success.html");
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

 }
