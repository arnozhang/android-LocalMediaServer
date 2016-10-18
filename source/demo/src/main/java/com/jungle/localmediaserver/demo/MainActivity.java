package com.jungle.localmediaserver.demo;

import android.Manifest;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import com.jungle.localmediaserver.LocalMediaServer;

import java.io.IOException;

public class MainActivity extends AppCompatActivity
        implements TextureView.SurfaceTextureListener {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_PERMISSION = 1000;


    private LocalMediaServer mMediaServer;
    private MediaPlayer mMediaPlayer = new MediaPlayer();
    private TextureView mTextureView;
    private boolean mIsPrepared = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initMediaPlayer();

        mTextureView = (TextureView) findViewById(R.id.texture_view);
        mTextureView.setSurfaceTextureListener(this);
        findViewById(R.id.play_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                    String[] permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
                    ActivityCompat.requestPermissions(MainActivity.this, permissions, REQUEST_PERMISSION);
                } else {
                    play();
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION) {
            play();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mMediaPlayer.stop();
        mMediaPlayer.reset();
        mMediaPlayer.release();

        mMediaServer.stop();
    }

    private void initMediaPlayer() {
        mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mMediaPlayer.setOnErrorListener(mOnErrorListener);
        mMediaPlayer.setOnPreparedListener(mOnPreparedListener);
        mMediaPlayer.setOnCompletionListener(mOnCompletionListener);
        mMediaPlayer.setOnVideoSizeChangedListener(mOnVideoSizeChangedListener);
    }

    private void play() {
        mIsPrepared = false;
        if (mMediaServer != null) {
            mMediaServer.stop();
            mMediaPlayer.reset();
        }

        String mediaPath = Environment.getExternalStorageDirectory().getPath() + "/media.mp4";
        mMediaServer = new LocalMediaServer();
        String url = mMediaServer.prepare(mediaPath);
        if (TextUtils.isEmpty(url)) {
            String msg = String.format("Cannot resolve Media-Url ! ensure file \"%s\" exists!", mediaPath);
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            return;
        }

        mMediaServer.start();

        try {
            mMediaPlayer.setDataSource(this, Uri.parse(url));
            mMediaPlayer.prepareAsync();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void resume() {
        if (mIsPrepared && !mMediaPlayer.isPlaying()) {
            mMediaPlayer.start();
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        if (mIsPrepared) {
            mMediaPlayer.setSurface(new Surface(surface));
        }

        resume();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        mMediaPlayer.pause();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    private MediaPlayer.OnPreparedListener mOnPreparedListener = new MediaPlayer.OnPreparedListener() {
        public void onPrepared(MediaPlayer player) {
            Log.e(TAG, "**SUCCESS** Video Prepared Complete!");

            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            if (texture != null) {
                mMediaPlayer.setSurface(new Surface(texture));
            }

            mMediaPlayer.setScreenOnWhilePlaying(true);
            mMediaPlayer.start();
            mMediaPlayer.seekTo(0);
        }
    };

    private MediaPlayer.OnCompletionListener mOnCompletionListener =
            new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer player) {
                    Toast.makeText(MainActivity.this, "Video Play Complete!", Toast.LENGTH_SHORT).show();
                }
            };

    private MediaPlayer.OnVideoSizeChangedListener mOnVideoSizeChangedListener =
            new MediaPlayer.OnVideoSizeChangedListener() {
                public void onVideoSizeChanged(MediaPlayer player, int width, int height) {
                    int viewWidth = mTextureView.getMeasuredWidth();
                    if (width > 0 && height > 0 && viewWidth > 0) {
                        ViewGroup.LayoutParams params = mTextureView.getLayoutParams();
                        params.height = viewWidth * height / width;
                        mTextureView.setLayoutParams(params);
                    }
                }
            };

    private MediaPlayer.OnErrorListener mOnErrorListener = new MediaPlayer.OnErrorListener() {
        public boolean onError(MediaPlayer player, int what, int extra) {
            String errorWhat;
            switch (what) {
                case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                    errorWhat = "MEDIA_ERROR_UNKNOWN";
                    break;
                case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                    errorWhat = "MEDIA_ERROR_SERVER_DIED";
                    break;
                default:
                    errorWhat = "!";
            }

            String errorExtra;
            switch (extra) {
                case MediaPlayer.MEDIA_ERROR_UNSUPPORTED:
                    errorExtra = "MEDIA_ERROR_UNSUPPORTED";
                    break;
                case MediaPlayer.MEDIA_ERROR_MALFORMED:
                    errorExtra = "MEDIA_ERROR_MALFORMED";
                    break;
                case MediaPlayer.MEDIA_ERROR_IO:
                    errorExtra = "MEDIA_ERROR_IO";
                    break;
                case MediaPlayer.MEDIA_ERROR_TIMED_OUT:
                    errorExtra = "MEDIA_ERROR_TIMED_OUT";
                    break;
                default:
                    errorExtra = "!";
            }

            String msg = String.format(
                    "what = %d (%s), extra = %d (%s)",
                    what, errorWhat, extra, errorExtra);
            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();

            return true;
        }
    };
}
