package cn.pylin.xykcb;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import java.io.File;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 隐私政策管理工具类
 * 提供弹窗提示和数据清除的公共方法
 */
public class PrivacyPolicyManager {
    
    // 用户协议API地址
    private static final String USER_AGREEMENT_API_URL = "https://api.pylin.cn/xykcb_agreement.json";
    
    // 隐私政策API地址
    private static final String PRIVACY_POLICY_API_URL = "https://api.pylin.cn/xykcb_privacy.json";
    
    // 默认内容（当联网加载失败时显示）
    private static final String DEFAULT_USER_AGREEMENT_CONTENT = "加载失败，点击进入官网查看！";
    private static final String DEFAULT_PRIVACY_POLICY_CONTENT = "加载失败，点击进入官网查看！";
    
    /**
     * 联网加载用户协议内容
     * @param callback 加载完成回调
     */
    public static void loadUserAgreementContent(PrivacyPolicyCallback callback) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        
        Request request = new Request.Builder()
                .url(USER_AGREEMENT_API_URL)
                .build();
        
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // 网络请求失败，使用默认内容并记录错误
                android.util.Log.e("PrivacyPolicyManager", "用户协议网络请求失败: " + e.getMessage());
                if (callback != null) {
                    callback.onPolicyLoaded(DEFAULT_USER_AGREEMENT_CONTENT);
                }
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        String content = response.body().string();
                        // 检查内容是否为空或过短
                        if (content != null && content.trim().length() > 10) {
                            if (callback != null) {
                                callback.onPolicyLoaded(content);
                            }
                        } else {
                            // 内容无效，使用默认内容
                            android.util.Log.w("PrivacyPolicyManager", "用户协议内容无效或过短");
                            if (callback != null) {
                                callback.onPolicyLoaded(DEFAULT_USER_AGREEMENT_CONTENT);
                            }
                        }
                    } else {
                        // 服务器响应失败，使用默认内容
                        android.util.Log.e("PrivacyPolicyManager", "用户协议服务器响应失败，状态码: " + response.code());
                        if (callback != null) {
                            callback.onPolicyLoaded(DEFAULT_USER_AGREEMENT_CONTENT);
                        }
                    }
                } catch (Exception e) {
                    // 处理解析异常
                    android.util.Log.e("PrivacyPolicyManager", "用户协议解析响应时发生异常: " + e.getMessage());
                    if (callback != null) {
                        callback.onPolicyLoaded(DEFAULT_USER_AGREEMENT_CONTENT);
                    }
                }
            }
        });
    }
    
    /**
     * 联网加载隐私政策内容
     * @param callback 加载完成回调
     */
    public static void loadPrivacyPolicyContent(PrivacyPolicyCallback callback) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        
        Request request = new Request.Builder()
                .url(PRIVACY_POLICY_API_URL)
                .build();
        
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // 网络请求失败，使用默认内容并记录错误
                android.util.Log.e("PrivacyPolicyManager", "隐私政策网络请求失败: " + e.getMessage());
                if (callback != null) {
                    callback.onPolicyLoaded(DEFAULT_PRIVACY_POLICY_CONTENT);
                }
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        String content = response.body().string();
                        // 检查内容是否为空或过短
                        if (content != null && content.trim().length() > 10) {
                            if (callback != null) {
                                callback.onPolicyLoaded(content);
                            }
                        } else {
                            // 内容无效，使用默认内容
                            android.util.Log.w("PrivacyPolicyManager", "隐私政策内容无效或过短");
                            if (callback != null) {
                                callback.onPolicyLoaded(DEFAULT_PRIVACY_POLICY_CONTENT);
                            }
                        }
                    } else {
                        // 服务器响应失败，使用默认内容
                        android.util.Log.e("PrivacyPolicyManager", "隐私政策服务器响应失败，状态码: " + response.code());
                        if (callback != null) {
                            callback.onPolicyLoaded(DEFAULT_PRIVACY_POLICY_CONTENT);
                        }
                    }
                } catch (Exception e) {
                    // 处理解析异常
                    android.util.Log.e("PrivacyPolicyManager", "隐私政策解析响应时发生异常: " + e.getMessage());
                    if (callback != null) {
                        callback.onPolicyLoaded(DEFAULT_PRIVACY_POLICY_CONTENT);
                    }
                }
            }
        });
    }
    
    /**
     * 隐私政策加载回调接口
     */
    public interface PrivacyPolicyCallback {
        void onPolicyLoaded(String content);
    }
    
    /**
     * 显示撤销同意确认弹窗
     * @param activity 当前Activity
     * @param onCancelCallback 取消回调（用于恢复开关状态）
     */
    public static void showRevokeConsentDialog(Activity activity, Runnable onCancelCallback) {
        AlertDialog.Builder confirmBuilder = new AlertDialog.Builder(activity, R.style.DialogTheme);
        View confirmView = LayoutInflater.from(activity).inflate(R.layout.dialog_revoke_consent, null);
        
        // 获取按钮
        Button btnConfirm = confirmView.findViewById(R.id.btn_confirm_revoke);
        Button btnCancel = confirmView.findViewById(R.id.btn_cancel_revoke);
        
        confirmBuilder.setView(confirmView);
        AlertDialog confirmDialog = confirmBuilder.create();

        // 设置弹窗关闭监听器，在弹窗关闭时还原开关状态
        confirmDialog.setOnCancelListener(dialog -> {
            restoreSwitchState(onCancelCallback);
        });
        
        // 设置确认按钮点击事件
        btnConfirm.setOnClickListener(v -> {
            // 用户确认撤销，清除所有APP数据并退出应用
            CustomToast.showShortToast(activity, "正在清除数据...");
            
            // 关闭对话框
            confirmDialog.dismiss();
            
            // 执行数据清除操作
            clearAppDataAndExit(activity);
        });
        
        // 设置取消按钮点击事件
        btnCancel.setOnClickListener(v -> {
            restoreSwitchState(onCancelCallback);
            confirmDialog.dismiss();
        });
        
        confirmDialog.show();
    }
    
    /**
     * 还原开关状态
     * @param onCancelCallback 取消回调函数
     */
    private static void restoreSwitchState(Runnable onCancelCallback) {
        if (onCancelCallback != null) {
            onCancelCallback.run();
        }
    }
    
    /**
     * 清除应用数据并退出应用
     * @param activity 当前Activity
     */
    public static void clearAppDataAndExit(Activity activity) {
        // 在新线程中执行数据清除操作，确保阻塞完成
        new Thread(() -> {
            File dataDir = activity.getFilesDir();
            File sharedPrefsDir = new File(activity.getApplicationInfo().dataDir + "/shared_prefs");
            File cacheDir = activity.getCacheDir();
            File externalCacheDir = activity.getExternalCacheDir();
            
            // 删除私有目录下的所有文件
            deleteDirectory(dataDir);
            deleteDirectory(sharedPrefsDir);
            deleteDirectory(cacheDir);
            if (externalCacheDir != null) {
                deleteDirectory(externalCacheDir);
            }
            
            // 在主线程中显示完成提示并退出
            activity.runOnUiThread(() -> {
                CustomToast.showShortToast(activity, "数据清除完成，即将退出...");
                // 延迟一秒退出应用，让用户看到提示
                new Handler().postDelayed(() -> {
                    activity.finishAffinity(); // 关闭所有Activity
                    System.exit(0);   // 退出应用进程
                }, 1000);
            });
        }).start();
    }
    
    /**
     * 递归删除目录及其内容
     * @param directory 要删除的目录
     */
    private static void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
    
    /**
     * 显示不同意确认弹窗（用于首次启动时的隐私政策）
     * @param activity 当前Activity
     */
    public static void showDisagreeDialog(Activity activity) {
        AlertDialog.Builder confirmBuilder = new AlertDialog.Builder(activity, R.style.DialogTheme);
        View confirmView = LayoutInflater.from(activity).inflate(R.layout.dialog_disagree_confirm, null);
        
        // 获取按钮
        Button btnConfirm = confirmView.findViewById(R.id.btn_confirm_disagree);
        Button btnCancel = confirmView.findViewById(R.id.btn_cancel_disagree);
        
        confirmBuilder.setView(confirmView);
        AlertDialog confirmDialog = confirmBuilder.create();

        // 设置确认按钮点击事件
        btnConfirm.setOnClickListener(v -> {
            // 用户确认不同意，清除所有APP数据并退出应用
            CustomToast.showShortToast(activity, "正在清除数据...");
            
            // 关闭对话框
            confirmDialog.dismiss();
            
            // 执行数据清除操作
            clearAppDataAndExit(activity);
        });
        
        // 设置取消按钮点击事件
        btnCancel.setOnClickListener(v -> {
            // 用户取消不同意，直接关闭对话框
            confirmDialog.dismiss();
        });
        
        confirmDialog.show();
    }
}