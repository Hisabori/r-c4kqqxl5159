package app.oncare.personal;

import android.app.PendingIntent;
import android.content.*;
import java.util.Arrays;

/** All launcher and widget inputs resolve to this small read-only navigation whitelist. */
final class AppRoutes {
    static final String KEY="oncare-route";
    static final String[] ROUTES={"home","exercise","sleep","mood","connect"};
    static String allowed(String route){return Arrays.asList(ROUTES).contains(route)?route:"home";}
    static String read(Intent intent){try{return intent==null||!intent.hasExtra(KEY)?null:allowed(intent.getStringExtra(KEY));}catch(RuntimeException e){return "home";}}
    static Intent intent(Context context,String route){return new Intent(context,EnhancedActivity.class).setAction(Intent.ACTION_VIEW).putExtra(KEY,allowed(route)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);}
    static PendingIntent pending(Context context,String route){String safe=allowed(route);return PendingIntent.getActivity(context,460+Arrays.asList(ROUTES).indexOf(safe),intent(context,safe),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
}
