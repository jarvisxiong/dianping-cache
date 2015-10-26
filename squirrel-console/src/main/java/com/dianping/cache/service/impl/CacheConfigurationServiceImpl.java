/**
 * Project: cache-server
 * 
 * File Created at 2010-10-19
 * $Id$
 * 
 * Copyright 2010 Dianping.com Corporation Limited.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Dianping Company. ("Confidential Information").  You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Dianping.com.
 */
package com.dianping.cache.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

import com.dianping.avatar.exception.DuplicatedIdentityException;
import com.dianping.cache.dao.CacheConfigurationDao;
import com.dianping.cache.entity.CacheConfiguration;
import com.dianping.cache.entity.CacheKeyConfiguration;
import com.dianping.cache.entity.ServerGroup;
import com.dianping.cache.remote.jms.CacheMessageNotifier;
import com.dianping.cache.remote.jms.CacheMessageProducer;
import com.dianping.cache.remote.translator.CacheConfiguration2DTOTranslator;
import com.dianping.cache.remote.translator.Translator;
import com.dianping.cache.service.CacheConfigurationService;
import com.dianping.cache.service.CacheKeyConfigurationService;
import com.dianping.cache.service.OperationLogService;
import com.dianping.cache.service.ServerGroupService;
import com.dianping.cache.util.CollectionUtils;
import com.dianping.cache.util.Migrator;
import com.dianping.lion.Environment;
import com.dianping.ops.cmdb.CmdbManager;
import com.dianping.ops.cmdb.CmdbProject;
import com.dianping.ops.cmdb.CmdbResult;
import com.dianping.ops.cmdb.CmdbServer;
import com.dianping.queue.SimpleQueueService;
import com.dianping.queue.message.Message;
import com.dianping.queue.message.TextMessage;
import com.dianping.remote.cache.dto.CacheConfigurationDTO;
import com.dianping.remote.cache.dto.CacheKeyTypeVersionUpdateDTO;
import com.dianping.remote.cache.dto.SingleCacheRemoveDTO;
import com.dianping.squirrel.client.core.CacheClient;
import com.dianping.squirrel.client.core.CacheClientBuilder;
import com.dianping.squirrel.client.impl.memcached.MemcachedClientConfiguration;
import com.dianping.squirrel.common.config.ConfigChangeListener;
import com.dianping.squirrel.common.config.ConfigManagerLoader;

/**
 * CacheConfigurationService to provide cache configuration data
 * 
 * @author danson.liu
 * 
 */
public class CacheConfigurationServiceImpl implements CacheConfigurationService, InitializingBean {

	private Logger logger = LoggerFactory.getLogger(getClass());

	private static final String CACHE_FINAL_KEY_SEP = "@|$";
	
	private static final String CAT_EVENT_TYPE = "Cache.notifications";
	
	private static final String KEY_DISABLE_CLEAR_CATEGORIES = "avatar-cache.disable.clear.categories";
	
	private static final String DEFAULT_DISABLE_CLEAR_CATEGORIES = "DianPing.Common.StaticFile,oStaticFileMD5,CortexDependency,CortexCombo";

	private CacheKeyConfigurationService cacheKeyConfigurationService;

	private OperationLogService operationLogService;

	private ServerGroupService serverGroupService;

	private CacheConfigurationDao configurationDao;

	private CacheMessageProducer cacheMessageProducer;

	private CacheMessageNotifier cacheMessageNotifier;

	private SimpleQueueService smsMessageSender;

	private Migrator migrator;

	private ExecutorService executorService = new ThreadPoolExecutor(3, 10, 10L, TimeUnit.MINUTES,
			new LinkedBlockingQueue<Runnable>(100));

	private Translator<CacheConfiguration, CacheConfigurationDTO> translator = new CacheConfiguration2DTOTranslator();

	private volatile List<String> disabledCategoryList;
	
