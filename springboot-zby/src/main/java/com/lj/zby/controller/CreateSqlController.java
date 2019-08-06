package com.lj.zby.controller;

import com.alibaba.fastjson.JSONObject;
import com.lj.zby.entity.ResponeData;
import com.lj.zby.impl.CreateSqlFileServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

@RestController
public class CreateSqlController {

    @Autowired
    CreateSqlFileServiceImpl createSqlFileService;

    @GetMapping(value = "getSqlFile", produces = {MediaType.MULTIPART_FORM_DATA_VALUE})
    public void getSqlFile(HttpServletRequest request, HttpServletResponse response) {
        ResponeData responeData = new ResponeData();
        createSqlFileService.getSqlFile();
        FileInputStream ips = null;
        ServletOutputStream out = null;
        try {
            String url = "D:\\output.txt";
            File file = new File(url);
            ips = new FileInputStream(file);
            String newFileName = "output.txt";
            response.addHeader("Content-Disposition", "attachment; filename=\"" +
                    new String(newFileName.getBytes("UTF-8"), "ISO8859-1") + "\"");
//            if (null == responeData.getMsg()){
//                out = response.getOutputStream();
//                String code1 = "测试相应体";
//                JSONObject jsonObject = new JSONObject();
//                jsonObject.put("code",code1);
//                out.write(jsonObject.toJSONString().getBytes());
//                response.setStatus(404);
//                return;
//            }
            out = response.getOutputStream();
            //读取文件流
            int len = 0;
            byte[] buffer = new byte[1024 * 10];
            while ((len = ips.read(buffer)) != -1){
                out.write(buffer,0,len);
            }
            String code1 = "测试相应体";
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("code",code1);
            out.write(jsonObject.toJSONString().getBytes());
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                out.close();
                ips.close();
            } catch (IOException e) {
                System.out.println("关闭流出现异常");
                e.printStackTrace();
            }
        }
        return ;
    }
}
