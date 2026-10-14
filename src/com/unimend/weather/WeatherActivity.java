package com.unimend.weather;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.appwidget.AppWidgetManager;
import android.widget.*;
import android.view.View;
import java.util.concurrent.*;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import org.json.*;

public class WeatherActivity extends Activity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView details; private ImageView preview; private EditText city; private Button search;
    private WeatherStore store;
    private int widgetId;
    private final BroadcastReceiver updates=new BroadcastReceiver() {
        @Override public void onReceive(Context c,Intent i) { show(); }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); widgetId=getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,0); store=new WeatherStore(this,widgetId);
        getWindow().setStatusBarColor(Color.BLACK); getWindow().setNavigationBarColor(Color.BLACK);
        LinearLayout body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(24),dp(20),dp(24),dp(28)); body.setBackgroundColor(Color.BLACK);
        ScrollView scroll=new ScrollView(this); scroll.addView(body); setContentView(scroll);
        TextView title=new TextView(this); title.setText(widgetId>0?"点阵天气 · 当前组件":"点阵天气 · 新组件默认值"); title.setTextSize(28); title.setTextColor(Color.WHITE); body.addView(title);
        preview=new ImageView(this); preview.setScaleType(ImageView.ScaleType.FIT_CENTER); body.addView(preview,new LinearLayout.LayoutParams(-1,dp(240)));
        preview.setOnClickListener(v->{store.toggle(0);show();}); preview.setContentDescription("点击切换天气预览页");
        details=new TextView(this); details.setTextSize(16); details.setTextColor(Color.LTGRAY); details.setLineSpacing(dp(5),1); body.addView(details);
        Button refresh=new Button(this); refresh.setText("刷新天气"); body.addView(refresh); refresh.setOnClickListener(v->{WeatherWidget.schedule(this,true); Toast.makeText(this,"已安排刷新，联网后更新",Toast.LENGTH_SHORT).show();});
        city=new EditText(this); city.setSingleLine(true); city.setTextColor(Color.WHITE); city.setHintTextColor(Color.GRAY); city.setHint("搜索城市，例如 杭州 / Hangzhou"); body.addView(city);
        search=new Button(this); search.setText("搜索并选择城市"); body.addView(search); search.setOnClickListener(v->search());
        Button hangzhou=new Button(this); hangzhou.setText("选择杭州"); body.addView(hangzhou); hangzhou.setOnClickListener(v->choose("杭州",30.2741,120.1551));
        TextView source=new TextView(this); source.setTextSize(13); source.setTextColor(Color.GRAY);
        source.setText("数据：Open-Meteo / CAMS（CC BY 4.0）\n空气质量为模型估算，US AQI 使用美国标准。\n降雨概率为未来约3小时的最高小时概率。\n约30分钟后台刷新，系统可能延迟；手动刷新不需定位。\n设计参考 Nothing KDE Widgets，图标为本项目原创，字体 Doto（SIL OFL）。\n\nhttps://open-meteo.com/\nhttps://atmosphere.copernicus.eu/\nhttps://github.com/jaxparrow07/nothing-kde-widgets");
        android.text.util.Linkify.addLinks(source,android.text.util.Linkify.WEB_URLS); source.setMovementMethod(android.text.method.LinkMovementMethod.getInstance()); body.addView(source);
    }
    private int dp(float n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private void choose(String name,double lat,double lon) {
        store.city(name,lat,lon); store.loading(false);
        if(widgetId>0) WeatherWidget.draw(this,widgetId,false); else WeatherWidget.publish(this);
        WeatherWidget.schedule(this,true); show();
        Toast.makeText(this,"已为当前组件选择"+name,Toast.LENGTH_SHORT).show();
    }
    private void search() {
        String name=city.getText().toString().trim(); if(name.length()<2) { city.setError("请输入至少两个字符"); return; }
        search.setEnabled(false); search.setText("搜索中…");
        io.execute(()-> {
            try {
                JSONArray results=WeatherSource.search(name);
                handler.post(()-> {
                    if(isFinishing()||isDestroyed()) return;
                    search.setEnabled(true); search.setText("搜索并选择城市");
                    if(results==null||results.length()==0) { Toast.makeText(this,"没有找到，可尝试英文城市名",Toast.LENGTH_LONG).show(); return; }
                    String[] labels=new String[results.length()];
                    for(int i=0;i<labels.length;i++) { JSONObject r=results.optJSONObject(i); labels[i]=r.optString("name")+" · "+r.optString("admin1")+" · "+r.optString("country"); }
                    new AlertDialog.Builder(this).setTitle("选择城市").setItems(labels,(dialog,index)-> {
                        JSONObject r=results.optJSONObject(index); choose(r.optString("name"),r.optDouble("latitude"),r.optDouble("longitude"));
                    }).show();
                });
            } catch(Exception e) { android.util.Log.w("WeatherActivity","城市搜索失败",e); handler.post(()-> { if(isFinishing()||isDestroyed()) return; search.setEnabled(true); search.setText("搜索并选择城市"); Toast.makeText(this,"城市搜索失败，请检查网络",Toast.LENGTH_LONG).show(); }); }
        });
    }
    private void show() {
        preview.setImageBitmap(WeatherRenderer.render(this,store,store.page(0),Math.min(1000,dp(240)),false));
        JSONObject d=store.data();
        if(!store.configured()) { details.setText("先选择城市，再将“点阵天气”放到桌面。\n建议1×1，与指南针并排放置。右侧点击切页，主体点击打开详情。"); return; }
        if(!d.has("temp")) { details.setText(store.city()+"\n"+(store.hasError()?"请求失败，请联网后刷新":"等待天气数据…")); return; }
        SimpleDateFormat time=new SimpleDateFormat("MM-dd HH:mm",Locale.US); time.setTimeZone(TimeZone.getTimeZone(d.optString("timezone","Asia/Shanghai")));
        String air=d.has("aqi")?String.format(Locale.US,"US AQI %.0f · PM2.5 %.1f μg/m³\n空气数据时间 %s",d.optDouble("aqi"),d.optDouble("pm"),time.format(new Date(d.optLong("airTime")))):"空气数据暂不可用";
        details.setText(String.format(Locale.US,"%s · %s\n温度 %.1f°C · 体感 %.1f°C\n最高 %.0f°C · 最低 %.0f°C\n风速 %.1f m/s · 风向 %.0f°（来向）\n%s\n未来3h降雨概率 %s\n天气数据时间 %s\n成功获取时间 %s%s",store.city(),WeatherSource.condition(d.optInt("code")),d.optDouble("temp"),d.optDouble("feel"),d.optDouble("high"),d.optDouble("low"),d.optDouble("wind"),d.optDouble("direction"),air,d.optInt("rain",-1)<0?"--":d.optInt("rain")+"%",time.format(new Date(d.optLong("observed"))),time.format(new Date(d.optLong("fetched"))),store.stale(d)?"\n缓存已过期或刷新失败":""));
    }
    @Override protected void onResume() { super.onResume(); registerReceiver(updates,new IntentFilter("com.unimend.weather.UPDATED")); show(); }
    @Override protected void onPause() { unregisterReceiver(updates); super.onPause(); }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); io.shutdownNow(); super.onDestroy(); }
}
