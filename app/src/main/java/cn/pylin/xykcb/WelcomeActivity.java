package cn.pylin.xykcb;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

public class WelcomeActivity extends AppCompatActivity {
    private ViewPager2 viewPager;
    private LinearLayout dotsLayout;
    private LinearLayout bottomLayout;
    private Button btnNext, btnStart; // 已移除 btnSkip
    private final int[] welcomeImages = {
            R.drawable.welcome_1,
            R.drawable.welcome_2,
            R.drawable.welcome_3
    };
    
    // 定义介绍页面显示的初始值
    private static final int INTRO_VERSION = 251018;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 检查是否从设置页面进入
        boolean fromSettings = getIntent().getBooleanExtra("from_settings", false);
        
        // 如果不是从设置页面进入，则检查是否需要显示介绍页面
        if (!fromSettings && !isFirstLaunch()) {
            startMainActivity();
            return;
        }
        
        setContentView(R.layout.activity_welcome);
        initViews();
        setupViewPager();
        setupClickListeners();
        
    }
    
    private boolean isFirstLaunch() {
        SharedPreferences prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE);
        int currentVersion = prefs.getInt("introVersion", 0);
        // 如果本地存储的值小于初始值，则需要显示介绍页面
        return currentVersion < INTRO_VERSION;
    }
    
    private void setFirstLaunchCompleted() {
        SharedPreferences prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE);
        prefs.edit().putInt("introVersion", INTRO_VERSION).apply();
    }
    
    private void setIntroRejected() {
        SharedPreferences prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE);
        prefs.edit().putInt("introVersion", 0).apply();
    }
    
    private void initViews() {
        viewPager = findViewById(R.id.viewPager);
        dotsLayout = findViewById(R.id.dotsLayout);
        bottomLayout = findViewById(R.id.bottomLayout);
        btnNext = findViewById(R.id.btnNext);
        btnStart = findViewById(R.id.btnStart);
    }
    
    private void setupViewPager() {
        // 创建本地图片适配器
        LocalWelcomeAdapter adapter = new LocalWelcomeAdapter(welcomeImages);
        viewPager.setAdapter(adapter);
        
        setupDots();
        
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateDots(position);
                updateButtons(position);
            }
        });
    }
    
    private void setupDots() {
        ImageView[] dots = new ImageView[welcomeImages.length];
        dotsLayout.removeAllViews();
        
        for (int i = 0; i < dots.length; i++) {
            dots[i] = new ImageView(this);
            dots[i].setImageResource(R.drawable.dot_inactive);
            
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(8, 0, 8, 0);
            
            dotsLayout.addView(dots[i], params);
        }
        
        if (dots.length > 0) {
            dots[0].setImageResource(R.drawable.dot_active);
        }
    }
    
    private void updateDots(int position) {
        for (int i = 0; i < dotsLayout.getChildCount(); i++) {
            ImageView dot = (ImageView) dotsLayout.getChildAt(i);
            if (i == position) {
                dot.setImageResource(R.drawable.dot_active);
            } else {
                dot.setImageResource(R.drawable.dot_inactive);
            }
        }
    }
    
    private void updateButtons(int position) {
        if (position == welcomeImages.length - 1) {
            // 最后一页：隐藏下一步，显示开始使用
            btnNext.setVisibility(View.GONE);
            btnStart.setVisibility(View.VISIBLE);
            btnStart.setText("开始使用");
        } else {
            // 其他页面：只显示下一步按钮
            btnNext.setVisibility(View.VISIBLE);
            btnStart.setVisibility(View.GONE);
        }
    }
    
    private void setupClickListeners() {
        boolean fromSettings = getIntent().getBooleanExtra("from_settings", false);
        
        // 移除 btnSkip 的点击监听器
        
        btnNext.setOnClickListener(v -> {
            int currentItem = viewPager.getCurrentItem();
            if (currentItem < welcomeImages.length - 1) {
                viewPager.setCurrentItem(currentItem + 1);
            }
        });
        
        btnStart.setOnClickListener(v -> {
            // 无论是否从设置页面进入，都显示隐私政策弹窗
            showPrivacyPolicyDialog();
        });
    }
    
    private void startMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
    
    private void showPrivacyPolicyDialog() {
        // 使用自定义布局创建AlertDialog，使用与菜单相同的主题
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_privacy_policy, null);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DialogTheme);
        builder.setView(dialogView);
        
        AlertDialog dialog = builder.create();
        
        // 获取按钮并设置点击事件
        Button btnAgree = dialogView.findViewById(R.id.btn_agree);
        Button btnDisagree = dialogView.findViewById(R.id.btn_disagree);
        TextView tvUserAgreement = dialogView.findViewById(R.id.tv_user_agreement);
        TextView tvPrivacyPolicy = dialogView.findViewById(R.id.tv_privacy_policy);
        
        // 设置TextView为可点击
        tvUserAgreement.setClickable(true);
        tvUserAgreement.setFocusable(true);
        tvPrivacyPolicy.setClickable(true);
        tvPrivacyPolicy.setFocusable(true);
        
        // 检查是否从设置页面进入
        boolean fromSettings = getIntent().getBooleanExtra("from_settings", false);
        
        // 联网加载用户协议内容
        PrivacyPolicyManager.loadUserAgreementContent(new PrivacyPolicyManager.PrivacyPolicyCallback() {
            @Override
            public void onPolicyLoaded(String content) {
                // 在主线程中更新UI
                runOnUiThread(() -> {
                    tvUserAgreement.setText(content);
                    // 如果内容是加载失败的提示，设置点击事件
                    if (content.contains("点击查看用户协议")) {
                        tvUserAgreement.setOnClickListener(v -> {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.pylin.cn/xykcb"));
                            startActivity(intent);
                        });
                    }
                });
            }
        });
        
        // 联网加载隐私政策内容
        PrivacyPolicyManager.loadPrivacyPolicyContent(new PrivacyPolicyManager.PrivacyPolicyCallback() {
            @Override
            public void onPolicyLoaded(String content) {
                // 在主线程中更新UI
                runOnUiThread(() -> {
                    tvPrivacyPolicy.setText(content);
                    // 如果内容是加载失败的提示，设置点击事件
                    if (content.contains("点击查看隐私政策")) {
                        tvPrivacyPolicy.setOnClickListener(v -> {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.pylin.cn/xykcb"));
                            startActivity(intent);
                        });
                    }
                });
            }
        });
        
        btnAgree.setOnClickListener(v -> {
            if (!fromSettings) {
                setFirstLaunchCompleted();
            }
            dialog.dismiss();
            startMainActivity();
        });
        
        btnDisagree.setOnClickListener(v -> {
            PrivacyPolicyManager.showDisagreeDialog(WelcomeActivity.this);
        });
        
        dialog.show();
    }
}