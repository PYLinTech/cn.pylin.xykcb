package cn.pylin.xykcb;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 检查是否从设置页面进入
        boolean fromSettings = getIntent().getBooleanExtra("from_settings", false);
        
        // 如果不是从设置页面进入，则检查是否首次启动
        if (!fromSettings && !isFirstLaunch()) {
            startMainActivity();
            return;
        }
        
        setContentView(R.layout.activity_welcome);
        initViews();
        setupViewPager();
        setupClickListeners();
        
        // 删除这部分代码，因为不再有跳过按钮
        // if (fromSettings) {
        //     btnSkip.setText("返回");
        //     btnStart.setText("返回");
        // }
    }
    
    private boolean isFirstLaunch() {
        SharedPreferences prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE);
        return prefs.getBoolean("isFirstLaunch", true);
    }
    
    private void setFirstLaunchCompleted() {
        SharedPreferences prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("isFirstLaunch", false).apply();
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
            if (!fromSettings) {
                setFirstLaunchCompleted();
            }
            startMainActivity();
        });
    }
    
    private void startMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}