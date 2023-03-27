package com.nova.audiolibrary;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.FloatRange;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.Toast;

import com.obsez.android.lib.filechooser.ChooserDialog;

import java.io.File;

import agency.tango.materialintroscreen.MaterialIntroActivity;
import agency.tango.materialintroscreen.MessageButtonBehaviour;
import agency.tango.materialintroscreen.SlideFragmentBuilder;
import agency.tango.materialintroscreen.animations.IViewTranslation;

public class IntroActivity extends MaterialIntroActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enableLastSlideAlphaExitTransition(true);

        getBackButtonTranslationWrapper()
                .setEnterTranslation(new IViewTranslation() {
                    @Override
                    public void translate(View view, @FloatRange(from = 0, to = 1.0) float percentage) {
                        view.setAlpha(percentage);
                    }
                });

        addSlide(new SlideFragmentBuilder()
                .backgroundColor(R.color.first_slide_background)
                .buttonsColor(R.color.first_slide_buttons)
                .image(R.drawable.audiobook_tracking_einfuehrung_1)
                .title("Organize your long audio files with us")
                .description("A simple way to manage your audiobooks and lectures on android")
                .build());

        addSlide(new SlideFragmentBuilder()
                .backgroundColor(R.color.second_slide_background)
                .buttonsColor(R.color.second_slide_buttons)
                .image(R.drawable.audiobook_tracking_einfuehrung_2)
                .title("Keep track of your progress")
                .build());

        addSlide(new SlideFragmentBuilder()
                .backgroundColor(R.color.third_slide_background)
                .buttonsColor(R.color.third_slide_buttons)
                .neededPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE})
                .title("Allow us to read your Audio Files")
                .build());

        addSlide(new SlideFragmentBuilder()
                .backgroundColor(R.color.third_slide_background)
                .buttonsColor(R.color.third_slide_buttons)
                .neededPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE})
                .title("Allow us to edit your Audio Files")
                .build());

        addSlide(new SlideFragmentBuilder()
                .backgroundColor(R.color.third_slide_background)
                .buttonsColor(R.color.third_slide_buttons)
                .neededPermissions(new String[]{Manifest.permission.READ_PHONE_STATE})
                .title("Allow us to stop the audio when a phone call comes")
                .build());

        addSlide(new SlideFragmentBuilder()
                        .backgroundColor(R.color.fourth_slide_background)
                        .buttonsColor(R.color.fourth_slide_buttons)
                        .image(R.drawable.folder_symbol)
                        .title("Select a folder")
                        .description("now choose the location of your audio files")
                        .build(),
                new MessageButtonBehaviour(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        new ChooserDialog()
                                .with(IntroActivity.this)
                                .withFilter(true, false)
                                .withStartFile(Environment.getExternalStorageDirectory().getAbsolutePath())
                                // to handle the result(s)
                                .withChosenListener(new ChooserDialog.Result() {
                                    @Override
                                    public void onChoosePath(String path, File pathFile) {

                                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

                                        prefs.edit().putString(MainActivity.FILE_DIRECTION_KEY, path).commit();

                                    }
                                })
                                .build()
                                .show();
                    }
                }, "choose"));
    }

    @Override
    public void onFinish() {
        super.onFinish();
        Intent intent = new Intent(IntroActivity.this, MainActivity.class);
        startActivity(intent);
    }
}
