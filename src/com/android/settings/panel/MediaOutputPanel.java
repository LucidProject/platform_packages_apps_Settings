/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.settings.panel;

import static com.android.settings.media.MediaOutputSlice.MEDIA_PACKAGE_NAME;
import static com.android.settings.slices.CustomSliceRegistry.MEDIA_OUTPUT_SLICE_URI;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.graphics.drawable.IconCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;

import com.android.internal.annotations.VisibleForTesting;

import com.android.settings.R;
import com.android.settings.slices.CustomSliceRegistry;

import com.android.settingslib.Utils;
import com.android.settingslib.media.InfoMediaDevice;
import com.android.settingslib.media.LocalMediaManager;
import com.android.settingslib.media.MediaDevice;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Represents the Media output Panel.
 *
 * <p>
 * Displays Media output item
 * </p>
 */
public class MediaOutputPanel implements PanelContent, LocalMediaManager.DeviceCallback, LifecycleObserver {

    @VisibleForTesting
    LocalMediaManager mLocalMediaManager;

    private boolean mIsCustomizedButtonUsed = true;

    private PanelCustomizedButtonCallback mCallback;
    private MediaController mMediaController;
    private MediaSessionManager mMediaSessionManager;

    private final Context mContext;
    private final String mPackageName;

    public static MediaOutputPanel create(Context context, String packageName) {
        return new MediaOutputPanel(context, packageName);
    }

    private MediaOutputPanel(Context context, String packageName) {
        mContext = context.getApplicationContext();
        mPackageName = TextUtils.isEmpty(packageName) ? "" : packageName;
        if (!TextUtils.isEmpty(packageName)) {
            mMediaSessionManager = mContext.getSystemService(MediaSessionManager.class);
            Iterator<MediaController> mediaIterator = mmediaSessionManager.getActiveSessions(null).iterator();
            while (true) {
                if (!mediaIterator.hasNext()) {
                    break;
                }
                MediaController next = mediaIterator.next();
                if (TextUtils.equals(next.getPackageName(), mPackageName)) {
                    mMediaController = next;
                    break;
                }
            }
        }
        if (mMediaController == null) {
            Log.e("MediaOutputPanel", "Unable to find " + mPackageName + " media controller");
        }
    }

    @Override
    public CharSequence getTitle() {
        MediaMetadata metadata;
        if (mMediaController == null || (metadata = mMediaController.getMetadata()) == null) {
            return mContext.getText(R.string.media_volume_title);
        }
        return metadata.getString("android.media.metadata.ARTIST");
    }

    @Override
    public CharSequence getSubTitle() {
        MediaMetadata metadata;
        if (mMediaController == null || (metadata = mMediaController.getMetadata()) == null) {
            return mContext.getText(R.string.media_output_panel_title);
        }
        return metadata.getString("android.media.metadata.ALBUM");
    }

    @Override
    public IconCompat getIcon() {
        Bitmap iconBitmap;
        if (mMediaController == null) {
            IconCompat withResource = IconCompat.createWithResource(mContext, R.drawable.ic_media_stream);
            withResource.setTint(Utils.getColorAccentDefaultColor(mContext));
            return withResource;
        }
        MediaMetadata metadata = mMediaController.getMetadata();
        if (metadata != null && (iconBitmap = metadata.getDescription().getIconBitmap()) != null) {
            return IconCompat.createWithBitmap(iconBitmap);
        }
        Log.d("MediaOutputPanel", "Media meta data does not contain icon information");
        return getPackageIcon();
    }

    @Override
    public List<Uri> getSlices() {
        final List<Uri> uris = new ArrayList<>();
        MEDIA_OUTPUT_SLICE_URI =
                MEDIA_OUTPUT_SLICE_URI
                        .buildUpon()
                        .clearQuery()
                        .appendQueryParameter(MEDIA_PACKAGE_NAME, mPackageName)
                        .build();
        uris.add(MEDIA_OUTPUT_SLICE_URI);
        return uris;
    }

    @Override
    public Intent getSeeMoreIntent() {
        return null;
    }

    @Override
    public void onClickCustomizedButton() {
    }

    @Override
    public boolean isCustomizedButtonUsed() {
        return mIsCustomizedButtonUsed;
    }

    @Override
    public CharSequence getCustomButtonTitle() {
        return mContext.getText(R.string.media_output_panel_stop_casting_button);
    }

    @Override
    public void registerCallback(PanelCustomizedButtonCallback panelCustomizedButtonCallback) {
        mCallback = panelCustomizedButtonCallback;
    }

    @Override
    public void onSelectedDeviceStateChanged(MediaDevice mediaDevice, int i) {
        dispatchCustomButtonStateChanged();
    }

    @Override
    public void onDeviceListUpdate(List<MediaDevice> list) {
        dispatchCustomButtonStateChanged();
    }

    @Override
    public void onDeviceAttributesChanged() {
        dispatchCustomButtonStateChanged();
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.PANEL_MEDIA_OUTPUT;
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    public void onStart() {
        if (mLocalMediaManager == null) {
            mLocalMediaManager = new LocalMediaManager(mContext, mPackageName, null);
        }
        mLocalMediaManager.registerCallback(this);
        mLocalMediaManager.startScan();
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    public void onStop() {
        mLocalMediaManager.unregisterCallback(this);
        mLocalMediaManager.stopScan();
    }

    private IconCompat getPackageIcon() {
        try {
            Drawable applicationIcon = mContext.getPackageManager().getApplicationIcon(mPackageName);
            if (applicationIcon instanceof BitmapDrawable) {
                return IconCompat.createWithBitmap(((BitmapDrawable) applicationIcon).getBitmap());
            }
            Bitmap createBitmap = Bitmap.createBitmap(
                        applicationIcon.getIntrinsicWidth(),
                        applicationIcon.getIntrinsicHeight(),
                        Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(createBitmap);
            applicationIcon.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            applicationIcon.draw(canvas);
            return IconCompat.createWithBitmap(createBitmap);
        } catch (PackageManager.NameNotFoundException unused) {
            Log.e("MediaOutputPanel", "Package is not found. Unable to get package icon.");
            return null;
        }
    }

    private void dispatchCustomButtonStateChanged() {
        hideCustomButtonIfNecessary();
        if (mCallback != null) {
            mCallback.onCustomizedButtonStateChanged();
        }
    }

    private void hideCustomButtonIfNecessary() {
        mIsCustomizedButtonUsed = mLocalMediaManager.getCurrentConnectedDevice() instanceof InfoMediaDevice;
    }
}
