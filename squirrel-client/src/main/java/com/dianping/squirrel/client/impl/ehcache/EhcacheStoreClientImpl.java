/**
 * Project: cache-core
 * 
 * File Created at 2010-9-1 $Id$
 * 
 * Copyright 2010 dianping.com Corporation Limited. All rights reserved.
 * 
 * This software is the confidential and proprietary information of Dianping
 * Company. ("Confidential Information"). You shall not disclose such
 * Confidential Information and shall use it only in accordance with the terms
 * of the license agreement you entered into with dianping.com.
 */
package com.dianping.squirrel.client.impl.ehcache;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import javax.management.MBeanServer;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.constructs.blocking.BlockingCache;
import net.sf.ehcache.management.ManagementService;

import com.dianping.squirrel.client.config.StoreCategoryConfig;
import com.dianping.squirrel.client.config.StoreClientConfig;
import com.dianping.squirrel.client.core.StoreCallback;
import com.dianping.squirrel.client.core.StoreFuture;
import com.dianping.squirrel.client.impl.AbstractStoreClient;
import com.dianping.squirrel.common.config.ConfigManagerLoader;
import com.dianping.squirrel.common.exception.StoreException;
import com.google.common.eventbus.EventBus;

/**
 * EhcacheStoreClientImpl for squirrel local cache
 * 
 * @author pengshan.zhang
 * @author jinhua.liang
 * @author enlight.chen
 * 
 */
public class EhcacheStoreClientImpl extends AbstractStoreClient {

	public static final EventBus eventBus = new EventBus();

	/**
	 * Template cache name
	 */
	private static final String TEMPLATE_CACHE_NAME = "templateCache";

	private static final int hotkeyLockTime = ConfigManagerLoader.getConfigManager().getIntValue(
			"avatar-cache.ehcache.hotkey.locktime", 30);

	/**
	 * Ehcache CacheManager instance
	 */
	private static CacheManager manager;

	private BlockingCache defaultBlockingCache;

	public static void publishCacheManager() {
		eventBus.post(new EhcacheEvent(manager));
	}

	public void clear() {
		defaultBlockingCache.removeAll();
		for (String cacheName : manager.getCacheNames()) {
			manager.getCache(cacheName).removeAll();
		}
	}

	@SuppressWarnings("unchecked")
	public <T> T get(StoreCategoryConfig categoryConfig, String key) {
		Element element = findCache(categoryConfig.getCategory()).get(key);
		return (T) (element == null ? null : element.getObjectValue());
	}

	public Future<Boolean> asyncDelete(String key, boolean isHot, String category) throws StoreException {
		boolean result = findCache(category).remove(key);
		StoreFuture<Boolean> future = new StoreFuture<Boolean>(key);
		future.onSuccess(result);
		return future;
	}

	@Override
	public void stop() {
		manager.shutdown();
	}

	/**
	 * @see com.dianping.squirrel.common.lifecycle.Lifecycle#start()
	 */
	@Override
	public void start() {
		Ehcache cache = manager.getCache(TEMPLATE_CACHE_NAME);
		defaultBlockingCache = new LooseBlockingCache(cache);
		manager.replaceCacheWithDecoratedCache(cache, defaultBlockingCache);
	}

	/**
	 * @see com.dianping.squirrel.client.core.InitialConfiguration#initialize(com.dianping.squirrel.client.config.StoreClientConfig)
	 */
	@Override
	public void configure(StoreClientConfig config) {
		if (config instanceof EhcacheClientConfig) {
			manager = ((EhcacheClientConfig) config).buildEhcacheManager();
		}
		if (manager == null) {
			manager = CacheManager.create();
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            ManagementService.registerMBeans(manager, server, true, true, true, true);
        }
	}

	@Override
	public boolean isDistributed() {
		return false;
	}

