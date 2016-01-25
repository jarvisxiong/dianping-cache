/**
 * Dianping.com Inc.
 * Copyright (c) 2003-2013 All Rights Reserved.
 */
package com.dianping.squirrel.common.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author xiangwu
 * @Sep 22, 2013
 * 
 */
public abstract class AbstractConfigManager implements ConfigManager {

	private static Logger logger = LoggerFactory.getLogger(AbstractConfigManager.class);

	public static final String KEY_GROUP = "swimlane";

	public static final String KEY_LOCAL_IP = "host.ip";

	public static final String KEY_APP_NAME = "app.name";

	public static final String KEY_ENV = "environment";

	public static final String DEFAULT_GROUP = "";

	public static final int DEFAULT_WEIGHT = 1;

	private static List<ConfigChangeListener> configChangeListeners = new ArrayList<ConfigChangeListener>();

	protected Map<String, Object> localCache = new HashMap<String, Object>();

	public abstract String doGetProperty(String key) throws Exception;

	public abstract String doGetLocalProperty(String key) throws Exception;

	public abstract String doGetEnv() throws Exception;

	public AbstractConfigManager() {
		Map<String, Object> properties = LocalConfigLoader.load(this);
		localCache.putAll(properties);
	}

	public boolean getBooleanValue(String key, boolean defaultValue) {
		Boolean value = getBooleanValue(key);
		return value != null ? value : defaultValue;
	}

	public Boolean getBooleanValue(String key) {
		return getProperty(key, Boolean.class);
	}

	public long getLongValue(String key, long defaultValue) {
		Long value = getLongValue(key);
		return value != null ? value : defaultValue;
	}

	public Long getLongValue(String key) {
		return getProperty(key, Long.class);
	}

	public int getIntValue(String key, int defaultValue) {
		Integer value = getIntValue(key);
		return value != null ? value : defaultValue;
	}

	public Integer getIntValue(String key) {
		return getProperty(key, Integer.class);
	}

	public float getFloatValue(String key, float defaultValue) {
		Float value = getFloatValue(key);
		return value != null ? value : defaultValue;
	}

	public Float getFloatValue(String key) {
		return getProperty(key, Float.class);
	}

	@Override
	public String getStringValue(String key, String defaultValue) {
		String value = getStringValue(key);
		return value != null ? value : defaultValue;
	}

	public String getLocalStringValue(String key) {
		return getPropertyFromLocal(key, String.class);
	}

	private <T> T getPropertyFromLocal(String key, Class<T> type) {
		String strValue = null;
		if (localCache.containsKey(key)) {
			Object value = localCache.get(key);
			if (value.getClass() == type) {
				return (T) value;
			} else {
				strValue = value + "";
			}
		}
		if (strValue == null) {
			strValue = System.getProperty(key);
		}
		if (strValue == null) {
			strValue = System.getenv(key);
		}
		if (strValue == null) {
			try {
				strValue = doGetLocalProperty(key);
			} catch (Throwable e) {
				logger.error("error while reading local config[" + key + "]:" + e.getMessage());
			}
		}
		if (strValue != null) {
			Object value = null;
			if (String.class == type) {
				value = strValue;
			} else if (!StringUtils.isBlank(strValue)) {
				if (Integer.class == type) {
					value = Integer.valueOf(strValue);
				} else if (Long.class == type) {
					value = Long.valueOf(strValue);
				} else if (Float.class == type) {
					value = Float.valueOf(strValue);
				} else if (Boolean.class == type) {
					value = Boolean.valueOf(strValue);
				}
			}
			if (value != null) {
				localCache.put(key, value);
			}
			return (T) value;
		} else {
		}
		return null;
	}

	@Override
	public String getStringValue(String key) {
		return getProperty(key, String.class);
	}

