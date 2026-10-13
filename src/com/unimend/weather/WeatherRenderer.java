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
        return render(c,store,page,pixels,feedback,-1);
    }
    static Bitmap render(Context c,WeatherStore store,int page,int pixels,boolean feedback,int frame) {
        Bitmap b=Bitmap.createBitmap(pixels,pixels,Bitmap.Config.ARGB_8888); b.setDensity(Bitmap.DENSITY_NONE);
        new WeatherRenderer(c,b).draw(store,page,feedback,frame); return b;
    }
    private void text(String value,float x,float y,float size,int color,boolean dot) {
        paint.setTypeface(dot?doto:Typeface.create("sans-serif-medium",Typeface.NORMAL));
        paint.setTextSize(size); paint.setColor(color); paint.setStyle(Paint.Style.FILL);
        Rect bounds=new Rect(); paint.getTextBounds(value,0,value.length(),bounds);
        canvas.drawText(value,x-(bounds.left+bounds.right)/2f,y-(bounds.top+bounds.bottom)/2f,paint);
    }
    private String shortCity(String name) { return name.length()>8?name.substring(0,7)+"…":name; }
    private static String degree(double n) { return String.format(Locale.US,"%.0f°",n); }
    private static int mix(int from,int to,float amount) {
        return Color.rgb(
            Math.round(Color.red(from)+(Color.red(to)-Color.red(from))*amount),
            Math.round(Color.green(from)+(Color.green(to)-Color.green(from))*amount),
            Math.round(Color.blue(from)+(Color.blue(to)-Color.blue(from))*amount));
    }
    private void draw(WeatherStore s,int page,boolean feedback,int frame) {
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
            float pulse=frame<0?0f:(float)(0.5-0.5*Math.cos(frame*Math.PI/3));
            double delta=d.optDouble("tempDelta",0);
            int trend=delta>0.15?Color.rgb(226,70,65):delta<-0.15?Color.rgb(72,155,210):Color.WHITE;
            int temperature=frame<0?Color.WHITE:mix(Color.rgb(205,205,205),trend,0.22f+0.34f*pulse);
            text(degree(d.optDouble("temp")),256,130,134,temperature,true);
            canvas.save(); canvas.translate(0,18); canvas.scale(1.18f,1.18f,256,262);
            icon(d.optInt("code"),d.optInt("day",1)==1,feedback?RED:Color.WHITE,frame);
            if(frame>=0) wind(d.optDouble("direction"),d.optDouble("wind"),frame);
            canvas.restore();
            text(shortCity(s.city())+" · "+WeatherSource.condition(d.optInt("code")),256,412,46,Color.WHITE,false);
        } else {
            text("↑ "+degree(d.optDouble("high")),256,105,82,Color.WHITE,true);
            text("↓ "+degree(d.optDouble("low")),256,195,82,Color.WHITE,true);
            boolean airStale=System.currentTimeMillis()-d.optLong("airTime",0)>3*3600000L;
            String air=d.has("aqi")&&!airStale?Integer.toString((int)Math.round(d.optDouble("aqi"))):"--";
            double aqi=d.optDouble("aqi",0);
            int airColor=aqi>100?RED:aqi>50?Color.rgb(232,184,58):Color.rgb(74,190,155);
            text("US AQI "+air,256,287,48,airStale?GREY:airColor,false);
            int rain=d.optInt("rain",-1);
            int rainColor=frame>=0&&rain>=40&&frame%2==1?Color.WHITE:GREY;
            text("3h降雨 "+(rain<0?"--":rain+"%"),256,355,48,rainColor,false);
            text("体感 "+degree(d.optDouble("feel")),256,421,48,GREY,false);
            if(frame>=0&&!airStale) airEdge(airColor,frame);
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
    private void airEdge(int color,int frame) {
        float pulse=(float)(0.45+0.35*(0.5-0.5*Math.cos(frame*Math.PI/3)));
        paint.setColor(Color.argb(Math.round(255*pulse),Color.red(color),Color.green(color),Color.blue(color)));
        paint.setStrokeWidth(7); paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(34,208,34,304,paint); paint.setStrokeCap(Paint.Cap.BUTT);
    }
    private void wind(double direction,double speed,int frame) {
        double angle=Math.toRadians(direction+180); float ux=(float)Math.sin(angle),uy=(float)-Math.cos(angle);
        paint.setColor(Color.rgb(105,105,105)); paint.setStyle(Paint.Style.FILL);
        float travel=18+(frame%6)*8; float spacing=Math.min(34,18+(float)speed*2);
        for(int i=0;i<3;i++) {
            float lane=(i-1)*spacing; float x=256+ux*(travel-42)+(-uy)*lane; float y=266+uy*(travel-42)+ux*lane;
            canvas.drawCircle(x,y,2.8f+i*.35f,paint);
        }
    }
    private void icon(int code,boolean day,int color) { icon(code,day,color,-1); }
    private void icon(int code,boolean day,int color,int frame) {
        boolean snow=(code>=71&&code<=77)||code==85||code==86;
        boolean rain=(code>=51&&code<=67)||(code>=80&&code<=82);
        boolean thunder=code>=95, fog=code==45||code==48, clear=code==0||code==1;
        int f=frame<0?0:frame;
        if(clear) drawSun(day,f);
        else if(fog) drawMist(f);
        else if(snow) drawSnow(f);
        else if(thunder) drawStorm(f);
        else if(rain) drawRain(f);
        else drawCloud(code==2,f);
    }
    private void dot(float x,float y,float radius,int color,int alpha) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(alpha,Color.red(color),Color.green(color),Color.blue(color)));
        canvas.drawCircle(x,y,radius,paint);
    }
    private void drawSun(boolean day,int frame) {
        int yellow=Color.rgb(255,224,32);
        if(!day) {
            int moon=Color.rgb(196,218,235);
            for(int y=-5;y<=5;y++) for(int x=-5;x<=5;x++)
                if(x*x+y*y<=25 && (x-2)*(x-2)+(y+1)*(y+1)>14) dot(256+x*8,266+y*8,3.4f,moon,235);
            return;
        }
        for(int y=-3;y<=3;y++) for(int x=-3;x<=3;x++) if(x*x+y*y<=10) dot(256+x*9,266+y*9,3.7f,yellow,255);
        double phase=Math.toRadians(frame*12);
        for(int arm=0;arm<8;arm++) {
            double a=phase+arm*Math.PI/4;
            for(int step=0;step<3;step++) {
                float r=47+step*10;
                dot(256+(float)Math.cos(a)*r,266+(float)Math.sin(a)*r,3.3f,yellow,250-step*18);
            }
        }
    }
    private void drawMist(int frame) {
        int mist=Color.rgb(190,205,210);
        paint.setColor(mist); paint.setStrokeWidth(8); paint.setStrokeCap(Paint.Cap.ROUND);
        for(int bar=0;bar<4;bar++) {
            float shift=((frame+bar)%6-2.5f)*2.8f;
            float x=196+bar*38+shift;
            canvas.drawLine(x,224,x+42,306,paint);
        }
        paint.setStrokeCap(Paint.Cap.BUTT);
    }
    private void drawCloud(boolean sunBehind,int frame) {
        if(sunBehind) {
            int yellow=Color.rgb(248,196,55);
            for(int i=0;i<8;i++) { double a=i*Math.PI/4+frame*.12; dot(220+(float)Math.cos(a)*30,238+(float)Math.sin(a)*30,3,yellow,175); }
        }
        drawCloudGradient();
    }
    private void drawCloudGradient() {
        int cloud=Color.rgb(220,225,228);
        for(int y=0;y<7;y++) for(int x=0;x<15;x++) {
            double dx=x-7,dy=y-3;
            boolean on=(dx*dx/36+dy*dy/8<=1)||(Math.pow(x-5,2)/12+Math.pow(y-2,2)/7<=1)||(Math.pow(x-9,2)/10+Math.pow(y-2.5,2)/6<=1);
            if(on) dot(256+(x-7)*8,214+y*7,3.0f,cloud,105+y*23);
        }
    }
    private void drawRain(int frame) {
        drawCloudGradient();
        int blue=Color.rgb(65,155,225);
        for(int lane=0;lane<4;lane++) for(int p=0;p<3;p++) {
            float travel=(frame*7+p*17)%42;
            dot(221+lane*24+travel*.16f,270+travel,3.0f,blue,225-p*25);
        }
    }
    private void drawSnow(int frame) {
        int sky=Color.WHITE; double phase=Math.toRadians(frame*12);
        dot(256,266,4,sky,250);
        for(int arm=0;arm<6;arm++) {
            double a=phase+arm*Math.PI/3;
            for(int step=1;step<=4;step++) {
                float r=step*14; float x=256+(float)Math.cos(a)*r,y=266+(float)Math.sin(a)*r;
                dot(x,y,3.2f,sky,245-step*22);
                if(step==3) {
                    dot(x+(float)Math.cos(a+.7)*11,y+(float)Math.sin(a+.7)*11,2.8f,sky,190);
                    dot(x+(float)Math.cos(a-.7)*11,y+(float)Math.sin(a-.7)*11,2.8f,sky,190);
                }
            }
        }
    }
    private void drawStorm(int frame) {
        int blue=Color.rgb(65,155,225),yellow=Color.rgb(255,224,32);
        int[] lanes={-72,-42,42,72};
        for(int lane:lanes) for(int p=0;p<5;p++) {
            float travel=(frame*8+p*18)%78;
            dot(256+lane-travel*.34f,224+travel,3.1f,blue,235-p*18);
        }
        int alpha=(frame%4==0||frame%4==1)?255:75;
        int[][] bolt={{1,-3},{0,-2},{1,-2},{0,-1},{-1,0},{0,0},{-1,1},{-2,2}};
        for(int[] p:bolt) dot(256+p[0]*11,266+p[1]*11,4.3f,yellow,alpha);
    }
}
