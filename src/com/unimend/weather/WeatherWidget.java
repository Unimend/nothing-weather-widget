package com.unimend.weather;

import android.app.PendingIntent;
import android.app.job.*;
import android.appwidget.*;
import android.content.*;
import android.os.*;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.RemoteViews;

public class WeatherWidget extends AppWidgetProvider {
    static final String TOGGLE="com.unimend.weather.TOGGLE", REFRESH="com.unimend.weather.REFRESH";
    static final int PERIODIC=701, ONCE=702;
    private static final android.util.SparseIntArray animations=new android.util.SparseIntArray();
    static int[] ids(Context c) { return AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c,WeatherWidget.class)); }
    static void publish(Context c) { for(int id:ids(c)) draw(c,id,false); }
    static void draw(Context c,int id,boolean feedback) { draw(c,id,feedback,-1); }
    static void draw(Context c,int id,boolean feedback,int frame) {
        WeatherStore s=new WeatherStore(c,id); AppWidgetManager manager=AppWidgetManager.getInstance(c);
        Bundle o=manager.getAppWidgetOptions(id);
        float side=Math.max(80,Math.min(o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,100)-18,o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,110)-21));
        int pixels=Math.max(256,Math.min(1000,Math.round(side*c.getResources().getDisplayMetrics().density)));
        RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget);
        try {
            Bitmap b=WeatherRenderer.render(c,s,s.page(id),pixels,feedback,frame);
            v.setImageViewBitmap(R.id.content,b); v.setViewVisibility(R.id.placeholder,View.GONE);
            // Android parcels the bitmap while sending; do not recycle before update completes.
        } catch(Exception e) { android.util.Log.e("WeatherWidget","绘制失败",e); v.setTextViewText(R.id.placeholder,"点击设置天气"); }
        JSONObjectDescription.attach(v,s,id);
        Intent open=new Intent(c,WeatherActivity.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);
        v.setOnClickPendingIntent(R.id.open,PendingIntent.getActivity(c,id,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT));
        Intent toggle=new Intent(c,WeatherWidget.class).setAction(TOGGLE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);
        v.setOnClickPendingIntent(R.id.toggle,PendingIntent.getBroadcast(c,id,toggle,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT));
        manager.updateAppWidget(id,v);
    }
    static void animate(Context c,int id) {
        final Context app=c.getApplicationContext(); final int token;
        synchronized(animations) { token=animations.get(id,0)+1; animations.put(id,token); }
        Handler h=new Handler(Looper.getMainLooper());
        for(int frame=0;frame<6;frame++) {
            final int f=frame;
            h.postDelayed(()-> {
                synchronized(animations) { if(animations.get(id)!=token) return; }
                boolean found=false; for(int x:ids(app)) if(x==id) found=true;
                if(found) draw(app,id,false,f);
            },frame*230L);
        }
        h.postDelayed(()-> {
            synchronized(animations) { if(animations.get(id)!=token) return; animations.delete(id); }
            for(int x:ids(app)) if(x==id) draw(app,id,false,-1);
        },1480L);
    }
    static void publishAnimated(Context c) { for(int id:ids(c)) animate(c,id); }
    static void schedule(Context c,boolean immediate) {
        JobScheduler jobs=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(ids(c).length==0) { jobs.cancel(PERIODIC); }
        else if(jobs.getPendingJob(PERIODIC)==null) {
            jobs.schedule(new JobInfo.Builder(PERIODIC,new ComponentName(c,WeatherJob.class))
                .setPeriodic(30*60*1000L,5*60*1000L).setPersisted(true).build());
        }
        boolean configured=false; for(int id:ids(c)) if(new WeatherStore(c,id).configured()) configured=true;
        if(immediate && configured) {
            jobs.schedule(new JobInfo.Builder(ONCE,new ComponentName(c,WeatherJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(0).build());
        }
    }
    @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids) { publish(c); schedule(c,true); }
    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,Bundle options) { draw(c,id,false); }
    @Override public void onDeleted(Context c,int[] ids) { for(int id:ids) new WeatherStore(c,id).delete(); }
    @Override public void onDisabled(Context c) {
        JobScheduler j=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE); j.cancel(PERIODIC); j.cancel(ONCE);
    }
    @Override public void onReceive(Context c,Intent intent) {
        super.onReceive(c,intent); String action=intent.getAction();
        if(TOGGLE.equals(action)) {
            int id=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1); boolean found=false;
            for(int x:ids(c)) if(x==id) found=true; if(!found) return;
            new WeatherStore(c,id).toggle(id); animate(c,id);
        } else if(REFRESH.equals(action)) { publish(c); schedule(c,true); }
        else if(Intent.ACTION_BOOT_COMPLETED.equals(action)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)||Intent.ACTION_TIME_CHANGED.equals(action)||Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
            for(int id:ids(c)) new WeatherStore(c,id).loading(false);
            if(Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) ((JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE)).cancel(PERIODIC);
            publish(c); schedule(c,true);
        }
    }
    private static class JSONObjectDescription {
        static void attach(RemoteViews v,WeatherStore s,int id) {
            org.json.JSONObject d=s.data(); String desc=s.city()+"，";
            if(d.has("temp")) desc+=Math.round(d.optDouble("temp"))+"摄氏度，"+WeatherSource.condition(d.optInt("code"))+(s.stale(d)?"，缓存已过期":"");
            else desc+="尚无天气数据";
            v.setContentDescription(R.id.content,desc);
            v.setContentDescription(R.id.toggle,s.page(id)==0?"切换到空气与降雨页":"切换到当前天气页");
        }
    }
}
