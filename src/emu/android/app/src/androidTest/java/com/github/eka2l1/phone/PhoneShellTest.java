package com.github.eka2l1.phone;

import android.content.Context;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.github.eka2l1.emu.Keycode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.*;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

/** Runs on Android to exercise actual view transforms and pointer state. */
@RunWith(AndroidJUnit4.class)
public class PhoneShellTest {
    private Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private void ui(Runnable action){InstrumentationRegistry.getInstrumentation().runOnMainSync(action);}
    private void layout(PhoneShellLayout shell,int w,int h){
        shell.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));shell.layout(0,0,w,h);
    }
    @Test public void familyOrientationAndLcdContainment(){ui(()->{
        for(PhoneModel family:new PhoneModel[]{PhoneModel.N73,PhoneModel.QD,PhoneModel.N8}){
            PhoneShellLayout shell=new PhoneShellLayout(context());View child=new View(context());shell.addView(child);shell.setScaledUi(true);shell.setModel(family);
            for(int[] size:new int[][]{{1080,2400},{2400,1080}}){
                if(family==PhoneModel.QD&&size[0]<size[1])continue;
                layout(shell,size[0],size[1]);PhoneModel m=family.oriented(size[0]>size[1]);assertEquals(m,shell.getModel());
                Rect lcd=shell.getScreenBoundsOnScreen();assertTrue(lcd.left>=0&&lcd.top>=0&&lcd.right<=size[0]&&lcd.bottom<=size[1]);
                assertEquals(lcd.width(),Math.round(child.getMeasuredWidth()*child.getScaleX()),2);
                assertEquals(lcd.height(),Math.round(child.getMeasuredHeight()*child.getScaleY()),2);
            }
        }
        assertEquals(PhoneModel.N73,PhoneModel.N86_MEDIA.oriented(false));
        assertEquals(PhoneModel.N86_MEDIA,PhoneModel.N73.oriented(true));
        assertEquals(PhoneModel.N8_LANDSCAPE,PhoneModel.N8.oriented(true));
    });}
    private JSONArray keys(String asset)throws Exception{
        StringBuilder s=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(context().getAssets().open("phone/"+asset+".json")))){String line;while((line=r.readLine())!=null)s.append(line);}return new JSONArray(s.toString());
    }
    @Test public void navigationAssembliesStayOutOfOrdinaryKeyMaps()throws Exception{
        for(String model:new String[]{"n73","qd"}){
            JSONArray map=keys(model);
            for(int i=0;i<map.length();i++){
                int code=map.getJSONObject(i).getInt("code");
                assertFalse(model+" navigation direction must not be a separate keycap",code>=Keycode.KEY_LEFT&&code<=Keycode.KEY_DOWN);
            }
        }
        JSONArray n8=keys("n8");
        assertEquals("N8 reference has one physical menu key",1,n8.length());
        assertEquals(Keycode.KEY_MENU,n8.getJSONObject(0).getInt("code"));
    }
    @Test public void annotatedOutlinesStayTightToTheReferenceCaps()throws Exception{
        JSONArray n8=keys("n8");
        JSONArray n8b=n8.getJSONObject(0).getJSONArray("hit_bounds");
        assertEquals(62.3,n8b.getDouble(0),1.0);
        assertEquals(807.3,n8b.getDouble(1),1.0);
        assertEquals(137.3,n8b.getDouble(2),1.0);
        assertEquals(827.3,n8b.getDouble(3),1.0);

        JSONArray n73=keys("n73");
        boolean hasN73CentreKey=false;
        for(int i=0;i<n73.length();i++){
            JSONObject k=n73.getJSONObject(i);
            if(k.getInt("code")==Keycode.KEY_FIRE){
                JSONArray b=k.getJSONArray("hit_bounds");
                assertEquals("N73 centre key is beside 1/4",39.3,b.getDouble(0),1.0);
                assertEquals(707.0,b.getDouble(1),1.0);
                hasN73CentreKey=true;
            }
        }
        assertTrue("N73 has a separate centre key beside 1/4",hasN73CentreKey);
        for(int i=0;i<n73.length();i++){
            JSONObject k=n73.getJSONObject(i);int code=k.getInt("code");
            if(code==Keycode.KEY_SOFT_LEFT||code==Keycode.KEY_SOFT_RIGHT||
                    code==Keycode.KEY_CALL||code==Keycode.KEY_END){
                JSONArray b=k.getJSONArray("hit_bounds");
                if(b.getDouble(1)>600&&b.getDouble(1)<700){
                    assertEquals("four marked N73 caps have the same width",76.0,b.getDouble(2)-b.getDouble(0),1.5);
                    assertEquals("four marked N73 caps have the same height",34.7,b.getDouble(3)-b.getDouble(1),1.5);
                }
            }
        }
        StringBuilder data=new StringBuilder();
        try(BufferedReader r=new BufferedReader(new InputStreamReader(context().getAssets().open("phone/n73-nav.json")))){String line;while((line=r.readLine())!=null)data.append(line);}
        JSONObject nav=new JSONObject(data.toString());JSONArray hb=nav.getJSONArray("hit_bounds");
        assertEquals(38.9,hb.getDouble(2)-hb.getDouble(0),1.5);
        assertEquals(41.4,hb.getDouble(3)-hb.getDouble(1),1.5);
    }
    private MotionEvent event(int action,float...xy){
        int n=xy.length/2;MotionEvent.PointerProperties[] props=new MotionEvent.PointerProperties[n];MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[n];
        for(int i=0;i<n;i++){props[i]=new MotionEvent.PointerProperties();props[i].id=i;props[i].toolType=MotionEvent.TOOL_TYPE_FINGER;coords[i]=new MotionEvent.PointerCoords();coords[i].x=xy[i*2];coords[i].y=xy[i*2+1];coords[i].pressure=1;coords[i].size=1;}
        long now=SystemClock.uptimeMillis();return MotionEvent.obtain(now,now,action,n,props,coords,0,0,1,1,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0);
    }
    private void send(PhoneShellLayout shell,int action,float...xy){MotionEvent e=event(action,xy);shell.onTouchEvent(e);e.recycle();}
    @Test public void everyReferenceKeyPressesAndReleases()throws Exception{
        for(PhoneModel model:new PhoneModel[]{PhoneModel.N73,PhoneModel.QD,PhoneModel.N86_MEDIA,PhoneModel.N8,PhoneModel.N8_LANDSCAPE}){
            JSONArray source=keys(model.asset);ui(()->{
                PhoneShellLayout shell=new PhoneShellLayout(context());shell.setModel(model);layout(shell,(int)model.width,(int)model.height);
                List<String> events=new ArrayList<>();shell.setKeyListener((code,down)->events.add(code+":"+down));
                try{for(int i=0;i<source.length();i++){JSONObject key=source.getJSONObject(i);JSONArray a=key.getJSONArray("bounds");float x=(float)(a.getDouble(0)+a.getDouble(2))/2,y=(float)(a.getDouble(1)+a.getDouble(3))/2;
                    if(model==PhoneModel.N8_LANDSCAPE){float oldX=x;x=850-y;y=oldX;}
                    events.clear();send(shell,MotionEvent.ACTION_DOWN,x,y);send(shell,MotionEvent.ACTION_UP,x,y);
                    assertEquals(model+" key "+i,Arrays.asList(key.getInt("code")+":true",key.getInt("code")+":false"),events);
                }}catch(JSONException e){throw new AssertionError(e);}
            });
        }
    }
    @Test public void multitouchDragCancelAndRotationRelease(){ui(()->{
        PhoneShellLayout shell=new PhoneShellLayout(context());shell.setModel(PhoneModel.N73);layout(shell,430,932);
        List<String> events=new ArrayList<>();shell.setKeyListener((code,down)->events.add(code+":"+down));
        send(shell,MotionEvent.ACTION_DOWN,100,742);
        send(shell,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),100,742,199,742);
        send(shell,MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),100,742,199,742);
        send(shell,MotionEvent.ACTION_UP,100,742);
        assertEquals(Arrays.asList("49:true","50:true","50:false","49:false"),events);
        events.clear();send(shell,MotionEvent.ACTION_DOWN,100,742);send(shell,MotionEvent.ACTION_MOVE,199,742);send(shell,MotionEvent.ACTION_CANCEL,199,742);
        assertEquals(Arrays.asList("49:true","49:false","50:true","50:false"),events);
        events.clear();send(shell,MotionEvent.ACTION_DOWN,100,742);layout(shell,1100,500);assertEquals(Arrays.asList("49:true","49:false"),events);
        events.clear();shell.setModel(PhoneModel.N73);layout(shell,430,932);send(shell,MotionEvent.ACTION_DOWN,100,742);shell.onWindowFocusChanged(false);assertEquals(Arrays.asList("49:true","49:false"),events);
    });}
    @Test public void joystickDirectionsAndQdCenterSelect(){ui(()->{
        PhoneShellLayout shell=new PhoneShellLayout(context());shell.setModel(PhoneModel.N73);layout(shell,430,932);
        List<String> events=new ArrayList<>();shell.setKeyListener((code,down)->events.add(code+":"+down));
        send(shell,MotionEvent.ACTION_DOWN,204,662);assertTrue(events.isEmpty());
        send(shell,MotionEvent.ACTION_MOVE,204,640);
        send(shell,MotionEvent.ACTION_MOVE,228,662);
        send(shell,MotionEvent.ACTION_UP,228,662);
        assertEquals(Arrays.asList("16:true","16:false","15:true","15:false"),events);
        events.clear();send(shell,MotionEvent.ACTION_DOWN,204,662);send(shell,MotionEvent.ACTION_UP,204,662);
        assertTrue("N73 joystick is directional only",events.isEmpty());
        send(shell,MotionEvent.ACTION_DOWN,204,662);send(shell,MotionEvent.ACTION_MOVE,207,663);send(shell,MotionEvent.ACTION_UP,207,663);
        assertTrue("Small joystick movement stays inside its deadzone",events.isEmpty());
        // A modest sideways drift must not turn a deliberate vertical push
        // into a diagonal. Diagonals are reserved for movements engaging both
        // axes at nearly the same time.
        events.clear();send(shell,MotionEvent.ACTION_DOWN,204,662);send(shell,MotionEvent.ACTION_MOVE,214,640);send(shell,MotionEvent.ACTION_UP,214,640);
        assertEquals(Arrays.asList("16:true","16:false"),events);
        events.clear();send(shell,MotionEvent.ACTION_DOWN,204,662);send(shell,MotionEvent.ACTION_MOVE,226,652);send(shell,MotionEvent.ACTION_UP,226,652);
        assertEquals(Arrays.asList("15:true","15:false"),events);
        events.clear();send(shell,MotionEvent.ACTION_DOWN,204,662);send(shell,MotionEvent.ACTION_MOVE,220,678);send(shell,MotionEvent.ACTION_UP,220,678);
        assertEquals(Arrays.asList("15:true","17:true","15:false","17:false"),events);
        events.clear();send(shell,MotionEvent.ACTION_DOWN,204,662);send(shell,MotionEvent.ACTION_CANCEL,204,662);assertTrue(events.isEmpty());
        shell.setModel(PhoneModel.QD);layout(shell,1000,580);events.clear();
        send(shell,MotionEvent.ACTION_DOWN,199,212);send(shell,MotionEvent.ACTION_UP,199,212);assertTrue("QD cross centre is not confirm",events.isEmpty());
        send(shell,MotionEvent.ACTION_DOWN,277,310);send(shell,MotionEvent.ACTION_UP,277,310);
        assertEquals(Arrays.asList("167:true","167:false"),events);
    });}
    @Test public void qdArrowMarkedSoftKeysUseBothLeftCapsAndRightBack(){ui(()->{
        PhoneShellLayout shell=new PhoneShellLayout(context());shell.setModel(PhoneModel.QD);layout(shell,1000,579);
        List<String> events=new ArrayList<>();shell.setKeyListener((code,down)->events.add(code+":"+down));
        // Coordinates are in the original colored-outline reference, not the
        // arrow tips. Exercise each physical cap at several interior points.
        int[][] controls={
            {495,875,Keycode.KEY_SOFT_LEFT},{455,855,Keycode.KEY_SOFT_LEFT},{520,850,Keycode.KEY_SOFT_LEFT},
            {695,1210,Keycode.KEY_SOFT_LEFT},{665,1190,Keycode.KEY_SOFT_LEFT},{720,1220,Keycode.KEY_SOFT_LEFT},
            {2090,1210,Keycode.KEY_SOFT_RIGHT},{2020,1200,Keycode.KEY_SOFT_RIGHT},{2160,1230,Keycode.KEY_SOFT_RIGHT}
        };
        for(int[] control:controls){
            events.clear();float x=control[0]*1000f/2656,y=control[1]*579f/1536;
            send(shell,MotionEvent.ACTION_DOWN,x,y);send(shell,MotionEvent.ACTION_UP,x,y);
            assertEquals("QD arrow cap at "+control[0]+","+control[1],
                Arrays.asList(control[2]+":true",control[2]+":false"),events);
        }
    });}
    @Test public void pressedDirectionTiltsWholePhysicalPad(){ui(()->{
        PhoneShellLayout shell=new PhoneShellLayout(context());shell.setModel(PhoneModel.QD);layout(shell,1000,580);
        android.graphics.Bitmap before=android.graphics.Bitmap.createBitmap(1000,580,android.graphics.Bitmap.Config.ARGB_8888);shell.draw(new android.graphics.Canvas(before));
        send(shell,MotionEvent.ACTION_DOWN,199,167);
        android.graphics.Bitmap after=android.graphics.Bitmap.createBitmap(1000,580,android.graphics.Bitmap.Config.ARGB_8888);shell.draw(new android.graphics.Canvas(after));
        int changed=0,lowerChanged=0;for(int y=0;y<580;y++)for(int x=0;x<1000;x++)if(before.getPixel(x,y)!=after.getPixel(x,y)){
            changed++;if(y>220)lowerChanged++;assertTrue("Tilt must stay inside physical navigation assembly at "+x+","+y,x>=109&&x<=269&&y>=129&&y<=299);
        }
        assertTrue("Sculpted pad must show tilt",changed>100);
        assertTrue("Opposite lobe must tilt with the same solid pad",lowerChanged>100);
        assertEquals("No rectangular corner overlay",before.getPixel(110,130),after.getPixel(110,130));
        send(shell,MotionEvent.ACTION_UP,199,167);
        android.graphics.Bitmap released=android.graphics.Bitmap.createBitmap(1000,580,android.graphics.Bitmap.Config.ARGB_8888);shell.draw(new android.graphics.Canvas(released));
        assertTrue(before.sameAs(released));before.recycle();after.recycle();released.recycle();
    });}
    @Test public void eightDirectionsAndSharedHoldsReleaseTogether(){ui(()->{
        PhoneShellLayout shell=new PhoneShellLayout(context());shell.setModel(PhoneModel.N73);layout(shell,430,932);
        Set<Integer> held=new HashSet<>();List<String> events=new ArrayList<>();
        shell.setKeyListener((code,down)->{events.add(code+":"+down);if(down)assertTrue(held.add(code));else assertTrue(held.remove(code));});
        int[][] vectors={{0,-25},{25,-25},{25,0},{25,25},{0,25},{-25,25},{-25,0},{-25,-25}};
        int[][] expected={{16},{15,16},{15},{15,17},{17},{14,17},{14},{14,16}};
        for(int i=0;i<vectors.length;i++){
            events.clear();send(shell,MotionEvent.ACTION_DOWN,204,662);
            send(shell,MotionEvent.ACTION_MOVE,204+vectors[i][0],662+vectors[i][1]);
            Set<Integer> wanted=new HashSet<>();for(int code:expected[i])wanted.add(code);assertEquals(wanted,held);
            send(shell,MotionEvent.ACTION_CANCEL,215+vectors[i][0],669+vectors[i][1]);assertTrue(held.isEmpty());
            assertFalse("Dragging must never click confirm",events.contains("167:true"));
        }
        events.clear();shell.setModel(PhoneModel.QD);layout(shell,1000,580);
        send(shell,MotionEvent.ACTION_DOWN,199,168);
        send(shell,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),199,168,220,203);
        assertEquals(new HashSet<>(Arrays.asList(16,15)),held);
        send(shell,MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),199,168,220,203);
        assertEquals(Collections.singleton(16),held);
        send(shell,MotionEvent.ACTION_UP,199,168);assertTrue(held.isEmpty());
        assertEquals(1,Collections.frequency(events,"16:true"));assertEquals(1,Collections.frequency(events,"16:false"));
    });}
    @Test public void n86LandscapeKeysFollowThePhotographedPositionsAndSymbols(){ui(()->{
        PhoneShellLayout shell=new PhoneShellLayout(context());shell.setModel(PhoneModel.N86_MEDIA);layout(shell,1060,490);
        List<String> events=new ArrayList<>();shell.setKeyListener((code,down)->events.add(code+":"+down));
        float scaleX=1060f/1844f,scaleY=490f/853f;
        int[][] controls={
            {130,190,Keycode.KEY_MENU},
            {260,106,Keycode.KEY_CALL},
            {414,190,Keycode.KEY_SOFT_LEFT},
            {260,735,Keycode.KEY_END},
            {102,648,Keycode.KEY_CLEAR},
            {414,648,Keycode.KEY_SOFT_RIGHT},
            {1730,210,Keycode.KEY_VOLUME_DOWN},
            {1730,350,Keycode.KEY_MEDIA_STOP},
            {1730,480,Keycode.KEY_MEDIA_PLAY},
            {1730,620,Keycode.KEY_VOLUME_UP},
        };
        for(int[] control:controls){
            events.clear();float x=control[0]*scaleX,y=control[1]*scaleY;
            send(shell,MotionEvent.ACTION_DOWN,x,y);send(shell,MotionEvent.ACTION_UP,x,y);
            assertEquals("N86 control at reference point "+control[0]+","+control[1],
                Arrays.asList(control[2]+":true",control[2]+":false"),events);
        }
        events.clear();
        send(shell,MotionEvent.ACTION_DOWN,120*scaleX,280*scaleY);
        send(shell,MotionEvent.ACTION_UP,120*scaleX,280*scaleY);
        assertTrue("Silver surround outside the D-pad is not a key",events.isEmpty());
        events.clear();
        float cx=249*scaleX,cy=422*scaleY;
        send(shell,MotionEvent.ACTION_DOWN,cx,cy);
        send(shell,MotionEvent.ACTION_MOVE,cx+24,cy);
        send(shell,MotionEvent.ACTION_UP,cx+24,cy);
        assertEquals(Arrays.asList("15:true","15:false"),events);
    });}
    @Test public void everyHomePageIconKeepsItsOriginalOpacity(){ui(()->{
        android.graphics.Bitmap icon=android.graphics.Bitmap.createBitmap(16,16,android.graphics.Bitmap.Config.ARGB_8888);icon.eraseColor(0xffff0000);
        com.github.eka2l1.applist.AppsListAdapter adapter=new com.github.eka2l1.applist.AppsListAdapter(context());
        List<com.github.eka2l1.applist.AppItem> apps=new ArrayList<>();
        for(int i=0;i<12;i++)apps.add(new com.github.eka2l1.applist.AppItem(i,0,String.format(Locale.ROOT,"%02d",i),icon));
        adapter.setItems(apps);PhoneHomeScreen home=new PhoneHomeScreen(context());home.setAdapter(adapter);home.setDevice(PhoneModel.N73,"Test",true);
        home.measure(View.MeasureSpec.makeMeasureSpec(334,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(486,View.MeasureSpec.EXACTLY));home.layout(0,0,334,486);
        for(int key:new int[]{0,15,165,15}){
            home.key(key,true);android.graphics.Bitmap rendered=android.graphics.Bitmap.createBitmap(334,486,android.graphics.Bitmap.Config.ARGB_8888);home.draw(new android.graphics.Canvas(rendered));
            for(int col=0;col<3;col++)assertEquals("Page's icons retain opaque source colour, including its unselected first icon",0xffff0000,rendered.getPixel(Math.round((col+.5f)*334/3),71));
            rendered.recycle();
        }
        icon.recycle();home.setAdapter(null);
    });}
}
