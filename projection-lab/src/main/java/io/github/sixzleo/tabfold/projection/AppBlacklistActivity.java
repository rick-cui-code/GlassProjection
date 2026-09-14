package io.github.sixzleo.tabfold.projection;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.*;
import java.text.Collator;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Load labels once off the UI thread; rows are recycled and selection is saved immediately. */
public final class AppBlacklistActivity extends Activity {
    private static final int BG=0xff10191c,TEXT=0xffedf4f3,MUTED=0xffa6b9bb,ACCENT=0xffa4e6d6;
    private final ExecutorService loader=Executors.newSingleThreadExecutor();
    private final List<App> apps=new ArrayList<>(),visible=new ArrayList<>();
    private final AppsAdapter adapter=new AppsAdapter();
    private final LruCache<String,Bitmap> icons=new LruCache<>(32);
    private final Set<String> pendingIcons=new HashSet<>();
    private Drawable defaultIcon;
    private TextView count,empty;
    private EditText search;
    private boolean loaded;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private static final class App {
        final String label,pkg,search;
        App(String label,String pkg){this.label=label;this.pkg=pkg;search=(label+" "+pkg).toLowerCase(Locale.ROOT);}
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);AnimationSettings.init(this);
        defaultIcon=getPackageManager().getDefaultActivityIcon();
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setBackgroundColor(BG);
        setContentView(page);
        page.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets bar=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            v.setPadding(bar.left+dp(20),bar.top,bar.right+dp(20),bar.bottom);return insets;
        });
        Button back=new Button(this);back.setText("返回");back.setTextColor(ACCENT);back.setBackgroundTintList(ColorStateList.valueOf(BG));
        back.setOnClickListener(v->finish());page.addView(back,new LinearLayout.LayoutParams(-2,dp(52)));
        TextView title=text("应用黑名单",26,TEXT);page.addView(title);
        TextView help=text("选中后，该应用前台运行时不显示投影或交接淡入淡出。离开后自动恢复，选择自动保存。",14,MUTED);
        help.setPadding(0,dp(10),0,dp(12));page.addView(help);
        search=new EditText(this);search.setSingleLine(true);search.setTextColor(TEXT);search.setHintTextColor(MUTED);
        search.setHint("搜索应用名称或包名");search.setContentDescription("搜索应用名称或包名");
        page.addView(search,new LinearLayout.LayoutParams(-1,dp(56)));
        count=text("正在加载应用…",13,ACCENT);count.setPadding(0,dp(12),0,dp(10));page.addView(count);
        FrameLayout results=new FrameLayout(this);page.addView(results,new LinearLayout.LayoutParams(-1,0,1));
        ListView list=new ListView(this);list.setDividerHeight(dp(1));list.setAdapter(adapter);
        results.addView(list,new FrameLayout.LayoutParams(-1,-1));
        empty=text("正在加载应用…",15,MUTED);empty.setGravity(Gravity.CENTER);results.addView(empty,new FrameLayout.LayoutParams(-1,-1));list.setEmptyView(empty);
        list.setOnItemClickListener((parent,view,position,id)->{
            App app=visible.get(position);
            AnimationSettings.blacklist(app.pkg,!AnimationSettings.blacklistedApps.contains(app.pkg));
            adapter.notifyDataSetChanged();updateCount();
        });
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){filter();}
            public void afterTextChanged(Editable s){}
        });
        if(saved!=null)search.setText(saved.getString("query",""));
        Set<String> selected=new HashSet<>(AnimationSettings.blacklistedApps);
        loader.execute(()->{
            try{
                PackageManager pm=getPackageManager();Map<String,App> found=new HashMap<>();
                for(String category:new String[]{Intent.CATEGORY_LAUNCHER,Intent.CATEGORY_HOME}){
                    for(ResolveInfo info:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0)){
                        if(info.activityInfo==null)continue;
                        String pkg=info.activityInfo.packageName;
                        found.putIfAbsent(pkg,new App(info.loadLabel(pm).toString(),pkg));
                    }
                }
                // Retain saved entries even when an app was removed or its launcher is disabled.
                for(String pkg:selected)if(!found.containsKey(pkg)){
                    String label=pkg;
                    try{label=pm.getApplicationLabel(pm.getApplicationInfo(pkg,0)).toString();}catch(PackageManager.NameNotFoundException ignored){}
                    found.put(pkg,new App(label,pkg));
                }
                List<App> result=new ArrayList<>(found.values());Collator collator=Collator.getInstance(Locale.CHINA);
                result.sort((a,b)->{int order=Boolean.compare(selected.contains(b.pkg),selected.contains(a.pkg));
                    if(order==0)order=collator.compare(a.label,b.label);return order!=0?order:a.pkg.compareTo(b.pkg);});
                runOnUiThread(()->{if(isDestroyed()||isFinishing())return;apps.addAll(result);loaded=true;filter();});
            }catch(RuntimeException failure){
                runOnUiThread(()->{if(isDestroyed()||isFinishing())return;count.setText("应用列表加载失败");empty.setText("请返回后重试");});
            }
        });
        page.requestApplyInsets();
    }
    private void filter(){
        String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);
        visible.clear();for(App app:apps)if(app.search.contains(query))visible.add(app);
        adapter.notifyDataSetChanged();
        if(loaded){empty.setText("没有匹配的应用");updateCount();}
    }
    private void updateCount(){count.setText("已选 "+AnimationSettings.blacklistedApps.size()+" 个 · 显示 "+visible.size()+" 个应用");}
    private TextView text(String value,int size,int color){TextView view=new TextView(this);view.setText(value);view.setTextSize(size);view.setTextColor(color);return view;}
    private void loadIcon(String pkg){
        if(loader.isShutdown()||pendingIcons.size()>=12||!pendingIcons.add(pkg))return;
        loader.execute(()->{
            Bitmap bitmap=Bitmap.createBitmap(dp(40),dp(40),Bitmap.Config.ARGB_8888);
            try{
                Drawable drawable=getPackageManager().getApplicationIcon(pkg);
                drawable.setBounds(0,0,bitmap.getWidth(),bitmap.getHeight());drawable.draw(new Canvas(bitmap));
            }catch(PackageManager.NameNotFoundException|RuntimeException ignored){
                Drawable drawable=getPackageManager().getDefaultActivityIcon();
                drawable.setBounds(0,0,bitmap.getWidth(),bitmap.getHeight());drawable.draw(new Canvas(bitmap));
            }
            runOnUiThread(()->{if(isDestroyed()||isFinishing())return;pendingIcons.remove(pkg);icons.put(pkg,bitmap);adapter.notifyDataSetChanged();});
        });
    }
    private static final class Row {
        final ImageView icon;final CheckedTextView label;
        Row(ImageView icon,CheckedTextView label){this.icon=icon;this.label=label;}
    }
    private final class AppsAdapter extends BaseAdapter {
        public int getCount(){return visible.size();}
        public App getItem(int position){return visible.get(position);}
        public long getItemId(int position){return position;}
        public View getView(int position,View recycled,ViewGroup parent){
            Row row;
            if(recycled!=null)row=(Row)recycled.getTag();
            else{
                LinearLayout container=new LinearLayout(AppBlacklistActivity.this);container.setGravity(Gravity.CENTER_VERTICAL);
                container.setPadding(dp(8),dp(8),dp(8),dp(8));container.setMinimumHeight(dp(76));
                ImageView icon=new ImageView(AppBlacklistActivity.this);icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                container.addView(icon,new LinearLayout.LayoutParams(dp(40),dp(40)));
                CheckedTextView label=(CheckedTextView)getLayoutInflater().inflate(android.R.layout.simple_list_item_multiple_choice,parent,false);
                label.setTextColor(TEXT);label.setTextSize(15);label.setGravity(Gravity.CENTER_VERTICAL);
                label.setPadding(dp(14),dp(4),0,dp(4));label.setCheckMarkTintList(ColorStateList.valueOf(ACCENT));
                label.setFocusable(false);label.setClickable(false);
                container.addView(label,new LinearLayout.LayoutParams(0,-2,1));
                row=new Row(icon,label);container.setTag(row);recycled=container;
            }
            App app=getItem(position);row.label.setText(app.label+"\n"+app.pkg);
            row.label.setChecked(AnimationSettings.blacklistedApps.contains(app.pkg));
            Bitmap icon=icons.get(app.pkg);
            if(icon!=null)row.icon.setImageBitmap(icon);else{row.icon.setImageDrawable(defaultIcon);loadIcon(app.pkg);}
            return recycled;
        }
    }
    @Override public void onSaveInstanceState(Bundle out){out.putString("query",search.getText().toString());super.onSaveInstanceState(out);}
    @Override public void onDestroy(){loader.shutdownNow();super.onDestroy();}
}
