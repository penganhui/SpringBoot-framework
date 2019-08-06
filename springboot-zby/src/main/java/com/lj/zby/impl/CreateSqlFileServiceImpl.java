package com.lj.zby.impl;

import com.lj.zby.service.ICreateSqlFileService;
import com.lj.zby.util.ParseSqlUtil;
import org.springframework.stereotype.Service;

@Service
public class CreateSqlFileServiceImpl implements ICreateSqlFileService {
    @Override
    public void getSqlFile() {
        String path = "D:\\TableConditon.sql";
        String writeFilePath = "D:\\output.txt";
        String context = ParseSqlUtil.readTxt(path);
        ParseSqlUtil.writeFileInfo(context,writeFilePath);
    }
}
