package com.dianping.cache.controller;

import com.dianping.cache.entity.CacheConfiguration;
import com.dianping.cache.entity.MemcachedStats;
import com.dianping.cache.entity.Server;
import com.dianping.cache.entity.ServerStats;
import com.dianping.cache.service.CacheConfigurationService;
import com.dianping.cache.service.MemcachedStatsService;
import com.dianping.cache.service.ServerService;
import com.dianping.cache.service.ServerStatsService;
import com.dianping.squirrel.view.highcharts.ChartsBuilder;
import com.dianping.squirrel.view.highcharts.HighChartsWrapper;
import com.dianping.squirrel.view.highcharts.statsdata.MemcachedStatsData;
import com.dianping.squirrel.view.highcharts.statsdata.ServerStatsData;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Controller
public class MonitorController  extends AbstractSidebarController {
	
	
	@Resource(name = "cacheConfigurationService")
	private CacheConfigurationService cacheConfigurationService ;
	
	@Resource(name = "memcacheStatsService")
	private MemcachedStatsService memcacheStatsService;
	
	@Resource(name = "serverService")
	private ServerService serverService;
	
	@Resource(name = "serverStatsService")
	private ServerStatsService serverStatsService;
	
	@RequestMapping(value = "/monitor/cluster")
	public ModelAndView viewCacheConfig(){
		
		subside = "mdashboard";
		return new ModelAndView("monitor/cluster",createViewMap());
	}
	
	@RequestMapping(value = "/monitor/dashboard")
	public ModelAndView viewClusterDashBoard(){
		
		subside = "dashboard";
		return new ModelAndView("monitor/dashboard",createViewMap());
	}
	
	
	@RequestMapping(value = "/monitor/nodedashboard")
	public ModelAndView viewServers() {

		subside = "node";
		return new ModelAndView("monitor/nodedashboard");
	}
	
	@RequestMapping(value = "/monitor/servers")
	@ResponseBody
	public List<Server> getServers(){
		return serverService.findAll();
	}
	
	@RequestMapping(value = "/monitor/dashboardinfo")
	@ResponseBody
	public List<HashMap<String, Object>> dashBoard(){
		List<CacheConfiguration> configList = cacheConfigurationService.findAll();
		List<HashMap<String, Object>> data = new ArrayList<HashMap<String,Object>>();
  
		Map<String, Map<String, Object>> currentServerStats = this.getCurrentServerStatsData();
		for (CacheConfiguration item : configList) { 
			//遍历所有的集群  对于集群名称为memcached的进行监控
			if(item.getCacheKey().contains("memcached")
					&& !"memcached-leo".equals(item.getCacheKey())
					&& "".equals(item.getSwimlane())){
				HashMap<String, Object> info = new HashMap<String, Object>();
				info.put("key", item.getCacheKey());

				List<String> serverList = item.getServerList();
				if(serverList == null)
					continue;
				long mem = 0,memused=0;
				long qpsmax = Long.MIN_VALUE,qpsmin = Long.MAX_VALUE,qpsavg = 0;
				long evictmax = Long.MIN_VALUE,evictmin = Long.MAX_VALUE, evictavg = 0;
				float hitmax = -1,hitmin=1,hitavg=0;
				int connmax = Integer.MIN_VALUE,connmin = Integer.MAX_VALUE,connavg = 0;
				
				int alarm = 3;
				int alive = 0;
				for(String server : serverList){
					if(currentServerStats.get(server) != null){
						alive ++;
						mem += (Long)currentServerStats.get(server).get("max_memory");
						memused += (Long)currentServerStats.get(server).get("used_memory");
						long currqps = (Long)currentServerStats.get(server).get("QPS");
						qpsavg += currqps;
						if(currqps > qpsmax)
							qpsmax = currqps;
						if(currqps < qpsmin)
							qpsmin = currqps;
						
						float currhit = (Float)currentServerStats.get(server).get("hitrate");
						hitavg += currhit;
						if(currhit > hitmax)
							hitmax = currhit;
						if(currhit < hitmin)
							hitmin = currhit;
						
						int currConn = (Integer)currentServerStats.get(server).get("curr_conn");
						connavg += currConn;
						if(currConn > connmax)
							connmax = currConn;
						if(currConn < connmin)
							connmin = currConn;
						
						long currEvict = (Long)currentServerStats.get(server).get("evict");
						evictavg += currEvict;
						if(currEvict > evictmax)
							evictmax = currEvict;
						if(currEvict < evictmin)
							evictmin = currEvict;
						
					}else{
						alarm = 1;
					}
				}
				float usage = (float)memused/mem;
				if(usage > 0.7)
					alarm = 2;
				if(hitmin < 0.97)
					alarm = 1;
				if(alive > 0){
					qpsavg = qpsavg/alive;
					hitavg = hitavg/alive;
					connavg = connavg/alive;
					evictavg = evictavg/alive;
				}else{
					qpsavg = 0;
					hitavg = 0;
					connavg = 0;
					evictavg = 0;
				}
				info.put("numbers", serverList.size() - alive +"/"+serverList.size());
				info.put("memory", (float)(Math.round(usage*100)) +"%/" + mem+"M");
				info.put("QPS", qpsmax+"/"+qpsmin+"/"+qpsavg);
				info.put("hitrate", (float)(Math.round(hitmax*100))/100 +"/"+(float)(Math.round(hitmin*100))/100+"/"+(float)(Math.round(hitavg*100))/100);
				info.put("conn", connmax +"/"+ connmin +"/"+ connavg);
				info.put("evict", evictmax + "/" + evictmin + "/" + evictavg);
				info.put("alarm",alarm);
				
				data.add(info);
			}
		}
		
		return data;
	}
	
	
	@RequestMapping(value = "/monitor/cluster/{cluster}")
	@ResponseBody
	public List<HighChartsWrapper> getClusterStats(@PathVariable("cluster") String cluster,@RequestParam("endTime") long endTime){
		
		Map<String,List<MemcachedStats>> stats0 = getClusterServerStats(cluster, endTime);
		
		Map<String,MemcachedStatsData> data0 = convertStats(stats0);
		
		Map<String,List<ServerStats>> stats = getClusterServerStats2(cluster, endTime);
		
		Map<String,ServerStatsData> data = convertStats2(stats);
		
		List<HighChartsWrapper> charts = ChartsBuilder.buildMemcachedStatsCharts(data0);
		charts.addAll(ChartsBuilder.buildServerStatsCharts(data));
		return charts;
	}
	
	

