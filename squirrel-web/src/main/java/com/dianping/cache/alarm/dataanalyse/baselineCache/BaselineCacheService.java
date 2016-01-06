package com.dianping.cache.alarm.dataanalyse.baselineCache;

import com.dianping.cache.alarm.entity.MemcacheBaseline;
import com.dianping.cache.alarm.entity.RedisBaseline;

import java.util.Map;

/**
 * Created by lvshiyun on 15/12/26.
 */

public interface BaselineCacheService {

    MemcacheBaseline getMemcacheBaselineByName(String name);

    RedisBaseline getRedisBaselineByName(String name);

    void reload();

    void putRedisBaselineMapToCache(Map<String, RedisBaseline> redisBaselineMap);

    Map<String, RedisBaseline> getRedisBaselineFromDb();

    void putMemcacheBaselineMapToCache(Map<String, MemcacheBaseline> memcacheBaselineMap);

    Map<String, MemcacheBaseline> getMemcacheBaselineFromDb();

}