	public CacheConfigurationServiceImpl() {
	    try {
	        String disabledCategories = ConfigManagerLoader.getConfigManager().getStringValue(
	                KEY_DISABLE_CLEAR_CATEGORIES, DEFAULT_DISABLE_CLEAR_CATEGORIES);
	        disabledCategoryList = CollectionUtils.toList(disabledCategories, ",");
            ConfigManagerLoader.getConfigManager().registerConfigChangeListener(new ConfigChangeListener() {

                @Override
                public void onChange(String key, String value) {
                    if(KEY_DISABLE_CLEAR_CATEGORIES.equals(key)) {
                        if(value != null) {
                            disabledCategoryList = CollectionUtils.toList(value, ",");
                        }
                    }
                }
                
            });
        } catch (Exception e) {
            logger.error("failed to init CacheConfigurationService", e);
        }
	}
	
	@Override
	public List<CacheConfiguration> findAll() {
		return configurationDao.findAll();
	}

	@Override
	public CacheConfiguration find(String cacheKey) {
		return configurationDao.find(cacheKey);
	}

	@Override
	public CacheConfiguration create(CacheConfiguration config) throws DuplicatedIdentityException {
		try {
			String cacheKey = config.getCacheKey();
			CacheConfiguration found = find(cacheKey);
			if (found != null) {
				throw new DuplicatedIdentityException("cache key[" + cacheKey + "] already exists.");
			}
			configurationDao.create(config);
			CacheConfiguration created = configurationDao.find(cacheKey);
			cacheMessageProducer.sendMessageToTopic(translator.translate(created));
			com.dianping.squirrel.client.core.CacheConfiguration.addCache(cacheKey, created.getClientClazz());
			logConfigurationCreate(config, true);
			return created;
		} catch (RuntimeException e) {
			logger.error("Create cache configuration failed.", e);
			logConfigurationCreate(config, false);
			throw e;
		}
	}

	@Override
	public CacheConfiguration update(CacheConfiguration config) {
		CacheConfiguration oldConfig = null;
		try {
			oldConfig = find(config.getCacheKey());
			configurationDao.update(config);
			// 保存后，从新从数据库加载数据，可能有数据库级别的触发逻辑
			CacheConfiguration updated = configurationDao.find(config.getCacheKey());
			if (updated == null) {
				throw new RuntimeException("Config maybe already removed by others.");
			}
			cacheMessageProducer.sendMessageToTopic(translator.translate(updated));
			String cacheKey = config.getCacheKey();
			CacheClientBuilder.closeCacheClient(cacheKey);
			com.dianping.squirrel.client.core.CacheConfiguration.removeCache(cacheKey);
			com.dianping.squirrel.client.core.CacheConfiguration.addCache(cacheKey, updated.getClientClazz());
			logConfigurationUpdate(oldConfig, updated, true);
			return updated;
		} catch (RuntimeException e) {
			logger.error("Update cache configuration failed.", e);
			logConfigurationUpdate(oldConfig, config, false);
			throw e;
		}
	}

	@Override
	public void delete(String cacheKey) {
		CacheConfiguration configFound = null;
		try {
			configFound = find(cacheKey);
			if (configFound != null) {
				CacheClientBuilder.closeCacheClient(cacheKey);
				com.dianping.squirrel.client.core.CacheConfiguration.removeCache(cacheKey);
				configurationDao.delete(cacheKey);
				logConfigurationDelete(configFound, true);
			}
		} catch (RuntimeException e) {
			logger.error("Delete cache configuration failed.", e);
			logConfigurationDelete(configFound, false);
			throw e;
		}
	}

	private void logConfigurationDelete(CacheConfiguration config, boolean succeed) {
		if (config != null) {
			operationLogService.create(succeed, "ConfigDelete", transferConfigDetail(config, null), true);
		}
	}

	@Override
	public void clearByCategory(String category) {
		clearByCategory(category, null);
	}

