package com.lj.zby.impl;

import com.lj.zby.entity.SysJob;
import com.lj.zby.mapper.SysJobMapper;
import com.lj.zby.service.ISysJobService;
import com.lj.zby.util.CommandUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

@Service
public class SysJobServiceImpl implements ISysJobService {
	@Autowired
	private SysJobMapper sysJobMapper;

	@Override
	public int getJobCount() {
		return sysJobMapper.getJobCount();
	}

	@Override
	public List<SysJob> querySysJobList(HashMap<String, String> map) {
		return sysJobMapper.querySysJobList(map);
	}

	@Override
	public int insertSelective(SysJob record) {
		return sysJobMapper.insertSelective(record);
	}

	@Override
	public int deleteByPrimaryKey(Integer id) {
		return sysJobMapper.deleteByPrimaryKey(id);
	}

	@Override
	public SysJob selectByPrimaryKey(Integer id) {
		return sysJobMapper.selectByPrimaryKey(id);
	}

	@Override
	public SysJob selectByBean(SysJob bean) {
		return sysJobMapper.selectByBean(bean);
	}

	@Override
	public int updateByPrimaryKeySelective(SysJob bean) {
		return sysJobMapper.updateByPrimaryKeySelective(bean);
	}

	@Override
	public void getSqlFile() {
		StringBuilder sb = new StringBuilder();
		sb.append("mysqldump -hlocalhost -uroot -padmin gupao sys_job --where=\" job_name = 'eeee' \" > D:\\TableConditon.sql");
		try {
			String command = CommandUtil.run(sb.toString());
			System.out.println(command);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
