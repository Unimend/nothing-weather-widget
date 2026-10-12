package com.unimend.weather;

import android.content.Context;
import android.graphics.*;
import org.json.JSONObject;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

final class WeatherRenderer {
    private final Canvas canvas;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface doto;
    private static final int GREY=Color.rgb(150,150,150), RED=Color.rgb(235,48,54);
    private WeatherRenderer(Context c,Bitmap b) {
        canvas=new Canvas(b); canvas.scale(b.getWidth()/512f,b.getHeight()/512f);
        doto=Typeface.createFromAsset(c.getAssets(),"doto.ttf");
    }
    static Bitmap render(Context c,WeatherStore store,int page,int pixels,boolean feedback) {
        Bitmap b=Bitmap.createBitmap(pixels,pixels,Bitmap.Config.ARGB_8888); b.setDensity(Bitmap.DENSITY_NONE);
        new WeatherRenderer(c,b).draw(store,page,feedback); return b;
    }
    private void text(String value,float x,float y,float size,int color,boolean dot) {
        paint.setTypeface(dot?doto:Typeface.create("sans-serif-medium",Typeface.NORMAL));
        paint.setTextSize(size); paint.setColor(color); paint.setStyle(Paint.Style.FILL);
        Rect bounds=new Rect(); paint.getTextBounds(value,0,value.length(),bounds);
        canvas.drawText(value,x-(bounds.left+bounds.right)/2f,y-(bounds.top+bounds.bottom)/2f,paint);
    }
    private String shortCity(String name) { return name.length()>8?name.substring(0,7)+"…":name; }
    private static String degree(double n) { return String.format(Locale.US,"%.0f°",n); }
    private void draw(WeatherStore s,int page,boolean feedback) {
        JSONObject d=s.data(); boolean valid=d.has("temp");
        if(!s.configured()) {
            text("WEATHER",256,162,58,Color.WHITE,true);
            icon(2,true,feedback?RED:Color.WHITE);
            text("点击选择城市",256,400,32,GREY,false); return;
        }
        boolean stale=valid && s.stale(d);
        String status=s.prefs.getBoolean("loading",false)?"UPDATING":!valid?(s.prefs.contains("error")?"RETRY":"WAITING"):stale?"STALE":"";
        if(!valid) {
            text("--°",244,142,110,Color.WHITE,true); icon(2,true,Color.rgb(70,70,70));
            text(shortCity(s.city()),244,384,32,Color.WHITE,false);
            text(status,244,440,23,RED,false);
        } else if(page==0) {
            text(degree(d.optDouble("temp")),256,130,134,Color.WHITE,true);
            icon(d.optInt("code"),d.optInt("day",1)==1,feedback?RED:Color.WHITE);
            text(shortCity(s.city()),256,365,52,Color.WHITE,false);
            text(WeatherSource.condition(d.optInt("code")),256,425,52,GREY,false);
        } else {
            text("↑ "+degree(d.optDouble("high")),256,105,82,Color.WHITE,true);
            text("↓ "+degree(d.optDouble("low")),256,195,82,Color.WHITE,true);
            boolean airStale=System.currentTimeMillis()-d.optLong("airTime",0)>3*3600000L;
            String air=d.has("aqi")&&!airStale?Integer.toString((int)Math.round(d.optDouble("aqi"))):"--";
            text("US AQI "+air,256,287,48,d.optDouble("aqi",0)>100?RED:GREY,false);
            text("3h降雨 "+(d.optInt("rain",-1)<0?"--":d.optInt("rain")+"%"),256,355,48,GREY,false);
            text("体感 "+degree(d.optDouble("feel")),256,421,48,GREY,false);
        }
        if(valid) {
            SimpleDateFormat f=new SimpleDateFormat("HH:mm",Locale.US);
            f.setTimeZone(TimeZone.getTimeZone(d.optString("timezone","Asia/Shanghai")));
            text(status.isEmpty()?f.format(new Date(d.optLong("observed"))):status,256,475,28,status.isEmpty()?GREY:RED,false);
        }
        paint.setStyle(Paint.Style.FILL);
        for(int i=0;i<2;i++) { paint.setColor(i==page?(feedback?RED:Color.WHITE):Color.rgb(60,60,60)); canvas.drawCircle(450,244+i*30,6,paint); }
    }
    // Original 17×13 dot art, derived from geometry rather than third-party icons.
    private void icon(int code,boolean day,int color) {
        boolean snow=(code>=71&&code<=77)||code==85||code==86;
        boolean rain=(code>=51&&code<=67)||(code>=80&&code<=82)||code>=95;
        boolean fog=code==45||code==48;
        boolean clear=code==0||code==1;
        paint.setColor(color); paint.setStyle(Paint.Style.FILL);
        for(int y=0;y<13;y++) for(int x=0;x<17;x++) {
            boolean on=false;
            if(clear) {
                double dx=x-8,dy=y-6,r=Math.sqrt(dx*dx+dy*dy);
                if(day) on=r<=3.2 || (r>=4.8&&r<=6.2&&(x==8||y==6||Math.abs(dx)==Math.abs(dy)));
                else on=r<=5 && Math.pow(x-10,2)+Math.pow(y-4,2)>22;
            } else if(fog) on=(y==3||y==6||y==9)&&x>=3&&x<=13;
            else {
                on=(Math.pow((x-6)/3.5,2)+Math.pow((y-4)/3.0,2)<=1)
                    ||(Math.pow((x-10)/3.5,2)+Math.pow((y-5)/2.5,2)<=1)
                    ||(y>=5&&y<=7&&x>=3&&x<=13);
                if(rain) on|=(x==5||x==9||x==13)&&(y==9||y==11);
                if(snow) on|=(y==10&&(x==5||x==9||x==13))||(y==12&&(x==7||x==11));
                if(code>=95) on|=(y==8&&x==9)||(y==9&&x==8)||(y==10&&x>=7&&x<=9)||(y==11&&x==8)||(y==12&&x==7);
            }
            if(on) canvas.drawCircle(256+(x-8)*10,266+(y-6)*10,3.4f,paint);
        }
    }
}
