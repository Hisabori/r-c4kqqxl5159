package app.oncare.personal;
import android.app.Activity;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
/** Shared Galaxy presentation for every screen, including independently opened activities. */
public class GalaxyActivity extends Activity {
    private final java.util.Set<View> styled=java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    @Override protected void onCreate(android.os.Bundle state){setTheme(night()?android.R.style.Theme_Material_NoActionBar:android.R.style.Theme_Material_Light_NoActionBar);super.onCreate(state);}
    boolean deepMode(){return getSharedPreferences("app-preferences",MODE_PRIVATE).getBoolean("deep-mind",false);}
    String deepStyle(){return DeepMood.style(getSharedPreferences("app-preferences",MODE_PRIVATE).getString("deep-mind-style","미드나잇"));}
    int deepAccent(){return DeepMood.accent(deepStyle());}
    boolean night(){if(deepMode())return true;String theme=getSharedPreferences("app-preferences",MODE_PRIVATE).getString("theme","system");if(theme.equals("dark"))return true;if(theme.equals("light"))return false;return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;}
    int surface(){return deepMode()?(deepStyle().equals("로즈")?0xFF301B27:deepStyle().equals("라일락")?0xFF251E35:0xFF211B2B):night()?0xFF202124:Color.WHITE;}
    int background(){return deepMode()?(deepStyle().equals("로즈")?0xFF210B16:deepStyle().equals("라일락")?0xFF160E24:0xFF0C0811):night()?0xFF101114:0xFFF2F3F5;}
    int foreground(){return night()?0xFFF5F5F7:0xFF17181C;}
    int secondary(){return night()?0xFFAFB2BA:0xFF646872;}
    int galaxyDp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    GradientDrawable galaxyShape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(galaxyDp(radius));return d;}
    @Override public void onContentChanged(){super.onContentChanged();View content=findViewById(android.R.id.content);if(content!=null){applyGalaxy(content);content.getViewTreeObserver().addOnGlobalLayoutListener(()->applyGalaxy(content));}getWindow().setStatusBarColor(background());getWindow().setNavigationBarColor(background());getWindow().getDecorView().setSystemUiVisibility(night()?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);}
    void applyGalaxy(View view){if(styled.add(view)){
        Drawable bg=view.getBackground();if(bg instanceof ColorDrawable){int c=((ColorDrawable)bg).getColor();if(c==Color.WHITE)view.setBackgroundColor(surface());else if(Color.red(c)>235&&Color.green(c)>235&&Color.blue(c)>235)view.setBackgroundColor(background());}
        if(bg instanceof GradientDrawable){ColorStateList colors=((GradientDrawable)bg).getColor();if(colors!=null&&colors.getDefaultColor()==Color.WHITE)((GradientDrawable)bg).setColor(surface());}
        if(view instanceof Button){Button b=(Button)view;b.setAllCaps(false);b.setTextSize(16);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));if(!"galaxy-action".equals(b.getTag()))b.setTextColor(deepMode()?deepAccent():night()?0xFF9DBBFF:0xFF245BD6);b.setMinHeight(galaxyDp(52));b.setMinimumHeight(galaxyDp(52));b.setPadding(galaxyDp(20),galaxyDp(14),galaxyDp(20),galaxyDp(14));b.setStateListAnimator(null);if(!"galaxy-action".equals(b.getTag()))b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x223B73FF),galaxyShape(night()?0xFF28344B:0xFFE8EEFC,26),null));if(!"galaxy-action".equals(b.getTag())&&b.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams){android.view.ViewGroup.MarginLayoutParams p=(android.view.ViewGroup.MarginLayoutParams)b.getLayoutParams();p.bottomMargin=Math.max(p.bottomMargin,galaxyDp(10));}}
        else if(view instanceof EditText){EditText e=(EditText)view;e.setTextColor(foreground());e.setHintTextColor(secondary());e.setPadding(galaxyDp(18),galaxyDp(14),galaxyDp(18),galaxyDp(14));e.setMinHeight(galaxyDp(52));e.setBackground(galaxyShape(surface(),26));}
        else if(view instanceof Spinner){view.setPadding(galaxyDp(16),galaxyDp(10),galaxyDp(16),galaxyDp(10));view.setMinimumHeight(galaxyDp(52));view.setBackground(galaxyShape(surface(),22));}
        else if(view instanceof TextView){TextView t=(TextView)view;int c=t.getCurrentTextColor();if(c==Color.BLACK||Color.red(c)<80&&Color.green(c)<80&&Color.blue(c)<100)t.setTextColor(foreground());if(t.getTextSize()/getResources().getDisplayMetrics().scaledDensity>=28){t.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.NORMAL));t.setLineSpacing(galaxyDp(3),1);}if(t instanceof Switch){t.setMinHeight(galaxyDp(56));t.setPadding(galaxyDp(18),galaxyDp(10),galaxyDp(18),galaxyDp(10));t.setBackground(galaxyShape(surface(),24));}}
    }if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)applyGalaxy(group.getChildAt(i));}}
}
