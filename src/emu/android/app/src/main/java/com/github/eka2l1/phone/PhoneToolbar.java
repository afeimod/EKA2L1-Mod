package com.github.eka2l1.phone;
import android.content.Context;
import android.content.ContextWrapper;
import android.util.AttributeSet;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
public class PhoneToolbar extends Toolbar {
    public PhoneToolbar(Context c){super(c);}
    public PhoneToolbar(Context c,AttributeSet a){super(c,a);}
    @Override public boolean showOverflowMenu(){Context c=getContext();while(c instanceof ContextWrapper){if(c instanceof AppCompatActivity){PhoneUi.showOptions((AppCompatActivity)c);return true;}c=((ContextWrapper)c).getBaseContext();}return false;}
}
