package com.ls.myapplication;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.ImageView;

import com.googlecode.tesseract.android.TessBaseAPI;
import com.linchaolong.android.imagepicker.ImagePicker;
import com.linchaolong.android.imagepicker.cropper.CropImage;
import com.linchaolong.android.imagepicker.cropper.CropImageView;

import org.angmarch.views.NiceSpinner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 直接使用compile编译进来，不要用导入module方式。
 * 注意路径。。。。很操蛋的。
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private String[] languages;//= new String[]{"chi_sim", "eng"};
    private String dir = Environment.getExternalStorageDirectory().getAbsolutePath();

    private ImageView imageView;
    private ImagePicker imagePicker = new ImagePicker();
    private Bitmap bitmap;

    private static final int PERMISSION_REQUEST_CODE = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageView = findViewById(R.id.image);
        bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.aa);
        imageView.setImageBitmap(bitmap);

        languages = getResources().getStringArray(R.array.languages);
        NiceSpinner niceSpinner = findViewById(R.id.nice_spinner);
        List<String> dataSet = new LinkedList<>(Arrays.asList(languages));
        niceSpinner.attachDataSource(dataSet);

        findViewById(R.id.btn).setOnClickListener(v -> {
            String langData = languages[niceSpinner.getSelectedIndex()] + ".traineddata";
            if (!new File(dir + "/tessdata/", langData).exists()) {
                new AlertDialog.Builder(this).setMessage("当前语言离线训练字库不存在，是否需要下载？")
                        .setPositiveButton("确定", (dialog, which) -> {
                            dialog.dismiss();
                            new DownLoadAsyncTask(this).execute("https://raw.githubusercontent.com/tesseract-ocr/tessdata/3.04.00/"
                                    + langData, dir + "/tessdata/", langData);

                        }).setNegativeButton("取消", null).show();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (checkSelfPermission(Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.INTERNET}, PERMISSION_REQUEST_CODE);
                    }
                }
                return;
            }
            new TesseractAsyncTask(this, bitmap).execute(dir, languages[niceSpinner.getSelectedIndex()]);
        });

        findViewById(R.id.choose).setOnClickListener(v -> {
            // 设置标题
            imagePicker.setTitle("选择图片");
            // 设置是否裁剪图片
            imagePicker.setCropImage(true);
            // 启动图片选择器
            imagePicker.startGallery(MainActivity.this, new ImagePicker.Callback() {
                // 选择图片回调
                @Override
                public void onPickImage(Uri imageUri) {


                }

                // 裁剪图片回调
                @Override
                public void onCropImage(Uri imageUri) {
                    try {
                        Bitmap b = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                        if (b != null) {
                            bitmap = b;
                            runOnUiThread(() -> imageView.setImageBitmap(bitmap));
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }

                // 自定义裁剪配置
                @Override
                public void cropConfig(CropImage.ActivityBuilder builder) {
                    builder
                            // 是否启动多点触摸
                            .setMultiTouchEnabled(false)
                            // 设置网格显示模式
                            .setGuidelines(CropImageView.Guidelines.ON)
                            // 圆形/矩形
                            .setCropShape(CropImageView.CropShape.RECTANGLE)
                            .setAutoZoomEnabled(true)
                            .setMinCropWindowSize(10, 10)
                            .setAllowRotation(true)
                            .setOutputCompressFormat(Bitmap.CompressFormat.PNG)
                            .setBorderCornerColor(Color.TRANSPARENT)
                            .setBorderCornerThickness(0)
                            .setBorderLineThickness(3)
                            .setInitialCropWindowPaddingRatio(0.05f)
                            .setBackgroundColor(Color.argb(100, 100, 100, 100))
                            .setMaxZoom(50)
                            .setOutputCompressQuality(100)
                            // 调整裁剪后的图片最终大小（单位：px）
                            // .setRequestedSize(960, 540)
                            .setScaleType(CropImageView.ScaleType.FIT_CENTER);
                    // 裁剪框宽高比
                    //.setAspectRatio(16, 9);
                }

                // 用户拒绝授权回调
                @Override
                public void onPermissionDenied(int requestCode, String[] permissions,
                                               int[] grantResults) {
                }
            });

        });


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
            }
        }

    }


    public void copyToSD() {
        new Thread(() -> {
            File tess = new File(dir, "tessdata");
            if (tess.isFile() && tess.delete()) {
                Log.e(TAG, tess.getAbsolutePath() + "is a file and try to deleted but no use");
            }
            if (!tess.exists() && tess.mkdirs()) {
                Log.e(TAG, "can not create direction" + tess.getAbsolutePath());
            }
            for (String language : languages) {
                try {
                    if (!new File(dir + "/tessdata/" + language + ".traineddata").exists()) {
                        copy2SD(getAssets(), language + ".traineddata", dir + "/tessdata/" + language + ".traineddata");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();

    }

    /**
     * 请求到权限后在这里复制识别库
     *
     * @param requestCode
     * @param permissions
     * @param grantResults
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "onRequestPermissionsResult: copy");
                    //copyToSD();
                }
                break;
            default:
                break;
        }
        imagePicker.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        imagePicker.onActivityResult(this, requestCode, resultCode, data);

    }


    private void copy2SD(AssetManager manager, String name, String des) throws IOException {
        byte[] buff = new byte[1024 * 8];
        int len;
        InputStream is = manager.open(name);
        OutputStream os = new FileOutputStream(des);
        while ((len = is.read(buff)) != -1) {
            os.write(buff, 0, len);
        }
        is.close();
        os.close();
    }

    static OkHttpClient client = new OkHttpClient();


    static class TesseractAsyncTask extends AsyncTask<String, Void, String> {
        private WeakReference<Context> wr;
        ProgressDialog progressDialog;
        private Bitmap bitmap;
        private String result = "";

        private Context getContext() {
            return wr.get();
        }

        public TesseractAsyncTask(Context context, Bitmap bitmap) {
            this.wr = new WeakReference<>(context);
            this.bitmap = bitmap;
        }


        @Override
        protected String doInBackground(String... objects) {
            String dir = objects[0];
            String lang = objects[1];
            TessBaseAPI tessBaseAPI = new TessBaseAPI();
            String rt;
            try {
                long start = System.currentTimeMillis();
                tessBaseAPI.init(dir, lang);
                tessBaseAPI.setImage(bitmap);
                result = tessBaseAPI.getUTF8Text();
                Log.i(TAG, result);
                long end = System.currentTimeMillis();
                rt = result + "\n耗时：" + (end - start) + "ms";
            } catch (Exception e) {
                e.printStackTrace();
                rt = "错误产生";
            } finally {
                tessBaseAPI.end();
            }
            return rt;
        }

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(getContext());
            progressDialog.setCancelable(false);
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.setTitle("正在识别中…");
            progressDialog.show();
        }

        @Override
        protected void onPostExecute(String s) {
            progressDialog.dismiss();
            new AlertDialog.Builder(getContext()).setTitle("识别结果")
                    .setMessage(s).setPositiveButton("复制",
                    (dialog, which) -> setClip(getContext(), result)).setNegativeButton("取消", null).show();
        }


    }

    private static void setClip(Context context, String str) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clipData = ClipData.newPlainText("simple text copy", str);
        //添加ClipData对象到剪切板中
        if (cm != null) {
            cm.setPrimaryClip(clipData);
        }
    }

    static class DownLoadAsyncTask extends AsyncTask<String, Integer, Void> {
        private WeakReference<Context> wr;

        private Context getContext() {
            return wr.get();
        }

        DownLoadAsyncTask(Context context) {
            wr = new WeakReference<>(context);
        }

        ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(getContext());
            progressDialog.setTitle("数据包正在下载中");
            progressDialog.setMessage("下载进度：");
            progressDialog.setCancelable(false);
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.setIndeterminate(false);
            progressDialog.setMax(100);
            progressDialog.setButton(ProgressDialog.BUTTON_POSITIVE, "取消", (dialog, which) -> cancel(true));
            progressDialog.show();
        }

        @Override
        protected void onProgressUpdate(Integer[] values) {

            progressDialog.setProgress(values[0]);


        }

        @Override
        protected Void doInBackground(String[] objects) {
            String url = objects[0];
            String dir = objects[1];
            String name = objects.length > 2 ? objects[2] : null;
            Request request = new Request.Builder().url(url).get().build();
            try {

                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    long size = Long.parseLong(response.header("Content-Length"));
                    name = name == null ? response.header("") : name;
                    if (name == null) {
                        throw new IOException("filename can not be null");
                    }
                    if (!new File(dir).exists() && !new File(dir).mkdirs()) {
                        Log.e(TAG, "can not make direction of " + dir);
                    }
                    OutputStream os = new FileOutputStream(new File(dir, name + ".temp"));
                    ResponseBody body = response.body();
                    if (body != null) {
                        InputStream is = body.byteStream();
                        int len;
                        long hasDownload = 0;
                        byte[] buff = new byte[1024];
                        while ((len = is.read(buff)) != -1) {
                            os.write(buff, 0, len);
                            hasDownload += len;
                            publishProgress((int) (100 * hasDownload / size));
                        }
                        os.close();
                        is.close();
                        new File(dir, name + ".temp").renameTo(new File(dir, name));
                    } else {
                        throw new IOException("response body is null");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void o) {
            progressDialog.dismiss();
        }
    }

}
