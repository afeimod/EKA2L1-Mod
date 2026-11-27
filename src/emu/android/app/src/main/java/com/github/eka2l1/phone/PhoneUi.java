package com.github.eka2l1.phone;

import android.app.Activity;
import android.graphics.Rect;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.github.eka2l1.R;
import com.github.eka2l1.settings.AppDataStore;
import java.util.ArrayList;

/** All application chrome lives in the LCD, including settings and file browsers. */
public final class PhoneUi {
    private PhoneUi() {}
    public static void useDisplayCutout(Activity activity) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            WindowManager.LayoutParams attrs=activity.getWindow().getAttributes();
            attrs.layoutInDisplayCutoutMode=android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
                ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            activity.getWindow().setAttributes(attrs);
        }
    }
    public static PhoneShellLayout wrap(Activity activity, View content, boolean toolbarNeeded) {
        PhoneShellLayout shell=new PhoneShellLayout(activity);shell.setId(R.id.phone_shell);shell.setScaledUi(true);
        shell.setModel(PhoneModel.parse(AppDataStore.getAndroidStore().getString(PhoneModel.PREF,"N73")));
        LinearLayout screen=new LinearLayout(activity);screen.setOrientation(LinearLayout.VERTICAL);
        android.util.TypedValue background=new android.util.TypedValue();activity.getTheme().resolveAttribute(android.R.attr.colorBackground,background,true);screen.setBackgroundColor(background.data);
        if(toolbarNeeded){Toolbar toolbar=new PhoneToolbar(activity);toolbar.setId(R.id.phone_toolbar);
            toolbar.setBackgroundColor(0xff1c3040);toolbar.setTitleTextColor(0xffedf5fa);
            screen.addView(toolbar,new LinearLayout.LayoutParams(-1,0));
            ((AppCompatActivity)activity).setSupportActionBar(toolbar);
            toolbar.setVisibility(View.GONE);
        }
        screen.addView(content,new LinearLayout.LayoutParams(-1,0,1));shell.addView(screen,new FrameLayout.LayoutParams(-1,-1));
        shell.setKeyListener((code,down)->key((AppCompatActivity)activity,code,down));
        activity.getWindow().getDecorView().setSystemUiVisibility(5894);
        return shell;
    }
    public static void showOptions(AppCompatActivity activity) {
        Toolbar toolbar=activity.findViewById(R.id.phone_toolbar);
        if(toolbar==null)toolbar=activity.findViewById(R.id.toolbar);
        if(toolbar==null)return;
        showMenu(activity,toolbar.getMenu());
    }
    public static void key(AppCompatActivity activity,int code,boolean down){
        if(!down)return;
        if(code==com.github.eka2l1.emu.Keycode.KEY_SOFT_LEFT||code==com.github.eka2l1.emu.Keycode.KEY_SEND
                ||code==com.github.eka2l1.emu.Keycode.KEY_MENU)showOptions(activity);
        else if(code==com.github.eka2l1.emu.Keycode.KEY_SOFT_RIGHT||code==com.github.eka2l1.emu.Keycode.KEY_END)activity.onBackPressed();
        else {int key=code==16?KeyEvent.KEYCODE_DPAD_UP:code==17?KeyEvent.KEYCODE_DPAD_DOWN:code==14?KeyEvent.KEYCODE_DPAD_LEFT:code==15?KeyEvent.KEYCODE_DPAD_RIGHT:code==167?KeyEvent.KEYCODE_DPAD_CENTER:0;
            if(key!=0){activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,key));activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,key));}
        }
    }
    public static void showMenu(AppCompatActivity activity,Menu menu) {showMenu(activity,menu,null);}
    public interface MenuAction { boolean select(MenuItem item); }
    public static void showMenu(AppCompatActivity activity,Menu menu,MenuAction action) {
        ArrayList<MenuItem> items=new ArrayList<>();ArrayList<String> labels=new ArrayList<>();
        for(int i=0;i<menu.size();i++){MenuItem item=menu.getItem(i);if(item.isVisible()){items.add(item);labels.add(item.getTitle().toString());}}
        items.sort((a,b)->Integer.compare(priority(a.getItemId()),priority(b.getItemId())));
        labels.clear();for(MenuItem item:items)labels.add(item.getTitle().toString());
        if(items.isEmpty()){new PhoneDialogs.SupportBuilder(activity).setTitle("选项").setItems(new String[]{"返回"},(d,i)->activity.onBackPressed()).show();return;}
        new PhoneDialogs.SupportBuilder(activity).setTitle("选项").setItems(labels.toArray(new String[0]),(dialog,index)->{
            MenuItem item=items.get(index);if(item.hasSubMenu())showMenu(activity,item.getSubMenu(),action);
            else if(action!=null)action.select(item);
            else if(!menu.performIdentifierAction(item.getItemId(),0))activity.onMenuItemSelected(Window.FEATURE_OPTIONS_PANEL,item);
        }).setNegativeButton("返回",null).show();
    }
    private static int priority(int id){return id==R.id.action_phone_family?0:id==R.id.action_phone_orientation?1:id==R.id.action_phone_keys?2:id==R.id.action_phone_install?3:4;}
    public static Rect bounds(Activity activity) {
        PhoneShellLayout shell=findShell(activity);
        return shell==null?new Rect(0,0,activity.getResources().getDisplayMetrics().widthPixels,activity.getResources().getDisplayMetrics().heightPixels):shell.getScreenBoundsOnScreen();
    }
    /** Reconcile a resumed host shell with orientation and model changes made by a game. */
    public static void refreshShell(Activity activity){
        PhoneShellLayout shell=findShell(activity);
        if(shell==null)return;
        shell.setModel(PhoneModel.parse(getPhoneModelName()));
        shell.setOrientationMode(getPhoneOrientation());
        shell.requestLayout();
        shell.invalidate();
    }
    private static PhoneShellLayout findShell(Activity activity){
        PhoneShellLayout shell=activity.findViewById(R.id.phone_shell);
        return shell==null?activity.findViewById(R.id.emulator_container):shell;
    }
    public static void chooseOrientation(Activity activity){
        AppDataStore store=AppDataStore.getAndroidStore();
        String current=store.getString("phone-orientation","auto");
        new PhoneDialogs.SupportBuilder(activity).setTitle("屏幕方向")
            .setSingleChoiceItems(new String[]{"竖屏","横屏","机型默认"},current.equals("portrait")?0:current.equals("landscape")?1:2,(d,i)->{
                setPhoneOrientation(activity,new String[]{"portrait","landscape","auto"}[i]);d.dismiss();
            }).setNegativeButton("返回",null).show();
    }
    public static void chooseModel(Activity activity){
        AppDataStore store=AppDataStore.getAndroidStore();
        String current=store.getString(PhoneModel.PREF,"N73");
        String[] names={"N73","N86","N8","QD"};
        String[] labels={"Nokia N73","Nokia N86","Nokia N8","N-Gage QD"};
        int selected=0;for(int i=0;i<names.length;i++)if(names[i].equals(current)){selected=i;break;}
        new PhoneDialogs.SupportBuilder(activity).setTitle("机型")
            .setSingleChoiceItems(labels,selected,(d,i)->{setPhoneModel(activity,names[i]);d.dismiss();})
            .setNegativeButton("返回",null).show();
    }
    public static String getPhoneModelName(){
        return AppDataStore.getAndroidStore().getString(PhoneModel.PREF,"N73");
    }
    public static String getPhoneOrientation(){
        return AppDataStore.getAndroidStore().getString("phone-orientation","auto");
    }
    public static void setPhoneModel(Activity activity,String name){
        PhoneModel model=PhoneModel.parse(name);
        AppDataStore store=AppDataStore.getAndroidStore();store.putString(PhoneModel.PREF,model.name());store.save();
        PhoneShellLayout shell=findShell(activity);
        if(shell!=null){shell.setOrientationMode(getPhoneOrientation());shell.setModel(model);}
        PhoneModel.applyOrientation(activity);
    }
    /** Settings screens save the next-launch model without rotating the editor itself. */
    public static void setPhoneModelFromSettings(Activity activity,String name){
        PhoneModel model=PhoneModel.parse(name);
        AppDataStore store=AppDataStore.getAndroidStore();store.putString(PhoneModel.PREF,model.name());store.save();
        PhoneShellLayout shell=findShell(activity);
        if(shell!=null){shell.setOrientationMode(getPhoneOrientation());shell.setModel(model);}
    }
    public static void setPhoneOrientation(Activity activity,String mode){
        AppDataStore store=AppDataStore.getAndroidStore();store.putString("phone-orientation",mode);store.save();
        PhoneShellLayout shell=findShell(activity);
        if(shell!=null)shell.setOrientationMode(mode);
        PhoneModel.applyOrientation(activity);
    }
    /** Defer orientation changes while a Java game's settings page is open. */
    public static void setPhoneOrientationFromSettings(Activity activity,String mode){
        AppDataStore store=AppDataStore.getAndroidStore();store.putString("phone-orientation",mode);store.save();
        PhoneShellLayout shell=findShell(activity);
        if(shell!=null)shell.setOrientationMode(mode);
    }
    /** Native MIDP LCD size for the selected handset; games may override it via their descriptor. */
    public static int[] getDefaultJavaDisplaySize(){
        String selected=getPhoneModelName();
        int w=240,h=320;
        if(selected.equals("QD")){w=176;h=208;}
        else if(selected.equals("N8")||selected.equals("N86")||selected.equals("N86_MEDIA")){w=360;h=640;}
        if(getPhoneOrientation().equals("landscape")||(selected.equals("QD")&&getPhoneOrientation().equals("auto"))){
            int swap=w;w=h;h=swap;
        }
        return new int[]{w,h};
    }
}
