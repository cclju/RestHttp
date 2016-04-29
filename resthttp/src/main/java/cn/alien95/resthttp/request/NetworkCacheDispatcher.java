package cn.alien95.resthttp.request;

import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;

import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;

import cn.alien95.resthttp.request.callback.HttpCallback;
import cn.alien95.resthttp.request.rest.RestHttpConnection;
import cn.alien95.resthttp.request.rest.callback.RestCallback;
import cn.alien95.resthttp.util.CacheKeyUtils;
import cn.alien95.resthttp.util.RestHttpLog;

/**
 * Created by linlongxin on 2016/4/27.
 */
public class NetworkCacheDispatcher {

    private boolean isEmptyQueue = true;
    private boolean isRestEmptyQueue = true;
    private LinkedBlockingDeque<Request> cacheQueue;
    private LinkedBlockingDeque<Request> restCacheQueue;
    private Handler handler;
    private static NetworkCacheDispatcher instance;

    private NetworkCacheDispatcher() {
        cacheQueue = new LinkedBlockingDeque<>();
        restCacheQueue = new LinkedBlockingDeque<>();
        handler = new Handler(Looper.myLooper());
    }

    public static NetworkCacheDispatcher getInstance() {
        if (instance == null) {
            instance = new NetworkCacheDispatcher();
        }
        return instance;
    }

    public void addCacheRequest(String url, int method, Map<String, String> params, HttpCallback callback) {
        cacheQueue.add(new Request(url, method, params, callback));
        if (isEmptyQueue) {
            start();
        }
    }

    public void addAsynRestCacheRequest(String url, int method, Map<String, String> params, RestCallback<?> restCallback) {
        restCacheQueue.add(new Request(url, method, params, restCallback));
        if (isRestEmptyQueue) {
            start();
        }
    }

    public void addSyncRestCacheRequest(String url, int method, Map<String, String> params, Class resultType) {
        restCacheQueue.add(new Request(url, method, params, false, resultType));
        if (isRestEmptyQueue) {
            start();
        }
    }


    public Object start() {
        Request request;
        Cache.Entry entry;

        /**
         * 普通请求方式
         */
        while (!cacheQueue.isEmpty()) {
            request = cacheQueue.poll();
            entry = NetworkCache.getInstance().get(request.httpUrl);
            if (entry != null) {
                if (entry.isExpired() || entry.refreshNeeded()) { //过期了
                    RequestQueue.getInstance().addRequest(request.httpUrl, request.method, request.params, request.callback);
                    RestHttpLog.i("network cache is out of date");
                } else {
                    getCacheAsyn(request.httpUrl, request.callback);
                }
            }

            isEmptyQueue = false;
        }

        /**
         * Restful接口缓存处理方式
         */
        while (!restCacheQueue.isEmpty()) {
            request = restCacheQueue.poll();
            entry = NetworkCache.getInstance().get(CacheKeyUtils.getCacheKey(request.httpUrl, request.params));
            if (entry != null) {
                if (entry.isExpired() || entry.refreshNeeded()) { //过期了
                    RestHttpConnection.getInstance().quest(request.httpUrl,
                            Method.POST, request.params, request.restRestCallback.getActualClass());
                    RestHttpLog.i("network cache is out of date");
                } else {
                    RestHttpLog.i("start network cache read");
                    if (request.isAsyn) {  //异步
                        getRestCacheAysn(CacheKeyUtils.getCacheKey(request.httpUrl, request.params), request.restRestCallback);
                    } else {  //同步
                        return getRestCacheSync(CacheKeyUtils.getCacheKey(request.httpUrl, request.params), request.resultType);
                    }
                }
            }

            isRestEmptyQueue = false;
        }

        isEmptyQueue = true;
        isRestEmptyQueue = true;
        return null;
    }


    /**
     * 异步读取文件并转化为对象
     *
     * @param url
     * @param callback
     */
    public void getCacheAsyn(final String url, final HttpCallback callback) {
        RequestQueue.getInstance().addReadNetworkCacheAsyn(new Runnable() {
            @Override
            public void run() {
                final Cache.Entry entry = NetworkCache.getInstance().get(url);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.success(entry.data);
                    }
                });
            }
        });
    }

    public <T> T getRestCacheSync(String key, Class<T> tClass) {
        final Cache.Entry entry = NetworkCache.getInstance().get(key);
        if (tClass != null && tClass != void.class) {
            Gson gson = new Gson();
            T result = (T) gson.fromJson(entry.data, tClass);
            return result;
        }
        return null;
    }

    public <T> void getRestCacheAysn(final String key, final RestCallback<T> callback) {
        RestHttpLog.i("get network data from cache");
        RequestQueue.getInstance().addReadNetworkCacheAsyn(new Runnable() {
            @Override
            public void run() {
                final Cache.Entry entry = NetworkCache.getInstance().get(key);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Class returnType = callback.getActualClass();
                        if (returnType != null && returnType != void.class) {
                            Gson gson = new Gson();
                            T result = (T) gson.fromJson(entry.data, returnType);
                            callback.callback(result);
                        }
                    }
                });
            }
        });
    }

}