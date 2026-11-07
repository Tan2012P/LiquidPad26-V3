package com.liquidpad.launcher;

import android.app.Activity;
import android.os.Bundle;
import android.os.BatteryManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private final int WHITE = Color.WHITE;
    private FrameLayout root;
    private FrameLayout pageContainer;
    private LinearLayout homeContent;
    private LinearLayout gridArea;
    private TextView indicators;
    private List<ResolveInfo> apps = new ArrayList<>();
    private ArrayList<String> dockPackages = new ArrayList<>();
    private SharedPreferences prefs;
    private int page = 0, columns = 8, perPage = 32, pages = 1;
    private boolean searching = false, pageMoving = false;
    private boolean panelOpen = false;
    private FrameLayout panelOverlay;
    private TextView liveClock;
    private float startX, startY;
    private boolean gestureTracking = false;

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radius)); return g;
    }
    private GradientDrawable wallpaper() {
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xFF183B83,0xFF3275C3,0xFF76AFE3,0xFFA6B5E7,0xFFD6B1D7});
    }
    private TextView text(String s, float size, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(WHITE);
        if (bold) t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        t.setIncludeFontPadding(false); return t;
    }
    private LinearLayout vertical() { LinearLayout l = new LinearLayout(this); l.setOrientation(1); return l; }
    private void refreshApps() {
        Intent q = new Intent(Intent.ACTION_MAIN); q.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> found = getPackageManager().queryIntentActivities(q, 0);
        Collections.sort(found, (a,b) -> String.CASE_INSENSITIVE_ORDER.compare(
            a.loadLabel(getPackageManager()).toString(),b.loadLabel(getPackageManager()).toString()));
        apps.clear(); HashSet<String> seen = new HashSet<>();
        for (ResolveInfo r:found) if (seen.add(r.activityInfo.packageName)) apps.add(r);
    }
    private ResolveInfo find(String pkg) {
        for (ResolveInfo r:apps) if (r.activityInfo.packageName.equals(pkg)) return r;
        return null;
    }
    private void loadDock() {
        dockPackages.clear();
        String saved=prefs.getString("dock","");
        if (!saved.isEmpty()) for(String p:saved.split(",")) if(find(p)!=null) dockPackages.add(p);
        if(dockPackages.isEmpty()) {
            String[] fav={"com.android.chrome","com.google.android.youtube","com.google.android.gm",
                "com.google.android.apps.photos","com.google.android.apps.docs","com.android.settings"};
            for(String p:fav) if(find(p)!=null && !dockPackages.contains(p)) dockPackages.add(p);
            for(ResolveInfo r:apps) if(dockPackages.size()<6 && !dockPackages.contains(r.activityInfo.packageName))
                dockPackages.add(r.activityInfo.packageName);
        }
    }
    private void toggleDock(ResolveInfo r) {
        String pkg=r.activityInfo.packageName;
        if(dockPackages.contains(pkg)) dockPackages.remove(pkg);
        else if(dockPackages.size()<8) dockPackages.add(pkg);
        else {Toast.makeText(this,"Dock can hold 8 apps",Toast.LENGTH_SHORT).show();return;}
        StringBuilder b=new StringBuilder();
        for(String p:dockPackages) {if(b.length()>0)b.append(",");b.append(p);}
        prefs.edit().putString("dock",b.toString()).apply();
        Toast.makeText(this,dockPackages.contains(pkg)?"Added to Dock":"Removed from Dock",Toast.LENGTH_SHORT).show();
        showHome(false);
    }
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(0xFF1B3E83);
        getWindow().setNavigationBarColor(0xFF9EAFDD);
        prefs=getSharedPreferences("liquidpad",MODE_PRIVATE);
        refreshApps();loadDock();showHome(true);
    }
    @Override public void onConfigurationChanged(Configuration c) {super.onConfigurationChanged(c);showHome(false);}
    @Override protected void onResume() {
        super.onResume();
        if(root!=null && !searching) {refreshApps();showHome(false);}
    }
    private void launch(ResolveInfo r, View icon) {
        icon.animate().cancel();
        icon.animate().scaleX(1.24f).scaleY(1.24f).alpha(.35f)
            .setDuration(190).setInterpolator(new DecelerateInterpolator())
            .withEndAction(() -> {
                try {
                    Intent i=getPackageManager().getLaunchIntentForPackage(r.activityInfo.packageName);
                    if(i==null) {i=new Intent(Intent.ACTION_MAIN);i.addCategory(Intent.CATEGORY_LAUNCHER);
                        i.setClassName(r.activityInfo.packageName,r.activityInfo.name);}
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                    overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out);
                } catch(Exception e) {Toast.makeText(this,"Unable to open app",Toast.LENGTH_SHORT).show();}
                icon.postDelayed(() -> {icon.setScaleX(1);icon.setScaleY(1);icon.setAlpha(1);},220);
            }).start();
    }
    private View appIcon(ResolveInfo r, boolean dock, int cellWidth) {
        LinearLayout cell=vertical();cell.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        cell.setPadding(dp(2),dp(4),dp(2),0);
        int iconSize=dp(dock?54:56);
        ImageView icon=new ImageView(this);icon.setImageDrawable(r.loadIcon(getPackageManager()));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        cell.addView(icon,new LinearLayout.LayoutParams(iconSize,iconSize));
        if(!dock) {
            TextView name=text(r.loadLabel(getPackageManager()).toString(),11.5f,false);
            name.setGravity(Gravity.CENTER);name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setShadowLayer(dp(3),0,dp(1),0x88000000);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(Math.max(dp(54),cellWidth-dp(4)),dp(23));
            lp.topMargin=dp(6);cell.addView(name,lp);
        }
        cell.setOnClickListener(v->launch(r,cell));
        cell.setOnLongClickListener(v->{toggleDock(r);return true;});
        cell.setOnTouchListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_DOWN) {
                cell.animate().cancel();cell.animate().scaleX(.89f).scaleY(.89f).setDuration(100).start();
            } else if(e.getAction()==MotionEvent.ACTION_UP || e.getAction()==MotionEvent.ACTION_CANCEL) {
                cell.animate().scaleX(1).scaleY(1).setDuration(190).setInterpolator(new DecelerateInterpolator()).start();
            }
            return false;
        });
        return cell;
    }
    private View glassWidget(String heading,String main,String sub,int width) {
        LinearLayout w=vertical();w.setGravity(Gravity.CENTER_VERTICAL);
        w.setPadding(dp(18),dp(13),dp(18),dp(12));
        w.setBackground(rounded(0x48FFFFFF,25));
        w.setElevation(dp(3));
        TextView h=text(heading,12,false);h.setAlpha(.86f);w.addView(h);
        TextView m=text(main,32,true);m.setPadding(0,dp(3),0,dp(3));w.addView(m);
        TextView s=text(sub,12,false);s.setAlpha(.9f);w.addView(s);
        w.setLayoutParams(new LinearLayout.LayoutParams(dp(width),dp(111)));
        return w;
    }
    private View clockWidget() {
        Date now=new Date();
        return glassWidget("TODAY",new SimpleDateFormat("h:mm",Locale.getDefault()).format(now),
            new SimpleDateFormat("EEEE, d MMMM",Locale.getDefault()).format(now),210);
    }
    private View batteryWidget() {
        BatteryManager b=(BatteryManager)getSystemService(Context.BATTERY_SERVICE);
        int pct=b==null?-1:b.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        return glassWidget("BATTERY",pct<0?"--":pct+"%","Lenovo tablet",155);
    }
    private void updateDots() {
        if(indicators==null)return;
        StringBuilder b=new StringBuilder();
        for(int i=0;i<Math.min(pages,12);i++) b.append(i==page?"●":"•").append("  ");
        indicators.setText(b.toString().trim());
    }
    private void populateGrid(LinearLayout target,int pageIndex) {
        target.removeAllViews();
        int width=getResources().getDisplayMetrics().widthPixels;
        int available=Math.max(dp(250),width-dp(48));
        int cell=available/columns;
        int start=pageIndex*perPage;
        int end=Math.min(start+perPage,apps.size());
        int rows=(int)Math.ceil((end-start)/(double)columns);
        for(int row=0;row<rows;row++) {
            LinearLayout line=new LinearLayout(this);
            line.setGravity(Gravity.CENTER_HORIZONTAL);
            for(int col=0;col<columns;col++) {
                int idx=start+row*columns+col;
                if(idx>=end)break;
                View v=appIcon(apps.get(idx),false,cell);
                line.addView(v,new LinearLayout.LayoutParams(cell,dp(102)));
            }
            target.addView(line);
        }
    }
    private void switchPage(int next) {
        if(pageMoving || next<0 || next>=pages || next==page)return;
        pageMoving=true;
        int direction=next>page?1:-1;
        LinearLayout outgoing=gridArea;
        LinearLayout incoming=vertical();incoming.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        populateGrid(incoming,next);
        int distance=getResources().getDisplayMetrics().widthPixels;
        incoming.setTranslationX(direction*distance);
        pageContainer.addView(incoming,new FrameLayout.LayoutParams(-1,-1));
        incoming.animate().translationX(0).alpha(1).setDuration(340)
            .setInterpolator(new DecelerateInterpolator()).start();
        outgoing.animate().translationX(-direction*distance*.7f).alpha(.15f)
            .setDuration(300).setInterpolator(new DecelerateInterpolator())
            .withEndAction(()->{
                pageContainer.removeView(outgoing);
                gridArea=incoming;pageMoving=false;
            }).start();
        page=next;updateDots();
    }
    private void showHome(boolean animate) {
        searching=false;panelOpen=false;pageMoving=false;
        boolean landscape=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
        int screenDp=(int)(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density);
        columns=landscape?(screenDp>=950?9:8):5;
        perPage=columns*(landscape?4:5);
        pages=Math.max(1,(apps.size()+perPage-1)/perPage);
        page=Math.max(0,Math.min(page,pages-1));
        root=new FrameLayout(this);root.setBackground(wallpaper());
        homeContent=vertical();homeContent.setPadding(dp(24),dp(17),dp(24),dp(18));
        root.addView(homeContent,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout status=new LinearLayout(this);
        status.setGravity(Gravity.CENTER_VERTICAL);
        TextView statusTime=text(new SimpleDateFormat("h:mm",Locale.getDefault()).format(new Date()),14,true);
        liveClock=statusTime;
        status.addView(statusTime);
        status.addView(new View(this),new LinearLayout.LayoutParams(0,dp(1),1));
        TextView controls=text("●●●  ▰  ▣",15,true);
        controls.setPadding(dp(15),dp(5),dp(5),dp(7));
        controls.setOnClickListener(v->showControlCenter());
        status.addView(controls);
        homeContent.addView(status);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(0,dp(10),0,0);
        top.addView(clockWidget());
        if(landscape || screenDp>650) {
            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(155),dp(111));bp.leftMargin=dp(14);
            top.addView(batteryWidget(),bp);
        }
        top.addView(new View(this),new LinearLayout.LayoutParams(0,dp(1),1));
        TextView search=text("⌕  Search",15,false);search.setGravity(Gravity.CENTER);
        search.setPadding(dp(19),dp(12),dp(19),dp(12));
        search.setBackground(rounded(0x55FFFFFF,24));
        search.setOnClickListener(v->showSearch());
        top.addView(search);
        homeContent.addView(top);
        pageContainer=new FrameLayout(this);
        pageContainer.setClipChildren(false);
        gridArea=vertical();gridArea.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        gridArea.setPadding(0,dp(25),0,0);
        populateGrid(gridArea,page);
        pageContainer.addView(gridArea,new FrameLayout.LayoutParams(-1,-1));
        homeContent.addView(pageContainer,new LinearLayout.LayoutParams(-1,0,1));
        indicators=text("",16,true);indicators.setGravity(Gravity.CENTER);
        indicators.setShadowLayer(dp(2),0,1,0x55000000);
        indicators.setPadding(0,0,0,dp(14));updateDots();
        indicators.setOnClickListener(v->switchPage((page+1)%pages));
        homeContent.addView(indicators);
        HorizontalScrollView dockScroll=new HorizontalScrollView(this);
        dockScroll.setHorizontalScrollBarEnabled(false);
        dockScroll.setFillViewport(false);
        LinearLayout dock=new LinearLayout(this);dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setPadding(dp(13),dp(11),dp(13),dp(10));
        dock.setBackground(rounded(0xB8E5ECFA,29));dock.setElevation(dp(12));
        for(String pkg:new ArrayList<>(dockPackages)) {
            ResolveInfo r=find(pkg);
            if(r!=null) dock.addView(appIcon(r,true,dp(68)),new LinearLayout.LayoutParams(dp(68),dp(63)));
        }
        dockScroll.addView(dock);
        LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(-2,dp(86));
        dl.gravity=Gravity.CENTER_HORIZONTAL;
        homeContent.addView(dockScroll,dl);
        TextView library=text("⌕  App Library",12,false);
        library.setGravity(Gravity.CENTER);library.setPadding(0,dp(11),0,dp(1));
        library.setOnClickListener(v->showAppLibrary());
        homeContent.addView(library);
        setContentView(root);
        if(animate) {
            homeContent.setAlpha(0);homeContent.setScaleX(.965f);homeContent.setScaleY(.965f);
            homeContent.animate().alpha(1).scaleX(1).scaleY(1).setDuration(380)
                .setInterpolator(new DecelerateInterpolator()).start();
        }
    }
    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        if(!searching && pageContainer!=null) {
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN) {
                startX=e.getX();startY=e.getY();gestureTracking=true;
            } else if(e.getActionMasked()==MotionEvent.ACTION_UP && gestureTracking) {
                float dx=e.getX()-startX,dy=e.getY()-startY;
                gestureTracking=false;
                if(Math.abs(dx)>dp(75) && Math.abs(dx)>Math.abs(dy)*1.3f) {
                    switchPage(page+(dx<0?1:-1));return true;
                }
                if(dy>dp(125) && Math.abs(dy)>Math.abs(dx)*1.5f) {
                    if(startX>getResources().getDisplayMetrics().widthPixels*.72f) showControlCenter();
                    else showSearch();
                    return true;
                }
                if(dy < -dp(125) && Math.abs(dy)>Math.abs(dx)*1.5f) {
                    showAppLibrary();return true;
                }
            } else if(e.getActionMasked()==MotionEvent.ACTION_CANCEL) gestureTracking=false;
        }
        return super.dispatchTouchEvent(e);
    }
    private void showSearch() {
        searching=true;
        FrameLayout overlay=new FrameLayout(this);overlay.setBackground(wallpaper());
        LinearLayout panel=vertical();panel.setPadding(dp(30),dp(32),dp(30),dp(18));
        overlay.addView(panel,new FrameLayout.LayoutParams(-1,-1));
        TextView back=text("‹  Home",18,true);back.setPadding(0,0,0,dp(22));
        panel.addView(back);back.setOnClickListener(v->showHome(true));
        EditText input=new EditText(this);input.setSingleLine(true);input.setTextSize(18);
        input.setTextColor(WHITE);input.setHintTextColor(0xDDFFFFFF);input.setHint("Search Apps");
        input.setPadding(dp(18),dp(13),dp(18),dp(13));
        input.setBackground(rounded(0x77FFFFFF,22));panel.addView(input);
        TextView title=text("App Library",25,true);
        title.setPadding(0,dp(24),0,dp(16));panel.addView(title);
        ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);
        LinearLayout results=vertical();scroll.addView(results);
        panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        ArrayList<TextView> entries=new ArrayList<>();
        for(ResolveInfo r:apps) {
            TextView row=text("  "+r.loadLabel(getPackageManager()).toString(),17,false);
            row.setGravity(Gravity.CENTER_VERTICAL);
            Drawable d=r.loadIcon(getPackageManager());d.setBounds(0,0,dp(37),dp(37));
            row.setCompoundDrawables(d,null,null,null);row.setCompoundDrawablePadding(dp(10));
            row.setPadding(dp(14),dp(8),dp(14),dp(8));
            row.setBackground(rounded(0x3BFFFFFF,18));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(58));lp.bottomMargin=dp(7);
            results.addView(row,lp);entries.add(row);
            row.setOnClickListener(v->launch(r,row));
            row.setOnLongClickListener(v->{toggleDock(r);return true;});
        }
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int a,int c,int f){}
            public void onTextChanged(CharSequence s,int a,int b,int c) {
                String q=s.toString().toLowerCase(Locale.ROOT);
                for(TextView t:entries) t.setVisibility(t.getText().toString().toLowerCase(Locale.ROOT).contains(q)?View.VISIBLE:View.GONE);
            }
            public void afterTextChanged(Editable e){}
        });
        setContentView(overlay);
        panel.setAlpha(0);panel.setTranslationY(dp(35));
        panel.animate().alpha(1).translationY(0).setDuration(330)
            .setInterpolator(new DecelerateInterpolator()).start();
    }
    private void showControlCenter() {
        if(root==null || panelOpen)return;
        panelOpen=true;
        panelOverlay=new FrameLayout(this);
        panelOverlay.setBackgroundColor(0x850C1730);
        root.addView(panelOverlay,new FrameLayout.LayoutParams(-1,-1));
        panelOverlay.setOnClickListener(v->hideControlCenter());
        LinearLayout card=vertical();
        card.setPadding(dp(20),dp(22),dp(20),dp(24));
        card.setBackground(rounded(0xEE5477A9,30));
        card.setElevation(dp(20));
        int w=getResources().getDisplayMetrics().widthPixels;
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(Math.min(dp(350),w-dp(32)),-2,Gravity.TOP|Gravity.RIGHT);
        p.setMargins(0,dp(45),dp(18),0);
        panelOverlay.addView(card,p);
        card.setOnClickListener(v->{});
        TextView title=text("Control Center",23,true);title.setPadding(0,0,0,dp(20));card.addView(title);
        LinearLayout buttons=new LinearLayout(this);buttons.setGravity(Gravity.CENTER);
        String[] names={"Wi-Fi","Bluetooth","Focus"};
        for(String name:names) {
            TextView tile=text(name,13,true);
            tile.setGravity(Gravity.CENTER);
            tile.setBackground(rounded(0x775FBBFF,22));
            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,dp(72),1);
            bp.setMargins(dp(3),0,dp(3),0);
            buttons.addView(tile,bp);
            tile.setOnClickListener(v->{
                Intent settings=new Intent(android.provider.Settings.ACTION_SETTINGS);
                startActivity(settings);
            });
        }
        card.addView(buttons);
        TextView brightness=text("☀   Brightness",15,true);
        brightness.setPadding(0,dp(20),0,dp(9));card.addView(brightness);
        SeekBar bright=new SeekBar(this);bright.setMax(255);
        try {bright.setProgress(android.provider.Settings.System.getInt(
            getContentResolver(),android.provider.Settings.System.SCREEN_BRIGHTNESS));}
        catch(Exception e){bright.setProgress(160);}
        card.addView(bright);
        bright.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int value,boolean user) {
                if(user) {android.view.WindowManager.LayoutParams a=getWindow().getAttributes();
                    a.screenBrightness=Math.max(.02f,value/255f);getWindow().setAttributes(a);}
            }
            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){}
        });
        TextView note=text("Quick controls · Android settings",12,false);
        note.setPadding(0,dp(12),0,0);card.addView(note);
        card.setTranslationY(-dp(320));card.setAlpha(0);
        card.animate().translationY(0).alpha(1).setDuration(330)
            .setInterpolator(new DecelerateInterpolator()).start();
    }
    private void hideControlCenter() {
        if(panelOverlay==null)return;
        FrameLayout old=panelOverlay;
        panelOverlay=null;panelOpen=false;
        old.animate().alpha(0).setDuration(180)
            .withEndAction(()->{if(root!=null)root.removeView(old);}).start();
    }
    private void showAppLibrary() {
        searching=true;
        FrameLayout overlay=new FrameLayout(this);overlay.setBackground(wallpaper());
        LinearLayout panel=vertical();panel.setPadding(dp(24),dp(22),dp(24),dp(14));
        overlay.addView(panel,new FrameLayout.LayoutParams(-1,-1));
        TextView back=text("‹  Home",18,true);
        back.setOnClickListener(v->showHome(true));panel.addView(back);
        TextView title=text("App Library",30,true);
        title.setPadding(0,dp(24),0,dp(14));panel.addView(title);
        TextView hint=text("All your apps",13,false);
        hint.setPadding(0,0,0,dp(20));panel.addView(hint);
        ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list=vertical();scroll.addView(list);
        panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        int width=getResources().getDisplayMetrics().widthPixels;
        int count=Math.max(3,Math.min(7,width/dp(110)));
        int cell=(width-dp(48))/count;
        for(int i=0;i<apps.size();i+=count) {
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_HORIZONTAL);
            for(int j=i;j<Math.min(i+count,apps.size());j++)
                row.addView(appIcon(apps.get(j),false,cell),
                    new LinearLayout.LayoutParams(cell,dp(105)));
            list.addView(row);
        }
        setContentView(overlay);
        panel.setAlpha(0);panel.setTranslationY(dp(35));
        panel.animate().alpha(1).translationY(0).setDuration(320).start();
    }
    @Override public void onBackPressed() {
        if(panelOpen)hideControlCenter();else showHome(true);
    }
}
