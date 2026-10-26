package com.unimend.weather;

import android.app.*;
import android.appwidget.AppWidgetManager;
import android.os.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.content.*;
import android.text.util.Linkify;
import android.view.*;
import android.widget.*;
import java.util.concurrent.*;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import org.json.*;

public class WeatherActivity extends Activity {
    private static final int WHITE=Color.WHITE, MUTED=Color.rgb(145,145,150), PANEL=Color.rgb(9,9,10);
    private static final int LINE=Color.rgb(36,36,39), RED=Color.rgb(235,48,54), YELLOW=Color.rgb(255,210,35);
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private ImageView preview;
    private EditText city;
    private TextView search,heroCity,heroTemp,heroStatus,feelValue,rangeValue,windValue,airValue,rainValue,timeValue;
    private WeatherStore store;
    private Typeface doto,zpix;
    private int widgetId;
    private final BroadcastReceiver updates=new BroadcastReceiver() {
        @Override public void onReceive(Context c,Intent i) { show(); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        widgetId=getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,0);
        store=new WeatherStore(this,widgetId);
        doto=Typeface.createFromAsset(getAssets(),"doto.ttf");
        zpix=Typeface.createFromAsset(getAssets(),"zpix.ttf");
        getWindow().setStatusBarColor(Color.BLACK); getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().getDecorView().setSystemUiVisibility(0);

        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Color.BLACK);
        LinearLayout body=column(); body.setPadding(dp(20),dp(18),dp(20),dp(32)); scroll.addView(body); setContentView(scroll);

