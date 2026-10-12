package com.unimend.weather;

import android.app.job.JobParameters;
import android.app.job.JobService;
import java.util.concurrent.*;
import org.json.JSONObject;

public class WeatherJob extends JobService {
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private final ConcurrentHashMap<Integer,Future<?>> running=new ConcurrentHashMap<>();
    @Override public boolean onStartJob(JobParameters p) {
        WeatherStore store=new WeatherStore(this);
        if(!store.configured()) return false;
        android.net.ConnectivityManager connectivity=(android.net.ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        android.net.NetworkCapabilities network=connectivity.getNetworkCapabilities(connectivity.getActiveNetwork());
        if(network==null || !network.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            store.error("当前离线"); store.prefs.edit().putBoolean("loading",false).apply();
            WeatherWidget.publish(this);
            sendBroadcast(new android.content.Intent("com.unimend.weather.UPDATED").setPackage(getPackageName()));
            return false;
        }
        final double lat=store.lat(),lon=store.lon();
        store.prefs.edit().putBoolean("loading",true).apply(); WeatherWidget.publish(this);
        Future<?> future=IO.submit(()-> {
            boolean retry=false;
            try {
                JSONObject data=WeatherSource.fetch(lat,lon);
                if(!Thread.currentThread().isInterrupted() && store.lat()==lat && store.lon()==lon) {
                    store.save(data); android.util.Log.i("WeatherJob","天气更新成功；空气可用="+data.has("aqi"));
                }
            } catch(Exception e) {
                if(!Thread.currentThread().isInterrupted() && store.lat()==lat && store.lon()==lon) { store.error("网络或数据暂不可用"); retry=true; }
                android.util.Log.w("WeatherJob","天气更新失败",e);
            } finally {
                boolean cancelled=Thread.currentThread().isInterrupted();
                store.prefs.edit().putBoolean("loading",false).apply();
                if(!cancelled) {
                    WeatherWidget.publish(this);
                    sendBroadcast(new android.content.Intent("com.unimend.weather.UPDATED").setPackage(getPackageName()));
                    jobFinished(p,retry);
                }
                running.remove(p.getJobId());
            }
        });
        running.put(p.getJobId(),future); return true;
    }
    @Override public boolean onStopJob(JobParameters p) {
        Future<?> f=running.remove(p.getJobId()); if(f!=null) f.cancel(true);
        new WeatherStore(this).prefs.edit().putBoolean("loading",false).apply();
        return WeatherWidget.ids(this).length>0;
    }
}
