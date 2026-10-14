package com.unimend.weather;

import android.app.job.JobParameters;
import android.app.job.JobService;
import java.util.concurrent.*;
import org.json.JSONObject;

public class WeatherJob extends JobService {
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private final ConcurrentHashMap<Integer,Future<?>> running=new ConcurrentHashMap<>();
    @Override public boolean onStartJob(JobParameters p) {
        int[] widgetIds=WeatherWidget.ids(this);
        boolean configured=false; for(int id:widgetIds) if(new WeatherStore(this,id).configured()) configured=true;
        if(!configured) return false;
        android.net.ConnectivityManager connectivity=(android.net.ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        android.net.NetworkCapabilities network=connectivity.getNetworkCapabilities(connectivity.getActiveNetwork());
        if(network==null || !network.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            for(int id:widgetIds) { WeatherStore store=new WeatherStore(this,id); if(store.configured()) { store.error("当前离线"); store.loading(false); } }
            WeatherWidget.publish(this);
            sendBroadcast(new android.content.Intent("com.unimend.weather.UPDATED").setPackage(getPackageName()));
            return false;
        }
        for(int id:widgetIds) { WeatherStore store=new WeatherStore(this,id); if(store.configured()) store.loading(true); }
        WeatherWidget.publish(this);
        Future<?> future=IO.submit(()-> {
            boolean retry=false;
            for(int id:widgetIds) {
                if(Thread.currentThread().isInterrupted()) break;
                WeatherStore store=new WeatherStore(this,id);
                if(!store.configured()) continue;
                final double lat=store.lat(),lon=store.lon();
                try {
                    JSONObject data=WeatherSource.fetch(lat,lon);
                    if(!Thread.currentThread().isInterrupted() && store.lat()==lat && store.lon()==lon) {
                        store.save(data); android.util.Log.i("WeatherJob","组件"+id+"天气更新成功；空气可用="+data.has("aqi"));
                    }
                } catch(Exception e) {
                    if(!Thread.currentThread().isInterrupted() && store.lat()==lat && store.lon()==lon) { store.error("网络或数据暂不可用"); retry=true; }
                    android.util.Log.w("WeatherJob","组件"+id+"天气更新失败",e);
                } finally {
                    store.loading(false);
                }
            }
            boolean cancelled=Thread.currentThread().isInterrupted();
            if(!cancelled) {
                WeatherWidget.publishAnimated(this);
                sendBroadcast(new android.content.Intent("com.unimend.weather.UPDATED").setPackage(getPackageName()));
                jobFinished(p,retry);
            }
            running.remove(p.getJobId());
        });
        running.put(p.getJobId(),future); return true;
    }
    @Override public boolean onStopJob(JobParameters p) {
        Future<?> f=running.remove(p.getJobId()); if(f!=null) f.cancel(true);
        for(int id:WeatherWidget.ids(this)) new WeatherStore(this,id).loading(false);
        return WeatherWidget.ids(this).length>0;
    }
}
