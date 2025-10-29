package cn.pylin.xykcb;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class CustomCourseDividerItemDecoration extends RecyclerView.ItemDecoration {
    
    private final Paint paint;
    private final int dividerHeight;
    
    public CustomCourseDividerItemDecoration() {
        this.paint = new Paint();
        // 设置分割线颜色为灰色
        this.paint.setColor(0xFFCCCCCC);
        this.paint.setStyle(Paint.Style.FILL);
        // 设置分割线高度为1dp
        this.dividerHeight = 1;
    }
    
    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, 
                              @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        super.getItemOffsets(outRect, view, parent, state);
        
        // 为每个item底部添加分割线空间（除了最后一个item）
        int position = parent.getChildAdapterPosition(view);
        if (position < parent.getAdapter().getItemCount() - 1) {
            outRect.bottom = dividerHeight;
        }
    }
    
    @Override
    public void onDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent, 
                       @NonNull RecyclerView.State state) {
        super.onDraw(canvas, parent, state);
        
        final int left = parent.getPaddingLeft();
        final int right = parent.getWidth() - parent.getPaddingRight();
        
        final int childCount = parent.getChildCount();
        for (int i = 0; i < childCount - 1; i++) {
            final View child = parent.getChildAt(i);
            final RecyclerView.LayoutParams params = 
                (RecyclerView.LayoutParams) child.getLayoutParams();
            
            // 计算分割线的顶部位置
            final int top = child.getBottom() + params.bottomMargin;
            final int bottom = top + dividerHeight;
            
            // 绘制分割线
            canvas.drawRect(left, top, right, bottom, paint);
        }
    }
}