/**
 * Project: avatar-cache
 * 
 * File Created at 2010-7-12
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
package com.dianping.squirrel.client.impl.memcached;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

import net.spy.memcached.transcoders.Transcoder;

import com.dianping.squirrel.client.config.StoreClientConfig;
import com.dianping.squirrel.client.core.CacheConfigurationListener;
import com.dianping.squirrel.common.exception.StoreInitializeException;

/**
 * The configuration for memcached client implementation(Spymemcached)
 * 
 * @author guoqing.chen
 * 
 */
public class MemcachedClientConfig implements StoreClientConfig {

	/**
	 * All servers
	 */
	private List<String> servers = new ArrayList<String>();

	/**
	 * Transcoder
	 */
	private Transcoder<Object> transcoder;

	private String clientClazz;

	private CacheConfigurationListener cacheConfigurationListener;

	public CacheConfigurationListener getCacheConfigurationListener() {
		return cacheConfigurationListener;
	}

	public void setCacheConfigurationListener(CacheConfigurationListener cacheConfigurationListener) {
		this.cacheConfigurationListener = cacheConfigurationListener;
	}

	/**
	 * Add memcached server and prot
	 */
	public void addServer(String server, int port) {
		addServer(server + ":" + port);
	}

	public void addServer(String address) {
		servers.add(address);
	}

	public void setServerList(List<String> servers) {
		this.servers = servers;
	}

	public List<String> getServerList() {
		return this.servers;
	}

	public String getServers() {

		StringBuffer sb = new StringBuffer();

		for (String server : servers) {
			sb.append(server.trim());
			sb.append(" ");
		}

		return sb.toString().trim();
	}

	/**
	 * @return the transcoder
	 */
	public Transcoder<Object> getTranscoder() {
		return transcoder;
	}

	/**
	 * @param transcoder
	 *            the transcoder to set
	 */
	public void setTranscoder(Transcoder<Object> transcoder) {
		this.transcoder = transcoder;
	}

	@SuppressWarnings("unchecked")
	public void setTranscoderClass(Class<?> transcoderClass) throws Exception {
		Transcoder<Object> transcoder = (Transcoder<Object>) transcoderClass.newInstance();
		setTranscoder(transcoder);
	}

	public String getClientClazz() {
		return this.clientClazz;
	}

	public void setClientClazz(String clientClazz) {
		this.clientClazz = clientClazz;
	}

	@Override
    public void init() throws StoreInitializeException {
        ensureSpymemcachedVersion();
    }

    private void ensureSpymemcachedVersion() throws StoreInitializeException {
        try {
            Class.forName("net.spy.memcached.internal.GetCompletionListener");
        } catch (ClassNotFoundException e) {
            throw new StoreInitializeException("spymemcached version is too low, please upgrade to version 2.11.x or above", e);
        }
    }

	public boolean isVersionChanged(String category, int recentSeconds) {
		if (cacheConfigurationListener != null) {
			return cacheConfigurationListener.isVersionChanged(category, recentSeconds);
		} else {
			return false;
		}
	}
	
	public String toString() {
	    return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
	            .append("servers", getServers())
	            .toString();
	}
	
}
