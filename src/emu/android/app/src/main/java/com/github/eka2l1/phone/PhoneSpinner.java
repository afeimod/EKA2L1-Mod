package com.github.eka2l1.phone;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.Spinner;
public class PhoneSpinner extends androidx.appcompat.widget.AppCompatSpinner {
    public PhoneSpinner(Context c,AttributeSet a){super(c,a);}
    @Override public boolean performClick(){
        if(getAdapter()==null)return false;
        String[] labels=new String[getAdapter().getCount()];for(int i=0;i<labels.length;i++)labels[i]=String.valueOf(getAdapter().getItem(i));
        new PhoneDialogs.SupportBuilder(getContext()).setTitle(getPrompt()).setSingleChoiceItems(labels,getSelectedItemPosition(),(d,i)->{setSelection(i);d.dismiss();}).setNegativeButton("返回",null).show();return true;
    }
}
