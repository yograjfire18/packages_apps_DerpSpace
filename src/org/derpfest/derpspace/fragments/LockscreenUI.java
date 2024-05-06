/*
 * Copyright (C) 2017 AospExtended ROM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.derpfest.derpspace.fragments;

import android.app.ActivityManagerNative;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.fingerprint.FingerprintManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.MediaStore;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.IWindowManager;
import android.view.View;
import android.view.WindowManagerGlobal;

import androidx.preference.ListPreference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;
import com.android.settings.search.BaseSearchIndexProvider;

import com.android.settingslib.search.Indexable;
import com.android.settingslib.search.SearchIndexable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SearchIndexable
public class LockscreenUI extends SettingsPreferenceFragment implements OnPreferenceChangeListener {

    private static final String FINGERPRINT_VIB = "fingerprint_success_vib";

    private FingerprintManager mFingerprintManager;
    private SwitchPreferenceCompat mFingerprintVib;

    private Preference mDepthWallpaperCustomImagePicker;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.lockscreen_ui);

        mDepthWallpaperCustomImagePicker = findPreference("depth_wallpaper_subject_image_uri");

        final ContentResolver resolver = getActivity().getContentResolver();
        final PreferenceScreen prefScreen = getPreferenceScreen();

        mFingerprintManager = (FingerprintManager) getActivity().getSystemService(Context.FINGERPRINT_SERVICE);
        mFingerprintVib = (SwitchPreferenceCompat) findPreference(FINGERPRINT_VIB);
        if (mFingerprintManager == null) {
            prefScreen.removePreference(mFingerprintVib);
        } else {
            mFingerprintVib.setChecked((Settings.System.getInt(getContentResolver(),
                Settings.System.FINGERPRINT_SUCCESS_VIB, 1) == 1));
            mFingerprintVib.setOnPreferenceChangeListener(this);
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.DERP;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference == mDepthWallpaperCustomImagePicker) {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            startActivityForResult(intent, 10001);
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent result) {
        if (requestCode == 10001) {
            if (resultCode != Activity.RESULT_OK) {
                return;
            }

            final Uri imgUri = result.getData();
            if (imgUri != null) {
                String savedImagePath = saveImageToInternalStorage(getContext(), imgUri);
                if (savedImagePath != null) {
                    ContentResolver resolver = getContext().getContentResolver();
                    Settings.System.putStringForUser(resolver, "depth_wallpaper_subject_image_uri", savedImagePath, UserHandle.USER_CURRENT);
                }
            }
        }
    }

    private String saveImageToInternalStorage(Context context, Uri imgUri) {
        try {
            InputStream inputStream;
            if (imgUri.toString().startsWith("content://com.google.android.apps.photos.contentprovider")) {
                List<String> segments = imgUri.getPathSegments();
                if (segments.size() > 2) {
                    String mediaUriString = URLDecoder.decode(segments.get(2), StandardCharsets.UTF_8.name());
                    Uri mediaUri = Uri.parse(mediaUriString);
                    inputStream = context.getContentResolver().openInputStream(mediaUri);
                } else {
                    throw new FileNotFoundException("Failed to parse Google Photos content URI");
                }
            } else {
                inputStream = context.getContentResolver().openInputStream(imgUri);
            }
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String imageFileName = "DEPTH_WALLPAPER_SUBJECT_" + timeStamp + ".png";
            File directory = new File("/sdcard/depthwallpaper");
            if (!directory.exists() && !directory.mkdirs()) {
                return null;
            }
            File[] files = directory.listFiles((dir, name) -> name.startsWith("DEPTH_WALLPAPER_SUBJECT_") && name.endsWith(".png"));
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
            File file = new File(directory, imageFileName);
            try (FileOutputStream outputStream = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            }
            return file.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        if (preference == mFingerprintVib) {
            boolean value = (Boolean) objValue;
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.FINGERPRINT_SUCCESS_VIB, value ? 1 : 0);
            return true;
        }
        return false;
    }

    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
        new BaseSearchIndexProvider() {
            @Override
            public List<SearchIndexableResource> getXmlResourcesToIndex(Context context,
                    boolean enabled) {
                final ArrayList<SearchIndexableResource> result = new ArrayList<>();
                final SearchIndexableResource sir = new SearchIndexableResource(context);
                sir.xmlResId = R.xml.lockscreen_ui;
                result.add(sir);
                return result;
            }

            @Override
            public List<String> getNonIndexableKeys(Context context) {
                final List<String> keys = super.getNonIndexableKeys(context);
                return keys;
            }
    };
}