        LinearLayout head=new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL);
        View mark=new View(this); mark.setBackgroundColor(RED); head.addView(mark,new LinearLayout.LayoutParams(dp(5),dp(34)));
        LinearLayout names=column(); names.setPadding(dp(12),0,0,0);
        names.addView(label("点阵天气",27,WHITE,zpix));
        names.addView(label(widgetId>0?"WEATHER  /  WIDGET "+widgetId:"WEATHER  /  DEFAULT",12,MUTED,doto));
        head.addView(names,new LinearLayout.LayoutParams(0,-2,1)); body.addView(head);

        TextView intro=label(widgetId>0?"正在编辑从桌面打开的这个天气组件":"设置以后新增天气组件使用的默认城市",14,MUTED,null);
        intro.setPadding(0,dp(10),0,dp(18)); body.addView(intro);

        LinearLayout previewCard=card();
        LinearLayout previewHead=row(); previewHead.setGravity(Gravity.CENTER_VERTICAL);
        previewHead.addView(eyebrow("LIVE PREVIEW"),new LinearLayout.LayoutParams(0,-2,1));
        TextView page=action("切换页面",false); page.setOnClickListener(v->{store.toggle(widgetId);show();}); previewHead.addView(page);
        previewCard.addView(previewHead);
        preview=new ImageView(this); preview.setScaleType(ImageView.ScaleType.FIT_CENTER); preview.setContentDescription("天气组件实时预览，点击切换页面");
        preview.setOnClickListener(v->{store.toggle(widgetId);show();});
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(252)); pp.topMargin=dp(8); previewCard.addView(preview,pp);
        body.addView(previewCard,spaced());

        LinearLayout data=card();
        LinearLayout dataHead=row(); dataHead.setGravity(Gravity.CENTER_VERTICAL);
        dataHead.addView(eyebrow("CURRENT CONDITIONS"),new LinearLayout.LayoutParams(0,-2,1));
        heroStatus=pill("WAITING",MUTED); dataHead.addView(heroStatus); data.addView(dataHead);
        LinearLayout hero=row(); hero.setGravity(Gravity.BOTTOM);
        heroCity=label("选择城市",22,WHITE,zpix); heroCity.setPadding(0,dp(18),0,dp(8)); hero.addView(heroCity,new LinearLayout.LayoutParams(0,-2,1));
        heroTemp=label("--°",54,WHITE,doto); heroTemp.setGravity(Gravity.RIGHT); hero.addView(heroTemp); data.addView(hero);
        data.addView(rule());
        LinearLayout metrics1=row(); feelValue=metric(metrics1,"FEELS"); rangeValue=metric(metrics1,"HIGH / LOW"); windValue=metric(metrics1,"WIND"); data.addView(metrics1);
        LinearLayout metrics2=row(); airValue=metric(metrics2,"US AQI"); rainValue=metric(metrics2,"RAIN · 3H"); timeValue=metric(metrics2,"DATA TIME");
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,-2); mp.topMargin=dp(18); data.addView(metrics2,mp);
        body.addView(data,spaced());

        LinearLayout cityCard=card(); cityCard.addView(eyebrow("CITY / INSTANCE"));
        TextView hint=label(widgetId>0?"更改只作用于当前桌面组件，不影响其他城市。":"这里是新组件的默认城市。",14,MUTED,null);
        hint.setPadding(0,dp(8),0,dp(14)); cityCard.addView(hint);
        city=new EditText(this); city.setSingleLine(true); city.setTextColor(WHITE); city.setHintTextColor(Color.rgb(100,100,105)); city.setTextSize(16); city.setHint("杭州 / Hangzhou");
        city.setPadding(dp(15),0,dp(15),0); city.setBackground(box(Color.rgb(14,14,16),LINE,10));
        cityCard.addView(city,new LinearLayout.LayoutParams(-1,dp(52)));
        LinearLayout cityActions=row();
        search=action("搜索城市",true); search.setOnClickListener(v->search()); cityActions.addView(search,new LinearLayout.LayoutParams(0,dp(50),1));
        TextView hangzhou=action("快速选择杭州",false); hangzhou.setOnClickListener(v->choose("杭州",30.2741,120.1551));
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,dp(50),1); hp.leftMargin=dp(10); cityActions.addView(hangzhou,hp);
        LinearLayout.LayoutParams cap=new LinearLayout.LayoutParams(-1,-2); cap.topMargin=dp(12); cityCard.addView(cityActions,cap);
        body.addView(cityCard,spaced());

        LinearLayout foot=card();
        LinearLayout refreshRow=row(); refreshRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout refreshText=column(); refreshText.addView(eyebrow("DATA / REFRESH")); refreshText.addView(label("Open-Meteo 天气 · CAMS 空气质量",13,MUTED,null));
        refreshRow.addView(refreshText,new LinearLayout.LayoutParams(0,-2,1));
        TextView refresh=action("立即刷新",false); refresh.setOnClickListener(v->{WeatherWidget.schedule(this,true);Toast.makeText(this,"已安排刷新",Toast.LENGTH_SHORT).show();}); refreshRow.addView(refresh);
        foot.addView(refreshRow);
        TextView source=label("约 30 分钟后台更新，系统可能延迟。空气质量为模型估算，使用 US AQI 标准；降雨为未来约 3 小时的最高小时概率。\n\nopen-meteo.com  ·  atmosphere.copernicus.eu",12,Color.rgb(105,105,110),null);
        source.setLineSpacing(dp(4),1); source.setPadding(0,dp(14),0,0); Linkify.addLinks(source,Linkify.WEB_URLS); source.setMovementMethod(android.text.method.LinkMovementMethod.getInstance()); foot.addView(source);
        body.addView(foot,spaced());
    }

    private LinearLayout column() { LinearLayout v=new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v=new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); return v; }
    private LinearLayout card() { LinearLayout v=column(); v.setPadding(dp(17),dp(16),dp(17),dp(17)); v.setBackground(box(PANEL,LINE,14)); return v; }
    private LinearLayout.LayoutParams spaced() { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.bottomMargin=dp(14); return p; }
    private GradientDrawable box(int fill,int stroke,float radius) { GradientDrawable d=new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(radius)); d.setStroke(dp(1),stroke); return d; }
    private TextView label(String value,float size,int color,Typeface face) { TextView v=new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color); if(face!=null)v.setTypeface(face); v.setIncludeFontPadding(false); return v; }
    private TextView eyebrow(String value) { TextView v=label(value,13,Color.rgb(190,190,194),doto); v.setLetterSpacing(.08f); return v; }
    private TextView pill(String value,int color) { TextView v=label(value,11,color,doto); v.setGravity(Gravity.CENTER); v.setPadding(dp(10),dp(6),dp(10),dp(5)); v.setBackground(box(Color.rgb(18,18,20),LINE,20)); return v; }
    private TextView action(String value,boolean primary) { TextView v=label(value,14,primary?Color.BLACK:WHITE,zpix); v.setGravity(Gravity.CENTER); v.setMinWidth(dp(92)); v.setMinHeight(dp(48)); v.setPadding(dp(14),0,dp(14),0); v.setBackground(box(primary?WHITE:Color.rgb(16,16,18),primary?WHITE:LINE,10)); v.setClickable(true); v.setFocusable(true); return v; }
    private View rule() { View v=new View(this); v.setBackgroundColor(LINE); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(1)); p.bottomMargin=dp(16); v.setLayoutParams(p); return v; }
    private TextView metric(LinearLayout parent,String name) { LinearLayout cell=column(); TextView nameView=label(name,11,MUTED,doto); TextView value=label("--",18,WHITE,doto); value.setPadding(0,dp(7),0,0); cell.addView(nameView); cell.addView(value); parent.addView(cell,new LinearLayout.LayoutParams(0,-2,1)); return value; }
    private int dp(float n) { return Math.round(n*getResources().getDisplayMetrics().density); }

    private void choose(String name,double lat,double lon) {
        store.city(name,lat,lon); store.loading(false);
        if(widgetId>0) WeatherWidget.draw(this,widgetId,false); else WeatherWidget.publish(this);
        WeatherWidget.schedule(this,true); city.setText(""); show();
        Toast.makeText(this,"已为当前组件选择 "+name,Toast.LENGTH_SHORT).show();
    }
    private void search() {
        String name=city.getText().toString().trim(); if(name.length()<2) { city.setError("请输入至少两个字符"); return; }
        search.setEnabled(false); search.setAlpha(.45f); search.setText("搜索中…");
        io.execute(()-> {
            try {
                JSONArray results=WeatherSource.search(name);
                handler.post(()-> {
                    if(isFinishing()||isDestroyed()) return; resetSearch();
                    if(results==null||results.length()==0) { Toast.makeText(this,"没有找到，可尝试英文城市名",Toast.LENGTH_LONG).show(); return; }
                    String[] labels=new String[results.length()];
                    for(int i=0;i<labels.length;i++) { JSONObject r=results.optJSONObject(i); labels[i]=r.optString("name")+" · "+r.optString("admin1")+" · "+r.optString("country"); }
                    AlertDialog dialog=new AlertDialog.Builder(this,AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("选择城市").setItems(labels,(d,index)-> {
                        JSONObject r=results.optJSONObject(index); choose(r.optString("name"),r.optDouble("latitude"),r.optDouble("longitude"));
                    }).create(); dialog.setOnShowListener(d->dialog.getWindow().setBackgroundDrawable(box(Color.rgb(12,12,14),LINE,14))); dialog.show();
                });
            } catch(Exception e) { android.util.Log.w("WeatherActivity","城市搜索失败",e); handler.post(()-> { if(isFinishing()||isDestroyed()) return; resetSearch(); Toast.makeText(this,"城市搜索失败，请检查网络",Toast.LENGTH_LONG).show(); }); }
        });
    }
    private void resetSearch() { search.setEnabled(true); search.setAlpha(1f); search.setText("搜索城市"); }
    private void show() {
        int pageId=widgetId>0?widgetId:0;
        preview.setImageBitmap(WeatherRenderer.render(this,store,store.page(pageId),Math.min(1000,dp(252)),false));
        JSONObject d=store.data(); heroCity.setText(store.configured()?store.city()+" · "+(d.has("temp")?WeatherSource.condition(d.optInt("code")):"等待数据"):"尚未选择城市");
        if(!store.configured()) { heroTemp.setText("--°"); setStatus("SETUP",YELLOW); clearMetrics(); return; }
        if(!d.has("temp")) { heroTemp.setText("--°"); setStatus(store.hasError()?"RETRY":"WAITING",store.hasError()?RED:MUTED); clearMetrics(); return; }
        SimpleDateFormat time=new SimpleDateFormat("HH:mm",Locale.US); time.setTimeZone(TimeZone.getTimeZone(d.optString("timezone","Asia/Shanghai")));
        heroTemp.setText(String.format(Locale.US,"%.0f°",d.optDouble("temp")));
        if(store.loading()) setStatus("UPDATING",YELLOW); else if(store.stale(d)) setStatus("STALE",RED); else setStatus("LIVE",Color.rgb(65,190,112));
        feelValue.setText(String.format(Locale.US,"%.0f°",d.optDouble("feel")));
        rangeValue.setText(String.format(Locale.US,"%.0f° / %.0f°",d.optDouble("high"),d.optDouble("low")));
        windValue.setText(String.format(Locale.US,"%.1f m/s",d.optDouble("wind")));
        airValue.setText(d.has("aqi")?String.format(Locale.US,"%.0f",d.optDouble("aqi")):"--");
        rainValue.setText(d.optInt("rain",-1)<0?"--":d.optInt("rain")+"%");
        timeValue.setText(time.format(new Date(d.optLong("observed"))));
    }
    private void clearMetrics() { feelValue.setText("--");rangeValue.setText("-- / --");windValue.setText("--");airValue.setText("--");rainValue.setText("--");timeValue.setText("--:--"); }
    private void setStatus(String value,int color) { heroStatus.setText(value); heroStatus.setTextColor(color); }
    @Override protected void onResume() { super.onResume(); registerReceiver(updates,new IntentFilter("com.unimend.weather.UPDATED")); show(); }
    @Override protected void onPause() { unregisterReceiver(updates); super.onPause(); }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); io.shutdownNow(); super.onDestroy(); }
}
