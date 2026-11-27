package com.github.eka2l1.phone;

import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import com.github.eka2l1.applist.AppItem;
import com.github.eka2l1.applist.AppItemViewHolder;
import com.github.eka2l1.applist.AppsListAdapter;
import com.github.eka2l1.emu.EmulatorActivity;
import com.github.eka2l1.emu.Keycode;
import static com.github.eka2l1.emu.Constants.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Guest application catalog, drawn from real UIDs, titles and icons. */
public class PhoneHomeScreen extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    // Bitmap opacity must never inherit translucent wallpaper or selection paint.
    private final Paint iconPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private AppsListAdapter adapter;
    private PhoneModel model=PhoneModel.N73;
    private String device="正在读取系统…";
    private boolean available;
    private int selected,page;
    private float startX,startY;
    private long started;
    private Runnable options;
    private Runnable context;
    private final RecyclerView.AdapterDataObserver observer=new RecyclerView.AdapterDataObserver(){
        @Override public void onChanged(){selected=0;page=0;invalidate();}
    };
    public PhoneHomeScreen(Context c){this(c,null);}
    public PhoneHomeScreen(Context c,AttributeSet a){super(c,a);setFocusable(true);setContentDescription("模拟器系统软件列表");}
    public void setAdapter(AppsListAdapter value){if(adapter!=null)adapter.unregisterAdapterDataObserver(observer);adapter=value;if(value!=null)value.registerAdapterDataObserver(observer);invalidate();}
    public void setDevice(PhoneModel value,String name,boolean installed){model=value;device=name;available=installed;selected=0;page=0;invalidate();}
    public void setOptionsAction(Runnable action){options=action;}
    public void setContextAction(Runnable action){context=action;}
    private int count(){
        if(adapter==null)return 0;
        if(available)return adapter.getItemCount();
        int javaCount=0;for(int i=0;i<adapter.getItemCount();i++)if(adapter.getItem(i).isJavaApp())javaCount++;
        return javaCount;
    }
    private AppItem itemAt(int visibleIndex){
        if(available)return adapter.getItem(visibleIndex);
        for(int i=0;i<adapter.getItemCount();i++){AppItem item=adapter.getItem(i);if(item.isJavaApp()&&visibleIndex--==0)return item;}
        throw new IndexOutOfBoundsException();
    }
    private int adapterIndex(int visibleIndex){
        if(available)return visibleIndex;
        for(int i=0;i<adapter.getItemCount();i++){if(adapter.getItem(i).isJavaApp()&&visibleIndex--==0)return i;}
        return -1;
    }
    private void text(Canvas c,String value,float x,float y,float size,int color,Paint.Align align,boolean bold){
        p.setShader(null);p.setColor(color);p.setTextSize(size);p.setTextAlign(align);p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));c.drawText(value,x,y,p);
    }
    private int columns(){return getWidth()>getHeight()?4:3;}
    private int perPage(){return columns()*3;}
    private float top(){return getHeight()*.025f;}
    private float bottom(){return getHeight()*.93f;}
    private String fit(String value,float width,float size){p.setTextSize(size);while(p.measureText(value)>width&&value.length()>2)value=value.substring(0,value.length()-2)+"…";return value;}
    @Override protected void onDraw(Canvas c){
        float w=getWidth(),h=getHeight(),f=w/334f;
        int base=model==PhoneModel.N73?0xffa74770:model==PhoneModel.QD?0xff173849:model==PhoneModel.N8?0xff225b8c:0xff26323e;
        p.setShader(new LinearGradient(0,0,w,h,new int[]{base,0xff111d30,base},null,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);
        p.setColor(0x14ffffff);Path light=new Path();light.moveTo(0,0);light.lineTo(w*.52f,0);light.lineTo(w*.13f,h);light.lineTo(0,h);light.close();c.drawPath(light,p);
        int n=count(),cols=columns(),size=perPage();float cw=w/cols,rh=(bottom()-top())/3;
        for(int slot=0;slot<size;slot++){
            int index=page*size+slot;if(index>=n)break;AppItem item=itemAt(index);
            float x=(slot%cols)*cw,y=top()+(slot/cols)*rh;
            if(index==selected){p.setColor(0x804f91b9);c.drawRoundRect(new RectF(x+4*f,y+3*f,x+cw-4*f,y+rh-3*f),7*f,7*f,p);p.setColor(0xffb5ddf4);p.setStyle(Paint.Style.STROKE);c.drawRoundRect(new RectF(x+4*f,y+3*f,x+cw-4*f,y+rh-3*f),7*f,7*f,p);p.setStyle(Paint.Style.FILL);}
            float icon=Math.min(cw*.46f,rh*.49f),cx=x+cw/2,cy=y+rh*.4f;
            if(item.getIcon()!=null){
                // Do not inherit the selection tile's translucent paint state.
                iconPaint.setAlpha(255);
                iconPaint.setXfermode(null);
                c.drawBitmap(item.getIcon(),null,new RectF(cx-icon/2,cy-icon/2,cx+icon/2,cy+icon/2),iconPaint);
            }
            else {p.setShader(new LinearGradient(cx,cy-icon/2,cx,cy+icon/2,0xffa5d1e8,0xff276a92,Shader.TileMode.CLAMP));c.drawRoundRect(new RectF(cx-icon/2,cy-icon/2,cx+icon/2,cy+icon/2),8*f,8*f,p);p.setShader(null);text(c,item.getTitle().isEmpty()?"·":item.getTitle().substring(0,1),cx,cy+8*f,23*f,Color.WHITE,Paint.Align.CENTER,true);}
            text(c,fit(item.getTitle(),cw-8*f,12*f),cx,y+rh*.82f,12*f,Color.WHITE,Paint.Align.CENTER,false);
        }
        if(n==0){text(c,available?"暂无软件":"请导入对应固件",w/2,h*.45f,18*f,Color.WHITE,Paint.Align.CENTER,false);text(c,available?"选项 → 安装软件":"选项 → 设备管理",w/2,h*.53f,13*f,0xffcadce7,Paint.Align.CENTER,false);}
        p.setColor(0x6602070e);c.drawRect(0,bottom(),w,h,p);
        float baseline=h-(h-bottom())*.32f;
        text(c,"选项",12*f,baseline,14*f,Color.WHITE,Paint.Align.LEFT,false);
        text(c,n==0?"导入":"打开",w/2,baseline,14*f,Color.WHITE,Paint.Align.CENTER,false);
        text(c,(page+1)+"/"+Math.max(1,(n+size-1)/size),w-12*f,baseline,12*f,Color.WHITE,Paint.Align.RIGHT,false);
    }
    public void key(int code,boolean down){if(!down)return;int n=count();
        if(code==Keycode.KEY_FIRE||code==Keycode.KEY_CALL){launch();return;}
        if(code==Keycode.KEY_MENU){if(options!=null)options.run();return;}
        if(code==Keycode.KEY_SOFT_LEFT||code==Keycode.KEY_SEND){if(options!=null)options.run();return;}
        if(code==Keycode.KEY_SOFT_RIGHT){turnPage(1);return;}
        int delta=code==Keycode.KEY_UP?-columns():code==Keycode.KEY_DOWN?columns():code==Keycode.KEY_LEFT?-1:code==Keycode.KEY_RIGHT?1:0;
        if(n>0&&delta!=0){selected=(selected+delta+n)%n;page=selected/perPage();invalidate();}
        if(code>='1'&&code<='9'&&page*perPage()+code-'1'<n){selected=page*perPage()+code-'1';launch();}
    }
    private void turnPage(int direction){int pages=Math.max(1,(count()+perPage()-1)/perPage());page=(page+direction+pages)%pages;selected=page*perPage();invalidate();}
    private void launch(){if(count()==0){if(options!=null)options.run();return;}AppItem item=itemAt(selected);
        if(item.isJavaApp()){ru.playsoftware.j2meloader.config.Config.startApp(getContext(),item.getTitle(),item.getJavaAppPath());return;}
        Intent intent=new Intent(getContext(),EmulatorActivity.class);intent.putExtra(KEY_APP_UID,item.getUid());intent.putExtra(KEY_APP_NAME,item.getTitle());intent.putExtra("phone_shell",model.name());getContext().startActivity(intent);
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN){startX=e.getX();startY=e.getY();started=android.os.SystemClock.uptimeMillis();return true;}
        if(e.getActionMasked()==MotionEvent.ACTION_UP){float dx=e.getX()-startX,dy=e.getY()-startY;
            if(Math.abs(dx)>getWidth()*.16f||Math.abs(dy)>getHeight()*.15f){turnPage(dx<0||dy<0?1:-1);return true;}
            if(e.getY()>bottom()){if(e.getX()<getWidth()/3f){if(options!=null)options.run();}else if(e.getX()>getWidth()*2/3f)turnPage(1);else launch();return true;}
            if(e.getY()>=top()&&e.getY()<bottom()){int col=Math.min(columns()-1,(int)(e.getX()/getWidth()*columns()));int row=(int)((e.getY()-top())/(bottom()-top())*3);int index=page*perPage()+row*columns()+col;
                if(index<count()){selected=index;invalidate();if(android.os.SystemClock.uptimeMillis()-started>550){AppItemViewHolder.contextIndex=adapterIndex(index);if(context!=null)context.run();}else{performClick();launch();}}
            }return true;
        }return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
