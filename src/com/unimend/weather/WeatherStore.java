package com.unimend.weather;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

final class WeatherStore {
    final SharedPreferences prefs;
    WeatherStore(Context c) { prefs = c.getSharedPreferences("weather", Context.MODE_PRIVATE); }
    boolean configured() { return prefs.contains("lat"); }
    String city() { return prefs.getString("city", "选择城市"); }
    double lat() { return Double.parseDouble(prefs.getString("lat", "30.2741")); }
    double lon() { return Double.parseDouble(prefs.getString("lon", "120.1551")); }
    void city(String name, double lat, double lon) {
        if (name.trim().isEmpty() || !Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat)>90 || Math.abs(lon)>180) throw new IllegalArgumentException("城市坐标无效");
        prefs.edit().putString("city", name).putString("lat", Double.toString(lat)).putString("lon", Double.toString(lon))
            .remove("data").remove("error").apply();
    }
    JSONObject data() {
        try { return new JSONObject(prefs.getString("data", "{}")); } catch (Exception e) { return new JSONObject(); }
    }
    void save(JSONObject data) {
        JSONObject old=data();
        try {
            if(old.has("temp") && old.optLong("observed")!=data.optLong("observed"))
                data.put("tempDelta",data.optDouble("temp")-old.optDouble("temp"));
            else if(old.has("tempDelta")) data.put("tempDelta",old.optDouble("tempDelta"));
        } catch(Exception ignored) { }
        prefs.edit().putString("data", data.toString()).remove("error").apply();
    }
    void error(String error) { prefs.edit().putString("error", error).apply(); }
    int page(int id) { return prefs.getInt("page_"+id, 0); }
    void toggle(int id) { prefs.edit().putInt("page_"+id, 1-page(id)).apply(); }
    boolean stale(JSONObject d) {
        long now=System.currentTimeMillis();
        long fetched=d.optLong("fetched", 0), observed=d.optLong("observed", 0);
        return prefs.contains("error") || now-fetched>90*60*1000L || now-observed>90*60*1000L || fetched>now+60000;
    }
}