	private String genLastVersionCacheKey(String currentVersionCacheKey) {
		if (currentVersionCacheKey == null) {
			return currentVersionCacheKey;
		}

		int versionSplitPos = currentVersionCacheKey.lastIndexOf("_");
		if (versionSplitPos < 0) {
			return currentVersionCacheKey;
		}

		String versionStr = currentVersionCacheKey.substring(versionSplitPos + 1);
		if (!isNumeric(versionStr)) {
			return currentVersionCacheKey;
		}

		Integer currentVersion = Integer.valueOf(versionStr);
		if (currentVersion > 0) {
			return currentVersionCacheKey.substring(0, versionSplitPos + 1) + (currentVersion - 1);
		} else {
			return currentVersionCacheKey;
		}

	}

	private boolean isNumeric(String src) {
		if (src == null || src.length() == 0) {
			return false;
		}

		for (int i = 0; i < src.length(); i++) {
			if (src.charAt(i) < '0' || src.charAt(i) > '9') {
				return false;
			}
		}

		return true;
	}

	private int getHotkeyLockTime() {
		return hotkeyLockTime;
	}

	private Ehcache findCache(String category) {
		if (category == null) {
			return defaultBlockingCache;
		}
		Ehcache cache = manager.getCache(category);
		if (cache == null) {
			return defaultBlockingCache;
		}
		return cache;
	}

    @Override
    protected <T> T doGet(StoreCategoryConfig categoryConfig, String key) throws Exception {
        String category = categoryConfig.getCategory();
        T result = get(categoryConfig, key);

        if (categoryConfig.isHot()) {
            if (result == null) {
                Element element = findCache(category).putIfAbsent(
                        new Element(key + "_lock", true, false, 0, getHotkeyLockTime()));
                boolean locked = (element == null);

                if (locked) {
                    return null;
                } else {
                    // 批量清理时，因为version升级了，所以bak数据要考虑从上一个版本中查找
                    result = get(categoryConfig, key + "_bak");
                    if (result == null) {
                        String lastVersionKey = genLastVersionCacheKey(key);
                        if (!key.equals(lastVersionKey)) {
                            result = get(categoryConfig, lastVersionKey + "_bak");
                        }
                    }
                    return result;
                }
            }
        }

        return result;
    }

    @Override
    protected Boolean doSet(StoreCategoryConfig categoryConfig, String key, Object value) throws Exception {
        String category = categoryConfig.getCategory();
        findCache(category).put(new Element(key, value, false, 0, categoryConfig.getDurationSeconds()));
        if (categoryConfig.isHot()) {
            findCache(category).put(new Element(key + "_bak", value, true, 0, 0));
            String lastVersionKey = genLastVersionCacheKey(key);
            // 当版本升级后，要清理上一个版本的hotkey数据
            if (!key.equals(lastVersionKey)) {
                findCache(category).remove(lastVersionKey + "_bak");
            }
        }
        return true;
    }

