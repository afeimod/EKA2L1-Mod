package com.github.eka2l1.phone;

import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.util.LruCache;
import android.util.SparseIntArray;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import org.json.*;
import java.io.*;
import java.util.ArrayList;

/** A permanent handset, with a single LCD viewport shared by host and guest UI. */
public class PhoneShellLayout extends FrameLayout {
    public interface KeyListener { void onKey(int code, boolean down); }
    private static final LruCache<String,Bitmap> CACHE=new LruCache<String,Bitmap>(48*1024*1024){
        @Override protected int sizeOf(String key,Bitmap value){return value.getAllocationByteCount();}
    };
    private static class Key {
        final RectF hitBounds,pressedBounds;
        final Rect hitAtlas,pressedAtlas;
        final int code;
        final boolean joystick;
        Key(JSONObject item)throws JSONException{
            hitBounds=rect(item.getJSONArray("hit_bounds"));pressedBounds=rect(item.getJSONArray("pressed_bounds"));
            hitAtlas=source(item.getJSONArray("hit_atlas"));pressedAtlas=source(item.getJSONArray("pressed_atlas"));
            code=item.getInt("code");joystick=item.optBoolean("joystick",false);
        }
        private static RectF rect(JSONArray a)throws JSONException{return new RectF((float)a.getDouble(0),(float)a.getDouble(1),(float)a.getDouble(2),(float)a.getDouble(3));}
        private static Rect source(JSONArray a)throws JSONException{return new Rect(a.getInt(0),a.getInt(1),a.getInt(2),a.getInt(3));}
    }
    private static class Navigation {
        final RectF bounds, hitBounds;
        final Rect[] states = new Rect[9];
        final float cx, cy, deadzone;
        final boolean joystick, confirm;
        final Region hitRegion;
        final Path hitPath;
        Navigation(JSONObject item) throws JSONException {
            bounds=Key.rect(item.getJSONArray("bounds"));
            hitBounds=Key.rect(item.getJSONArray("hit_bounds"));
            JSONArray center=item.getJSONArray("center");
            cx=(float)center.getDouble(0);cy=(float)center.getDouble(1);
            deadzone=(float)item.getDouble("deadzone");
            joystick=item.optBoolean("joystick");confirm=item.optBoolean("confirm");
            JSONArray polygon=item.optJSONArray("hit_polygon");
            if(polygon!=null&&polygon.length()>=3){
                Path path=new Path();
                path.moveTo((float)polygon.getJSONArray(0).getDouble(0),(float)polygon.getJSONArray(0).getDouble(1));
                for(int i=1;i<polygon.length();i++){
                    JSONArray point=polygon.getJSONArray(i);
                    path.lineTo((float)point.getDouble(0),(float)point.getDouble(1));
                }
                path.close();
                hitPath=path;
                Rect clip=new Rect((int)Math.floor(hitBounds.left),(int)Math.floor(hitBounds.top),
                    (int)Math.ceil(hitBounds.right),(int)Math.ceil(hitBounds.bottom));
                Region region=new Region();region.setPath(path,new Region(clip));hitRegion=region;
            }else{hitPath=null;hitRegion=null;}
            JSONArray list=item.getJSONArray("states");
            for(int i=0;i<9;i++)states[i]=Key.source(list.getJSONArray(i));
        }
    }
    private static class NavigationTouch {
        final PointF origin;
        final boolean drag;
        boolean moved;
        NavigationTouch(PointF origin,boolean drag){this.origin=origin;this.drag=drag;}
    }
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final ArrayList<Key> keys=new ArrayList<>();
    private final SparseArray<int[]> pointers=new SparseArray<>();
    private final SparseIntArray held=new SparseIntArray();
    private final SparseArray<NavigationTouch> navigationTouches=new SparseArray<>();
    private final SparseArray<int[]> navigationPointers=new SparseArray<>();
    private final SparseIntArray heldNavigation=new SparseIntArray();
    private Navigation navigation;
    private final RectF lcd=new RectF();
    private PhoneModel family=PhoneModel.N73,model=PhoneModel.N73;
    private Bitmap chassis,keyTextures,navigationTextures;
    private boolean scaledUi;
    private boolean ngageGame;
    private String orientationMode;
    private float sx=1,sy=1,ox,oy,contentScale=1;
    private KeyListener listener;

