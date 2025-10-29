package cn.pylin.xykcb;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

/**
 * 自定义Toast工具类，用于替代系统Toast
 * 使用悬浮窗实现，显示前会检测权限
 */
public class CustomToast {
    private static final int TOAST_SHORT_DURATION = 2000; // 短时间显示的毫秒数
    private static final int TOAST_LONG_DURATION = 3500;  // 长时间显示的毫秒数

    private static View mToastView = null;
    private static TextView mToastMessage = null;
    private static Handler mHandler = new Handler(Looper.getMainLooper());
    private static Runnable mHideToastRunnable = null;
    private static WindowManager.LayoutParams mWindowParams = null;
    private static WindowManager mWindowManager = null;

    /**
     * 显示短时间的Toast
     * @param context 上下文
     * @param message 消息内容
     */
    public static void showShortToast(final Context context, final String message) {
        showToast(context, message, TOAST_SHORT_DURATION);
    }

    /**
     * 显示长时间的Toast
     * @param context 上下文
     * @param message 消息内容
     */
    public static void showLongToast(final Context context, final String message) {
        showToast(context, message, TOAST_LONG_DURATION);
    }

    /**
     * 显示自定义Toast
     * @param context 上下文
     * @param message 消息内容
     * @param duration 显示时长（毫秒）
     */
    private static void showToast(final Context context, final String message, final int duration) {
        // 确保在主线程中执行
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mHandler.post(() -> showToastInUiThread(context, message, duration));
        } else {
            showToastInUiThread(context, message, duration);
        }
    }

    /**
     * 在UI线程中显示Toast
     */
    private static void showToastInUiThread(Context context, String message, int duration) {
        // 移除之前的隐藏任务
        if (mHideToastRunnable != null) {
            mHandler.removeCallbacks(mHideToastRunnable);
        }

        // 检查悬浮窗权限
        if (!hasOverlayPermission(context)) {
            // 没有权限，使用系统Toast
            android.widget.Toast.makeText(context, message, 
                    duration == TOAST_SHORT_DURATION ? android.widget.Toast.LENGTH_SHORT : android.widget.Toast.LENGTH_LONG).show();
            return;
        }

        // 获取WindowManager
        if (mWindowManager == null) {
            mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        }

        // 创建或更新Toast视图
        if (mToastView == null) {
            // 初始化Toast视图
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            if (inflater != null) {
                mToastView = inflater.inflate(R.layout.custom_toast, null);
                mToastMessage = mToastView.findViewById(R.id.toast_message);
            }
        }

        if (mToastView == null || mToastMessage == null) {
            // 如果无法创建视图，降级使用系统Toast
            android.widget.Toast.makeText(context, message, 
                    duration == TOAST_SHORT_DURATION ? android.widget.Toast.LENGTH_SHORT : android.widget.Toast.LENGTH_LONG).show();
            return;
        }

        // 更新消息内容
        mToastMessage.setText(message);

        // 创建或更新Window参数
        if (mWindowParams == null) {
            mWindowParams = createWindowParams();
        }

        try {
            // 移除之前的Toast视图（如果存在）
            if (mToastView.getParent() != null) {
                mWindowManager.removeView(mToastView);
            }

            // 添加Toast视图到窗口
            mWindowManager.addView(mToastView, mWindowParams);

            // 设置动画
            mToastView.setAlpha(0);
            mToastView.post(() -> {
                if (mToastView != null) {
                    // 组合动画：淡入和上移动画
                    mToastView.animate()
                            .alpha(1)
                            .setDuration(300)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            // 添加失败，降级使用系统Toast
            android.widget.Toast.makeText(context, message, 
                    duration == TOAST_SHORT_DURATION ? android.widget.Toast.LENGTH_SHORT : android.widget.Toast.LENGTH_LONG).show();
            return;
        }

        // 设置新的隐藏任务
        mHideToastRunnable = () -> hideToast();
        mHandler.postDelayed(mHideToastRunnable, duration);
    }

    /**
     * 创建Window参数
     */
    private static WindowManager.LayoutParams createWindowParams() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        
        // 设置Window类型
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            params.type = WindowManager.LayoutParams.TYPE_PHONE;
        }
        
        // 设置Window属性
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        
        // 设置背景透明
        params.format = android.graphics.PixelFormat.TRANSLUCENT;
        
        // 设置大小
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        
        // 设置位置：居中偏下
        params.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
        params.y = 200; // 距离底部的距离
        
        return params;
    }

    /**
     * 检查是否有悬浮窗权限
     */
    private static boolean hasOverlayPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(context);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // Android 4.4-6.0 不需要明确的悬浮窗权限
            return true;
        } else {
            // 更早的版本也不需要明确的悬浮窗权限
            return true;
        }
    }

    /**
     * 隐藏Toast
     */
    private static void hideToast() {
        if (mToastView != null && mWindowManager != null) {
            try {
                // 防止用户在动画过程中与视图交互
                mToastView.setClickable(false);
                mToastView.setFocusable(false);
                
                // 设置淡出动画
                mToastView.animate()
                        .alpha(0)
                        .setDuration(300)
                        .setInterpolator(new DecelerateInterpolator())
                        .withEndAction(() -> {
                            // 使用Handler延迟一点时间再移除视图，确保动画完全结束
                            mHandler.postDelayed(() -> {
                                // 动画结束后移除视图
                                try {
                                    if (mToastView != null && mWindowManager != null) {
                                        // 先将视图设为不可见，然后再移除，避免闪烁
                                        mToastView.setVisibility(View.GONE);
                                        // 使用标准的removeView方法移除视图
                                        mWindowManager.removeView(mToastView);
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                mToastView = null;
                                mToastMessage = null;
                            }, 50); // 小延迟确保动画完全结束
                        })
                        .start();
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    if (mToastView != null && mWindowManager != null) {
                        mWindowManager.removeView(mToastView);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                mToastView = null;
                mToastMessage = null;
            }
        }
    }
}