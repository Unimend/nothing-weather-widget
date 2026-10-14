package com.unimend.weather;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

final class WeatherStore {
    final SharedPreferences prefs;
    final int widgetId;
    WeatherStore(Context c) { this(c,0); }
    WeatherStore(Context c,int widgetId) {
        prefs=c.getSharedPreferences("weather",Context.MODE_PRIVATE); this.widgetId=Math.max(0,widgetId);
        migrateLegacy();
    }
    private String key(String name) { return widgetId==0?name:name+"_"+widgetId; }
    private void migrateLegacy() {
        if(widgetId==0 || prefs.contains(key("lat")) || !prefs.contains("lat")) return;
        SharedPreferences.Editor e=prefs.edit();
        for(String name:new String[]{"city","lat","lon","data","error"}) if(prefs.contains(name)) e.putString(key(name),prefs.getString(name,""));
        if(prefs.contains("loading")) e.putBoolean(key("loading"),prefs.getBoolean("loading",false));
        e.apply();
    }
    boolean configured() { return prefs.contains(key("lat")); }
    String city() { return prefs.getString(key("city"), "选择城市"); }
    double lat() { return Double.parseDouble(prefs.getString(key("lat"), "30.2741")); }
    double lon() { return Double.parseDouble(prefs.getString(key("lon"), "120.1551")); }
    boolean loading() { return prefs.getBoolean(key("loading"),false); }
    void loading(boolean value) { prefs.edit().putBoolean(key("loading"),value).apply(); }
    boolean hasError() { return prefs.contains(key("error")); }
    void city(String name, double lat, double lon) {
        if (name.trim().isEmpty() || !Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat)>90 || Math.abs(lon)>180) throw new IllegalArgumentException("城市坐标无效");
        prefs.edit().putString(key("city"), name).putString(key("lat"), Double.toString(lat)).putString(key("lon"), Double.toString(lon))
            .remove(key("data")).remove(key("error")).apply();
    }
    JSONObject data() {
        try { return new JSONObject(prefs.getString(key("data"), "{}")); } catch (Exception e) { return new JSONObject(); }
    }
    void save(JSONObject data) {
        JSONObject old=data();
        try {
            if(old.has("temp") && old.optLong("observed")!=data.optLong("observed"))
                data.put("tempDelta",data.optDouble("temp")-old.optDouble("temp"));
            else if(old.has("tempDelta")) data.put("tempDelta",old.optDouble("tempDelta"));
        } catch(Exception ignored) { }
        prefs.edit().putString(key("data"), data.toString()).remove(key("error")).apply();
    }
    void error(String error) { prefs.edit().putString(key("error"), error).apply(); }
    int page(int id) { return prefs.getInt("page_"+id, 0); }
    void toggle(int id) { prefs.edit().putInt("page_"+id, 1-page(id)).apply(); }
    boolean stale(JSONObject d) {
        long now=System.currentTimeMillis();
        long fetched=d.optLong("fetched", 0), observed=d.optLong("observed", 0);
        return hasError() || now-fetched>90*60*1000L || now-observed>90*60*1000L || fetched>now+60000;
    }
    void delete() {
        if(widgetId==0) return;
        SharedPreferences.Editor e=prefs.edit();
        for(String name:new String[]{"city","lat","lon","data","error","loading","page"}) e.remove(key(name));
        e.apply();
    }
}
