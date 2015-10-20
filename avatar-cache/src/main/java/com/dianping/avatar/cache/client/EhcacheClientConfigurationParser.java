/**
 * Project: avatar
 * 
 * File Created at 2010-10-18
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
package com.dianping.avatar.cache.client;

import com.dianping.cache.core.CacheClientConfiguration;
import com.dianping.cache.ehcache.EhcacheConfiguration;
import com.dianping.remote.cache.dto.CacheConfigurationDTO;

/**
 * EhcacheClient Configuration Parser
 * @author danson.liu
 *
 */
public class EhcacheClientConfigurationParser implements CacheClientConfigurationParser {

	@Override
	public CacheClientConfiguration parse(CacheConfigurationDTO detail) {
		//Can extend some ehcache configuration here
	    EhcacheConfiguration config = new EhcacheConfiguration();
	    config.setClientClazz(detail.getClientClazz());
	    return config;
	}

}
