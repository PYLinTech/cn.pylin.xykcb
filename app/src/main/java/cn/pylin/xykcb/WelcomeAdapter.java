package cn.pylin.xykcb;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class WelcomeAdapter extends RecyclerView.Adapter<WelcomeAdapter.WelcomeViewHolder> {
    private final String[] imageUrls;
    private final Map<String, Integer> retryCount = new HashMap<>();
    private static final int MAX_RETRY_COUNT = 3;
    private final ImageLoadCallback callback;
    
    // 图片加载回调接口
    public interface ImageLoadCallback {
        void onImageLoaded(int imageIndex);
        void onImageLoadFailed(int imageIndex);
    }
    
    public WelcomeAdapter(String[] imageUrls, ImageLoadCallback callback) {
        this.imageUrls = imageUrls;
        this.callback = callback;
    }
    
    @NonNull
    @Override
    public WelcomeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_welcome, parent, false);
        return new WelcomeViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull WelcomeViewHolder holder, int position) {
        String imageUrl = imageUrls[position];
        
        // 每次都重新加载图片，不使用缓存
        loadImageFromUrl(holder.imageView, imageUrl, position);
    }
    
    @Override
    public int getItemCount() {
        return imageUrls.length;
    }
    
    // 网络图片加载方法
    @SuppressLint("StaticFieldLeak")
    private void loadImageFromUrl(ImageView imageView, String imageUrl, int imageIndex) {
        new AsyncTask<String, Void, Bitmap>() {
            @Override
            protected Bitmap doInBackground(String... urls) {
                try {
                    URL url = new URL(urls[0]);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setDoInput(true);
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(15000);
                    // 禁用缓存，确保每次都从服务器获取最新图片
                    connection.setUseCaches(false);
                    connection.addRequestProperty("Cache-Control", "no-cache");
                    connection.connect();
                    InputStream input = connection.getInputStream();
                    return BitmapFactory.decodeStream(input);
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }
            
            @Override
            protected void onPostExecute(Bitmap bitmap) {
                if (bitmap != null) {
                    // 清除重试计数
                    retryCount.remove(imageUrl);
                    
                    // 直接设置图片，不缓存
                    if (imageView != null) {
                        imageView.setImageBitmap(bitmap);
                    }
                    
                    // 通知加载完成
                    if (callback != null) {
                        callback.onImageLoaded(imageIndex);
                    }
                } else {
                    // 加载失败时的重试逻辑
                    handleLoadFailure(imageView, imageUrl, imageIndex);
                }
            }
        }.execute(imageUrl);
    }
    
    // 处理加载失败的重试逻辑
    private void handleLoadFailure(ImageView imageView, String imageUrl, int imageIndex) {
        int currentRetryCount = retryCount.getOrDefault(imageUrl, 0);
        
        if (currentRetryCount < MAX_RETRY_COUNT) {
            // 增加重试次数
            retryCount.put(imageUrl, currentRetryCount + 1);
            
            // 延迟重试
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                loadImageFromUrl(imageView, imageUrl, imageIndex);
            }, 1000L * (currentRetryCount + 1));
            
        } else {
            // 达到最大重试次数，最终失败
            retryCount.remove(imageUrl);
            
            // 不设置任何图片，保持空白
            // 通知最终加载失败
            if (callback != null) {
                callback.onImageLoadFailed(imageIndex);
            }
        }
    }
    
    static class WelcomeViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        
        public WelcomeViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.welcomeImage);
        }
    }
}