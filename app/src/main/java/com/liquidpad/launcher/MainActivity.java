package com.liquidpad.launcher;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private final int WHITE = Color.WHITE;
    private FrameLayout root;
    private List<ResolveInfo> installed = new ArrayList<>();
    private ArrayList<String> dockPackages = new ArrayList<>();
    private SharedPreferences preferences;
    private int page = 0;
    private int columns = 8;
    private int rows = 4;
    private float downX, downY;
    private boolean searching = false;
    private LinearLayout content;
    private TextView pageDots;
    private final int BLUE = 0xFF2357AD;

    private int dp(float n) { return (int) (n * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private TextView label(String s, int size, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextColor(WHITE); t.setTextSize(size);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private LinearLayout vertical() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private void refreshApps() {
        Intent query = new Intent(Intent.ACTION_MAIN); query.addCategory(Intent.CATEGORY_LAUNCHER);
        installed = getPackageManager().queryIntentActivities(query, 0);
        Collections.sort(installed, (a,b) -> String.CASE_INSENSITIVE_ORDER.compare(a.loadLabel(getPackageManager()).toString(), b.loadLabel(getPackageManager()).toString()));
        List<ResolveInfo> unique = new ArrayList<>(); HashSet<String> seen = new HashSet<>();
        for (ResolveInfo r : installed) if (seen.add(r.activityInfo.packageName)) unique.add(r);
        installed = unique;
    }
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(0xFF2C5AA2); getWindow().setNavigationBarColor(0xFF2C5AA2);
        preferences = getSharedPreferences("liquidpad", MODE_PRIVATE);
        refreshApps(); loadDock(); showHome(true);
    }
    @Override public void onConfigurationChanged(Configuration c) { super.onConfigurationChanged(c); showHome(false); }
    @Override protected void onResume() { super.onResume(); if (!searching && root != null) { refreshApps(); showHome(false); } }
    private void loadDock() {
        String saved = preferences.getString("dock", "");
        dockPackages.clear();
        if (!saved.isEmpty()) { for (String s : saved.split(",")) if (!s.isEmpty()) dockPackages.add(s); }
        if (dockPackages.isEmpty()) {
            String[] favorites = {"com.android.chrome", "com.google.android.youtube", "com.google.android.gm", "com.google.android.apps.docs", "com.android.settings"};
            for (String f : favorites) if (find(f) != null) dockPackages.add(f);
            for (ResolveInfo r : installed) if (dockPackages.size() < 6 && !dockPackages.contains(r.activityInfo.packageName)) dockPackages.add(r.activityInfo.packageName);
        }
    }
    private ResolveInfo find(String pkg) { for (ResolveInfo r : installed) if (r.activityInfo.packageName.equals(pkg)) return r; return null; }
    private void saveDock() { StringBuilder s = new StringBuilder(); for (String p : dockPackages) { if (s.length()>0) s.append(','); s.append(p); } preferences.edit().putString("dock", s.toString()).apply(); }
    private void toggleDock(ResolveInfo r) {
        String pkg = r.activityInfo.packageName;
        if (dockPackages.contains(pkg)) { dockPackages.remove(pkg); Toast.makeText(this,"Removed from dock",Toast.LENGTH_SHORT).show(); }
        else if (dockPackages.size() < 8) { dockPackages.add(pkg); Toast.makeText(this,"Pinned to dock",Toast.LENGTH_SHORT).show(); }
        else Toast.makeText(this,"Dock is full (8 apps)",Toast.LENGTH_SHORT).show();
        saveDock(); showHome(false);
    }
    private void launch(ResolveInfo r) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN); i.addCategory(Intent.CATEGORY_LAUNCHER);
            i.setClassName(r.activityInfo.packageName, r.activityInfo.name); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i); overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        } catch (Exception e) { Toast.makeText(this, "Cannot open app", Toast.LENGTH_SHORT).show(); }
    }
    private View appIcon(ResolveInfo r, boolean inDock) {
        LinearLayout cell = vertical(); cell.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        cell.setPadding(dp(3),dp(5),dp(3),dp(3));
        ImageView icon = new ImageView(this); icon.setImageDrawable(r.loadIcon(getPackageManager()));
        int iconPx = dp(inDock ? 57 : 61);
        cell.addView(icon, new LinearLayout.LayoutParams(iconPx,iconPx));
        if (!inDock) {
            TextView name = label(r.loadLabel(getPackageManager()).toString(), 12, false);
            name.setGravity(Gravity.CENTER); name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setShadowLayer(3,0,1,0x88000000);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(104),dp(25)); lp.topMargin=dp(6);
            cell.addView(name,lp);
        }
        cell.setOnClickListener(v -> {
            cell.animate().scaleX(1.12f).scaleY(1.12f).alpha(.72f).setDuration(115).withEndAction(() -> {
                cell.animate().scaleX(1).scaleY(1).alpha(1).setDuration(140).start(); launch(r);
            }).start();
        });
        cell.setOnLongClickListener(v -> { toggleDock(r); return true; });
        return cell;
    }
    private View clockWidget() {
        LinearLayout widget = vertical(); widget.setPadding(dp(20),dp(13),dp(23),dp(13));
        widget.setBackground(round(0x55FFFFFF,28));
        TextView clock = label(new SimpleDateFormat("h:mm",Locale.getDefault()).format(new Date()),40,true);
        TextView date = label(new SimpleDateFormat("EEEE, d MMMM",Locale.getDefault()).format(new Date()),14,false);
        widget.addView(clock); widget.addView(date); return widget;
    }
    private View batteryWidget() {
        LinearLayout widget = vertical(); widget.setPadding(dp(18),dp(16),dp(20),dp(15)); widget.setBackground(round(0x55FFFFFF,26));
        BatteryManager bm = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
        int pct = bm == null ? -1 : bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        TextView title = label("Battery",14,false); TextView percent = label(pct < 0 ? "--" : pct+"%",27,true);
        widget.addView(title); widget.addView(percent); return widget;
    }
    private void showHome(boolean animate) {
        searching=false;
        columns = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 8 : 5;
        rows = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 4 : 6;
        int perPage=columns*rows;
        int pages=Math.max(1,(installed.size()+perPage-1)/perPage);
        page=Math.max(0,Math.min(page,pages-1));
        root=new FrameLayout(this);
        GradientDrawable wallpaper=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFF154995,0xFF3378CE,0xFF8EB9ED,0xFFB6A3E6});
        root.setBackground(wallpaper);
        content=vertical(); content.setPadding(dp(24),dp(18),dp(24),dp(15));
        root.addView(content,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(clockWidget());
        LinearLayout.LayoutParams batParams=new LinearLayout.LayoutParams(-2,-2); batParams.leftMargin=dp(14);
        header.addView(batteryWidget(),batParams);
        View spacer=new View(this); header.addView(spacer,new LinearLayout.LayoutParams(0,dp(1),1));
        TextView search=label("⌕  Search",16,false); search.setGravity(Gravity.CENTER);
        search.setPadding(dp(27),dp(16),dp(27),dp(16)); search.setBackground(round(0x66FFFFFF,26));
        search.setOnClickListener(v->showSearch()); header.addView(search);
        content.addView(header);
        FrameLayout center=new FrameLayout(this);
        LinearLayout gridHolder=vertical(); gridHolder.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        GridLayout grid=new GridLayout(this); grid.setColumnCount(columns); grid.setUseDefaultMargins(false);
        grid.setPadding(0,dp(26),0,0);
        int start=page*perPage, end=Math.min(start+perPage,installed.size());
        for(int j=start;j<end;j++) {
            View icon=appIcon(installed.get(j),false);
            GridLayout.LayoutParams p=new GridLayout.LayoutParams(); p.width=dp(117); p.height=dp(118);
            grid.addView(icon,p);
        }
        gridHolder.addView(grid);
        center.addView(gridHolder,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL));
        content.addView(center,new LinearLayout.LayoutParams(-1,0,1));
        pageDots=label("",15,true); pageDots.setGravity(Gravity.CENTER);
        StringBuilder dots=new StringBuilder(); for(int i=0;i<pages;i++) dots.append(i==page?"●":"○").append("  ");
        pageDots.setText(dots.toString().trim()); pageDots.setPadding(0,0,0,dp(12)); content.addView(pageDots);
        HorizontalScrollView scroll=new HorizontalScrollView(this); scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout dock=new LinearLayout(this); dock.setGravity(Gravity.CENTER); dock.setPadding(dp(16),dp(11),dp(16),dp(10));
        dock.setBackground(round(0x99FFFFFF,30));
        for(String pkg:new ArrayList<>(dockPackages)) { ResolveInfo r=find(pkg); if(r!=null) {
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(79),dp(70)); dock.addView(appIcon(r,true),p);
        }}
        scroll.addView(dock);
        LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(-2,-2); dlp.gravity=Gravity.CENTER_HORIZONTAL;
        content.addView(scroll,dlp);
        root.setOnTouchListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();return true;}
            if(e.getAction()==MotionEvent.ACTION_UP){float dx=e.getX()-downX,dy=e.getY()-downY;
                if(Math.abs(dx)>dp(65)&&Math.abs(dx)>Math.abs(dy)){page=Math.max(0,Math.min(pages-1,page+(dx<0?1:-1)));showHome(false);}
                else if(dy>dp(110)&&Math.abs(dy)>Math.abs(dx))showSearch();return true;
            }return true;
        });
        // Touch gestures also work on the empty area; the app icons retain their own click handlers.
        setContentView(root);
        if(animate){content.setAlpha(0f);content.setScaleX(.97f);content.setScaleY(.97f);
            content.animate().alpha(1).scaleX(1).scaleY(1).setDuration(260).start();}
    }
    private void showSearch(){
        searching=true;
        FrameLayout overlay=new FrameLayout(this);
        overlay.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFF2859A9,0xFF718ECF,0xFFAA9ED8}));
        LinearLayout panel=vertical();panel.setPadding(dp(28),dp(20),dp(28),dp(20));
        overlay.addView(panel,new FrameLayout.LayoutParams(-1,-1));
        TextView back=label("‹  Home",19,true);back.setPadding(0,0,0,dp(18));panel.addView(back);
        back.setOnClickListener(v->showHome(true));
        EditText input=new EditText(this);input.setSingleLine(true);input.setTextSize(18);input.setTextColor(WHITE);
        input.setHintTextColor(0xEEFFFFFF);input.setHint("Search apps");input.setPadding(dp(18),dp(13),dp(18),dp(13));
        input.setBackground(round(0x66FFFFFF,24));panel.addView(input);
        TextView title=label("App Library",21,true);title.setPadding(0,dp(22),0,dp(12));panel.addView(title);
        ScrollView sc=new ScrollView(this); LinearLayout results=vertical();sc.addView(results);
        panel.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        ArrayList<TextView> rows=new ArrayList<>();
        for(ResolveInfo r:installed){TextView row=label("  "+r.loadLabel(getPackageManager()).toString(),17,false);
            row.setGravity(Gravity.CENTER_VERTICAL);row.setCompoundDrawablePadding(dp(14));
            Drawable icon=r.loadIcon(getPackageManager());icon.setBounds(0,0,dp(36),dp(36));row.setCompoundDrawables(icon,null,null,null);
            row.setPadding(dp(14),dp(9),dp(12),dp(9));row.setBackground(round(0x44FFFFFF,16));
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(58));rp.bottomMargin=dp(6);results.addView(row,rp);
            row.setOnClickListener(v->launch(r));row.setOnLongClickListener(v->{toggleDock(r);return true;});rows.add(row);
        }
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}
            public void onTextChanged(CharSequence s,int st,int before,int count){String q=s.toString().toLowerCase(Locale.ROOT);
                for(TextView r:rows)r.setVisibility(r.getText().toString().toLowerCase(Locale.ROOT).contains(q)?View.VISIBLE:View.GONE);}
            public void afterTextChanged(Editable e){}
        });
        setContentView(overlay);panel.setAlpha(0);panel.setTranslationY(dp(20));panel.animate().alpha(1).translationY(0).setDuration(220).start();
    }
    @Override public void onBackPressed(){showHome(true);}
}
