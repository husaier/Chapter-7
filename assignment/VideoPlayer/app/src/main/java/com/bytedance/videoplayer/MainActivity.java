package com.bytedance.videoplayer;

import android.content.res.Configuration;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.VideoView;

import tv.danmaku.ijk.media.player.IMediaPlayer;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "husserl";
    private static final int UPDATE_VIDEO_PROGRESS = 0;
    private static final String LIFECYCLE_CALLBACKS_TEXT_KEY = "callbacks";

    private VideoView mVideoView;
    private TextView tv_videoTime;
    private SeekBar sk_video;
    private static Handler mHandler;
    private updateThread videoUpdater;
    private static Integer lastPos;
    private Bundle savedIS;

    private Uri uri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        savedIS = savedInstanceState;

        if(this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            //横屏
            initialLandscape();
            Log.d(TAG, "横屏");
        }
        else {
            //竖屏
            initialPortrai();
            Log.d(TAG, "竖屏");
        }

        tv_videoTime = findViewById(R.id.time);
        tv_videoTime.setText("0:0/0:0");

        sk_video = findViewById(R.id.video_progress);
        sk_video.setProgress(0);
        sk_video.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private boolean shouldPlaying;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if(fromUser) {
                    float proportion = (float)progress / 100;
                    float length = (float)mVideoView.getDuration();
                    int cur = (int)(length * proportion);
                    setTime(cur);
                    //Log.d(TAG, "" + length);
                    mVideoView.seekTo(cur);

                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if(mVideoView.isPlaying()) {
                    shouldPlaying = true;
                    mVideoView.pause();
                }
                else
                    shouldPlaying = false;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if(shouldPlaying)
                    mVideoView.start();
            }
        });

        mHandler = new Handler(){
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case UPDATE_VIDEO_PROGRESS:
                        int mCur = mVideoView.getCurrentPosition();
                        setTime(mCur);
                }
            }
        };

        videoUpdater = new updateThread();
        videoUpdater.start();

        mVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener(){

            @Override
            public void onPrepared(MediaPlayer mp) {
                Log.d(TAG, "video prepared");
                if (savedIS != null) {//恢复日志
                    Log.d(TAG, "true lastPos:" + lastPos);
                    mVideoView.seekTo((int)lastPos);
                    setTime((int)lastPos);
                    //seekTo是异步实现的，所以切换的时候会有seek还没有完成而播放已经开始的现象
                    //其实seekTo跳转的位置其实并不是参数所带的position，而是离position最近的关键帧
                    //可以利用seekComplete
//                    mp.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
//                        @Override
//                        public void onSeekComplete(MediaPlayer mp) {
//
//                        }
//                    });
                }
                else {//如果是第一次调用onCreate，lastPos需要初始化
                    Log.d(TAG, "false lastPos:" + lastPos);
                    lastPos = 1;
                    mVideoView.seekTo((int)lastPos);
                    setTime((int)lastPos);
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        videoUpdater.interrupt();  //退出子线程
        super.onDestroy();
    }

    private void initialVideoView() {
        uri = getIntent().getData();
        setContentView(R.layout.activity_main);
        mVideoView = findViewById(R.id.video_view);
        if(uri == null)
            mVideoView.setVideoPath(getVideoPath(R.raw.dancing));
        else
            mVideoView.setVideoURI(uri);
    }

    private void initialLandscape() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        getSupportActionBar().hide();

        initialVideoView();

        mVideoView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mVideoView.isPlaying())
                    mVideoView.pause();
                else
                    mVideoView.start();
            }
        });
    }

    private void initialPortrai() {
        initialVideoView();

        Button btn_paly = findViewById(R.id.btn_play);
        btn_paly.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setTime(mVideoView.getCurrentPosition());
                if(mVideoView.isPlaying())
                    mVideoView.pause();
                else
                    mVideoView.start();
            }
        });
    }
    
    private void setTime(int cur) {
        int length = mVideoView.getDuration();
        if(length < 0)
            return;
        float tmp = (float)cur / (float)length * (float) 100;
        sk_video.setProgress((int)tmp);
        Log.d(TAG, "video duration:" + length);
        Log.d(TAG, "current position:" + cur + " seek bar progress:" +  tmp);

        if(cur % 1000 >= 500)
            cur = cur / 1000 + 1;
        else
            cur = cur / 1000;

        if(length % 1000 >= 500)
            length = length / 1000 + 1;
        else
            length = length / 1000;
        int length_min = length / 60;
        int length_sec = length % 60;
        int cur_min = cur / 60;
        int cur_sec = cur % 60;
        String time = cur_min + ":" + cur_sec + "/" + length_min + ":" + length_sec;
        //Log.d(TAG, "length" + length);
        tv_videoTime.setText(time);

        lastPos = mVideoView.getCurrentPosition();
    }

    private String getVideoPath(int resId) {
        return "android.resource://" + this.getPackageName() + "/" + resId;
    }

    private class updateThread extends Thread {
        @Override
        public void run() {
            super.run();
            while(!isInterrupted()) {
                try{
                    Thread.sleep(500);
                    if(mVideoView.isPlaying()) {
                        mHandler.sendEmptyMessage(UPDATE_VIDEO_PROGRESS);
                    }
                }
                catch (InterruptedException e) {
                    Log.d(TAG, "updateThread is destroyed!");
                }
            }
        }
    }
}
