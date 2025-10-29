package cn.pylin.xykcb;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import androidx.core.content.ContextCompat;

public class CourseTextView extends androidx.appcompat.widget.AppCompatTextView {
    private boolean showBadge = false;
    private Paint badgePaint;
    private Paint shadowPaint;
    private Path badgePath;
    private RectF backgroundRect;
    private float cornerRadius = 5f;
    private float badgeSize;
    
    public CourseTextView(Context context) {
        super(context);
        init();
    }
    
    public CourseTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public CourseTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        // 初始化画笔
        badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        badgePaint.setColor(ContextCompat.getColor(getContext(), R.color.red)); // 使用color/red资源
        badgePaint.setStyle(Paint.Style.FILL);
        
        // 阴影画笔
        shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(0x40000000); // 半透明黑色阴影
        shadowPaint.setStyle(Paint.Style.FILL);
        
        badgePath = new Path();
        backgroundRect = new RectF();
        
        // 转换dp到px
        badgeSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 16f, 
            getContext().getResources().getDisplayMetrics()
        );
        
        cornerRadius = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 5f, 
            getContext().getResources().getDisplayMetrics()
        );
    }
    
    public void setBadgeVisible(boolean visible) {
        if (showBadge != visible) {
            showBadge = visible;
            invalidate();
        }
    }
    
    public void setBadgeColor(int color) {
        badgePaint.setColor(color);
        if (showBadge) {
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (showBadge) {
            drawTriangleBadge(canvas);
        }
    }
    
    private void drawTriangleBadge(Canvas canvas) {
        float right = getWidth();
        float top = 0;
        
        // 重置路径
        badgePath.reset();
        
        // 创建右上角三角形路径
        // 从右上角开始，逆时针绘制三角形
        badgePath.moveTo(right - badgeSize, top); // 左下角点
        badgePath.lineTo(right, top); // 右上角点
        badgePath.lineTo(right, top + badgeSize); // 右下角点
        badgePath.close();
        
        // 先绘制阴影（稍微偏移）
        canvas.save();
        canvas.translate(1, 1);
        canvas.drawPath(badgePath, shadowPaint);
        canvas.restore();
        
        // 绘制主要的三角形角标
        canvas.drawPath(badgePath, badgePaint);
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // 当尺寸改变时，更新背景矩形
        backgroundRect.set(0, 0, w, h);
    }
}