    public PhoneShellLayout(Context c){this(c,null);}
    public PhoneShellLayout(Context c,AttributeSet attrs){
        super(c,attrs);setWillNotDraw(false);setClipChildren(true);setClipToPadding(true);
        orientationMode=com.github.eka2l1.settings.AppDataStore.getAndroidStore()
                .getString("phone-orientation","auto");
        load();
    }
    public PhoneModel getModel(){return model;}
    public void setNgageGame(boolean value){ngageGame=value;}
    public void setScaledUi(boolean value){scaledUi=value;requestLayout();}
    public void setKeyListener(KeyListener value){releaseKeys();listener=value;}
    public void setOrientationMode(String value){
        orientationMode=value==null?"auto":value;
        PhoneModel next=family.oriented(isLandscape());
        if(next!=model){releaseKeys();model=next;load();}
        requestLayout();invalidate();
    }
    public void setModel(PhoneModel value){
        family=value;PhoneModel next=family.oriented(isLandscape());
        if(next!=model){releaseKeys();model=next;load();}
        setContentDescription(model.title+" 可操作机身");requestLayout();invalidate();
    }
    private boolean isLandscape(){
        if(orientationMode.equals("portrait"))return false;
        if(orientationMode.equals("landscape"))return true;
        // "auto" uses the handset's stable default direction. Relying on the
        // window's transient aspect ratio makes the shell flash into a landscape
        // variant while Android is switching activities.
        return family==PhoneModel.QD;
    }
    public Rect getScreenBoundsOnScreen(){
        int[] p=new int[2];getLocationOnScreen(p);
        return new Rect(Math.round(p[0]+lcd.left),Math.round(p[1]+lcd.top),Math.round(p[0]+lcd.right),Math.round(p[1]+lcd.bottom));
    }
    private void load(){
        keys.clear();String asset=model.asset;chassis=CACHE.get(asset);
        try {
            if(chassis==null){try(InputStream in=getContext().getAssets().open("phone/"+asset+".webp")){chassis=BitmapFactory.decodeStream(in);}CACHE.put(asset,chassis);}
            keyTextures=CACHE.get(asset+"-keys");
            if(keyTextures==null){try(InputStream in=getContext().getAssets().open("phone/"+asset+"-keys.webp")){keyTextures=BitmapFactory.decodeStream(in);}CACHE.put(asset+"-keys",keyTextures);}
            StringBuilder json=new StringBuilder();
            try(BufferedReader r=new BufferedReader(new InputStreamReader(getContext().getAssets().open("phone/"+asset+".json")))){String line;while((line=r.readLine())!=null)json.append(line);}
            JSONArray list=new JSONArray(json.toString());
            for(int i=0;i<list.length();i++)keys.add(new Key(list.getJSONObject(i)));
            navigation=null;navigationTextures=null;
            try(BufferedReader r=new BufferedReader(new InputStreamReader(getContext().getAssets().open("phone/"+asset+"-nav.json")))){
                StringBuilder data=new StringBuilder();String line;while((line=r.readLine())!=null)data.append(line);
                JSONObject nav=new JSONObject(data.toString());
                if(nav.has("states")){
                    navigation=new Navigation(nav);navigationTextures=CACHE.get(asset+"-nav");
                    if(navigationTextures==null){try(InputStream in=getContext().getAssets().open("phone/"+asset+"-nav.webp")){navigationTextures=BitmapFactory.decodeStream(in);}CACHE.put(asset+"-nav",navigationTextures);}
                }
            }
        }catch(IOException|JSONException e){throw new IllegalStateException("Handset asset "+asset,e);}
    }
    @Override protected void onMeasure(int ws,int hs){
        int w=MeasureSpec.getSize(ws),h=MeasureSpec.getSize(hs);setMeasuredDimension(w,h);
        PhoneModel next=family.oriented(isLandscape());if(next!=model){releaseKeys();model=next;load();}
        sx=sy=Math.max(w/model.width,h/model.height);
        ox=(w-model.width*sx)/2;oy=(h-model.height*sy)/2;
        lcd.set(ox+model.screen.left*sx,oy+model.screen.top*sy,ox+model.screen.right*sx,oy+model.screen.bottom*sy);
        float logicalWidth=scaledUi?Math.max(lcd.width(),getResources().getDisplayMetrics().density*300):lcd.width();
        contentScale=lcd.width()/logicalWidth;
        for(int i=0;i<getChildCount();i++)getChildAt(i).measure(MeasureSpec.makeMeasureSpec(Math.round(logicalWidth),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.round(lcd.height()/contentScale),MeasureSpec.EXACTLY));
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        for(int i=0;i<getChildCount();i++){View child=getChildAt(i);
            child.layout(Math.round(lcd.left),Math.round(lcd.top),Math.round(lcd.left)+child.getMeasuredWidth(),Math.round(lcd.top)+child.getMeasuredHeight());
            child.setPivotX(0);child.setPivotY(0);child.setScaleX(contentScale);child.setScaleY(contentScale);
            child.setClipBounds(new Rect(0,0,child.getMeasuredWidth(),child.getMeasuredHeight()));
        }
        if(changed&&getContext() instanceof Activity)post(()->PhoneDialogs.refit((Activity)getContext()));
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);c.save();c.translate(ox,oy);c.scale(sx,sy);
        if(model==PhoneModel.N8_LANDSCAPE){c.translate(850,0);c.rotate(90);c.drawBitmap(chassis,null,new RectF(0,0,430,850),paint);}
        else c.drawBitmap(chassis,null,new RectF(0,0,model.width,model.height),paint);
        int dx=0,dy=0;boolean navDown=false;
        for(int i=0;i<heldNavigation.size();i++){
            int code=heldNavigation.keyAt(i);
            if(isNavigationKey(code)){
                navDown=true;dx+=code==14?-1:code==15?1:0;dy+=code==16?-1:code==17?1:0;
            }
        }
        for(int i=0;i<held.size();i++){
            Key key=keys.get(held.keyAt(i));
            if(navigation!=null&&isNavigationKey(key.code)){
                navDown=true;dx+=key.code==14?-1:key.code==15?1:0;dy+=key.code==16?-1:key.code==17?1:0;
            }else c.drawBitmap(keyTextures,key.pressedAtlas,key.pressedBounds,paint);
        }
        for(int i=0;i<navigationTouches.size();i++)if(navigationTouches.valueAt(i).drag&&!navigationTouches.valueAt(i).moved)navDown=true;
        if(navDown&&navigation!=null){
            int state=(Integer.signum(dy)+1)*3+Integer.signum(dx)+1;
            // Filtered bitmap sampling can bleed one pixel past a fractional
            // destination edge; keep the sculpted navigation cap inside its
            // photographed assembly.
            c.save();
            if(navigation.hitPath!=null)c.clipPath(navigation.hitPath);
            else c.clipRect(navigation.bounds);
            c.drawBitmap(navigationTextures,navigation.states[state],navigation.bounds,paint);
            c.restore();
        }
        c.restore();
    }
    private PointF local(float x,float y){
        x=(x-ox)/sx;y=(y-oy)/sy;
        if(model==PhoneModel.N8_LANDSCAPE){float oldX=x;x=y;y=850-oldX;}
        return new PointF(x,y);
    }
    private int hit(float x,float y){
        PointF point=local(x,y);x=point.x;y=point.y;
        for(int i=keys.size()-1;i>=0;i--){Key key=keys.get(i);if(key.hitBounds.contains(x,y)){
            int px=key.hitAtlas.left+Math.min(key.hitAtlas.width()-1,(int)((x-key.hitBounds.left)/key.hitBounds.width()*key.hitAtlas.width()));
            int py=key.hitAtlas.top+Math.min(key.hitAtlas.height()-1,(int)((y-key.hitBounds.top)/key.hitBounds.height()*key.hitAtlas.height()));
            if(Color.alpha(keyTextures.getPixel(px,py))>127)return i;
        }}return -1;
    }
    private boolean isNavigationKey(int code){return code==14||code==15||code==16||code==17||(code==167&&navigation.confirm);}
    private int[] direction(float dx,float dy){
        // A joystick gesture resolves to exactly one of the four cardinal
        // directions, even when the finger drifts diagonally.
        float ax=Math.abs(dx),ay=Math.abs(dy);
        if(ax==0&&ay==0)return new int[0];
        return ax>ay?new int[]{dx<0?14:15}:new int[]{dy<0?16:17};
    }
    private boolean navigationHit(PointF p){
        if(navigation==null||!navigation.hitBounds.contains(p.x,p.y))return false;
        if(navigation.hitRegion!=null)return navigation.hitRegion.contains(Math.round(p.x),Math.round(p.y));
        float dx=(p.x-navigation.cx)/(navigation.hitBounds.width()/2),dy=(p.y-navigation.cy)/(navigation.hitBounds.height()/2);
        return dx*dx+dy*dy<=1.08f;
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent e){return e.getActionMasked()==MotionEvent.ACTION_DOWN&&(hit(e.getX(),e.getY())>=0||navigationHit(local(e.getX(),e.getY())));}
    private void emit(int code,boolean down){if(!PhoneDialogs.key(getContext(),code,down)&&listener!=null)listener.onKey(code,down);}
    private void emitKey(int index,boolean down){
        Key key=keys.get(index);
        emit(PhoneKeyMappings.get(model.asset,"key:"+index,key.code,ngageGame),down);
    }
    private void emitNavigation(int code,boolean down){
        String control=code==16?"nav:up":code==17?"nav:down":code==14?"nav:left":code==15?"nav:right":"nav:center";
        emit(PhoneKeyMappings.get(model.asset,control,code,ngageGame),down);
    }
    private static boolean contains(int[] values,int value){if(values!=null)for(int v:values)if(v==value)return true;return false;}
    private void pointer(int id,int... next){
        if(next!=null&&next.length>0){
            int count=0;for(int index:next)if(index>=0)count++;
            if(count!=next.length){int[] valid=new int[count];int at=0;for(int index:next)if(index>=0)valid[at++]=index;next=valid;}
        }
        int[] old=pointers.get(id);
        if(next.length==0)pointers.remove(id);else pointers.put(id,next);
        if(old!=null)for(int index:old)if(!contains(next,index)){
            if(index<0||index>=keys.size())continue;
            int count=held.get(index)-1;
            if(count==0){held.delete(index);emitKey(index,false);}else held.put(index,count);
        }
        for(int index:next)if(index>=0&&!contains(old,index)){
            if(index>=keys.size())continue;
            int count=held.get(index);held.put(index,count+1);if(count==0)emitKey(index,true);
        }
    }
    private void navigationPointer(int id,int... next){
        int[] old=navigationPointers.get(id);
        if(next.length==0)navigationPointers.remove(id);else navigationPointers.put(id,next);
        if(old!=null)for(int code:old)if(!contains(next,code)){
            int count=heldNavigation.get(code)-1;
            if(count<=0){heldNavigation.delete(code);emitNavigation(code,false);}else heldNavigation.put(code,count);
        }
        for(int code:next)if(code>=0&&!contains(old,code)){
            int count=heldNavigation.get(code);heldNavigation.put(code,count+1);if(count==0)emitNavigation(code,true);
        }
    }
    private void updateNavigation(int id,PointF point){
        NavigationTouch touch=navigationTouches.get(id);
        float dx=point.x-(touch.drag?touch.origin.x:navigation.cx),dy=point.y-(touch.drag?touch.origin.y:navigation.cy);
        // Ignore small hand jitter around the joystick's touch origin.
        float deadzone=touch.drag?Math.max(5,navigation.deadzone*.5f):navigation.deadzone;
        if(Math.hypot(dx,dy)>deadzone){
            touch.moved=true;navigationPointer(id,direction(dx,dy));
        }else if(!touch.drag&&navigation.confirm){navigationPointer(id,167);}
        else navigationPointer(id);
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        int action=e.getActionMasked(),i=e.getActionIndex();
        if(action==MotionEvent.ACTION_CANCEL){releaseKeys();return true;}
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){
            int id=e.getPointerId(i),index=hit(e.getX(i),e.getY(i));PointF point=local(e.getX(i),e.getY(i));
            if(navigationHit(point)){
                boolean drag=navigation.joystick&&Math.hypot(point.x-navigation.cx,point.y-navigation.cy)<navigation.deadzone;
                navigationTouches.put(id,new NavigationTouch(point,drag));updateNavigation(id,point);
            }else if(index>=0)pointer(id,index);
            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
        }
        else if(action==MotionEvent.ACTION_MOVE){for(int j=0;j<e.getPointerCount();j++){
            int id=e.getPointerId(j);
            if(navigationTouches.get(id)!=null)updateNavigation(id,local(e.getX(j),e.getY(j)));
            else {int index=hit(e.getX(j),e.getY(j));if(index>=0)pointer(id,index);else pointer(id);}
        }}
        else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP){
            int id=e.getPointerId(i);pointer(id);navigationPointer(id);
            NavigationTouch touch=navigationTouches.get(id);
            if(touch!=null&&touch.drag&&!touch.moved&&navigation.confirm){emit(167,true);emit(167,false);}
            navigationTouches.remove(id);
            if(action==MotionEvent.ACTION_UP)performClick();
        }
        invalidate();return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    public void releaseKeys(){while(pointers.size()>0)pointer(pointers.keyAt(0));while(navigationPointers.size()>0)navigationPointer(navigationPointers.keyAt(0));navigationTouches.clear();invalidate();}
    @Override protected void onDetachedFromWindow(){releaseKeys();super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility!=VISIBLE)releaseKeys();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(!focused)releaseKeys();}
}
