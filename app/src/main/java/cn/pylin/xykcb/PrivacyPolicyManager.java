package cn.pylin.xykcb;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;

/**
 * 隐私政策管理工具类
 * 提供弹窗提示和数据清除的公共方法
 */
public class PrivacyPolicyManager {
    
    /**
     * 显示撤销同意确认弹窗
     * @param activity 当前Activity
     * @param onCancelCallback 取消回调（用于恢复开关状态）
     */
    public static void showRevokeConsentDialog(Activity activity, Runnable onCancelCallback) {
        AlertDialog.Builder confirmBuilder = new AlertDialog.Builder(activity, R.style.DialogTheme);
        View confirmView = LayoutInflater.from(activity).inflate(R.layout.dialog_main, null);
        
        // 设置标题和内容
        TextView tvTitle = confirmView.findViewById(R.id.tvCourseName);
        tvTitle.setText("确认撤销？");
        
        // 获取内容容器并添加消息
        LinearLayout contentContainer = confirmView.findViewById(R.id.courseContainer);
        TextView tvMessage = new TextView(activity);
        tvMessage.setText("撤销同意《小雨课程表用户协议及隐私政策》将清除本应用的所有数据并退出，是否继续？");
        tvMessage.setTextSize(16);
        tvMessage.setTextColor(activity.getResources().getColor(R.color.info_text_color));
        tvMessage.setPadding(0, 0, 0, 20);
        contentContainer.addView(tvMessage);
        
        // 设置按钮
        Button btnConfirm = confirmView.findViewById(R.id.btn_close);
        btnConfirm.setText("确认撤销");
        // 以代码形式设置确认按钮的背景颜色为红色
        GradientDrawable confirmBackground = new GradientDrawable();
        confirmBackground.setShape(GradientDrawable.RECTANGLE);
        confirmBackground.setColor(Color.parseColor("#FF5252")); // 红色背景
        confirmBackground.setCornerRadius(10);
        btnConfirm.setBackground(confirmBackground);
        btnConfirm.setTextColor(Color.WHITE); // 白色文字
        
        // 添加取消按钮
        Button btnCancel = new Button(activity);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 10, 0, 0);
        btnCancel.setLayoutParams(params);
        btnCancel.setText("取消");
        btnCancel.setTextSize(18);
        // 以代码形式设置取消按钮的背景颜色为灰色
        GradientDrawable cancelBackground = new GradientDrawable();
        cancelBackground.setShape(GradientDrawable.RECTANGLE);
        cancelBackground.setColor(Color.parseColor("#757575")); // 灰色背景
        cancelBackground.setCornerRadius(10);
        btnCancel.setBackground(cancelBackground);
        btnCancel.setTextColor(Color.WHITE); // 白色文字
        
        // 将取消按钮添加到对话框底部
        LinearLayout rootLayout = (LinearLayout) confirmView;
        rootLayout.addView(btnCancel);
        
        confirmBuilder.setView(confirmView);
        AlertDialog confirmDialog = confirmBuilder.create();
        confirmDialog.setCancelable(false);
        
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
            // 用户取消撤销，调用回调函数
            if (onCancelCallback != null) {
                onCancelCallback.run();
            }
            confirmDialog.dismiss();
        });
        
        confirmDialog.show();
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
        View confirmView = LayoutInflater.from(activity).inflate(R.layout.dialog_main, null);
        
        // 设置标题和内容
        TextView tvTitle = confirmView.findViewById(R.id.tvCourseName);
        tvTitle.setText("提示");
        
        // 获取内容容器并添加消息
        LinearLayout contentContainer = confirmView.findViewById(R.id.courseContainer);
        TextView tvMessage = new TextView(activity);
        tvMessage.setText("不同意《小雨课程表用户协议及隐私政策》将清除本应用的所有数据并退出，是否继续？");
        tvMessage.setTextSize(16);
        tvMessage.setTextColor(activity.getResources().getColor(R.color.info_text_color));
        tvMessage.setPadding(0, 0, 0, 20);
        contentContainer.addView(tvMessage);
        
        // 设置按钮
        Button btnConfirm = confirmView.findViewById(R.id.btn_close);
        btnConfirm.setText("不同意并退出");
        // 以代码形式设置确认按钮的背景颜色为红色
        GradientDrawable confirmBackground = new GradientDrawable();
        confirmBackground.setShape(GradientDrawable.RECTANGLE);
        confirmBackground.setColor(Color.parseColor("#FF5252")); // 红色背景
        confirmBackground.setCornerRadius(10);
        btnConfirm.setBackground(confirmBackground);
        btnConfirm.setTextColor(Color.WHITE); // 白色文字
        
        // 添加取消按钮
        Button btnCancel = new Button(activity);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 10, 0, 0);
        btnCancel.setLayoutParams(params);
        btnCancel.setText("取消");
        btnCancel.setTextSize(18);
        // 以代码形式设置取消按钮的背景颜色为灰色
        GradientDrawable cancelBackground = new GradientDrawable();
        cancelBackground.setShape(GradientDrawable.RECTANGLE);
        cancelBackground.setColor(Color.parseColor("#757575")); // 灰色背景
        cancelBackground.setCornerRadius(10);
        btnCancel.setBackground(cancelBackground);
        btnCancel.setTextColor(Color.WHITE); // 白色文字
        
        // 将取消按钮添加到对话框底部
        LinearLayout rootLayout = (LinearLayout) confirmView;
        rootLayout.addView(btnCancel);
        
        confirmBuilder.setView(confirmView);
        AlertDialog confirmDialog = confirmBuilder.create();
        confirmDialog.setCancelable(false);
        
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