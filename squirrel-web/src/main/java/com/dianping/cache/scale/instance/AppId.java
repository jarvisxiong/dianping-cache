package com.dianping.cache.scale.instance;

public enum AppId {
	redis10("redis10", 1, 6379), redis15("redis15", 1, 6379), redis20("redis20", 1, 6379), redis30(
			"redis30", 1, 6379), memcached8("memcached5", 0, 11211),memcached10("memcached10", 0, 11211),memcached20("memcached20", 0, 11211),memcached30("memcached30", 0, 11211);
	private String value;
	private int type;
	private int port;

	AppId() {
	}

	AppId(String value, int type, int port) {
		this.value = value;
		this.type = type;
		this.port = port;
	}

	@Override
	public String toString() {
		return value;
	}

	public int getType() {
		return type;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

}