	private Map<String, MemcachedStatsData> convertStats(
			Map<String, List<MemcachedStats>> stats) {
		
		Map<String,MemcachedStatsData> result = new HashMap<String,MemcachedStatsData>();
		for(Map.Entry<String, List<MemcachedStats>> item : stats.entrySet()){
			if(item.getValue() != null && item.getValue().size() > 1){
				result.put(item.getKey(), new MemcachedStatsData(item.getValue()));
			}
//			else{
//				result.put(item.getKey(),null);
//			}
		}
		return result;
	}

	private Map<String, ServerStatsData> convertStats2(
			Map<String, List<ServerStats>> stats) {
		
		Map<String,ServerStatsData> result = new HashMap<String,ServerStatsData>();
		for(Map.Entry<String, List<ServerStats>> item : stats.entrySet()){
			if(item.getValue() != null && item.getValue().size() > 1)
				result.put(item.getKey(), new ServerStatsData(item.getValue()));
		}
		return result;
	}
	
	
	private  Map<String,List<ServerStats>> getClusterServerStats2(String cluster,long endTime) {
		//get all server in cluster
		CacheConfiguration configuration = cacheConfigurationService.find(cluster);
		List<String> servers = configuration.getServerList();
		long start = (endTime - TimeUnit.MILLISECONDS.convert(60, TimeUnit.MINUTES))/1000;
		long end = endTime/1000;
		
		Map<String,List<ServerStats>> result = new HashMap<String,List<ServerStats>>();
		for(String server : servers){
			result.put(server,getServerStats(server,start,end));
		}
		return result;
	}



	private List<ServerStats> getServerStats(String address, long start,
			long end) {
		List<ServerStats> result = serverStatsService.findByServerWithInterval(address, start, end);
		return result;
	}

	private  Map<String,List<MemcachedStats>> getClusterServerStats(String cluster, long endTime) {
		//get all server in cluster
		CacheConfiguration configuration = cacheConfigurationService.find(cluster);
		List<String> servers = configuration.getServerList();
		
		long start = (endTime - TimeUnit.MILLISECONDS.convert(60, TimeUnit.MINUTES))/1000;
		long end = endTime/1000;
		
		Map<String,List<MemcachedStats>> result = new HashMap<String,List<MemcachedStats>>();
		for(String server : servers){
			result.put(server,getMemcacheStats(server,start,end));
		}
		return result;
	}

	private  Map<String,Map<String,Object>> getCurrentServerStatsData(){
		Map<String, List<MemcachedStats>> serverStats = getAllServerStats();
		Map<String,MemcachedStatsData> serverStatsData = convertStats(serverStats);
		Map<String,Map<String,Object>> currentStats = new HashMap<String,Map<String,Object>>();
		for(Map.Entry<String,MemcachedStatsData> item : serverStatsData.entrySet()){
			Map<String,Object> temp = new HashMap<String,Object>();
			if(item.getValue() == null){
				continue;
			}
			int length = item.getValue().getLength();
			temp.put("QPS", item.getValue().getHitDatas()[length-1]);
			temp.put("max_memory", item.getValue().getMax_memory());
			temp.put("used_memory", item.getValue().getBytes()[length-1]);
			temp.put("curr_conn", item.getValue().getConnDatas()[length-1]);
			temp.put("evict", item.getValue().getEvictionsDatas()[length-1]);
			long miss = item.getValue().getGetMissDatas()[length-1];
			long get = item.getValue().getGetsDatas()[length-1];
			float hitrate;
			if(get > 0){
				hitrate  = (float) ((double)get/(miss+get));
			}
			else{
				hitrate = 1.0f;
			}
			temp.put("hitrate", hitrate);
			currentStats.put(item.getKey(), temp);
		}
		return currentStats;
	}
	
	
	private  Map<String,List<MemcachedStats>> getAllServerStats() {
		//get all server in cluster
		List<Server> sc = serverService.findAllMemcachedServers();
		
		long start = (System.currentTimeMillis()- TimeUnit.MILLISECONDS.convert(2, TimeUnit.MINUTES))/1000;
		long end = (System.currentTimeMillis())/1000;
		
		Map<String,List<MemcachedStats>> result = new HashMap<String,List<MemcachedStats>>();
		for(Server server : sc){
			result.put(server.getAddress(),getMemcacheStats(server.getAddress(),start,end));
		}
		return result;
	}
	
	private List<MemcachedStats> getMemcacheStats(String address, long start, long end){
		List<MemcachedStats> result = memcacheStatsService.findByServerWithInterval(address, start, end);
		return result;
	}
	
	private String subside = "realtime";
	
	@Override
	protected String getSide() {
		return "monitor";
	}

	@Override
	public String getSubSide() {
		return subside;
	}

}
