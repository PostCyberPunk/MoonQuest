package com.limelight.preferences;

import android.annotation.TargetApi;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.preference.ListPreference;
import android.provider.Settings;
import android.util.AttributeSet;

public class LanguagePreference extends ListPreference {
    public LanguagePreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public LanguagePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public LanguagePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LanguagePreference(Context context) {
        super(context);
    }

    @Override
    protected void onClick() {
        // If we don't have native app locale settings, launch the normal dialog
        super.onClick();
    }
}
