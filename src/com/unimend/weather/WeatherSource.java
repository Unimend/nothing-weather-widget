package com.unimend.weather;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.json.JSONObject;
import org.json.JSONArray;

final class WeatherSource {
    static JSONObject get(String url) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(7000); c.setReadTimeout(7000);
        c.setRequestProperty("User-Agent", "NothingWeatherWidget/1.0 (personal Android widget)");
        long deadline=System.nanoTime()+14000000000L;
        try {
            if(c.getResponseCode()!=200) throw new IOException("HTTP "+c.getResponseCode());
            try(InputStream in=c.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buf=new byte[4096]; int n;
                while((n=in.read(buf))!=-1) {
                    if(out.size()+n>256000 || System.nanoTime()>deadline || Thread.currentThread().isInterrupted()) throw new IOException("响应过大、超时或已取消");
                    out.write(buf,0,n);
                }
                return new JSONObject(out.toString("UTF-8"));
            }
        } finally { c.disconnect(); }
    }
    static JSONArray search(String name) throws Exception {
        return get("https://geocoding-api.open-meteo.com/v1/search?count=8&language=zh&format=json&name="+URLEncoder.encode(name,"UTF-8")).optJSONArray("results");
    }
    static JSONObject fetch(double lat,double lon) throws Exception {
        String coord="latitude="+lat+"&longitude="+lon;
        JSONObject response=get("https://api.open-meteo.com/v1/forecast?"+coord+
            "&current=temperature_2m,apparent_temperature,weather_code,is_day,wind_speed_10m,wind_direction_10m"+
            "&daily=temperature_2m_max,temperature_2m_min&hourly=precipitation_probability&forecast_days=2&timezone=auto&timeformat=unixtime&wind_speed_unit=ms");
        JSONObject current=response.getJSONObject("current"), daily=response.getJSONObject("daily");
        double temp=current.getDouble("temperature_2m");
        if(!Double.isFinite(temp)||temp< -100||temp>70) throw new IOException("温度无效");
        JSONObject data=new JSONObject();
        data.put("temp",temp).put("feel",current.getDouble("apparent_temperature"))
            .put("code",current.getInt("weather_code")).put("day",current.getInt("is_day"))
            .put("wind",current.getDouble("wind_speed_10m")).put("direction",current.getDouble("wind_direction_10m"))
            .put("high",daily.getJSONArray("temperature_2m_max").getDouble(0))
            .put("low",daily.getJSONArray("temperature_2m_min").getDouble(0))
            .put("observed",current.getLong("time")*1000L).put("fetched",System.currentTimeMillis())
            .put("timezone",response.optString("timezone","Asia/Shanghai"));
        JSONObject hourly=response.getJSONObject("hourly");
        JSONArray times=hourly.getJSONArray("time"), rain=hourly.getJSONArray("precipitation_probability");
        long now=System.currentTimeMillis()/1000L; int max=-1;
        for(int i=0;i<times.length();i++) if(times.getLong(i)>=now-3600 && times.getLong(i)<=now+3*3600 && !rain.isNull(i)) max=Math.max(max,rain.getInt(i));
        data.put("rain",max);
        try {
            JSONObject air=get("https://air-quality-api.open-meteo.com/v1/air-quality?"+coord+"&current=us_aqi,pm2_5&timezone=auto&timeformat=unixtime").getJSONObject("current");
            double aqi=air.getDouble("us_aqi"), pm=air.getDouble("pm2_5");
            if(!Double.isFinite(aqi)||!Double.isFinite(pm)||aqi<0||pm<0) throw new IOException("空气数据无效");
            data.put("aqi",aqi).put("pm",pm).put("airTime",air.getLong("time")*1000L);
        } catch(Exception e) { data.put("airError",true); android.util.Log.w("WeatherSource","空气数据暂不可用",e); }
        return data;
    }
    static String condition(int c) {
        if(c==0) return "晴"; if(c==1||c==2) return "多云"; if(c==3) return "阴";
        if(c==45||c==48) return "雾"; if(c>=51&&c<=57) return "毛毛雨";
        if(c>=61&&c<=67) return "雨"; if(c>=71&&c<=77) return "雪";
        if(c>=80&&c<=82) return "阵雨"; if(c==85||c==86) return "阵雪";
        if(c>=95&&c<=99) return "雷雨"; return "天气未知";
    }
}
