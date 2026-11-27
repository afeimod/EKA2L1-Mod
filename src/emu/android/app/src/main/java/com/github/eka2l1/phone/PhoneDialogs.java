package com.github.eka2l1.phone;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Rect;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import com.github.eka2l1.emu.Keycode;

/** Native dialogs are positioned inside the physical LCD instead of across the host display. */
public final class PhoneDialogs {
    private static final ArrayList<WeakReference<Dialog>> OPEN=new ArrayList<>();
    private PhoneDialogs() {}
    public static class PlatformBuilder extends android.app.AlertDialog.Builder {
        public PlatformBuilder(Context c){super(c);}
        public PlatformBuilder(Context c,int theme){super(c,theme);}
        @Override public android.app.AlertDialog create(){android.app.AlertDialog d=super.create();prepare(d);return d;}
    }
    public static class SupportBuilder extends androidx.appcompat.app.AlertDialog.Builder {
        public SupportBuilder(Context c){super(c);}
        public SupportBuilder(Context c,int theme){super(c,theme);}
        @Override public androidx.appcompat.app.AlertDialog create(){androidx.appcompat.app.AlertDialog d=super.create();prepare(d);return d;}
    }
    public static class Progress extends android.app.ProgressDialog {
        public Progress(Context c){
            super(c);
            setIndeterminate(true);
            // Keep the loading state inside the selected handset LCD. This is
            // an in-app Nokia Connecting People animation, so it does not
            // depend on a remote video URL or a third-party player.
            setView(new HandshakeView(c));
            prepare(this);
        }
    }
    private static final class HandshakeView extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path left=new Path(),right=new Path();
        private long start;
        private final Runnable tick=new Runnable(){@Override public void run(){invalidate();postDelayed(this,33);}};
        HandshakeView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        @Override protected void onAttachedToWindow(){super.onAttachedToWindow();start=android.os.SystemClock.uptimeMillis();post(tick);}
        @Override protected void onDetachedFromWindow(){removeCallbacks(tick);super.onDetachedFromWindow();}
        @Override protected void onDraw(Canvas c){
            float w=getWidth(),h=getHeight(),s=Math.min(w/320f,h/480f),cx=w*.5f;
            paint.setShader(new LinearGradient(0,0,w,h,Color.rgb(9,18,24),Color.rgb(2,5,8),Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,paint);paint.setShader(null);
            paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(28*s);paint.setColor(Color.rgb(230,235,235));c.drawText("NOKIA",cx,h*.22f,paint);
            paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));paint.setTextSize(10*s);paint.setColor(Color.rgb(132,164,177));
            c.drawText("Connecting People",cx,h*.27f,paint);
            float phase=((android.os.SystemClock.uptimeMillis()-start)%2200L)/2200f;
            float meet=Math.min(1f,Math.max(0f,phase*2.2f));
            if(phase>.55f)meet=1f-(phase-.55f)/.45f*.22f;
            float y=h*.52f, handW=118*s, handH=42*s, gap=(1f-meet)*74*s;
            paint.setStyle(Paint.Style.FILL);paint.setColor(Color.rgb(209,177,151));
            left.reset();left.moveTo(cx-gap-handW*.5f,y-handH*.20f);left.lineTo(cx-gap+handW*.28f,y-handH*.52f);left.lineTo(cx-gap+handW*.52f,y-handH*.15f);left.lineTo(cx-gap+handW*.22f,y+handH*.50f);left.lineTo(cx-gap-handW*.5f,y+handH*.38f);left.close();c.drawPath(left,paint);
            right.reset();right.moveTo(cx+gap+handW*.5f,y-handH*.20f);right.lineTo(cx+gap-handW*.28f,y-handH*.52f);right.lineTo(cx+gap-handW*.52f,y-handH*.15f);right.lineTo(cx+gap-handW*.22f,y+handH*.50f);right.lineTo(cx+gap+handW*.5f,y+handH*.38f);right.close();c.drawPath(right,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2.2f*s);paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(Color.rgb(121,86,72));
            c.drawLine(cx-gap-handW*.28f,y-handH*.18f,cx-gap+handW*.27f,y+handH*.27f,paint);
            c.drawLine(cx+gap+handW*.28f,y-handH*.18f,cx+gap-handW*.27f,y+handH*.27f,paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(11*s);paint.setColor(Color.rgb(155,182,191));c.drawText("处理中",cx,h*.71f,paint);
            for(int i=0;i<3;i++){float a=((phase*3-i+3)%3);paint.setColor(Color.rgb(71+(int)(a*45),142+(int)(a*20),174+(int)(a*15)));c.drawCircle(cx+(i-1)*18*s,h*.78f,4*s,paint);}
        }
    }
    private static Activity activity(Context context){
        while(context instanceof ContextWrapper){if(context instanceof Activity)return (Activity)context;context=((ContextWrapper)context).getBaseContext();}return null;
    }
    public static void prepare(Dialog dialog) {
        for(WeakReference<Dialog> ref:OPEN)if(ref.get()==dialog)return;
        OPEN.add(new WeakReference<>(dialog));
        Activity host=activity(dialog.getContext());
        if(host instanceof androidx.lifecycle.LifecycleOwner){
            androidx.lifecycle.Lifecycle lifecycle=((androidx.lifecycle.LifecycleOwner)host).getLifecycle();
            lifecycle.addObserver(new androidx.lifecycle.LifecycleEventObserver(){
                @Override public void onStateChanged(androidx.lifecycle.LifecycleOwner owner,androidx.lifecycle.Lifecycle.Event event){
                    if(event==androidx.lifecycle.Lifecycle.Event.ON_DESTROY){if(dialog.isShowing())dialog.dismiss();lifecycle.removeObserver(this);}
                }
            });
        }
        Window window=dialog.getWindow();if(window==null)return;
        window.getDecorView().addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            @Override public void onViewAttachedToWindow(View v){v.post(()->{fit(dialog);containInputs(v);});}
            @Override public void onViewDetachedFromWindow(View v){}
        });
    }
    public static void fit(Dialog dialog){
        Activity host=activity(dialog.getContext());if(host==null||host.isFinishing())return;
        Rect lcd=PhoneUi.bounds(host);if(lcd.width()<=0||lcd.height()<=0)return;
        Window window=dialog.getWindow();if(window==null)return;
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_FULLSCREEN);
        host.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        host.getWindow().getDecorView().setSystemUiVisibility(5894);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        window.getDecorView().setSystemUiVisibility(5894);
        WindowManager.LayoutParams params=window.getAttributes();
        if(android.os.Build.VERSION.SDK_INT>=android.os.Build.VERSION_CODES.P)params.layoutInDisplayCutoutMode=
            android.os.Build.VERSION.SDK_INT>=android.os.Build.VERSION_CODES.R?WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS:WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        params.gravity=Gravity.TOP|Gravity.LEFT;params.x=lcd.left;params.y=lcd.top;
        params.width=lcd.width();params.height=lcd.height();params.dimAmount=0;
        window.setAttributes(params);
        android.util.TypedValue background=new android.util.TypedValue();dialog.getContext().getTheme().resolveAttribute(android.R.attr.colorBackground,background,true);
        window.setBackgroundDrawableResource(android.R.color.transparent);
        View content=window.findViewById(android.R.id.content);if(content!=null)content.setBackgroundColor(background.data);
        window.getDecorView().setElevation(0);window.getDecorView().setPadding(0,0,0,0);
    }
    public static void refit(Activity host){
        for(int i=OPEN.size()-1;i>=0;i--){Dialog d=OPEN.get(i).get();if(d==null){OPEN.remove(i);continue;}if(d.isShowing()&&activity(d.getContext())==host)fit(d);}
    }
    public static boolean key(Context context,int code,boolean down){
        Activity host=activity(context);Dialog top=null;
        for(int i=OPEN.size()-1;i>=0;i--){Dialog d=OPEN.get(i).get();if(d!=null&&d.isShowing()&&activity(d.getContext())==host){top=d;break;}}
        if(top==null)return false;
        if(!down)return true;
        if(code==Keycode.KEY_SOFT_RIGHT||code==Keycode.KEY_END){top.cancel();return true;}
        int androidCode=code==Keycode.KEY_UP?KeyEvent.KEYCODE_DPAD_UP:code==Keycode.KEY_DOWN?KeyEvent.KEYCODE_DPAD_DOWN:
            code==Keycode.KEY_LEFT?KeyEvent.KEYCODE_DPAD_LEFT:code==Keycode.KEY_RIGHT?KeyEvent.KEYCODE_DPAD_RIGHT:
            code==Keycode.KEY_FIRE||code==Keycode.KEY_SOFT_LEFT?KeyEvent.KEYCODE_DPAD_CENTER:0;
        if(androidCode!=0){top.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,androidCode));top.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,androidCode));}
        return true;
    }
    public static void containInputs(View view){
        if(view instanceof EditText&&!"phone-keyboard".equals(view.getTag())){
            EditText input=(EditText)view;input.setShowSoftInputOnFocus(false);
            input.setOnClickListener(v->askText(activity(v.getContext()),"输入",input.getText().toString(),input::setText));
        }
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)containInputs(((ViewGroup)view).getChildAt(i));
    }
    public interface TextAction{void done(String value);}
    public static void askText(Activity host,String title,String value,TextAction action){
        LinearLayout content=new LinearLayout(host);content.setOrientation(LinearLayout.VERTICAL);
        EditText input=new EditText(host);input.setTag("phone-keyboard");input.setText(value);input.setSingleLine(true);input.setShowSoftInputOnFocus(false);
        content.addView(input,new LinearLayout.LayoutParams(-1,-2));
        String[] rows={"1234567890","qwertyuiop","asdfghjkl","zxcvbnm","./:_-@+"};
        int keyHeight=Math.round(38*host.getResources().getDisplayMetrics().density);
        for(String chars:rows){LinearLayout row=new LinearLayout(host);content.addView(row,new LinearLayout.LayoutParams(-1,-2));
            for(char letter:chars.toCharArray()){Button b=new Button(host);b.setText(String.valueOf(letter));b.setTextSize(12);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(0,0,0,0);row.addView(b,new LinearLayout.LayoutParams(0,keyHeight,1));
                b.setAllCaps(false);b.setOnClickListener(v->{int start=Math.max(0,input.getSelectionStart()),end=Math.max(start,input.getSelectionEnd());input.getText().replace(start,end,String.valueOf(letter));});
            }
        }
        LinearLayout row=new LinearLayout(host);content.addView(row);
        for(String label:new String[]{"空格","⌫","清空","大小写"}){Button b=new Button(host);b.setText(label);b.setTextSize(12);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(0,0,0,0);row.addView(b,new LinearLayout.LayoutParams(0,keyHeight,1));b.setOnClickListener(v->{
            int i=Math.max(0,input.getSelectionStart());if(label.equals("空格"))input.getText().insert(i," ");else if(label.equals("⌫")){if(i>0)input.getText().delete(i-1,i);}else if(label.equals("清空"))input.setText("");else{input.setText(input.getText().toString().equals(input.getText().toString().toUpperCase())?input.getText().toString().toLowerCase():input.getText().toString().toUpperCase());input.setSelection(input.length());}
        });}
        ScrollView scroll=new ScrollView(host);scroll.addView(content);
        new SupportBuilder(host).setTitle(title).setView(scroll).setPositiveButton("确定",(d,w)->action.done(input.getText().toString())).setNegativeButton("返回",null).show();
        input.setSelection(input.length());
    }
}
