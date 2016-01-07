package com.dianping.cache.deamontask.tasks;

import com.dianping.cache.deamontask.AbstractDeamonTask;
import com.dianping.squirrel.client.StoreClient;
import com.dianping.squirrel.client.StoreClientFactory;
import com.dianping.squirrel.client.StoreKey;
import com.dianping.squirrel.client.impl.redis.RedisStoreClient;
import redis.clients.jedis.*;

import java.util.Set;

/**
 * Created by thunder on 16/1/6.
 */
public class ClearCategoryTask extends AbstractDeamonTask {

    private String category;
    private StoreClient storeClient;


    public ClearCategoryTask(String category) {
        this.category = category;
        this.storeClient = StoreClientFactory.getStoreClientByCategory(category);
    }


    @Override
    public void taskRun() {
        if (storeClient == null || !(storeClient instanceof RedisStoreClient)) {
            return;
        }
        JedisCluster jedisCluster = ((RedisStoreClient) storeClient).getJedisClient();
        Set<HostAndPort> nodes = jedisCluster.getCluserNodesHostAndPort();
        for (HostAndPort node : nodes) {
            Jedis jedis = new Jedis(node.getHost(), node.getPort());
            ScanParams scanParams = new ScanParams();
            scanParams.match(category + "*");
            scanParams.count(50);
            ScanResult<String> result;
            result = jedis.scan("0", scanParams);
            while (!result.getStringCursor().equals("0")) {
                for(String k : result.getResult()) {
                    jedis.del(k);
                }
                result = jedis.scan(result.getStringCursor(), scanParams);
            }
        }
    }

}