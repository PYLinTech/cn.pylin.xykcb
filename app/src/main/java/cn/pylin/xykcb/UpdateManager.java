package cn.pylin.xykcb;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.icu.text.SimpleDateFormat;
import android.icu.util.Calendar;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import cn.pylin.xykcb.CustomToast;

import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class UpdateManager {
    private static final String UPDATE_API_URL = "https://api.pylin.cn/xykcb_config.json";
    private final Context context;
    private final RecyclerView recyclerView;
    private boolean isManualCheck = false; // 新添加的标志，用于跟踪是否是手动检查更新
    
    public interface UpdateCallback {
        void onUpdateCheckComplete();
        void onError(String message);
    }
    
    public UpdateManager(Context context, RecyclerView recyclerView) {
        this.context = context;
        this.recyclerView = recyclerView;
    }
    
    public void checkForUpdates() {
        checkForUpdates(null);
    }
    
    public void checkForUpdates(UpdateCallback callback) {
        checkForUpdates(callback, false);
    }
    
    public void checkForUpdates(UpdateCallback callback, boolean isManualCheck) {
        // 设置是否是手动检查的标志
        this.isManualCheck = isManualCheck;
        
        // 对于手动检查，我们清除已忽略的版本记录，确保用户能看到所有更新
        if (isManualCheck) {
            SharedPreferences prefs = context.getSharedPreferences("UpdatePrefs", Context.MODE_PRIVATE);
            prefs.edit().remove("ignored_version_code").apply();
        }
        
        // 执行网络请求检查更新
        performUpdateCheck(callback);
    }
    
    private void performUpdateCheck(UpdateCallback callback) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        
        Request request = new Request.Builder()
                .url(UPDATE_API_URL)
                .build();
        
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                new Handler(Looper.getMainLooper()).post(() -> {
                    String errorMsg = "检查更新失败";
                    CustomToast.showShortToast(context, errorMsg);
                    if (callback != null) {
                        callback.onError(errorMsg);
                    }
                });
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    new Handler(Looper.getMainLooper()).post(() -> {
                        try {
                            parseAndHandleResponse(responseBody, callback);
                        } catch (Exception e) {
                            e.printStackTrace();
                            String errorMsg = "解析响应失败：" + e.getMessage();
                            CustomToast.showShortToast(context, errorMsg);
                            if (callback != null) {
                                callback.onError(errorMsg);
                            }
                        }
                    });
                } else {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        String errorMsg = "检查更新失败";
                        CustomToast.showShortToast(context, errorMsg);
                        if (callback != null) {
                            callback.onError(errorMsg);
                        }
                    });
                }
            }
        });
    }
    
    private void parseAndHandleResponse(String responseBody, UpdateCallback callback) throws Exception {
        JSONObject jsonResponse = new JSONObject(responseBody);
        int latestVersionCode = jsonResponse.getInt("latestVersionCode");
        String downloadUrl = jsonResponse.getString("directlink");
        String updateDescription = jsonResponse.getString("updateDescription");
        
        // 不再从API获取firstDay信息，周次日期信息现在通过LoginManager中的teachingWeek API获取
        
        PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        int currentVersionCode = packageInfo.versionCode;
    
        if (latestVersionCode > currentVersionCode) {
            // 对于自动检查更新，我们需要检查是否已忽略当前版本
            if (!isManualCheck) {
                SharedPreferences prefs = context.getSharedPreferences("UpdatePrefs", Context.MODE_PRIVATE);
                int ignoredVersionCode = prefs.getInt("ignored_version_code", -1);
                
                // 如果是已忽略的版本，则不显示更新对话框
                if (latestVersionCode == ignoredVersionCode) {
                    if (callback != null) {
                        callback.onUpdateCheckComplete();
                    }
                    return;
                }
            }
            
            // 显示更新对话框（手动检查或未被忽略的自动更新）
            showUpdateDialog(downloadUrl, updateDescription, latestVersionCode);
        } else {
            // 如果无需更新，清理旧的APK文件
            cleanupOldApkFiles();
            
            // 只在手动检查时显示"暂无更新可用"的提示
            if (isManualCheck) {
                CustomToast.showLongToast(context, "暂无更新可用！");
            }
        }
        
        if (callback != null) {
            callback.onUpdateCheckComplete();
        }
    }
    
    private void updateWeekDates(Date firstDate, SharedPreferences sp) {
        SharedPreferences.Editor editor = sp.edit();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(firstDate);
    
        int maxWeeks = 24;
        for (int week = 1; week <= maxWeeks; week++) {
            StringBuilder weekData = new StringBuilder();
            for (int i = 0; i < 7; i++) {
                Date date = calendar.getTime();
                String dateStr = new SimpleDateFormat("M.d", Locale.getDefault()).format(date);
                weekData.append(dateStr);
                if (i < 6) {
                    weekData.append(",");
                }
                calendar.add(Calendar.DAY_OF_YEAR, 1);
            }
            editor.putString(String.valueOf(week), weekData.toString());
        }
        editor.apply();
        
        if (recyclerView != null && recyclerView.getAdapter() != null) {
            recyclerView.getAdapter().notifyDataSetChanged();
        }
    }
    
    private void showUpdateDialog(String downloadUrl, String updateDescription, int currentVersionCode) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.DialogTheme);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_update, null);

        TextView tvUpdateDescription = view.findViewById(R.id.tv_update_description);
        Button btnUpdateNow = view.findViewById(R.id.btn_update_now);
        TextView tvUpdateLater = view.findViewById(R.id.tv_update_later);
        TextView tvIgnoreUpdate = view.findViewById(R.id.tv_ignore_update);
        LinearLayout layoutDownloadProgress = view.findViewById(R.id.layout_download_progress);
        TextView tvDownloadStatus = view.findViewById(R.id.tv_download_status);
        ProgressBar progressDownload = view.findViewById(R.id.progress_download);

        tvUpdateDescription.setText(updateDescription);
        final AlertDialog dialog = builder.setView(view).create();
        dialog.show();

        btnUpdateNow.setOnClickListener(v -> {
            // 显示进度条，隐藏按钮
            layoutDownloadProgress.setVisibility(View.VISIBLE);
            tvUpdateLater.setVisibility(View.GONE);
            tvIgnoreUpdate.setVisibility(View.GONE);
            btnUpdateNow.setVisibility(View.GONE);
            
            new ApkDownloader(context, dialog, tvDownloadStatus, progressDownload).downloadAndInstall(downloadUrl);
        });

        tvUpdateLater.setOnClickListener(v -> dialog.dismiss());

        tvIgnoreUpdate.setOnClickListener(v -> {
            // 保存忽略的当前版本号（使用数字版本号）
            SharedPreferences prefs = context.getSharedPreferences("UpdatePrefs", Context.MODE_PRIVATE);
            prefs.edit().putInt("ignored_version_code", currentVersionCode).apply();
            dialog.dismiss();
            // 只有在手动检查更新时才显示Toast提示
            if (isManualCheck) {
                CustomToast.showShortToast(context, "已忽略此次更新");
            }
        });
    }
    
    public void cleanupOldApkFiles() {
        try {
            File apkDir = new File(context.getFilesDir(), "apk");
            if (apkDir.exists()) {
                File[] apkFiles = apkDir.listFiles();
                if (apkFiles != null) {
                    for (File file : apkFiles) {
                        if (file.isFile() && file.getName().endsWith(".apk")) {
                            boolean deleted = file.delete();
                        }
                    }
                }
            }
        } catch (Exception e) {
        }
    }
    
    private static class ApkDownloader {
        private final Context context;
        private final AlertDialog dialog;
        private final TextView statusText;
        private final TextView progressPercent;
        private final ProgressBar progressBar;
        private final OkHttpClient client;
    
        public ApkDownloader(Context context, AlertDialog dialog, TextView statusText, ProgressBar progressBar) {
            this.context = context;
            this.dialog = dialog;
            this.statusText = statusText;
            this.progressBar = progressBar;
            this.progressPercent = dialog.findViewById(R.id.tv_progress_percent);
            
            // 创建OkHttp客户端，设置较长的超时时间用于文件下载
            this.client = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .build();
        }
    
        public void downloadAndInstall(String downloadUrl) {
            // 在UI线程中初始化进度显示
            new Handler(Looper.getMainLooper()).post(() -> {
                statusText.setText("正在下载安装包...");
                progressBar.setProgress(0);
                progressPercent.setText("0%");
            });
            
            Request request = new Request.Builder()
                    .url(downloadUrl)
                    .build();
            
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    e.printStackTrace();
                    new Handler(Looper.getMainLooper()).post(() -> {
                        statusText.setText("下载失败，请检查网络连接");
                        CustomToast.showShortToast(context, "下载失败，请检查网络连接");
                    });
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            statusText.setText("下载失败，服务器错误：" + response.code());
                            CustomToast.showShortToast(context, "下载失败，服务器错误：" + response.code());
                        });
                        return;
                    }
                    
                    try {
                        downloadFileWithProgress(response);
                    } catch (Exception e) {
                        e.printStackTrace();
                        new Handler(Looper.getMainLooper()).post(() -> {
                            statusText.setText("下载失败：" + e.getMessage());
                            CustomToast.showShortToast(context, "下载失败：" + e.getMessage());
                        });
                    }
                }
            });
        }
        
        private void downloadFileWithProgress(Response response) throws IOException {
            long fileLength = response.body().contentLength();
            InputStream input = response.body().byteStream();
            
            File apkDir = new File(context.getFilesDir(), "apk");
            if (!apkDir.exists()) apkDir.mkdirs();
            File outputFile = new File(apkDir, "update.apk");
            
            FileOutputStream output = new FileOutputStream(outputFile);
            byte[] data = new byte[4096];
            long total = 0;
            int count;
            
            try {
                while ((count = input.read(data)) != -1) {
                    total += count;
                    output.write(data, 0, count);
                    
                    // 更新进度
                    if (fileLength > 0) {
                        final int progress = (int) (total * 100 / fileLength);
                        new Handler(Looper.getMainLooper()).post(() -> {
                            progressBar.setProgress(progress);
                            statusText.setText("正在下载安装包...");
                            progressPercent.setText(progress + "%");
                        });
                    }
                }
                
                // 下载完成
                new Handler(Looper.getMainLooper()).post(() -> {
                    statusText.setText("下载完成，准备安装...");
                    dialog.dismiss();
                    installApk(outputFile);
                });
                
            } finally {
                try {
                    output.close();
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    
        private void installApk(File apkFile) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                Uri uri = FileProvider.getUriForFile(
                        context,
                        context.getPackageName() + ".fileprovider",
                        apkFile);
    
                intent.setDataAndType(uri, "application/vnd.android.package-archive");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
                CustomToast.showShortToast(context, "安装失败：" + e.getMessage());
            }
        }
    }
}