	@Override
	public void clearByCategory(String category, String serverOrGroup) {
	    if(!canClearCategory(category, serverOrGroup)) {
	        throw new RuntimeException("Can not clear category " + category + ", please contact DBA.");
	    }
		List<String> destinations = getDestinations(serverOrGroup);

		String version = cacheKeyConfigurationService.incAndRetriveVersion(category);
		if (version != null) {
			try {
				final CacheKeyTypeVersionUpdateDTO message = new CacheKeyTypeVersionUpdateDTO();
				message.setAddTime(System.currentTimeMillis());
				message.setMsgValue(category);
				message.setVersion(version);
				message.setDestinations(destinations);
				cacheMessageProducer.sendMessageToTopic(message);
				logCacheBatchClear(category, true);
			} catch (RuntimeException e) {
				logCacheBatchClear(category, false);
				throw e;
			}
		} else {
			logger.warn("Clear cache by category[" + category + "] failed, the category not found.");
		}
	}

	@Override
	public void clearByCategoryBothSide(String category) {
		clearByCategory(category);
		sendBatchClearMsg2DotNet(category);
	}

	@Override
	public void clearByKey(String cacheType, String key) {
		clearByKey(cacheType, key, true);
	}

	@SuppressWarnings("unchecked")
	public void clearByKey(String cacheType, String key, boolean clearDistributed) {
		CacheConfiguration configuration = find(cacheType);
		if (configuration != null) {
			String clientClazz = configuration.getClientClazz();
			try {
				if ("com.dianping.cache.memcached.MemcachedClientImpl".equals(clientClazz)) {
					if (clearDistributed) {
						MemcachedClientConfiguration config = new MemcachedClientConfiguration();
						config.setServerList(configuration.getServerList());
						Class<?> transcoderClazz = Class.forName(configuration.getTranscoderClazz());
						config.setTranscoderClass(transcoderClazz);
						CacheClient cacheClient = CacheClientBuilder.buildCacheClient(cacheType, config);
						String[] keyList = StringUtils.splitByWholeSeparator(key, CACHE_FINAL_KEY_SEP);
						if (keyList != null) {
							for (String singleKey : keyList) {
								cacheClient.asyncDelete(singleKey, true, null);
							}
						}
					}
				} else {
					final SingleCacheRemoveDTO message = new SingleCacheRemoveDTO();
					message.setAddTime(System.currentTimeMillis());
					message.setCacheType(cacheType);
					message.setCacheKey(key);
					cacheMessageProducer.sendMessageToTopic(message);
				}
				logCacheItemClear(cacheType, key, true);
			} catch (Exception e) {
				logCacheItemClear(cacheType, key, false);
				throw new RuntimeException("Clear cache with key[" + key + "] failed.", e);
			}
		} else {
			logger.error("Clear cache by key failed, cacheType[" + cacheType + "] not found.");
		}
	}

	/*
	 * 具体应用调用的API，所以无需重复清除分布式缓存，应用已经清除过
	 */
	@Override
	public void clearByKeyBothSide(String cacheType, String key, String category, List<Object> params) {
		clearByKey(cacheType, key, false);
		if (category != null) {
			sendClearMsg2DotNet(category, params);
		}
	}

	private void sendBatchClearMsg2DotNet(final String category) {
		CacheKeyConfiguration cacheKeyConfig = cacheKeyConfigurationService.find(category);
		if (cacheKeyConfig.isSync2Dnet()) {
			executorService.execute(new Runnable() {
				@Override
				public void run() {
					try {
						smsMessageSender.enqueue(new TextMessage("*" + category));
					} catch (Throwable e) {
						logger.error("Send cache batch-clear msg to .net failed with category[" + category + "].", e);
					}
				}
			});
		}
	}

	private void sendClearMsg2DotNet(final String category, final List<Object> params) {
		CacheKeyConfiguration cacheKeyConfig = cacheKeyConfigurationService.find(category);
		if (cacheKeyConfig.isSync2Dnet()) {
			executorService.execute(new Runnable() {
				@Override
				public void run() {
					StringBuilder msgBuilder = new StringBuilder(category);
					if (params != null) {
						for (Object param : params) {
							msgBuilder.append("|").append(param);
						}
					}
					Message message = new TextMessage(msgBuilder.toString());
					try {
						smsMessageSender.enqueue(message);
					} catch (Throwable e) {
						logger.error("Send cache clear msg to .net failed with content[" + msgBuilder + "].", e);
					}
				}
			});
		}
	}

