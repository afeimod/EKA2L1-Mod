package com.github.eka2l1.emu;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.PixelCopy;
import android.view.SurfaceView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.github.eka2l1.MainActivity;
import com.github.eka2l1.R;
import com.github.eka2l1.phone.PhoneShellLayout;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Requires an installed RM-409 device with Groove Labyrinth 2. Run on a clean process. */
@RunWith(AndroidJUnit4.class)
public class SessionRestartTest {
    private void ui(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
    }

    private boolean hasGameFrame(EmulatorActivity activity) throws Exception {
        AtomicReference<Bitmap> image=new AtomicReference<>();
        int[] result={PixelCopy.ERROR_SOURCE_NO_DATA};
        CountDownLatch done=new CountDownLatch(1);
        ui(()->{
            SurfaceView surface=activity.findViewById(R.id.surface_view);
            if(!surface.getHolder().getSurface().isValid()||surface.getWidth()==0){done.countDown();return;}
            Bitmap bitmap=Bitmap.createBitmap(surface.getWidth(),surface.getHeight(),Bitmap.Config.ARGB_8888);
            image.set(bitmap);
            PixelCopy.request(surface,bitmap,value->{result[0]=value;done.countDown();},new Handler(Looper.getMainLooper()));
        });
        assertTrue("Frame capture timed out",done.await(4,TimeUnit.SECONDS));
        Bitmap bitmap=image.get();
        if(bitmap==null)return false;
        boolean visible=false;
        if(result[0]==PixelCopy.SUCCESS){
            java.util.HashSet<Integer> colors=new java.util.HashSet<>();
            int samples=0,gold=0;
            for(int y=0;y<bitmap.getHeight();y+=9)for(int x=0;x<bitmap.getWidth();x+=9){
                int pixel=bitmap.getPixel(x,y);colors.add(pixel);samples++;
                int r=android.graphics.Color.red(pixel),g=android.graphics.Color.green(pixel),b=android.graphics.Color.blue(pixel);
                if(r>160&&g>110&&r-g>20&&g-b>30)gold++;
            }
            // Groove's orange/yellow title artwork must replace the red Symbian
            // boot wallpaper. A merely non-black Surface can still be booting.
            visible=colors.size()>24&&gold*100>samples*8;
        }
        bitmap.recycle();return visible;
    }

    @Test public void sameGameRestartsAfterThreeCompleteCloses() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        try(ActivityScenario<MainActivity> home=ActivityScenario.launch(MainActivity.class)){
            long initDeadline=SystemClock.uptimeMillis()+30000;
            while(!Emulator.isInitialized()&&SystemClock.uptimeMillis()<initDeadline)SystemClock.sleep(100);
            assertTrue("Installed device must boot",Emulator.isInitialized());
            assertFalse("Regression must exercise normal mode",Emulator.isDebugControlEnabled());
            String[] codes=Emulator.getDeviceFirmwareCodes();int device=-1;
            for(int i=0;i<codes.length;i++)if(codes[i].equalsIgnoreCase("RM-409"))device=i;
            assertTrue("Install the supplied RM-409 preset first",device>=0);
            Emulator.setCurrentDevice(device,true);
            assertTrue("Groove Labyrinth 2 must be installed",Emulator.getAppsList().blockingGet().stream()
                    .anyMatch(app->app.getUid()==0xA0006D8BL));
            for(int run=0;run<3;run++){
                Intent intent=new Intent(context,EmulatorActivity.class);
                intent.putExtra(Constants.KEY_APP_UID,0xA0006D8BL);
                intent.putExtra(Constants.KEY_APP_NAME,"Groove Labyrinth 2");
                AtomicReference<EmulatorActivity> current=new AtomicReference<>();
                try(ActivityScenario<EmulatorActivity> game=ActivityScenario.launch(intent)){
                    game.onActivity(current::set);
                    EmulatorActivity activity=current.get();
                    long frameDeadline=SystemClock.uptimeMillis()+25000;boolean visible=false;
                    while(SystemClock.uptimeMillis()<frameDeadline&&!visible){SystemClock.sleep(250);visible=hasGameFrame(activity);}
                    assertTrue("Game must render after launch "+run,visible);
                    Bitmap evidence=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
                    java.io.File folder=context.getExternalFilesDir("regression");
                    assertNotNull(folder);
                    try(java.io.FileOutputStream output=new java.io.FileOutputStream(new java.io.File(folder,"restart-"+run+".png"))){
                        assertTrue(evidence.compress(Bitmap.CompressFormat.PNG,100,output));
                    }
                    evidence.recycle();
                    game.onActivity(a->{
                        PhoneShellLayout shell=a.findViewById(R.id.emulator_container);
                        assertTrue(shell.isShown());
                        Emulator.exitInstance();
                        Emulator.exitInstance(); // Duplicate exit callback/confirmation must be harmless.
                    });
                    long closeDeadline=SystemClock.uptimeMillis()+25000;
                    while(!activity.isDestroyed()&&SystemClock.uptimeMillis()<closeDeadline)SystemClock.sleep(100);
                    assertTrue("Reset must finish before returning home "+run,activity.isDestroyed());
                }
                assertTrue("App registry must survive reset",Emulator.getAppsList().blockingGet().stream()
                        .anyMatch(app->app.getUid()==0xA0006D8BL));
            }
        }
    }
}
