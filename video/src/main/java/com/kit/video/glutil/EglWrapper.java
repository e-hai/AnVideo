package com.kit.video.glutil;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.util.Log;


public class EglWrapper {
    private static final boolean DEBUG = false;
    private static final String TAG = "EglWrapper";

    // 定义用于可记录Surface的EGL属性
    private static final int EGL_RECORDABLE_ANDROID = 0x3142;

    private EGLConfig eglConfig = null;  // EGL配置信息
    private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;  // EGL上下文
    private EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;  // EGL显示
    private EGLContext defaultContext = EGL14.EGL_NO_CONTEXT;  // 默认的EGL上下文

    // 构造函数，初始化EGL环境
    public EglWrapper(final EGLContext shared_context, final boolean with_depth_buffer, final boolean isRecordable) {
        if (DEBUG) Log.v(TAG, "EglWrapper:");
        init(shared_context, with_depth_buffer, isRecordable);
    }

    // 释放EGL资源
    public void release() {
        if (DEBUG) Log.v(TAG, "release:");
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            destroyContext();  // 销毁EGL上下文
            EGL14.eglTerminate(eglDisplay);  // 终止EGL显示
            EGL14.eglReleaseThread();  // 释放EGL线程
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY;
        eglContext = EGL14.EGL_NO_CONTEXT;
    }

    // 创建一个与Surface关联的EglSurface对象
    public EglSurface createFromSurface(final Object surface) {
        if (DEBUG) Log.v(TAG, "createFromSurface:");
        final EglSurface eglSurface = new EglSurface(this, surface);
        eglSurface.makeCurrent();  // 将该Surface设置为当前上下文
        return eglSurface;
    }

    // 获取当前EGL上下文
    public EGLContext getContext() {
        return eglContext;
    }

    // 查询Surface的属性
    int querySurface(final EGLSurface eglSurface, final int what) {
        final int[] value = new int[1];
        EGL14.eglQuerySurface(eglDisplay, eglSurface, what, value, 0);
        return value[0];
    }