    @Override
    protected Boolean doAdd(StoreCategoryConfig categoryConfig, String key, Object value) throws Exception {
        String category = categoryConfig.getCategory();
        Element element = findCache(category).
                        putIfAbsent(new Element(key, value, false, 0, categoryConfig.getDurationSeconds()));
        if(element == null) {
            if (categoryConfig.isHot()) {
                findCache(category).put(new Element(key + "_bak", value, true, 0, 0));
                String lastVersionKey = genLastVersionCacheKey(key);
                // 当版本升级后，要清理上一个版本的hotkey数据
                if (!key.equals(lastVersionKey)) {
                    findCache(category).remove(lastVersionKey + "_bak");
                }
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected Boolean doDelete(StoreCategoryConfig categoryConfig, String finalKey) throws Exception {
        return findCache(categoryConfig.getCategory()).remove(finalKey);
    }

    @Override
    protected <T> Future<T> doAsyncGet(StoreCategoryConfig categoryConfig, String key) throws Exception {
        T result = doGet(categoryConfig, key);
        StoreFuture<T> future = new StoreFuture<T>(key);
        future.onSuccess(result);
        return future;
    }

    @Override
    protected Future<Boolean> doAsyncSet(StoreCategoryConfig categoryConfig, String key, Object value) throws Exception {
        boolean result = doSet(categoryConfig, key, value);
        StoreFuture<Boolean> future = new StoreFuture<Boolean>(key);
        future.onSuccess(result);
        return future;
    }

    @Override
    protected Future<Boolean> doAsyncAdd(StoreCategoryConfig categoryConfig, String key, Object value) throws Exception {
        boolean result = doAdd(categoryConfig, key, value);
        StoreFuture<Boolean> future = new StoreFuture<Boolean>(key);
        future.onSuccess(result);
        return future;
    }

    @Override
    protected Future<Boolean> doAsyncDelete(StoreCategoryConfig categoryConfig, String key) throws Exception {
        boolean result = doDelete(categoryConfig, key);
        StoreFuture<Boolean> future = new StoreFuture<Boolean>(key);
        future.onSuccess(result);
        return future;
    }

    @Override
    protected <T> Void doAsyncGet(StoreCategoryConfig categoryConfig, String finalKey, 
                                  StoreCallback<T> callback) throws Exception {
        T result = doGet(categoryConfig, finalKey);
        callback.onSuccess(result);
        return null;
    }

    @Override
    protected Void doAsyncSet(StoreCategoryConfig categoryConfig, String finalKey, Object value,
                              StoreCallback<Boolean> callback) throws Exception {
        boolean result = doSet(categoryConfig, finalKey, value);
        callback.onSuccess(result);
        return null;
    }

    @Override
    protected Void doAsyncAdd(StoreCategoryConfig categoryConfig, String finalKey, Object value,
                              StoreCallback<Boolean> callback) throws Exception {
        boolean result = doAdd(categoryConfig, finalKey, value);
        callback.onSuccess(result);
        return null;
    }

    @Override
    protected Void doAsyncDelete(StoreCategoryConfig categoryConfig, String finalKey, 
                                 StoreCallback<Boolean> callback) throws Exception {
        boolean result = doDelete(categoryConfig, finalKey);
        callback.onSuccess(result);
        return null;
    }

    @Override
    protected Long doIncrease(StoreCategoryConfig categoryConfig, String finalKey, int amount) throws Exception {
        throw new UnsupportedOperationException("ehcache does not support increase operation");
    }

    @Override
    protected Long doDecrease(StoreCategoryConfig categoryConfig, String key, int amount) throws Exception {
        throw new UnsupportedOperationException("ehcache does not support decrease operation");
    }
    
    protected boolean needMonitor(String cacheType) {
        return false;
    }

    @Override
    protected <T> Map<String, T> doMultiGet(StoreCategoryConfig categoryConfig, List<String> finalKeyList) throws Exception {
        Map<String, T> map = new HashMap<String, T>();
        for (String key : finalKeyList) {
            T result = doGet(categoryConfig, key);
            if(result != null) {
                map.put(key, result);
            }
        }
        return map;
    }

    @Override
    protected <T> Void doAsyncMultiGet(StoreCategoryConfig categoryConfig, List<String> finalKeyList,
                                       StoreCallback<Map<String, T>> callback) throws Exception {
        Map<String, T> result = doMultiGet(categoryConfig, finalKeyList);
        callback.onSuccess(result);
        return null;
    }

    @Override
    protected <T> Boolean doMultiSet(StoreCategoryConfig categoryConfig, List<String> keys, List<T> values) throws Exception {
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            T value = values.get(i);
            doSet(categoryConfig, key, value);
        }
        return true;
    }

    @Override
    protected <T> Void doAsyncMultiSet(StoreCategoryConfig categoryConfig, List<String> keys, List<T> values,
                                    StoreCallback<Boolean> callback) throws Exception {
        doMultiSet(categoryConfig, keys, values);
        callback.onSuccess(true);
        return null;
    }

    @Override
    public void configChanged(StoreClientConfig config) {
        // TODO: can trigger ehcache config reload etc.
    }

    @Override
    public String getScheme() {
        return "ehcache";
    }
    
}