	private <T> T getProperty(String key, Class<T> type) {
		String strValue = null;
		if (localCache.containsKey(key)) {
			Object value = localCache.get(key);
			if (value.getClass() == type) {
				return (T) value;
			} else {
				strValue = value + "";
			}
		}
		if (strValue == null) {
			strValue = System.getProperty(key);
		}
		if (strValue == null) {
			strValue = System.getenv(key);
		}
		if (strValue == null) {
			try {
				strValue = doGetLocalProperty(key);
			} catch (Throwable e) {
				logger.error("error while reading local config[" + key + "]:" + e.getMessage());
			}
		}
		if (strValue == null && StringUtils.isNotBlank(getAppName())) {
			if (!key.startsWith(getAppName())) {
				try {
					strValue = doGetProperty(getAppName() + "." + key);
					if (strValue != null && logger.isInfoEnabled()) {
						logger.info("read from config server with key[" + getAppName() + "." + key + "]:" + strValue);
					}
				} catch (Throwable e) {
					logger.error("error while reading property[" + getAppName() + "." + key + "]:" + e.getMessage());
				}
			}
		}
		if (strValue == null) {
			try {
				strValue = doGetProperty(key);
				if (strValue != null && logger.isInfoEnabled()) {
					logger.info("read from config server with key[" + key + "]:" + strValue);
				}
			} catch (Throwable e) {
				logger.error("error while reading property[" + key + "]:" + e.getMessage());
			}
		}
		if (strValue != null) {
			Object value = null;
			if (String.class == type) {
				value = strValue;
			} else if (!StringUtils.isBlank(strValue)) {
				if (Integer.class == type) {
					value = Integer.valueOf(strValue);
				} else if (Long.class == type) {
					value = Long.valueOf(strValue);
				} else if (Float.class == type) {
					value = Float.valueOf(strValue);
				} else if (Boolean.class == type) {
					value = Boolean.valueOf(strValue);
				}
			}
			if (value != null) {
				localCache.put(key, value);
			}
			return (T) value;
		} else {
		}
		return null;
	}

	public int getLocalIntValue(String key, int defaultValue) {
		String strValue = getLocalProperty(key);
		if (!StringUtils.isBlank(strValue)) {
			return Integer.valueOf(strValue);
		}
		return defaultValue;
	}

	public long getLocalLongValue(String key, long defaultValue) {
		String strValue = getLocalProperty(key);
		if (!StringUtils.isBlank(strValue)) {
			return Long.valueOf(strValue);
		}
		return defaultValue;
	}

	public boolean getLocalBooleanValue(String key, boolean defaultValue) {
		String strValue = getLocalProperty(key);
		if (!StringUtils.isBlank(strValue)) {
			return Boolean.valueOf(strValue);
		}
		return defaultValue;
	}

	public String getLocalStringValue(String key, String defaultValue) {
		String value = getLocalProperty(key);
		return value != null ? value : defaultValue;
	}

	public String getLocalProperty(String key) {
		if (localCache.containsKey(key)) {
			String value = "" + localCache.get(key);
			return value;
		}
		try {
			String value = doGetLocalProperty(key);
			if (value != null) {
				localCache.put(key, value);
				if (logger.isInfoEnabled()) {
					logger.info("read from config server with key[" + key + "]:" + value);
				}
				return value;
			} else {
			}
		} catch (Throwable e) {
			logger.error("error while reading property[" + key + "]:" + e.getMessage());
		}
		return null;
	}

	@Override
	public void init(Properties properties) {
		for (Iterator ir = properties.keySet().iterator(); ir.hasNext();) {
			String key = ir.next().toString();
			String value = properties.getProperty(key);
			localCache.put(key, value);
		}
	}

	public String getEnv() {
		String value = getLocalProperty(KEY_ENV);
		if (value == null) {
			try {
				value = doGetEnv();
			} catch (Throwable e) {
				logger.error("error while reading env:" + e.getMessage());
			}
			if (value != null) {
				localCache.put(KEY_ENV, value);
			}
		}
		return value;
	}

	public String getAppName() {
		String value = getLocalProperty(KEY_APP_NAME);
		if (value == null) {
			try {
				value = LocalConfigLoader.getAppName();
			} catch (Throwable e) {
				logger.error("error while reading app name:" + e.getMessage());
			}
			if (value != null) {
				localCache.put(KEY_APP_NAME, value);
			}
			if (StringUtils.isNotBlank(value)) {
				System.out.println("app name:" + value);
			}
		}
		return value;
	}

	public void registerConfigChangeListener(ConfigChangeListener configChangeListener) throws Exception {
		configChangeListeners.add(configChangeListener);
		doRegisterConfigChangeListener(configChangeListener);
	}
	
	public void unregisterConfigChangeListener(ConfigChangeListener configChangeListener) throws Exception {
        configChangeListeners.remove(configChangeListener);
        doUnregisterConfigChangeListener(configChangeListener);
    }

	public abstract void doRegisterConfigChangeListener(ConfigChangeListener configChangeListener) throws Exception;

	public abstract void doUnregisterConfigChangeListener(ConfigChangeListener configChangeListener) throws Exception;
	
	public Map<String, Object> getLocalConfig() {
		return localCache;
	}

	public List<ConfigChangeListener> getConfigChangeListeners() {
		return configChangeListeners;
	}

	public void onConfigChange(String key, String value) {
		List<ConfigChangeListener> listeners = getConfigChangeListeners();
		for (ConfigChangeListener listener : listeners) {
			listener.onChange(key, value);
		}
	}

}
