package com.dianping.squirrel.client.core;

public interface CacheCallback<T> {

	public void onSuccess(T result);

	public void onFailure(String msg, Throwable e);
}