	private void logConfigurationCreate(CacheConfiguration config, boolean succeed) {
		operationLogService.create(succeed, "ConfigCreate", transferConfigDetail(config, null), true);
	}

	private void logConfigurationUpdate(CacheConfiguration oldConfig, CacheConfiguration newConfig, boolean succeed) {
		Map<String, String> detail = new TreeMap<String, String>();
		if (oldConfig != null) {
			detail.putAll(transferConfigDetail(oldConfig, "old"));
		} else {
			detail.put("old.config", null);
		}
		detail.putAll(transferConfigDetail(newConfig, "new"));
		operationLogService.create(succeed, "ConfigUpdate", detail, true);
	}

	private void logCacheItemClear(String cacheType, String key, boolean succeed) {
		Map<String, String> detail = new HashMap<String, String>();
		detail.put("cacheType", cacheType);
		detail.put("key", key);
		operationLogService.create(succeed, "CacheItemClear", detail, true);
	}

	private void logCacheBatchClear(String category, boolean succeed) {
		Map<String, String> detail = new HashMap<String, String>();
		detail.put("category", category);
		operationLogService.create(succeed, "CacheBatchClear", detail, true);
	}

	private void logCacheVersionUpgrade(String category, boolean succeed) {
		Map<String, String> detail = new HashMap<String, String>();
		detail.put("category", category);
		operationLogService.create(succeed, "CacheVersionUpgrade", detail, true);
	}

	private void logCacheConfigPush(String category, List<String> destinations, boolean succeed) {
		Map<String, String> detail = new HashMap<String, String>();
		detail.put("category", category);
		detail.put("destinations", StringUtils.join(destinations, ","));
		operationLogService.create(succeed, "CacheConfigPush", detail, true);
	}

	private Map<String, String> transferConfigDetail(CacheConfiguration config, String prefix) {
		prefix = prefix != null ? prefix + "." : "";
		Map<String, String> detail = new HashMap<String, String>();
		detail.put(prefix + "key", config.getCacheKey());
		detail.put(prefix + "clientClazz", config.getClientClazz());
		detail.put(prefix + "servers", config.getServers());
		detail.put(prefix + "transcoder", config.getTranscoderClazz());
		return detail;
	}

	public void setConfigurationDao(CacheConfigurationDao configurationDao) {
		this.configurationDao = configurationDao;
	}

	/**
	 * @param cacheKeyConfigurationService
	 *            the cacheKeyConfigurationService to set
	 */
	public void setCacheKeyConfigurationService(CacheKeyConfigurationService cacheKeyConfigurationService) {
		this.cacheKeyConfigurationService = cacheKeyConfigurationService;
	}

	public void setOperationLogService(OperationLogService operationLogService) {
		this.operationLogService = operationLogService;
	}

	/**
	 * @param cacheMessageProducer
	 *            the cacheMessageProducer to set
	 */
	public void setCacheMessageProducer(CacheMessageProducer cacheMessageProducer) {
		this.cacheMessageProducer = cacheMessageProducer;
	}

	public void setCacheMessageNotifier(CacheMessageNotifier cacheMessageNotifier) {
		this.cacheMessageNotifier = cacheMessageNotifier;
	}

	public void setSmsMessageSender(SimpleQueueService smsMessageSender) {
		this.smsMessageSender = smsMessageSender;
	}

	public void setServerGroupService(ServerGroupService serverGroupService) {
		this.serverGroupService = serverGroupService;
	}

