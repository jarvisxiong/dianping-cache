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
package com.dianping.squirrel.client.config;

import com.dianping.remote.cache.dto.CacheConfigurationDTO;

/**
 * Parse store client configuration
 * 
 * @author danson.liu
 */
public interface StoreClientConfigParser {

	/**
	 * @param detail
	 * @return
	 */
	StoreClientConfig parse(CacheConfigurationDTO detail);

}
