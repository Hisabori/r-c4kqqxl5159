package app.oncare.personal;
import org.json.*;
final class SleepAudioCoverage {
    final double elapsed,recorded;final boolean monotonic;
    SleepAudioCoverage(double elapsed,double recorded,boolean monotonic){this.elapsed=elapsed;this.recorded=recorded;this.monotonic=monotonic;}
    double gap(){return Math.max(0,elapsed-recorded);}
    double fraction(){return Math.min(1,recorded/elapsed);}
    static SleepAudioCoverage analyze(JSONObject s){
        if(s==null||!s.has("ended")||s.optString("reason").contains("복구"))return null;
        double start=s.optDouble("started",Double.NaN),end=s.optDouble("ended",Double.NaN),elapsed=s.has("elapsedSeconds")?s.optDouble("elapsedSeconds",Double.NaN):(end-start)/1000;
        if(!Double.isFinite(start)||!Double.isFinite(end)||!Double.isFinite(elapsed)||start<=0||elapsed<1||elapsed>9*3600)return null;
        JSONArray bins=s.optJSONArray("bins");if(bins==null||bins.length()>480)return null;double sum=0;
        for(int i=0;i<bins.length();i++){JSONObject b=bins.optJSONObject(i);double n=b==null?Double.NaN:b.optDouble("seconds",Double.NaN);if(!Double.isFinite(n)||n<=0||n>65)return null;sum+=n;}
        return sum>elapsed+2?null:new SleepAudioCoverage(elapsed,sum,s.has("elapsedSeconds"));
    }
}
