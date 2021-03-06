package com.dianping.cache.alarm.alarmtemplate;

import com.dianping.cache.alarm.dao.MemcacheAlarmTemplateDao;
import com.dianping.cache.alarm.entity.MemcacheTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Created by lvshiyun on 15/12/24.
 */
@Service
public class MemcacheAlarmTemplateServiceImpl implements MemcacheAlarmTemplateService {

    @Autowired
    MemcacheAlarmTemplateDao memcacheAlarmTemplateDao;


    @Override
    public boolean insert(MemcacheTemplate memcacheTemplate) {
        return memcacheAlarmTemplateDao.insert(memcacheTemplate);
    }

    @Override
    public boolean update(MemcacheTemplate memcacheTemplate) {
        return memcacheAlarmTemplateDao.update(memcacheTemplate);
    }

    @Override
    public List<MemcacheTemplate> findAll() {
        return memcacheAlarmTemplateDao.findAll();
    }

    @Override
    public MemcacheTemplate findById(int id) {
        return memcacheAlarmTemplateDao.findById(id);
    }

    @Override
    public MemcacheTemplate findAlarmTemplateByTemplateName(String templateName) {
        return memcacheAlarmTemplateDao.findAlarmTemplateByTemplateName(templateName);
    }

    @Override
    public int deleteById(int id) {
        return memcacheAlarmTemplateDao.deleteById(id);
    }
}