    // 初始化EGL环境
    private void init(EGLContext shared_context, final boolean with_depth_buffer, final boolean isRecordable) {
        if (DEBUG) Log.v(TAG, "init:");
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException("EGL already set up");
        }

        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);  // 获取EGL显示
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException("eglGetDisplay failed");
        }

        final int[] version = new int[2];
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            eglDisplay = null;
            throw new RuntimeException("eglInitialize failed");
        }

        shared_context = shared_context != null ? shared_context : EGL14.EGL_NO_CONTEXT;
        if (eglContext == EGL14.EGL_NO_CONTEXT) {
            eglConfig = getConfig(with_depth_buffer, isRecordable);  // 获取EGL配置
            if (eglConfig == null) {
                throw new RuntimeException("chooseConfig failed");
            }
            // 创建EGL渲染上下文
            eglContext = createContext(shared_context);
        }

        // 确保EGL上下文成功创建
        final int[] values = new int[1];
        EGL14.eglQueryContext(eglDisplay, eglContext, EGL14.EGL_CONTEXT_CLIENT_VERSION, values, 0);
        if (DEBUG) Log.d(TAG, "EGLContext created, client version " + values[0]);
        makeDefault();  // 设置默认上下文
    }

    // 将指定Surface设置为当前上下文
    boolean makeCurrent(final EGLSurface surface) {
        if (eglDisplay == null) {
            if (DEBUG) Log.d(TAG, "makeCurrent:eglDisplay not initialized");
        }
        if (surface == null || surface == EGL14.EGL_NO_SURFACE) {
            final int error = EGL14.eglGetError();
            if (error == EGL14.EGL_BAD_NATIVE_WINDOW) {
                Log.e(TAG, "makeCurrent:returned EGL_BAD_NATIVE_WINDOW.");
            }
            return false;
        }

        // 将EGL渲染上下文绑定到指定Surface
        if (!EGL14.eglMakeCurrent(eglDisplay, surface, surface, eglContext)) {
            Log.w(TAG, "eglMakeCurrent:" + EGL14.eglGetError());
            return false;
        }
        return true;
    }

    // 将当前上下文设置为默认上下文（无Surface）
    void makeDefault() {
        if (DEBUG) Log.v(TAG, "makeDefault:");
        if (!EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)) {
            Log.w("TAG", "makeDefault" + EGL14.eglGetError());
        }
    }

    // 交换Surface的缓冲区
    int swap(final EGLSurface surface) {
        if (!EGL14.eglSwapBuffers(eglDisplay, surface)) {
            final int err = EGL14.eglGetError();
            if (DEBUG) Log.w(TAG, "swap:err=" + err);
            return err;
        }
        return EGL14.EGL_SUCCESS;
    }

    // 创建EGL上下文
    private EGLContext createContext(final EGLContext shared_context) {
        final int[] attrib_list = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,  // OpenGL ES 2.0
                EGL14.EGL_NONE
        };
        final EGLContext context = EGL14.eglCreateContext(eglDisplay, eglConfig, shared_context, attrib_list, 0);
        checkEglError("eglCreateContext");
        return context;
    }

    // 销毁EGL上下文
    private void destroyContext() {
        if (DEBUG) Log.v(TAG, "destroyContext:");

        if (!EGL14.eglDestroyContext(eglDisplay, eglContext)) {
            Log.e("destroyContext", "display:" + eglDisplay + " context: " + eglContext);
            Log.e(TAG, "eglDestroyContex:" + EGL14.eglGetError());
        }
        eglContext = EGL14.EGL_NO_CONTEXT;
        if (defaultContext != EGL14.EGL_NO_CONTEXT) {
            if (!EGL14.eglDestroyContext(eglDisplay, defaultContext)) {
                Log.e("destroyContext", "display:" + eglDisplay + " context: " + defaultContext);
                Log.e(TAG, "eglDestroyContex:" + EGL14.eglGetError());
            }
            defaultContext = EGL14.EGL_NO_CONTEXT;
        }
    }

    EGLSurface createWindowSurface(final Object surface) {
        if (DEBUG) Log.v(TAG, "createWindowSurface:nativeWindow=" + surface);

        final int[] surfaceAttribs = {
                EGL14.EGL_NONE
        };
        EGLSurface result = null;
        try {
            result = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, surfaceAttribs, 0);
        } catch (final IllegalArgumentException e) {
            Log.e(TAG, "eglCreateWindowSurface", e);
        }
        return result;
    }


    void destroyWindowSurface(EGLSurface surface) {
        if (DEBUG) Log.v(TAG, "destroySurface:");

        if (surface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglMakeCurrent(eglDisplay,
                    EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            EGL14.eglDestroySurface(eglDisplay, surface);
        }
        surface = EGL14.EGL_NO_SURFACE;
        if (DEBUG) Log.v(TAG, "destroySurface:finished");
    }

    private void checkEglError(final String msg) {
        int error;
        if ((error = EGL14.eglGetError()) != EGL14.EGL_SUCCESS) {
            throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
        }
    }

    @SuppressWarnings("unused")
    private EGLConfig getConfig(final boolean with_depth_buffer, final boolean isRecordable) {
        final int[] attribList = {
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE, EGL14.EGL_NONE,    //EGL14.EGL_STENCIL_SIZE, 8,
                EGL14.EGL_NONE, EGL14.EGL_NONE,    //EGL_RECORDABLE_ANDROID, 1,	// 此标志需要录制MediaCodec
                EGL14.EGL_NONE, EGL14.EGL_NONE,    //	with_depth_buffer ? EGL14.EGL_DEPTH_SIZE : EGL14.EGL_NONE,
                // with_depth_buffer ? 16 : 0,
                EGL14.EGL_NONE
        };
        int offset = 10;
        if (false) {
            attribList[offset++] = EGL14.EGL_STENCIL_SIZE;
            attribList[offset++] = 8;
        }
        if (with_depth_buffer) {
            attribList[offset++] = EGL14.EGL_DEPTH_SIZE;
            attribList[offset++] = 16;
        }
        if (isRecordable) {// MediaCodecの入力用Surfaceの場合
            attribList[offset++] = EGL_RECORDABLE_ANDROID;
            attribList[offset++] = 1;
        }
        for (int i = attribList.length - 1; i >= offset; i--) {
            attribList[i] = EGL14.EGL_NONE;
        }
        final EGLConfig[] configs = new EGLConfig[1];
        final int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.length, numConfigs, 0)) {
            // XXX，最好退回到RGB565
            Log.w(TAG, "unable to find RGBA8888 / " + " EGLConfig");
            return null;
        }
        return configs[0];
    }

}