	public void setMigrator(Migrator migrator) {
		this.migrator = migrator;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		for (CacheConfiguration configuration : findAll()) {
			com.dianping.squirrel.client.core.CacheConfiguration.addCache(configuration.getCacheKey(),
					configuration.getClientClazz());
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.dianping.cache.service.CacheConfigurationService#incVersion(java.
	 * lang.String)
	 */
	@Override
	public void incVersion(String category) {
		Assert.hasLength(category);

		try {
			if (cacheKeyConfigurationService.incAndRetriveVersion(category) != null) {
				logCacheVersionUpgrade(category, true);
			} else {
				logCacheVersionUpgrade(category, false);
			}
		} catch (RuntimeException e) {
			logCacheVersionUpgrade(category, false);
			throw e;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.dianping.cache.service.CacheConfigurationService#pushCategoryConfig
	 * (java.lang.String)
	 */
	@Override
	public void pushCategoryConfig(String category, String serverOrGroup) {
	    Assert.hasLength(category);
        List<String> destinations = getDestinations(serverOrGroup);

        CacheKeyConfiguration config = cacheKeyConfigurationService.find(category);
        if (config != null) {
            try {
                final CacheKeyTypeVersionUpdateDTO message = new CacheKeyTypeVersionUpdateDTO();
                message.setAddTime(System.currentTimeMillis());
                message.setMsgValue(category);
                message.setVersion(String.valueOf(config.getVersion()));
                message.setDestinations(destinations);
                cacheMessageProducer.sendMessageToTopic(message);
                logCacheConfigPush(category, destinations, true);
            } catch (RuntimeException e) {
                logCacheConfigPush(category, destinations, false);
                throw e;
            }
        } else {
            logger.warn("Push category[" + category + "] config failed, the category not found.");
        }
	}

	boolean canClearCategory(String category, String serverOrGroup) {
	    if(StringUtils.isBlank(category)) 
	        throw new NullPointerException("category is blank");
	    if(isProductEnvironment()) {
	        if(isDisabledCategory(category) && isClearAll(serverOrGroup)) {
	            return false;
	        }
	    }
	    return true;
	}

    boolean isProductEnvironment() {
        return "product".equals(Environment.getEnv());
    }

    boolean isDisabledCategory(String category) {
        return disabledCategoryList == null ? 
                false : disabledCategoryList.contains(category.trim());
    }
    
    boolean isClearAll(String serverOrGroup) {
        return StringUtils.isBlank(serverOrGroup) || serverOrGroup.trim().equals("全部");
    }

    List<String> getDestinations(String serverOrGroup) {
	    List<String> destinations = null;
	    if (!isClearAll(serverOrGroup)) {
            String servers = serverOrGroup.trim();
            ServerGroup serverGroup = serverGroupService.find(servers);
            if (serverGroup != null) {
                servers = serverGroup.getServers();
            } else {
                if(isProjectName(servers)) {
                    if(isProductEnvironment()) {
                        List<String> devices = getDevicesFromCmdb(servers, "生产");
                        if(!CollectionUtils.isEmpty(devices)) {
                            return devices;
                        }
                    } else {
                        return null;
                    }
                    
                }
            }
            String[] serverArray = StringUtils.split(servers, ",，");
            destinations = new ArrayList<String>();
            for (String server : serverArray) {
                destinations.add(server.trim());
            }
        }
	    return destinations;
	}
	
    boolean isProjectName(String project) {
        CmdbResult<CmdbProject> result = CmdbManager.getProject(project);
        return (result != null && result.cmdbResult != null);
    }
    
    List<String> getDevicesFromCmdb(String project, String env) {
	    List<String> servers = null;
	    CmdbResult<List<CmdbServer>> result = CmdbManager.getAllDeviceByProject(project);
	    if(result != null && !CollectionUtils.isEmpty(result.cmdbResult)) {
	        servers = new ArrayList<String>();
	        for(CmdbServer cs : result.cmdbResult) {
	            if(StringUtils.equals(cs.getEnv(), env)) {
	                if(!CollectionUtils.isEmpty(cs.getPrivate_ip())) {
	                    servers.add(cs.getPrivate_ip().get(0));
	                }
	            }
	        }
	    }
        return servers;
    }

    @Override
	public void migrate() {
		migrator.migrate();
	}
}